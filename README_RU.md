# Remote Bot for Telegram

<p align="center">
  <strong>Автономный, приватный сервер удаленного администрирования, видеонаблюдения и автоматизации на Android с управлением через Telegram и SMS.</strong>
</p>

<p align="center">
  <a href="README.md">🇬🇧 Read in English (English version)</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Платформа-Android_8.0+_--_15_(API_26--35)-brightgreen.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Язык-Kotlin_2.0-purple.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-blue.svg" alt="Compose" />
  <img src="https://img.shields.io/badge/Лицензия-Apache_2.0-blue.svg" alt="License" />
  <img src="https://github.com/runalsh/remote-bot-android/actions/workflows/ci.yml/badge.svg" alt="CI Status" />
  <img src="https://github.com/runalsh/remote-bot-android/actions/workflows/docker.yml/badge.svg" alt="Docker Status" />
</p>

---

## 📖 Оглавление

- [Обзор и философия проекта](#-обзор-и-философия-проекта)
- [Скриншоты интерфейса](#-скриншоты-интерфейса)
- [Основной функционал и возможности](#-основной-функционал-и-возможности)
- [Документация и руководства (`docs/`)](#-документация-и-руководства-docs)
- [Серверная инфраструктура и Docker (`server/`)](#-серверная-инфраструктура-и-docker-server)
- [Сборка из исходников](#-сборка-из-исходников)
- [Шпаргалка по командам Makefile](#-шпаргалка-по-командам-makefile)
- [CI/CD и контроль качества](#-cicd-и-контроль-качества)
- [Лицензия](#-лицензия)

---

## 🌟 Обзор и философия проекта

**Remote Bot for Telegram** превращает любое Android-устройство (смартфон, планшет, ТВ-приставку или эмулятор) в полноценный защищенный сервер удаленного контроля. Вы можете управлять устройством через персональный чат в Telegram или с помощью резервных SMS-команд в оффлайне.

### Ключевые принципы:
- 🔒 **Полная приватность и 0 телеметрии**: Прямое подключение к `api.telegram.org` или вашему собственному инстансу Bot API. Никаких сторонних серверов, аналитики, логов или закрытых облачных БД.
- ⚡ **Энергосберегающая Push-архитектура**: Приложение не держит постоянно открытый сокет в фоне. Пробуждение бота из глубокого сна (Android Doze Mode) происходит по тихому дата-пушу Google Firebase (FCM) или Huawei Mobile Services (HMS), сохраняя батарею.
- 📶 **Оффлайн-отказоустойчивость**: Автоматическое переключение на SMS-бот, если мобильный интернет или Wi-Fi недоступны.
- 🛡 **Многоуровневая безопасность**: Авторизация по белому списку Telegram User ID, подтверждение деструктивных действий через SMS PIN-код, шифрование секретов в Android Keystore.
- 📱 **Современные стандарты Android**: Разработано под Android 14 и 15 (Target SDK 35) с поддержкой Foreground Service types, Scoped Storage, CameraX, Room и адаптивным UI на Jetpack Compose Material 3 с NavigationRail.

---

## 📱 Скриншоты интерфейса

| 🚀 Фоновая служба и статус | 🤖 Настройки Telegram | 🔔 Центр уведомлений |
| :---: | :---: | :---: |
| <img src="docs/screenshots/01_start.png" width="280" alt="Фоновая служба и статус" /> | <img src="docs/screenshots/04_telegram.png" width="280" alt="Настройки Telegram" /> | <img src="docs/screenshots/06_notifications.png" width="280" alt="Центр уведомлений" /> |

<details>
<summary><b>📸 Показать остальные экраны (Права, SMS-шлюз, Push Wakeup, Прокси, О боте, Debug)</b></summary>
<br />

| 🛡️ 20 системных разрешений | 💬 SMS-шлюз | 🔥 Dual Push Wakeup (FCM/HMS) |
| :---: | :---: | :---: |
| <img src="docs/screenshots/02_permissions.png" width="280" alt="Системные разрешения" /> | <img src="docs/screenshots/05_sms.png" width="280" alt="SMS-шлюз" /> | <img src="docs/screenshots/07_push.png" width="280" alt="Push Wakeup" /> |

| 🌐 Прокси и обход DPI | ℹ️ О приложении и сборка | 🛠️ Инженерное меню (Debug) |
| :---: | :---: | :---: |
| <img src="docs/screenshots/08_proxy.png" width="280" alt="Прокси и обход DPI" /> | <img src="docs/screenshots/09_about.png" width="280" alt="О приложении" /> | <img src="docs/screenshots/10_debug.png" width="280" alt="Инженерное меню" /> |

</details>

---

## ⚡ Основной функционал и возможности

### 1. 🤖 Двойной движок управления (Telegram + SMS)
- **Более 54 удаленных команд**: Мониторинг системы, мультимедиа, файлы, уведомления, звонки, SMS, геолокация, управление питанием и Shell-консоль.
- **Режимы связи**: Высокоскоростной Long Polling или мгновенный Webhook Push Wakeup.
- **SMS-бот**: Полное управление устройством в отсутствие интернета по SMS-командам с настраиваемым префиксом (`rb status`, `rb photo`, `rb location` и т.д.).
- **Обход блокировок и DPI**: Возможность указать собственный API Endpoint, подключить Direct или SOCKS5 прокси с авторизацией, либо использовать Cloudflare Reverse Proxy.

### 2. 🔔 Пересылка уведомлений и удаленный ответ
- **Зеркалирование уведомлений**: Пересылка входящих уведомлений от любых установленных приложений прямо в ваш Telegram-чат.
- **Движок фильтрации**: 10 гибких правил отбора (выбор приложений, поиск по подстроке, регулярные выражения RegEx, инверсия, регистронезависимость).
- **Anti-Echo и окно дедупликации**: Блокировка повторяющихся уведомлений в заданном интервале и фильтрация уведомлений от самого бота.
- **Удаленный ответ (`/answer <id> <text>`)**: Мгновенный ответ на сообщения в мессенджерах (Telegram, WhatsApp, Signal, SMS) прямо из чата через нативный Android `RemoteInput` API.

### 3. 🔋 Dual Push Wakeup (Google FCM + Huawei HMS)
- **Нулевой разряд батареи**: Устройство не тратит заряд на постоянный опрос сети в фоне.
- **Двойной шлюз**: Поддержка сервисов Google Play Services (`google-services.json`) и экосистемы Huawei (`agconnect-services.json`).
- **Пул серверов**: Автоматическая проверка здоровья шлюзов и прозрачный failover между ними.

### 4. 📞 Телефония и SMS
- **Мониторинг звонков**: Уведомления о входящих, исходящих и пропущенных вызовах с определением имени контакта.
- **Удаленный вызов (`/call <номер>`)**: Совершение телефонных звонков с устройства.
- **Отправка SMS (`/sms <номер> <текст>`)**: Удаленная отправка SMS с выбором нужной SIM-карты и поддержкой транслитерации для экономии длины сообщения.
- **Выполнение USSD (`/ussd <код>`)**: Проверка баланса и управление услугами оператора.

### 5. 📸 Скрытая камера, аудио и запись экрана
- **Скрытая съемка CameraX (`/photo [front|back] [flash]`)**: Моментальное фото с фронтальной или основной камеры в фоне без превью и звука затвора.
- **Скрытая видеозапись (`/video <секунды> [front|back]`)**: Запись видеороликов в фоновом режиме.
- **Аудиозапись (`/record <секунды>`)**: Запись звука с микрофона с отправкой в виде голосового сообщения или аудиофайла.
- **Снимок и запись экрана (`/screenshot`, `/screen <секунды>`)**: Захват экрана устройства в реальном времени через Android MediaProjection API.

### 6. 📍 Гибридный трекинг геолокации
- **Многоуровневый провайдер**: Fused Location Provider (Google), Huawei Location Kit и прямой AOSP GPS/Network провайдер.
- **Непрерывный трекинг (`/track_location <интервал> <время>`)**: Периодическая отправка координат со ссылками на Google Maps и OpenStreetMap.

### 7. 🛡 Безопасность, права и гранулярный доступ
- **Белый список пользователей**: Защита от несанкционированного доступа чужих Telegram-аккаунтов.
- **Гранулярные подписки (`/events`)**: Управление категориями уведомлений (🔔 Push, 💬 SMS, 📞 Звонки, ⚙️ Система) отдельно для каждого пользователя через инлайн-кнопки в Telegram.
- **PIN-код для опасных команд**: Подтверждение команд `/reboot`, `/wipe`, `/shell` и сброса настроек через SMS PIN или диалог подтверждения.
- **Шифрование данных**: Все токены, пароли от прокси и ключи шифруются библиотекой Google Tink через аппаратный Android Keystore.

### 8. ⚙️ Root и Shell консоль
- **Libsu Integration**: Асинхронное выполнение root-команд через `su` с неблокирующим I/O (`/shell <команда>`).
- **Поддержка Shizuku**: Выполнение привилегированных системных команд через Shizuku API без полного root.
- **Управление приложениями (`/apps`, `/app_info`)**: Просмотр установленных пакетов, проверка прав, запуск и скачивание APK.

---

## 📚 Документация и руководства (`docs/`)

В папке [`docs/`](docs/) содержатся подробные пошаговые инструкции и шаблоны конфигураций:

| Файл | Назначение | Для чего необходим |
| :--- | :--- | :--- |
| [**`FIREBASE_SETUP.md`**](docs/FIREBASE_SETUP.md) | **Настройка Google Firebase FCM** | Пошаговое руководство по созданию проекта в консоли Firebase, генерации ключей и скачиванию `google-services.json` для работы функции FCM Push Wakeup. |
| [**`HMS_SETUP.md`**](docs/HMS_SETUP.md) | **Настройка Huawei HMS Push Kit** | Инструкция по созданию проекта в Huawei AppGallery Connect, включению Push Kit, регистрации Release (`remotebot`) и Debug SHA-256 отпечатков сертификата и скачиванию `agconnect-services.json`. |
| [**`CLOUDFLARE_WORKER_SETUP.md`**](docs/CLOUDFLARE_WORKER_SETUP.md) | **Push-шлюз на Cloudflare Workers** | Руководство по бесплатному развертыванию бессерверного Push-шлюза на Cloudflare Workers, позволяющего пересылать вебхуки Telegram в FCM/HMS без собственного VPS-сервера. |
| [**`CLOUDFLARE_TELEGRAM_REVERSE_PROXY.md`**](docs/CLOUDFLARE_TELEGRAM_REVERSE_PROXY.md) | **Реверс-прокси для Telegram API** | Инструкция по развертыванию собственного HTTPS reverse proxy для `api.telegram.org` на Cloudflare Workers для обхода блокировок Роскомнадзора / DPI без сторонних VPN. |
| [**`google-services.json.example`**](docs/google-services.json.example) | **Шаблон конфига Firebase** | Пример структуры файла `app/google-services.json`, требуемого Gradle-плагином Google Services. |
| [**`agconnect-services.json.example`**](docs/agconnect-services.json.example) | **Шаблон конфига Huawei AGC** | Пример структуры файла `app/agconnect-services.json`, требуемого Gradle-плагином Huawei AGConnect. |
| [**`keystore.properties.example`**](docs/keystore.properties.example) | **Шаблон ключа подписи Release** | Образец для настройки параметров боевого ключа подписи (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) для автоматической подписи Release APK. |
| [**`.env.example`**](docs/.env.example) | **Шаблон переменных окружения** | Пример переменных для автономных серверных шлюзов (сервисные аккаунты FCM, токены Huawei, секретные ключи вебхуков). |

---

## 🖥 Серверная инфраструктура и Docker (`server/`)

В директории [`server/`](server/) собраны легковесные серверные компоненты для маршрутизации вебхуков Telegram и проксирования запросов:

### 1. Push Gateways (`server/serverpush/`)
Принимают вебхуки от Telegram и мгновенно отправляют silent data push в FCM и HMS:
- **Go (`go/`)**: Минималистичный бинарник `FROM scratch` (~10 МБ RAM, встроенные хелсчеки).
- **Python (`python/`)**: Микросервис на Python 3.14 Alpine.
- **OpenResty (`nginx/`)**: Высокопроизводительный Lua-шлюз на базе OpenResty Alpine.
- **Cloudflare Worker (`cloudflare-worker/`)**: Edge-скрипт на JavaScript для serverless развертывания.

### 2. Telegram API Reverse Proxies (`server/serverapi/`)
Реверс-прокси для `api.telegram.org` с поддержкой TLS:
- **Go (`go/`)**: Высокопроизводительный обратный прокси `FROM scratch`.
- **Python (`python/`)**: Легковесный прокси на Python 3.14.
- **Nginx (`nginx/`)**: Nginx 1.30.6 Alpine прокси.
- **Cloudflare Worker (`cloudflare-worker/`)**: Прокси-скрипт для Cloudflare Workers.

---

## 🚀 Сборка из исходников

### Требования:
- **JDK 17** (рекомендуется Eclipse Temurin)
- **Android SDK 35** (Build-tools `35.0.0+`, Platform SDK 35)
- **Docker** (опционально, для запуска серверных шлюзов)

### 1. Клонирование и подготовка
```bash
git clone https://github.com/runalsh/remote-bot-android.git
cd remote-bot-android

# (Опционально) Добавьте свои конфигурации Push-уведомлений
cp docs/google-services.json.example app/google-services.json
cp docs/agconnect-services.json.example app/agconnect-services.json

# (Опционально) Настройте боевой ключ подписи
cp docs/keystore.properties.example keystore.properties
```

### 2. Сборка APK
```bash
# Сборка Debug APK (подписан дебажным ключом)
make build-apk
# Артефакт: app/build/outputs/apk/debug/app-debug.apk

# Сборка Release APK (подписан боевым release.jks либо debug-ключом при его отсутствии)
make build-release
# Артефакт: app/build/outputs/apk/release/app-release.apk
```

### 3. Установка на устройство
```bash
# Сборка и установка на подключенное по ADB устройство
make install

# Чистая переустановка (удаление старого приложения -> установка -> запуск)
make reinstall
```

---

## 🛠 Шпаргалка по командам Makefile

| Команда | Описание |
| :--- | :--- |
| `make check` | Полный гейт качества: компиляция (`check-compile`), линтеры (`lint`) и тесты (`test`). |
| `make check-compile` | Быстрая проверка типов и компиляции Kotlin всех модулей (`compileDebugKotlin`). |
| `make check-ui` | Быстрая проверка компиляции модуля интерфейса (`:app:compileDebugKotlin`). |
| `make lint` | Запуск всех статических анализаторов без изменения файлов (`ktlintCheck`, `detekt`, `lintDebug`). |
| `make lint-fix` | Автоматическое исправление стиля кода Kotlin через KtLint (`ktlintFormat`). |
| `make test` | Запуск всех тестов проекта (Android Domain + Go, Python, Worker серверные шлюзы). |
| `make test-coverage` | Запуск тестов с генерацией HTML/XML отчётов покрытия Jacoco. |
| `make test-android` | Запуск юнит-тестов Android/Kotlin модулей (`:domain:test`). |
| `make test-server` | Запуск юнит-тестов всех реализаций Push Gateway и API Proxy. |
| `make build-apk` | Сборка Android Debug APK (`./gradlew assembleDebug`). |
| `make build-release` | Сборка подписанного Android Release APK (`./gradlew assembleRelease`). |
| `make docker-build-all`| Сборка всех 8 Docker-образов серверов под `linux/amd64` (Go, Python, Nginx, NPX). |
| `make docker-compose-build`| Сборка всех профилей Docker Compose (`go`, `python`, `nginx`, `npx`). |
| `make clean` | Очистка временных файлов и кэша Gradle. |

---

## 🧪 CI/CD и контроль качества

В репозитории настроены автоматические пайплайны GitHub Actions:

1. **Continuous Integration ([`.github/workflows/ci.yml`](.github/workflows/ci.yml))**:
   - Валидация контрольной суммы Gradle Wrapper.
   - Статический анализ: KtLint, Detekt и Android Lint.
   - Запуск юнит-тестов Android & JVM с генерацией отчётов покрытия Jacoco.
   - Тестирование всех 6 серверных шлюзов (Go, Python, Cloudflare Worker).
   - Сборка и подпись Debug и Release APK.
2. **Docker Multi-Server Publish ([`.github/workflows/docker.yml`](.github/workflows/docker.yml))**:
   - Сборка и публикация 8 Docker-образов серверов строго под платформу `linux/amd64`.
3. **Автоматический релиз ([`.github/workflows/release.yml`](.github/workflows/release.yml))**:
   - Срабатывает при создании тега `v*` (например, `v0.1.0`).
   - Собирает и подписывает оба варианта:
     - `remote-bot-android_${TAG}.apk` (боевой ключ `remotebot`).
     - `remote-bot-android_${TAG}_testkey.apk` (дебажная подпись).
   - Генерирует SHA-256 контрольные суммы (`*.sha256`) для каждого файла.
   - Публикует официальный GitHub Release с автоматическим списком изменений и файлами.

---

## ⚠️ Отказ от ответственности (Disclaimer)

Данное программное обеспечение разработано и распространяется исключительно в образовательных целях, для личного удаленного управления собственными устройствами и авторизованного системного администрирования.

- Автор и контрибьюторы **не несут никакой ответственности** за любое неправомерное использование, утерю конфиденциальных данных, повреждение оборудования, юридические последствия, скрытое наблюдение без ведома владельца или любые другие прямые или косвенные убытки, возникшие в результате установки или работы данного приложения.
- Всю ответственность за соблюдение действующего законодательства, нормативных актов о связи, защите персональных данных и неприкосновенности частной жизни вашей страны несет конечный пользователь.
- Установка и эксплуатация программы осуществляются исключительно на ваш собственный страх и риск.

---

## 📄 Лицензия

```text
Copyright 2026 runalsh (https://github.com/runalsh/remote-bot-android)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Полный текст лицензии и условия использования доступны в файле [LICENSE](LICENSE).
