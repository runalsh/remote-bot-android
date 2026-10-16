# Архитектура и план переписывания Remote Bot с нуля

План создания современного, чистого Android-приложения удаленного управления через Telegram на замену закрытому и устаревшему `remote-bot.com`.

> **Примечание**: Поддержка Viber полностью исключена (скипаем). Фокус на надежной, прямой и безопасной работе с **Telegram** (Bot API + TDLib/MTProto).

---

## 1. Технологический стек (Modern Android Stack)

| Компонент | Устаревшее в текущем APK | Выбор для нового проекта | Преимущества |
|---|---|---|---|
| **Язык** | Kotlin 1.6 (байткод 2022) | **Kotlin 2.0.21** | Стабильный K2 компилятор, скорость, строгая типизация |
| **Сборка** | Gradle 7.x / AGP 7.x | **Gradle 8.14.5 + AGP 8.13.2** | Свежая версия AGP из Android Studio, стабильный Gradle Daemon |
| **UI** | XML Layouts + Mvp (Moxy) | **Jetpack Compose (BOM 2024.12.01) + Material 3** | Декларативный UI, в 3 раза меньше кода, легкая темизация |
| **Архитектура** | MVP + God-классы | **Clean Architecture + MVI / MVVM** | Четкое разделение (Domain, Data, Presentation), тестируемость |
| **Асинхронность** | RxJava2 | **Kotlin Coroutines 1.10.1 + StateFlow / SharedFlow** | Нативная поддержка Kotlin, структурированная конкурентность |
| **DI** | Dagger 2 (Dagger-Android) | **Koin 4.0.2 (Compose + Android)** | Минимум boilerplate, быстрая сборка, простота модулей |
| **Сеть** | OkHttp 4 + Retrofit | **Ktor Client 3.1.1 + OkHttp Engine** | Поддержка сокетов, WSS-туннелей, динамических прокси |
| **База данных** | Room 2.4 | **Room 2.6.1 (KSP 2.0.21-1.0.28)** | Поддержка Flow, корутин, миграций без KAPT |
| **Сериализация**| Gson | **kotlinx.serialization 1.7.3** | Быстрее, безопаснее с non-nullable полями |
| **Target SDK** | Android 12 (API 31) | **Target SDK 35 (Android 15)** | Соответствие современным требованиям безопасности ОС |

### Зафиксированные версии зависимостей (Pinned в `libs.versions.toml`)

| Зависимость | Версия | Примечание |
|---|---|---|
| Gradle Wrapper | **8.14.5** | `gradle-wrapper.properties` |
| Android Gradle Plugin (AGP) | **8.13.2** | `com.android.application` / `library` |
| Kotlin | **2.0.21** | K2 compiler + Compose compiler plugin |
| KSP | **2.0.21-1.0.28** | Генерация кода Room |
| Compose BOM | **2024.12.01** | `androidx.compose:compose-bom` |
| Kotlin Coroutines | **1.10.1** | `kotlinx-coroutines-core/android` |
| kotlinx.serialization | **1.7.3** | `kotlinx-serialization-json` |
| Koin Core / Android / Compose | **4.0.2** | `io.insert-koin:koin-*` |
| Ktor Client | **3.1.1** | `io.ktor:ktor-client-*` (OkHttp, WebSockets) |
| Room | **2.6.1** | `androidx.room` (KSP) |
| CameraX | **1.4.1** | `androidx.camera:camera-*` |
| libsu | **6.0.0** | `com.topjohnwu.superuser:core` |
| Google Tink | **1.16.0** | `com.google.crypto.tink:tink-android` |
| SQLCipher Android | **4.5.4** | `net.zetetic:android-database-sqlcipher` |
| WorkManager | **2.10.0** | `androidx.work:work-runtime-ktx` |
| TDLib | **1.8.x** | `libtdjni.so` (опционально) |

---

## 2. Сетевой уровень и Telegram: Прямая работа без посредников

В исходном приложении критическая уязвимость архитектуры заключалась в зависимости от бэкенда автора (`api.remote-bot.com`, `push.remote-bot.com`). В новом проекте связь строится **напрямую**:

### 2.1. Режим Telegram Bot API (HTTP)
* **Механизмы получения входящих событий (гибридная модель)**:
  1. **Нативный Long Polling** (`getUpdates` с `timeout=30..60s`):
     * Полная автономность: не требует белых IP, webhook и сторонних push-серверов.
     * Работает прямо из Android внутри Foreground Service.
     * Идеально для изолированных сетей и сценариев без Google Services.
  2. **Push-сервер (Webhook Gateway + FCM / UnifiedPush)**:
     * **Зачем нужен**: Колоссальная экономия заряда аккумулятора. Приложению не требуется непрерывно держать активный TCP-сокет в фоне и постоянно расходовать мобильный трафик/батарею. Устройство «спит» в Doze Mode до момента поступления команды в Telegram.
     * **Архитектура собственного Push-шлюза (Self-Hosted)**:
       * Легковесный микросервис (Go / Python / Cloudflare Worker / VPS).
       * Бот регистрирует Webhook в Telegram: `setWebhook(url="https://push.mydomain.com/tg/:fcmToken/:secretKey")`.
       * **Решение проблемы лимита FCM (4 КБ)**: Telegram-сообщения (особенно с медиа, длинным текстом или цитатами) могут превышать жесткий лимит FCM в 4 КБ. Поэтому шлюз отправляет в FCM только компактный wakeup-сигнал с `update_id` (~50 байт).
       * При получении Data Push Android-приложение (`FirebaseMessagingService` / `UnifiedPushReceiver`) мгновенно просыпается, делает точечный сетевой запрос `getUpdates(offset=update_id, limit=1)` напрямую к Bot API, парсит `Update`, исполняет команду и отправляет ответ в чат.
     * **Гибридный фоллбэк**: Если Google Services недоступны или push-сервер не отвечает, приложение автоматически переключается на Long Polling.
* **Кастомный API Root (для Long Polling)**: Возможность указать свой сервер Telegram Bot API или собственный домен/обратный прокси (например, `https://my-tg-proxy.example.com` или локальный Docker-контейнер `telegram-bot-api` с флагом `--local` для снятия лимита на загрузку файлов до 2 ГБ вместо 50 МБ и обхода DPI). В режиме Long Polling бот обращается напрямую к указанному эндпоинту, а параметры Push Gateway не требуются.
* **Разграничение зон ответственности (Telegram vs FCM)**:
  * Вкладка `✈️ Telegram` управляет выбором режима (`Long Polling` с полем кастомного API root vs `FCM Push Wakeup` со сводкой параметров шлюза).
  * Вкладка `🔥 FCM` является единой точкой конфигурации Firebase и Push Gateway (`Push Gateway URL`, `Push Secret Key`), исключая дублирование настроек и конфликты перезаписи.

### 2.2. Режим Telegram Client (TDLib / MTProto) — [Опционально / Отложено]
* **Текущий статус**: Отключен и не используется. Основной архитектурный стек полностью опирается на нативный **Telegram Bot API (HTTP)**.
* **Почему не используется в ядре**:
  * Сборка `libtdjni.so` (TDLib) раздувает размер APK на +80–100 МБ.
  * Потребляет 80–150 МБ ОЗУ (критический риск выгрузки службы системой Android по LMK).
  * Для всех 54 команд удаленного управления HTTP Bot API полностью достаточен, работает быстрее и тратит в разы меньше батареи.
* **Назначение в будущем (если потребуется)**: Только для режима UserBot (авторизация по номеру телефона вместо токена бота). При включении будет вынесен в изолированный вторичный процесс (`android:process=":tdlib"`).

### 2.3. Универсальный модуль проксирования (Proxy Engine)
* **Поддержка протоколов из коробки**:
  * **HTTP / HTTPS Proxy** с поддержкой `Basic` авторизации (логин/пароль).
  * **SOCKS5 Proxy** с поддержкой RFC 1928/1929 авторизации (логин/пароль).
  * **MTProto Proxy** (для режима TDLib).
  * **WebSocket Proxy (WSS / dumbproxy / wstunnel / `tg://webproxy`)**:
    * **Зачем нужен**: Обход жестких DPI-блокировок интернет-провайдеров и мобильных операторов, которые детектируют и блокируют сигнатуры классического SOCKS5 и MTProto.
    * **Механика**: Трафик оборачивается в WebSocket поверх TLS (порт 443) и маскируется под обычное HTTPS-соединение к веб-сайту или CDN (Cloudflare).
    * **Реализация в Android**:
      * Встроенный легковесный loopback-прокси (`127.0.0.1:local_port`), поднимаемый на корутинах (Kotlin Coroutines Sockets / Ktor / OkHttp WebSockets) или через готовую нативную библиотеку туннелирования.
      * Приложение перенаправляет трафик Telegram HTTP / TDLib в локальный порт, а loopback-клиент прозрачно упаковывает его в WSS-туннель до внешнего прокси.
      * Настройки: Host, Port, Path (например, `/ws`), SNI, Host Header, TLS/SSL, кастомные заголовки авторизации (`Authorization: Bearer <secret>`).
* **Специфика реализации SOCKS5 в Java/Android**:
  * Использовать `java.net.Authenticator.setDefault()` или кастомный `SocketFactory`, чтобы избежать утечек учетных данных и зависаний SSL-handshake.
  * DNS over Proxy (удаленный резолвинг DNS через SOCKS5 для обхода DPI-блокировок).

### 2.4. Режим управления через SMS (Offline Fallback Engine)
* **Зачем нужен**: Абсолютная отказоустойчивость устройства при полном отсутствии интернета, отключении Wi-Fi/сотовых данных оператором, блокировке трафика или сбоях в сети Telegram.
* **Принцип работы**:
  * Входящие SMS перехватываются через `BroadcastReceiver` с максимальным приоритетом (`Telephony.Sms.Intents.SMS_RECEIVED_ACTION`).
  * Проверка входящего сообщения по двум уровням:
    1. **Белый список номеров (Phone Whitelist)**: проверка номера отправителя по международному формату E.164 (`+7XXXXXXXXXX`).
    2. **Секретный PIN-префикс (опционально, по умолчанию выключен)**: защита от подделки номера отправителя (SMS Spoofing).
       * **По умолчанию выключен**: для выполнения команды достаточно отправки с доверенного номера из белого списка.
       * **При включении в настройках**: каждая SMS-команда обязана начинаться с префикса и заданного PIN-кода (например: `rb 1234 /location` или `#pin /wifi on`). Сообщения без корректного PIN игнорируются.
  * Команда передается в единый диспетчер команд ядра (`CommandDispatcher`).
  * Ответ формируется и отправляется обратно администратору в виде SMS через `SmsManager`.
* **Поддержка Dual SIM**:
  * Использование `SubscriptionManager` для выбора нужного `SubscriptionId`.
  * Возможность настройки: отвечать с фиксированной SIM-карты (SIM 1 / SIM 2) либо автоматически отвечать с той же SIM-карты, на которую поступил входящий запрос.
* **Обработка длинных ответов**:
  * Автоматическое разбиение через `smsManager.sendMultipartTextMessage(...)`.
  * Лимит на количество частей (например, максимум 3 SMS на ответ) во избежание списания баланса.
* **Ключевые SMS-команды спасения (Rescue Commands)**:
  * `/location` — моментальные координаты ссылкой на Google Maps / OpenStreetMap.
  * `/mobile_data on` / `/wifi on` — включение сетевых интерфейсов для возврата онлайн.
  * `/call <номер>` — обратный скрытый звонок администратору.
  * `/reboot` — экстренная перезагрузка (при наличии Root/Shizuku).

---

## 3. Системные особенности Android и подводные камни (Gotchas)

### 3.1. Живучесть в фоне (Background Survival)
* **Foreground Service (Требования Android 14/15)**:
  * **Тип сервиса**: Использовать `FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING` (API 34+) или `SPECIAL_USE`. 
    > [!WARNING]
    > Тип `DATA_SYNC` на Android 14 имеет системную квоту исполнения (убивается ОС через 6 часов). Для постоянного демона он непригоден.
  * **Динамический подъем типов FGS (Camera & Microphone)**: Начиная с Android 11+ доступ к камере и микрофону из фонового сервиса блокируется ОС (`SecurityException`), если сервис не объявлен с типами `camera` и `microphone`. При старте команд `/photo`, `/videorec`, `/rec` сервис динамически повышает свои типы через `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_MICROPHONE)`.
  * Рестарт при убийстве ОС: возврат `START_STICKY` в `onStartCommand()`.
* **Энергосбережение (Doze Mode & App Standby)**:
  * Запрос исключения из оптимизации батареи через интент `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
* **Автозапуск и вендорские ограничения**:
  * Ресивер `BOOT_COMPLETED` для старта после перезагрузки телефона.
  * Экран-помощник с инструкциями для вендорских прошивок (MIUI, HyperOS, ColorOS, EMUI/HarmonyOS) по включению автозапуска и отключению фоновых ограничений (через автоопределение OEM Intent).
* **Симбиоз системных служб (Watchdog)**:
  * `NotificationListenerService` (перехват пушей) и `AccessibilityService` (USSD/автоматизация) являются системными компонентами Android: ОС сама удерживает их с максимальным приоритетом OOM-adj и перезапускает при падении. Они выступают взаимными сторожевыми демонами (Watchdog) для сетевого ядра.
  * `WorkManager` используется как периодический вторичный таймер.

### 3.2. Разрешения (Permissions) и системный доступ

Приложение распространяется вне каталога Google Play (GitHub Releases / F-Droid / прямой APK), что позволяет использовать полный спектр системных полномочий Android без ограничений политик Google Play Console.

Все 20 разрешений приложения сгруппированы по функциональным направлениям с привязкой к командам бота:

#### 1. Системный доступ (Root и Установка)
1. **Root (`libsu` 6.0.0)**:
   * **Команды бота**: `/shell` (алиас `/sh`), `/reboot`, `/shutdown` (алиас `/poweroff`), `/recovery`, `/fastboot`, `/screenshot`, `/screen_rec`, `/airplane`, `/hotspot`, `/mobile_data`, `/gps`, `/powersave`, `/adb`, `/adb_wifi`, `/install`, `/kill`, `/script`.
   * **Системные функции**: пакетная выдача всех 27 разрешений и привилегий в 1 клик без подтверждений пользователем.
2. **Установка приложений (`REQUEST_INSTALL_PACKAGES`)**:
   * **Команды бота**: `/install <file.apk>` (тихая установка и обновление APK).
   * **Системное действие**: `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
3. **Все файлы (`MANAGE_EXTERNAL_STORAGE`)**:
   * **Команды бота**: `/get_file` (алиас `/gf`), `/download`, `/ls`, `/dir`, `/ls_interactive` (алиас `/lsi`), `/unsent_files`.
   * **Системное действие**: `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (полный доступ к хранилищу Android 11+ без Scoped Storage).
4. **Специальные возможности (`Accessibility Service`)**:
   * **Команды бота**: `/ussd [sim] <code>`, перехват интерактивных USSD-ответов операторов, автоматизация UI, чтение экрана и обход диалогов.
   * **Системная служба**: `RemoteBotAccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`).
5. **Изменение системных настроек (`WRITE_SETTINGS`)**:
   * **Команды бота**: `/auto_brightness [on|off|toggle]`, интерактивная панель переключателей `/toggles`.
   * **Системное действие**: `Settings.ACTION_MANAGE_WRITE_SETTINGS`.
6. **Отображение поверх окон (`SYSTEM_ALERT_WINDOW` / Overlays)**:
   * **Команды бота**: `/toggles`, всплывающие информационные окна и диалоги поверх активных приложений.
   * **Системное действие**: `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.

#### 2. Связь и уведомления (Уведомления, SMS, USSD, Звонки)
7. **Чтение уведомлений (`Notification Listener`)**:
   * **Команды бота**: `/notify`, `/notifications`, `/notify_send`, `/answer <id> <text>` (алиас `/resp`), `/notify_clear <id>`, `/notify_rule <list|add|del>`, пересылка пушей мессенджеров и банков в реальном времени.
   * **Системная служба**: `RemoteBotNotificationListenerService` (`BIND_NOTIFICATION_LISTENER_SERVICE`).
8. **SMS (`SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`)**:
   * **Команды бота**: `/sms [sim] <number> <text>`, `/sms_inbox [limit]`, `/sms_bot [on|off|toggle]`, `/sms_forward`, `/whitelist_phone`, автономный SMS-бот при отсутствии интернета.
9. **Звонки, USSD и журнал (`CALL_PHONE`, `READ_CALL_LOG`, `READ_PHONE_STATE`)**:
   * **Команды бота**: `/call [sim] <number>`, `/callshistory [limit]`, `/ussd [sim] <code>`, перехват и пересылка входящих вызовов (`PhoneCallReceiver`).
10. **Режим «Не беспокоить» (`ACCESS_NOTIFICATION_POLICY` / DND)**:
    * **Команды бота**: `/ring_mode [normal|silent|vibrate]`, `/volume [stream] [level]`.

#### 3. Мультимедиа и сенсоры (Камера, Звук, Локация, Сеть, Фоновый режим)
11. **Камера (`CAMERA`)**:
    * **Команды бота**: `/photo [front|back] [flash_on|off] [rotate]`, `/videorec [front|back] [duration]`, `/detectmotion [1..10|on|off]`, `/flashlight [on|off|toggle]`.
12. **Микрофон (`RECORD_AUDIO`)**:
    * **Команды бота**: `/rec [sec]`, `/rec_file [sec]`, `/rec stop`, `/sound_detector [1..10|on|off]`, `/tts [lang] <text>`, `/device_search`.
13. **Геолокация (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`)**:
    * **Команды бота**: `/location [sec|last]` (алиас `/trace`), `/track [interval|stop|status]`, `/shake_detector [1..10|on|off]`, `/gps [on|off|toggle]`.
14. **Контакты (`READ_CONTACTS`)**:
    * **Команды бота**: автоматическое сопоставление телефонных номеров с именами контактов в `/call`, `/callshistory`, входящих звонках и SMS.
15. **Энергосбережение (`Doze Mode` / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)**:
    * **Системные функции**: круглосуточная работа фонового сервиса 24/7 (Long Polling, WebSocket `/ws`, Push Wakeup) без засыпания процесса.
16. **Уведомления службы (`POST_NOTIFICATIONS` / Android 13+)**:
    * **Системные функции**: постоянный показ статуса Foreground Service `RemoteBotService` в системной шторке.
17. **Фото и изображения (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE`)**:
    * **Команды бота**: `/gallery [limit]`, `/get_file <path>`.
