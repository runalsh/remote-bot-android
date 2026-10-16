#!/usr/bin/env python3
"""
RemoteBot Webhook Push Gateway (Python)
Supports Firebase Cloud Messaging HTTP v1 & Legacy, and Huawei Mobile Services Push Kit REST API v1.
"""

import base64
import json
import os
import ssl
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn

SERVER_SECRET_KEY = os.environ.get("PUSH_GATEWAY_SECRET", "")
FCM_LEGACY_SERVER_KEY = os.environ.get("FCM_LEGACY_SERVER_KEY", "")

HUAWEI_APP_ID = os.environ.get("HUAWEI_APP_ID", "")
HUAWEI_CLIENT_ID = os.environ.get("HUAWEI_CLIENT_ID", HUAWEI_APP_ID)
HUAWEI_CLIENT_SECRET = os.environ.get("HUAWEI_CLIENT_SECRET", "")

_cached_token = None
_token_expiry = 0

_cached_huawei_token = None
_huawei_token_expiry = 0

_token_lock = threading.Lock()


def get_service_account() -> dict | None:
    raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT", "")
    if not raw:
        path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "")
        if path and os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    raw = f.read()
            except Exception as e:
                print(f"[FCM] Error reading credentials file: {e}")
                return None

    if raw:
        try:
            return json.loads(raw)
        except Exception as e:
            print(f"[FCM] Error parsing service account JSON: {e}")
            return None
    return None


def get_google_oauth_token(sa: dict) -> str | None:
    global _cached_token, _token_expiry
    now = int(time.time())
    with _token_lock:
        if _cached_token and _token_expiry > now + 120:
            return _cached_token

    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding
    except ImportError:
        print("[FCM v1] Error: 'cryptography' package is required for FCM v1 RSA signing. Run: pip install cryptography")
        return None

    header = {"alg": "RS256", "typ": "JWT"}
    if "private_key_id" in sa:
        header["kid"] = sa["private_key_id"]

    claim_set = {
        "iss": sa.get("client_email"),
        "scope": "https://www.googleapis.com/auth/firebase.messaging",
        "aud": "https://oauth2.googleapis.com/token",
        "iat": now - 30,
        "exp": now + 3600,
    }

    def b64url(data: bytes) -> str:
        return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")

    enc_header = b64url(json.dumps(header).encode("utf-8"))
    enc_claims = b64url(json.dumps(claim_set).encode("utf-8"))
    sig_input = f"{enc_header}.{enc_claims}".encode("utf-8")

    pem_str = sa.get("private_key", "").replace("\\n", "\n").encode("utf-8")
    private_key = serialization.load_pem_private_key(pem_str, password=None)

    signature = private_key.sign(sig_input, padding.PKCS1v15(), hashes.SHA256())
    jwt = f"{enc_header}.{enc_claims}.{b64url(signature)}"

    post_data = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": jwt,
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://oauth2.googleapis.com/token",
        data=post_data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            token = data.get("access_token")
            expires_in = data.get("expires_in", 3600)
            with _token_lock:
                _cached_token = token
                _token_expiry = now + expires_in
            return token
    except Exception as e:
        print(f"[FCM v1] Error obtaining OAuth token: {e}")
        return None


def get_huawei_oauth_token(client_id: str, client_secret: str) -> str | None:
    global _cached_huawei_token, _huawei_token_expiry
    now = int(time.time())
    with _token_lock:
        if _cached_huawei_token and _huawei_token_expiry > now + 60:
            return _cached_huawei_token

    post_data = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "client_id": client_id,
        "client_secret": client_secret,
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://oauth-login.cloud.huawei.com/oauth2/v3/token",
        data=post_data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            token = data.get("access_token")
            expires_in = data.get("expires_in", 3600)
            with _token_lock:
                _cached_huawei_token = token
                _huawei_token_expiry = now + expires_in
            return token
    except Exception as e:
        print(f"[HMS] Error obtaining OAuth token: {e}")
        return None


def send_fcm_v1(sa: dict, fcm_token: str, update_id: int, update_json: str):
    token = get_google_oauth_token(sa)
    if not token:
        return

    project_id = sa.get("project_id")
    data_payload = {
        "action": "wakeup",
        "update_id": str(update_id),
    }
    if update_json and len(update_json) < 3800:
        data_payload["update_json"] = update_json

    payload = {
        "message": {
            "token": fcm_token,
            "data": data_payload,
            "android": {
                "priority": "high",
            },
        }
    }

    req = urllib.request.Request(
        f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"[FCM v1] Delivered wakeup for update_id {update_id}, status: {resp.status}")
    except urllib.error.URLError as e:
        print(f"[FCM v1] Failed to send wakeup: {e}")


def send_fcm_legacy(server_key: str, fcm_token: str, update_id: int, update_json: str):
    data_payload = {
        "action": "wakeup",
        "update_id": str(update_id),
    }
    if update_json and len(update_json) < 3800:
        data_payload["update_json"] = update_json

    payload = {
        "to": fcm_token,
        "data": data_payload,
    }

    req = urllib.request.Request(
        "https://fcm.googleapis.com/fcm/send",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"key={server_key}",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"[FCM Legacy] Delivered wakeup for update_id {update_id}, status: {resp.status}")
    except urllib.error.URLError as e:
        print(f"[FCM Legacy] Failed to send wakeup: {e}")


