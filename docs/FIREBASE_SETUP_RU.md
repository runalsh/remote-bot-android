[English](FIREBASE_SETUP.md) | [Русский](FIREBASE_SETUP_RU.md)

# Инструкция: Как получить свой Firebase конфиг (`google-services.json`) для Remote Bot

Данная инструкция описывает пошаговое создание личного проекта в Google Firebase и получение файла конфигурации `google-services.json` для работы функции **FCM Push Wakeup** (пробуждение бота через push-сообщения).

---

## 1. Регистрация и создание проекта в Firebase

1. Перейдите в [Firebase Console](https://console.firebase.google.com/) и войдите под вашим Google-аккаунтом.
2. Нажмите кнопку **«Добавить проект» (Add project)** или **«Создать проект» (Create project)**.
3. Введите название проекта (например, `MyRemoteBot` или любое произвольное).
4. Нажмите **Продолжить (Continue)**.
5. Пункт *«Включить Google Аналитику»*: можно отключить (тумблер выключен) для ускорения создания и приватности, затем нажмите **«Создать проект» (Create project)**.
6. Дождитесь окончания создания и нажмите **Продолжить**.

---

## 2. Добавление Android-приложения в проект

1. На главном экране панели управления проекта (Project Overview) нажмите на круглую иконку **Android** (добавить приложение).
2. Заполните обязательные поля:
   * **Имя пакета Android (Android package name)**:
     ```text
     com.remotebot.app
     ```
     > [!IMPORTANT]
     > Имя пакета должно **в точности совпадать** с `com.remotebot.app` (пакет нашего приложения).
   * **Псевдоним приложения (App nickname)**: произвольно (например, `Remote Bot`).
   * **Сертификат подписи отладки SHA-1 (Debug signing certificate SHA-1)**: необязательно для базового получения пушей, можно оставить пустым.
3. Нажмите синюю кнопку **«Зарегистрировать приложение» (Register app)**.

---

## 3. Скачивание `google-services.json`

1. На следующем шаге («Скачайте файл конфигурации») нажмите кнопку **«Скачать google-services.json» (Download google-services.json)**.
2. Файл сохранится на ваш компьютер или телефон.
3. В консоли Firebase нажмите **«Далее» (Next)**, затем еще раз **«Далее»**, и в конце — **«Перейти в консоль» (Continue to console)**.

---

## 4. Включение необходимых Google API (Критически важно!)

Чтобы регистрация устройства и получение FCM-токена не завершались ошибкой `Firebase Installations Service is unavailable`, включите системные API проекта:

1. Откройте библиотеку Google Cloud Console для вашего проекта:
   * **Firebase Installations API**:
     👉 [https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com](https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com)
     *(Выберите вверху созданный проект и нажмите **«Включить» (Enable)**)*.
   * **Cloud Messaging API**:
     👉 [https://console.cloud.google.com/apis/library/fcm.googleapis.com](https://console.cloud.google.com/apis/library/fcm.googleapis.com)
     *(Нажмите **«Включить» (Enable)**)*.

---

## 5. Загрузка конфигурации в приложение Remote Bot

1. Откройте приложение **Remote Bot** на устройстве.
2. В боковом меню (NavigationRail) перейдите во вкладку **🔥 FCM**.
3. Добавьте конфигурацию одним из двух способов:
   * **Способ А (Файл)**: Нажмите **«📁 Выбрать файл»** и укажите скачанный `google-services.json`.
   * **Способ Б (Текст)**: Откройте `google-services.json` в любом текстовом редакторе, скопируйте его содержимое в буфер обмена и нажмите кнопку **«📋 Из буфера»**.
4. Нажмите оранжевую кнопку **«⚡ Применить и инициализировать Firebase»**.
5. Приложение выполнит парсинг параметров, подключит `FirebaseApp` и запросит **FCM Device Token**:
   * Зеленый статус: `✅ Firebase успешно инициализирован!`
   * Отобразится карточка с токеном устройства и кнопкой «📋 Копировать токен».
   * Данный токен и URL вашего Push Gateway используются для пробуждения бота без постоянного удержания TCP-сокета.

---

## 6. Решение возможных проблем

| Проблема / Ошибка | Причина | Решение |
| :--- | :--- | :--- |
| `Таймаут (12 сек) ожидания Google Play Services / FCM` | Блокировка серверов Google (РКН) или отсутствие Play Services | Включить VPN на устройстве/эмуляторе либо использовать эмулятор с **Google APIs / Google Play**. |
| `Firebase Installations Service is unavailable` | В Google Cloud не включен API инсталляций | Включить [Firebase Installations API](https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com) по инструкции из п. 4. |
| `Не найден клиент для пакета com.remotebot.app` | При создании приложения в Firebase указан чужой package name | В Firebase Console добавить Android-приложение именно с пакетом `com.remotebot.app` и скачать обновленный JSON. |
