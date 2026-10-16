[English](HMS_SETUP.md) | [Русский](HMS_SETUP_RU.md)

# Инструкция: Как получить свой Huawei HMS конфиг (`agconnect-services.json`) для Remote Bot

Данная инструкция описывает пошаговое создание проекта в Huawei AppGallery Connect (AGC) и получение файла конфигурации `agconnect-services.json` для работы функции **HMS Push Wakeup** (пробуждение бота через push-сообщения Huawei Mobile Services).

---

## 1. Регистрация и вход в AppGallery Connect

1. Перейдите на портал разработчиков Huawei: [Huawei Developers](https://developer.huawei.com/consumer/en/) (или [AppGallery Connect](https://developer.huawei.com/consumer/en/service/josp/agc/index.html)).
2. Зарегистрируйтесь или войдите под своим HUAWEI ID аккаунтом.
3. Откройте панель управления **AppGallery Connect** и выберите раздел **«Мои проекты» (My Projects)**.

---

## 2. Создание проекта и добавление приложения

1. Нажмите кнопку **«Добавить проект» (Add project)** и укажите название (например, `RemoteBot`).
2. Внутри созданного проекта нажмите **«Добавить приложение» (Add app)**:
   * **Тип платформы (Platform)**: `Android`
   * **Тип устройства (Device)**: `Phone` (Мобильный телефон)
   * **Название приложения (App name)**: `Remote Bot`
   * **Имя пакета (Package name)**:
     ```text
     com.remotebot.app
     ```
     > [!IMPORTANT]
     > Имя пакета должно **в точности совпадать** с `com.remotebot.app` (пакет нашего приложения).
   * **Категория приложения (App category)**: `App` (Приложение).
   * **Язык по умолчанию (Default language)**: `Russian` (или любой на выбор).
3. Нажмите **ОК**.

---

## 3. Включение Push Kit в службах проекта

1. В левом боковом меню проекта выберите **Manage APIs** (Управление API).
2. Найдите в списке службу **Push Kit** и переведите тумблер в положение **Включено (Enabled)**.
3. В левом меню перейдите в подраздел **Push Kit** $\to$ **Enable Push Service** (Включить службу Push) и подтвердите активацию.

---

## 4. Добавление SHA-256 отпечатка ключа (Критически важно!)

> [!WARNING]
> В отличие от Firebase, Huawei **обязательно** требует добавления отпечатка SHA-256 сертификата подписи APK. Если этого не сделать, SDK вернет ошибку `6003: CERT_FINGERPRINT_EMPTY` или `907135702`.

1. **Скопируйте отпечаток подписи вашего APK**:
   * **Для боевого Release APK (ключ `remotebot` / `release.jks`)**:
     ```text
     9F:1B:55:4A:BA:2B:E9:FA:43:6A:FC:36:A4:BF:D9:CD:93:6A:11:19:2D:5C:A3:D4:F0:B1:20:5B:D7:D4:6F:CC
     ```
   * **Для Debug APK (стандартный `debug.keystore`)**:
     ```text
     79:80:EC:EB:4A:F2:72:6B:FF:C2:89:6D:88:9D:B7:8D:11:8E:E6:FD:D3:10:66:E6:2D:68:A3:D4:9C:2C:CF:EC
     ```
   > [!TIP]
   > В консоли Huawei AppGallery Connect можно добавить **оба** отпечатка (кнопка `+`), чтобы пуши доходили и до Release APK, и до Debug-версий при разработке.
   * **Если собирали сами со своим ключом подписи**:
     * Из debug keystore:
       ```bash
       keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep "SHA256:"
       ```
     * Из готового Release APK (пароль от хранилища не требуется):
       ```bash
       apksigner verify --print-certs app-release.apk | grep "SHA-256"
       ```
     * Из файла Release Keystore (`.jks` / `.keystore`):
       ```bash
       keytool -list -v -keystore /path/to/release.jks -alias your_alias | grep "SHA256:"
       ```
2. В консоли AppGallery Connect перейдите в **Project Settings** (Настройки проекта) $\to$ вкладка **General Information** (Общие сведения).
3. Прокрутите страницу вниз до блока **App Information** (Информация о приложении).
4. Найдите строку **SHA-256 certificate fingerprint** (Сертификат SHA-256).
5. Нажмите иконку **+** (или галочку) и вставьте ваш SHA-256 отпечаток.
6. Нажмите **Сохранить** (галочка подтверждения).

---

## 5. Скачивание `agconnect-services.json`

1. На той же странице **Project Settings** $\to$ **General Information** в блоке **App Information** найдите кнопку:
   **«agconnect-services.json»** (кнопка скачивания файла конфигурации).
2. Скачайте файл `agconnect-services.json` на компьютер или телефон.

---

## 6. Загрузка конфигурации в приложение Remote Bot

1. Откройте приложение **Remote Bot** на смартфоне.
2. В левом боковом меню (NavigationRail) перейдите во вкладку **🔔 Push**.
3. В верхнем блоке выбора провайдера выберите **🦅 Huawei HMS** (или **⚡ Гибридный**).
4. Загрузите конфигурацию одним из двух способов:
   * **Способ А (Файл)**: Нажмите **«📁 Выбрать файл»** и выберите скачанный `agconnect-services.json`.
   * **Способ Б (Текст)**: Скопируйте текст из `agconnect-services.json` в буфер обмена и нажмите кнопку **«📋 Из буфера»**.
5. Нажмите кнопку **«⚡ Применить и инициализировать HMS»**.
6. Приложение распарсит параметры (`app_id`, `client_id`, `api_key`), инициализирует `AGConnectInstance` и запросит **HMS Device Token**:
   * Зеленый статус: `✅ Huawei HMS готов! Токен: ...`
   * Отобразится карточка с токеном устройства и кнопка быстрого копирования в буфер обмена.

---

## 7. Решение возможных проблем

| Код / Ошибка | Причина | Решение |
| :--- | :--- | :--- |
| `6003: CERT_FINGERPRINT_EMPTY` | В консоли AGC не указан SHA-256 отпечаток ключа подписи | Добавьте SHA-256 вашего `debug.keystore` (или release-ключа) в AGC Console по шагу 4, скачайте обновленный JSON. |
| `907135702` | Несоответствие SHA-256 отпечатка или имени пакета | Проверьте, что в AGC указан именно пакет `com.remotebot.app` и актуальный SHA-256 ключа, которым подписан установленный APK. |
| `HMS Core is not available` | На смартфоне другого вендора (Xiaomi, Samsung) не установлена служба HMS Core | Установите приложение **HMS Core** (`com.huawei.hwid`) из Huawei AppGallery или используйте Google FCM. |
| `Токен пуст (null)` | Служба Push Kit выключена в проекте | Зайдите в AGC $\to$ Manage APIs и убедитесь, что тумблер **Push Kit** включен (Шаг 3). |
