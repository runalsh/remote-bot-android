[English](README.md) | [Русский](README_RU.md)

# RemoteBot Server Gateways (`server/`)

This directory contains infrastructure server components of RemoteBot:
1. **Push Gateway (`serverpush/`)** — 100% reliable wake-up of Android devices from deep sleep (Doze Mode) via push notifications (Google FCM and Huawei HMS) upon incoming Telegram bot updates.
2. **API Reverse Proxy (`serverapi/`)** — transparent reverse proxy to `api.telegram.org` for uninterrupted app operation during DPI filtering, ISP blocking, or regional restrictions.

---

## Architectural Overview

```
[ Telegram User ]
        │ (sends message to bot)
        ▼
[ Telegram Bot API ]
        │ (Webhook)
        ▼
┌──────────────────────────────────────────────┐
│  Push Gateway (:8080)                        │
│  - Signature and Whitelist validation        │
│  - Provider detection (FCM v1 / HMS)         │
│  - High-priority data-only Push dispatch     │
└──────────────┬───────────────────────────────┘
               │
      ┌────────┴────────┐
      ▼                 ▼
 [Google FCM]    [Huawei Push Kit]
      │                 │
      └────────┬────────┘
               ▼
┌──────────────────────────────────────────────┐
│  Android Device (RemoteBot)                  │
│  - Receives Wakeup Push (WakeLock/Foreground)│
│  - Fetches update and executes command       │
│  - Dispatches response via API Proxy         │
└──────────────┬───────────────────────────────┘
               │ (HTTPS requests)
               ▼
┌──────────────────────────────────────────────┐
│  API Reverse Proxy (:8081)                   │
│  - Proxies https://api.telegram.org          │
│  - SNI masking / DPI evasion                 │
└──────────────┬───────────────────────────────┘
               │
               ▼
    [ Telegram Bot API ]
```

---

## Implementations

Each gateway is implemented across 4 technology stacks. Each subdirectory includes its own independent `Dockerfile`:

### 1. Push Gateway (`serverpush/`)
| Directory | Stack | Features | Dockerfile |
|---|---|---|---|
| [`serverpush/go/`](serverpush/go) | **Go 1.27.2** | **Primary / Recommended**. Pure Scratch (0 MB OS, zero attack surface), static binary, SSL root certificates, standalone Healthcheck (`-healthcheck`). | [`Dockerfile`](serverpush/go/Dockerfile) |
| [`serverpush/python/`](serverpush/python) | **Python 3.14.8** | Zero-dependency (pure Python standard library), instant startup without compilation. | [`Dockerfile`](serverpush/python/Dockerfile) |
| [`serverpush/nginx/`](serverpush/nginx) | **OpenResty 1.31.1.1-alpine-slim** | Ultra-high throughput Nginx + Lua, shared memory OAuth token caching. | [`Dockerfile`](serverpush/nginx/Dockerfile) |
| [`serverpush/cloudflare-worker/`](serverpush/cloudflare-worker) | **Cloudflare Workers (Node 24.22.0)** | Serverless Edge worker. Free execution on Cloudflare global edge without renting a server. | [`Dockerfile`](serverpush/cloudflare-worker/Dockerfile) |

### 2. API Reverse Proxy (`serverapi/`)
| Directory | Stack | Features | Dockerfile |
|---|---|---|---|
| [`serverapi/go/`](serverapi/go) | **Go 1.27.2** | **Primary / Recommended**. Pure Scratch (0 MB OS, zero attack surface), static `httputil.ReverseProxy`, unbuffered streaming, Long Polling support (`getUpdates`), standalone Healthcheck (`-healthcheck`). | [`Dockerfile`](serverapi/go/Dockerfile) |
| [`serverapi/python/`](serverapi/python) | **Python 3.14.8** | Lightweight proxy based on `http.server` and `urllib`. | [`Dockerfile`](serverapi/python/Dockerfile) |
| [`serverapi/nginx/`](serverapi/nginx) | **Nginx 1.30.6-alpine-slim** | Classic production reverse proxy with optimized TLS buffers and `client_max_body_size 50M`. | [`Dockerfile`](serverapi/nginx/Dockerfile) |
| [`serverapi/cloudflare-worker/`](serverapi/cloudflare-worker) | **Cloudflare Workers (Node 24.22.0)** | Single-file JavaScript edge proxy routed via Cloudflare global network. | [`Dockerfile`](serverapi/cloudflare-worker/Dockerfile) |

---

## Quickstart via Docker Compose

To launch both services in one step, use [`docker-compose.yml`](docker-compose.yml).