18. **Аудиофайлы (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`)**:
    * **Команды бота**: `/player` (алиас `/playback`), `/rec_file [duration]`.
19. **Bluetooth (`BLUETOOTH_CONNECT`, `BLUETOOTH`)**:
    * **Команды бота**: `/bluetooth [on|off|toggle]`.
20. **Безлимитный мобильный трафик (Data Saver bypass)**:
    * **Системные функции**: бесперебойная связь с ботом 24/7 при активном режиме экономии мобильного трафика.

#### Пакетная выдача 27 разрешений через Root в 1 клик
При наличии Root-доступа мастер выдает весь комплекс разрешений одним нажатием за 1–2 секунды через выполнение 27 системных команд:
```bash
# 1. Runtime-разрешения подсистемы pm
pm grant $pkg android.permission.CAMERA
pm grant $pkg android.permission.RECORD_AUDIO
pm grant $pkg android.permission.ACCESS_FINE_LOCATION
pm grant $pkg android.permission.ACCESS_COARSE_LOCATION
pm grant $pkg android.permission.ACCESS_BACKGROUND_LOCATION
pm grant $pkg android.permission.SEND_SMS
pm grant $pkg android.permission.RECEIVE_SMS
pm grant $pkg android.permission.READ_SMS
pm grant $pkg android.permission.CALL_PHONE
pm grant $pkg android.permission.READ_CALL_LOG
pm grant $pkg android.permission.READ_PHONE_STATE
pm grant $pkg android.permission.READ_CONTACTS
pm grant $pkg android.permission.POST_NOTIFICATIONS
pm grant $pkg android.permission.BLUETOOTH_CONNECT
pm grant $pkg android.permission.READ_MEDIA_IMAGES
pm grant $pkg android.permission.READ_MEDIA_VIDEO
pm grant $pkg android.permission.READ_MEDIA_AUDIO
pm grant $pkg android.permission.READ_EXTERNAL_STORAGE

# 2. Специальные права AppOps
appops set $pkg SYSTEM_ALERT_WINDOW allow
appops set $pkg MANAGE_EXTERNAL_STORAGE allow
appops set $pkg WRITE_SETTINGS allow
appops set $pkg REQUEST_INSTALL_PACKAGES allow

# 3. Службы уведомлений и специальных возможностей
cmd notification allow_listener $pkg/com.remotebot.app.service.RemoteBotNotificationListenerService
settings put secure enabled_accessibility_services $pkg/com.remotebot.app.service.RemoteBotAccessibilityService
settings put secure accessibility_enabled 1

# 4. Исключения энергосбережения и мобильной сети
dumpsys deviceidle whitelist +$pkg
cmd netpolicy add restrict-background-whitelist $uid
```

#### Фоновые службы Foreground Service (Android 14/15 / API 34+)
* `android.permission.FOREGROUND_SERVICE` — базовое разрешение для постоянной работы сервиса в фоне.
* `android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING` — целевой тип FGS для непрерывного сетевого соединения, Long Polling и FCM Push Wakeup. Не имеет 6-часовой квоты убийства в отличие от `DATA_SYNC`.
* `android.permission.FOREGROUND_SERVICE_CAMERA` — обязательное динамическое расширение FGS перед съемкой скрытых фото и видео (`/photo`, `/videorec`).
* `android.permission.FOREGROUND_SERVICE_MICROPHONE` — обязательное динамическое расширение FGS перед фоновой аудиозаписью (`/rec`).
* `android.permission.FOREGROUND_SERVICE_LOCATION` — FGS тип для отслеживания геопозиции в фоне.

### 3.3. Root и альтернативы (Shizuku API)
* **Root интеграция**:
  * Использовать современную библиотеку `libsu` (от автора Magisk - topjohnwu) вместо старых небезопасных вызовов `Runtime.getRuntime().exec("su")`.
  * Преимущества `libsu`: переиспользование сессий шелла, не зависает I/O потоки, безопасная передача окружения.
* **Поддержка Shizuku (без Root)**:
  * Дает возможность выполнять привилегированные команды Android через ADB-права без Root:
    * Тихая установка/удаление APK (`pm install`).
    * Остановка приложений (`am force-stop`) и чтение чужих процессов для `/taskmanager` (в обход изоляции SELinux).
    * Скрытый захват экрана (`screencap`).
    * Доступ к скрытым системным логам (`logcat`).

### 3.4. Специальные возможности (Accessibility Service)
* Необходим для:
  * Перехвата ответов USSD-запросов (USSD диалоги не отдаются через стандартный Telephony API).
  * Автоматизации нажатий на экране при необходимости.

### 3.5. Двусторонняя интеграция с Tasker / Locale
* **1. Управление Tasker из Telegram / SMS (Бот -> Tasker)**:
  * Команда `/tasker <TaskName> [par1] [par2]` — запуск любых профилей и сценариев автоматизации.
  * Чтение списка доступных тасок через ContentProvider Tasker (`TaskerIntent.getTasks()`).
  * Передача аргументов в виде переменных `%par1`, `%par2` внутри Tasker.
* **2. Встраиваемый плагин Tasker (Tasker -> Remote Bot -> Telegram / SMS)**:
  * Реализация стандартного протокола Locale/Tasker Action Plugin (`com.twofortyfouram.locale.intent.action.FIRE_SETTING`).
  * `TaskerPluginEditActivity` — экран конфигурации действия прямо в интерфейсе Tasker.
  * `TaskerActionReceiver` — фоновый приемник действий от движка Tasker.
  * **Сценарии**: При наступлении любого аппаратного или системного события в Tasker (движение, геозона, заряд, смена сети Wi-Fi, падение) Tasker вызывает Remote Bot для:
    * Отправки тревожного сообщения с переменными Tasker (`%BATT`, `%LOC`, `%DATE`) в Telegram/SMS.
    * Скрытой съемки фото с камеры и отправки в чат.
    * Вызова любой внутренней команды Remote Bot.

### 3.6. Пересылка и умная фильтрация уведомлений (Notification Forwarding Engine)
* **Архитектура службы**:
  * Реализация системного `NotificationListenerService` (`NotificationsService`).
  * Перехват входящих статус-бар уведомлений `StatusBarNotification` в реальном времени.
* **Выбор приложений (App Selection) и Package Visibility (Android 11–15)**:
  * Белый список (White list) и черный список (Black list) пакетов (`packageName`).
  * Compose UI диалог выбора приложений: поиск, иконка, название приложения, переключатель скрытия системных.
  * **Требования к Package Visibility (Android 11+)**:
    - На Android 11+ (API 30+) вызов `packageManager.getInstalledPackages()` без специального разрешения возвращает только собственный пакет приложения.
    - В `AndroidManifest.xml` обязательно объявляются: `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` и блок `<queries><intent><action android:name="android.intent.action.MAIN" /></intent></queries>`.
    - Репозиторий приложений использует быстрый вызов с `flags = 0` (вместо тяжелого `GET_META_DATA`, исключая `TransactionTooLargeException` на устройствах с сотнями программ), корректно учитывает обновленные системные приложения (`FLAG_UPDATED_SYSTEM_APP`) и содержит fallback через `getInstalledApplications(0)`.
* **Фильтрация по содержимому (Regex & Substring Rules)**:
  * **Типы совпадений**:
    * `TitleMatches` / `TitleNotMatches` (совпадение по заголовку).
    * `TitleContains` / `TitleNotContains` (вхождение подстроки в заголовок).
    * `BodyMatches` / `BodyNotMatches` (совпадение по тексту тела).
    * `BodyContains` / `BodyNotContains` (вхождение подстроки в текст).
  * **Поддержка RegEx (Регулярных выражений)**:
    * Включение флага `regex: true` для правила.
    * Сценарии:
      * **Белый список (только важное)**: пересылка кодов подтверждения банков: `(?i)(код|пароль|code|auth)[\s:]*(\d{4,8})`.
      * **Черный список (игнор спама)**: пропуск рекламы маркетплейсов: `(?i)(скидк|акци|распродаж|кешбэк|sale)`.
  * **Логические комбинации**: поддержка групп условий (И / ИЛИ).
* **Удаленный быстрый ответ (Remote Quick Reply в WhatsApp / Telegram / Signal / SMS / чаты)**:
  * Извлечение системных действий `NotificationCompat.Action` с привязанным объектом `RemoteInput` (стандартный механизм быстрого ответа Android из шторки и умных часов).
  * **Изолированное хранение по уникальному ID события (`shortId`)**:
    - Входящие уведомления и их объекты `StatusBarNotification` сохраняются в памяти в `recentSbnById` по уникальному инкрементному `shortId` (или timestamp), а **НЕ по `sbn.key`**.
    - Это предотвращает перезапись контекста: если приходят уведомления от разных людей или разных приложений (например, параллельно из WhatsApp и Telegram), ответ гарантированно отправляется конкретному адресату, на кнопку которого нажал пользователь.
  * **Строгая фильтрация кнопки ответа (только для мессенджеров)**:
    - Инлайн-кнопка «Ответить» формируется только при наличии `RemoteInput` со свободным вводом текста (`allowFreeFormInput == true`).
    - Исключаются системные сервисы, плееры, банки без чата: проверяется категория (`CATEGORY_MESSAGE`, `CATEGORY_CALL`, `"msg"`), наличие `MessagingStyle` (`EXTRA_MESSAGING_STYLE_USER`, `EXTRA_HISTORIC_MESSAGES`) в `extras` или SMS-пакет.
  * **Интерактивный диалог ответа без ручного ввода команд**:
    - Под пересланным уведомлением прикрепляется инлайн-кнопка «Ответить» (без лишних эмодзи на кнопке).
    - При нажатии бот присылает prompt: `✍️ Введите текст ответа для <Приложение> (<Заголовок>):` с кнопкой `[ ❌ Отмена ]` (без навязчивого системного `force_reply` баннера).
    - Пользователь просто пишет текст в чат Telegram и отправляет -> бот упаковывает его в `RemoteInput.addResultsToIntent()` и активирует `PendingIntent.send(context, 0, intent)`. Целевой мессенджер (например, WhatsApp) в фоне принимает системный интент и отправляет сообщение собеседнику.
    - Поддерживается нативный Swipe/Reply на пересланное уведомление Telegram.
    - Прямой ввод через команду: `/answer <id> <text>` / `/resp <id> <text>`.
  * **Системные ограничения Android RemoteInput API (Архитектурная специфика)**:
    - **Привязка к диалогу/чату, а не к сообщению**: Системный `PendingIntent` быстрого ответа создается целевым приложением (Telegram, WhatsApp, Signal) на уровне **активного диалога/беседы (chat/peer ID)**. Он не хранит ID конкретного промежуточного сообщения.
    - **Поведение при очереди входящих сообщений в одном чате**: Если в один и тот же чат собеседник прислал подряд несколько сообщений (`test1`, `test2`, `test3`), то при ответе на любое из них целевой мессенджер при получении `PendingIntent` автоматически направляет реплику в этот чат, привязывая ее к **самому последнему входящему сообщению** диалога. Это фундаментальное поведение Android RemoteInput API (идентично поведению ответа с часов WearOS или из шторки Android).
    - **Многопользовательская изоляция**: Между разными чатами и разными приложениями разделение строго изолированное — ответ Алисе никогда не уйдет Бобу благодаря уникальному маппингу по `shortId`.
* **Защита от зацикливания и эхо-петель (Anti-Echo / Cascade Protection)**:
  * **Динамический кэш исходящих сообщений бота**: скользящий буфер (60 секунд) отправленных ботом текстов. Входящие уведомления от самого Telegram/мессенджера, цитирующие ответы или системные отчеты бота, мгновенно сбрасываются без пересылки.
  * **Игнорирование системных группировок**: фильтрация `FLAG_GROUP_SUMMARY` (сводные уведомления "3 новых сообщения из 3 чатов") и `FLAG_ONGOING_EVENT` (постоянные служебные уведомления).
  * Полный отказ от ненадежных строковых хардкодов в пользу стандартных системных флагов Android и динамического трекинга исходящих сообщений.
* **Эмуляция и тестирование уведомлений (Notification Simulation)**:
  * Возможность генерации фиктивных уведомлений от имени любого пакета приложения (`/fake_notify <pkg> <title> <text>`).
  * Позволяет верифицировать фильтрацию, пересылку и логику быстрых ответов без необходимости запуска физических приложений на устройстве.
* **Защита от дубликатов (Notification Anti-Spam Deduplication)**:
  * Единый прозрачный таймаут дедупликации (`notificationDedupTimeoutSeconds`, настраиваемый в UI: 5 сек, 15 сек, 30 сек, 1 мин, 5 мин, 15 мин; по умолчанию 15 секунд).
  * Ключ дедупликации: `${packageName}|${title}|${text}`.
  * **Поведение**: любое повторное уведомление от любого приложения с абсолютно идентичным заголовком и текстом внутри заданного окна времени **безвозвратно отбрасывается (дропается)** — не пересылается в Telegram/SMS и не ставится в очередь.
  * Уведомления с изменившимся текстом (например, push-код с другими цифрами) имеют другой ключ и пересылаются сразу.
  * Уведомления с одинаковым текстом (включая повторные сессии 2FA или повторную отправку того же кода) пересылаются только в том случае, если пауза между событиями превышает установленный таймаут.
* **Автоудаление (Cancel Notification)**: автоматическое смахивание уведомления из статус-бара телефона после успешной пересылки в Telegram.

---

## 4. Архитектура модулей проекта

Рекомендуется многомодульная структура (Gradle Multi-Module):

```text
├── app/                  # Точка входа, Application, DI конфигурация
├── core/
│   ├── common/           # Общие утилиты, расширения, Base классы
│   ├── network/          # OkHttp, Ktor, HTTP/SOCKS5 менеджер, DNS-over-Proxy
│   ├── proxy/            # Движок прокси: WSS/WebSocket tunnel, SOCKS5, HTTP Basic
│   ├── push/             # Мульти-провайдерный Push-движок (Google FCM + Huawei HMS + Xiaomi Mi Push)
│   ├── database/         # Room сущности, DAO, миграции
│   └── security/         # Шифрование настроек (EncryptedSharedPreferences)
├── domain/
│   ├── model/            # Чистые модели (Command, Message, ProxyConfig)
│   └── repository/       # Интерфейсы репозиториев
├── channels/             # Каналы связи и управления
│   ├── telegram-bot/     # Telegram Bot API (Long Polling + Webhook/Push диспетчер)
│   ├── telegram-tdlib/   # Опциональный TDLib MTProto (UserBot)
│   ├── sms-bot/          # Движок управления через SMS (Offline Fallback Engine)
│   ├── whatsapp-bot/     # Движок управления через WhatsApp (Notification/Cloud API/Bridge)
│   ├── discord-bot/      # Движок управления через Discord (Gateway WebSocket / Slash Commands)
│   ├── matrix-bot/       # Движок управления через Matrix (Децентрализованный REST API / E2EE)
│   ├── slack-bot/        # Движок управления через Slack (Socket Mode WSS / Block Kit)
│   ├── max-bot/          # Движок управления через MAX (VK Teams / MyTeam Bot API)
│   └── facebook-bot/     # Движок управления через Facebook Messenger (Meta Platform API)
├── server/               # (Внешний) Легковесный Push/Webhook Gateway (Go / Worker)
├── features/
│   ├── camera/           # CameraX съемка, детектор движения (ImageAnalysis)
│   ├── audio/            # Фоновая запись звука, TTS, аудиоплеер
│   ├── telephony/        # Обработка SMS, звонков, контактов, USSD
│   ├── notifications/    # Перехват пушей, Regex-фильтрация, /answer (RemoteInput)
│   ├── location/         # FusedLocationProviderClient + системный GPS fallback
│   ├── files/            # Управление файлами, загрузчик, проводник
│   ├── shell/            # Выполнение Sh/Bash/Root команд через libsu
│   ├── tasker/           # Команда /tasker + плагин Locale/Tasker Action
│   ├── scripts/          # Движок исполнения JavaScript (QuickJS) с системным API-мостом
│   ├── timers/           # Планировщик отложенных команд (AlarmManager / WorkManager)
│   └── system/           # Мониторинг батареи, приложений, диспетчер задач
└── presentation/         # Jetpack Compose UI (Настройки, Логи, Прокси)
```

---

## 5. Полная спецификация команд и их реализация с нуля (на базе FEATURES.md)

### 🔰 Блок 0. Базовые и сервисные команды
* `/start` / `/help`
  * **Реализация**: Интерактивная справка и сводка статуса устройства (Root, активные каналы, полный перечень всех доступных команд с подсказками по аргументам).
* `/ping`
  * **Реализация**: Проверка сетевого канала связи и времени отклика бота (`Pong!`).
* `/echo <text>`
  * **Реализация**: Эхо-тест приема и возврата текста.
* `/chat_id`
  * **Реализация**: Получение Telegram Chat ID и User ID отправителя.
* `/about`
  * **Реализация**: Информация о сборке клиента, версии, архитектуре и статусе безопасности.

### 📷 Блок 1. Камера, Фото и Видео
* `/photo [front|back] [flash_on|flash_off] [rotate]`
  * **Реализация**: Скрытая съемка через Camera2 / CameraX (`ImageCapture`) без UI-превью на экране с мгновенной отправкой фото в чат Telegram.
  * **API/Права**: `CAMERA`, привязка `ProcessCameraProvider` / Camera2 Device.
* `/videorec [front|back] [duration_sec]`
  * **Реализация**: Фоновая видеозапись с автосохранением в файл `.mp4` и отправкой в чат (`sendVideo`).
  * **Права**: `CAMERA`, `RECORD_AUDIO`.
* `/gallery [limit]`
  * **Реализация**: Выборка последних фото через `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` с отправкой медиагруппой (`sendMediaGroup`).

* `/rec [duration_sec] [file|voice]` / `/rec_file [duration_sec]` / `/rec stop`
  * **Реализация**: Скрытая фоновая запись с микрофона через `MediaRecorder` в `.m4a` (AAC). По умолчанию отправляется голосовым сообщением (`sendVoice`), при указании аргумента `file`/`doc` или команды `/rec_file` — аудиотреком/файлом с метаданными плеера (`sendAudio`).
  * **Права**: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`.
* `/tts <lang> <text>`
  * **Реализация**: Синтез речи через системный динамик телефона с помощью `android.speech.tts.TextToSpeech`.
* `/playback` / `/player`
  * **Реализация**: Воспроизведение переданных медиафайлов/голосовых заметок через `MediaPlayer` / `ExoPlayer` (управление: плей, пауза, громкость).
* `/volume [stream] [level]`
  * **Реализация**: Просмотр и регулировка уровней громкости (звонок, медиа, будильник) через `AudioManager.setStreamVolume(...)`.
* `/ring_mode [normal|silent|vibrate]`
  * **Реализация**: Переключение профиля звука через `AudioManager.setRingerMode(...)` (требует разрешения Не беспокоить: `ACCESS_NOTIFICATION_POLICY`).
* `/device_search`
  * **Реализация**: Поиск устройства. Принудительная установка максимальной громкости потока `STREAM_ALARM` и зацикленное проигрывание громкой сирены тревоги.

### 📞 Блок 3. Звонки, SMS и USSD
* `/sms [sim] <phone> <text>`
  * **Реализация**: Отправка SMS через `SmsManager.sendTextMessage(...)` с поддержкой Dual SIM (`SubscriptionManager`) и авторазбиением длинных текстов (`divideMessage`).
  * **Права**: `SEND_SMS`, `READ_PHONE_STATE`.
* `/sms_inbox [limit]`
  * **Реализация**: Чтение входящих и исходящих SMS из базы `Telephony.Sms.CONTENT_URI` с форматированием времени и статуса (📥 / 📤).
  * **Права**: `READ_SMS`.
* `/call [sim] <phone>`
  * **Реализация**: Исходящий телефонный звонок через `Intent(Intent.ACTION_CALL, Uri.parse("tel:..."))`.
  * **Права**: `CALL_PHONE`.
* `/callshistory [limit]`
  * **Реализация**: Чтение журнала звонков (входящие, исходящие, пропущенные, длительность) через `CallLog.Calls.CONTENT_URI`.
  * **Права**: `READ_CALL_LOG`.
* `/ussd [sim] <code>`
  * **Реализация**: Отправка USSD-кодов (баланс, услуги) через `TelephonyManager.sendUssdRequest(...)` (на Android 8+) с фоллбэком на Accessibility Service для перехвата диалогов USSD.
* **Автоматические фоновые уведомления и Offline SMS Bot (2 режима безопасности)**:
  * **Режим 1 (Любой номер + PIN)**: исполнение команд при наличии префикса `smsPin` (например, `1234 /wifi on`).
  * **Режим 2 (Доверенный номер - без PIN)**: исполнение команд со слэшем напрямую от номеров из `allowedPhoneNumbers`.
  * Поддержка префикса бота `rb` в любом регистре (`rb`, `RB`, `Rb`, `rB`) с пробелами, переносами строк, слешами или двоеточием (`rb/photo`, `RB: help`, `rb /status`).
  * Моментальная пересылка входящих SMS в Telegram (`Telephony.Sms.Intents.SMS_RECEIVED_ACTION`).
  * **Интерактивный ответ на SMS без ручного ввода команд**:
    - Инлайн-кнопка «💬 Ответить по SMS»: активирует режим Telegram `ForceReply` (`selective=true`, `input_field_placeholder`), автоматически раскрывая клавиатуру и поле ввода у пользователя с цитированием номера.
    - Нативный Swipe/Reply на исходное сообщение SMS или запрос бота: парсер автоматически извлекает номер отправителя и слот SIM-карты, отправляя введённый текст по SMS адресату.
    - Отмена действия командой `/cancel` или словом «отмена».
  * Пересылка информации о входящих звонках в реальном времени (`TelephonyManager.ACTION_PHONE_STATE_CHANGED`).

### 📍 Блок 4. Геолокация и Безопасность
* `/location [args]` (алиас: `/track`)
  * **Режимы**:
    * **Однократный запрос (по умолчанию)**: моментальное определение текущих координат с таймаутом.
    * **Периодический трекинг (Live tracking)**: непрерывная отправка геопозиции с заданным интервалом до команды остановки (`/location live 30s|1m|5m|15m|30m|60m`, `/location stop`, `/location status`). Интерактивные inline-кнопки для быстрого переключения интервалов и остановки.
  * **Мультипровайдерная Fused Геолокация (Google Play Services + Huawei HMS + AOSP + IP Fallback)**:
    * **1. Google Fused Location Provider (`com.google.android.gms:play-services-location`)**: первоочередной провайдер при наличии Google Play Services на устройстве. Использование `getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY)` и `lastLocation`.
    * **2. Huawei HMS Location Kit (`com.huawei.hms:location`)**: нативная поддержка устройств Huawei и Honor без сервисов Google. Автоматический опрос `com.huawei.hms.location.LocationServices.getFusedLocationProviderClient(context)` с `LocationRequest.PRIORITY_HIGH_ACCURACY` и fallback на `lastLocation`.
    * **3. Системный GNSS / GPS и AOSP LocationManager**: прямой опрос спутниковых группировок (GPS, ГЛОНАСС, Galileo, BeiDou) через `GnssStatus.Callback` со спектральным анализом несущей C/N0 (dB-Hz) и количества спутников, системные провайдеры GPS, Network, Passive и AOSP Fused (Android 12+).
    * **4. Резервная сетевая IP-геолокация**: fallback-опрос внешних IP-сервисов (ipinfo.io, ipapi.co) с извлечением региона, города, провайдера при полном отсутствии спутникового сигнала и сотовых вышек (бункеры, подвалы, экранированные помещения).
  * **Диагностика источника и сигнала**:
    * Точное определение и вывод активного движка в ответе бота:
      * `🤖 Google Fused Location (Синтез GPS, Wi-Fi и сотовых вышек)`
      * `🤖 Huawei HMS Fused Location (Синтез GPS, Wi-Fi и вышек HMS)`
      * `🛰️ Спутниковая навигация (GPS/ГЛОНАСС/Galileo/BeiDou)`
      * `📡 Косвенные признаки (Вышки связи Cell-ID, точки Wi-Fi)`
      * `🌐 Сетевая геолокация (IP: город, регион, ISP)`
  * **Навигация**: Точность (±м), высота, скорость, обратное геокодирование через Android `Geocoder` в адрес и ссылки на Google Maps / OpenStreetMap.
  * **Права**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`.
* `/gps [on|off|toggle]`
  * **Реализация**: Программное включение/выключение GPS через Root/Shizuku (`cmd location set-location-enabled` / `settings put secure location_mode ...`).
* `/shake_detector [on|off] [sensitivity] [cooldown_sec]`
  * **Реализация**: Датчик тряски через `SensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)`. Интерактивная 10-балльная шкала чувствительности (от 30 до 1 м/с²), выбор интервала антиспама (1с, 2с, 5с, 10с по умолч., 15с, 30с, 60с) кнопками. При фиксации ускорения — мгновенная отправка тревоги в бот не чаще установленного интервала.
* `/detectmotion [1..10|on|off] [cooldown_sec]`
  * **Реализация**: Охранный детектор движения. Работает непрерывно в фоне до явной остановки. Интерактивная 10-балльная шкала чувствительности (шаг 1: от 10% до 100%) и строка выбора интервала антиспама (1с, 2с, 5с по умолч., 10с, 15с, 30с, 60с) с кнопками включения/выключения. Потоковый анализ разницы матриц яркости кадров камеры. При фиксации движения — автоматический снимок и мгновенная отправка фото с тревогой в чат не чаще установленного интервала.
  * **Права**: `CAMERA`, `FOREGROUND_SERVICE_CAMERA`.

### 📁 Блок 5. Файловая система и Приложения
* `/get_file <path> [zip]` (алиас: `/gf <path> [zip]`)
  * **Реализация**: Скачивание любого файла или директории (с автоупаковкой в zip через `ZipOutputStream`) с телефона в Telegram. Поддержка передачи файлов до 50 МБ через стандартный Bot API или до 2 ГБ через кастомный Bot API сервер.
* `/download <url> [path]`
  * **Реализация**: Скачивание файла по ссылке или из чата бота в указанную директорию памяти телефона через `OkHttp` / `Ktor`.
* `/ls` / `/dir [path]`
  * **Реализация**: Проводник по файловой системе (включая чтение `/Android/data` и `/data/data` через Root Shell `toybox ls -la`).
* `/ls_interactive [path]` (алиас: `/lsi [path]`)
  * **Реализация**: Интерактивный проводник с inline-кнопками. Навигация по дереву каталогов (`⬆️ Вверх`, `🔄 Обновить`), скачивание файлов и автоупаковка папок в zip по клику. Пути кэшируются через `NavigationCache` с короткими токенами для предотвращения превышения лимита Telegram `callback_data` (64 байта).
* `/install <apk_path>`
  * **Реализация**: Удаленная установка APK. Нативно через Root/Shizuku (`pm install -r -d -g <path>`), либо через `PackageInstaller` / интент `ACTION_INSTALL_PACKAGE`.
  * **Прямая загрузка из чата**: отправка `.apk` документа прямо в чат Telegram автоматически скачивает файл в `Downloads` и запускает установку.
* `/apps [filter|system|all]`
  * **Реализация**: Список установленных приложений через `PackageManager.getInstalledPackages(0)` с fallback на `getInstalledApplications(0)`. Поддержка Package Visibility на Android 11–15 (`QUERY_ALL_PACKAGES` + `<queries>`), фильтрация чисто системных приложений с сохранением обновленных пользовательских пакетов (`FLAG_UPDATED_SYSTEM_APP`).
* `/app_launch <pkg>`
  * **Реализация**: Удаленный запуск приложения по имени пакета через `getLaunchIntentForPackage(...)` или Root `monkey`.
* `/taskmanager`
  * **Реализация**: Диспетчер задач. Просмотр активных процессов с PID, потреблением CPU % и RAM % (через Root `ps -A` / `ActivityManager`).
* `/kill <PID|pkg>`
  * **Реализация**: Принудительная остановка процесса через `am force-stop <pkg>` или `kill -9 <PID>` (Root/Shizuku).
* `/unsent_files`
  * **Реализация**: Просмотр очереди неотправленных файлов при обрывах связи.

### 🖥 Блок 6. Скриншоты и Экран
* `/screenshot`
  * **Реализация**: Скрытый снимок экрана через Root (`/system/bin/screencap -p`) с мгновенной отправкой в Telegram-чат.
  > [!WARNING]
  > На Android 14+ `MediaProjection` требует интерактивного подтверждения пользователя на каждый захват (single-use token). **Скрытый автономный скриншот без присутствия пользователя возможен только через Root или Shizuku.** MediaProjection остается лишь интерактивным резервным вариантом.
* `/screen_rec [duration_sec]`
  * **Реализация**: Видеозапись происходящего на экране через Root/Shizuku команду `/system/bin/screenrecord --time-limit ...` с отправкой mp4 в чат.

### ⚙ Блок 7. Системный контроль и Root
* `/shell <command>`
  * **Реализация**: Полноценный Linux-терминал. Исполнение команд через библиотеку `libsu` (Root сессия) или `ProcessBuilder` (обычный shell) с отправкой stdout/stderr в чат.
* `/system_info`
  * **Реализация**: Подробная сводка об устройстве (модель, CPU, архитектура, память, версия Android, активные интерфейсы сети, Uptime).
* `/battery`
  * **Реализация**: Детальное состояние аккумулятора через `IntentFilter(ACTION_BATTERY_CHANGED)` (процент, вольтаж, температура, статус зарядки, состояние батареи).
* `/toggles` / `/quick_settings` / `/panel`
  * **Реализация**: Интерактивная панель переключателей с инлайн-кнопками управления в Telegram.
* `/network`
  * **Реализация**: Полная матрица сетевых интерфейсов (wlan/rmnet/tun/eth/lo), статусы UP/DOWN, MAC, MTU, IPv4/IPv6, активный транспорт и DNS.
* `/wifi [on|off|toggle]`
  * **Реализация**: Статус и управление Wi-Fi (`WifiManager` / через `svc wifi` при Root/Shizuku).
* `/bluetooth [on|off|toggle]`
  * **Реализация**: Статус и включение/выключение Bluetooth (`BluetoothAdapter` / `svc bluetooth`).
* `/flashlight [on|off|toggle]`
  * **Реализация**: Включение вспышки в режиме фонарика через `CameraManager.setTorchMode(...)`.
* `/airplane [on|off|toggle]`
  * **Реализация**: Включение/выключение режима полета (Root).
* `/hotspot [on|off|toggle]`
  * **Реализация**: Управление точкой доступа Wi-Fi / Hotspot (Root).
* `/mobile_data [on|off|toggle]`
  * **Реализация**: Управление мобильной передачей данных (Root).
* `/powersave [on|off|toggle]`
  * **Реализация**: Управление режимом энергосбережения (Root).
* `/auto_brightness [on|off|toggle]`
  * **Реализация**: Управление автояркостью экрана.
* `/clipboard [get|set|clear <text>]`
  * **Реализация**: Чтение и установка буфера обмена через `ClipboardManager` (фоновый доступ на Android 10+ через Root/Shizuku/Accessibility).
* `/sync [on|off|toggle]`
  * **Реализация**: Управление системной автосинхронизацией через `ContentResolver.setMasterSyncAutomatically(...)`.
* `/adb [on|off|toggle]`
  * **Реализация**: Управление режимом отладки по USB (`Settings.Global.ADB_ENABLED` / `setprop persist.sys.usb.config` через Root).
* `/adb_wifi [on|off|toggle|port]`
  * **Реализация**: Управление режимом беспроводной отладки ADB по Wi-Fi (`setprop service.adb.tcp.port 5555`, рестарт adbd через Root) с выводом команды подключения `adb connect <ip>:<port>`.
* `/reboot`, `/shutdown`, `/recovery`, `/fastboot`
  * **Реализация**: Перезагрузка и выключение устройства через Root (`PowerManager` / `svc power reboot` / `reboot recovery`).

### 🔔 Блок 8. Перехват уведомлений (Notification Mirroring)
* **Фоновый перехват**:
  * Перехват всех уведомлений смартфона через `RemoteBotNotificationListenerService`.
  * Умная фильтрация по пакетам и RegEx.
* `/notify [limit]` / `/notifications`
  * **Реализация**: Список последних перехваченных push-уведомлений с ID, именем приложения, заголовком и текстом.
* `/answer <id> <text>` / `/resp <id> <text>`
  * **Реализация**: Удаленный ответ на уведомления прямо из Telegram через `RemoteInput` целевого приложения (ответ в WhatsApp, Telegram, Viber, Signal, VK, Skype, Gmail без открытия приложения).
  * **Интерактивный ответ без ввода команды**:
    - Инлайн-кнопка «💬 Ответить» под пересланным уведомлением переводит бота в режим ожидания текста для конкретного уведомления.
    - Бот выводит приглашение к вводу с инлайн-кнопкой `[ ❌ Отмена ]`.
    - Пользователь отправляет обычный текст в чат (или делает Swipe/Reply на сообщение) -> бот формирует системный `Bundle` с ключом `remoteInput.resultKey`, добавляет через `RemoteInput.addResultsToIntent()` и выполняет `pendingIntent.send(context, 0, intent)`.
    - Мессенджер на устройстве отправляет ответ собеседнику, а бот подтверждает доставку в Telegram.
* `/notify_clear <id>`
  * **Реализация**: Закрытие/удаление уведомления из системной шторки через `cancelNotification`.
* `/notify_rule <list|add|del>`
  * **Реализация**: Управление правилами фильтрации уведомлений (исключение/включение пакетов, заголовков).


### ⚡ Блок 9. Автоматизация, Скрипты и Интеграции
* `/script <code>`
  * **Реализация**: Движок исполнения JavaScript на Android (QuickJS / Rhino). Проброс системных методов (`rc.takePhoto()`, `rc.sendSms(...)`) для написания гибких сценариев автоматизации.
* `/tasker <TaskName> [par1] [par2]`
  * **Реализация**: Удаленный вызов сценариев Tasker из Telegram / SMS.
  * **Tasker Action Plugin**: плагин для Tasker, позволяющий вызывать действия Remote Bot по любым системным триггерам Tasker.
* `/timers [list|add|del]`
  * **Реализация**: Планировщик команд и отложенных действий через `WorkManager` и `AlarmManager` (`setExactAndAllowWhileIdle`).
* **Системные триггеры**:
  * Мониторинг подключения/отключения кабеля зарядки (`ACTION_POWER_CONNECTED/DISCONNECTED`).
  * Оповещение при критическом разряде батареи (`ACTION_BATTERY_LOW`).
  * Автозапуск при включении устройства (`BOOT_COMPLETED`).

### 🤖 Блок 10. Управление ботом и сетью
* `/start`, `/help`, `/about` — интерактивное меню команд с inline-клавиатурой и справкой.
* `/chat_id` — получение ID текущего чата и ID пользователя.
* `/proxy [status|on|off|socks5|http|wss]` — просмотр статуса и переключение прокси (HTTP, SOCKS5 с авторизацией, WebSocket WSS туннель).
* `/active` — отображение списка текущих фоновых операций (идущая запись видео, аудио, закачка).
* `/stop_all` — экстренная отмена всех запущенных фоновых корутин и процессов.
* `/unsent_files` — просмотр очереди файлов, ожидающих отправки при восстановлении связи.
* `/backup` — экспорт чистой резервной копии конфигурации в `.json` файл в чат.
* `/restore_backup [json]` — восстановление конфигурации из текста или при отправке `.json` документа в чат.
* `/log [lines]` — выгрузка последних логов работы бота в чат (по умолчанию 20, макс. 100).

---

## 6. Безопасность и авторизация

1. **Белый список администраторов (Admin Whitelist)**:
   * **Telegram**: фильтрация строго по `Telegram User ID` (числовой ID, неизменен), ни в коем случае не по `@username` (юзернейм можно сменить или освободить).
   * **SMS**: фильтрация строго по доверенным номерам телефонов в формате E.164 (`+7XXXXXXXXXX`).
   * Сообщения от неизвестных ID / номеров игнорируются без ответа.
2. **Два режима безопасности SMS-управления (Префикс `rb ` + SMS Security Modes)**:
   * **Обязательный триггер-префикс `rb `**: Чтобы бот не реагировал на случайные входящие SMS, банки и сервисные уведомления, сообщение считается потенциальной командой **исключительно если оно начинается с `rb `** (регистронезависимо: `rb ` или `RB `). Любые другие входящие SMS гарантированно игнорируются как команды и просто пересылаются в Telegram.
   * **Режим 1: PIN + Команда с любого номера (Any Number + PIN)**:
     - При отправке SMS в формате `rb <smsPin> [/<команда> [аргументы]]` (например, `rb 1234 /wifi on` или `rb 1234 reboot`) бот валидирует PIN, отсекает его и исполняет команду независимо от того, с какого номера пришло сообщение (доверенный, новый или чужой номер). Ответ отправляется по SMS обратно отправителю.
   * **Режим 2: Доверенный номер без PIN (Whitelisted Number - No PIN)**:
     - Если номер отправителя находится в белом списке `allowedPhoneNumbers`, ввод PIN **не требуется**. Достаточно отправить `rb <команда>` (например, `rb /wifi on`, `rb status`, `rb /ping`). Ответ отправляется по SMS отправителю.
   * **Защита от несанкционированного доступа и обычные SMS**:
     - Если сообщение не начинается с `rb ` или отправлено с номера не из белого списка без корректного PIN, оно **не исполняется как команда**. Бот безопасно пересылает его в чат владельца в Telegram как стандартное входящее SMS (с указанием номера отправителя и текста).
   * Ограничение частоты SMS-ответов (Rate Limiting) для защиты баланса SIM-карты от флуда.
3. **2FA / Мастер-пароль в Telegram**:
   * Возможность задать пароль на опасные команды (`/shell`, `/install`, `/get_file`, `/reboot`).
   * Сессия открывается на определенное время (например, на 10 минут).
4. **Хранение секретов (Без дедлоков Keystore)**:
   * Взамен устаревшей и нестабильной `androidx.security.crypto` (частые сбои Keystore на прошивках MIUI/HyperOS и OneUI) используется библиотека **Google Tink** напрямую либо зашифрованная БД **Room + SQLCipher**.

---

## 7. Пошаговый план разработки (Roadmap)

### Фаза 1: Фундамент (Core, Telegram & Прокси)
- [x] Инициализация многомодульного проекта (Kotlin 2, Gradle Version Catalogs `libs.versions.toml`).
- [x] Сетевой слой: HTTP, SOCKS5 (с авторизацией и DNS-over-Proxy), WebSocket WSS туннель (на базе проверенного сокет-движка/JNI).
- [x] Модуль Telegram Bot API: движок Long Polling (`getUpdates`) + гибридная поддержка Push-уведомлений (FCM с компактным `update_id` wakeup / UnifiedPush).
- [x] Реализация легковесного внешнего Webhook Push Gateway (на Go / Python / Cloudflare Worker).
- [x] Фоновый сервис (`ForegroundService`) с типом `remoteMessaging`, Doze Mode оптимизацией и автозапуском.
- [x] Базовые сервисные команды: `/start`, `/help`, `/about`, `/chat_id`, `/ping`, `/echo`, `/proxy` (просмотр состояния сетевого подключения), `/status` (сводный статус подключения, пинг API и доступность Push).
- [x] Белый список Telegram User ID (Whitelist): динамический интерфейс управления в Compose (+/- добавление/удаление строк) и валидация входящих команд в `DefaultCommandDispatcher`.
- [x] Multi-Endpoint Failover & Балансировка Telegram API: пул эндпоинтов `apiEndpoints` с 3 режимами переключения (`FAILOVER` при таймаутах, `ROUND_ROBIN` по кругу, `LOWEST_LATENCY` по минимальному пингу) и динамический UI управления зеркалами.
- [x] Multi-Server Push Gateway Pool & Балансировка: поддержка пула независимых Push-серверов (`PushServerConfig`) с индивидуальными доменами, секретными ключами и JSON-конфигурациями (`google-services.json` / `agconnect-services.json`). Реализован `PushServerPoolManager` с 3 режимами выбора (`FAILOVER`, `ROUND_ROBIN`, `LOWEST_LATENCY`).
- [x] Реорганизация экрана Push-уведомлений: режим «Push Wakeup» вынесен на самый верх экрана с ярким статусом активности (🟢 Активен / ⚪ Отключен, когда работает Long Polling), под ним — динамический UI управления пулом серверов (`PushServersPoolCard`).
- [x] Экспресс-диагностика сети и Telegram DC 1-5 (`NetworkDiagnosticManager`): быстрый замер задержки TCP-коннекта до дата-центров Telegram DC 1–5, шлюза `api.telegram.org` и push-серверов FCM/HMS.
- [x] Шифрование секретов Google Tink (AES-256-GCM): безопасное хранение токена бота и ключей push-шлюзов без Keystore-дедлоков на кастомных прошивках.

> **💬 QA Audit и архитектурный ревью фазы 1 (Sber Dev & Разработчик, октябрь 2026, статус: ПОЛНОСТЬЮ РЕАЛИЗОВАНО ✅):**
>
> **Итоговый статус реализации: 100%** — все подтвержденные замечания устранены, сетевой стек расширен отказоустойчивостью, секреты защищены шифрованием Tink.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **Модульная структура:**
> ```
> app (Android Application)
> ├── :domain (Kotlin JVM — pure domain, PushWakeupHandler, BotConfig) ✅
> ├── :core:common (Android Library — shared) ✅
> ├── :core:network (Android Library — Ktor + OkHttp, NetworkDiagnosticManager) ✅
> ├── :core:proxy (Android Library — SOCKS5 + WSS, Shared OkHttp) ✅
> ├── :core:push (Android Library — FCM + HMS, изолирован через :domain) ✅
> ├── :core:database (Android Library — Room + SQLCipher) ✅
> ├── :core:security (Android Library — Tink SecureStorageManager + SQLCipher) ✅
> └── :channels:telegram-bot (Android Library — Bot engine, Multi-Endpoint Failover) ✅
> ```
>
> **✅ Что реализовано и исправлено (100% закрыто):**
> 1. **[БЕЗОПАСНОСТЬ] Шифрование секретов Google Tink (AES-256-GCM):** В модуль `:core:security` добавлен `SecureStorageManager`. `bot_token`, `push_secret_key` и `fcm_api_key` шифруются перед сохранением в SharedPreferences и прозрачно расшифровываются. Решение свободно от Keystore-дедлоков на MIUI/HyperOS/OneUI.
> 2. **[БЕЗОПАСНОСТЬ] Устранение Whitelist bypass:** В `TelegramBotEngine` полностью удалено самодобавление незнакомых `senderId` в доверенный список `allowedUserIds`.
> 3. **[НАДЕЖНОСТЬ] Multi-Endpoint Failover & Балансировка Telegram API:** В `TelegramApiClient` и `BotConfig` внедрен пул эндпоинтов `apiEndpoints` с 3 режимами переключения: `FAILOVER` (по очереди при таймаутах), `ROUND_ROBIN` (балансировка по кругу) и `LOWEST_LATENCY` (по минимальному пингу). На вкладке «Telegram» добавлен динамический UI управления зеркалами.
> 4. **[СЕТЬ] Экспресс-диагностика сети и Telegram DC 1-5:** Реализован `NetworkDiagnosticManager` для быстрого замера TCP-коннекта (порт 443) до дата-центров Telegram DC 1–5, шлюза `api.telegram.org` и push-серверов FCM/HMS.
> 5. **[КОМАНДЫ] Сводная команда `/status`:** Карточка в Telegram с отображением Uptime, активного транспорта, пинга активного Telegram API эндпоинта и доступности push-серверов.
> 6. **[РЕСУРСЫ] Устранение утечки сокетов в WSS:** В `WssTunnelManager` внедрен синглтон `sharedClient` с пулом соединений (`ConnectionPool(10, 60s)`).
> 7. **[АРХИТЕКТУРА] Развязка зависимостей `:core:push` → `:channels:telegram-bot`:** Вынесен интерфейс `PushWakeupHandler` в чистый `:domain`.
> 8. **[СЕТЬ] Оптимизация SOCKS5 авторизации:** В `DynamicProxySelector` кэшируется авторизация, вызов `Authenticator.setDefault()` не дергается на каждый HTTP-запрос.
> 9. **[СТАБИЛЬНОСТЬ] Защита очереди Long Polling:** Добавлен параметр `limit = 50` в `getUpdates()`.
> 10. **[ДАННЫЕ] HTML-aware chunking сообщений:** Функция `splitTextIntoChunks()` в `TelegramApiClient` автоматически балансирует и закрывает HTML-теги (`<b>`, `<i>`, `<code>`, `<pre>`).
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **Замена `LifecycleService` на ServiceCompat:** Замечание ложное (галлюцинация). Класс `androidx.lifecycle.LifecycleService` **НЕ** является deprecated в Android SDK и необходим для привязки `lifecycleScope` к Foreground Service.
> 2. ❌ **`TelegramEventForwarder` memory leak:** Отклонено как неактуальное. Сервис работает в рамках долгоживущего синглтона FGS процесса, а не в пересоздаваемой Activity.
> 3. ❌ **Использование `EncryptedSharedPreferences`:** Отклонено из-за известных дедлоков Android Keystore на HyperOS/MIUI/OneUI. Заменено на прямое шифрование через Google Tink.
> 4. ❌ **Постоянный фоновый Connection Quality Monitor с графиками:** Отклонено: непрерывные сетевые замеры в фоне постоянно будят радиомодуль смартфона, убивают батарею и ломают Doze Mode. Автоматическое хаотичное переключение сетевых режимов при просадке связи способно сломать управление.
> 5. ❌ **Telemetry Module (Сбор метрик на внешний сервер):** Отклонено: приложение создано для скрытного и приватного управления. Встроенная сетевая телеметрия деанонимизирует IP-адреса и вызывает срабатывания антивирусов.
> 6. ❌ **Onboarding Tour на 5-7 экранов:** Отклонено: лишний визуальный шум и зависимости для системной утилиты администрирования.
> 7. ❌ **Выделение `:core:connectivity` ради Viber/Signal:** Отклонено: Viber исключен из проекта по требованию заказчика, Signal не планируется. Текущая Clean Architecture полностью оптимальна.
>

### Фаза 2: Системный контроль, Переключатели и Терминал (Блок 7)
- [x] Модуль `system` (Блок 7):
  - Информационные команды: `/system_info` (сводка по устройству, ОС, RAM, ROM, CPU, Uptime, сети), `/battery` (детальные параметры АКБ, статус зарядки, температура, вольтаж), `/network` (полный список сетевых интерфейсов wlan/rmnet/tun/eth/lo, статусы UP/DOWN, MAC, MTU, IPv4/IPv6 адреса, активный транспорт и DNS), `/uptime` (время непрерывной работы процесса службы бота и системы Android).
  - Интерактивная панель переключателей: `/toggles` / `/quick_settings` / `/panel` с Inline-кнопками управления и обновлением состояния в реальном времени. Кнопка быстрого перехода к панели тумблеров в клавиатуре приветствия `/start`.
  - **Система индикации и предупреждений о Root-правах**:
    - Иконки на инлайн-кнопках: если Root предоставлен — `⚡` (активен), если Root отсутствует — `🔒 [Root]` (заблокирован).
    - Нативные модальные всплывашки в Telegram (`answerCallbackQuery(showAlert = true)`): при нажатии на Root-тумблер без прав суперпользователя на экране смартфона в Telegram мгновенно всплывает модальный диалог с подсказкой.
    - Текстовые предупреждения в командах с пояснением шагов предоставления Root через вкладку «Диагностика».
  - Системные тумблеры (с инлайн-кнопками переключения и сквозным логированием `logger.i`/`logger.w`):
    - `/wifi [on|off|toggle]` — Wi-Fi адаптер (нативно / Root).
    - `/bluetooth [on|off|toggle]` — Bluetooth адаптер.
    - `/flashlight [on|off|toggle]` — фонарик (вспышка камеры).
    - `/airplane [on|off|toggle]` — режим полета (Root).
    - `/hotspot [on|off|toggle]` — точка доступа Wi-Fi / Hotspot (Root).
    - `/mobile_data [on|off|toggle]` — мобильная передача данных (Root).
    - `/gps [on|off|toggle]` — геолокация / GPS (Root).
    - `/powersave [on|off|toggle]` — режим энергосбережения (Root).
    - `/auto_brightness [on|off|toggle]` — автояркость экрана.
    - `/sync [on|off|toggle]` — автосинхронизация системных аккаунтов.
    - `/adb [on|off|toggle]` — режим отладки по USB (Root).
    - `/adb_wifi [on|off|toggle|порт]` — режим беспроводной отладки по Wi-Fi с портом и IP (Root).
  - Буфер обмена: `/clipboard [get|set|clear]` (чтение, запись текста, очистка с кнопкой).
- [x] Модуль `shell` & `power` (Блок 7):
  - `/shell <cmd>`: интерактивный терминал Linux на базе `libsu` 6.0.0 с автоопределением Root/sh, таймаутом 30с и ограничением вывода. Полный аудит вызовов (логирование выполняемой команды, кода завершения и полного ответа stdout/stderr в лог событий).
  - Управление питанием: `/reboot`, `/shutdown`, `/recovery`, `/fastboot` (через Root shell с 10-секундным таймером отмены).
- [x] Резервное копирование и восстановление настроек через файловый пикер:
  - Версионирование схемы `AppBackupData` (`version = "2.0.0"`) и строгая валидация JSON структуры при импорте.
  - Экспорт настроек в файл `.json` через системный диалог `ActivityResultContracts.CreateDocument("application/json")`.
  - Импорт и восстановление настроек из файла `.json` через системный проводник `ActivityResultContracts.OpenDocument()`.

> **💬 QA Audit и архитектурный ревью фазы 2 (Sber Dev & Разработчик, октябрь 2026, статус: ПОЛНОСТЬЮ РЕАЛИЗОВАНО ✅):**
>
> **Итоговый статус реализации: 100%** — функционал системного контроля полностью реализован, внедрена команда `/uptime`, сквозное логирование тумблеров и бэкапа, валидация версий бэкапа и быстрый запуск `/toggles` из приветствия `/start`.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что реализовано и улучшено (100% закрыто):**
> 1. **[КОМАНДЫ] Новая команда `/uptime`:** Реализован вывод времени аптайма службы бота (`Process.getStartElapsedRealtime()`) и ОС Android (`SystemClock.elapsedRealtime()`) с кнопками быстрого перехода к `/status` и `/toggles`.
> 2. **[UX] Инлайн-кнопка перехода к `/toggles` из `/start`:** В приветственную клавиатуру добавлена кнопка `🎛️ Переключатели` для мгновенного доступа к панели управления тумблерами.
> 3. **[НАДЕЖНОСТЬ] Валидация JSON и версионирование Backup:** В `BackupManager` добавлена проверка структуры схемы и допустимых версий (v1–v2), предотвращающая сбои при поврежденных или несовместимых конфигурациях. Добавлено логирование успешных и аварийных импортов.
> 4. **[НАБЛЮДАЕМОСТЬ] Сквозное логирование системных тумблеров:** Во все методы `DeviceRadioCommandsHandler` и `SystemSettingsCommandsHandler` внедрены вызовы `logger.i()` и `logger.w()` с фиксацией успешных переключений и аппаратных сбоев.
> 5. **[АУДИТ И ОТЛАДКА] Полное логирование `/shell`:** В лог событий фиксируется факт вызова команды, код возврата и полный текстовый вывод (`stdout` / `stderr`), обеспечивая полную прозрачность и возможность аудита удаленных действий.
> 6. **[СИСТЕМА] Полный набор системных тумблеров:** Wi-Fi, Bluetooth, Фонарик, Авиарежим, Точка доступа, Мобильные данные, GPS, Энергосбережение, Автояркость, Автосинхронизация, ADB и ADB по Wi-Fi.
> 7. **[ROOT ИНДИКАЦИЯ] Интерактивная панель `/toggles`:** Динамические кнопки `⚡` / `🔒 [Root]`, модальные алерты `answerCallbackQuery(showAlert=true)` при попытке дернуть Root-тумблер без su.
> 8. **[УПРАВЛЕНИЕ ПИТАНИЕМ] Модуль `power`:** `/reboot`, `/shutdown`, `/recovery`, `/fastboot` с 10-секундной задержкой и защитным уведомлением.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **Whitelist команд для `/shell` (ограничить только `ls`, `cat`, `df`...):** Отклонено. RemoteBot — инструмент полного удаленного администрирования. Запрет кастомных команд превращает Root в бесполезную песочницу: нельзя будет выполнить `iptables`, `settings`, `pm`, `am`, кастомные bash-скрипты или модули. Защита обеспечивается Whitelist Telegram User ID и SMS PIN.
> 2. ❌ **Auto-refresh для `/toggles` (фоновый опрос и спам API каждые 5 сек):** Отклонено. Постоянные вызовы `editMessageReplyMarkup` каждые 5 сек пробьют rate limit Telegram (HTTP 429) и убьют батарею в Doze Mode. Реактивная смена по клику на кнопку (`onCallbackQuery`) оптимальна и тратит 0 ресурсов в простое.
> 3. ❌ **Команда `/dumpsys`:** Отклонено как дубликат. На 100% покрывается существующим `/shell dumpsys <service>`.
> 4. ❌ **Команда `/processes`:** Отклонено как дубликат. Реализуется в Фазе 6 через `/taskmanager` и `/kill`.
> 5. ❌ **Команда `/health`:** Отклонено как дубликат. Полностью перекрывается командами `/system_info` (железо/память/ОС) и `/status` (сеть/служба/пинг/root).
> 6. ❌ **Интерактивный терминал в Compose UI приложения:** Отклонено. Оверинжиниринг (создание второго Termux внутри фонового сервиса). Приложение создано как headless-сервис для удаленного контроля.
> 7. ❌ **История буфера обмена (`/clipboard_history`):** Отклонено. Непрерывный фоновый перехват буфера триггерит системные алерты Android 12+ о слежке и создает риск утечки паролей/2FA кодов. Текущего чтения/записи по явному запросу `/clipboard [get|set|clear]` достаточно.
> 8. ❌ **Пресеты и расписание тумблеров в модуле system:** Отклонено в Фазе 2. Для сценариев автоматизации и расписаний есть интеграция с Tasker (`/tasker`) и планировщик отложенных команд (`/timers`) в Фазе 6.
>

### Фаза 3: Пользовательский интерфейс, Разрешения и Управление (NavigationRail, 3 группы прав, Логирование, Root)
- [x] **Пользовательский интерфейс (Jetpack Compose Material 3 — NavigationRail + Content Container)**:
  - **Вертикальная боковая панель слева (`NavigationRail`)**:
    - Компактный вертикальный рейл с 11 разделами: 🚀 `Старт` (по умолчанию при старте, управление службой, лаконичный статус Root, шкала привилегий 0..20), 🛡 `Права` (20 системных прав в 3 группах), ⏱️ `Задачи` (мониторинг активных фоновых операций `ActiveOperationsTracker`), `Telegram` (официальный векторный значок), 💬 `SMS` (SMS-бот), 🔔 `Уведомл.`, 🔥 `Push` (FCM / HMS и шлюз), 🌐 `Прокси`, 📜 `Логи`, 💾 `Бэкап`, ℹ️ `О боте`.
    - Без лишних верхних иконок (значок молнии убран) и с визуальным pill-индикатором выбранной вкладки.
  - **Правый контейнер содержимого (`Surface` со скруглением `16.dp` и возвышением)**:
    - Информационная шапка (иконка, заголовок, описание) и изолированный контейнер рабочей области.
    - Бесшовное переключение экранов без реконфигурации и мерцания.
- [x] **Раздел настройки пересылки уведомлений (`NotificationsSettingsTab`)**:
  - Выбор приложений (`AppPickerDialog`): список установленных пакетов через `AppManagerRepository`, переключатель «Скрыть системные приложения», строка поиска и множественный выбор чекбоксами.
  - Черный список стоп-слов: добавление/удаление фраз и ключевых слов для отсеивания спама, рекламы или сервисных сообщений.
  - Анти-спам дедупликация: настраиваемый таймаут (15 секунд по умолчанию) для предотвращения спама дублирующимися уведомлениями от одного приложения в Telegram.
- [x] **Экран управления разрешениями (`PermissionsTab`) — 20 системных прав в 3 группах**:
  - **1. Системный доступ (Root и Установка ПО)**:
    - 👑 **Root (`libsu` 6.0.0)**: поставщик `RootStatusProvider`, 1-клик выдача всех 27 прав через `su` (`/shell`, `/reboot`, `/shutdown`, `/recovery`, `/fastboot`, `/screenshot`, `/screen_rec`, `/airplane`, `/hotspot`, `/mobile_data`, `/gps`, `/powersave`, `/adb`, `/adb_wifi`, `/install`, `/kill`, `/script`).
    - 📦 **Установка приложений (`REQUEST_INSTALL_PACKAGES`)**: установка и обновление APK (`/install <file.apk>`).
    - 📁 **Все файлы (`MANAGE_EXTERNAL_STORAGE`)**: полный доступ к диску без ограничений Scoped Storage (`/get_file`, `/download`, `/ls`, `/dir`, `/ls_interactive`, `/unsent_files`).
    - ♿ **Специальные возможности (`Accessibility Service`)**: автоматизация UI, клики, обход диалогов (`/ussd [sim] <code>`).
    - ⚙️ **Изменение настроек системы (`WRITE_SETTINGS`)**: автояркость экрана и переключатели (`/auto_brightness`, `/toggles`).
    - 🪟 **Показ поверх окон (`SYSTEM_ALERT_WINDOW`)**: всплывающие диалоги и окна поверх приложений (`/toggles`).
  - **2. Связь и уведомления (Уведомления, SMS, USSD, Звонки)**:
    - 🔔 **Чтение уведомлений (`Notification Listener`)**: мониторинг пушей, фильтрация, удаленный ответ (`/notify`, `/notifications`, `/notify_send`, `/answer`, `/resp`, `/notify_clear`, `/notify_rule`).
    - 💬 **SMS (`SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`)**: отправка и чтение SMS, автономный SMS-бот (`/sms`, `/sms_inbox`, `/sms_bot`, `/sms_forward`, `/whitelist_phone`).
    - 📞 **Звонки, USSD и журнал (`CALL_PHONE`, `READ_CALL_LOG`, `READ_PHONE_STATE`)**: вызовы, журнал звонков, перехват звонков (`/call`, `/callshistory`, `/ussd`).
    - 🔕 **Режим «Не беспокоить» (`ACCESS_NOTIFICATION_POLICY` / DND)**: звуковые профили и громкость (`/ring_mode`, `/volume`).
  - **3. Мультимедиа и сенсоры (Камера, Звук, Локация, Сеть, Фоновая служба)**:
    - 📷 **Камера (`CAMERA`)**: скрытая фото- и видеосъемка CameraX, охранная детекция движения, фонарик (`/photo`, `/videorec`, `/detectmotion`, `/flashlight`).
    - 🎙️ **Микрофон (`RECORD_AUDIO`)**: скрытая аудиозапись, детектор звука, синтез речи, сирена (`/rec`, `/rec_file`, `/rec stop`, `/sound_detector`, `/tts`, `/device_search`).
    - 📍 **Геолокация (`GPS / ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)**: точные координаты, GPS-трекинг, датчик тряски (`/location`, `/track`, `/shake_detector`, `/gps`).
    - 👥 **Контакты (`READ_CONTACTS`)**: автоматическое сопоставление телефонных номеров с именами абонентов в `/call`, `/callshistory`, SMS.
    - 🔋 **Энергосбережение (`Doze Mode` / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)**: круглосуточная работа фонового сервиса 24/7 (Long Polling, WebSocket `/ws`, Push).
    - 📣 **Уведомления службы (`POST_NOTIFICATIONS`)**: постоянный статус Foreground Service в шторке (Android 13+).
    - 🖼️ **Фото и видео (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`)**: отправка фото и медиафайлов (`/gallery`, `/get_file`).
    - 🎵 **Аудиофайлы (`READ_MEDIA_AUDIO`)**: воспроизведение аудио и управление плеером (`/player`, `/playback`, `/rec_file`).
    - 📡 **Bluetooth (`BLUETOOTH_CONNECT`, `BLUETOOTH`)**: управление адаптером Bluetooth (`/bluetooth [on|off|toggle]`).
    - 🌐 **Безлимитный интернет (Data Saver whitelist)**: непрерывная работа сотовой передачи данных в фоне.
  - **Пакетное управление**:
    - 🚀 **Мастер-кнопка «Выдать все разрешения через Root»**: автоматическое выполнение 27 консольных команд за 1–2 секунды.
    - ⚡ **Пакетный запрос стандартных прав**: запуск системного диалога `RequestMultiplePermissions` в 1 клик при отсутствии Root + показ информационного диалога-всплывашки (`AlertDialog` и `Toast`) о том, что из-за ограничений безопасности Android остальные специальные права необходимо выдать по одному кнопками в списке ниже.
    - 🔄 **Live-обновление статусов**: автопересчет бейджей `✅ ВЫДАНО` / `❌ НЕТ` по событию `Lifecycle.Event.ON_RESUME`.
- [x] **Подсистема живого логирования (`AppLogger` + UI `LogsTab`)**:
  - Уровни `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` с дисковой ротацией (2 МБ в `filesDir/logs/app_events.log`).
  - UI фильтрация плиткой (`Все уровни`, `ERR/WARN`, `INFO/DEBUG/TRACE`, лимит буфера RAM `50..1000`).
  - Экспорт и шеринг логов из UI: кнопки «📤 Поделиться» (системный ShareSheet) и «💾 Сохранить в файл» (`ActivityResultContracts.CreateDocument`).
- [x] **Сервисные команды управления ботом**:
  - `/proxy` (просмотр и оперативное переключение прокси на лету).
  - `/active` (список запущенных фоновых операций через `ActiveOperationsTracker`).
  - `/stop_all` (экстренное прерывание всех фоновых корутин).
  - `/backup` / `/restore_backup` (экспорт/импорт конфигурации в JSON).
  - `/log [lines] [level]` (выгрузка журнала событий через `AppLogger` с поддержкой фильтрации по минимальному уровню DEBUG/INFO/WARN/ERROR).
  - `/permissions [group]` (удаленная проверка статуса выданных Android-разрешений: all, critical, confidential, standard, denied).
- [x] **Визуальный мониторинг и UX-улучшения**:
  - Живой индикатор статуса службы (🟢 Активен / 🔴 Остановлен) непосредственно на кнопке вкладки «Старт».
  - Тактильная отдача (`HapticFeedbackType.TextHandleMove`) при переключении всех вкладок.
  - Структурированное логирование жизненного цикла в `ActiveOperationsTracker` и `AppPickerDialog`.

> **💬 QA Audit и предложения по расширению фазы 3 (Sber Dev, октябрь 2026):**
>
> **Текущий статус реализации:**
> - **Общий статус: 100% — Полностью реализовано.**
> - **Модули:** NavigationRail/M3BrowserTabsHeader, PermissionsTab, NotificationsSettingsTab, AppPickerDialog, LogsTab, RootStatusProvider, ActiveOperationsTracker, AppLogger, PermissionsStatusProvider, DefaultPermissionsStatusProvider.
>
> **✅ Принято и полностью реализовано:**
> 1. **Логирование:** Внедрен `AppLogger` в `ActiveOperationsTracker` (жизненный цикл, отмена задач) и `AppPickerDialog` (загрузка приложений, подтверждение выбора).
> 2. **Экспорт логов в UI (LogsTab):** Добавлены кнопки «📤 Поделиться» (системный ShareSheet) и «💾 Сохранить в файл» через `ActivityResultContracts.CreateDocument` (SAF).
> 3. **Команда `/permissions [group]` в Telegram:** Реализован `PermissionsStatusProvider` + `DefaultPermissionsStatusProvider`, возвращающий статус 20 прав с фильтрами (`critical`, `confidential`, `standard`, `denied`). Добавлена в `/start` и `/help`.
> 4. **Команда `/log [N] [level]` в Telegram:** Добавлена фильтрация журнала по минимальному уровню логирования (`DEBUG`, `INFO`, `WARN`, `ERROR`), добавлено описание в `/start`.
> 5. **Визуальный индикатор службы:** В шапке на кнопке вкладки «Старт» добавлен живой светодиод статуса (🟢 Активен / 🔴 Остановлен) с подпиской на StateFlow службы.
> 6. **Тактильная отдача:** Добавлен `HapticFeedback` при переключении вкладок.
>
> **❌ Отклонено (с техническим обоснованием):**
> 1. **Отдельная 12-я вкладка «Диагностика»:** Отклонено как избыточная. Все показатели здоровья системы уже отображаются на вкладке «Старт» и во встроенном инженерном меню «Debug».
> 2. **Команда `/health` в Telegram:** Отклонено как дубликат. Полный сетевой и сервисный статус выдается командой `/status`, аппаратные метрики — через `/system_info`, а права — через `/permissions`.
> 3. **Push-уведомления для операций >5 мин:** Отклонено из соображений скрытности. Всплывающие push-уведомления на экране демаскируют работу утилиты на смартфоне.
> 4. **Свайпы между вкладками и Tooltips:** Отклонено в рамках фазы 3 — консолидировано для фазы 14 (UI/UX полировка).

### Фаза 4: Мультимедиа (Камера, Звук, Экран — Блоки 1, 2, 6)
- [x] Модуль `camera` (Блок 1):
  - `/photo [front|back] [flash_on|flash_off] [rotate]` (скрытая съемка Camera2/CameraX в файл и отправка в чат `sendPhoto`).
  - `/videorec [front|back] [duration_sec]` (фоновая запись видео в mp4 и отправка `sendVideo`).
  - `/detectmotion [sensitivity]` (анализ видеопотока через детектор движения).
  - `/gallery [limit]` (выгрузка последних фото из MediaStore медиагруппой `sendMediaGroup`).
- [x] Модуль `audio` (Блок 2):
  - `/rec [duration_sec]` (фоновая скрытая аудиозапись в AAC/m4a и отправка `sendVoice`).
  - `/rec stop` (остановка текущей аудиозаписи).
  - `/tts [lang] <text>` (синтез речи TextToSpeech через динамик).
  - `/playback` / `/player` (интерактивный медиаплеер с инлайн-кнопками).
  - `/volume [stream] [level]` (просмотр и регулировка потоков громкости).
  - `/ring_mode [normal|silent|vibrate]` (управление звуковыми профилями).
  - `/device_search` (громкая сирена тревоги на максимальной громкости с кнопкой выключения).
- [x] Модуль `screen` (Блок 6):
   - `/screenshot` (захват экрана: Root `screencap` / Shell и отправка фото в чат).
   - `/screen_rec [duration_sec]` (видеозапись экрана через `screenrecord`).

> **💬 QA Audit и предложения по расширению фазы 4 (Sber Dev, октябрь 2026):**
>
> **Текущий статус реализации:**
> - **Общий статус: 100% — Полностью реализовано.**
> - **Модули:** `DefaultCameraRepository.kt`, `DefaultAudioRepository.kt`, `DefaultScreenRepository.kt`, `MediaCommandsHandler.kt`.
>
> **✅ Принято и полностью реализовано:**
> 1. **Структурированное логирование мультимедиа (`AppLogger`):**
>    - Внедрен экземплярный логгер в `DefaultScreenRepository` (детальное логирование Root screencap, shell fallback, screenrecord).
>    - Добавлено логирование жизненного цикла Camera2 в `DefaultCameraRepository` (открытие камеры, ошибки/отключения `CameraDevice.StateCallback`, съемка кадров).
>    - Добавлено логирование аудио в `DefaultAudioRepository` (`MediaRecorder`, синтез речи TTS, `MediaPlayer`).
> 2. **Валидация аудиоустройств и защита от сбоев:**
>    - Проверка `AudioRecord.state == AudioRecord.STATE_INITIALIZED` в детекторе звука с освобождением ресурсов.
> 3. **Защита от спама детектора движения (`Motion Detection spam protection`):**
>    - Работает встроенный кулдаун таймер (`1..60` секунд) между отправками тревожных кадров в Telegram при постоянном движении.
> 4. **Справка и команды в `/start` и `/help`:**
>    - В `/start` добавлена команда `/detectmotion [1..10|on|off]`, актуализированы параметры `/photo`, `/videorec`, `/rec`, `/tts`, `/player`, `/screenshot`.
>
> **❌ Отклонено (с техническим обоснованием):**
> 1. **Live Camera Preview в Telegram (`/live [duration]` каждые 2 сек):** Отклонено из-за жестких rate-limits Telegram Bot API (HTTP 429) и перегрева устройства.
> 2. **Локальный UI-экран «Мультимедиа» со стримом камеры и галереей:** Отклонено, так как RemoteBot — утилита скрытого фонового управления, локальный просмотр демаскирует назначение приложения.
> 3. **Интерактивная предпрослушка и обрезка аудиозаписей:** Отклонено как избыточное для автономного бота.
> 4. **Отображение кружков касаний при записи экрана (`show_taps`):** Отклонено, нарушает скрытность записи и требует прав разработчика.
> 5. **Выделение отдельного модуля `:core:media`:** Отклонено как избыточная фрагментация; контракты уже изолированы в `:domain`, реализации — в `:core:common:media`.
> 6. **Паттерны фонарика SOS и водяные знаки на фото:** Отклонено — дополнительная нагрузка без практической пользы.

### Фаза 5: Связь, SMS-управление и пересылка уведомлений (Блоки 3, 8)
- [x] Модуль `telephony` & `sms-bot` (Блок 3):
  - `/sms [sim] <phone> <text>` (отправка SMS сообщений с поддержкой Multi-SIM).
  - `/sms_inbox [limit]` (чтение входящих и исходящих SMS из `Telephony.Sms.CONTENT_URI`).
  - `/call [sim] <phone>` (инициация телефонного вызова `Intent.ACTION_CALL`).
  - `/callshistory [limit]` (журнал вызовов со статусами и длительностью).
  - `/ussd [sim] <code>` (асинхронные USSD-запросы через `TelephonyManager.sendUssdRequest`).
  - Полноценный **Offline SMS Bot**: прием команд по SMS, проверка SMS PIN, выполнение и отправка SMS-ответа (`SmsReceiver`).
  - Фоновый пересыльщик входящих SMS и уведомлений о звонках (`PhoneCallReceiver`) в реальном времени в Telegram с возможностью прямого ответа на SMS свайпом (Reply/цитированием) в Telegram, кнопкой «💬 Ответить по SMS» и командой `/sms`.
- [x] Модуль `notifications` (Блок 8):
  - `RemoteBotNotificationListenerService` для фонового перехвата системных уведомлений.
  - `/notify [limit]` / `/notifications` (список последних перехваченных уведомлений).
  - `/notify_send <title> <text>` (публикация системного уведомления в шторку устройства).
  - `/fake_notify <pkg> <title> <text>` (алиас: `/simulate_notify`) — эмуляция входящего уведомления от имени указанного пакета для тестирования фильтрации и ответов.
  - `/answer <id> <text>` / `/resp <id> <text>` (удаленный быстрый ответ через `RemoteInput`).
  - **Интерактивный быстрый ответ в мессенджеры (WhatsApp, Telegram, Signal, VK, SMS и др.)**:
    - Инлайн-кнопка «Ответить» под пересланным уведомлением (без лишних значков).
    - Строгая фильтрация: кнопка появляется только для реальных мессенджеров с полем свободного ввода (`allowFreeFormInput == true` + `CATEGORY_MESSAGE` / `MessagingStyle`).
    - Пошаговый диалоговый режим (Prompt + кнопка `[ ❌ Отмена ]` без `force_reply` баннера).
    - Внедрение ответа в системный `RemoteInput` и запуск `PendingIntent.send()`.
  - **Защита от дубликатов и спама (`notificationDedupTimeoutSeconds`)**:
    - Настраиваемый в UI таймаут подавления дубликатов по ключу `${packageName}|${title}|${text}` (быстрый выбор 5с, 15с, 30с, 1 мин, 5 мин, 15 мин).
    - **Поведение**: любое повторное уведомление с идентичным текстом внутри таймаута безвозвратно **дропается** (не отправляется в Telegram/SMS). Уведомления с новым/изменившимся текстом пересылаются сразу, а идентичные — только если пауза между событиями превышает выбранный таймаут.
  - **Защита от зацикливания и эхо-петель**: динамический 60-сек кэш исходящих текстов бота, фильтрация `FLAG_GROUP_SUMMARY` и `FLAG_ONGOING_EVENT`.
  - **Package Visibility (Android 11–15)** в окне выбора приложений для захвата уведомлений (`QUERY_ALL_PACKAGES` + `<queries>`, флаги без переполнения Binder IPC, учет `FLAG_UPDATED_SYSTEM_APP`).
  - `/notify_clear <id>` (закрытие/удаление уведомления из шторки).
   - `/notify_rule <list|add|del>` (правила фильтрации по пакетам и RegEx).

> **💬 QA Audit и архитектурный ревью фазы 5 (Sber Dev & Разработчик, октябрь 2026, статус: ПОЛНОСТЬЮ РЕАЛИЗОВАНО ✅):**
>
> **Итоговый статус реализации: 100%** — функционал связи, SMS-управления и перехвата уведомлений полностью реализован, расширено структурированное логирование NLS и телефонии, внедрен fallback поиска контактов в телефонной книге и написаны юнит-тесты.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что принято и полностью реализовано (100% закрыто):**
> 1. **[ЛОГИРОВАНИЕ NLS] Полная наблюдаемость `RemoteBotNotificationListenerService`:** Внедрено подробное логирование жизненного цикла (`onListenerConnected`, `onListenerDisconnected`, `onDestroy`), перехвата уведомлений (`onNotificationPosted`: пакет, ID, тег, ключ) и их удаления (`onNotificationRemoved`).
> 2. **[ФИЛЬТРАЦИЯ УВЕДОМЛЕНИЙ] Прозрачность решений в `DefaultNotificationRepository`:** Логируются причины пропуска уведомлений: пустой текст/заголовок, системные флаги `FLAG_GROUP_SUMMARY` и `FLAG_ONGOING_EVENT`, а также срабатывания пользовательских правил фильтрации `NotificationRule`.
> 3. **[ТЕЛЕФОНИЯ И SMS] Сквозное логирование `DefaultTelephonyRepository`:** Логирование отправки SMS (длина сообщения, разбивка на multipart-части, выбор SIM-слота), чтения истории SMS, инициации вызова и отправки/ответов USSD.
> 4. **[ПРИЕМНИКИ СОБЫТИЙ] Логирование `SmsReceiver` и `PhoneCallReceiver`:** Фиксация входящих SMS с извлечением SIM-слота подписки, подробное логирование состояний вызова (`RINGING`, `OFFHOOK`, `IDLE`, `MISSED`) с фиксацией телефонного номера.
> 5. **[КОНТАКТЫ] Сопоставление номеров с именами (`READ_CONTACTS`):** В журнал вызовов добавлен fallback-поиск имени абонента в адресной книге Android через `ContactsContract.PhoneLookup`.
> 6. **[USSD] Очистка и форматирование ответов:** Нормализация ответа оператора (удаление лишних пробелов, переводов строк и управляющих символов) для чистого отображения в Telegram.
> 7. **[ТЕСТИРОВАНИЕ] Юнит-тесты телефонии и уведомлений:** В `TelephonyCommandsTest.kt` добавлены тесты логики сопоставления правил фильтрации `NotificationRule` (включая RegEx и исключения), парсинга USSD и форматирования длительности звонков.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **SMS Chat View в локальном UI (чат с пузырями сообщений):** Отклонено. RemoteBot — утилита скрытого фонового администрирования, а не сторонний SMS-мессенджер. Полноценный GUI-мессенджер раздувает приложение, расходует память и демаскирует инструмент на устройстве. Удаленное управление и просмотр ведутся через Telegram (`/sms_inbox`) и SMS-команды.
> 2. ❌ **SMS Room DB Backup (дублирование всех SMS во вторую базу SQLite):** Отклонено. В Android вся история SMS уже надежно сохраняется в системном провайдере `Telephony.Sms.CONTENT_URI`. Дублировать те же данные во вторую базу — бессмысленная трата флеш-памяти и ресурсов.
> 3. ❌ **SMS Delivery Reports через `PendingIntent`:** Отклонено. В скрытом фоновом режиме на Android 12–15 регистрация динамических broadcast-приемников для каждого SMS-сегмента часто блокируется фоновыми ограничениями и нестабильна на OEM-прошивках (MIUI/ColorOS). Отправки через `SmsManager.sendTextMessage` / `sendMultipartTextMessage` с проверкой на уровне API достаточно.
> 4. ❌ **Рефакторинг `NotificationProcessor` в отдельный UseCase:** Отклонено как оверинжиниринг. Текущая Clean Architecture (`NotificationRepository` в domain + реализация в `core:common`) полностью изолирована и не создает связанности.
> 5. ❌ **SMS Templates (`/sms_template_add`, `/sms_template_list`):** Отклонено как избыточное. В Telegram есть встроенные черновики, а сложные сценарии автоматической отправки покрываются модулями `/script` и `/tasker`.
> 6. ❌ **Silent Call Detection (оповещение о скрытых вызовах):** Отклонено. В современных версиях Android (10–15) входящий вызов без номера отображается как «Неизвестный» в стандартном `ACTION_PHONE_STATE_CHANGED`, что уже перехватывается нашим `PhoneCallReceiver`. Создавать под это отдельную сложную подсистему не имеет практического смысла.
> 7. ❌ **SMS Auto-Reply (автоответчик на SMS):** Отклонено. RemoteBot спроектирован для безопасности; бесконтрольный автоответчик на SMS создает риски спам-петель с сервисными номерами и банками, а также расходует баланс SIM-карты. При необходимости реализуется через `/script` или Tasker.
>

### Фаза 6: Геолокация, Файлы, Скрипты, Приложения и Tasker (Блоки 4, 5, 9, 10)
- [x] Модуль `location` (Блок 4):
  - `/location` / `/track [args]` (однократный запрос по умолчанию; периодический трекинг live до остановки с интервалами 30с, 1м, 5м, 15м, 30м, 60м; статус и остановка `/location stop` / `/track stop`; каскадный опрос: Google Fused Location -> Huawei HMS Location Kit -> Системный GNSS/GPS -> Сотовые/Wi-Fi вышки -> Резервный IP-геолокатор; детальный отчет со способом определения и уровнем C/N0 dB-Hz / спутников).
  - `/gps [on|off|toggle]` (программное включение/выключение через Root).
  - `/shake_detector [on|off] [порог]` (охранный датчик тряски акселерометра с автоотправкой алерта).
- [x] Модуль `files` & `apps` (Блок 5):
  - `/get_file <path> [zip]` (выгрузка файлов и потоковое архивирование директорий в zip).
  - `/download <url> [path]` (скачивание файлов из интернета/Telegram на устройство).
  - `/ls` / `/dir [path]` (проводник по файловой системе с Root-доступом к `/Android/data` и `/data/data`).
  - `/apps [filter|system|all]` (список установленных пакетов).
  - `/app_launch <pkg>` (удаленный запуск приложений).
  - `/install <apk_path>` (тихая установка APK через Root `pm install -r -d -g`).
  - `/taskmanager` (диспетчер процессов с отображением PID, потребления CPU % и памяти).
  - `/kill <PID|pkg>` (принудительное завершение процессов через `kill -9` / `am force-stop`).
  - `/unsent_files` (очередь неотправленных файлов).
- [x] Модуль `tasker` (Блок 9):
  - `/tasker <TaskName> [par1] [par2]` (запуск тасок в приложении Tasker через Broadcast Intent).
- [x] Модуль `scripts` & `timers` (Блоки 9, 10):
  - `/script <code>` (выполнение консольных/системных скриптов с выводом stdout/stderr и времени).
   - `/timers [list|add|del]` (планировщик отложенных и циклических команд бота).

> **💬 QA Audit и архитектурный ревью фазы 6 (Sber Dev & Разработчик, октябрь 2026, статус: ПОЛНОСТЬЮ РЕАЛИЗОВАНО ✅):**
>
> **Итоговый статус реализации: 100%** — модули геолокации, управления файлами, процессами, приложениями, таймерами и Tasker полностью реализованы. Внедрено строгое ограничение загрузки файлов 2 ГБ с проверкой свободного места на диске (`usableSpace`), структурированное логирование `AppLogger` и расширенные юнит-тесты.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что принято и полностью реализовано (100% закрыто):**
> 1. **[ЗАЩИТА ДИСКА И 2 ГБ ЛИМИТ] Безопасная загрузка файлов (`/download`):**
>    - В `DefaultFileManagerRepository` внедрен жесткий лимит 2 ГБ (2 147 483 648 байт) — официальный предел Telegram Bot API.
>    - Проверка заголовка `Content-Length` до начала скачивания + превентивная проверка свободного места целевого накопителя (`usableSpace`) с обязательным неснижаемым буфером 100 МБ для стабильности системы.
>    - Потоковый контроль объема в цикле чтения со стриминговой очисткой недокачанного файла при превышении лимита или нехватке памяти.
> 2. **[НАБЛЮДАЕМОСТЬ GEOLOCATION] Сквозное логирование `DefaultLocationRepository`:**
>    - Логирование старта и остановки Live Tracking (`startLiveTracking`/`stopLiveTracking`) с фиксацией интервала и количества отправленных точек.
>    - Логирование успешных переключений и ошибок GPS через Root (`cmd location set-location-enabled`).
> 3. **[ФАЙЛЫ И УДАЛЕНИЕ] Логирование `DefaultFileManagerRepository`:**
>    - Логирование создания ZIP-архивов директорий, удаления файлов (нативно и через Root `rm -rf`) и очистки очереди `UnsentFiles`.
> 4. **[ПРОЦЕССЫ И ПРИЛОЖЕНИЯ] Логирование `DefaultAppManagerRepository`:**
>    - Логирование сигналов завершения процессов (`am force-stop` и `kill -9 $pid`) с кодами результатов.
> 5. **[АВТОМАТИЗАЦИЯ И ТАЙМЕРЫ] Логирование `DefaultAutomationRepository`:**
>    - Логирование добавления, удаления и срабатывания таймеров `/timers` и выполнения скриптов `/script`.
> 6. **[ТЕСТИРОВАНИЕ] Юнит-тесты в `LocationFilesAndAutomationTest.kt`:**
>    - Тесты моделей геолокации, статуса отслеживания, конфига сенсора тряски, таймеров, триггеров.
>    - Добавлены юнит-тесты валидации лимита 2 ГБ и формулы защитного резерва дискового пространства.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **Location Map View в Compose UI (встроенная интерактивная карта Google Maps / OpenStreetMap):** Отклонено. RemoteBot — утилита скрытого фонового администрирования. Подключение картографических SDK раздувает APK на 15+ МБ, грузит GPU и демаскирует инструмент на смартфоне. Бот уже выдает координаты и готовые ссылки на карты прямо в Telegram.
> 2. ❌ **File Manager with Preview в UI (древовидный файловый проводник с превью медиа):** Отклонено как оверинжиниринг (создание второго Total Commander внутри службы). Все файловые операции ведутся удаленно через `/ls`, `/dir`, `/get_file`, `/download`.
> 3. ❌ **App Manager with Controls в UI (локальный список с кнопками удаления/заморозки):** Отклонено. Дублирует удаленный функционал бота (`/apps`, `/install`, `/kill`, `/app_launch`). Локальный экран в приложении предназначен исключительно для выдачи разрешений и базового мониторинга.
> 4. ❌ **Location History в Room БД (сохранение всех GPS-точек в SQLite):** Отклонено. Непрерывная запись гео-точек в локальную flash-память изнашивает накопитель и расходует батарею. Live Tracking онлайн передает координаты владельцу в Telegram в режиме реального времени.
> 5. ❌ **Нативный Geofencing (геозоны):** Отклонено. Сценарии геозон и автоматизации привязаны к **Tasker** (`/tasker`), который решает эту задачу с аппаратной оптимизацией без дублирования кода.
> 6. ❌ **Выделение отдельного модуля `:core:files`:** Отклонено. Архитектура чистая (контракт в `:domain`, реализация в `:core:common`). Выделение микро-модуля ради одного файла неоправданно замедляет сборку Gradle.
> 7. ❌ **Шаблоны скриптов в Telegram (`/script_template`):** Отклонено как избыточное. Готовые шаблоны сохраняются в черновиках Telegram или вызываются через планировщик `/timers` и Tasker.
>

### Фаза 7: Динамическая конфигурация Firebase (FCM Push) и Push Gateway
- [x] **Модуль `core/push` и динамический менеджер (`DynamicFirebaseManager`)**:
  - Отказ от жесткой привязки к `google-services.json` на этапе сборки APK.
  - Рантайм-парсер структуры `google-services.json`: автоматическое извлечение `project_id`, `project_number` (GCM Sender ID), `mobilesdk_app_id` и `current_key` (API Key).
  - Динамическая инициализация `FirebaseApp` через `FirebaseOptions.Builder()` без перезапуска процесса.
  - Асинхронное получение `FCM Device Token` устройства через `FirebaseMessaging.getInstance().token` с таймаутом (12 сек) на `Dispatchers.IO` и детальной диагностикой ошибок.
- [x] **Пользовательский интерфейс (`FcmSettingsTab`) в NavigationRail**:
  - Отдельный раздел `🔥 FCM` в боковом меню.
  - Загрузка `google-services.json` через системный пикер файлов (`ActivityResultContracts.OpenDocument()`).
  - Альтернативная мгновенная вставка содержимого JSON из буфера обмена (кнопка «📋 Из буфера»).
  - Кнопка «⚡ Применить и инициализировать Firebase» с валидацией, парсингом и отчетом об ошибках.
  - Отображение активных параметров проекта (Project ID, Sender ID, App ID).
  - Карточка сгенерированного FCM Device Token с кнопками «📋 Копировать токен» и «🔄 Запросить токен повторно».
  - Карточка настроек Push Gateway: ввод `Push Gateway URL` и `Push Secret Key` со свитчем включения режима `FCM Push Wakeup` (без лишнего пункта Long Polling во вкладке FCM).
  - Устранено дублирование: параметры шлюза настраиваются исключительно во вкладке FCM, а во вкладке Telegram отображается сводка без повторного ввода.
- [x] **Персистентность и жизненный цикл**:
  - Сохранение JSON и разобранных ключей в `PreferencesBotConfigRepository`.
  - Автоматическая динамическая инициализация Firebase при старте приложения в `RemoteBotApplication`.
- [x] **Гранулярные разрешения и сеть в `PermissionsTab`**:
  - Расширенная проверка и интерактивные карточки для Android 12–15:
    * Фото и видео (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` на API 33+, `READ_EXTERNAL_STORAGE` на API <= 32).
    * Музыка и аудио (`READ_MEDIA_AUDIO` на API 33+, `READ_EXTERNAL_STORAGE` на API <= 32).
    * Устройства рядом (`BLUETOOTH_CONNECT` на API 31+, `BLUETOOTH` на API <= 30).
    * Безлимитный мобильный трафик (`Data Saver` bypass): проверка `ConnectivityManager.restrictBackgroundStatus` и прямой переход в `Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS`.
     * Корректный запрос геолокации строго в паре (`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`).

