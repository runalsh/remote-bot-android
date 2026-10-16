[English](README.md) | [Русский](README_RU.md)

# Telegram Bot API Reverse Proxy (`serverapi`)

This directory contains implementations of a Reverse Proxy server designed to forward Android application requests to `api.telegram.org`.

## Why is this needed?
1. **Bypass censorship & DPI blocks**: when the mobile carrier or ISP blocks direct access to `api.telegram.org`.
2. **Dedicated custom mirror**: ability to configure your personal domain in the RemoteBot settings ("Telegram" -> "Custom API Root / Mirrors").
3. **Full protocol preservation**: transparently proxies all traffic (GET, POST, file uploads, Long Polling `getUpdates`).

---

## Implementations

| Variant | Directory | Description | Requires Nginx? |
| :--- | :--- | :--- | :--- |
| **Nginx (Pure Proxy)** | [`nginx/`](./nginx/) | **Fastest and lightest option**. Requires zero runtime dependencies (no Node/Python/Go). Nginx directly proxies all traffic to `api.telegram.org` over HTTPS. | **Yes** (Nginx itself is the server) |
| **Cloudflare Worker** | [`cloudflare-worker/`](./cloudflare-worker/) | Serverless setup without owning a VPS. Free SSL and `*.workers.dev` domain. | **No** (runs on Cloudflare Edge) |
| **Go Daemon** | [`go/`](./go/) | Compiled static binary with zero external dependencies. High throughput. | Optional (in front of Go as HTTPS termination) |
| **Python Daemon** | [`python/`](./python/) | Zero-dependency script using pure Python 3 standard library. | Optional (in front of Python as HTTPS termination) |

---

## 1. Nginx Variant (Recommended for VPS, No Code)

Can be operated using **Nginx alone** without running any daemons in Go, Python, or Node.js.

### Step-by-Step Configuration:
1. Copy the configuration file [`nginx/nginx.conf`](./nginx/nginx.conf) to your VPS at `/etc/nginx/sites-available/tg-proxy.conf`.
2. Replace `tg-api.yourdomain.com` with your actual domain name.
3. Issue a free SSL certificate via Certbot:
   ```bash
   sudo certbot --nginx -d tg-api.yourdomain.com
   ```
4. Enable the virtual host and reload Nginx:
   ```bash
   sudo ln -s /etc/nginx/sites-available/tg-proxy.conf /etc/nginx/sites-enabled/
   sudo nginx -t
   sudo systemctl reload nginx
   ```
5. In RemoteBot settings under **Telegram**, enter:
   `https://tg-api.yourdomain.com`

---

## 2. Cloudflare Worker Variant (No VPS Needed)

1. Open the [Cloudflare Workers dashboard](https://dash.cloudflare.com/).
2. Create a new Worker (e.g., `my-tg-api-proxy`).
3. Paste the contents of [`cloudflare-worker/worker.js`](./cloudflare-worker/worker.js):
   ```javascript
   export default {
     async fetch(request) {
       const url = new URL(request.url);
       url.hostname = "api.telegram.org";
       return fetch(url.toString(), request);
     },
   };
   ```
4. Click **Deploy**.
5. Copy the generated URL (e.g., `https://my-tg-api-proxy.yourname.workers.dev`) and paste it into the RemoteBot "Custom API Root" field.

---

## 3. Go Standalone Variant

```bash
cd serverapi/go
go build -o /usr/local/bin/tg-api-proxy main.go
PORT=8081 /usr/local/bin/tg-api-proxy
```

### systemd unit (`/etc/systemd/system/tg-api-proxy.service`):
```ini
[Unit]
Description=Telegram API Proxy Go Daemon
After=network.target

[Service]
Type=simple
Environment=PORT=8081
ExecStart=/usr/local/bin/tg-api-proxy
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

To terminate HTTPS via Nginx:
```nginx
location / {
    proxy_pass http://127.0.0.1:8081;
    proxy_set_header Host $host;
    proxy_buffering off;
    proxy_read_timeout 90s;
}
```

---

## 4. Python Standalone Variant

```bash
cd serverapi/python
PORT=8081 python3 main.py
```
Similarly configure systemd unit and reverse proxy Nginx to `http://127.0.0.1:8081`.

---

## Running Tests

From project root:
```bash
make test-serverapi-go   # Test Go proxy
make test-serverapi-py   # Test Python proxy
make test-serverapi-js   # Test Cloudflare Worker
make test-serverapi      # All 3 tests
```
