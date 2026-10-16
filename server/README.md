# Серверные шлюзы RemoteBot (`server/`)

Данная директория содержит инфраструктурные серверные компоненты RemoteBot, обеспечивающие:
1. **Push Gateway (`serverpush/`)** — 100% надежное пробуждение устройства из глубокого сна (Doze Mode) через Push-уведомления (Google FCM и Huawei HMS) при поступлении входящих сообщений в Telegram-бот.
2. **API Reverse Proxy (`serverapi/`)** — прозрачное обратное проксирование запросов к `api.telegram.org` для бесперебойной работы приложения в условиях DPI, блокировок или региональных ограничений.

---

## Архитектура работы

```
[ Пользователь в Telegram ]
            │ (сообщение боту)
            ▼
    [ Telegram Bot API ]
            │ (Webhook)
            ▼
┌──────────────────────────────────────────────┐
│  Push Gateway (:8080)                        │
│  - Валидация подписи и Whitelist             │
│  - Определение провайдера (FCM v1 / HMS)      │
│  - Отправка data-only Push (High Priority)    │
└──────────────┬───────────────────────────────┘
               │
      ┌────────┴────────┐
      ▼                 ▼
 [Google FCM]    [Huawei Push Kit]
      │                 │
      └────────┬────────┘
               ▼
┌──────────────────────────────────────────────┐
│  Android-устройство (RemoteBot)               │
│  - Прием Wakeup Push (WakeLock + Foreground) │
│  - Забор апдейта и выполнение команды        │
│  - Отправка ответа через API Proxy           │
└──────────────┬───────────────────────────────┘
               │ (HTTPS запросы)
               ▼
┌──────────────────────────────────────────────┐
│  API Reverse Proxy (:8081)                   │
│  - Проксирует https://api.telegram.org       │
│  - Скрытие SNI / обход DPI                   │
└──────────────┬───────────────────────────────┘
               │
               ▼
    [ Telegram Bot API ]
```

---

## Варианты реализаций

Каждый шлюз реализован на 4 технологических стеках. Каждый подкаталог содержит свой независимый `Dockerfile`:

