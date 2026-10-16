[English](README.md) | [Русский](README_RU.md)

# RemoteBot Push Gateway (`serverpush`)

Серверный шлюз пробуждения (Push Gateway) для Android-бота RemoteBot.
Принимает входящие Webhook-уведомления от серверов Telegram и отправляет пробуждающий push-сигнал (`Wakeup Data Push`) через **Google Firebase Cloud Messaging (FCM)** и/или **Huawei Push Kit (HMS)** на устройство.

---

## Варианты реализации

| Вариант | Папка | Описание |
| :--- | :--- | :--- |
| **Cloudflare Worker** | [`cloudflare-worker/`](./cloudflare-worker/) | **Рекомендуемый бессерверный вариант**. Работает без своего VPS. Бесплатный SSL и домен Cloudflare. |
| **Go Daemon** | [`go/`](./go/) | Скомпилированный бинарник для Linux VPS. Поддержка горутин и параллельной доставки. |
| **Python Daemon** | [`python/`](./python/) | Zero-dependency скрипт для Linux VPS / Docker (требуется только стандартный Python 3). |
| **Nginx (OpenResty / Lua)** | [`nginx/`](./nginx/) | Шлюз прямо внутри Nginx без внешних демонов (требует OpenResty или пересборку Nginx с `lua-nginx-module`). |

---

## Сетевой контракт API

- **Healthcheck**: `GET /health` $\to$ `{"status":"healthy","fcm":true,"hms":true}` (HTTP 200).
- **FCM маршрут**: `POST /tg/fcm/:token/:secretKey`
- **HMS маршрут**: `POST /tg/hms/:token/:secretKey`
- **Авто-выбор**: `POST /tg/:token/:secretKey`
- **Выбор через query**: `POST /tg/:token/:secretKey?provider=hms` (или `?provider=fcm`)

Тело запроса: стандартный JSON Telegram Webhook с полем `update_id`.

---

## 1. Деплой через Cloudflare Worker (Без VPS)

Подробное пошаговое руководство со скриншотами находится в [`docs/CLOUDFLARE_WORKER_SETUP.md`](../../docs/CLOUDFLARE_WORKER_SETUP.md).

