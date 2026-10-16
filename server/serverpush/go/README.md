# RemoteBot Push Gateway (Go)

Легковесный высокопроизводительный микросервис Webhook $\to$ Push Wakeup Gateway на Go (поддерживает Google FCM и Huawei HMS Push Kit).

## Переменные окружения
* `PORT` — порт HTTP сервера (по умолчанию `8080`).
* `PUSH_GATEWAY_SECRET` — секретный ключ для авторизации вебхуков (пароль шлюза).

### Для Google Firebase (FCM):
* `FIREBASE_SERVICE_ACCOUNT` — JSON сервисного аккаунта (FCM v1).
* `GOOGLE_APPLICATION_CREDENTIALS` — альтернативно: путь к файлу `serviceAccount.json`.
* `FCM_LEGACY_SERVER_KEY` — ключ сервера Firebase Legacy (опциональный фоллбэк).

### Для Huawei HMS Push:
* `HUAWEI_APP_ID` — идентификатор приложения из `agconnect-services.json`.
* `HUAWEI_CLIENT_ID` — идентификатор клиента (обычно равен `HUAWEI_APP_ID`).
* `HUAWEI_CLIENT_SECRET` — секрет из `agconnect-services.json`.

## Маршруты
* `POST /tg/:token/:secretKey` — автовыбор провайдера (или через `?provider=hms`/`fcm`).
* `POST /tg/fcm/:fcmToken/:secretKey` — доставка через Google FCM.
* `POST /tg/hms/:hmsToken/:secretKey` — доставка через Huawei Push Kit.
* `GET /health` — проверка статуса готовности (`{"status":"healthy","fcm":true,"hms":true}`).

## Запуск
```bash
go run main.go
```

## Тесты
```bash
go test -v ./...
```
