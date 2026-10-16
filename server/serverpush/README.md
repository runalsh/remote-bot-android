[English](README.md) | [Русский](README_RU.md)

# RemoteBot Push Gateway (`serverpush`)

Wake-up Push Gateway service for RemoteBot Android.
Receives incoming Webhook notifications from Telegram servers and dispatches a wake-up push signal (`Wakeup Data Push`) via **Google Firebase Cloud Messaging (FCM)** and/or **Huawei Push Kit (HMS)** to the target device.

---

## Implementations

| Variant | Directory | Description |
| :--- | :--- | :--- |
| **Cloudflare Worker** | [`cloudflare-worker/`](./cloudflare-worker/) | **Recommended serverless setup**. Zero VPS requirement, free SSL and Cloudflare domain. |
| **Go Daemon** | [`go/`](./go/) | Compiled Linux binary. Native goroutines with concurrent multi-provider push delivery. |
| **Python Daemon** | [`python/`](./python/) | Zero-dependency script for Linux VPS / Docker (standard Python 3 only). |
| **Nginx (OpenResty / Lua)** | [`nginx/`](./nginx/) | In-process proxy directly inside Nginx (requires OpenResty or Nginx built with `lua-nginx-module`). |

---

## API Network Contract

- **Healthcheck**: `GET /health` $\to$ `{"status":"healthy","fcm":true,"hms":true}` (HTTP 200).
- **FCM Route**: `POST /tg/fcm/:token/:secretKey`
- **HMS Route**: `POST /tg/hms/:token/:secretKey`
- **Auto Route**: `POST /tg/:token/:secretKey`
- **Query Selection**: `POST /tg/:token/:secretKey?provider=hms` (or `?provider=fcm`)

Request body: standard Telegram Webhook JSON with `update_id` payload.

---

## 1. Cloudflare Worker Deployment (Serverless)

Comprehensive step-by-step documentation with screenshots is available at [`docs/CLOUDFLARE_WORKER_SETUP.md`](../../docs/CLOUDFLARE_WORKER_SETUP.md).

