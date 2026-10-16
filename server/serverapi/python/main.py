#!/usr/bin/env python3
"""
Zero-dependency Telegram Bot API Reverse Proxy in Python.
Перенаправляет входящие HTTP/HTTPS запросы на api.telegram.org:443 с сохранением
путей, query параметров, заголовков и тел запросов (GET, POST, multipart и т.д.).
"""

import sys
import os
import urllib.request
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler

TARGET_HOST = "api.telegram.org"
PORT = int(os.environ.get("PORT", 8081))

# Headers that should not be forwarded directly
HOP_BY_HOP_HEADERS = {
    "connection", "keep-alive", "proxy-authenticate",
    "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade", "host"
}

class TelegramProxyHandler(BaseHTTPRequestHandler):

    def do_proxy(self):
        schema = "http" if TARGET_HOST.startswith("127.0.0.1") or TARGET_HOST.startswith("localhost") else "https"
        target_url = f"{schema}://{TARGET_HOST}{self.path}"

        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else None

        req_headers = {}
        for header, value in self.headers.items():
            if header.lower() not in HOP_BY_HOP_HEADERS:
                req_headers[header] = value

        req = urllib.request.Request(
            url=target_url,
            data=body,
            headers=req_headers,
            method=self.command
        )

        try:
            # If target host is local (in tests) or does not require proxy, disable local env proxy
            if "127.0.0.1" in TARGET_HOST or "localhost" in TARGET_HOST:
                opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            else:
                opener = urllib.request.build_opener()

            with opener.open(req, timeout=60) as resp:
                self.send_response(resp.status)
                for header, value in resp.headers.items():
                    if header.lower() not in HOP_BY_HOP_HEADERS:
                        self.send_header(header, value)
                self.end_headers()
                self.wfile.write(resp.read())
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            for header, value in e.headers.items():
                if header.lower() not in HOP_BY_HOP_HEADERS:
                    self.send_header(header, value)
            self.end_headers()
            self.wfile.write(e.read())
        except Exception as e:
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            err_msg = f'{{"ok":false,"error_code":502,"description":"Proxy Bad Gateway: {str(e)}"}}'
            self.wfile.write(err_msg.encode("utf-8"))

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"healthy"}\n')
            return
        self.do_proxy()

    def do_POST(self):
        self.do_proxy()

    def do_HEAD(self):
        self.do_proxy()

    def do_OPTIONS(self):
        self.do_proxy()

def run_server(port=PORT):
    server_address = ("", port)
    httpd = HTTPServer(server_address, TelegramProxyHandler)
    print(f"[Telegram Proxy Python] Listening on http://0.0.0.0:{port} -> https://{TARGET_HOST}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()

if __name__ == "__main__":
    run_server()