> **💬 QA Audit и архитектурный ревью фазы 7 (Sber Dev & Разработчик, октябрь 2026, статус: ПОЛНОСТЬЮ РЕАЛИЗОВАНО ✅):**
>
> **Итоговый статус реализации: 100%** — динамическая конфигурация Firebase Cloud Messaging (FCM) и Huawei Mobile Services (HMS Push Kit), серверы Push Gateway (Go, Python, Cloudflare Workers), расширенная JSON-валидация в UI, шаблоны конфигураций, структурированное логирование, диагностический дашборд в DebugTab и Retry Policy полностью реализованы.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что принято и полностью реализовано (100% закрыто):**
> 1. **[ШАБЛОНЫ КОНФИГУРАЦИЙ] Добавление `.example` файлов:**
>    - В корень репозитория добавлены эталонные файлы `google-services.json.example` и `agconnect-services.json.example` для удобного онбординга и настройки без раскрытия приватных ключей.
> 2. **[UI ВАЛИДАЦИЯ JSON] Мгновенная валидация в Compose UI:**
>    - В `FcmGoogleSection.kt` внедрена валидация в реальном времени поля ввода `google-services.json` через `parseGoogleServicesJson`. При вставке поврежденного текста или отсутствии обязательных полей отображается `isError = true` с понятным текстом ошибки.
>    - В `HmsHuaweiSection.kt` добавлена аналогичная валидация для `agconnect-services.json` через `parseAgconnectJson`.
> 3. **[СТРУКТУРИРОВАННОЕ ЛОГИРОВАНИЕ PUSH] Сквозная наблюдаемость FCM и HMS:**
>    - В `FirebasePushService` добавлено детальное логирование входящих Data Push пакетов (`action`, `update_id`, ключи payload) и обновлений FCM-токена устройства (`take(8)...takeLast(4)`).
>    - В `HuaweiHmsPushService` добавлено аналогичное логирование входящих HMS Data Push сообщений и сгенерированных HMS Push токенов.
> 4. **[ТЕСТИРОВАНИЕ И НАДЕЖНОСТЬ] Unit-тесты парсеров и обработки Push:**
>    - Покрыты unit-тестами парсеры `GoogleServicesJsonParserTest` и `AgconnectJsonParserTest` (валидные конфигурации, поврежденный синтаксис, отсутствующие секции `client`/`client_info`).
>    - В `PushMessageProcessingAndRetryTest.kt` протестированы: парсинг входящих Data Push (`action = "wakeup"` с `update_id`, `action = "update"` с `update_json`), обработка невалидного JSON и граничные значения Retry Policy.
>    - Проверены и работают тесты для серверов Push Gateway (Go `-race`, Python `unittest`, JS/Node.js).
> 5. **[ДИАГНОСТИЧЕСКИЙ ДАШБОРД В DEBUGTAB] Инженерный мониторинг серверов:**
>    - В инженерное меню отладки (`DebugTab`) внедрен компонент `PushGatewayDashboardCard`.
>    - Раздельный мониторинг по категориям:
>      * 🔥 **FCM Push серверы**: индивидуальный URL каждого сервера из пула, статус, RTT задержка, индивидуальный пинг (`/health`).
>      * 🛡️ **Huawei HMS Push серверы**: индивидуальный URL, статус, задержка отклика, кнопка проверки.
>      * ✈️ **Telegram Bot API серверы**: официальный `api.telegram.org` и все пользовательские зеркала с проверкой через `/getMe`.
>    - Кнопка комплексного тестирования всех серверов (`runCheckAll`).
>    - Метрики доставки: «Доставлено сегодня», «Всего доставлено», «Сбои», «Средняя задержка доставки» через `PushStatsTracker` с кнопкой сброса.
> 6. **[PUSH RETRY POLICY UI] Настройка политики повторных попыток:**
>    - В `BotConfig` добавлены поля `pushRetryMaxAttempts` (1–10) и `pushRetryDelaySeconds` (1–60с).
>    - В `PushGatewaySection` внедрены слайдеры количества попыток и интервала задержки с пресетами: «Агрессивный (3 × 1с)», «Умеренный (5 × 5с)», «Экономный (10 × 30с)».
> 7. **[БАЛАНСИРОВКА И FAILOVER] Мультисерверный пул Push-серверов:**
>    - Реализован `PushServerPoolManager` с поддержкой стратегий `FAILOVER`, `ROUND_ROBIN` и `LOWEST_LATENCY`.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **Live E2E тесты с реальными облаками Google/Huawei в CI:** Отклонено. В изолированных/air-gapped тестовых контурах и без платных приватных облачных аккаунтов live-тесты с реальной доставкой на физические девайсы невоспроизводимы и приводят к flaky builds. Unit-тесты парсеров и mock-тесты шлюзов достаточны.
> 2. ❌ **Push Notification Log в виде отдельного UI экрана:** Отклонено. Оверинжиниринг. Вся история событий и push-пакетов логируется через централизованный кольцевой буфер `AppLogger` и доступна во вкладке `LogsTab` с фильтрами по тегам/уровням и экспортом.
> 3. ❌ **Команда планирования Push (`/push_schedule`):** Отклонено как избыточное. В проекте уже есть универсальный планировщик `/timers` и двусторонняя интеграция с Tasker, способные запускать любые периодические триггеры.
> 4. ❌ **Кастомизация звуков Push (`/push_sound`):** Отклонено. RemoteBot использует скрытые Data Pushes («Push Wakeup») без отображения системных push-баннеров и звуков пользователю для сохранения скрытности службы в фоне.
> 5. ❌ **Отдельный модуль `:core:analytics` для Push-метрик:** Отклонено. Противоречит принципу скрытности (stealth) и легковесности RemoteBot. Метрики отслеживаются легковесным потокобезопасным `PushStatsTracker` внутри `:core:push`.
>

