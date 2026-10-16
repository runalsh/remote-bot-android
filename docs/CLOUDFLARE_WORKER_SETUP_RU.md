[English](CLOUDFLARE_WORKER_SETUP.md) | [Русский](CLOUDFLARE_WORKER_SETUP_RU.md)

# Инструкция: Деплой гибридного Push Gateway на Cloudflare Workers

Данная инструкция описывает пошаговое развертывание бессерверного (serverless) шлюза **RemoteBot Push Gateway** на бесплатной платформе **Cloudflare Workers**. 

Шлюз принимает входящие вебхуки от Telegram и мгновенно отправляет пробуждающий **Data Push** (через **Google FCM** или **Huawei HMS Push**) на ваш смартфон. Это позволяет боту мгновенно просыпаться и обрабатывать команды, экономя до 90% заряда батареи.

---

## 1. Преимущества Cloudflare Workers
* **Полностью бесплатно**: до 100 000 входящих запросов в сутки на бесплатном тарифе (хватит на тысячи сообщений в день).
* **Не нужен свой сервер/VPS**: работает в глобальной сети Cloudflare Edge с задержкой 10–30 мс.
* **Двойная поддержка**: отправляет пуши на устройства с Google сервисами (FCM) и на устройства Huawei/Honor (HMS).

---

## 2. Способ А: Деплой через веб-интерфейс (Без терминала, прямо из браузера)

### Шаг 1: Создание воркера
1. Войдите в панель управления [Cloudflare Dashboard](https://dash.cloudflare.com/) (или зарегистрируйтесь бесплатно).
2. В левом боковом меню перейдите в раздел **Compute (Workers & Pages)**.
3. Нажмите кнопку **Create application** $\to$ вкладка **Workers** $\to$ нажмите **Create Worker**.
4. Задайте имя воркера (например, `remotebot-push-gateway`) и нажмите синюю кнопку **Deploy**.

### Шаг 2: Вставка исходного кода
1. На странице созданного воркера нажмите кнопку **Edit code** (Редактировать код).
2. Удалите стандартный пример кода в редакторе.
3. Откройте файл [`worker.js`](../serverpush/cloudflare-worker/worker.js), скопируйте весь его текст целиком и вставьте в редактор Cloudflare.
4. В правом верхнем углу нажмите **Save and deploy** (Сохранить и развернуть).

### Шаг 3: Настройка переменных и секретов
1. Вернитесь на главную страницу воркера и перейдите во вкладку **Settings** $\to$ подраздел **Variables and Secrets** (Переменные и секреты).
2. Нажмите **Add** для добавления нужных параметров:

| Имя переменной | Тип | Описание | Откуда взять |
| :--- | :--- | :--- | :--- |
| `PUSH_GATEWAY_SECRET` | Secret (Зашифровано) | Ваш секретный пароль шлюза для защиты от спама | Придумайте любую надежную строку (например: `my_secret_key_123`) |
| `FIREBASE_SERVICE_ACCOUNT` | Secret (Зашифровано) | *(Опционально)* Закрытый ключ сервисного аккаунта Firebase | Firebase Console $\to$ Project Settings $\to$ Service accounts $\to$ Generate private key. Вставить текст скачанного `.json` файла целиком |
| `HUAWEI_APP_ID` | Variable / Secret | *(Опционально)* Идентификатор приложения Huawei | Поле `app_id` из вашего файла `agconnect-services.json` |
| `HUAWEI_CLIENT_ID` | Variable / Secret | *(Опционально)* Идентификатор клиента Huawei | Поле `client_id` (обычно совпадает с `HUAWEI_APP_ID`) |
| `HUAWEI_CLIENT_SECRET` | Secret (Зашифровано) | *(Опционально)* Секретный ключ Huawei | Поле `client_secret` из вашего файла `agconnect-services.json` |

> [!TIP]
> Если вы используете только Google FCM — достаточно указать `FIREBASE_SERVICE_ACCOUNT`.  
> Если только Huawei — укажите три переменные `HUAWEI_*`.  
> Если нужны оба провайдера — укажите все переменные, шлюз будет поддерживать обоих.

3. После добавления переменных нажмите **Deploy** для применения изменений.

---

## 3. Способ Б: Деплой через CLI (Wrangler)

Если у вас установлен Node.js и утилита Wrangler:

```bash
cd server/serverpush/cloudflare-worker

# Авторизация в Cloudflare
npx wrangler login

# Установка секретов
npx wrangler secret put PUSH_GATEWAY_SECRET
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT   # при использовании FCM
npx wrangler secret put HUAWEI_CLIENT_SECRET       # при использовании HMS

# Публикация воркера
npx wrangler deploy
```

---

## 4. Проверка работоспособности (Healthcheck)

После публикации откройте адрес вашего воркера со страницей `/health` в браузере:
```text
https://remotebot-push-gateway.<ваш-поддомен>.workers.dev/health
```

Ответ должен быть в формате JSON:
```json
{
  "status": "healthy",
  "fcm": true,
  "hms": true
}
```
*(Значения `true`/`false` отражают, какие провайдеры настроены и готовы отправлять пуши)*.

---

## 5. Настройка в приложении Remote Bot

1. Откройте приложение **Remote Bot** на смартфоне.
2. Перейдите во вкладку **🔔 Push** (в левом меню NavigationRail).
3. Прокрутите экран вниз до карточки **⚙️ Настройки Push Gateway (Шлюз)**:
   * **Push Gateway URL**: вставьте URL вашего воркера:
     ```text
     https://remotebot-push-gateway.<ваш-поддомен>.workers.dev
     ```
   * **Push Secret Key**: введите тот же секрет, что вы указали в `PUSH_GATEWAY_SECRET` (например, `my_secret_key_123`).
   * Переведите переключатель **Режим Push Wakeup** во **Включено** (зеленый).
   * Нажмите кнопку **«Сохранить параметры Push Gateway»**.

---

## 6. Как устроен поток вызовов

```
[Пользователь] 
      │ 
      ▼ (отправляет команду в Telegram)
[Telegram Bot Servers]
      │ 
      ▼ (Webhook POST /tg/:token/:secretKey)
[Cloudflare Worker Gateway]
      │ 
      ▼ (отправляет легкий Data Push "wakeup" ~50 байт)
[Google FCM / Huawei HMS]
      │ 
      ▼ (пробуждает Android-приложение)
[Remote Bot Service на смартфоне]
      │ 
      ▼ (забирает апдейт из пуша или опрашивает Telegram)
[Отправка ответа пользователю]
```

---

## 7. Решение частых проблем

| Симптом | Причина | Решение |
| :--- | :--- | :--- |
| `HTTP 401 Unauthorized` | Не совпадает секретный ключ | Проверьте, что ключ в приложении Remote Bot точно совпадает с переменной `PUSH_GATEWAY_SECRET` в Cloudflare. |
| `HTTP 400 Invalid path` | Неверный формат пути | Убедитесь, что Telegram отправляет запросы на `/tg/:token/:secretKey` или `/tg/hms/:token/:secretKey`. |
| Пуш не доходит на Huawei | Ошибка OAuth Huawei | Проверьте правильность `HUAWEI_APP_ID` и `HUAWEI_CLIENT_SECRET` в секретах воркера. В логах воркера (вкладка Logs) будет видна ошибка от Huawei. |
| Пуш не доходит на Google | Ошибка Google OAuth | Убедитесь, что `FIREBASE_SERVICE_ACCOUNT` содержит валидный JSON сервисного аккаунта с закрытым ключом `private_key`. |
