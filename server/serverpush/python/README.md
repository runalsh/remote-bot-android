# RemoteBot Push Gateway (Python)

Легковесный шлюз Telegram Webhook $\to$ Push Wakeup на Python (поддерживает Google FCM и Huawei HMS Push Kit).
Работает со стандартной библиотекой Python 3.10+ (для генерации JWT Google FCM v1 используется пакет `cryptography`).

## Переменные окружения
* `PORT` — порт сервера (по умолчанию `8080`).
* `PUSH_GATEWAY_SECRET` — секретный ключ для авторизации вебхуков (пароль шлюза).

### Для Google Firebase (FCM):
* `FIREBASE_SERVICE_ACCOUNT` — JSON сервисного аккаунта (FCM v1).
* `GOOGLE_APPLICATION_CREDENTIALS` — путь к файлу `serviceAccount.json`.
* `FCM_LEGACY_SERVER_KEY` — ключ сервера Firebase Legacy (опциональный фоллбэк).

### Для Huawei HMS Push:
* `HUAWEI_APP_ID` — идентификатор приложения из `agconnect-services.json`.
* `HUAWEI_CLIENT_ID` — идентификатор клиента (обычно равен `HUAWEI_APP_ID`).
* `HUAWEI_CLIENT_SECRET` — секрет из `agconnect-services.json`.

## Маршруты
* `POST /tg/:token/:secretKey` — автовыбор провайдера.
* `POST /tg/fcm/:fcmToken/:secretKey` — доставка через Google FCM.
* `POST /tg/hms/:hmsToken/:secretKey` — доставка через Huawei Push Kit.
* `GET /health` — проверка статуса (`{"status":"healthy","fcm":true,"hms":true}`).

## Запуск
```bash
python3 main.py
```

## Тесты
```bash
python3 -m unittest -v test_main.py
```