### Summary:
1. Open [Cloudflare Dashboard](https://dash.cloudflare.com/) $\to$ **Compute (Workers & Pages)** $\to$ **Create Worker**.
2. Paste the source code from [`cloudflare-worker/worker.js`](./cloudflare-worker/worker.js).
3. In **Settings** $\to$ **Variables and Secrets**, configure:
   - `PUSH_GATEWAY_SECRET`: secret gateway key.
   - `FIREBASE_SERVICE_ACCOUNT`: contents of Firebase service account private key JSON (optional for FCM).
   - `HUAWEI_APP_ID`, `HUAWEI_CLIENT_ID`, `HUAWEI_CLIENT_SECRET`: Huawei AppGallery Connect credentials (optional for HMS).
4. Click **Deploy**.

---

## 2. Go Daemon Deployment on Linux VPS

### Build and Run:
```bash
cd serverpush/go
go build -o /usr/local/bin/remotebot-push-gateway main.go
```

### Environment Variables:
- `PORT`: HTTP server port (default: `8080`).
- `PUSH_GATEWAY_SECRET`: secret authentication key.
- `FIREBASE_SERVICE_ACCOUNT`: Firebase private key JSON string or file path.
- `HUAWEI_APP_ID`, `HUAWEI_CLIENT_ID`, `HUAWEI_CLIENT_SECRET`: Huawei HMS credentials.

### systemd unit (`/etc/systemd/system/remotebot-push-gateway.service`):
```ini
[Unit]
Description=RemoteBot Push Gateway (Go)
After=network.target

[Service]
Type=simple
User=www-data
Environment="PORT=8080"
Environment="PUSH_GATEWAY_SECRET=your_super_secret_key"
EnvironmentFile=/etc/remotebot/gateway.env
ExecStart=/usr/local/bin/remotebot-push-gateway
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

---

## 3. Python Daemon Deployment on Linux VPS

### Execution:
```bash
cd serverpush/python
PORT=8080 PUSH_GATEWAY_SECRET=your_secret python3 main.py
```

### systemd unit (`/etc/systemd/system/remotebot-push-python.service`):
```ini
[Unit]
Description=RemoteBot Push Gateway (Python)
After=network.target

[Service]
Type=simple
User=www-data
Environment="PORT=8080"
Environment="PUSH_GATEWAY_SECRET=your_super_secret_key"
EnvironmentFile=/etc/remotebot/gateway.env
ExecStart=/usr/bin/python3 /opt/remotebot/serverpush/python/main.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

---

## 4. Nginx Reverse Proxy & SSL Termination

Telegram Webhooks require a valid public HTTPS endpoint. Recommended standard Nginx configuration in front of Go or Python daemon:

```nginx
server {
    listen 80;
    server_name push.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name push.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/push.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/push.yourdomain.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 30s;
        proxy_connect_timeout 10s;
    }
}
```

---

## 5. In-Process Nginx Push Gateway (OpenResty / Lua)

### Can the Push Gateway run directly inside Nginx without Go/Python?
**On vanilla standard Nginx — no**, because Nginx out of the box only forwards raw TCP/HTTP streams, but cannot:
1. Sign **RS256 JWT** tokens with private RSA keys to exchange for Google OAuth2 access tokens (`https://oauth2.googleapis.com/token`);
2. Execute HTTP POST requests to Huawei OAuth endpoints (`https://oauth-login.cloud.huawei.com/oauth2/v2/token`);
3. Parse incoming Telegram JSON payloads (`update_id`) and construct new outbound FCM/HMS JSON Data Pushes.

**However, this is fully achievable using Nginx with `lua-nginx-module` (or OpenResty).**

Files are located in [`nginx/`](./nginx/):
- [`nginx/gateway.lua`](./nginx/gateway.lua) — complete authentication and push dispatch script;
- [`nginx/nginx.conf`](./nginx/nginx.conf) — OpenResty / Nginx configuration.

---

### Installing OpenResty:

On Debian / Ubuntu:
```bash
sudo apt-get update
sudo apt-get install -y wget gnupg ca-certificates lsb-release
wget -O - https://openresty.org/package/pubkey.gpg | sudo gpg --dearmor -o /usr/share/keyrings/openresty.gpg
echo "deb [signed-by=/usr/share/keyrings/openresty.gpg] http://openresty.org/package/ubuntu $(lsb_release -sc) main" | sudo tee /etc/apt/sources.list.d/openresty.list
sudo apt-get update
sudo apt-get install -y openresty luarocks

# Install libraries for JWT and HTTPS
sudo luarocks install lua-resty-http
sudo luarocks install lua-resty-jwt
sudo luarocks install lua-cjson
```

---

### Deployment Configuration:

1. Copy [`nginx/gateway.lua`](./nginx/gateway.lua) to `/etc/nginx/remotebot/gateway.lua`.
2. Copy [`nginx/nginx.conf`](./nginx/nginx.conf) to `/etc/nginx/conf.d/remotebot-push.conf`.
3. Set environment variables in `/etc/environment` or the `nginx.service` systemd unit:
   ```bash
   PUSH_GATEWAY_SECRET="your_secret_key"
   FIREBASE_SERVICE_ACCOUNT="/etc/remotebot/firebase.json"
   HUAWEI_APP_ID="12345678"
   HUAWEI_CLIENT_ID="12345678"
   HUAWEI_CLIENT_SECRET="huawei_secret"
   ```
4. Verify syntax and reload:
   ```bash
   sudo nginx -t
   sudo systemctl reload nginx
   ```

---

## Running Tests

From project root:
```bash
make test-serverpush-go   # Test Go gateway
make test-serverpush-py   # Test Python gateway
make test-serverpush-js   # Test Cloudflare Worker
make test-serverpush      # Run all 3 tests
```
