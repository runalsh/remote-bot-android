#!/usr/bin/env python3
import unittest
import threading
import urllib.request
import json
from http.server import HTTPServer, BaseHTTPRequestHandler
import sys
import os

# Добавляем текущую директорию в sys.path
sys.path.insert(0, os.path.dirname(__file__))
from main import TelegramProxyHandler

class MockTelegramServerHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"ok":true,"result":"get_ok"}')

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        resp = {"ok": True, "received_path": self.path, "received_body": body.decode("utf-8")}
        self.wfile.write(json.dumps(resp).encode("utf-8"))

class TestPythonApiProxy(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Поднимаем Mock сервер Telegram
        cls.mock_server = HTTPServer(("127.0.0.1", 18090), MockTelegramServerHandler)
        cls.mock_thread = threading.Thread(target=cls.mock_server.serve_forever, daemon=True)
        cls.mock_thread.start()

        # Патчим TARGET_HOST в модуле main на наш mock-сервер
        import main
        cls.original_target = main.TARGET_HOST
        main.TARGET_HOST = "127.0.0.1:18090"

        # Поднимаем сам Proxy сервер
        cls.proxy_server = HTTPServer(("127.0.0.1", 18091), TelegramProxyHandler)
        cls.proxy_thread = threading.Thread(target=cls.proxy_server.serve_forever, daemon=True)
        cls.proxy_thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.proxy_server.shutdown()
        cls.proxy_server.server_close()
        cls.mock_server.shutdown()
        cls.mock_server.server_close()
        import main
        main.TARGET_HOST = cls.original_target

    def test_proxy_get_request(self):
        url = "http://127.0.0.1:18091/bot12345/getMe"
        req = urllib.request.Request(url, method="GET")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(req) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            self.assertTrue(data["ok"])
            self.assertEqual(data["result"], "get_ok")

    def test_proxy_post_request(self):
        url = "http://127.0.0.1:18091/bot12345/sendMessage?chat_id=123"
        payload = json.dumps({"text": "Hello proxy"}).encode("utf-8")
        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"}, method="POST")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(req) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            self.assertTrue(data["ok"])
            self.assertEqual(data["received_path"], "/bot12345/sendMessage?chat_id=123")
            self.assertIn("Hello proxy", data["received_body"])

if __name__ == "__main__":
    unittest.main()