### Step 1. Prepare Environment Variables

Copy the example configuration file:
```bash
cp ../docs/.env.example .env
```

Configure parameters in `.env`:
```ini
# Service Ports
PUSH_GATEWAY_PORT=8080
API_PROXY_PORT=8081

# Telegram
TELEGRAM_BOT_TOKEN=123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ
WHITELIST_CHAT_IDS=12345678,87654321
PUSH_PROVIDER=auto

# Google Firebase Cloud Messaging v1 (Service Account JSON)
FIREBASE_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"...","private_key":"...","client_email":"..."}'

# Huawei Push Kit (HMS)
HUAWEI_APP_ID=105581234
HUAWEI_CLIENT_ID=105581234
HUAWEI_CLIENT_SECRET=your_huawei_client_secret

# Telegram API Target Host
TARGET_HOST=api.telegram.org

# Active Profile (go / python / nginx / npx)
COMPOSE_PROFILES=go
```

### Step 2. Launch Profile Containers

`docker-compose.yml` provides 4 isolated profiles: `go`, `python`, `nginx`, `npx`.
Launch your preferred stack with `--profile` or set `COMPOSE_PROFILES=...` in `.env`:

```bash
# 1. Go Stack (Recommended for production: push-gateway-go + api-proxy-go)
docker compose --profile go up -d

# 2. Python Stack (push-gateway-python + api-proxy-python)
docker compose --profile python up -d

# 3. Nginx / OpenResty Stack (push-gateway-nginx + api-proxy-nginx)
docker compose --profile nginx up -d

# 4. Cloudflare Workers / Node Stack (push-gateway-npx + api-proxy-npx)
docker compose --profile npx up -d
```

### Step 3. Verify Health

Check running containers:
```bash
docker compose ps
```

Verify health endpoints:
```bash
# Push Gateway (port 8080)
curl http://localhost:8080/health
# Response: {"status":"healthy","fcm":true,"hms":true}

# API Proxy (port 8081)
curl http://localhost:8081/health
# Response: {"status":"healthy","target_host":"api.telegram.org"}
```

---

## Building Docker Images

### Build All 8 Images via Makefile
From project root:
```bash
# Build all 8 server images in one command
make docker-build-all

# Or separately:
make docker-build-push   # 4 Push Gateway variants (Go, Python, Nginx, NPX)
make docker-build-api    # 4 API Proxy variants (Go, Python, Nginx, NPX)

# Or via Docker Compose:
make docker-compose-build
```

### Manual Individual Docker Builds:

### Push Gateway:
```bash
# Go version (Pure Scratch, recommended)
docker build -t remotebot-push-gateway:go -f serverpush/go/Dockerfile serverpush/go

# Python version
docker build -t remotebot-push-gateway:python -f serverpush/python/Dockerfile serverpush/python

# OpenResty Nginx version
docker build -t remotebot-push-gateway:nginx -f serverpush/nginx/Dockerfile serverpush/nginx

# Cloudflare Worker version (local dev server)
docker build -t remotebot-push-gateway:cf -f serverpush/cloudflare-worker/Dockerfile serverpush/cloudflare-worker
```

### API Reverse Proxy:
```bash
# Go version (recommended)
docker build -t remotebot-api-proxy:go -f serverapi/go/Dockerfile serverapi/go

# Python version
docker build -t remotebot-api-proxy:python -f serverapi/python/Dockerfile serverapi/python

# Nginx version
docker build -t remotebot-api-proxy:nginx -f serverapi/nginx/Dockerfile serverapi/nginx

# Cloudflare Worker version
docker build -t remotebot-api-proxy:cf -f serverapi/cloudflare-worker/Dockerfile serverapi/cloudflare-worker
```

---

## Register Telegram Webhook to Gateway

To route incoming Telegram updates to your Push Gateway, register the webhook:

```bash
curl -F "url=https://your-push-domain.com/tg/auto/<PUSH_TOKEN>/<SECRET_KEY>" \
     https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook
```

Where:
- `your-push-domain.com` — public domain of your gateway with valid SSL certificate.
- `auto` — automatic provider selection (`fcm`, `hms`, or `auto`).
- `<PUSH_TOKEN>` — device push token obtained from RemoteBot Settings/Debug tab.
- `<SECRET_KEY>` — your gateway secret security key (`PUSH_GATEWAY_SECRET`).

---

## Testing Server Gateways

From project root [`remote-bot-android/`](../):
```bash
# Run all server gateway tests (Push Gateway + API Proxy)
make test-server
```