def send_hms_push(app_id: str, client_id: str, client_secret: str, hms_token: str, update_id: int, update_json: str):
    token = get_huawei_oauth_token(client_id, client_secret)
    if not token:
        return

    data_payload = {
        "action": "wakeup",
        "update_id": str(update_id),
    }
    if update_json and len(update_json) < 3800:
        data_payload["update_json"] = update_json

    payload = {
        "validate_only": False,
        "message": {
            "data": json.dumps(data_payload),
            "token": [hms_token],
            "android": {
                "urgency": "HIGH",
                "category": "IM",
            },
        },
    }

    req = urllib.request.Request(
        f"https://push-api.cloud.huawei.com/v1/{app_id}/messages:send",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json; charset=UTF-8",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"[HMS] Delivered wakeup for update_id {update_id}, status: {resp.status}")
    except urllib.error.URLError as e:
        print(f"[HMS] Failed to send wakeup: {e}")


def dispatch_wakeup(provider: str, push_token: str, update_id: int, update_json: str):
    hms_app_id = os.environ.get("HUAWEI_APP_ID", HUAWEI_APP_ID)
    hms_client_id = os.environ.get("HUAWEI_CLIENT_ID", HUAWEI_CLIENT_ID or hms_app_id)
    hms_client_secret = os.environ.get("HUAWEI_CLIENT_SECRET", HUAWEI_CLIENT_SECRET)
    has_hms = bool(hms_app_id and hms_client_secret)

    sa = get_service_account()
    legacy_key = os.environ.get("FCM_LEGACY_SERVER_KEY", FCM_LEGACY_SERVER_KEY)
    has_fcm = bool(sa or legacy_key)

    target_hms = provider == "hms" or (provider == "auto" and has_hms and not has_fcm)
    target_fcm = provider == "fcm" or (provider == "auto" and has_fcm)

    if target_hms and has_hms:
        send_hms_push(hms_app_id, hms_client_id, hms_client_secret, push_token, update_id, update_json)

    if target_fcm and has_fcm:
        if sa:
            send_fcm_v1(sa, push_token, update_id, update_json)
        elif legacy_key:
            send_fcm_legacy(legacy_key, push_token, update_id, update_json)


class PushGatewayHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        sys.stdout.write(f"[{self.log_date_time_string()}] {format % args}\n")

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/health":
            sa = get_service_account()
            has_fcm = bool(sa or os.environ.get("FCM_LEGACY_SERVER_KEY", FCM_LEGACY_SERVER_KEY))
            has_hms = bool(os.environ.get("HUAWEI_APP_ID", HUAWEI_APP_ID) and os.environ.get("HUAWEI_CLIENT_SECRET", HUAWEI_CLIENT_SECRET))

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            resp = json.dumps({"status": "healthy", "fcm": has_fcm, "hms": has_hms})
            self.wfile.write(resp.encode("utf-8"))
        elif parsed.path.startswith("/tg/"):
            self.send_response(405)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"error":"Method Not Allowed"}')
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        # Supported paths:
        # - /tg/<token>/<secret_key>
        # - /tg/fcm/<token>/<secret_key>
        # - /tg/hms/<token>/<secret_key>
        parsed = urllib.parse.urlparse(self.path)
        parts = parsed.path.strip("/").split("/")
        provider = "auto"
        if len(parts) == 3 and parts[0] == "tg":
            push_token = parts[1]
            client_secret = parts[2]
            query_params = urllib.parse.parse_qs(parsed.query)
            prov_query = query_params.get("provider", [None])[0]
            if prov_query:
                provider = prov_query.lower()
        elif len(parts) == 4 and parts[0] == "tg":
            provider = parts[1].lower()
            push_token = parts[2]
            client_secret = parts[3]
        else:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"error":"Invalid path. Expected /tg/:token/:secretKey or /tg/hms/:token/:secretKey"}')
            return

        secret = os.environ.get("PUSH_GATEWAY_SECRET", SERVER_SECRET_KEY)
        if secret and client_secret != secret:
            self.send_response(401)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"error":"Unauthorized"}')
            return

        content_length = int(self.headers.get("Content-Length", 0))
        if content_length <= 0:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"error":"Empty body"}')
            return

        body = self.rfile.read(content_length)
        update_json = ""
        try:
            update = json.loads(body.decode("utf-8"))
            update_id = update.get("update_id", 0)
            update_json = body.decode("utf-8")
        except Exception:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"error":"Invalid JSON"}')
            return

        print(f"[WAKEUP] Received update_id: {update_id} for token: {push_token[:8]}...")

        # Async background push
        threading.Thread(target=dispatch_wakeup, args=(provider, push_token, update_id, update_json), daemon=True).start()

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"status":"ok"}')


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


def main():
    port = int(os.environ.get("PORT", "8080"))
    server = ThreadedHTTPServer(("0.0.0.0", port), PushGatewayHandler)
    print(f"Starting RemoteBot Webhook Push Gateway (Python) on :port {port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
