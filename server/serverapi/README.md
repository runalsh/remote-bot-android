# Telegram Bot API Reverse Proxy (`serverapi`)

В данной папке находятся реализации обратного прокси-сервера (Reverse Proxy) для перенаправления запросов Android-приложения к `api.telegram.org`.

## Зачем это нужно?
1. **Обход сетевых блокировок и цензуры**: если провайдер блокирует прямые подключения к `api.telegram.org`.
2. **Единый адрес зеркала**: возможность указать свой домен в настройках приложения (раздел «Telegram» -> «Кастомный API Root / Зеркала»).
3. **Сохранение полного функционала**: проксируется весь трафик (GET, POST, передача файлов, Long Polling `getUpdates`).

---

## Варианты реализации

| Вариант | Папка | Описание | Нужен ли Nginx? |
| :--- | :--- | :--- | :--- |
| **Nginx (Pure Proxy)** | [`nginx/`](./nginx/) | **Самый быстрый и легкий вариант**. Не требует запуска runtime (Node/Python/Go). Nginx сам проксирует весь трафик на `api.telegram.org` по HTTPS. | **Да** (сам Nginx и есть сервер) |
| **Cloudflare Worker** | [`cloudflare-worker/`](./cloudflare-worker/) | Бессерверный вариант без своего VPS/сервера. Бесплатный SSL и домен `*.workers.dev`. | **Нет** (работает в облаке Cloudflare) |
| **Go Daemon** | [`go/`](./go/) | Скомпилированный бинарник без внешних зависимостей. Высокая производительность. | Опционально (перед Go как reverse proxy для HTTPS) |
| **Python Daemon** | [`python/`](./python/) | Zero-dependency скрипт на стандартной библиотеке Python 3. | Опционально (перед Python как reverse proxy для HTTPS) |

---

## 1. Вариант Nginx (Рекомендуемый для VPS, без кода)

Можно обойтись **только одним Nginx**, не запуская никаких демонов на Go, Python или Node.js.

### Пошаговая настройка:
1. Скопируйте готовый конфигурационный файл [`nginx/nginx.conf`](./nginx/nginx.conf) на ваш VPS в `/etc/nginx/sites-available/tg-proxy.conf`.
2. Замените `tg-api.yourdomain.com` на ваш реальный домен.
3. Выпустите бесплатный SSL-сертификат через Certbot:
   ```bash
   sudo certbot --nginx -d tg-api.yourdomain.com
   ```
4. Активируйте сайт и перезапустите Nginx:
   ```bash
   sudo ln -s /etc/nginx/sites-available/tg-proxy.conf /etc/nginx/sites-enabled/
   sudo nginx -t
   sudo systemctl reload nginx
   ```
5. В приложении RemoteBot в разделе **«Telegram»** укажите:
   `https://tg-api.yourdomain.com`

---

## 2. Вариант Cloudflare Worker (Без собственного сервера)

1. Откройте дашборд [Cloudflare Workers](https://dash.cloudflare.com/).
2. Создайте новый Worker (например, `my-tg-api-proxy`).
3. Вставьте содержимое [`cloudflare-worker/worker.js`](./cloudflare-worker/worker.js):
   ```javascript
   export default {
     async fetch(request) {
       const url = new URL(request.url);
       url.hostname = "api.telegram.org";
       return fetch(url.toString(), request);
     },
   };
   ```
4. Нажмите **Deploy**.
5. Полученный URL (например, `https://my-tg-api-proxy.yourname.workers.dev`) вставьте в приложение RemoteBot в поле «Кастомный API Root».

---

## 3. Вариант Go Standalone

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

Если нужно закрыть HTTPS через Nginx:
```nginx
location / {
    proxy_pass http://127.0.0.1:8081;
    proxy_set_header Host $host;
    proxy_buffering off;
    proxy_read_timeout 90s;
}
```

---

## 4. Вариант Python Standalone

```bash
cd serverapi/python
PORT=8081 python3 main.py
```
Аналогично настраивается systemd unit и проксирование Nginx на `http://127.0.0.1:8081`.

---

## Запуск тестов

Из папки `newapp`:
```bash
make test-serverapi-go   # Тест Go прокси
make test-serverapi-py   # Тест Python прокси
make test-serverapi-js   # Тест Cloudflare Worker
make test-serverapi      # Все 3 теста
```