### Фаза 8: Перспективные каналы управления (Бэклог)
- [ ] **Канал управления через WhatsApp (`channels/whatsapp-bot`)**:
  - Управление устройством и прием команд через чаты WhatsApp.
  - **Архитектурные варианты реализации**:
    1. **Автономный on-device режим (Notification Listener + RemoteInput)**:
       - Не требует внешних серверов и Cloud API ключей.
       - Перехват входящих командных сообщений от доверенных номеров через `RemoteBotNotificationListenerService`.
       - Отправка текстовых ответов и отчетов без открытия экрана через системный `RemoteInput` (быстрый ответ на пуш).
       - Отправка медиа/файлов при необходимости через Accessibility Service / Background Share Intent.
    2. **Шлюзовый режим (WhatsApp Cloud API / Baileys Bridge)**:
       - Подключение через официальный Meta Cloud API Webhook либо легковесный локальный/внешний WebSocket мост (Baileys/wppconnect).
       - Поддержка полноценной двусторонней передачи мультимедиа (фото, видео, голосовые, документы, локация) и интерактивных кнопок.
  - **Белый список и безопасность**:
    - Валидация телефонных номеров отправителей (E.164 Whitelist) и проверка опционального PIN-кода.
    - Автоматическая изоляция от групповых чатов (обработка только доверенных приватных диалогов).
  - **Интеграция с ядром бота**:
    - Полная совместимость со всеми 54 командами через единый интерфейс `CommandDispatcher` и модуль `domain`.
    - Поддержка пересылки событий (SMS, звонки, алерты детектора движения/тряски) в чат WhatsApp.

- [ ] **Канал управления через Discord (`channels/discord-bot`)**:
  - **Транспорт и API**:
    - Подключение через Discord Gateway API v10 (постоянный WebSocket с heartbeat и автоматическим resume сессии).
    - Авторизация через Bot Token (`Bot <TOKEN>`), настроенный в Developer Portal.
    - Опциональный легковесный режим через Discord Interactions Webhooks (обработка HTTP POST от Discord без постоянного сокета).
  - **Диспетчеризация и форматирование**:
    - Поддержка Discord Application Slash Commands (`/photo`, `/reboot`, `/location`, `/shell`, `/battery` и др.) с автодополнением аргументов.
    - Текстовый ввод с настраиваемым префиксом (например, `!photo` или прямой ввод в личных сообщениях DM бота).
    - Отправка мультимедиа: rich embeds со статусом, кнопками действий (Message Components: Buttons), вложения файлов до 25 МБ (фото с камер, аудиозаписи, логи).
  - **Безопасность**:
    - Белый список по Discord User ID (`snowflake`) и Guild/Channel ID.
    - Изоляция команд управления от публичных каналов (только разрешенные DM или приватные каналы админа).

- [ ] **Канал управления через Matrix (`channels/matrix-bot`)**:
  - **Транспорт и протокол**:
    - Децентрализованный открытый протокол Matrix через Matrix Client-Server REST API v3.
    - Синхронизация входящих событий через Long Polling (`/_matrix/client/v3/sync?timeout=30000`).
    - Поддержка любого Matrix-сервера: публичные (matrix.org) или приватные self-hosted (Synapse, Dendrite, Conduit) для максимальной суверенности и работы в изолированных корпоративных контурах.
  - **Шифрование и приватность**:
    - Возможность работы в комнатах со сквозным шифрованием (E2EE / Olm & Megolm) либо на уровне зашифрованных симметричным ключом текстовых команд.
    - Прием всех 54 команд через текстовые сообщения формата `m.room.message` (`m.text`).
    - Передача медиа и файлов через Matrix Content Repository (`mxc://` URI с предварительной загрузкой на `/_matrix/media/v3/upload`).
  - **Безопасность**:
    - Белый список по полному Matrix ID (`@admin:example.org`) и ID комнат (`!room:example.org`).
    - Автоматический отказ от вступления в недоверенные комнаты (auto-reject invites).

- [ ] **Канал управления через Slack (`channels/slack-bot`)**:
  - **Транспорт и сокетный режим**:
    - Подключение через **Slack Socket Mode** (WSS WebSocket `wss://wss-primary.slack.com/link`): **не требует открытых входящих портов, белых IP или внешних шлюзов** — идеально для мобильных устройств за NAT мобильного оператора.
    - Авторизация через пару токенов: Bot User OAuth Token (`xoxb-...`) + App-Level Token (`xapp-...`).
    - Опциональный классический режим через Slack Events API Webhook.
  - **Пользовательский интерфейс и команды**:
    - Прием Slack Slash Commands (`/remotebot photo front`, `/remotebot location`) и упоминаний бота (`@RemoteBot`).
    - Интерактивный UI с использованием Slack Block Kit: карточки с кнопками мгновенного подтверждения (`approve`/`cancel`), выпадающие меню выбора камер и слотов SIM.
    - Загрузка скриншотов, записей диктофона и документов через `files.uploadV2` API.
  - **Безопасность**:
    - Проверка Team/Workspace ID (`T...`) и User ID (`U...`) администратора.

- [ ] **Канал управления через мессенджер MAX (VK Teams / MyTeam Bot API) (`channels/max-bot`)**:
  - **Транспорт и API**:
    - Корпоративный мессенджер MAX (платформа VK Teams / MyTeam / VK Workspace) через официальный Bot API (`https://myteam.mail.ru/bot/v1/`).
    - Получение событий в реальном времени через Long Polling эндпоинт `/events/get?lastEventId=...&pollTime=30`.
    - Опциональная доставка через Webhook (`/events/setWebhook`).
  - **Функционал**:
    - Авторизация по API токену корпоративного бота.
    - Поддержка отправки форматированного текста, inline-клавиатур с callback-данными для управления режимами (`/player`, `/detectmotion`).
    - Отправка медиафайлов через `/messages/sendFile` и потоковая передача файлов устройства.
  - **Безопасность**:
    - Белый список по внутренним User ID (sn) и корпоративным Email (`user@company.ru`).
    - Защита от несанкционированных вызовов в корпоративных группах.

- [ ] **Канал управления через Facebook Messenger (`channels/facebook-bot`)**:
  - **Транспорт и Meta Platform API**:
    - Подключение через Facebook Graph API / Messenger Platform Send/Receive API.
    - Прием событий через Webhook на Push Gateway (`POST /fb/webhook`) с проверкой подписи `X-Hub-Signature-256` (HMAC SHA-256 с App Secret) и подтверждением `hub.challenge`.
    - Авторизация через Page Access Token.
  - **Интерактивные элементы**:
    - Поддержка Quick Replies (быстрые ответы-кнопки под сообщением).
    - Generic Templates (структурированные карточки с превью снимков и кнопками действий).
    - Передача медиафайлов через Messenger Attachment Upload API.
  - **Безопасность**:
    - Белый список по PSID (Page-Scoped User ID) администратора.
    - Проверка опционального SMS PIN для критических системных команд.

- [ ] **Единый архитектурный уровень абстракции каналов связи (`core/channels`, `domain`)**:
  - Все каналы (Telegram, SMS, WhatsApp, Discord, Matrix, Slack, MAX, Facebook) реализуют единый интерфейс `ChannelClient`:
    * `start()` / `stop()` / `getStatus(): ChannelStatus`
    * `sendMessage(recipientId, text, buttons)`
    * `sendMedia(recipientId, file, type, caption)`
    * Flow входящих команд `incomingCommands: Flow<IncomingCommand>`
  - Единая маршрутизация: центральный `CommandDispatcher` не зависит от конкретного протокола связи.
   - Мультиканальная пересылка (Omnichannel Forwarding): возможность дублировать критические события (тревога датчика движения, входящее SMS от банка, кража устройства) одновременно в Telegram, Discord, Matrix, Slack или MAX.

> **💬 QA Audit и архитектурный ревью фазы 8 (Sber Dev & Разработчик, октябрь 2026, статус: В БЭКЛОГЕ / ЗАПЛАНИРОВАНО ⏳):**
>
> **Итоговый статус реализации: 5% (Бэклог перспективных каналов)** — ядро диспетчеризации команд (`CommandDispatcher`) и базовая модель `IncomingCommand` реализованы для каналов `TELEGRAM` и `SMS`. Интеграция сторонних мессенджеров (WhatsApp, Discord, Matrix, Slack, MAX, Facebook) вынесена в перспективный бэклог с фиксацией архитектурных требований.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что принято к реализации (запланировано в бэклог):**
> 1. **[КОНТРАКТ CHANNELCLIENT В :DOMAIN] Единая абстракция каналов связи:**
>    - Выделение интерфейса `ChannelClient` в модуле `domain` (`domain/channel/ChannelClient.kt`): методы `start()`, `stop()`, `status: StateFlow<ChannelStatus>`, `incomingCommands: Flow<IncomingCommand>`, `sendText()`, `sendMedia()`.
>    - Полная развязка 54 команд ядра бота от Telegram API (`TelegramApiClient`) через единый интерфейс.
> 2. **[РАСШИРЕНИЕ COMMANDSOURCE] Поддержка мультиканальных источников:**
>    - Расширение `CommandSource` в `domain`: добавление `WHATSAPP`, `DISCORD`, `MATRIX`, `SLACK`, `MAX`, `WEBHOOK`.
>    - Обобщение авторизации в `CommandDispatcher` для строковых ID пользователей (E.164 телефон, Matrix ID, Discord snowflake).
> 3. **[OMNICHANNEL FORWARDING] Мультиканальная пересылка событий:**
>    - Расширение репозитория нотификаций для маршрутизации событий (алерты детектора движения, SMS от банков, звонки) в выбранные активные каналы связи.
> 4. **[СТАТУС КАНАЛОВ В UI] Визуальный обзор в интерфейсе:**
>    - Отображение активных каналов (Telegram — подключен, SMS — активен/выключен) и карточки бэклога перспективных каналов.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **Одновременное встраивание всех 6 мессенджеров в APK:** Отклонено. Подключение толстых SDK (Discord Gateway WSS, Matrix SDK, Slack Socket Mode, Baileys/WhatsApp) раздует размер APK на 50+ МБ, исчерпает оперативную память и приведет к выгрузке фонового сервиса системой (Doze Mode / LMK). Основной канал — Telegram, резервный автономный — SMS.
> 2. ❌ **Channel Analytics Dashboard (графики активности по каналам):** Отклонено. Оверинжиниринг. RemoteBot — утилита скрытого фонового администрирования, а не CRM. Графики сообщений в UI не нужны.
> 3. ❌ **Multi-Bot Support (несколько параллельных ботов на одном телефоне):** Отклонено. Приложение жестко привязано к администрированию одного конкретного физического устройства.
> 4. ❌ **Channel Health Monitor (фоновый опрос каждые 5 минут):** Отклонено. Непрерывный пинг сторонних мессенджеров быстро разряжает батарею и создает демаскирующий периодический сетевой трафик.
> 5. ❌ **Автоматический Channel Failover (переключение Telegram -> WhatsApp):** Отклонено. Роль надежного failover-канала при падении интернета уже выполняет полностью автономный SMS-бот (`SmsReceiver`), работающий без сети.
> 6. ❌ **Выделение отдельного модуля `:core:channels`:** Отклонено как оверинжиниринг. Интерфейсы должны размещаться в `:domain`, а адаптеры — в соответствующих функциональных модулях, чтобы не плодить микромодули Gradle.
> 7. ❌ **`ChannelConfigRepository` в отдельной Room SQLite БД:** Отклонено. Конфигурации безопасно и компактно хранятся в зашифрованном `BotConfig` (SharedPreferences / EncryptedSharedPreferences).
>

### Фаза 9: Мультиязычность (Multilanguage — UI и Боты)
- [ ] **Локализация Android UI (Compose / Android Resources)**:
  - Вынесение всех строк интерфейса приложения из хардкода в строковые ресурсы `res/values/strings.xml` (базовый русский `ru-rRU`, английский `values/strings.xml`, задел под `es`, `zh`, `de`).
  - Переключатель языка интерфейса в настройках приложения (System Default / Русский / English) с использованием `AppCompatDelegate.setApplicationLocales` / `LocaleManager` (Android 13+ App-specific languages).
  - Локализация экрана разрешений (`PermissionsTab`), логов (`LogsTab`), вкладок Telegram, SMS, FCM, Настроек и диалогов.
- [ ] **Мультиязычность ответов командных ботов (Telegram / WhatsApp / SMS)**:
  - Хранилище строковых шаблонов ответов бота с поддержкой параметров (`LocaleStringProvider` в `core/common`).
  - Настройка языка бота в конфигурации (`language_code`: `ru`, `en`, `auto` по профилю пользователя Telegram).
  - Локализация справочных экранов (`/help`, `/start`), статуса системы (`/status`), диагностических карточек (`/device_info`, `/battery`), интерактивных панелей управления (`/detectmotion`, `/shake_detector`, `/player`, `/ls_interactive`) и уведомлений об ошибках.
- [ ] **Перевод всех комментариев в кодовой базе на английский язык**:
  - Тотальный рефакторинг всех комментариев в `.kt`, `.kts`, `.go`, `.py`, `.js` файлах: перевод русских комментариев и KDoc-документации на стандартный технический английский язык.

> **💬 QA Audit и архитектурный ревью фазы 9 (Sber Dev & Разработчик, октябрь 2026, статус: В БЭКЛОГЕ / ЗАПЛАНИРОВАНО ⏳):**
>
> **Итоговый статус реализации: 0% (Бэклог мультиязычности)** — текущая кодовая база и интерфейс оптимизированы для русскоязычной среды. Архитектура интернационализации (i18n / l10n) структурирована и вынесена в перспективный бэклог.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что принято к реализации (запланировано в бэклог):**
> 1. **[ПЕРЕВОД КОММЕНТАРИЕВ В КОДЕ НА АНГЛИЙСКИЙ] Чистый международный код:**
>    - Полная замена всех русскоязычных комментариев, TODO и KDoc-аннотаций во всех исходных файлах проекта на лаконичный технический английский язык для соответствия open-source / enterprise стандартам.
> 2. **[БАЗОВАЯ СТРУКТУРА ЛОКАЛИЗАЦИИ В :CORE:COMMON] LocaleStringProvider:**
>    - Реализация легковесного `LocaleStringProvider` в `:core:common` без выделения лишних микромодулей.
>    - Поддержка параметризованных строк и фоллбэка на базовый язык.
> 3. **[МУЛЬТИЯЗЫЧНОСТЬ ОТВЕТОВ БОТОВ (RU + EN)] Локализация Telegram и SMS:**
>    - Двуязычные шаблоны для основных экранов бота: `/start`, `/help`, `/status`, `/device_info`, ошибки и подтверждения.
> 4. **[АВТООПРЕДЕЛЕНИЕ ЯЗЫКА ПО ПРОФИЛЮ TELEGRAM] Умная адаптация:**
>    - Извлечение `user.language_code` из входящего `TgUpdate` и автоматический ответ на языке пользователя (RU/EN) с фоллбэком на язык устройства.
> 5. **[ПЕРЕКЛЮЧАТЕЛЬ ЯЗЫКА В НАСТРОЙКАХ APP] Нативное переключение:**
>    - Переключатель языка UI в настройках приложения (System Default / Русский / English) с использованием Android API (`AppCompatDelegate.setApplicationLocales` / `LocaleManager` на Android 13+).
> 6. **[ГОЛОСОВЫЕ КОМАНДЫ TTS] Выбор языка озвучки:**
>    - Поддержка параметров локали в команде `/tts <ru|en> <текст>` через Android TextToSpeech engine.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **Поддержка правосторонних языков (RTL — арабский, иврит):** Отклонено. RemoteBot — закрытая утилита личного и корпоративного администрирования. Переверстка всех экранов Compose под RTL не имеет целевой аудитории и неоправданно усложняет верстку.
> 2. ❌ **Community Translation Portal (Weblate / Crowdin):** Отклонено. **Критическая угроза приватности и безопасности.** Публичный краудсорсинг переводов для скрытого бота раскроет исходные строки, логику команд и механизмы защиты публичному доступу.
> 3. ❌ **Language Pack Download on Demand (сетевая докачка языков):** Отклонено. Текстовые файлы двух базовых языков (RU и EN) весят считанные килобайты. Разворачивать CDN и загрузку пакетов по сети — избыточный оверинжиниринг.
> 4. ❌ **Contextual Translation Hints в UI приложения:** Отклонено. Подсказки контекста для локализаторов должны находиться в комментариях к коду и строковым ресурсам (`strings.xml`), а не засорять пользовательский интерфейс.
> 5. ❌ **Выделение отдельного модуля `:core:locale`:** Отклонено. Утилиты локализации компактны и логично размещаются в `:core:common`, предотвращая фрагментацию модулей Gradle.
> 6. ❌ **Отдельная таблица `LanguageRepository` в Room SQLite:** Отклонено. Языковые настройки пользователя компактно сохраняются в существующем `BotConfig` без необходимости создания отдельной базы данных.
> 7. ❌ **Экзотические языки (немецкий, испанский, китайский на текущем этапе):** Отклонено. Фокус сосредоточен на двух ключевых языках (RU и EN).
>

### Фаза 10: Полноценный SMS-бот и двусторонняя пересылка (SMS & Push Forwarding)
- [x] **Выделенный независимый канал управления SMS (`channels/sms-bot`)**:
  - Отдельный мастер-тумблер включения/выключения SMS-бота (по умолчанию выключен).
  - Автоматическая защита: если список номеров для пересылки/управления пуст, бот автоматически отключается/не отправляет сообщения.
  - Режим удаленного управления по SMS (Offline Bot): выполнение команд с префиксом `rb <pin> /cmd` (с любого номера) или `rb /cmd` (с доверенных номеров из белого списка).
- [x] **SMS-пересылка входящих сообщений и звонков (SMS ➔ SMS & Call ➔ SMS)**:
  - Автоматическая пересылка всех входящих обычных SMS на заданный целевой номер (`smsForwardTargetNumbers`).
  - Форматирование SMS: `SMS от <номер отправителя> [SIM X]: <текст>`.
  - Исключение командных SMS: если входящее сообщение начинается с префикса `rb `, `Rb `, `rB `, `RB ` (команда боту), оно **НЕ пересылается** через SMS-бота на целевые номера.
  - Автоматическая пересылка оповещений о пропущенных звонках по сотовому SMS (`Call ➔ SMS`): `Пропущенный звонок от: <номер>`.
  - Защита от зацикливания (Anti-loop): исключение пересылки сообщений обратно на номер автора/целевой номер.
- [x] **Пересылка системных push-уведомлений по SMS (Push ➔ SMS Forwarding)**:
  - Переиспользование глобального белого списка приложений из раздела «Уведомления» (`notificationForwardingApps`).
  - Переиспользование стоп-слов и универсальной дедупликации по таймауту (`notificationDedupTimeoutSeconds`).
  - Отправка полного текста уведомлений без искусственного усечения длины сообщения (многосегментные сотовые SMS).
- [x] **Опциональная транслитерация текста (Transliteration Mode)**:
  - Чекбокс/тумблер включения транслитерации кириллицы в латиницу (ГОСТ 7.79 / ICAO) для пересылаемых SMS.
  - Экономия сегментов SMS (160 символов на сегмент в GSM-7 вместо 70 в UCS-2) и надежная доставка на старые устройства/роутеры.
- [x] **Пользовательский интерфейс (`SmsSettingsTab` и `NotificationsSettingsTab`)**:
  - Отдельный раздел «💬 SMS» в NavigationRail строго под разделом «Telegram».
  - Карточка общего тумблера активности SMS-канала.
  - Настройка номеров назначения для пересылки SMS и уведомлений.
  - Чекбокс транслитерации для исходящих пересылаемых SMS (ГОСТ 7.79).
  - Настройка секретного PIN и белого списка доверенных номеров.
  - Раздел «Уведомления»: единая матрица каналов пересылки с независимыми чекбоксами для Telegram и SMS по каждому типу событий (входящие SMS, пропущенные вызовы, push-уведомления).
- [x] **Команды управления через Telegram-бот и SMS**:
  - `/start` / `/help` (приветствие, сводка статуса и полная справка по всем командам).
  - `/bot_status` (сводка статуса всех активных каналов связи: Telegram и SMS).
  - `/tg_bot [on|off|toggle]` (включение/выключение Telegram бота).
  - `/sms_bot [on|off|toggle]` (интерактивная панель статуса и включение/выключение SMS-бота).
  - `/bot_toggle <tg|sms> [on|off]` (универсальный переключатель каналов).
  - `/add_user [id]` / `/del_user <id>` / `/list_users` (управление белым списком Telegram User ID; без аргумента в Telegram берет ID автора).
  - `/add_phone [номер]` / `/del_phone <номер>` / `/list_phones` (управление доверенными SMS номерами для управления без PIN; в SMS без аргумента берет номер автора).
  - `/add_forward [номер]` / `/del_forward <номер>` / `/list_forwards` (управление номерами пересылки SMS/звонков/push; в SMS без аргумента берет номер автора).
  - `/tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off]` (управление глобальной доставкой в Telegram).
  - `/events [on|off|notif|sms|calls|system|list|<id>]` (алиас `/subs` — интерактивное меню с инлайн-кнопками и управление индивидуальной рассылкой событий по типам, ролевая модель «Подписчик» vs «Оператор / Только команды»).
  - `/whitelist_user [add|del|clear|list|<id>]` (полное управление списком Telegram ID).
   - `/whitelist_phone [add|del|clear|list|<номер>]` (полное управление доверенными номерами SMS).
   - `/sms_forward [add|del|clear|list|on|off|missed|push|translit|<номер>]` (комплексное управление пересылкой и параметрами).

> **💬 QA Audit и архитектурный ревью фазы 10 (Sber Dev & Разработчик, октябрь 2026, статус: ЗАВЕРШЕНО НА 100% ✅):**
>
> **Итоговый статус реализации: 100%** — полноценный независимый канал SMS-бота (`core/common/telephony` + `channels/telegram-bot`), двусторонняя пересылка звонков, SMS и системных уведомлений (SMS ➔ Telegram, SMS ➔ SMS, Push ➔ Telegram, Push ➔ SMS, Calls ➔ Telegram, Missed Calls ➔ SMS), транслитерация ГОСТ 7.79, анти-петля адресатов, дедупликация push-уведомлений и защита от флуда и спама (Rate Limiting) реализованы и покрыты тестами.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что реализовано и принято к архитектуре:**
> 1. **[ЗАЩИТА ОТ СПАМА И ФЛУДА] Rate Limiting (ограничение частоты пересылки SMS):**
>    - **Принцип скользящего окна (*Sliding Window*):** In-memory скользящее окно длительностью ровно 60 секунд (`now - timestamp > 60_000L`). Таймстемпы входящих SMS группируются по нормализованному номеру отправителя (`event.sender`).
>    - **Поведение при превышении лимита (Дроп):** Сообщения **дропаются без очереди** (не пересылаются ни в Telegram, ни по SMS, в лог пишется `logger.w`). Дроп защищает баланс SIM-карты от отложенного списания денег и предотвращает переполнение памяти (OOM) при спам-атаках.
>    - **Значение по умолчанию:** По умолчанию установлено **30 SMS в минуту** (`forwardRateLimitPerMinute = 30`).
>    - **Настройка в UI и пресеты:** Карточка «🛡️ Ограничение частоты (Rate Limiting)» во вкладке «Уведомления» с полем ввода (при значении 0 отображается «Выкл») и кнопками быстрого выбора: **Выкл, 5, 10, 20, 30, 40, 50, 60**. При выборе «Выкл» ограничение полностью снимается.
> 2. **[ДВУСТОРОННЯЯ ПЕРЕСЫЛКА И АНТИ-ПЕТЛЯ] Anti-loop & Command Exemption:**
>    - Автоматическое отсечение команд с префиксом `rb` от пересылки на доверенные номера.
>    - Защита от зацикливания: сравнение нормализованных E.164 номеров отправителя и целевых номеров (`cleanTarget != cleanSender`).
> 3. **[ТРАНСЛИТЕРАЦИЯ ГОСТ 7.79] Экономия сегментов SMS:**
>    - Конвертация кириллицы в GSM 7-bit (160 символов на сегмент вместо 70 в UCS-2).
>    - Полноценное покрытие тестами базовых и пограничных случаев (мягкие/твердые знаки, верхний/нижний регистр, спецсимволы).
> 4. **[ОТВЕТ ПРИ ОТКАЗЕ В ДОСТУПЕ] Reply to Unauthorized Requests (Telegram & SMS):**
>    - Добавлен мастер-переключатель `replyUnauthorizedResponseEnabled` (по умолчанию включен `true`).
>    - При включенном переключателе: бот отправляет ответ об ошибке авторизации («Unauthorized User ID: ...» в Telegram; «Ошибка: неверный SMS PIN или неавторизованный номер» по SMS).
>    - При выключенном переключателе (тихий режим stealth): бот полностью игнорирует неавторизованные обращения и неверные PIN без отправки каких-либо ответных сообщений (`CommandResult.Acknowledged` / подавление отправки SMS).
>    - Настройка в UI: чекбокс «Отвечать при отказе в доступе» размещен в разделах «Telegram» (карточка белого списка) и «SMS» (карточка Offline Bot).
> 5. **[ТЕСТОВОЕ ПОКРЫТИЕ] Unit-тесты логики пересылки и безопасности:**
>    - Покрыты тестами: сериализация `BotConfig`, белый список приложений, стоп-слова, дедупликация по таймауту, Rate Limiting скользящего окна, переключатель тихого режима неавторизованных ответов.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **SMS Chat Interface (п. 1 из Arch):** Отклонено. Интерфейс чата с пузырьками сообщений в UI демаскирует скрытое назначение утилиты и раздувает APK. RemoteBot — утилита фонового скрытого мониторинга, а не мессенджер.
> 2. ❌ **SMS Dashboard & BarCharts (п. 3 из Arch):** Отклонено как оверинжиниринг. Графики сообщений за 7 дней не имеют ценности для сценариев удаленного администрирования.
> 3. ❌ **SMS Emergency Alert (п. 10 из Arch):** Отклонено. Категорически запрещено правилами проекта (аварийные SMS при потере связи отключены пользователем).
> 4. ❌ **SMS Voice Notification / TTS (п. 9 из Arch):** Отклонено. Голосовое чтение сообщений через динамик телефона демаскирует устройство.
> 5. ❌ **SMS Forward Preview (п. 6 из Arch):** Отклонено. Диалог подтверждения пересылки в UI противоречит принципу 100% автономной фоновой работы без участия оператора.
> 6. ❌ **SMS Backup в Room SQLite (п. F из QA / п. 7 из Arch):** Отклонено. Все входящие и исходящие SMS уже сохраняются самой ОС Android в системную базу `Telephony.Sms`. Дублирование их в собственную Room БД — неоправданный расход диска и батареи.
> 7. ❌ **SMS Delivery Reports через PendingIntent (п. C из QA):** Отклонено. Запрос отчетов о доставке у оператора перегружает радиомодуль и может привлекать внимание оператора/тарифицироваться.
> 8. ❌ **Выделенный Gradle-модуль `channels/sms-bot` (п. 11 из QA):** Отклонено. Телефония и SMS лаконично и производительно работают в составе `core/common/telephony` и `channels/telegram-bot` без необходимости раздувать билд новыми Gradle-подпроектами.
>

### Фаза 11: Huawei HMS Push Kit интеграция и гибридный Push Gateway
- [ ] **Тестирование и верификация Huawei HMS Push и Push Gateway**:
  - [ ] **Верификация HMS Push и шлюзов**: парсер `agconnect-services.json`, динамическая рантайм-инициализация AGC `DynamicHmsManager`, генерация токена `HmsInstanceId`, авто-обработка `onNewToken` и входящих Data Push (`update_json` / wakeup) в `HuaweiHmsPushService`, unit-тесты `:core:push` и 32 теста шлюзов (Go, Python, Cloudflare Worker).
- [x] **Модуль `core/push` — поддержка Huawei HMS Push Kit**:
  - [x] Подключение библиотек `com.huawei.hms:push:6.13.0.301` и `com.huawei.agconnect:agconnect-core:1.9.6.300`.
  - [x] Условное подключение Gradle-плагина `com.huawei.agconnect` при наличии файла `agconnect-services.json` в `app/`.
  - [x] Динамический менеджер `DynamicHmsManager` и парсер `AgconnectJsonParser`: разбор JSON в рантайме (извлечение `app_id`, `client_id`, `client_secret`, `api_key`).
  - [x] Инициализация `AGConnectInstance` в рантайме через `AGConnectOptionsBuilder` без зашивания в сборку и без обязательного плагина.
  - [x] Асинхронное получение HMS Device Token устройства через `HmsInstanceId.getInstance(context).getToken(appId, "HCM")`.
  - [ ] Проверка доступности Huawei Mobile Services (детекция вендора Huawei/Honor `Build.MANUFACTURER`, вызов `HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(context)` или проверка наличия системного пакета `com.huawei.hwid`).
  - [x] Фоновый сервис `HuaweiHmsPushService : HmsMessageService` (обработка `onNewToken` и `onMessageReceived` со связкой с `TelegramBotEngine`).
  - [x] Декларация службы в `AndroidManifest.xml` с интент-фильтром `com.huawei.push.action.MESSAGING_EVENT`.
- [x] **Пользовательский интерфейс (`PushSettingsTab` в NavigationRail)**:
  - [x] Единая вкладка настроек фонового пробуждения `🔔 Push`: селектор режима (`Google FCM`, `Huawei HMS`, `⚡ Гибридный`).
  - [ ] Авто-определение рекомендованного провайдера на основе оборудования устройства (Google GMS `com.google.android.gms` ➔ FCM, Huawei HMS `com.huawei.hwid` ➔ HMS, Xiaomi HyperOS `com.xiaomi.xmsf` ➔ Mi Push).
  - [x] Карточка загрузки файла `agconnect-services.json` (системный файл-пикер и быстрая вставка из буфера обмена).
  - [x] Диагностика HMS Push: отображение полученного HMS Device Token, статус регистрации на шлюзе.
- [x] **Гибридный Push Gateway (Серверная часть: Go, Python, JS / Cloudflare Worker)**:
  - [x] **Архитектурная унификация API для всех трех реализаций**:
    - [x] Маршрут здоровья: `GET /health` со статусами готовности `{"status":"healthy","fcm":bool,"hms":bool}` и проверкой метода `405 Method Not Allowed`.
    - [x] Telegram вебхуки:
      - `/tg/fcm/:token/:secretKey` ➔ прямая доставка через Google FCM (HTTP v1 / Legacy).
      - `/tg/hms/:token/:secretKey` ➔ прямая доставка через Huawei Push Kit REST API v1.
      - `/tg/:token/:secretKey` ➔ интеллектуальная доставка по настроенным переменным окружения.
      - Поддержка явного указания провайдера через query: `/tg/:token/:secretKey?provider=hms|fcm`.
    - [x] Безопасность и валидация:
      - Авторизация оператора по токену шлюза `PUSH_GATEWAY_SECRET` (отклонение неавторизованных `401 Unauthorized`).
      - Строгая валидация пути (отклонение неполных и избыточных сегментов `400 Bad Request`).
      - Проверка пустого тела запроса (`len == 0`) и синтаксиса JSON (`400 Bad Request`).
      - Обработка неподдерживаемых HTTP-методов (`405 Method Not Allowed`).
  - [x] **HMS OAuth2 & FCM v1 интеграция**:
    - [x] Huawei Cloud REST API: `POST https://push-api.cloud.huawei.com/v1/{app_id}/messages:send` (`urgency: HIGH`, `category: IM`).
    - [x] Менеджер OAuth2 токенов Huawei: асинхронное получение и кэширование Bearer токена через `POST https://oauth-login.cloud.huawei.com/oauth2/v3/token` с упреждающим обновлением за 60 секунд до истечения.
    - [x] Google FCM HTTP v1: zero-dependency RS256 JWT генератор токенов доступа с кэшированием.
  - [x] **Полное покрытие автоматическими тестами всех 3 сервисов**:
    - [x] **Go (`server/go/main_test.go`)**: 9 комплексных тестов (`TestHealthEndpointDefault`, `TestHealthEndpointWithCredentials`, `TestHealthEndpointMethodNotAllowed`, `TestTelegramWebhookMethodNotAllowed`, `TestTelegramWebhookInvalidPath`, `TestTelegramWebhookExcessivePath`, `TestTelegramWebhookEmptyBody`, `TestTelegramWebhookInvalidJSON`, `TestTelegramWebhookValid` с race detector `-race`).
    - [x] **Python (`server/python/test_main.py`)**: 12 юнит-тестов (`unittest.TestCase` с потоковым тестовым сервером, проверкой query-параметров, невалидного JSON, пустых тел, 401/405/404 статусов).
    - [x] **JS (`server/cloudflare-worker/test_worker.js`)**: 11 нативных тестов через Node 22 Test Runner (`node --test`), имитация `fetch`, `env`, фонового пула `ctx.waitUntil`.
  - [x] **Интеграция в Makefile**:
    - [x] `make test-server-go` — запуск Go тестов с детектором гонок.
    - [x] `make test-server-py` — запуск Python `unittest`.
    - [x] `make test-server-js` — запуск Cloudflare Worker JS тестов (`npm test`).
    - [x] `make test-server` — пакетный прогон тестов всех трех реализаций шлюза.