### Кратко:
1. Зайти в [Cloudflare Dashboard](https://dash.cloudflare.com/) $\to$ **Compute (Workers & Pages)** $\to$ **Create Worker**.
2. Вставить код из [`cloudflare-worker/worker.js`](./cloudflare-worker/worker.js).
3. Во вкладке **Settings** $\to$ **Variables and Secrets** задать:
   - `PUSH_GATEWAY_SECRET`: секретный ключ шлюза (придумайте пароль).
   - `FIREBASE_SERVICE_ACCOUNT`: содержимое приватного JSON-ключа Firebase Service Account (опционально для FCM).
   - `HUAWEI_APP_ID`, `HUAWEI_CLIENT_ID`, `HUAWEI_CLIENT_SECRET`: параметры приложения Huawei AppGallery Connect (опционально для HMS).
4. Нажать **Deploy**.

---

## 2. Деплой Go демона на Linux VPS

### Сборка и запуск:
```bash
cd serverpush/go
go build -o /usr/local/bin/remotebot-push-gateway main.go
```

### Переменные окружения:
- `PORT`: порт HTTP-сервера (по умолчанию: `8080`).
- `PUSH_GATEWAY_SECRET`: секретный ключ шлюза.
- `FIREBASE_SERVICE_ACCOUNT`: JSON-строка ключа Firebase или путь к файлу.
- `HUAWEI_APP_ID`, `HUAWEI_CLIENT_ID`, `HUAWEI_CLIENT_SECRET`: учетные данные Huawei HMS.

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

## 3. Деплой Python демона на Linux VPS

### Запуск:
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

## 4. Настройка Nginx перед Go / Python шлюзом (SSL Termination)

Для работы Telegram Webhook требуется валидный публичный HTTPS URL. Рекомендуется настроить стандартный Nginx перед Go или Python сервисом:

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

## 5. Вариант шлюза прямо внутри Nginx (OpenResty / Lua)

### Возможно ли запустить Push Gateway внутри Nginx без Go/Python?
**На стандартном чистом Nginx — нет**, потому что Nginx "из коробки" умеет только перенаправлять байты (как в `serverapi`), но не умеет:
1. Подписывать **RS256 JWT** токены приватным RSA-ключом и обменивать их на Google OAuth2 `access_token` (`https://oauth2.googleapis.com/token`);
2. Выполнять HTTP POST запрос в Huawei OAuth (`https://oauth-login.cloud.huawei.com/oauth2/v2/token`);
3. Парсить входящий JSON Telegram (`update_id`) и формировать новый JSON Data Push для FCM/HMS.

**Но это можно сделать, пересобрав Nginx с модулем `lua-nginx-module` (или установив OpenResty).**

Готовые файлы находятся в папке [`nginx/`](./nginx/):
- [`nginx/gateway.lua`](./nginx/gateway.lua) — полный скрипт авторизации в Google/Huawei и отправки пушей;
- [`nginx/nginx.conf`](./nginx/nginx.conf) — конфигурация OpenResty / Nginx.

---

### Как пересобрать Nginx с поддержкой Lua:

#### Способ 1: Установка готового OpenResty (Рекомендуется, без долгой компиляции)

На Debian / Ubuntu:
```bash
sudo apt-get update
sudo apt-get install -y wget gnupg ca-certificates lsb-release
wget -O - https://openresty.org/package/pubkey.gpg | sudo gpg --dearmor -o /usr/share/keyrings/openresty.gpg
echo "deb [signed-by=/usr/share/keyrings/openresty.gpg] http://openresty.org/package/ubuntu $(lsb_release -sc) main" | sudo tee /etc/apt/sources.list.d/openresty.list
sudo apt-get update
sudo apt-get install -y openresty luarocks

# Установка необходимых библиотек для JWT и HTTPS запросов
sudo luarocks install lua-resty-http
sudo luarocks install lua-resty-jwt
sudo luarocks install lua-cjson
```

#### Способ 2: Ручная сборка Nginx из исходников с `lua-nginx-module`

1. **Установка сборочных зависимостей**:
   ```bash
   sudo apt-get install -y build-essential libpcre3 libpcre3-dev zlib1g zlib1g-dev libssl-dev libluajit-5.1-dev luarocks
   ```

2. **Загрузка исходников**:
   ```bash
   cd /tmp
   # Загрузка Nginx
   wget http://nginx.org/download/nginx-1.24.0.tar.gz
   tar -zxvf nginx-1.24.0.tar.gz

   # Загрузка модулей Lua и Devel Kit
   git clone https://github.com/vision5/ngx_devel_kit.git
   git clone https://github.com/openresty/lua-nginx-module.git
   ```

3. **Конфигурация и компиляция**:
   ```bash
   cd /tmp/nginx-1.24.0
   export LUAJIT_LIB=/usr/lib/x86_64-linux-gnu
   export LUAJIT_INC=/usr/include/luajit-2.1

   ./configure \
       --prefix=/etc/nginx \
       --sbin-path=/usr/sbin/nginx \
       --modules-path=/usr/lib/nginx/modules \
       --conf-path=/etc/nginx/nginx.conf \
       --error-log-path=/var/log/nginx/error.log \
       --http-log-path=/var/log/nginx/access.log \
       --with-http_ssl_module \
       --with-http_v2_module \
       --add-module=/tmp/ngx_devel_kit \
       --add-module=/tmp/lua-nginx-module

   make -j$(nproc)
   sudo make install
   ```

4. **Установка Lua-библиотек**:
   ```bash
   sudo luarocks install lua-resty-http
   sudo luarocks install lua-resty-jwt
   sudo luarocks install lua-cjson
   ```

---

### Деплой конфигурации:

1. Скопируйте [`nginx/gateway.lua`](./nginx/gateway.lua) в `/etc/nginx/remotebot/gateway.lua`.
2. Скопируйте [`nginx/nginx.conf`](./nginx/nginx.conf) в `/etc/nginx/conf.d/remotebot-push.conf`.
3. Задайте системные переменные окружения в `/etc/environment` или в systemd unit `nginx.service`:
   ```bash
   PUSH_GATEWAY_SECRET="ваш_секретный_ключ"
   FIREBASE_SERVICE_ACCOUNT="/etc/remotebot/firebase.json"
   HUAWEI_APP_ID="12345678"
   HUAWEI_CLIENT_ID="12345678"
   HUAWEI_CLIENT_SECRET="секрет_huawei"
   ```
4. Проверьте синтаксис и перезапустите:
   ```bash
   sudo nginx -t
   sudo systemctl reload nginx
   ```

---

## Запуск тестов

Из корня проекта:
```bash
make test-serverpush-go   # Тест Go шлюза
make test-serverpush-py   # Тест Python шлюза
make test-serverpush-js   # Тест Cloudflare Worker
make test-serverpush      # Запуск всех 3 тестов
```