### 1. Push Gateway (`serverpush/`)
| Каталог | Стек | Особенности | Dockerfile |
|---|---|---|---|
| [`serverpush/go/`](file:///root/remote-bot-android/server/serverpush/go) | **Go 1.27.2** | **Основной / Рекомендуемый**. Pure Scratch (0 MB ОС, нулевая поверхность атаки), статический бинарник, SSL-сертификаты, автономный Healthcheck (`-healthcheck`). | [`Dockerfile`](file:///root/remote-bot-android/server/serverpush/go/Dockerfile) |
| [`serverpush/python/`](file:///root/remote-bot-android/server/serverpush/python) | **Python 3.14.8** | Zero-dependency (чистая стандартная библиотека Python), быстрый запуск без компиляции. | [`Dockerfile`](file:///root/remote-bot-android/server/serverpush/python/Dockerfile) |
| [`serverpush/nginx/`](file:///root/remote-bot-android/server/serverpush/nginx) | **OpenResty 1.31.1.1-alpine-slim** | Сверхвысокая производительность Nginx + Lua, кэширование OAuth-токенов в shared memory. | [`Dockerfile`](file:///root/remote-bot-android/server/serverpush/nginx/Dockerfile) |
| [`serverpush/cloudflare-worker/`](file:///root/remote-bot-android/server/serverpush/cloudflare-worker) | **Cloudflare Workers (Node 24.22.0)** | Serverless Edge-воркер. Бесплатный запуск на инфраструктуре Cloudflare без необходимости арендовать собственный сервер. | [`Dockerfile`](file:///root/remote-bot-android/server/serverpush/cloudflare-worker/Dockerfile) |

### 2. API Reverse Proxy (`serverapi/`)
| Каталог | Стек | Особенности | Dockerfile |
|---|---|---|---|
| [`serverapi/go/`](file:///root/remote-bot-android/server/serverapi/go) | **Go 1.27.2** | **Основной / Рекомендуемый**. Pure Scratch (0 MB ОС, нулевая поверхность атаки), статический `httputil.ReverseProxy`, стриминг без буферизации, поддержка Long Polling (`getUpdates`), Healthcheck (`-healthcheck`). | [`Dockerfile`](file:///root/remote-bot-android/server/serverapi/go/Dockerfile) |
| [`serverapi/python/`](file:///root/remote-bot-android/server/serverapi/python) | **Python 3.14.8** | Легковесный прокси на `http.server` и `urllib`. | [`Dockerfile`](file:///root/remote-bot-android/server/serverapi/python/Dockerfile) |
| [`serverapi/nginx/`](file:///root/remote-bot-android/server/serverapi/nginx) | **Nginx 1.30.6-alpine-slim** | Классический production reverse proxy с оптимизированными TLS-буферами и client_max_body_size 50M. | [`Dockerfile`](file:///root/remote-bot-android/server/serverapi/nginx/Dockerfile) |
| [`serverapi/cloudflare-worker/`](file:///root/remote-bot-android/server/serverapi/cloudflare-worker) | **Cloudflare Workers (Node 24.22.0)** | Edge-прокси в один файл JavaScript, работающий через глобальную сеть Cloudflare. | [`Dockerfile`](file:///root/remote-bot-android/server/serverapi/cloudflare-worker/Dockerfile) |

---

## Быстрый старт через Docker Compose

Для запуска обоих сервисов в один шаг используйте [`docker-compose.yml`](file:///root/remote-bot-android/server/docker-compose.yml).

### Шаг 1. Подготовка переменных окружения

Скопируйте пример файла конфигурации из `docs/.env.example` в `server/.env`:
```bash
cp ../docs/.env.example .env
```

Заполните необходимые параметры в `.env`:
```ini
# Порты сервисов
PUSH_GATEWAY_PORT=8080
API_PROXY_PORT=8081

# Telegram
TELEGRAM_BOT_TOKEN=123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ
WHITELIST_CHAT_IDS=12345678,87654321
PUSH_PROVIDER=auto

# Google Firebase Cloud Messaging v1 (JSON сервисного аккаунта)
FIREBASE_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"...","private_key":"...","client_email":"..."}'

# Huawei Push Kit (HMS)
HUAWEI_APP_ID=105581234
HUAWEI_CLIENT_ID=105581234
HUAWEI_CLIENT_SECRET=your_huawei_client_secret

# Хост Telegram API
TARGET_HOST=api.telegram.org

# Выбор активного профиля (go / python / nginx / npx)
COMPOSE_PROFILES=go
```

### Шаг 2. Запуск контейнеров с профилями

`docker-compose.yml` содержит 4 изолированных профиля: `go`, `python`, `nginx`, `npx`.
Вы можете запустить нужный стек через ключ `--profile` или задать `COMPOSE_PROFILES=...` в файле `.env`:

```bash
# 1. Стек на Go (рекомендуется для продакшена: push-gateway-go + api-proxy-go)
docker compose --profile go up -d

# 2. Стек на Python (push-gateway-python + api-proxy-python)
docker compose --profile python up -d

# 3. Стек на Nginx / OpenResty (push-gateway-nginx + api-proxy-nginx)
docker compose --profile nginx up -d

# 4. Стек на Cloudflare Workers / Node (push-gateway-npx + api-proxy-npx)
docker compose --profile npx up -d
```

### Шаг 3. Проверка работоспособности

Проверьте статус запущенных контейнеров:
```bash
docker compose ps
```

Проверьте healthcheck endpoints:
```bash
# Push Gateway (порт 8080)
curl http://localhost:8080/health
# Ответ: {"status":"healthy","fcm":true,"hms":true}

# API Proxy (порт 8081)
curl http://localhost:8081/health
# Ответ: {"status":"healthy","target_host":"api.telegram.org"}
```

---

## Сборка Docker-образов

### Сборка всех 8 образов через Makefile
Из корневой папки проекта:
```bash
# Сборка всех 8 образов серверов одной командой
make docker-build-all

# Или раздельно:
make docker-build-push   # 4 варианта Push Gateway (Go, Python, Nginx, NPX)
make docker-build-api    # 4 варианта API Proxy (Go, Python, Nginx, NPX)

# Или через Docker Compose:
make docker-compose-build
```

### Сборка отдельных Docker-образов вручную:
Вы можете собрать любую конкретную реализацию под свои нужды:

### Push Gateway:
```bash
# Go версия (Pure Scratch, рекомендуется)
docker build -t remotebot-push-gateway:go -f serverpush/go/Dockerfile serverpush/go

# Python версия
docker build -t remotebot-push-gateway:python -f serverpush/python/Dockerfile serverpush/python

# OpenResty Nginx версия
docker build -t remotebot-push-gateway:nginx -f serverpush/nginx/Dockerfile serverpush/nginx

# Cloudflare Worker версия (локальный dev-сервер)
docker build -t remotebot-push-gateway:cf -f serverpush/cloudflare-worker/Dockerfile serverpush/cloudflare-worker
```

### API Reverse Proxy:
```bash
# Go версия (рекомендуется)
docker build -t remotebot-api-proxy:go -f serverapi/go/Dockerfile serverapi/go

# Python версия
docker build -t remotebot-api-proxy:python -f serverapi/python/Dockerfile serverapi/python

# Nginx версия
docker build -t remotebot-api-proxy:nginx -f serverapi/nginx/Dockerfile serverapi/nginx

# Cloudflare Worker версия
docker build -t remotebot-api-proxy:cf -f serverapi/cloudflare-worker/Dockerfile serverapi/cloudflare-worker
```

---

## Настройка Telegram Webhook на шлюз

Чтобы Telegram направлял входящие сообщения в ваш Push Gateway, зарегистрируйте вебхук:

```bash
curl -F "url=https://your-push-domain.com/tg/auto/<PUSH_TOKEN>/<SECRET_KEY>" \
     https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook
```

Где:
- `your-push-domain.com` — публичный домен вашего шлюза (с валидным SSL-сертификатом от Let's Encrypt / Cloudflare).
- `auto` — автоматический выбор провайдера (`fcm`, `hms` или `auto`).
- `<PUSH_TOKEN>` — push-токен устройства, полученный из приложения RemoteBot на вкладке Настройки/Debug.
- `<SECRET_KEY>` — ваш секретный ключ безопасности (параметр `PUSH_GATEWAY_SECRET`).

---

## Тестирование шлюзов

Из корневой папки проекта [`remote-bot-android/`](file:///root/remote-bot-android):
```bash
# Запуск тестов всех реализаций Push Gateway (Go, Python, JS)
make test-server

# Запуск тестов всех реализаций API Reverse Proxy (Go, Python, JS)
make test-serverapi
```