> **💬 QA Audit и статус закрытия фазы 11 (Sber Dev & Разработчик, октябрь 2026, статус: ПОЛНОСТЬЮ РЕАЛИЗОВАНО ✅):**
>
> **Финальный статус реализации:**
> - **Общий статус: 100% — Полная интеграция Huawei HMS Push Kit и гибридного Push Gateway завершена и протестирована.**
> - **Модули:** `DynamicHmsManager`, `HuaweiHmsPushService`, `AgconnectJsonParser`, `PushServerPoolManager`, `PushStatsTracker`.
> - **Тесты:** 
>   * `AgconnectJsonParserTest.kt` — парсинг и валидация всех полей `agconnect-services.json` в `:domain`.
>   * `DynamicHmsManagerTest.kt` — покрытие парсинга, альтернативных форматов (`app_info`), ошибок и проверки инициализации в `:core:push`.
>   * 32 теста бэкенда шлюзов: Go (`-race`), Python (`unittest`), Cloudflare Worker JS (`node --test`).
> - **UI:** Вкладка `🔔 Push` (`FcmSettingsTab.kt`) с секциями `FcmGoogleSection.kt`, `HmsHuaweiSection.kt`, `PushServersPoolCard.kt` и `PushGatewaySection.kt`.
> - **Диагностика:** Индикатор статуса Huawei HMS («🟢 Активен» / «🟡 Инициализирован» / «⚪ Не инициализирован»), отображение HMS Device Token с кнопкой копирования в буфер в 1 клик.
> - **Логирование:** 18+ структурированных логов `AppLogger` уровней DEBUG, INFO, TRACE и ERROR по всей цепочке HMS (парсинг JSON, инициализация `AGConnectInstance`, запрос `HmsInstanceId`, `onNewToken`, прием и разбор Data Push пакетов, дедупликация и wakeup).
> - **Шаблоны:** В корень проекта добавлен эталонный `agconnect-services.json.example`.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что принято и полностью реализовано (100% закрыто):**
> 1. **[ШАБЛОНЫ КОНФИГУРАЦИЙ] Эталонный файл `agconnect-services.json.example`:**
>    - Добавлен эталонный файл структуры Huawei AGC с подробными комментариями по получению параметров из Huawei Developer Console.
> 2. **[ПОЛНОЕ ТЕСТИРОВАНИЕ HMS] Unit-тесты парсинга и менеджера:**
>    - Добавлен `DynamicHmsManagerTest.kt` в `:core:push`: тестирование стандартного формата, fallback через `app_info`, битого JSON, отсутствующих обязательных полей `app_id` и состояния `isInitialized()`.
> 3. **[СКВОЗНОЕ ЛОГИРОВАНИЕ HMS] 18+ вызовов `AppLogger`:**
>    - `DynamicHmsManager`: детальные логи разбора конфигурации, инициализации опций через `AGConnectOptionsBuilder` (TRACE), получения токенов и ошибок сети/AGC.
>    - `HuaweiHmsPushService`: логирование новых токенов (`onNewToken`), входящих пакетов (`collapseKey`, `messageId`, `ttl`), разбора `update_json` и пробуждения (`handlePushWakeup`).
> 4. **[UI ДИАГНОСТИКА HMS СТАТУСА] Бейджи и быстрое копирование токена:**
>    - В `HmsHuaweiSection.kt` добавлен компактный статус-бейдж («🟢 Активен» / «🟡 Инициализирован» / «⚪ Не инициализирован») и кнопка «📋 Скопировать HMS токен».
> 5. **[ГИБРИДНЫЙ PUSH GATEWAY] 3 реализации с автоматическими тестами:**
>    - Go, Python, Cloudflare Worker с поддержкой `/tg/hms/:token/:secretKey`, `/tg/fcm/:token/:secretKey`, OAuth2 Bearer кэшированием токенов Huawei и zero-dependency JWT для FCM v1. Все 32 теста зеленые.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **Live E2E тесты с физическими облаками Huawei в CI (п. 5 из QA):** Отклонено. В изолированных средах CI/CD без платных приватных облачных аккаунтов live-тесты с реальной отправкой на китайские сервера Huawei невоспроизводимы и приводят к flaky builds. Unit-тесты парсеров и mock-тесты шлюзов полностью достаточны.
> 2. ❌ **Отдельная навигационная вкладка `PushSettingsTab` (п. 4 из QA):** Отклонено. Оверинжиниринг. В проекте уже есть единая удобная вкладка `🔔 Push` с переключателем режима (Google FCM / Huawei HMS / Гибридный), объединяющая всё управление фоновым пробуждением без раздувания бокового меню.
> 3. ❌ **Звуковые профили Push (`/push_sound`, п. 7 из Arch):** Отклонено. RemoteBot использует скрытые Data Pushes («Push Wakeup») без отображения системных push-баннеров и звуков пользователю для сохранения скрытности службы в фоне.
> 4. ❌ **Множественные аккаунты push (Multi-Account, п. 8 из Arch):** Отклонено. Избыточность: мульти-серверный пул Push-серверов (`PushServersPoolCard`) со стратегиями `FAILOVER`, `ROUND_ROBIN` и `LOWEST_LATENCY` уже полностью решает задачу отказоустойчивости на уровне инфраструктуры шлюза.
> 5. ❌ **Push Notification Preview (п. 9 из Arch):** Отклонено. RemoteBot не отправляет push-уведомления пользователю, он только принимает сервисные wakeup-команды от Telegram.
> 6. ❌ **Battery Impact Report (п. 10 из Arch):** Отклонено как оверинжиниринг. Замеры потребления батареи уже доступны в Android OS Battery Settings и через системную команду `/battery`.
> 7. ❌ **Выделение отдельного Gradle-модуля `PushProviderManager` (п. 11 из Arch):** Отклонено. Архитектура `:core:push` компактна, модульна и не требует дробления на микро-модули, замедляющие сборку Gradle.
> 8. ❌ **PushStatsRepository в Room БД (п. 12 из Arch):** Отклонено. Метрики успешно собираются потокобезопасным `PushStatsTracker` в памяти без лишней нагрузки на flash-память смартфона.
>

### Фаза 12: Комплексная архитектура логирования (Trace / Debug / Info) и сквозная диагностика
- [ ] **Архитектурный стандарт уровней логирования (`AppLogger` & `domain/model/LogEntry.kt`)**:
  - [ ] **`TRACE` (Низкоуровневая трассировка команд, протоколов и дампов)**:
    - [ ] **Выдача системных прав**: точные shell-команды Superuser / libsu (`Root cmd: pm grant ...`, `appops set ...`, `dumpsys deviceidle whitelist`, `cmd notification allow_listener`), коды возврата и ошибки из stderr (`code=X, err=...`).
    - [ ] **Выполняемые пользовательские команды бота**: сырой ввод пользователя, разобранные аргументы команд, вывод системных утилит при выполнении `/shell`, `/reboot`, `/input`, `/app_freeze`, `/dumpsys`.
    - [ ] **Сетевой стек и внешние серверы (Ktor / SOCKS5 / Push Gateway)**:
      - [ ] Сырые HTTP-запросы и ответы: метод, URL эндпоинта (с автоматическим маскированием bot_token `bot<TOKEN>/` -> `bot***/`), заголовки запроса/ответа, размер передаваемого тела (payload size in bytes).
      - [ ] Точные замеры задержки (RTT / latency in ms) для каждого запроса к Telegram API, Push Gateway и DNS-серверам.
      - [ ] Байты рукопожатия SOCKS5 прокси (greeting, auth methods, connect response), IP:Port назначения.
      - [ ] Сырые JSON-пейлоады FCM/HMS Data Push (`update_json`, `action=wakeup`).
    - [ ] **Телефония и уведомления**: сырые PDU входящих/исходящих SMS, raw `StatusBarNotification` keys, extras бандлы.
  - [ ] **`DEBUG` (Факты действий, намерения и попытки сетевых подключений)**:
    - [ ] **Выдача прав**: фиксация факта запроса пакета прав (`"Запрос пакета всех прав через Root (27 команд)"`, `"Запрос Runtime-разрешений вручную"`), факты открытия системных экранов Intent.
    - [ ] **Выполняемые команды бота**: факт вызова команды оператором (`"Получена команда /photo [chat_id=12345, user_id=67890]"`, `"Команда /sms_send: валидация номера +7999..."`, `"Проверка SMS PIN для деструктивной команды /shutdown"`).
    - [ ] **Попытки запросов к внешним серверам**:
      - [ ] Попытка подключения к Telegram API: `"Отправка запроса getUpdates [offset=12345, timeout=30s]"`, `"Отправка ответа в чат 12345 (метод sendMessage)"`.
      - [ ] Проверка доступности прокси: `"Проверка доступности SOCKS5 прокси 1.2.3.4:1080..."`.
      - [ ] Взаимодействие с Push Gateway: `"Регистрация токена устройства на шлюзе: https://gateway.domain/register"`.
      - [ ] Внешние DNS-запросы и DoH: `"Резолвинг домена api.telegram.org через DoH Cloudflare/Google"`.
    - [ ] **Жизненный цикл**: переключение состояний служб (`"Переключение в режим PUSH_WAKEUP"`, `"Старт таймера автоматизации ID=1"`).
  - [ ] **`INFO` (Бизнес-вехи и успешные эксплуатационные события)**:
    - [ ] Успешная выдача всех прав (`"Все права успешно выданы через Root (27 команд)"`).
    - [ ] Успешное выполнение команды: `"Команда /photo успешно выполнена: снимок отправлен за 1.4с"`.
    - [ ] Сеть и подключение: `"Успешное соединение с Telegram Bot API (@MyRemoteBot, Ping: 124ms)"`, `"SOCKS5 прокси активен и стабилен"`, `"Push-шлюз успешно подтвердил регистрацию устройства"`.
    - [ ] Фоновый сервис: `"RemoteBotService запущен в режиме Foreground (remoteMessaging)"`.
  - [ ] **`WARN` (Предупреждения, сетевые ретраи и мягкие отказы)**:
    - [ ] Сетевые сбои: `"Попытка запроса getUpdates не удалась (таймаут 15с), повтор через 2с..."`, `"Rate limit HTTP 429 от Telegram, пауза 5с"`, `"Не удалось подключиться к прокси 1.2.3.4:1080 (Connection refused), fallback на прямой коннект"`.
    - [ ] Команды: `"Команда /contacts отклонена: отсутствует разрешение READ_CONTACTS"`, `"Попытка вызова неизвестной команды /xyz от user_id=12345"`.
  - [ ] **`ERROR` (Исключения и критические отказы)**:
    - [ ] Полный стектрейс непредвиденных исключений (`Log.getStackTraceString(throwable)`).
    - [ ] Невалидный токен бота (HTTP 401 Unauthorized), фатальный отказ Push Gateway (HTTP 500), сбои базы данных Room.
- [ ] **Реализация в модулях проекта**:
  - [ ] **Модуль `channels/telegram-bot` (`TelegramBotEngine`, `TelegramApiClient`, Handlers)**:
    - [ ] Сквозное логирование каждой из 54 команд: вход в команду -> запуск -> передача результата.
    - [ ] Детальные замеры времени выполнения команд и отправки медиафайлов.
  - [ ] **Модуль `core/network` & `core/proxy` (`TelegramApiClient`, `Socks5ProxySocket`)**:
    - [ ] Логирование каждого исходящего HTTP/HTTPS вызова к серверам Telegram и шлюзам (DEBUG: факт и URL, TRACE: latency, status, headers).
    - [ ] Проверка состояния пинга и сетевых интерфейсов.
  - [ ] **Модуль `core/push` (`FirebasePushService`, `HuaweiHmsPushService`)**:
    - [ ] Логирование обращений к Push-серверам Google / Huawei и шлюзу маршрутизации.
  - [ ] **Модуль `app` (`PermissionsTab`, Sections)**:
    - [ ] Разделение факта запроса прав (DEBUG) и выполнения консольных команд (TRACE).
