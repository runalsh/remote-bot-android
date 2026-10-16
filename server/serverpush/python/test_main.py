#!/usr/bin/env python3
import json
import os
import threading
import unittest
import urllib.request
import urllib.error
from main import ThreadedHTTPServer, PushGatewayHandler
import main


class TestPythonPushGateway(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadedHTTPServer(("127.0.0.1", 0), PushGatewayHandler)
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        main.SERVER_SECRET_KEY = "test_secret_123"
        # Отключаем внешние прокси для локальных тестов
        cls.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        main.SERVER_SECRET_KEY = ""

    def test_health_check_default(self):
        url = f"http://127.0.0.1:{self.port}/health"
        with self.opener.open(url) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode())
            self.assertEqual(data.get("status"), "healthy")

    def test_health_check_with_credentials(self):
        url = f"http://127.0.0.1:{self.port}/health"
        orig_key = os.environ.get("FCM_LEGACY_SERVER_KEY", "")
        orig_hms_id = os.environ.get("HUAWEI_APP_ID", "")
        orig_hms_sec = os.environ.get("HUAWEI_CLIENT_SECRET", "")
        try:
            os.environ["FCM_LEGACY_SERVER_KEY"] = "legacy_key"
            os.environ["HUAWEI_APP_ID"] = "1001"
            os.environ["HUAWEI_CLIENT_SECRET"] = "hms_sec"
            with self.opener.open(url) as resp:
                self.assertEqual(resp.status, 200)
                data = json.loads(resp.read().decode())
                self.assertEqual(data.get("status"), "healthy")
                self.assertTrue(data.get("fcm"))
                self.assertTrue(data.get("hms"))
        finally:
            if orig_key: os.environ["FCM_LEGACY_SERVER_KEY"] = orig_key
            else: os.environ.pop("FCM_LEGACY_SERVER_KEY", None)
            if orig_hms_id: os.environ["HUAWEI_APP_ID"] = orig_hms_id
            else: os.environ.pop("HUAWEI_APP_ID", None)
            if orig_hms_sec: os.environ["HUAWEI_CLIENT_SECRET"] = orig_hms_sec
            else: os.environ.pop("HUAWEI_CLIENT_SECRET", None)

    def test_webhook_invalid_path(self):
        url = f"http://127.0.0.1:{self.port}/tg/only_token"
        req = urllib.request.Request(url, data=b'{"update_id":1}', method="POST")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.opener.open(req)
        self.assertEqual(ctx.exception.code, 400)

    def test_webhook_excessive_path(self):
        url = f"http://127.0.0.1:{self.port}/tg/one/two/three/four"
        req = urllib.request.Request(url, data=b'{"update_id":1}', method="POST")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.opener.open(req)
        self.assertEqual(ctx.exception.code, 400)

    def test_webhook_unauthorized(self):
        url = f"http://127.0.0.1:{self.port}/tg/sample_token/wrong_secret"
        req = urllib.request.Request(url, data=json.dumps({"update_id": 123}).encode(), method="POST")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.opener.open(req)
        self.assertEqual(ctx.exception.code, 401)

    def test_webhook_method_not_allowed(self):
        url = f"http://127.0.0.1:{self.port}/tg/sample_token/test_secret_123"
        req = urllib.request.Request(url, method="GET")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.opener.open(req)
        self.assertEqual(ctx.exception.code, 405)

    def test_webhook_invalid_json(self):
        url = f"http://127.0.0.1:{self.port}/tg/sample_token/test_secret_123"
        req = urllib.request.Request(url, data=b"not-json-content", method="POST")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.opener.open(req)
        self.assertEqual(ctx.exception.code, 400)

    def test_webhook_empty_body(self):
        url = f"http://127.0.0.1:{self.port}/tg/sample_token/test_secret_123"
        req = urllib.request.Request(url, data=b"", method="POST")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.opener.open(req)
        self.assertEqual(ctx.exception.code, 400)

    def test_webhook_success(self):
        url = f"http://127.0.0.1:{self.port}/tg/sample_token/test_secret_123"
        payload = json.dumps({"update_id": 998877}).encode()
        req = urllib.request.Request(url, data=payload, method="POST")
        with self.opener.open(req) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode())
            self.assertEqual(data.get("status"), "ok")

    def test_webhook_hms_success(self):
        url = f"http://127.0.0.1:{self.port}/tg/hms/hms_token_xyz/test_secret_123"
        payload = json.dumps({"update_id": 112233}).encode()
        req = urllib.request.Request(url, data=payload, method="POST")
        with self.opener.open(req) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode())
            self.assertEqual(data.get("status"), "ok")

    def test_webhook_provider_query(self):
        url = f"http://127.0.0.1:{self.port}/tg/hms_token_xyz/test_secret_123?provider=hms"
        payload = json.dumps({"update_id": 445566}).encode()
        req = urllib.request.Request(url, data=payload, method="POST")
        with self.opener.open(req) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode())
            self.assertEqual(data.get("status"), "ok")

    def test_not_found(self):
        url = f"http://127.0.0.1:{self.port}/non_existent_route"
        req = urllib.request.Request(url, method="GET")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.opener.open(req)
        self.assertEqual(ctx.exception.code, 404)


if __name__ == "__main__":
    unittest.main()
