[English](README.md) | [Русский](README_RU.md)

# RemoteBot Push Gateway (Cloudflare Worker — Hybrid FCM & HMS)

Бессерверный (Serverless) шлюз на Cloudflare Workers с поддержкой двух провайдеров:
* **Google Firebase Cloud Messaging (FCM HTTP v1 & Legacy)**
* **Huawei Mobile Services Push Kit (HMS REST API v1 с OAuth2)**
* Бесплатный тариф Cloudflare включает 100 000 запросов в сутки.
* Не требует VPS или отдельного сервера.

---

## 1. Секреты и параметры (Settings -> Variables and Secrets)

В панели Cloudflare Worker настройте необходимые переменные:

### Общий секрет (Обязательно):
* **`PUSH_GATEWAY_SECRET`**: ваш секретный пароль шлюза для защиты от спама.

### Для работы Google Firebase (FCM):
* **`FIREBASE_SERVICE_ACCOUNT`**: содержимое файла `serviceAccount.json` из Firebase Console (*Project Settings $\to$ Service accounts $\to$ Generate new private key*).

### Для работы Huawei HMS Push:
* **`HUAWEI_APP_ID`**: идентификатор приложения (поле `app_id` из `agconnect-services.json` или AppGallery Connect, например `119367373`).
* **`HUAWEI_CLIENT_ID`**: идентификатор клиента OAuth2 для сервера Push Kit. Укажите то же значение, что и **`HUAWEI_APP_ID`** (например `119367373`).
* **`HUAWEI_CLIENT_SECRET`**: серверный ключ **App Secret** (он же Push Secret) из консоли AppGallery Connect (*Мои проекты $\to$ Выбрать проект $\to$ Настройки проекта / Push Kit $\to$ Сведения о приложении $\to$ ID клиента OAuth 2.0 $\to$ Секрет приложения / App Secret*). **Важно:** это НЕ поле `client_secret` из файла `agconnect-services.json` (в json указан клиентский ключ SDK, а для вызова Push API серверу требуется именно **App Secret**).

---

## 2. Развертывание в Cloudflare Workers

1. Откройте [Cloudflare Dashboard](https://dash.cloudflare.com/) $\to$ **Workers & Pages** $\to$ **Create application** $\to$ **Create Worker**.
2. Задайте имя (например `remotebot-push-gateway`) и нажмите **Deploy**.
3. Нажмите **Edit code**, вставьте содержимое [worker.js](worker.js) и нажмите **Save and deploy**.
4. В разделе **Settings** $\to$ **Variables and Secrets** добавьте указанные выше секреты.
5. Нажмите **Deploy**.

---

## 3. Формат маршрутов Webhook в Telegram

Шлюз поддерживает автоматическую или явную маршрутизацию:

| URL маршрута | Провайдер | Назначение |
| :--- | :--- | :--- |
| `https://<worker>/tg/:token/:secretKey` | Автоматически | Если настроен только FCM $\to$ шлет FCM. Если только HMS $\to$ шлет HMS. |
| `https://<worker>/tg/fcm/:fcmToken/:secretKey` | Google FCM | Явная отправка через Firebase FCM. |
| `https://<worker>/tg/hms/:hmsToken/:secretKey` | Huawei HMS | Явная отправка через Huawei Push Kit. |
| `https://<worker>/tg/hybrid/:fcmToken+hmsToken/:secretKey` | Гибридный (FCM + HMS) | Одновременная отправка пуша и в Google FCM, и в Huawei HMS (Dual-Push). |

---

## 4. Проверка работоспособности (Healthcheck)

Откройте в браузере:
```text
https://remotebot-push-gateway.<subdomain>.workers.dev/health
```
Ответ:
```json
{
  "status": "healthy",
  "fcm": true,
  "hms": true
}
```