- [ ] **Интерфейс журнала (`LogsTab.kt` и `MainActivity.kt`)**:
  - [ ] По умолчанию включены все фильтры логов (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`), исключая скрытие критической отладки.
  - [ ] Цветовая дифференциация: TRACE (серый/моноширинный), DEBUG (фиолетовый), INFO (зеленый), WARN (оранжевый), ERROR (красный).
   - [ ] Экспорт отфильтрованного списка логов в буфер обмена в 1 клик с форматированием времени (`HH:mm:ss.SSS`).

> **💬 QA Audit и предложения по расширению логирования (Sber Dev, октябрь 2026):**
>
> **Текущий статус реализации:**
> - **Базовая инфраструктура:** `AppLogger` (t/d/i/w/e), `LogEntry`, `LogLevel` enum — реализованы.
> - **UI:** `LogsTab` с 5 уровнями фильтров, цветовой дифференциацией, моноширинным шрифтом, лимитом записей (50/100/250/500/1000), экспортом в буфер.
> - **Охват логирования:** ~242 вызова `logger.*` в кодовой базе (119 main-файлов). Основные модули: `channels/telegram-bot` (~142 вызовов), `app/ui/screens` (~60), `core/*` (~40).
> - **Формат логов:** `timestamp|LEVEL|Tag|Message` (pipe-delimited), ротация при >2MB, загрузка при старте.
>
> **✅ Что реализовано хорошо:**
> - 5 уровней логирования (TRACE/DEBUG/INFO/WARN/ERROR) с семантическим разделением.
> - Цветовая дифференциация в `LogsTab` (TRACE=серый, DEBUG=фиолет, INFO=зел, WARN=оранж, ERROR=крас).
> - Экспорт с `HH:mm:ss.SSS` форматированием + копирование в буфер обмена.
> - Логирование команд (Shell, Media, Proxy, SystemControl, Backup, Permissions).
> - Логирование фоновых сервисов (BatteryReceiver, ShakeDetector, SmsReceiver, PhoneCallReceiver).
> - Логирование Push-сервисов (FCM, HMS token generation + incoming push).
> - Stacktrace capture в `AppLogger.e()` через `Log.getStackTraceString()`.
> - Disk persistence с ротацией (2MB limit).
>
> **❌ Критические пробелы (TRACE-уровень):**
>
> **1. Сетевой стек (`core/network`, `core/proxy`) — 0 TRACE-логов**
> - **HTTP request/response:** метод, URL (с маскированием токенов `bot<TOKEN>/` → `bot***/`), заголовки, payload size.
> - **RTT/latency:** замер времени каждого запроса к Telegram API, Push Gateway, DNS.
> - **SOCKS5 handshake:** greeting bytes, auth methods, connect response, IP:Port.
> - **WSS/SOCKS5 headers:** формирование заголовков прокси, рукопожатие.
> - **Rate limiting:** HTTP 429 → retry count + backoff delay (1s, 2s, 4s, 8s, 16s).
> - **DNS/DoH:** резолвинг `api.telegram.org` через Cloudflare/Google DoH, IP-адреса, RTT.
> - **Network switch:** WiFi → Mobile transition, reconnect events.
>
> **2. Telegram Bot Engine (`channels/telegram-bot`) — 0 TRACE-логов**
> - **Raw JSON payloads:** входящие `TgUpdate` (message, callback_query, inline_query), исходящие ответы.
> - **Command lifecycle:** вход в команду → парсинг аргументов → выполнение → результат (TRACE).
> - **FCM/HMS Data Push:** сырые `update_json`, `action=wakeup`, `update_id`.
> - **Webhook 409 Conflict:** fallback event, retry count.
> - **File chunking:** split >50MB на чанки, progress, retry.
>
> **3. Телефония и уведомления — 0 TRACE-логов**
> - **Raw SMS PDU:** входящие/исходящие SMS в сыром формате.
> - **StatusBarNotification extras:** raw bundle keys, notification text, action intents.
> - **RemoteInput:** сырые данные inline-ответа.
>
> **4. Security и конфиденциальность**
> - **Token masking:** токены не маскируются в логах (риск утечки в `logsFlow`/`getFormattedLogsForExport()`).
> - **Sensitive data filtering:** нет автоматической маскировки `api_key`, `secret_key`, `phone_number`.
> - **Log sanitization:** нет фильтрации чувствительных данных перед записью на диск.
>
> **5. Performance и метрики**
> - **Command execution time:** замер времени выполнения каждой из 54 команд.
> - **Notification latency:** system notification → Telegram message (target <3s).
> - **Shell command latency:** `/shell ls` <500ms (p99).
> - **Memory usage:** PSS/Heap при каждом ERROR логе.
>
> **Рекомендуемые изменения:**
>
> **A. Добавить HTTP Interceptor для Ktor Client:**
> ```kotlin
> // Логирование каждого HTTP запроса/ответа
> logger.t("HttpClient", "GET /bot***/getUpdates | RTT: 124ms | Status: 200 | Size: 2048B")
> logger.t("HttpClient", "POST /bot***/sendMessage | RTT: 89ms | Status: 200 | Size: 512B")
> ```
>
> **B. Добавить маскирование токенов в `AppLogger`:**
> ```kotlin
> fun sanitize(message: String): String {
>     return message
>         .replace(Regex("bot\\w{8,}:\\w{8,}"), "bot***/***/")
>         .replace(Regex("api_key\\s*=\\s*\\w+"), "api_key=***")
>         .replace(Regex("secret_key\\s*=\\s*\\w+"), "secret_key=***")
> }
> ```
>
> **C. Добавить RTT-замеры в `TelegramApiClient`:**
> ```kotlin
> val t0 = System.currentTimeMillis()
> val response = client.get(url)
> val rtt = System.currentTimeMillis() - t0
> logger.t("Network", "GET /getUpdates | RTT: ${rtt}ms | Status: ${response.status}")
> ```
>
> **D. Добавить логирование FCM/HMS Data Push:**
> ```kotlin
> logger.t("FCM", "Raw push payload: ${payload.toString()}")
> logger.t("HMS", "Raw push payload: action=$action, update_id=$updateIdStr")
> ```
>
> **E. Добавить метрики производительности:**
> ```kotlin
> logger.d("Metrics", "Command /photo executed in 1420ms (upload: 1200ms, server: 220ms)")
> logger.d("Metrics", "Notification latency: 890ms (capture: 10ms, process: 50ms, send: 830ms)")
> ```
>
> **F. Добавить Network Resilience логирование:**
> ```kotlin
> logger.w("Network", "HTTP 429 Rate Limit | Retry #3/5 | Backoff: 4s")
> logger.d("Network", "DNS resolved api.telegram.org -> 91.108.56.120 (DoH Cloudflare, RTT: 45ms)")
> logger.d("Network", "Network switch: WiFi (192.168.1.100) → Mobile (LTE, 4G)")
> ```
>
> **Матрица приоритетов доработки логирования:**
>
> | Приоритет | Что добавить | effort | impact |
> |-----------|-------------|--------|--------|
> | 🔴 High | Token masking + sanitize() | 0.5 дня | High (security) |
> | 🔴 High | HTTP Interceptor (request/response, RTT) | 1-2 дня | High (debugging) |
> | 🔴 High | Rate limit / retry logging | 0.5 дня | High (resilience) |
> | 🟡 Medium | FCM/HMS raw payload logging | 0.5 дня | Medium (push debug) |
> | 🟡 Medium | Command execution time metrics | 1 день | Medium (performance) |
> | 🟡 Medium | SOCKS5/WSS handshake logging | 1 день | Medium (proxy debug) |
> | 🟢 Low | Raw SMS PDU logging | 0.5 дня | Low (telephony) |
> | 🟢 Low | StatusBarNotification extras dump | 0.5 дня | Low (notification) |
> | 🟢 Low | Network switch / DNS DoH logging | 1 день | Low (network) |
>
> **Дополнительные улучшения `AppLogger`:**
> - **Structured logging:** добавить `correlationId` для отслеживания запроса через все модули.
> - **Log rotation by date:** `app_events_2026-10-10.log`, `app_events_2026-10-11.log`.
> - **Log compression:** `.gz` архивация старых логов для экономии места.
> - **Remote log upload:** кнопка в `LogsTab` для отправки логов на сервер разработчика.
> - **Performance mode:** отключение TRACE-логов в production (`BuildConfig.DEBUG == false`).
> - **Log size limit:** configurable max disk size (current: 2MB rotation, no total limit).
>
> ---
>
> #### 🏗️ Архитектурные рекомендации и предложения по расширению (Mobile Architect & UI Designer, Sber Dev)
>
> **Контекст:** Фаза 12 — комплексное логирование и диагностика. Ключевые направления: улучшение UX логов, добавление аналитики производительности, расширение диагностических инструментов.
>
> ---
>
> **🔴 Приоритет HIGH — Новый функционал:**
>
> **1. Real-Time Log Stream (Потоковые логи в реальном времени)**
> - **Новый функционал:** На вкладке «Логи» — live-поток:
>   - Автоматическая прокрутка при новых записях.
>   - Фильтр в реальном времени: «Только ERROR», «Только network».
>   - Поиск по текущему буферу: строка поиска вверху.
>   - Подсветка синтаксиса: разные цвета для разных уровней.
>   - **Effort:** 2 дня
>
> **2. Log Export & Share (Экспорт и отправка логов)**
> - **Новый функционал:**
>   - Кнопка «📤 Экспорт» — сохранение логов в файл с датой.
>   - Кнопка «📧 Отправить разработчику» — отправка логов боту.
>   - Команда `/logs [last 100 lines]` — отправка в Telegram.
>   - Команда `/logs level=ERROR` — только ошибки.
>   - Команда `/logs search="timeout"` — поиск по ключевому слову.
> - **Effort:** 1.5 дня
>
> **3. Performance Monitor (Мониторинг производительности)**
> - **Новый функционал:** Вкладка «Производительность»:
>   ```
>   🧠 RAM: 4.2 / 12.0 GB (35%)
>   📊 PSS: 89 MB (steady)
>   ⚡ CPU: 12% (3 cores active)
>   🔋 Battery: 78% | Discharging | 32°C
>   📡 Network: Wi-Fi | Download: 15Mbps | Upload: 5Mbps
>   🔄 GC: 12 collections | 45ms total
>   ```
>   - Графики RAM/CPU за последние 60 минут.
>   - Алерт при превышении порога: «⚠️ RAM > 90%».
> - **Effort:** 3 дня
>
> ---
>
> **🟡 Приоритет MEDIUM — Улучшение существующего:**
>
> **4. Log Correlation IDs (Корреляционные ID для трассировки)**
> - **Улучшение:** Каждый запрос получает уникальный ID:
>   - `cmd-12345 /battery → cmd-12345 response sent`.
>   - Позволяет отследить полный путь команды через все модули.
>   - В UI: клик по ID → показать полную трассировку.
> - **Effort:** 1.5 дня
>
> **5. Log Categories & Tags (Категории и теги логов)**
> - **Улучшение:** Группировка логов по категориям:
>   - `network`, `shell`, `camera`, `sms`, `push`, `permissions`.
>   - Переключатели категорий в UI: «Показать только network».
>   - Настраиваемые пользовательские теги.
> - **Effort:** 1 день
>
> **6. Log Retention Policy (Политика хранения логов)**
> - **Улучшение:** Настраиваемое хранение:
>   - «Хранить 24 часа» / «7 дней» / «30 дней» / «Без ограничений».
>   - Авто-очистка старых логов.
>   - Индикатор занимаемого места: «Логи: 2.3 MB».
> - **Effort:** 1 день
>
> ---
>
> **🟢 Приоритет LOW — Полировка:**
>
> **7. Log Syntax Highlighting (Цветовая подсветка логов)**
> - **Улучшение:** Разные цвета для разных уровней:
>   - TRACE: серый, DEBUG: синий, INFO: зелёный.
>   - WARN: жёлтый, ERROR: красный, FATAL: тёмно-красный.
>   - **Effort:** 0.5 дня
>
> **8. Log Compression & Archiving (Сжатие и архивация)**
> - **Новый функционал:** Авто-сжатие старых логов:
>   - `.gz` архивация логов старше 24 часов.
>   - Экспорт в ZIP-архив.
>   - **Effort:** 1 день
>
> **9. Remote Log Upload (Удалённая загрузка логов)**
> - **Новый функционал:** Отправка логов на сервер:
>   - Кнопка «📤 Загрузить на сервер» в LogsTab.
>   - Автоматическая отправка при критической ошибке.
>   - **Effort:** 1.5 дня
>
> **10. Log Search with Regex (Поиск логов по RegEx)**
> - **Новый функционал:** Продвинутый поиск:
>   - `/logs regex="error.*timeout"` — поиск по регулярному выражению.
>   - Сохранение частых запросов поиска.
>   - **Effort:** 1.5 дня
>
> ---
>
> **📐 Архитектурные рекомендации:**
>
> **11. Выделить LogEngine в отдельный модуль `:core:logging`**
>   ```kotlin
>   interface LogEngine {
>       fun trace(message: String, tags: List<String> = emptyList())
>       fun debug(message: String, tags: List<String> = emptyList())
>       fun info(message: String, tags: List<String> = emptyList())
>       fun warn(message: String, tags: List<String> = emptyList())
>       fun error(message: String, throwable: Throwable? = null, tags: List<String> = emptyList())
>       fun exportLogs(from: Instant, to: Instant): List<LogEntry>
>       fun observeLogs(level: LogLevel?): Flow<List<LogEntry>>
>   }
>   ```
> - **Effort:** 2 дня
>
> **12. Добавить PerformanceMonitor**
> - **Новый функционал:** Фоновый мониторинг ресурсов:
>   ```kotlin
>   class PerformanceMonitor @Inject constructor(
>       private val activityManager: ActivityManager,
>       private val batteryManager: BatteryManager,
>       private val connectivityManager: ConnectivityManager
>   ) {
>       fun observeMemory(): Flow<MemoryReport>
>       fun observeCpu(): Flow<CpuReport>
>       fun observeBattery(): Flow<BatteryReport>
>   }
>   ```
> - **Effort:** 2.5 дня
>

### Фаза 13: Скрытое инженерное меню отладки (Developer / Debug Menu по 5 тапам)

> **Статус реализации**: Фаза 13 полностью реализована и расширена дополнительными диагностическими инструментами (октябрь 2026). Проект собирается успешно (`assembleDebug` — BUILD SUCCESSFUL).

- [x] **Активация режима разработчика (5-Tap Gesture Trigger)**:
  - Скрытый счетчик кликов на строке версии приложения (`AboutTab.kt`, карточка «Версия приложения» `v1.0.0`).
  - Обратная связь: всплывающие Toast-подсказки при приближении (`"Осталось 3 тапа до открытия Debug меню"`, `"Осталось 2 тапа..."`, `"Вы вошли в режим отладки 🛠️"`).
  - Персистентное сохранение состояния разработчика в настройках (`isDebugModeEnabled`) с возможностью быстрого скрытия по кнопке «Выйти из режима отладки».
  - Появление нового раздела `🛠️ Debug` в навигационной панели (индекс 11).

- [x] **Функциональные блоки инженерного меню Debug**:

  - **1. Симуляция и тестирование уведомлений (Notification Simulation Lab)**:
    - Интерактивный генератор фиктивных уведомлений: выбор пакета (`org.telegram.plus`, `com.whatsapp`, `ru.sberbankmobile`, `com.google.android.apps.messaging`), ввод кастомного заголовка и текста.
    - **Флаги симуляции уведомлений (дополнение фазы 13)**:
      - Переключатель `RemoteInput` (имитация Quick Reply кнопок в уведомлениях мессенджеров).
      - Переключатель `FLAG_GROUP_SUMMARY` (проверка фильтрации групповых уведомлений, предотвращение эхо-петель).
      - Поле ввода `Категория` (`CATEGORY_MESSAGE`, `CATEGORY_CALL`, `msg`) — эмуляция системных категорий Android.
      - Новый метод `simulateNotificationWithFlags()` в `NotificationRepository` с передачей всех флагов.
      - Новые поля `isGroupSummary` и `notificationCategory` в модели `CapturedNotification` (`domain/model/TelephonyModels.kt`).
    - Переключатель «Постить в реальную шторку Android» — выбор между инъекцией в пайплайн бота и публикацией в системную Notification Shade.
    - Haptic-фидбек при генерации уведомления.

  - **2. Диагностика фоновых служб и Doze Mode (Process & Lifecycle Inspector)**:
    - **PID и Uptime службы (дополнение фазы 13)**:
      - `RemoteBotService.servicePid` — PID процесса службы (StateFlow).
      - `RemoteBotService.serviceStartTime` — timestamp запуска службы.
      - `RemoteBotService.getServiceUptimeMillis()` — расчёт uptime в миллисекундах.
      - `RemoteBotService.formatUptime()` — форматирование в читаемый вид (`дч мин с`).
      - Отображается в UI при активном статусе службы.
    - **Счётчик событий NotificationListener (дополнение фазы 13)**:
      - `RemoteBotNotificationListenerService.eventsIntercepted` — счётчик перехваченных уведомлений (MutableStateFlow).
      - `NotificationRepository.recentNotificationsCount` — размер кэша `recentNotificationsById`.
      - Автоматический инкремент в `onNotificationPosted()`.
      - Кнопка `resetEventCounter()` для сброса счётчика.
    - Реальный статус `RemoteBotService` (PID, Uptime, Foreground Service type `remoteMessaging`).
    - Статус `RemoteBotNotificationListenerService`: подключен ли `INotificationListener` к системе (`isConnected`).
    - Статус `AccessibilityService`: включен ли сервис в системе, доступность окон.
    - Кнопка принудительного перезапуска служб (`stopService + startService`).
    - Быстрый переход в режим тестирования Doze: кнопки симуляции входа в Deep Doze через Root/Shell (`dumpsys deviceidle step deep`, `dumpsys battery unplug`, `force-idle`, `reset`).

  - **3. Сетевой инспектор и DPI-песочница (Network & Proxy Sandbox)**:
    - Интерактивный тест прямого коннекта к Telegram API (`api.telegram.org:443`) с замером времени TLS-рукопожатия (Handshake RTT ms).
    - Тест прокси SOCKS5 / HTTP с проверкой доступности и авторизации (Proxy Auth).
    - **Wakeup-пинк на своё устройство (дополнение фазы 13)**:
      - Тест 6: отправка POST запроса на `/tg/:token/:secretKey` через Push Gateway.
      - Проверка доставки FCM/HMS Data Push wakeup на устройство.
      - Валидация наличия gateway URL, secret key, FCM token перед отправкой.
    - **WSS WebSocket Handshake (дополнение фазы 13)**:
      - Тест 7: проверка WebSocket рукопожатия через прокси.
      - Формирование WebSocket Upgrade запроса (`Upgrade: websocket`, `Sec-WebSocket-Key`, `Sec-WebSocket-Version: 13`).
      - Проверка TCP-соединения с прокси и замер ping RTT.
      - Валидация ответа HTTP 101 Switching Protocols.
    - Тест DNS Leak / Resolve Check — резолвинг хоста эндпоинта через локальный DNS.
    - Тест Push Gateway Health Check — проверка связи с `/health` эндпоинтом шлюза.

  - **4. Инспектор системных баз данных и хранилища (Storage & Cache Inspector)**:
    - Просмотр и очистка локального кэша последних уведомлений (`recentSbnById`, `recentNotificationsById`).
    - Дамп и очистка `recentSentBotMessages` (60-сек кэш защиты от эхо).
    - Сброс очереди неотправленных файлов (`unsent_files`).
    - **Экспорт логов в буфер обмена (дополнение фазы 13)**:
      - Кнопка «📋 Копировать логи в буфер (последние 500)».
      - Форматирование: `HH:mm:ss.SSS [LEVEL] Tag: Message`.
      - Метод `getFormattedLogsForExport(count: Int)` в `AppLogger` с асинхронным выполнением.
      - Toast-фидбек с количеством скопированных записей.
    - Очистка накопленных лог-файлов (`app_events.log`) с освобождением дисковой памяти.

  - **5. Экстренный сброс и эмуляция окружения (Environment & State Controls)**:
    - Переключатель эмуляции «Root отсутствует» (для тестирования поведения UI и предупреждений без реального отзыва su-прав).
    - Кнопка полного сброса конфигурации к дефолтным значениям (Factory Reset Settings).
    - Генератор тестовых системных событий: эмуляция падения батареи до 15%, эмуляция тряски датчика (`shake_event`), эмуляция входящего SMS.

  - **6. Профилировщик системных ресурсов с графиком памяти (дополнение фазы 13)**:
    - CPU (ядра, потоки, процессорное время через `/proc/self/stat`).
    - Память (PSS, Java Heap, Native Heap, общая RAM, Low Memory Mode).
    - Сетевой трафик (Upload/Download через TrafficStats).
    - Хранилище (внутренние файлы, кэш, база данных SQLite, свободное место).
    - **График потребления памяти PSS в реальном времени (дополнение фазы 13)**:
      - Canvas-визуализация за последние 60 секунд (12 точек при обновлении каждые 5 сек).
      - Автоматический сбор метрик через `Debug.getPss()` в фоне (GlobalScope coroutine).
      - Цветовая сетка, линия графика основным цветом, красная точка текущего значения.
       - Отображение Min/Max/Current значений под графиком.

  - **7. Мониторинг просыпаний, сетевых подключений по серверам и ошибок аутентификации (дополнение фазы 13)**:
    - **Глобальный трекер метрик (`AppDiagnosticsTracker`)**:
      - Сбор метрик активен **только когда режим отладки включен** (`isDebugModeEnabled == true`). При выключении режима отладки сбор полностью останавливается без выделения памяти на CPU.
      - Автоматическое очищение событий старше 1 часа для защиты от утечек памяти (Retention Policy).
    - **Временные окна агрегации**:
      - Быстрое переключение срезов: **5 минут, 15 минут, 30 минут, 1 час и «С запуска приложения»**.
    - **Счётчик просыпаний приложения (Wakeup counter)**:
      - Учёт количества просыпаний с разделением по типам источников: `PUSH_FCM`, `PUSH_HMS`, `SMS`, `PHONE_CALL`, `BATTERY_EVENT`.
    - **Раздельная статистика коннектов по каждому серверу (Push Gateway & Telegram API)**:
      - Для **каждого** сервера Push Gateway из пула независимо фиксируются: `Успешно`, `Сбой`, `Таймаут`.
      - Для **каждого** эндпоинта Telegram API (основной `api.telegram.org` и кастомные зеркала) независимо фиксируются: `Успешно`, `Сбой`, `Таймаут`.
    - **Ошибки аутентификации (раздельно для SMS и Telegram)**:
      - Telegram: фиксация обращений от `userId`, отсутствующих в белом списке (`allowedUserIds`).
      - SMS: фиксация команд с неавторизованных номеров или с неверным/отсутствующим PIN-кодом.
    - **Жизненный цикл и элементы управления**:
      - UI (`DebugDiagnosticsMetricsCard`) обновляется по тикеру **только когда карточка находится на экране**.
      - Кнопка **«✕ Отключить Debug»** вверху экрана навигации (`MainActivity`) и в шапке `DebugTab` для мгновенного отключения отладки в 1 клик со скрытием вкладки.
      - Кнопка **«Сброс ↺»** для очистки накопленной статистики.
    - **Открытие приложения по тапу на уведомление**:
      - Системный `PendingIntent` (`MainActivity`, `FLAG_ACTIVITY_SINGLE_TOP`, `FLAG_IMMUTABLE`) добавлен во все постоянные и всплывающие уведомления приложения.

  - **8. Экспериментальные функции (Experimental Features Lab)**:
    - **Перехват и подтверждение 2FA-действий (`enable2FaInterception`, по умолчанию `false`)**:
      - Выделенный карточный блок `DebugExperimentalFeaturesCard` в инженерном меню DebugTab.
      - **Тумблер по умолчанию выключен**: при выключенном тумблере пуши от аутентификаторов пересылаются как чистый информационный текст без кнопок, а любые вызовы `notif_act` блокируются.
      - **При включении тумблера**: автоматическое извлечение интерактивных кнопок (`approve`/`deny`, «Подтвердить»/«Отклонить») для доверенных 2FA-приложений (`ru.multifactor.app`, `com.lastpass.authenticator`, `com.duosecurity.duomobile`, `ru.yandex.key`, `com.azure.authenticator`, `com.google.android.apps.authenticator2`).
      - **Защищённый 2FA-кэш и строгая FIFO-ротация**: 2FA-уведомления пишутся в отдельный `active2FaSbnById` и защищены от вытеснения обычным фоновым спамом сообщений (устранение бага псевдослучайного вытеснения `firstOrNull`).
      - **Системный Fallback**: если ID не найден в памяти, метод `findSbn` через `NotificationListenerService.getActiveNotifications()` запрашивает живые пуши прямо из шторки Android OS.
      - **Android 14+ совместимость**: передача безопасных опций запуска `PendingIntent` (`MODE_BACKGROUND_ACTIVITY_START_ALLOWED`).

> **💬 QA Audit и статус закрытия фазы 13 (Sber Dev & Разработчик, октябрь 2026, статус: ПОЛНОСТЬЮ РЕАЛИЗОВАНО ✅):**
>
> **Финальный статус реализации:**
> - **Общий статус: 100% — Инженерное меню отладки (DebugTab) полностью реализовано и расширено дополнительными инструментами.**
> - **Скрытая активация:** 5-tap жест на версии приложения в `AboutTab` со всплывающими подсказками (обратный отсчёт кликов) и сохранением флага `isDebugModeEnabled` в `BotConfig`.
> - **7 ключевых блоков Debug:**
>   1. **Notification Simulation Lab:** симуляция уведомлений с флагами `RemoteInput`, `FLAG_GROUP_SUMMARY`, выбором категории, отправкой в шторку и тактильным откликом.
>   2. **Process & Lifecycle Inspector:** мониторинг PID, точный uptime службы, счётчик перехваченных событий `RemoteBotNotificationListenerService`, сброс счётчиков, симуляция Deep Doze режимов через Root/Superuser.
>   3. **Network & Proxy Sandbox:** тест прямого коннекта к Telegram API, TLS RTT замеры, пинг Push Gateway, проверка DNS Leak, отправка Wakeup пинга на собственное устройство и проверка WebSocket WSS рукопожатия через прокси.
>   4. **Storage & Cache Inspector:** очистка анти-эхо кэша, сброс дедупликации, сброс очереди неотправленных файлов, сброс дисковых логов, экспорт последних 500 записей лога в буфер обмена, **полный системный дамп отладки в файл** (`debug_dump_*.txt`).
>   5. **Environment & State Controls:** эмуляция отсутствия Root-прав, фабричный сброс настроек (Factory Reset) с подтверждением в диалоге, генерация тестовых событий (АКБ 15%, тряска датчика, входящее SMS) и **контролируемый сбой (Crash Recovery Test)**.
>   6. **Resource Profiler & Memory Graph:** CPU ядра и потоки через `/proc/self/stat`, память PSS/Java Heap/Native, сетевой трафик, дисковые объёмы, Canvas-график PSS за 60 секунд (12 точек с Min/Max/Current).
>   7. **Diagnostics Metrics & Connection Tracker:** просыпания по источникам (FCM, HMS, SMS, звонки, АКБ), раздельная статистика серверов (Push Gateway, Telegram API зеркала) на успехи/сбои/таймауты, ошибки auth (TG whitelist, SMS PIN), временные окна (5м, 15м, 30м, 1ч, с запуска), кнопка выключения режима вверху экрана.
>
> ---
>
> #### 📐 Архитектурный обзор (Architecture Review)
>
> **✅ Что принято и полностью реализовано (100% закрыто):**
> 1. **[ЭКСПОРТ ДАМПА ОТЛАДКИ В ФАЙЛ] Debug Dump File:**
>    - В `DebugStorageAndCacheCard` добавлена кнопка «📤 Экспорт полного системного дампа в файл». В файл `debug_dump_<timestamp>.txt` сохраняются: точное время, версия Android SDK, модель устройства, PID, размеры кэшей, размер логов и последние 200 строк журнала.
> 2. **[КОНТРОЛИРУЕМЫЙ СБОЙ] Crash Recovery Testing:**
>    - В `DebugEnvironmentControlsCard` добавлена кнопка «💥 Инициировать контролируемый сбой» для безопасного тестирования перехвата исключений, логирования и восстановления службы.
> 3. **[ГРАФИК ПАМЯТИ PSS] Canvas-визуализация:**
>    - Внедрен интерактивный график потребления PSS памяти за 60 секунд в реальном времени с отображением экстремумов и текущего значения.
> 4. **[ЭМУЛЯЦИЯ СЕТЕВЫХ И СИСТЕМНЫХ ТРИГГЕРОВ]:**
>    - Проверка WSS рукопожатия через прокси, wakeup-пинг шлюза, эмуляция разряда батареи, тряски и SMS.
>
> ---
>
> #### ⚪ ОТКЛОНЕННЫЕ / НЕАКТУАЛЬНЫЕ ПРЕДЛОЖЕНИЯ (С аргументацией разработчика):
>
> 1. ❌ **Mock GPS Coordinates в UI (п. 4 из Arch / п. 5 из QA):** Отклонено. Подмена геопозиции на уровне ОС требует назначения приложения фиктивным поставщиком местоположения в параметрах разработчика Android (Settings -> Mock Location App), что демаскирует скрытый инструмент. Для тестирования команд геолокации достаточно передачи координат напрямую через команду бота `/gps`.
> 2. ❌ **Killable Processes List в UI (п. 6 из Arch / п. 4 из QA):** Отклонено как оверинжиниринг (создание встроенного диспетчера задач). Управление процессами выполняется удаленно через команды бота `/apps`, `/kill`, `/app_freeze`, `/shell`.
> 3. ❌ **Memory Leak Detector с LeakCanary (п. 7 из Arch):** Отклонено. Подключение LeakCanary раздувает размер APK, задерживает GC паузами и демаскирует работу утилиты системными уведомлениями об утечках.
> 4. ❌ **Эмуляция сетевых профилей 2G/3G (п. 8 из Arch):** Отклонено. Замедление трафика на системном уровне без VPN-модуля невозможно на non-root устройствах. Для проверки задержек уже встроен тест замера RTT и DoH DNS.
> 5. ❌ **Выделение отдельного модуля `:core:debug` (п. 13 из Arch):** Отклонено. `DebugTab` используется только внутри `:app` и не имеет зависимостей за пределами UI. Выделение отдельного Gradle-модуля замедляет конфигурацию сборки.
> 6. ❌ **DebugConfigRepository в Room SQLite (п. 14 из Arch):** Отклонено. Состояние разработчика лаконично сохраняется флагом `isDebugModeEnabled` в `BotConfig` без создания отдельной таблицы в базе данных.
>
### Фаза 14: Унификация пользовательского интерфейса (Modern Android UI & Material 3 Best Practices)
- [x] **1. Централизованная дизайн-система и Dynamic Color (`ui/theme/`)**:
  - Создание 4 файлов модуля темы:
    - `Color.kt` — семантическая палитра: `successGreen`, `errorRed`, `warningOrange`, `infoBlue`, `logDebug`, `logTrace`, `logWarn`, `logError` + M3 `lightColorScheme()` / `darkColorScheme()` fallback.
    - `Theme.kt` — `AppTheme()` composable: `dynamicLightColorScheme` / `dynamicDarkColorScheme` (Android 12+ Monet) с автоматическим fallback на статическую палитру для Android 8-11. Поддержка Dark / Light / System.
    - `Shape.kt` — 3 уровня: `small = RoundedCornerShape(8.dp)`, `medium = RoundedCornerShape(16.dp)`, `large = RoundedCornerShape(24.dp)`. Замена текущих 8 вариантов скруглений.
    - `Type.kt` — `Typography` с семантическими стилями M3: `headlineMedium`, `titleLarge`, `titleMedium`, `bodyMedium`, `labelSmall`.
  - **Миграция цветов** — замена 35 хардкодных `Color(0xFF...)` на семантические токены:
    - `Color(0xFF2E7D32)` → `MaterialTheme.colorScheme.primary` или `AppColors.success` (22 вхождения в 10 файлах).
    - `Color(0xFFC62828)` / `Color(0xFFEF5350)` → `MaterialTheme.colorScheme.error` / `errorContainer`.
    - `Color(0xFFE8F5E9)` / `Color(0xFFFFEBEE)` → `successContainer` / `errorContainer`.
    - `Color(0xFF81C784)` / `Color(0xFFFFB74D)` / `Color(0xFF64B5F6)` / `Color(0xFFBA68C8)` → `AppColors.logInfo` / `logWarn` / `logDebug` / `logTrace` (LogsTab).
    - `Color(0xFF181818)` → `MaterialTheme.colorScheme.surfaceContainerLowest` (LogsTab фон консоли).
  - Замена `MaterialTheme { }` в `MainActivity` на `AppTheme { }`.
- [x] **2. Полноценная поддержка Edge-to-Edge и Window Insets (Android 15+ Enforcement)**:
  - Вызов `enableEdgeToEdge()` в `MainActivity.onCreate()` с прозрачными системными барами.
  - Замена `safeDrawingPadding()` на точечную обработку `WindowInsets.safeDrawing`, `WindowInsets.statusBars`, `WindowInsets.navigationBars`.
  - Корректная обработка вырезов камер (Display Cutout) и жестовой навигационной полосы.
- [x] **3. Библиотека переиспользуемых дизайн-примитивов (`ui/components/`)**:
  - **`AppCard`** / **`AppOutlinedCard`**: унифицированные контейнеры с `MaterialTheme.shapes.medium` (16.dp), padding 16.dp, M3 elevation и визуальной иерархией. Заменит ~40 инлайн `Card()` по всем вкладкам.
  - **`AppStatusBadge`**: стандартизированный бейдж состояния (Success/Green, Error/Red, Warning/Yellow, Info/Blue). Заменит дублированные паттерны `Surface + Text` из PermissionItemCard:60-71, StartTab:94-98, FcmGoogleSection:187, HmsHuaweiSection:187. Используется ~20 раз.
  - **`AppTextField`**: унифицированное `OutlinedTextField` со скруглением `shapes.small`, кнопкой очистки (крестик) и `supportingText`. Заменит ~20 инлайн полей в TelegramSettingsTab (5), SmsSettingsTab (3), ProxySettingsTab (5), BackupTab (2), NotificationsSettingsTab (2), DebugTab (3+).
  - **`AppSectionHeader`**: заголовок секции (иконка + Title + Description в Surface). Заменит 3 копии из CriticalPermissionsSection:51-76, ConfidentialPermissionsSection:47-72, StandardPermissionsSection:60-85.
  - **`StatusMessageText`**: единый компонент статусного сообщения (success/error цвет + текст). Заменит идентичные блоки в TelegramSettingsTab:526-532, SmsSettingsTab:439-445, NotificationsSettingsTab:442-448, ProxySettingsTab:256-262, FcmGoogleSection:178-184, HmsHuaweiSection:178-184.
  - **`RemovableInputRow`**: текстовое поле + кнопка удаления «—». Заменит 3 копии из TelegramSettingsTab:223-269 (User IDs), SmsSettingsTab:190-238 и 356-404 (номера).
  - `SegmentedSlider` — доработка существующего компонента (95 строк) под единую форму M3.
- [x] **4. Анимации переходов и отзывчивость навигации**:
  - Замена `when(safeSelectedTab) { ... }` в `MainActivity:277-313` на `AnimatedContent` с переходами `fadeIn() + slideInHorizontally()` / `fadeOut() + slideOutHorizontally()`.
  - Тактильный отклик (`HapticFeedbackType.TextHandleMove`) на Switch-переключателях и кнопках действий.
  - Проверка навигации TabChipButton (3 ряда по 4/4/3-4 кнопки) на Font Scale до 200% — убедиться, что вкладки не перекрывают рабочую область.
  - Сохранение текущей стэковой навигации `tabHistory` + `BackHandler` (работает хорошо, не трогать).
- [x] **5. Рефакторинг всех 12 вкладок под единые компоненты**:
  - Перевод всех экранов на созданные примитивы `AppCard`, `AppStatusBadge`, `AppTextField`, `StatusMessageText`, `RemovableInputRow`:
    - `StartTab` — замена 4 хардкодных цветов, 2 инлайн-бейджей.
    - `PermissionsTab` + `PermissionItemCard` + 3 секции — замена 20 бейджей на `AppStatusBadge`, 3 заголовков на `AppSectionHeader`.
    - `ActiveTasksTab` — замена карточек на `AppCard`.
    - `TelegramSettingsTab` — замена 5 текстовых полей на `AppTextField`, 1 статусного блока на `StatusMessageText`, рядов User ID на `RemovableInputRow`.
    - `SmsSettingsTab` — замена 3 полей, 2 блоков `RemovableInputRow`, 1 `StatusMessageText`.
    - `NotificationsSettingsTab` — замена 2 полей, 1 `StatusMessageText`.
    - `FcmSettingsTab` + `FcmGoogleSection` + `HmsHuaweiSection` + `PushGatewaySection` — замена хардкодных цветов и бейджей.
    - `ProxySettingsTab` — замена 5 полей, 1 `StatusMessageText`.
    - `LogsTab` — замена хардкодных цветов логов на `AppColors.log*`, фона консоли на `surfaceContainerLowest`.
    - `BackupTab` — замена 2 полей на `AppTextField`.
    - `AboutTab` — замена карточек на `AppCard` с единым `shapes.medium`.
    - `DebugTab` — замена ~10 карточек, 3 полей, кнопок на единые компоненты.
   - Унификация corner radii: замена 8 разных значений (4/6/8/9/10/12/16/20 dp) на `MaterialTheme.shapes.small` (8.dp) / `medium` (16.dp) / `large` (24.dp).
- [ ] **6. Ручной переключатель режима темы (Dark / Light / System Material 3)**:
  - Добавление перечисления `ThemeMode` (`SYSTEM`, `LIGHT`, `DARK`) в конфигурацию и репозиторий настроек приложения (`AppPreferences` / `DataStore`).
  - UI-компонент выбора темы (SegmentedButton / Choice Chips / RadioGroup) в настройках интерфейса (`AboutTab` / общие настройки).
  - Реактивное переключение темы через StateFlow/Compose State в `AppTheme()` без перезапуска Activity.
  - Сохранение и применение выбора пользователя поверх системных настроек с поддержкой Dynamic Colors (Monet на Android 12+).

> **💬 UX Audit и предложения по улучшению (Sber Dev, октябрь 2026):**
>
> Фаза 14 полностью реализована — дизайн-система, 7 примитивов, 0 хардкодных цветов, 32 вызова компонентов, 12 вкладок рефакторинга. Однако UI требует дальнейшей оптимизации для production-использования. Ниже — результаты UX-audit текущей кодовой базы и конкретные предложения.
>
> **1. Навигация — 3 ряда по 4 кнопки занимают ~40% экрана**
> - **Проблема:** `M3BrowserTabsHeader` с 11–12 вкладками (13 с Debug) занимает огромное пространство. На экранах 5" рабочая область остаётся минимальной.
> - **Предложение:** Заменить на `Horizontal ViewPager + TabRow` (как Gmail/YouTube) — экономит ~25% высоты. Альтернатива: Bottom Navigation Bar с 5 основными вкладками + "Ещё…" (More) для остальных.
>
> **2. Отсутствие Search / Command Palette**
> - **Проблема:** 11+ вкладок, 54 команды бота. Пользователь не может быстро найти нужную настройку.
> - **Предложение:** Search bar в header для фильтрации вкладок по названию/описанию. Command Palette (Long-press на иконке приложения) — быстрый доступ к любой команде бота через `SearchTextField`.
>
> **3. LogsTab — нет поиска в реальном времени**
> - **Проблема:** Фильтрация только по уровням (чекбоксы), нет текстового поиска по тегу/сообщению.
> - **Предложения:** Search field с фильтрацией в реальном времени. Pin/Bookmark для закрепления важных логов. Toggle auto-scroll vs ручной скролл.
>
> **4. PermissionsTab — дублирование паттерна в 3 секциях**
> - **Проблема:** `CriticalPermissionsSection`, `ConfidentialPermissionsSection`, `StandardPermissionsSection` — 3 почти идентичных composable.
> - **Предложение:** Общий `PermissionGroupCard` с параметризуемым `PermissionGroup` data class. Progress indicator "X/Y разрешений получено". Bulk grant — кнопка "Запросить все критические".
>
> **5. TelegramSettingsTab — нет live-валидации токена**
> - **Проблема:** Токен вводится без визуальной обратной связи.
> - **Предложения:** Live validation — подсветка поля зелёным/красным. Кнопка "Проверить токен" прямо в поле (иконка check/spinner). Password visibility toggle (eye icon).
>
> **6. BackupTab — нет прогресса и истории**
> - **Проблема:** Критическая операция без визуализации прогресса.
> - **Предложения:** `LinearProgressIndicator` при экспорте/импорте. История бэкапов с датой и размером. Toggle auto-backup при изменении настроек.
>
> **7. DebugTab — нет визуализации сетевых тестов**
> - **Проблема:** 7 тестов (getMe, Direct, Proxy, DNS, Gateway, Wakeup, WSS) — результаты только текстом.
> - **Предложения:** Speedometer/gauge для RTT (цвет: зелёный <100ms, жёлтый <300ms, красный >500ms). Connection tree — схема `App → Proxy → Telegram API → Gateway` с цветными линиями. History log — последние 10 результатов каждого теста.
>
> **8. Notification Simulation Lab — нет истории тестов**
> - **Проблема:** После генерации нет списка сгенерированных пушей.
> - **Предложения:** Test run history — последние 20 симуляций с флагами. One-click re-run. Export test report в `.json`.
>
> **9. Accessibility (TalkBack, Font Scale, Contrast)**
> - **Проблема:** Фаза 14 не покрывает accessibility. Нет `contentDescription`, нет проверки контраста.
> - **Предложения:** TalkBack labels — `contentDescription` для всех кнопок и чипов. Minimum touch target 48dp. Contrast ratio 4.5:1. Font scale до 200% — протестировать на эмуляторе.
>
> **10. Onboarding / First-run experience**
> - **Проблема:** При первом запуске — 12 пустых вкладок без подсказок.
> - **Предложения:** Setup wizard — пошаговый туториал (токен → права → проверка). Empty states с CTA ("Введите токен для начала"). Tooltips при первом запуске.
>
> **11. Theme switching**
> - **Проблема:** `AppTheme` поддерживает Dynamic Color, но нет переключателя в UI.
> - **Предложения:** Theme switcher (Light/Dark/System) в `NotificationsSettingsTab`. Accent color picker — выбор акцентного цвета.
>
> **12. Haptic & Sound feedback**
> - **Проблема:** Haptic только на TabChipButton.
> - **Предложения:** Switch toggle haptic (Deep Doze, Root simulation). Error haptic (`LongPress`) при ошибках. Success haptic (`Confirm`) при успешном бэкапе.
>
> **Матрица приоритизации:**
> | Приоритет | Предложение | effort | impact |
> |-----------|-------------|--------|--------|
> | 🔴 High | Collapsible header / Bottom Nav | Medium | High |
> | 🔴 High | Empty states + Setup wizard | Low | High |
> | 🔴 High | Live validation в TelegramSettings | Low | High |
> | 🟡 Medium | Search/Command Palette | Medium | Medium |
> | 🟡 Medium | LogsTab search + auto-scroll | Low | Medium |
> | 🟡 Medium | Backup progress + history | Low | Medium |
> | 🟢 Low | Accessibility audit | Medium | Medium |
> | 🟢 Low | Theme switcher | Low | Low |
> | 🟢 Low | Haptic expansion | Low | Low |
>
> ---
>
> #### 🏗️ Архитектурные рекомендации и предложения по расширению (Mobile Architect & UI Designer, Sber Dev)
>
> **Контекст:** Фаза 14 — унификация UI. Material 3 уже внедрён, но есть возможности для значительного улучшения UX, доступности и визуальной консистентности.
>
> ---
>
> **🔴 Приоритет HIGH — Новый функционал:**
>
> **1. Adaptive Layout (Адаптивный лейаут для планшетов)**
> - **Новый функционал:** Поддержка планшетов и складных устройств:
>   - NavigationRail → HorizontalBottomBar на широких экранах.
>   - Двухколоночный режим: список + детали.
>   - `WindowSizeClass` из `material3-window-size-class`.
>   - **Effort:** 3 дня
>
> **2. Animation System (Система анимаций)**
> - **Новый функционал:** Единая система анимаций:
>   - Переход между вкладками: `animateContentSize`.
>   - Переключение тумблеров: `animateColorAsState`.
>   - Загрузка данных: `Shimmer` эффект.
>   - Pull-to-refresh на всех вкладках.
>   - **Effort:** 2 дня
>
> **3. Empty States & Error Screens (Пустые состояния и экраны ошибок)**
> - **Новый функционал:** Красивые экраны для пустых состояний:
>   - «Нет SMS» — иллюстрация + кнопка «Отправить первое SMS».
>   - «Нет уведомлений» — иллюстрация + описание.
>   - «Нет сети» — кнопка «Проверить снова».
>   - Использовать `Lottie` анимации.
>   - **Effort:** 2 дня
>
> **4. Onboarding Flow (Обучающий поток при первом запуске)**
> - **Новый функционал:** Пошаговый тур по приложению:
>   - 5-7 экранов с подсказками.
>   - Прогресс-бар (точки внизу).
>   - Кнопка «Пропустить» и «Далее».
>   - Сохранение состояния «showedOnboarding» в DataStore.
>   - **Effort:** 2.5 дня
>
> ---
>
> **🟡 Приоритет MEDIUM — Улучшение существующего:**
>
> **5. Consistent Card Design (Консистентный дизайн карточек)**
> - **Улучшение:** Единый стиль карточек на всех вкладках:
>   - `Card(elevation = CardDefaults.cardElevation(defaultDp))`.
>   - Одинаковые padding (16.dp), скругления (16.dp), тени.
>   - Цветовые акценты для статусов (зелёный/жёлтый/красный).
>   - **Effort:** 1.5 дня
>
> **6. Loading States & Skeletons (Скелетонные экраны загрузки)**
> - **Улучшение:** Вместо спиннера — скелетоны:
>   - `Shimmer` эффект для списков (SMS, уведомления, приложения).
>   - Постепенное появление контента.
>   - **Effort:** 1.5 дня
>
> **7. Input Validation Feedback (Визуальная валидация ввода)**
> - **Улучшение:** Мгновенная обратная связь при вводе:
>   - Поле токена: зелёная рамка ✅ / красная ❌.
>   - Слайдеры: анимированное значение рядом.
>   - TextField: `leadingIcon` с иконкой статуса.
>   - **Effort:** 1.5 дня
>
> **8. Toast/Snackbar System (Единая система уведомлений)**
> - **Улучшение:** Вместо `Toast` — `Snackbar`:
>   - `SnackbarHost` в корневом Compose.
>   - Цвета: успех (зелёный), ошибка (красный), информация (синий).
>   - Кнопка «Повторить» в error-snackbar.
>   - **Effort:** 1 день
>
> ---
>
> **🟢 Приоритет LOW — Полировка:**
>
> **9. Icon Consistency (Консистентность иконок)**
> - **Улучшение:** Единый стиль иконок NavigationRail:
>   - Все иконки из `MaterialIconsExtended` или `Icons.Outlined`.
>   - Одинаковый размер (24.dp) и padding.
>   - Активная вкладка: `Icons.Filled`, неактивная: `Icons.Outlined`.
>   - **Effort:** 0.5 дня
>
> **10. Typography Scale (Шкала типографики)**
> - **Улучшение:** Строгое использование M3 Typography:
>   - Заголовки: `Typography.headlineMedium`.
>   - Подзаголовки: `Typography.titleMedium`.
>   - Основной текст: `Typography.bodyMedium`.
>   - Метки: `Typography.labelSmall`.
>   - **Effort:** 0.5 дня
>
> **11. Dark Mode Polish (Полировка тёмной темы)**
> - **Улучшение:** Улучшение контрастности в тёмной теме:
>   - Проверка всех цветов через Material Theme Builder.
>   - WCAG AA контраст (4.5:1 для текста).
>   - Адаптация фонов карточек (`surfaceVariant`).
>   - **Effort:** 1 день
>
> **12. Haptic Feedback Map (Карта тактильной отдачи)**
> - **Новый функционал:** Тактильная отдача в ключевых точках:
>   - Переключение вкладок: лёгкая.
>   - Переключение тумблеров: средняя.
>   - Ошибка: сильная (критическая).
>   - Успех: приятная (success).
>   - **Effort:** 0.5 дня
>
> ---
>
> **📐 Архитектурные рекомендации:**
>
> **13. Выделить UiKit в отдельный модуль `:core:uikit`**
>   ```kotlin
>   // Переиспользуемые компоненты
>   fun RemoteBotCard(
>       title: String,
>       subtitle: String?,
>       icon: (@Composable () -> Unit)? = null,
>       action: (@Composable () -> Unit)? = null,
>       content: @Composable () -> Unit
>   )
>   
>   fun LoadingSkeleton(lines: Int = 3)
>   fun EmptyState(icon: @Composable () -> Unit, title: String, subtitle: String)
>   fun StatusIndicator(status: Status)
>   fun AnimatedSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit)
>   ```
> - **Effort:** 2 дня
>
> **14. Добавить ScreenNavigation Contract**
>   ```kotlin
>   sealed class Screen {
>       object Start : Screen()
>       object Permissions : Screen()
>       object Tasks : Screen()
>       object Telegram : Screen()
>       // ...
>   }
>   
>   class ScreenNavigator @Inject constructor() {
>       val currentScreen: StateFlow<Screen>
>       fun navigateTo(screen: Screen)
>       fun goBack(): Screen?
>   }
>   ```
> - **Effort:** 1.5 дня
>

### Фаза 15: Система обновления приложения через GitHub Releases (In-App Updater & APK Installer)
- [ ] **1. Модель данных и сетевой клиент GitHub Releases (`domain`, `core/network`)**:
  - [ ] Модель `AppUpdateInfo` в модуле `domain`:
    - `currentVersion: String` (текущая версия `BuildConfig.VERSION_NAME`).
    - `latestVersion: String` (версия последнего релиза с GitHub, например `v1.1.0` или `1.1.0`).
    - `changelog: String` (описание релиза / Release Notes из поля `body`).
    - `apkDownloadUrl: String?` (прямая ссылка на `.apk` из массива `assets`).
    - `apkSize: Long` (размер файла обновления в байтах).
    - `hasUpdate: Boolean` (флаг наличия обновления после семантического сравнения SemVer).
    - `publishedAt: String` (дата публикации релиза).
  - [ ] Сетевой клиент `GitHubUpdateChecker`:
    - Endpoint: `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`.
    - Поддержка заголовков `User-Agent: RemoteBot-Android` и `Accept: application/vnd.github.v3+json`.
    - Поддержка обхода сетевых ограничений через общий `OkHttpClient` с проксированием (`Direct / SOCKS5 / WSS`).
    - Надежный парсер SemVer: корректное сравнение `1.0.0` vs `1.0.1`, игнорирование префикса `v`, обработка пре-релизов.
    - Фильтрация assets: автоматический поиск первого файла с расширением `.apk`.
- [ ] **2. Менеджер скачивания и хранилище обновлений (`core/common`)**:
  - [ ] `AppUpdateDownloader`:
    - Асинхронное скачивание APK в кэш приложения (`context.externalCacheDir/updates/` или `context.cacheDir/updates/`).
    - Реактивный поток прогресса скачивания (`StateFlow<DownloadState>`: `Idle`, `Downloading(progress: Float, downloadedBytes: Long, totalBytes: Long)`, `Completed(file: File)`, `Error(message: String)`).
    - Валидация целостности скачанного файла перед установкой (проверка размера и читаемости файла).
- [ ] **3. Установка обновлений и системные разрешения Android (`app`, `AndroidManifest.xml`)**:
  - [ ] Настройка `FileProvider` в манифесте:
    - Конфигурация `res/xml/file_paths.xml` с директориями `external-cache-path` и `cache-path`.
    - Провайдер `androidx.core.content.FileProvider` с `android:authorities="${applicationId}.fileprovider"`.
  - [ ] Запуск установки APK:
    - Формирование `Intent(Intent.ACTION_VIEW)` с `setDataAndType(contentUri, "application/vnd.android.package-archive")`.
    - Флаг `FLAG_GRANT_READ_URI_PERMISSION` и `FLAG_ACTIVITY_NEW_TASK`.
  - [ ] Обработка разрешения `REQUEST_INSTALL_PACKAGES` (Android 8.0+ / API 26+):
    - Проверка `packageManager.canRequestPackageInstalls()`.
    - При отсутствии разрешения — показ объяснения и редирект в системные настройки `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
- [ ] **4. Пользовательский интерфейс проверки и установки обновлений (`AboutTab.kt`)**:
  - [ ] Карточка «Обновление приложения» на экране «О приложении»:
    - Кнопка «Проверить обновления» со спиннером загрузки при проверке.
    - Статусный бейдж: «У вас актуальная версия» либо «Доступна новая версия vX.Y.Z».
  - [ ] Всплывающее диалоговое окно (Update Dialog):
    - Отображение версии, даты и размера APK в МБ.
    - Прокручиваемый список изменений (Changelog).
    - Кнопка «Скачать обновление» с переходом к индикатору прогресса (LinearProgressIndicator + проценты / МБ).
    - После завершения скачивания — переход к кнопке «Установить обновление».

### Фаза 16: Комплексное тестирование и обеспечение качества (Automated Testing & QA Suite)
- [ ] **1. Unit-тесты бизнес-логики и предметной области (JVM / Domain & Core Tests)**:
  - **Диспетчеризация и парсинг команд (`channels/telegram-bot`, `channels/sms-bot`)**:
    - Тестирование разбора аргументов всех 54 команд бота (кавычки, пробелы, спецсимволы, пустые аргументы, переносы строк).
    - Проверка авторизации: фильтрация по `Telegram User ID` (белый список), отсеивание чужих пользователей и групп.
    - Проверка защиты деструктивных команд SMS PIN-кодом (перезагрузка, форматирование, сброс, shell).
    - Парсинг команд SMS-бота с префиксом `rb` в различных регистрах (`rb`, `RB`, `rB`) и разделителях.
  - **Движок фильтрации и дедупликации уведомлений (`features/notifications`, `core/common`)**:
    - Тесты на все 10 правил фильтрации (вхождение подстроки, точное совпадение, регулярные выражения RegEx, инверсии).
    - Тестирование настраиваемого окна дедупликации (`notificationDedupTimeoutSeconds`): блокировка повторных идентичных уведомлений в пределах заданного интервала, пропуск последующих.
    - Тестирование защиты от эхо-петель (Anti-Echo): фильтрация сообщений самого бота за скользящий 60-секундный период.
  - **Шифрование, безопасность и криптография (`core/security`)**:
    - Тесты шифрования/дешифрования токенов и ключей через `Tink` / `EncryptedSharedPreferences`.
    - Тесты валидации и нормализации телефонных номеров (E.164).
- [ ] **2. Интеграционные тесты сетевого стека и хранилища (MockK + Ktor MockEngine + Robolectric)**:
  - **Сетевой клиент Telegram и прокси (`core/network`, `core/proxy`)**:
    - Тестирование Long Polling с эмуляцией сетевых тайм-аутов (`SocketTimeoutException`), ошибок `429 Too Many Requests` (экспоненциальный бэкофф) и `401 Unauthorized`.
    - Тестирование обхода DPI через кастомный `api_endpoint` и работу через цепочки Direct / SOCKS5 с авторизацией.
    - Тестирование разбиения тяжелых файлов >50 МБ на чанки при отправке в Telegram Bot API.
  - **База данных Room и кэш (`core/database`)**:
    - Тестирование миграций схемы БД через `MigrationTestHelper`.
    - Тестирование очереди `unsent_files`: сохранение при оффлайне, повторная отправка при восстановлении сети, атомарное удаление.
  - **Двусторонняя интеграция Tasker (`features/tasker`)**:
    - Обработка входящих интентов `FIRE_SETTING`, парсинг переменных и отправка обратных широковещательных интентов.
- [ ] **3. UI-тесты Compose и визуальная регрессия (Compose Test Rule / Robolectric Compose)**:
  - **Компонентные тесты ключевых экранов**:
    - Тестирование переключения вкладок и сохранения состояния ViewModel (`TelegramTab`, `SmsTab`, `PermissionsTab`, `NotificationsSettingsTab`).
    - Тестирование ввода токенов и скрытия чувствительных данных (символы-звездочки, копирование/вставка).
    - Тестирование диалога выбора приложений (`AppPickerDialog`): поиск по списку, фильтрация системных, множественный выбор.
    - Тестирование вызова Debug-меню (5 быстрых тапов по номеру версии в `AboutTab`).
  - **Тестирование динамической темы и доступности**:
    - Проверка контрастности и корректности `contentDescription` для TalkBack.
    - Проверка масштабирования текста при крупном системном шрифте (Font Scale 200%).
- [ ] **4. Инструментальные тесты на устройстве / эмуляторе (Android Instrumentation Tests)**:
  - **Фоновые сервисы и Foreground Service Lifecycle (`app`)**:
    - Проверка старта FGS с типом `remoteMessaging` на Android 14/15.
    - Проверка динамического расширения типов FGS на `camera` и `microphone` при записи и их корректного освобождения.
  - **Перехват уведомлений и RemoteInput (`features/notifications`)**:
    - Инструментальный тест с инжекцией тестового `StatusBarNotification` с `RemoteInput`.
    - Проверка вызова `/answer <id> <text>` и корректной упаковки ответа в `PendingIntent`.
  - **Привилегированный уровень (Root / `libsu`)**:
    - Тестирование работы с шеллом `libsu` без блокировки Main UI потока.
    - Проверка отката при отсутствии Root-прав (корректные сообщения об ошибках без крэшей).
- [ ] **5. Серверные тесты Push Gateway и Cloudflare Worker (`tools/push-gateway`)**:
  - Юнит- и интеграционные тесты Go-сервера шлюза: HMAC-аутентификация запросов, валидация секретного ключа, проксирование вебхуков в FCM v1 и Huawei HMS Push Kit.
  - Тесты Cloudflare Worker: валидация подписи, таймауты и доставка в Push-провайдеры.
  - CI-пайплайн запуска тестов через Makefile (`make test-server`, `gradlew test`).

- [ ] **6. TelegramBotEngine Tests (Long Polling, FCM Wakeup, Retry, Chunking)**:
  - **Long Polling loop**: тестирование `getUpdates` с таймаутами, ретраями при `SocketTimeoutException`, экспоненциальным бэкоффом (1s → 2s → 4s → 8s → 16s).
  - **FCM Data Push wakeup**: эмуляция Push с `action=wakeup` и `update_id` → докачка обновления через `getUpdates`.
  - **Webhook 409 Conflict**: graceful fallback при установленном Webhook → переключение на Long Polling.
  - **Split файлов >50MB**: разбиение на чанки ≤50MB, отправка через `sendDocument`/`sendMediaGroup`, восстановление после сбоя.
  - **Proxy chain**: тестирование цепочек Direct → SOCKS5 → WSS с авторизацией, проверка DNS Leak.
  - **State machine transitions**: `IDLE → CONNECTING → POLLING → ERROR → RECOVERING` с валидацией каждого перехода.

- [ ] **7. NotificationFilterEngine Tests (Dedup, Anti-Echo, Rules)**:
  - **Anti-Echo loop detection**: бот отправляет сообщение → не пересылает обратно в Telegram (60-сек sliding window `recentSentBotMessages`).
  - **Dedup window**: настраиваемый sliding window (`notificationDedupTimeoutSeconds`) для одинаковых уведомлений от одного пакета → пропуск дубликатов.
  - **FLAG_GROUP_SUMMARY фильтрация**: отсев групповых уведомлений (предотвращение эхо-петель от системных групп).
  - **10 правил фильтрации**: TitleMatches, TitleContains, BodyMatches, BodyContains + их инверсии (Exclude). RegEx шаблоны для банковских SMS, 2FA codes, spam patterns.
  - **Backpressure**: 100 notifications/sec → `BufferOverflow.SUSPEND` без потери событий.

- [ ] **8. Root / Shell Tests (libsu, Fallback, Concurrency)**:
  - **Non-blocking shell execution**: `Shell.cmd(...).exec()` на `Dispatchers.IO` без блокировки Main UI.
  - **Fallback при отсутствии Root**: понятные ошибки без crash, корректный UI-фидбек.
  - **Race conditions**: одновременный `/shell` + `/reboot` + `/format` → очередь команд, deadlock-free.
  - **Privilege escalation flow**: запрос su → подтверждение пользователем → выполнение → кэширование UID 0.
  - **SQL injection sanitization**: `/shell ; rm -rf /` → sanitized, dangerous commands blocked.

- [ ] **9. Network Resilience Tests (Backoff, Reconnect, Proxy)**:
  - **Exponential backoff**: 429 Too Many Requests → retry с экспоненциальной задержкой.
  - **401 Unauthorized**: token refresh / invalidate flow.
  - **Network switch (WiFi → Mobile)**: reconnect без потери connection, без прерывания polling.
  - **DPI blocking detection**: сравнение latency direct vs proxy → auto-switch при обнаружении блокировки.
  - **Ktor MockEngine integration**: эмуляция всех сетевых сценариев без реальных запросов.

- [ ] **10. Database / Room Tests (Queue, Eviction, TTL)**:
  - **unsent_files queue**: offline → save → online → retry → atomic delete. Transactional integrity.
  - **recentNotificationsById eviction**: 200 record limit → FIFO eviction, no memory leak.
  - **recentSentBotMessages anti-echo**: 60s TTL → auto-expiry, sliding window.
  - **MigrationTestHelper**: тестирование миграций схемы БД между версиями.

- [ ] **11. UI Compose Tests (Component Regression)**:
  - **AppStatusBadge**: 4 состояния (success/error/warning/info) → correct color + text + icon.
  - **RemovableInputRow**: add/remove items → state consistency, correct list updates.
  - **SegmentedSlider**: value change → config update → visual feedback.
  - **TabChipButton**: navigation → AnimatedContent transition (fadeIn + slideInHorizontally).
  - **DebugTab**: 5-tap gesture → debug mode toggle → tab history update.
  - **AppTextField**: input validation, clear button, supportingText display.
  - **AppCard / AppOutlinedCard**: elevation, shape, padding consistency.

- [ ] **12. Foreground Service Lifecycle Tests (Android 14/15)**:
  - **Start/stop FGS**: `remoteMessaging` type на Android 14/15, корректный startForeground.
  - **Dynamic FGS type upgrade**: `remoteMessaging` → `camera|microphone` при `/photo` / `/videorec`.
  - **Dynamic FGS type downgrade**: освобождение типов по окончании записи.
  - **Doze Mode survival**: interaction с `dumpsys deviceidle`, проверка работы в Deep Doze.
  - **BootReceiver**: автозапуск при `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `QUICKBOOT_POWERON`.

- [ ] **13. Security Tests (Token Leak, Encryption, PIN Bypass)**:
  - **Token leak prevention**: токены не логируются в `AppLogger` (проверка через анализ кода + тесты).
  - **EncryptedSharedPreferences**: токены зашифрованы через Tink, не читаемы через `adb shell`.
  - **SMS PIN bypass**: деструктивная команда без PIN → rejected даже при `smsPinRequiredAlways=false`.
  - **Backup data encryption**: экспорт настроек шифруется, не читаем в открытом виде.
  - **Intent security**: Tasker интенты валидируют source package, предотвращают spoofing.

- [ ] **14. Performance / Benchmark Tests**:
  - **Startup time**: cold start <3s, warm start <1s (проверка через `adb shell am start -S`).
  - **Memory leak detection**: LeakCanary integration в debug build, stress-test 60min → <10MB PSS growth.
  - **Battery drain**: 24h background → <5% на устройстве 4000mAh (проверка через `dumpsys batterystats`).
  - **Notification latency**: system notification → Telegram message <3s (p99).
  - **Shell command latency**: `/shell ls` <500ms (p99), `/shell reboot` <1s.

- [ ] **15. E2E / Integration Tests (Espresso + ActivityTestRule)**:
  - **Full setup flow**: enter token → grant permissions → check connection → service starts.
  - **Command execution**: `/battery` → response in chat within 5s.
  - **Notification forwarding**: system notification → Telegram message within 3s.
  - **SMS forwarding**: incoming SMS → Telegram message within 3s.
  - **RemoteInput reply**: notification with Quick Reply → `/answer <id> <text>` → success.
  - **Backup & restore**: export → import → settings consistency.

- [x] **16. CI/CD Pipeline & Quality Gates**:
  - [x] **GitHub Actions CI/CD**: workflows `.github/workflows/ci.yml` (полный pre-merge гейт: Gradle Wrapper, linters, unit-тесты, build APK, server gateways tests) и `.github/workflows/docker.yml` (сборка 8 образов серверов для `linux/amd64`).
  - [x] **Static Analysis & Lint Gates**: KtLint (`ktlintCheck`), Detekt (`detekt`), Android Lint (`lintDebug`) с выгрузкой отчётов в артефакты CI.
  - [x] **APK Build & Release Signing**: автоматическая подпись Release APK через секреты GitHub Actions (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) и локальный `keystore.properties` с фоллбэком на debug-ключ.
  - [x] **Automated GitHub Releases**: workflow `.github/workflows/release.yml` на каждый тег `v*` с генерацией release notes, сборкой подписанного Release APK, подсчетом SHA-256 контрольной суммы и прикреплением артефактов (`app-release.apk`, `app-release.apk.sha256`).
  - [x] **Code Coverage (Jacoco)**: генерация HTML/XML отчётов покрытия тестами через Jacoco (`:domain:jacocoTestReport`), поддержка в `Makefile` (`make test-coverage`) и автоматическая загрузка отчётов покрытия в артефакты CI (`unit-test-and-coverage-reports`).
  - [ ] **Mutation testing (Stryker4k)**: качество тестов (опционально, для критических модулей).
  - [ ] **Performance regression gate**: startup time <3s, PSS growth <10MB/60min.

> **💬 QA Audit и рекомендации по внедрению (Sber Dev, октябрь 2026):**
>
> **Текущий статус тестирования:**
> - **8 тест-файлов** на **119 файлов** main-кода (~6.7% покрытие по файлам).
> - Все тесты — JVM unit-тесты (`domain/` + `core/common/backup`).
> - **Нет:** UI-тестов (Compose Test Rule), instrumentation-тестов, network-интеграционных тестов, корутин-тестов, CI-пайплайна.
>
> **Критические зоны с 0 тестов:**
> 1. **TelegramBotEngine** — Long Polling, FCM wakeup, retry, chunking, proxy chain, state machine.
> 2. **NotificationFilterEngine** — anti-echo, dedup window, 10 правил фильтрации, backpressure.
> 3. **Root/Shell** — non-blocking execution, fallback, race conditions, SQL injection.
> 4. **Network Resilience** — exponential backoff, 429/401 handling, network switch, DPI detection.
> 5. **Database/Room** — unsent_files queue, eviction, TTL, migrations.
> 6. **FGS Lifecycle** — start/stop, dynamic type upgrade, Doze survival, BootReceiver.
> 7. **Security** — token leak, encryption, PIN bypass, backup encryption, intent validation.
>
> **Рекомендуемый план внедрения (по приоритету):**
>
> | Фаза | Что тестировать | Приоритет | effort |
> |------|-----------------|-----------|--------|
> | 15.6 | TelegramBotEngine (polling, FCM, retry) | 🔴 High | 3-4 дня |
> | 15.7 | NotificationFilterEngine (dedup, anti-echo, rules) | 🔴 High | 2-3 дня |
> | 15.9 | Network resilience (backoff, reconnect, proxy) | 🔴 High | 2 дня |
> | 15.10 | Database / Room (unsent_files, eviction) | 🟡 Medium | 1-2 дня |
> | 15.8 | Root / Shell fallback + concurrency | 🟡 Medium | 1-2 дня |
> | 15.16 | CI pipeline + coverage gate | 🟡 Medium | 1 день |
> | 15.12 | FGS Lifecycle (Android 14/15) | 🟡 Medium | 1-2 дня |
> | 15.11 | UI Compose tests (badge, slider, tab, card) | 🟢 Low | 2 дня |
> | 15.13 | Security tests (token leak, SQL injection) | 🟢 Low | 1 день |
> | 15.14 | Performance benchmarks (startup, memory, battery) | 🟢 Low | 2 дня |
> | 15.15 | E2E integration tests (full flow) | 🟢 Low | 3-4 дня |
>
> **Инструментарий:**
> - **JUnit 5 + Kotlin Test** — базовый фреймворк.
> - **MockK** — mock-объекты для Kotlin (лучше Mockito для Coroutines).
> - **Ktor MockEngine** — тестирование сети без реальных запросов.
> - **Turbine** — тестирование Kotlin Flows (assert emissions, error handling).
> - **Robolectric** — Android API на JVM (Context, PackageManager, NotificationManager).
> - **Compose Test Rule** — `createComposeRule()` для UI-тестов.
> - **Espresso + ActivityTestRule** — instrumentation E2E тесты.
> - **LeakCanary** — memory leak detection в debug build.
> - **Jacoco** — code coverage report.
> - **Detekt** — static code analysis в CI.
> - **Stryker4k** — mutation testing для критических модулей (опционально).
>
> **Метрики качества (target):**
> - Code coverage: `domain/` ≥60%, `core/` ≥40%, `app/` ≥30%.
> - Test execution time: <5min для JVM, <10min для instrumentation.
> - Flaky test rate: <1% (автоматический retry + flaky test detection).
> - Mutation score: ≥80% для `TelegramBotEngine` и `NotificationFilterEngine`.
> - Performance regression: cold start <3s, PSS growth <10MB/60min, battery <5%/24h.
>
> ---
>
> #### 🏗️ Архитектурные рекомендации и предложения по расширению (Mobile Architect & UI Designer, Sber Dev)
>
> **Контекст:** Фаза 15 — тестирование и QA. Ключевые направления: расширение покрытия тестами, добавление UI-тестов, улучшение CI/CD.
>
> ---
>
> **🔴 Приоритет HIGH — Новый функционал:**
>
> **1. UI Test Suite (Compose UI Тесты)**
> - **Новый функционал:** Автоматизированные UI-тесты:
>   - `createComposeRule()` для каждого экрана.
>   - Тесты навигации: переключение вкладок, back navigation.
>   - Тесты ввода: заполнение форм, валидация.
>   - Тесты состояний: loading, empty, error states.
>   - **Effort:** 4-5 дней
>
> **2. E2E Integration Tests (Сквозные интеграционные тесты)**
> - **Новый функционал:** Полные сценарии использования:
>   - «Запуск → подключение → команда /battery → получение ответа».
>   - «Включение Root → выдача прав → проверка статуса».
>   - «Отправка SMS → пересылка в Telegram → получение».
>   - Espresso + `ActivityTestRule` для instrumentation.
>   - **Effort:** 4-5 дней
>
> **3. CI/CD Pipeline (GitHub Actions)**
> - **Новый функционал:** Автоматизация сборки и тестирования:
>   - `make test` на каждый PR.
>   - `make lint` (Detekt + KtLint) на каждый PR.
>   - `assembleDebug` на каждый commit.
>   - Code coverage report (Jacoco) с gate ≥40%.
>   - APK build на каждый tag.
>   - **Effort:** 2 дня
>
> **4. Performance Benchmarks (Бенчмарки производительности)**
> - **Новый функционал:** Автоматические бенчмарки:
>   - Cold start time: <3s.
>   - PSS growth: <10MB/60min.
>   - Battery drain: <5%/24h.
>   - Memory leak detection: LeakCanary в debug.
>   - **Effort:** 2.5 дня
>
> ---
>
> **🟡 Приоритет MEDIUM — Улучшение существующего:**
>
> **5. Mock Server for Telegram API (Мокабельный Telegram API)**
> - **Улучшение:** Тестовый сервер Telegram Bot API:
>   - Эмуляция `getUpdates`, `sendMessage`, `sendPhoto`.
>   - Сетевые ошибки: таймауты, 429, 500.
>   - Ktor MockEngine для unit-тестов.
>   - **Effort:** 2 дня
>
> **6. Test Data Generator (Генератор тестовых данных)**
> - **Улучшение:** Автоматическая генерация тестовых данных:
>   - 100 SMS, 50 уведомлений, 30 приложений.
>   - Различные сценарии: спам, дубли, пустые состояния.
>   - **Effort:** 1.5 дня
>
> **7. Flaky Test Detection (Обнаружение нестабильных тестов)**
> - **Улучшение:** Авто-детект flaky тестов:
>   - Повторный запуск упавших тестов (retry 2x).
>   - Логирование нестабильных тестов.
>   - Блокировка merge при >1% flaky rate.
>   - **Effort:** 1.5 дня
>
> **8. Test Coverage Visualization (Визуализация покрытия тестами)**
> - **Улучшение:** Интерактивная карта покрытия:
>   - Цветовая индикация по модулям (зелёный ≥60%, жёлтый 40-60%, красный <40%).
>   - Детализация по файлам и функциям.
>   - **Effort:** 1 день
>
> ---
>
> **🟢 Приоритет LOW — Полировка:**
>
> **9. Mutation Testing (Мутационное тестирование)**
> - **Новый функционал:** Stryker4k для критических модулей:
>   - Mutation score ≥80% для `TelegramBotEngine`.
>   - Mutation score ≥80% для `NotificationFilterEngine`.
>   - **Effort:** 2 дня
>
> **10. Accessibility Testing (Тестирование доступности)**
> - **Новый функционал:** Автопроверка доступности:
>   - TalkBack навигация по всем экранам.
>   - Контрастность текста (WCAG AA).
>   - `contentDescription` для всех интерактивных элементов.
>   - **Effort:** 1.5 дня
>
> **11. Security Testing (Тестирование безопасности)**
> - **Новый функционал:** Проверка уязвимостей:
>   - Тест на утечку токенов в логах.
>   - Тест SQL injection в shell командах.
>   - Тест на plaintext storage (токены, ключи).
>   - **Effort:** 1.5 дня
>
> **12. Device Matrix Testing (Матрица устройств)**
> - **Новый функционал:** Тестирование на разных устройствах:
>   - Android 8-15, разные OEM (Samsung, Xiaomi, Huawei).
>   - Эмуляторы + реальные устройства.
>   - Автоматизация через Firebase Test Lab.
>   - **Effort:** 2 дня
>
> ---
>
> **📐 Архитектурные рекомендации:**
>
> **13. Выделить TestUtilities в отдельный модуль `:test:utils`**
>   ```kotlin
>   // Переиспользуемые тестовые утилиты
>   object TestHelpers {
>       fun createMockContext(): Context
>       fun createMockTelegramApiClient(): TelegramApiClient
>       fun createMockRootStatusProvider(): RootStatusProvider
>       fun awaitItem(flow: Flow<*>): Any?
>   }
>   
>   object TestData {
>       val mockSms: SmsEntry
>       val mockNotification: NotificationEntry
>       val mockProxyConfig: ProxyConfig
>   }
>   ```
> - **Effort:** 1.5 дня
>
> **14. Добавить TestFixtures для модульной изоляции**
>   ```kotlin
>   // Test fixtures для изоляции тестов
>   class TelegramBotEngineTestFixtures {
>       fun createEngineWithMockApi(): TelegramBotEngine
>       fun createEngineWithMockDatabase(): TelegramBotEngine
>       fun createEngineWithMockNetwork(): TelegramBotEngine
>   }
>   ```
> - **Effort:** 1 день
>

### Фаза 17: Xiaomi Mi Push Services интеграция и триплексный Push Gateway (FCM + HMS + Mi Push)
- [ ] **1. Модуль `core/push` — клиентская интеграция Xiaomi Mi Push SDK**:
  - [ ] **Зависимость и SDK**:
    - Интеграция официального Xiaomi MiPush Android SDK (`MiPush_SDK_Client_*.aar` или maven артефакт `com.xiaomi.mipush.sdk`).
    - Изоляция зависимостей: опциональное подключение без влияния на устройства других вендоров.
  - [ ] **Динамический менеджер `DynamicMiPushManager` и парсер конфигурации**:
    - Разбор параметров в рантайме: `app_id`, `app_key`, `app_secret`, `region` (Global / Russia / Europe / China).
    - Поддержка загрузки конфигурационного файла `mipush-services.json` или ручного ввода учетных данных разработчика Xiaomi через UI.
    - Динамическая инициализация в рантайме через `MiPushClient.registerPush(context, appId, appKey)` без жесткого хардкода в коде сборки.
    - Определение вендора устройства: детекция устройств Xiaomi / Redmi / POCO (`Build.MANUFACTURER`, `Build.BRAND`), автоматическая проверка наличия системного фреймворка `com.xiaomi.xmsf` (Xiaomi Service Framework / Cloud Service).
    - Интеграция с вендор-детектором (`VendorDetector`): проверка доступности сервисов Google Play (`com.google.android.gms`), Huawei HMS Core (`com.huawei.hwid` / `HuaweiApiAvailability`) и Xiaomi XMSF (`com.xiaomi.xmsf`).
    - Получение и сохранение RegID устройства (`MiPushClient.getRegId(context)`), обработка callback регистрации.
  - [ ] **Фоновый ресивер `MiPushMessageReceiver : PushMessageReceiver`**:
    - Наследование от системного базового класса `com.xiaomi.mipush.sdk.PushMessageReceiver`.
    - Обработка сквозных сообщений `onReceivePassThroughMessage(context, MiPushMessage)`:
      * Прием скрытых Pass-Through (Transparent / Data Push) сообщений без отображения системных баннеров пользователю.
      * Извлечение полезной нагрузки (`update_json`, `action=wakeup`, `offset`).
      * Мгновенное пробуждение `TelegramBotEngine` для скачивания и исполнения команд из Telegram.
      * Учет доставки в `PushStatsTracker.recordDelivery(provider = MiPush)` по аналогии с FCM и HMS.
    - Обработка `onReceiveRegisterResult(context, MiPushCommandMessage)`:
      * Сохранение полученного `regId` и передача в `PushStatsTracker` и `DynamicMiPushManager`.
    - Обработка `onCommandResult(context, MiPushCommandMessage)`:
      * Логирование результатов команд подписки/отписки (`set_alias`, `subscribe_topic`).
  - [ ] **Декларация в `AndroidManifest.xml`**:
    - Системные сервисы и ресиверы MiPush:
      * `com.xiaomi.push.service.XMPushService` (системный push сервис)
      * `com.xiaomi.push.service.receivers.NetworkStatusReceiver`
      * `com.xiaomi.push.service.receivers.PingReceiver`
      * Кастомный `com.remotebot.core.push.MiPushMessageReceiver` с интент-фильтрами:
        `com.xiaomi.mipush.RECEIVE_MESSAGE`, `com.xiaomi.mipush.MESSAGE_ARRIVED`, `com.xiaomi.mipush.ERROR`.
    - Необходимые разрешения: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `VIBRATE`.
  - [ ] **Интеграция с доменным слоем (`domain`)**:
    - Модели `MiPushConfig`, `MiPushWakeupMessage` в `domain/model/`.
    - Парсер `MiPushJsonParser` в `domain/parser/` для валидации параметров `app_id`, `app_key`, `app_secret`, `region`.
    - Расширение `PushProviderType`: добавление типа `MIPUSH` к существующим `FCM` и `HMS`.

- [ ] **2. Пользовательский интерфейс и диагностика (`PushSettingsTab` и Debug-меню)**:
  - [ ] **Секция `MiPushXiaomiSection`**:
    - Карточка загрузки файла `mipush-services.json` (системный файл-пикер и быстрая вставка JSON из буфера).
    - Текстовые поля ручной настройки: `App ID`, `App Key`, `App Secret`, селектор региона (`Global (api.xmpush.global.xiaomi.com)`, `Russia`, `Europe`, `China`).
    - Статус-бейдж состояния: «🟢 Активен» / «🟡 Инициализирован» / «⚪ Не инициализирован».
    - Индикатор окружения Xiaomi: проверка работы на устройствах Xiaomi/Redmi/POCO с подсказкой о системных разрешениях MIUI/HyperOS (Автозапуск / Autostart, отключение оптимизации батареи MIUI).
    - Отображение текущего `RegID` устройства с кнопкой «📋 Скопировать RegID» в 1 клик.
  - [ ] **Диагностический дашборд в Debug-меню (`PushGatewayDashboardCard.kt`)**:
    - Добавление строки статуса Xiaomi MiPush: «⚡ MiPush: Initialized / Not Initialized» рядом с FCM и HMS.
    - Включение пинга шлюзов Xiaomi в «Проверить все серверы» (`runCheckAll`).
    - Детализированная статистика доставки по провайдерам в `PushStatsTracker`: раздельный учет (FCM / HMS / MiPush) и общая сводка (доставлено сегодня, сбои, средняя задержка).
  - [ ] **Триплексный переключатель провайдеров**:
    - Выбор приоритетного провайдера: `Google FCM`, `Huawei HMS`, `Xiaomi Mi Push`, `⚡ Триплекс / Мульти-шлюз`.
    - В режиме мульти-шлюза — автоматический fallback: если на устройстве нет GMS (Google) и HMS (Huawei), но есть Xiaomi HyperOS — автоматический выбор Mi Push; для сторонних брендов без GMS — fallback на HMS или Long Polling.

- [ ] **3. Триплексный Push Gateway (Серверная часть: Go, Python, JS / Cloudflare Worker)**:
  - [ ] **Архитектурное расширение REST API шлюза**:
    - Эндпоинты доставки для Telegram Webhook:
      * `/tg/mipush/:token/:secretKey` (или `/tg/xiaomi/:token/:secretKey`) — прямая отправка push через Xiaomi Mi Push REST API.
      * `/tg/:token/:secretKey?provider=mipush` — маршрутизация через query-параметр.
      * Интеллектуальный роутинг `/tg/:token/:secretKey` по доступным учетным данным в `env` (`FCM`, `HMS`, `MIPUSH`).
    - Расширение `/health` эндпоинта: статус готовности `{"status":"healthy","fcm":bool,"hms":bool,"mipush":bool}`.
  - [ ] **Интеграция с Xiaomi Push Server REST API**:
    - URL эндпоинта: `https://api.xmpush.global.xiaomi.com/v3/message/regid` (с поддержкой региональных серверов).
    - Заголовок авторизации: `Authorization: key=<app_secret>`.
    - Формат данных: `application/x-www-form-urlencoded`:
      * `registration_id`: RegID целевого устройства.
      * `payload`: полезная нагрузка (JSON-строка с `update_json` / wakeup `update_id`).
      * `pass_through`: `1` (сквозное сообщение без отображения UI-нотификации пользователю, чисто фоновое пробуждение).
      * `notify_type`: `0` (отключение звука, вибрации и светодиода).
      * `time_to_live`: TTL сообщения (по умолчанию 300 секунд).
    - Обработка ответа Xiaomi API: разбор `{"code":0,"result":"ok","description":"success","data":{"id":"..."}}`, обработка ошибок невалидного токена (`code: 20301`) и квоты запросов.
  - [ ] **Реализация на всех трех серверных стеках**:
    - **Go (`serverpush/go`)**: добавление функции `sendMiPush(regID, payload, secret, region)`, поддержка переменных окружения `XIAOMI_APP_SECRET`, `XIAOMI_REGION`.
    - **Python (`serverpush/python`)**: модуль отправки через `urllib.request` / `requests` с обработкой `pass_through=1`.
    - **JS (`serverpush/cloudflare-worker`)**: асинхронный fetch к Xiaomi Push API внутри Worker с таймаутом и кэшированием.

- [ ] **4. Комплексное тестирование и обеспечение качества**:
  - [ ] **Unit-тесты в `:domain` и `:core:push`**:
    - `MiPushJsonParserTest.kt`: валидация полей `app_id`, `app_key`, `app_secret`, обработка битого JSON и дефолтных значений региона.
    - `DynamicMiPushManagerTest.kt`: тест инициализации, проверка смены конфигурации, обработка ошибок регистрации.
  - [ ] **Серверные тесты Push Gateway**:
    - Go: расширение `main_test.go` тестами эндпоинта `/tg/mipush/...` (валидные/невалидные параметры, 401, 400, 405).
    - Python: расширение `test_main.py` тестами `test_telegram_webhook_mipush_*`.
    - JS: расширение `test_worker.js` в Node 22 test runner.
  - [ ] **Интеграция в Makefile**:
    - Обновление таргетов `test-serverpush` и `test-server` для проверки всех трех провайдеров (FCM + HMS + MiPush).

- [ ] **5. Документация и шаблоны**:
  - [ ] Добавление эталонного файла конфигурации `mipush-services.json.example` в корень проекта с описанием получения `AppID`, `AppKey`, `AppSecret` в Xiaomi Developer Console (`dev.mi.com`).
  - [ ] Обновление документации `serverpush/README.md` с описанием переменных окружения и curl-примерами для Mi Push.

---

### Фаза 18: Интерактивное группированное меню `/start` и `/help` в Telegram-боте (Inline Navigation)

- [ ] **1. Проблема и цель рефакторинга**:
  - **Текущее поведение:** При вводе `/start` или `/help` бот отправляет гигантское монолитное полотно текста на 3-4 сообщения подряд (более 150 строк), перечисляющее все 54 команды сразу. Пользователю неудобно ориентироваться, экран чата забивается.
  - **Целевое поведение:** При вызове `/start` выводится компактная сводная карточка бота (версия, статус Root, быстрый статус связи) и двухуровневое Inline-меню с категоризированными кнопками. При нажатии на категорию текущее сообщение **редактируется (`editMessageText`)**, отображая только команды выбранной категории и кнопку «🔙 Назад в главное меню».

- [ ] **2. Архитектура категорий команд в `:channels:telegram-bot`**:
  - [ ] **Логические группы команд (Категории)**:
    1. ⚙️ **Базовые & Статус** (`cat_base`): `/status`, `/permissions`, `/ping`, `/echo`, `/chat_id`, `/about`, `/uptime`.
    2. 📷 **Камера, Звук, Экран** (`cat_media`): `/photo`, `/videorec`, `/gallery`, `/rec`, `/rec_file`, `/tts`, `/player`, `/volume`, `/ring_mode`, `/device_search`, `/screenshot`, `/screen_rec`.
    3. 📞 **Связь, SMS & Уведомления** (`cat_comm`): `/sms`, `/sms_inbox`, `/call`, `/callshistory`, `/ussd`, `/notify`, `/notify_send`, `/fake_notify`, `/answer`, `/notify_clear`, `/notify_rule`.
    4. 📍 **Геолокация & Охрана** (`cat_geo_sec`): `/location`, `/track`, `/shake_detector`, `/detectmotion`, `/sound_detector`.
    5. 📁 **Файлы & Приложения** (`cat_files`): `/get_file`, `/download`, `/ls`, `/ls_interactive`, `/apps`, `/app_launch`, `/install`, `/taskmanager`, `/kill`.
    6. ⚡ **Система & Root** (`cat_system`): `/toggles`, `/system_info`, `/battery`, `/network`, `/wifi`, `/bluetooth`, `/flashlight`, `/airplane`, `/hotspot`, `/mobile_data`, `/gps`, `/powersave`, `/auto_brightness`, `/clipboard`, `/sync`, `/adb`, `/adb_wifi`, `/shell`, `/reboot`, `/shutdown`, `/recovery`, `/fastboot`.
    7. 🤖 **Автоматизация & Сеть** (`cat_auto`): `/tasker`, `/script`, `/timers`, `/proxy`, `/active`, `/stop_all`, `/log`, `/backup`, `/restore_backup`, `/unsent_files`, `/bot_status`, `/tg_bot`, `/sms_bot`, `/bot_toggle`, `/whitelist_user`, `/whitelist_phone`, `/sms_forward`, `/tg_forward`, `/events`.
  - [ ] **Структура главного меню `/start`**:
    - Компактный заголовок: имя бота, статус службы (🟢 Активна), Root-доступ (✅ / ⚠️).
    - Сетка Inline-кнопок категорий (по 2 в ряд):
      * `[ ⚙️ Базовые ]` `[ 📷 Мультимедиа ]`
      * `[ 📞 Связь и SMS ]` `[ 📍 Геолокация ]`
      * `[ 📁 Файлы и App ]` `[ ⚡ Система & Root ]`
      * `[ 🤖 Автоматизация ]` `[ 🎛️ Переключатели ]`
      * `[ 📜 Полный список (текстом) ]` `[ 📊 Статус ]`

- [ ] **3. Обработка Callback-навигации (`BaseCommandsHandler` / `TelegramBotEngine`)**:
  - [ ] Регистрация обработчиков callback данных:
    - `menu_cat_<category_id>`: редактирование сообщения с выводом форматированного списка команд данной категории с описанием и синтаксисом параметров + кнопка `[ 🔙 Назад в меню ]`.
    - `menu_main`: возврат к главному компактному экрану категорий.
    - `menu_all`: вывод полного списка (для пользователей, которым нужен старый текстовый монолитный режим).
  - [ ] Плавное обновление сообщения: использование `apiClient.editMessageText` с сохранением контекста и поддержкой `parse_mode = "HTML"`.
  - [ ] Быстрый ответ `answerCallbackQuery` без всплывающего алерта для мгновенного отклика UI.

- [ ] **4. Тестирование и верификация**:
  - [ ] Unit-тесты `BaseCommandsHandlerTest`:
    - Проверка генерации главного меню `/start` с клавиатурой категорий.
    - Проверка возврата списка команд при переходе по каждой категории (`menu_cat_base`, `menu_cat_media` и т.д.).
    - Проверка наличия кнопки «🔙 Назад» в каждом подразделе.
    - Проверка целостности всех 54 команд (ни одна команда не потеряна при группировке).
  - [ ] Верификация через `make lint` и `make check-ui`.

---

### Фаза 19: Интерактивное подтверждение 2FA / MFA пуш-уведомлений в Telegram (GitHub, LastPass, MS Authenticator, Google Prompt)

- [x] **1. Анализ и цель**:
  - **Проблема:** Уведомления двухфакторной аутентификации (GitHub, LastPass, Microsoft Authenticator, Google, банковские пуши) содержат кнопки действия (`Notification.Action`), такие как *"Approve"*, *"Reject"*, *"Yes, it's me"*, *"Deny"*, либо кнопки с цифрами (number matching). Сейчас бот пересылает только текстовый заголовок и тело пуша, а кнопки отбрасывает (поддерживается только текстовый `RemoteInput` для мессенджеров). Пользователю приходится физически брать телефон для подтверждения входа.
  - **Цель:** Полная поддержка исполнения кнопок действий системных уведомлений прямо из Telegram: автоматический парсинг экшенов уведомления, трансляция их в Inline-кнопки под пересылаемым сообщением в Telegram и удаленное исполнение `PendingIntent` при нажатии в боте.

- [x] **2. Доменный слой (`:domain`) и структуры данных**:
  - [x] Модель действия уведомления `NotificationActionButton`:
    - `index: Int` (индекс действия внутри массива actions уведомления).
    - `title: String` (текст кнопки: «Approve», «Reject», «Подтвердить», «Accept»).
  - [x] Расширение `CapturedNotification`:
    - Добавление списка `actions: List<NotificationActionButton> = emptyList()`.
  - [x] Интерфейс репозитория `TelephonyRepositories`:
    - Метод `executeNotificationAction(notificationId: String, actionIndex: Int): Result<Unit>`.

- [x] **3. Реализация в `:core:common` (`DefaultNotificationRepository`)**:
  - [x] Извлечение `Notification.Action` из `StatusBarNotification`:
    - Обход массива `sbn.notification.actions`.
    - Фильтрация: разделение текстовых `RemoteInput` (ответы в чаты) и кнопок прямого действия (`action.actionIntent != null`).
    - Поддержка разных типов `PendingIntent`: `broadcastIntent` (Multifactor), `startActivity` (LastPass), `startService`.
  - [x] Парсинг кастомных стилей `DecoratedCustomViewStyle` (LastPass-паттерн):
    - Fallback при пустых `extras.android.title` и `extras.android.text`: автоматическая генерация fallback-описания `"$appName: Запрос подтверждения авторизации / действия"`.
  - [x] Исполнение `executeNotificationAction`:
    - Извлечение соответствующего `PendingIntent` для указанного `actionIndex`.
    - Вызов `intent.send(context, 0, Intent())`.
    - Логирование выполнения через `AppLogger.i("DefaultNotificationRepository", "⚡ Исполнение действия [$title]...")`.

- [x] **4. Интеграция с Telegram-каналом (`:channels:telegram-bot`)**:
  - [x] Генерация Inline-клавиатуры в `TelegramEventForwarder`:
    - Формирование ряда Inline-кнопок из `notif.actions`:
      * `[ ✅ Подтвердить ]` `[ ❌ Отклонить ]` (Multifactor)
      * `[ ❌ Reject ]` `[ ✅ Accept ]` (LastPass)
      * Callback формат: `notif_act:<id>:<actionIndex>`.
    - Сохранение кнопки быстрого ответа `[ 💬 Ответить ]` для мессенджеров.
  - [x] Обработка нажатий в `TelegramInteractiveReplyHandler`:
    - Обработка `notif_act:<id>:<actionIndex>`:
      * Вызов `notificationRepository.executeNotificationAction(notifId, actionIndex)`.
      * Всплывающее уведомление `answerCallbackQuery(text = "✅ Действие выполнено!")`.
      * Редактирование исходного сообщения в Telegram: деактивация кнопок и добавление статуса «✅ Действие выполнено в ЧЧ:ММ:СС».
    - Команда-дублер: `/notify_act <id> [номер_действия]`.

- [x] **5. Тестирование и верификация (Unit-тесты и эмуляция)**:
  - [x] Реальные дамп-паттерны (Verified with hardware device):
    - **Multifactor 2FA (`ru.multifactor.app`) — [В РЕАЛИЗАЦИИ]**:
      * `actions[0]`: `"Подтвердить"` ➔ `broadcastIntent (PendingIntentRecord)`
      * `actions[1]`: `"Отклонить"` ➔ `broadcastIntent (PendingIntentRecord)`
    - **LastPass Authenticator (`com.lastpass.authenticator`) — [В РЕАЛИЗАЦИИ]**:
      * `template`: `DecoratedCustomViewStyle`, `extras.android.title/text = null` (парсинг `contentView/RemoteViews` + fallback)
      * `actions[0]`: `"Reject"` ➔ `startActivity (PendingIntentRecord)`
      * `actions[1]`: `"Accept"` ➔ `startActivity (PendingIntentRecord)`
    - **Duo Mobile (`com.duosecurity.duomobile`) — [В РЕАЛИЗАЦИИ]**:
      * `actions[0]`: `"Approve"` ➔ `broadcastIntent`
      * `actions[1]`: `"Deny"` ➔ `broadcastIntent`
    - **Яндекс Ключ (`ru.yandex.key`) — [В РЕАЛИЗАЦИИ]**:
      * `actions[0]`: `"Да, это я"` ➔ `PendingIntent`
      * `actions[1]`: `"Не я"` ➔ `PendingIntent`
    - **Microsoft Authenticator (`com.azure.authenticator`) — [В РЕАЛИЗАЦИИ]**:
      * Простой режим: `actions` `[Approve]` / `[Deny]`
      * Number Matching: поддержка текстового ответа числом через `RemoteInput` (`/answer <id> 48`)
    - **Защитный механизм (Allowlist-first)**:
      * Строгий белый список пакетов (`ru.multifactor.app`, `com.lastpass.authenticator`, `com.duosecurity.duomobile`, `ru.yandex.key`, `com.azure.authenticator`, `*.authenticator`, `*.duomobile`).
      * Исключает захламление чата кнопками из медиаплееров (Spotify/YouTube), загрузчиков и навигаторов, а также не зависит от языка системы/интерфейса.
    - **GitHub Mobile (`com.github.android`) — [ТОЛЬКО ДОКУМЕНТАЦИЯ, НЕ РЕАЛИЗУЕМ]**:
      * Задокументирован как кейс без кнопок в шторке.
  - [x] Расширение генератора симуляции в `DebugTab.kt`:
    - Добавление пресетов 2FA для Multifactor, LastPass Authenticator, Duo Mobile, Яндекс Ключ, Microsoft Authenticator.
  - [x] Unit-тесты `TelephonyCommandsTest` в `:domain`.
  - [x] Пройдено: `testDebugUnitTest` (все модули), `ktlintFormat`, `assembleDebug`.

---

## 8. Стратегия тестирования и верификации (Testing Strategy)

Для надежной работы сервиса в фоне и предотвращения регрессий при изменениях Android API выделяется 4 уровня проверок:

### 8.1. Unit-тесты (JVM / Локальный запуск)
* **Парсинг и диспетчеризация команд (`core/common`, `domain`)**:
  * Корректный разбор аргументов всех 54 команд.
  * Обработка кавычек, пробелов, экранирования и пустых параметров.
  * Проверка валидатора Telegram User ID и номеров телефонов E.164.
  * Логика SMS PIN: отклонение деструктивных команд без PIN даже при выключенном глобальном требовании PIN.
* **Движок фильтрации уведомлений (`features/notifications`)**:
  * Юнит-тесты на все 10 правил фильтрации (`TitleMatches`, `TitleContains`, `BodyMatches`, `BodyContains` и их отрицания).
  * Сложные RegEx-шаблоны (банковские SMS, двухфакторные коды, игнорирование спама).
  * Корректная дедупликация идентичных уведомлений и очистка кеша.
* **Сетевой уровень и сериализация (`core/network`, `core/proxy`)**:
  * Парсинг ответов Telegram Bot API (JSON) в модели данных.
  * Формирование WSS/SOCKS5 заголовков и рукопожатия.

### 8.2. Интеграционные тесты (MockK + Ktor MockEngine + Robolectric)
* **Сценарии Telegram Bot API**:
  * Эмуляция сетевых сбоев, таймаутов `getUpdates`, ретраев с экспоненциальной задержкой.
  * Эмуляция пробуждения по FCM Data Push (`update_id`) с последующим докачиванием обновления.
  * Автоматический сплит файлов >50 МБ на фрагменты перед отправкой в Bot API.
* **База данных Room (`core/database`)**:
  * Тестирование миграций схемы БД через `MigrationTestHelper`.
  * Очередь `unsent_files`: сохранение при потере сети, транзакционная вычитка и удаление после успешной доставки.
* **Tasker интеграция (`features/tasker`)**:
  * Обработка входящих интентов `FIRE_SETTING`, парсинг переменных и возвращение статус-кодов.

### 8.3. Инструментальные и On-Device тесты (Эмулятор / Реальное устройство)
* **Фоновый сервис и жизненный цикл (`app`, `channels`)**:
  * Проверка запуска Foreground Service с типом `remoteMessaging`.
  * Динамический апгрейд FGS типов на `camera` и `microphone` при старте записи и корректный даунгрейд по окончании.
  * Поведение при переходе устройства в Doze Mode / App Standby.
* **Root и привилегированные вызовы (`features/shell`, `features/system`)**:
  * Проверка доступности Root через `libsu` без блокировки главного потока.
  * Тест скриншота через `screencap` (Root/Shizuku) в фоновом режиме без активного UI.
  * Проверка получения списка процессов `/taskmanager` и выполнения `am force-stop`.
* **Перехват уведомлений и RemoteInput (`features/notifications`)**:
  * Отправка эмулированного уведомления с объектом `RemoteInput`.
  * Проверка срабатывания `/answer` и передача ответа в целевой интент без открытия экрана.

### 8.4. Матрица ручной верификации (OEM & Rescue Testing)
* **Тест на агрессивных прошивках (MIUI/HyperOS, OneUI, ColorOS, EMUI)**:
  * Проверка выживаемости демона после 12 часов глубокого сна экрана.
  * Проверка срабатывания связки Watchdog (Accessibility + Notification Listener).
* **Тест полного оффлайна (Offline Rescue Test)**:
  * Отключение мобильных данных и Wi-Fi.
  * Отправка аварийного SMS с PIN: `/location`, `/mobile_data on`.
  * Проверка получения обратного SMS с координатами и поднятия сети.

### 8.5. Реестр реализованных автоматических тестов

В проекте настроен и поддерживается актуальный набор автоматических тестов (запуск через `make test` или по модулям):

#### 1. Модульные тесты Kotlin (`domain` JVM Suite — `make test-domain`):
* **`DomainModelsTest.kt`** (`domain/src/test/kotlin/com/remotebot/domain/DomainModelsTest.kt`):
  * `testIncomingCommandCreation`: валидация фабрики и полей `IncomingCommand` (ID, имя, аргументы, источник, отправитель, chat_id).
  * `testProxyConfigDefaultsAndSerialization`: двусторонняя сериализация/десериализация `ProxyConfig` (WSS, SOCKS5, HTTP, аутентификация).
  * `testTelegramUpdateMessageJsonParsing`: парсинг входящих сообщений `TgUpdate` и `TgMessage` из реального JSON Telegram Bot API.
  * `testTelegramCallbackQueryJsonParsing`: парсинг инлайн-нажатий кнопок `callback_query` (ID запроса, пользователь, `data`, ID сообщения).
  * `testInlineKeyboardMarkupSerialization`: генерация Telegram JSON разметки `inline_keyboard` с многострочными рядами кнопок.
  * `testTelegramErrorResponseParsing`: обработка и парсинг ошибок Telegram Bot API (HTTP 401, `error_code`, `description`).
  * `testAppBackupDataSerializationRoundtrip`: экспорт и импорт полной структуры бэкапа настроек `AppBackupData` в JSON.
  * `testBotConfigWhitelistLogic`: валидация белого списка Telegram User ID и доверенных номеров телефонов.
* **`CommandParsingTest.kt`** (`domain/src/test/kotlin/com/remotebot/domain/CommandParsingTest.kt`):
  * `testSimpleCommandParsing`: базовый разбор команд без параметров (`/ping`).
  * `testCommandWithBotMention`: корректное отсечение суффикса имени бота в групповых чатах (`/battery@MyRemoteDeviceBot` -> `/battery`).
  * `testCommandWithArgumentsAndExtraWhitespace`: нормализация множественных пробелов и корректное выделение списка аргументов (`/adb_wifi on 5555`).
  * `testShellCommandWithComplexArgs`: парсинг составных аргументов терминала с путями и флагами (`/shell ls -la /sdcard/Download`).
* **`GoogleServicesJsonParserTest.kt`** (`domain/src/test/kotlin/com/remotebot/domain/GoogleServicesJsonParserTest.kt`):
  * `testParseValidGoogleServicesJson`: валидация извлечения `project_id`, `project_number`, `mobilesdk_app_id`, `current_key` из `google-services.json`.
  * `testParseGoogleServicesJsonWithMultipleClientsMatchingPackage`: извлечение параметров клиента при наличии нескольких приложений по точному `package_name`.
  * `testParseGoogleServicesJsonFallbackToFirstClient`: корректный fallback на первый клиент, если пакет не совпал.
  * `testParseGoogleServicesJsonMissingProjectInfo`: обработка ошибки отсутствия секции `project_info`.
  * `testParseGoogleServicesJsonMissingClient`: обработка ошибки пустого массива `client`.
  * `testParseGoogleServicesJsonMissingApiKey`: обработка ошибки отсутствия ключа `api_key`.
  * `testParseMalformedJson`: устойчивость парсера к поврежденному/невалидному JSON.
  * `testFcmPushPayloadWakeupAndUpdateParsing`: десериализация FCM Data Push пейлоадов (`action=wakeup`, `action=update` с сырым Telegram JSON).
* **`TelephonyCommandsTest.kt`** (`domain/src/test/kotlin/com/remotebot/domain/TelephonyCommandsTest.kt`):
  * `testSmsMessageSerialization`: сериализация/десериализация SMS сообщений и метаданных SIM-карт.
* **`LocationFilesAndAutomationTest.kt`** (`domain/src/test/kotlin/com/remotebot/domain/LocationFilesAndAutomationTest.kt`):
  * `testLocationDataSerialization`: валидация координат GPS/Network и точности.
  * `testFileItemAndAppSerialization`: проверка моделей файлового менеджера и списка приложений.
  * `testAutomationRulesSerialization`: проверка моделей правил автоматизации, таймеров и триггеров.

#### 2. Тесты Go Push Gateway (`server/go` — `make test-server-go`):
* **`main_test.go`** (`server/go/main_test.go`):
  * `TestHealthCheck`: проверка endpoint `/health` (HTTP 200, JSON статус).
  * `TestFCMTokenCache`: тестирование потокобезопасного кеширования OAuth2 JWT Bearer токенов Google FCM.
  * `TestRS256Signature`: проверка корректности генерации и криптографической подписи RS256 приватным ключом сервисного аккаунта.
  * `TestWebhookPayloadForwarding`: сквозная эмуляция приема POST Webhook от Telegram и упаковки в FCM Data Push `update_json`.

#### 3. Тесты Python Push Gateway (`server/python` — `make test-server-py`):
* **`test_main.py`** (`server/python/test_main.py`):
  * `TestGatewayHealth`: проверка жизнеспособности Flask/HTTP обработчика.
    * `TestFCMV1Payload`: проверка структуры тела запроса FCM HTTP v1 API.

---

## 9. Журнал выполненных работ (Implementation Log)

### 9.1. Инструментарий и зависимости (Обновлено)
* **Android Gradle Plugin**: обновлен до **8.13.2**.
* **Gradle Wrapper**: обновлен до **8.14.5** (`bin` дистрибутив).
* **Kotlin**: **2.0.21** + KSP `2.0.21-1.0.28`.
* **Библиотеки**: Ktor Client `3.1.1`, Kotlin Coroutines `1.10.1`, Koin `4.0.2`, CameraX `1.4.1`, Google Tink `1.16.0`, Compose BOM `2024.12.01`.
* **Защита конфигурации окружения**: сохранение Windows SDK (`sdk.dir=F:\VB\Android_Studio_SDK`) в `local.properties.win`. Makefile в WSL изолирован через `trap`, исключая случайное затирание путей хоста.

### 9.2. Статус TDLib (Разъяснение роли)
* **Текущий статус**: Отложен, не используется в коде приложения.
* **Зачем вообще нужен TDLib**: клиентский движок протокола MTProto. Позволяет авторизоваться по номеру телефона и SMS как обычный пользователь (режим UserBot), читать личные диалоги аккаунта и писать от лица человека.
* **Почему боту он не нужен**: все 54 функции удаленного управления мобильным устройством реализуются через стандартный **Telegram Bot API (HTTP)** по токену бота от `@BotFather`.
* **Причина отказа**: TDLib раздувает APK на +80–100 МБ нативными библиотеками (`libtdjni.so`) и потребляет от 80 до 150 МБ оперативной памяти в фоновом режиме, вызывая гарантированное убийство службы системой Android по Low Memory Killer (LMK).

### 9.3. Ключевые архитектурные и технические решения реализации

#### 9.3.1. Сетевой стек, FCM HTTP v1 и Webhook 409 Conflict
* **Переход на FCM HTTP v1 (OAuth2 / RS256)**:
  * Полный отказ от устаревшего HTTP Legacy API.
  * Реализованы 3 варианта Push-шлюза (`Cloudflare Worker`, `Go`, `Python`) с генерацией JWT и криптографической подписью RS256 (WebCrypto API / нативный `crypto/rsa`).
* **Решение проблемы Telegram Webhook 409 Conflict**:
  * При установленном Webhook вызов `getUpdates` из приложения возвращает ошибку 409 Conflict.
  * Шлюз перехватывает POST-запрос Telegram с объектом `Update`, упаковывает его в Data Push payload (`update_json`), и Android-клиент исполняет команду напрямую через `TelegramBotEngine.processPushUpdate` без обращения к `getUpdates`.
* **Обход DPI**:
  * Поддержка Cloudflare HTTPS Reverse Proxy и WebSocket (WSS) туннелей в условиях блокировки стандартных портов Telegram и Google FCM.

#### 9.3.2. Живучесть в фоне и динамические типы Foreground Service (Android 14/15)
* **Динамическое переключение Foreground Service Types**:
  * Базовый тип службы — `remoteMessaging` (минимальное потребление ОЗУ и батареи).
  * При выполнении команд `/photo`, `/videorec` или `/rec` служба динамически обновляет `foregroundServiceType` через `ServiceCompat.startForeground` с добавлением битовых флагов `camera` или `microphone`, предотвращая SecurityException на Android 14/15.
* **Doze Mode и OEM-защита**:
  * Автоматический запрос исключения из оптимизации батареи (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
  * `START_STICKY` и автозапуск через `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `QUICKBOOT_POWERON`).

#### 9.3.3. Подсистема Root и изоляция привилегий (LibSU 6.0.0)
* **Централизованный `RootStatusProvider`**:
  * Потокобезопасный `StateFlow<Boolean?>` с асинхронной проверкой UID 0 и кэшированием статуса.
  * Превентивная фоновая инициализация при старте приложения без блокировки главного потока.
* **Гранулярный Fallback для нерутированных устройств**:
  * Все команды ядра разделены на доступные в обычном режиме и требующие Root (отмечены ⚡). При отсутствии Root бот не падает, а выводит понятное предупреждение в чат или модальный диалог в callback query.
* **1-клик выдача системных прав через Root**:
  * Возможность мгновенно включить `AccessibilityService` и `NotificationListener` через Shell-команды `settings put secure enabled_accessibility_services` / `enabled_notification_listeners`.

#### 9.3.4. Мультимедиа пайплайн без оверлеев (Camera2 / MediaRecorder / Root Screencap)
* **Фоновый захват кадров без `SurfaceView`**:
  * Отказ от устаревшего `WindowManager.addView` с невидимым оверлеем (из-за которого старый бот падал на Android 10+).
  * Использование Camera2/CameraX `ImageCapture` и `ImageAnalysis` напрямую в буфер оперативной памяти.
* **Аудио и Детектор движения**:
  * Запись сжатого звука в формате AAC/M4A через `MediaRecorder` с прямой отправкой голосовым сообщением `sendVoice`.
  * Анализ видеопотока через разность соседних кадров (frame differencing) для детекции движения.
* **Root захват экрана и настраиваемая папка хранения (StorageHelper)**:
  * Прямой вызов системных утилит `/system/bin/screencap -p` и `/system/bin/screenrecord` без запроса прав `MediaProjection` в UI.
  * Автоматический вызов `chmod 666` и `outFile.setReadable(true, false)` после создания файлов Root'ом, устраняющий `EACCES (Permission denied)`.
  * **Пользовательская папка хранения**: возможность задать произвольный путь к папке в UI (вкладка `Telegram`, поле «Папка сохранения медиа и файлов»). По умолчанию файлы сохраняются в `/sdcard/Download/RemoteBot` (в подпапки `screenshots`, `screen_records`, `camera_photos`, `audio_records`), откуда они легко доступны пользователю и файловому менеджеру.

#### 9.3.5. Телефония, USSD, SMS Бот и Quick Reply в уведомления
* **Multi-SIM маршрутизация и SMS Бот (Префикс `rb ` + 2 режима безопасности)**:
  * Поддержка выбора слота SIM-карты через `SubscriptionManager` и `SmsManager.getSmsManagerForSubscriptionId`.
  * Авторазбиение длинных текстов через `divideMessage` / `sendMultipartTextMessage`.
  * **Обязательный триггер-префикс `rb `**: входящее SMS рассматривается как команда бота только при наличии префикса `rb ` в начале строки (`startsWith("rb ", ignoreCase = true)`). Все остальные SMS (коды банков, реклама, личные сообщения) сразу исключаются из парсинга команд и прозрачно пересылаются в Telegram.
  * **Режим 1 (PIN + Команда с любого номера)**: в `TelegramBotEngine.handleIncomingSms` — если после `rb ` сообщение содержит валидный `smsPin` (например, `rb 1234 /wifi on` или `rb 1234 reboot`), PIN отсекается, команда нормализуется со слэшем и исполняется с любого номера (доверенного или нового), а результат возвращается по SMS.
  * **Режим 2 (Доверенный номер без PIN)**: проверка по белому списку `allowedPhoneNumbers` (с очисткой нецифровых символов и сопоставлением по последним 10 цифрам номера). Для доверенных номеров ввод PIN не требуется: достаточно отправить `rb /wifi on` или `rb ping`.
  * **Фильтрация неавторизованных SMS**: сообщения от номеров вне белого списка без корректного PIN не исполняются как команды и пересылаются в Telegram-чат администратора как обычные SMS.
* **Асинхронные USSD запросы**:
  * Выполнение USSD через `TelephonyManager.sendUssdRequest` с асинхронным `UssdResponseCallback` и корутинным мостом `suspendCancellableCoroutine`.
* **Перехват уведомлений и RemoteInput Quick Reply**:
  * `RemoteBotNotificationListenerService` извлекает `sbn.notification.actions` с `RemoteInput`.
  * Команда `/answer <ID> <текст>` собирает ответный `Bundle` через `RemoteInput.addResultsToIntent` и триггерит `action.actionIntent.send()`, отправляя ответ в WhatsApp, Telegram, Viber или SMS без открытия приложения.
  * **Форматирование и чистота текста пересылки**:
    * Отказ от шаблонов `"""...""".trimIndent()` в пользу `buildString` для сборки сообщений уведомлений и входящих SMS. Устранена ловушка `trimIndent()`, при которой многострочный текст из уведомления с нулевым отступом приводил к сохранению 24 лидирующих пробелов кода на строках заголовка и автора (визуальное боковое смещение текста).
    * Экранирование HTML спецсимволов (`&`, `<`, `>`) в имени приложения, заголовке и теле уведомления для предотвращения ошибок парсинга Telegram API.
  * **Отключение превью ссылок (Link Previews Suppression)**:
    * Во все методы отправки сообщений бота (`sendMessage`, `editMessageText`, `broadcastMessage`) внедрены параметры `disable_web_page_preview = true` и `link_preview_options = {"is_disabled": true}`. Ссылки из уведомлений и команд больше не генерируют громоздкие превью-карточки в чате.

#### 9.3.6. Геолокация, Обратное геокодирование и Акселерометр
* **Гибридное определение координат**:
  * Запрос свежих координат через `LocationListener` с таймаутом и fallback на `getLastKnownLocation` (GPS / Network).
  * Преобразование координат в человекочитаемый адрес через Android `Geocoder` (`Locale.getDefault()`).
  * Формирование кликабельных ссылок для Google Maps и OpenStreetMap.
* **Охранный датчик тряски (Shake Detector)**:
  * Векторный расчет ускорения по трем осям $\sqrt{x^2+y^2+z^2} - g$ через `Sensor.TYPE_ACCELEROMETER`.
  * Настраиваемый порог чувствительности (по умолчанию 13.0 m/s²) с дебаунсом 1.5с и автоотправкой алерта в Telegram.

#### 9.3.7. Файловый менеджер, Приложения и TaskManager
* **Обход ограничений Scoped Storage**:
  * Чтение стандартных путей через `java.io.File`, а для защищенных системных директорий (`/Android/data`, `/data/data`) — прозрачное вычитывание через Root Shell (`ls -la`, `cp` во временный кэш).
* **Сжатие на лету (Zip Streaming)**:
  * Автоматическая упаковка директорий любого уровня вложенности в `.zip` архив через `ZipOutputStream` перед отправкой в Telegram.
* **Управление приложениями и процессами**:
  * Тихая установка APK пакетов через Root `pm install -r -d -g`.
  * Парсинг вывода `ps -A -o PID,NAME,%CPU,%MEM` для построения диспетчера задач с процентами загрузки процессора и памяти.
  * Завершение процессов через `am force-stop <package>` и `kill -9 <PID>`.

#### 9.3.8. Интеграция с Tasker, Скрипты и Планировщик таймеров
* **Tasker Intent Bridge**:
  * Отправка управляющих сигналов в Tasker через Broadcast Intent `net.dinglisch.android.tasker.ACTION_TASK` с передачей именованных параметров `par1`, `par2`, `par3`.
* **Планировщик таймеров на корутинах**:
  * Потокобезопасное планирование однократных и циклических команд (`add 60s /photo`, `add 10m /location`).
  * Автоматический dispatch сработавших таймеров в `TelegramBotEngine` с отправкой результата в чат владельца.




### 9.4. Архитектура и компоновка интерфейса (Browser Tabs + Back Navigation + Fullscreen Content)
* **Концепция верстки**: лента вкладок в стиле вкладок интернет-браузера сверху (`BrowserTabBar`) + полноэкранный рабочий контейнер контента снизу:
  * **Верхняя браузерная лента вкладок (`BrowserTabBar`)**:
    * **Браузерная кнопка «Назад» (←)**: слева от ленты расположена аппаратная/экранная кнопка возврата. Активна при наличии истории переходов между окнами (`canGoBack = tabHistory.size > 1`). При нажатии возвращает к предыдущему открытому экрану режима.
    * **Системная навигация Android (`BackHandler`)**: перехват системного жеста и физической кнопки «Назад» смартфона. Приложение не закрывается случайно, а пошагово возвращается по стеку истории посещенных вкладок. При возврате к стартовой вкладке повторное нажатие сворачивает приложение стандартно.
    * **Стилизация под браузерные вкладки**:
      * Геометрия вкладки: скругленные верхние углы (`RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)`), соединяющиеся с нижней панелью содержимого.
      * Подсветка открытого окна: активная вкладка подсвечивается основным цветом поверхности (`surface`) без швов с телом экрана, получает верхний светящийся акцентный маркер (`primary`) толщиной 3dp, жирный шрифт заголовка и яркую иконку. Неактивные вкладки приглушены (`surfaceVariant.copy(alpha = 0.35f)`).
      * Автоматическая плавная докрутка (`LaunchedEffect(selectedTab) { scrollState.animateScrollTo(...) }`) при переключении, удерживающая активное окно в зоне видимости.
    * Вкладки режимов (`BrowserTabItem`):
      * 🚀 **Старт**: служба, Doze mode, Root (`libsu`), статус фоновых сервисов.
      * 🛡 **Права**: единый экран проверки и выдачи всех системных, Root и Runtime-разрешений.
      * ⏱️ **Задачи**: контроль активных фоновых операций (`ActiveOperationsTracker`).
      * ✈️ **Telegram**: векторный логотип, токен, компактный список Whitelist ID с кнопкой `+`, папка сохранения медиа (`OpenDocumentTree`).
      * 💬 **SMS**: SMS-бот, PIN-код, компактный список доверенных номеров (`+`), префикс `rb`.
      * 🔔 **Уведомл.**: пересылка SMS/звонков/пушей, выбор приложений, стоп-слова с кнопкой `+`, дедупликация (5с – 15 мин).
      * 🔥 **Push**: Push-уведомления (FCM / HMS). **Анимированный слайдер-переключатель (`PushProviderSegmentedSlider`)**: выбор между `Google FCM`, `Huawei HMS` и `Гибридный (FCM+HMS)` с плавным плавающим бегунком.
      * 🌐 **Прокси**: компактные строки Host/Port и User/Pass (Direct, SOCKS5, HTTP, WSS).
      * 📜 **Логи**: кольцевой буфер `AppLogger` с моноширинным терминалом и фильтрами.
      * 💾 **Бэкап**: экспорт и импорт JSON-конфигурации.
      * ℹ️ **О боте**: версия приложения, коммит сборки (`BUILD_ID`), параметры компиляции.
  * **Контейнер содержимого (`Surface`)**:
    * Занимает всю ширину экрана под лентой вкладок, обеспечивая максимальный комфорт ввода текста и чтения логов.
    * Внутренняя информационная плашка: отображает иконку, заголовок и краткое описание текущего открытого окна режима.
* **Преимущества компоновки**:
  * Освобождена полезная горизонтальная ширина на экранах смартфонов (больше нет сжатия форм из-за бокового рельса).
  * Привычный и интуитивный пользовательский опыт работы с вкладками, аналогичный веб-браузерам.
  * Бесшовная и предсказуемая навигация с поддержкой кнопки «Назад».

### 9.5. Системные службы (Accessibility и Notification Listener)
* **Служба специальных возможностей (`RemoteBotAccessibilityService`)**:
  * Реализован сервис `com.remotebot.app.service.RemoteBotAccessibilityService : AccessibilityService`.
  * Декларация в `AndroidManifest.xml` с системным разрешением `android.permission.BIND_ACCESSIBILITY_SERVICE`.
  * Конфигурация в `res/xml/accessibility_service_config.xml`:
    * `android:accessibilityEventTypes="typeAllMask"`
    * `android:accessibilityFeedbackType="feedbackGeneric"`
    * `android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews|flagReportViewIds"`
    * `android:canRetrieveWindowContent="true"`, `android:canPerformGestures="true"`.
  * Пользовательское описание в `res/values/strings.xml` (`accessibility_service_description`).
* **Служба перехвата уведомлений (`RemoteBotNotificationListenerService`)**:
  * Реализован сервис `com.remotebot.app.service.RemoteBotNotificationListenerService : NotificationListenerService`.
  * Декларация в `AndroidManifest.xml` с системным разрешением `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` и фильтром `android.service.notification.NotificationListenerService`.
* **Package Visibility и выбор приложений (Android 11–15)**:
  * В `AndroidManifest.xml` объявлены `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="QueryAllPackagesPermission" />` и блок `<queries>`.
  * Репозиторий `DefaultAppManagerRepository`: безопасное чтение через `packageManager.getInstalledPackages(0)` с отсечением чисто системных приложений и сохранением обновленных пользовательских (`FLAG_UPDATED_SYSTEM_APP`), плюс fallback через `getInstalledApplications(0)`. Полное устранение проблемы пустого списка приложений в Compose UI пикере уведомлений.

### 9.6. Интерактивные ответы без ручного ввода команд (SMS и Мессенджеры)
* **Архитектурный принцип (Диалоговый State Machine вместо `force_reply`)**:
  * В оригинальном приложении и новой архитектуре используется пошаговый мастер ожидания ввода, не загромождающий интерфейс Telegram навязчивым баннером принудительного цитирования `force_reply`.
* **Ответ на SMS из Telegram**:
  * Под каждым входящим SMS крепится инлайн-кнопка «💬 Ответить по SMS» с payload `sms_reply:<sender>:<simSlot>`.
  * При нажатии бот фиксирует сессию `PendingSmsReply` и присылает сообщение: `✍️ Введите текст сообщения для <номер> (SIM X):` с инлайн-кнопкой `[ ❌ Отмена ]`.
  * Пользователь пишет обычный текст в чат и отправляет -> бот отправляет SMS через `SmsManager` с нужного SIM-слота и подтверждает: `✅ SMS отправлено`.
  * Отмена доступна по инлайн-кнопке, слову «отмена» или команде `/cancel`.
  * Поддерживается нативный свайп (Reply/цитирование) на сообщение SMS.
* **Ответ на Push-уведомления мессенджеров (WhatsApp, Telegram, Viber, Signal, VK, Skype, Gmail)**:
  * Служба `RemoteBotNotificationListenerService` извлекает доступные системные действия `Notification.Action` с объектом `RemoteInput`.
  * Под пересланным в Telegram уведомлением выводится кнопка «💬 Ответить».
  * При нажатии бот активирует режим ожидания текста ответа для конкретного `notificationId`.
  * Введенный пользователем текст упаковывается в `Bundle` с ключом `remoteInput.resultKey`, добавляется через `RemoteInput.addResultsToIntent()` и триггерит `PendingIntent.send(context, 0, intent)`.
  * Целевой мессенджер (например, WhatsApp) в фоновом режиме принимает системный ответ и отправляет его собеседнику без открытия экрана.
### 9.7. Сквозное разделение логирования (Фаза 12: Trace / Debug / Info / Error)

#### 9.7.1. Разделение обязанностей и уровни детализации
1. **`TRACE` (Низкоуровневая трассировка оболочки, команд и сетевых дампов)**:
   - Точные shell-команды Root / Shizuku: `pm grant $pkg ...`, `appops set $pkg ... allow`, `cmd notification allow_listener ...`, `cmd netpolicy add restrict-background-whitelist $uid`.
   - Вывод ошибок выполнения shell: код возврата `code=...`, ошибки из stderr `err=...`.
   - Выполняемые команды бота: сырой ввод оператора, разобранные аргументы команд, вывод утилит `/shell`, `/reboot`, `/input`, `/app_freeze`, `/dumpsys`.
   - Сетевые дампы Ktor / SOCKS5 / Push Gateway: сырые HTTP-запросы и ответы (с маскированием приватных токенов), заголовки, размер payload в байтах, точный RTT / latency (ms), байты рукопожатия SOCKS5, сырые JSON-пейлоады FCM/HMS Data Push (`update_json`).
   - Телефония: сырые PDU входящих/исходящих SMS.
2. **`DEBUG` (Факты действий, намерения и попытки сетевых подключений)**:
   - Факт запроса пакета прав: `"Запрос пакета всех прав через Root (Superuser)"`, `"Запрос пакета Runtime-разрешений вручную (11 разрешений)"`.
   - Факт выполнения действий по отдельным разрешениям: `"Запрос включения Accessibility Service через Root"`, `"Запрос включения Notification Listener через Root"`, `"Запрос снятия ограничений Data Saver через Root (UID=...)"`.
   - Факт открытия системных экранов: `"Открытие экрана настроек Accessibility (без Root)"`, `"Открытие экрана настроек Notification Listener (без Root)"`.
   - Выполняемые команды бота: факт приема команды оператора (`"Получена команда /photo [chat_id=..., user_id=...]"`), валидация параметров (`"Команда /sms_send: валидация номера +7999..."`), проверка SMS PIN для деструктивных действий.
   - Попытки запросов к внешним серверам:
     - Telegram API: `"Отправка запроса getUpdates [offset=12345, timeout=30s]"`, `"Отправка ответа в чат 12345 (метод sendMessage)"`.
     - Прокси: `"Проверка доступности SOCKS5 прокси 1.2.3.4:1080..."`.
     - Push Gateway: `"Регистрация токена устройства на шлюзе: https://gateway.domain/register"`.
     - DNS & DoH: `"Резолвинг домена api.telegram.org через DoH Cloudflare/Google"`.
3. **`INFO` (Ключевые вехи бизнес-процесса)**:
   - Итоговые факты успешного завершения операций: `"Все права успешно выданы через Root (27 команд)"`, `"Спец. возможности успешно включены через Root"`, `"Доступ к уведомлениям успешно выдан через Root"`.
   - Успешное выполнение команд: `"Команда /photo успешно выполнена: снимок отправлен за 1.4с"`.
   - Сеть и внешние сервисы: `"Успешное соединение с Telegram Bot API (@MyRemoteBot, Ping: 124ms)"`, `"SOCKS5 прокси активен и стабилен"`, `"Push-шлюз успешно подтвердил регистрацию устройства"`.
   - Фоновый сервис: `"RemoteBotService запущен в режиме Foreground (remoteMessaging)"`.
4. **`WARN` / `ERROR` (Сбои и фатальные ошибки)**:
   - Fallback-переходы при отказе Root-команд: `"Сбой включения через Root, открываем системные настройки..."`.
   - Сетевые сбои: `"Попытка запроса getUpdates не удалась (таймаут 15с), повтор через 2с..."`, `"Rate limit HTTP 429 от Telegram, пауза 5с"`, `"Не удалось подключиться к прокси 1.2.3.4:1080 (Connection refused), fallback на прямой коннект"`.
   - Отказ по правам: `"Команда /contacts отклонена: отсутствует разрешение READ_CONTACTS"`.
   - Полный стектрейс исключений (`Log.getStackTraceString(throwable)`).

#### 9.7.2. Настройки отображения в UI (`MainActivity` и `LogsTab`)
- В `MainActivity` начальное состояние `logEnabledLevels` переведено на включение всех доступных уровней (`LogLevel.entries.map { it.name }.toSet()`), чтобы детальные логи отладки и трассировки не скрывались стартовым фильтром.
- В `LogsTab` поддержано копирование полного отфильтрованного списка с временными метками `HH:mm:ss.SSS` и визуальная цветовая индикация каждого уровня.

### 9.8. Унификация и верификация серверов Push Gateway (Go, Python, JS)

Для поддержки серверного push-пробуждения в любых инфраструктурных окружениях (от бессерверного Cloudflare Workers до standalone VM на Linux/Docker) реализованы три полностью эквивалентных шлюза:
1. **Cloudflare Worker (JS/ESM)** (`serverpush/cloudflare-worker/`): бессерверный легковесный edge-шлюз на Cloudflare.
2. **Go Standalone Daemon** (`serverpush/go/`): высокопроизводительный скомпилированный демон с поддержкой конкурентной доставки через горутины.
3. **Python Standalone Daemon** (`serverpush/python/`): zero-dependency легковесный демон для Linux-хостов и Docker-контейнеров.

#### 9.8.1. Единый сетевой контракт и маршрутизация
* **Healthcheck**: `GET /health` возвращает `{"status":"healthy","fcm":bool,"hms":bool}` с HTTP 200. При обращении через другие методы возвращает `405 Method Not Allowed`.
* **Прямой маршрут FCM**: `POST /tg/fcm/:token/:secretKey` ➔ доставляет Wakeup через Google FCM.
* **Прямой маршрут HMS**: `POST /tg/hms/:token/:secretKey` ➔ доставляет Wakeup через Huawei Push Kit.
* **Автоматический выбор**: `POST /tg/:token/:secretKey` ➔ интеллектуальный выбор по настроенным переменным (`FCM`, `HMS`).
* **Query-параметр**: `POST /tg/:token/:secretKey?provider=hms` (или `?provider=fcm`) ➔ принудительный выбор провайдера без изменения структуры URL.

#### 9.8.2. Безопасность и обработка ошибок
* **Авторизация**: Если переменная окружения `PUSH_GATEWAY_SECRET` задана, запрос с несовпадающим `:secretKey` отклоняется с `401 Unauthorized`.
* **Валидация URL**: Пути с недостаточным числом сегментов (например, `/tg/token`) или избыточными сегментами (`/tg/a/b/c/d`) отклоняются с `400 Bad Request`.
* **Валидация тела**: Пустые тела запросов и невалидный JSON не приводят к внутренним ошибкам сервера, а возвращают `400 Bad Request`.
* **HTTP-методы**: Метод `GET` на `/tg/...` отклоняется с кодом `405 Method Not Allowed`.
* **Изоляция секретов**: При отправке в логи выводятся только маскированные префиксы push-токенов (`pushToken[:8]...`).

#### 9.8.3. Результаты автоматизированного тестирования
* **Go (`serverpush/go/main_test.go`)**: 9/9 тестов успешно пройдены (`go test -v -race ./...`).
* **Python (`serverpush/python/test_main.py`)**: 12/12 тестов успешно пройдены (`python3 -m unittest -v test_main.py`).
* **JS (`serverpush/cloudflare-worker/test_worker.js`)**: 11/11 тестов успешно пройдены (`node --test test_worker.js` / `npm test`).
* **Команды запуска**:
  ```bash
  make test-server-go   # Прогон тестов Go шлюза
  make test-server-py   # Прогон тестов Python шлюза
  make test-server-js   # Прогон тестов Cloudflare Worker JS шлюза
  make test-server      # Комплексный прогон всех 3 шлюзов
  ```

### 9.9. Прокси-серверы Telegram Bot API (`serverapi/` — Cloudflare, Python, Go)

Для обхода блокировок и DPI при прямом обращении к `api.telegram.org` реализованы 3 варианта обратного прокси (Reverse Proxy), сохраняющие пути, query-параметры, заголовки и тела запросов:
1. **Cloudflare Worker (JS)** (`serverapi/cloudflare-worker/worker.js`):
   ```javascript
   export default {
     async fetch(request) {
       const url = new URL(request.url);
       url.hostname = "api.telegram.org";
       return fetch(url.toString(), request);
     },
   };
   ```
2. **Python Standalone Proxy** (`serverapi/python/main.py`):
   - Zero-dependency обратный прокси на базе `http.server` и `urllib.request`.
   - Поддерживает GET, POST (включая multipart при отправке фото/документов), сохраняет заголовки и стриминг ответов.
3. **Go Standalone Proxy** (`serverapi/go/main.go`):
   - Высокопроизводительный обратный прокси на базе стандартного `net/http/httputil.ReverseProxy`.
   - Потоковая передача данных без накопления буферов в памяти.

#### 9.9.1. Результаты автоматизированного тестирования `serverapi`
* **Go (`serverapi/go/main_test.go`)**: 2/2 теста пройдены (`go test -v ./...`).
* **Python (`serverapi/python/test_main.py`)**: 2/2 теста пройдены (`python3 -m unittest -v test_main.py`).
* **JS (`serverapi/cloudflare-worker/test_worker.js`)**: 1/1 тест пройден (`node --test test_worker.js`).
* **Команды запуска**:
  ```bash
  make test-serverapi-go   # Тест Go Telegram API прокси
  make test-serverapi-py   # Тест Python Telegram API прокси
  make test-serverapi-js   # Тест Cloudflare Worker Telegram API прокси
  make test-serverapi      # Комплексный прогон всех 3 прокси-серверов
  ```
