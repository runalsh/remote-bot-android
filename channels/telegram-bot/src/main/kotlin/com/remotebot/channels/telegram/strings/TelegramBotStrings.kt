package com.remotebot.channels.telegram.strings

import com.remotebot.domain.model.BotLanguage

interface BotStrings {
    fun startMenuTitle(version: String, rootStatus: String): String
    fun startMenuSubtitle(): String
    fun btnBasic(): String
    fun btnMedia(): String
    fun btnComm(): String
    fun btnGeoSec(): String
    fun btnFiles(): String
    fun btnSystem(): String
    fun btnAutomation(): String
    fun btnAllCommands(): String
    fun btnStatus(): String
    fun btnBack(): String
    fun btnToggles(): String
    fun aboutTitle(version: String, rootStatus: String): String
    fun permissionsTitle(granted: Int, total: Int): String
    fun filterLabel(filter: String): String
    fun groupCriticalTitle(): String
    fun groupConfidentialTitle(): String
    fun groupStandardTitle(): String
    fun statusGranted(): String
    fun statusDenied(): String
    fun noPermissionsFound(): String
    fun permissionsUsageHint(): String
    fun statusTitle(): String
    fun statusServiceRunning(): String
    fun statusRoot(granted: Boolean): String
    fun statusConnectionMode(mode: String): String
    fun statusTelegramApiTitle(): String
    fun statusActiveNode(status: String): String
    fun statusStrategy(strategy: String): String
    fun statusEndpointsPool(): String
    fun incomingSmsTitle(from: String, sim: String): String
    fun incomingCallTitle(from: String, sim: String): String
    fun missedCallTitle(from: String, sim: String): String
    fun batteryChargingTitle(level: Int): String
    fun batteryLowTitle(level: Int): String
    fun batteryLevel(level: Int): String
    fun actionApproved(): String
    fun actionRejected(): String
    fun commandNotFound(cmd: String): String

    // Category Help Menus
    fun categoryBase(): String
    fun categoryMedia(): String
    fun categoryComm(): String
    fun categoryGeoSec(): String
    fun categoryFiles(): String
    fun categorySystem(): String
    fun categoryAuto(): String
    fun fullHelp(version: String, rootText: String): String
}

object BotStringsEn : BotStrings {
    override fun startMenuTitle(version: String, rootStatus: String): String =
        "🤖 <b>Remote Bot Android</b> ($version)\n👑 <b>Root status:</b> $rootStatus\n📡 <b>Service:</b> 🟢 Active"
    override fun startMenuSubtitle(): String = "Select a command category:"
    override fun btnBasic(): String = "⚙️ Basic"
    override fun btnMedia(): String = "📷 Media"
    override fun btnComm(): String = "📞 Calls & SMS"
    override fun btnGeoSec(): String = "📍 Location"
    override fun btnFiles(): String = "📁 Files & Apps"
    override fun btnSystem(): String = "⚡ System & Root"
    override fun btnAutomation(): String = "🤖 Automation"
    override fun btnAllCommands(): String = "📋 All Commands"
    override fun btnStatus(): String = "📊 Status"
    override fun btnBack(): String = "🔙 Back to Menu"
    override fun btnToggles(): String = "🎛️ Toggles"
    override fun aboutTitle(version: String, rootStatus: String): String =
        """
        🤖 <b>Remote Bot Android</b>
        
        • <b>Version:</b> $version
        • <b>Build:</b> Clean Architecture (SDK 35 / Android 15)
        • <b>Root status:</b> $rootStatus
        
        🐙 <b>GitHub:</b> <a href="https://github.com/runalsh/remote-bot-android">github.com/runalsh/remote-bot-android</a>
        ✈️ <b>Author Telegram:</b> <a href="https://t.me/runalsh">@runalsh</a>
        
        ⚖️ License: Apache License 2.0
        """.trimIndent()

    override fun permissionsTitle(granted: Int, total: Int): String =
        "🛡️ <b>Android Permissions Status</b> ($granted/$total)"
    override fun filterLabel(filter: String): String = "Filter: <code>$filter</code>"
    override fun groupCriticalTitle(): String = "🔴 <b>Critical (System/Network/Background):</b>"
    override fun groupConfidentialTitle(): String = "🟡 <b>Confidential (Camera/Microphone/SMS):</b>"
    override fun groupStandardTitle(): String = "🟢 <b>Standard (Base features):</b>"
    override fun statusGranted(): String = "Granted"
    override fun statusDenied(): String = "Denied"
    override fun noPermissionsFound(): String = "No permissions found for filter."
    override fun permissionsUsageHint(): String = "💡 <i>Usage: /permissions [critical|confidential|standard|denied]</i>"

    override fun statusTitle(): String = "📊 <b>Remote Bot Status Summary</b>"
    override fun statusServiceRunning(): String = "🤖 <b>Service state:</b> 🟢 Active"
    override fun statusRoot(granted: Boolean): String =
        "👑 <b>Root (Superuser):</b> " + if (granted) "✅ Granted (UID 0)" else "❌ Denied"
    override fun statusConnectionMode(mode: String): String = "📡 <b>Connection mode:</b> <code>$mode</code>"
    override fun statusTelegramApiTitle(): String = "🌐 <b>Telegram Bot API:</b>"
    override fun statusActiveNode(status: String): String = "• <b>Active node:</b> $status"
    override fun statusStrategy(strategy: String): String = "• <b>Selection strategy:</b> <code>$strategy</code>"
    override fun statusEndpointsPool(): String = "• <b>Endpoints pool:</b>"

    override fun incomingSmsTitle(from: String, sim: String): String =
        "📩 <b>Incoming SMS</b> (SIM: $sim)\n<b>From:</b> <code>$from</code>"
    override fun incomingCallTitle(from: String, sim: String): String =
        "📞 <b>Incoming Call</b> (SIM: $sim)\n<b>From:</b> <code>$from</code>"
    override fun missedCallTitle(from: String, sim: String): String =
        "🔴 <b>Missed Call</b> (SIM: $sim)\n<b>From:</b> <code>$from</code>"
    override fun batteryChargingTitle(level: Int): String = "⚡ <b>Charger Connected</b> ($level%)"
    override fun batteryLowTitle(level: Int): String = "🪫 <b>Low Battery</b> ($level%)"
    override fun batteryLevel(level: Int): String = "Battery level: $level%"
    override fun actionApproved(): String = "✅ Approved"
    override fun actionRejected(): String = "❌ Rejected"
    override fun commandNotFound(cmd: String): String = "Unknown command: $cmd"

    override fun categoryBase(): String =
        """
        ⚙️ <b>Basic Commands & Diagnostics:</b>
        • /status — overall connection status, Telegram API ping, FCM/HMS
        • /permissions [filter] — permission audit (critical/confidential/standard/denied)
        • /ping — response latency test
        • /echo &lt;text&gt; — echo incoming command test
        • /chat_id — display current chat Chat ID and User ID
        • /about — bot build version, repo and contacts
        • /uptime — service process and Android system uptime
        """.trimIndent()

    override fun categoryMedia(): String =
        """
        📷 <b>Camera, Audio & Screen:</b>
        • /photo [front|back] — snapshot from front or rear camera
        • /videorec [front|back] [seconds] — covert video recording
        • /gallery [N] — send latest N media files from gallery
        • /rec [seconds] — covert voice audio recording from microphone
        • /rec_file [seconds] — audio recording saved as .wav/.m4a
        • /tts &lt;text&gt; — speech synthesizer playback via speaker
        • /player [play|pause|next|prev|stop] — media playback control
        • /volume [0-100|stream] [value] — volume control for audio streams
        • /ring_mode [normal|silent|vibrate] — ringer mode switch
        • /device_search — loud alarm siren and vibration to find lost phone
        • /screenshot — instant screenshot (MediaProjection / Root)
        • /screen_rec [seconds] — record screen to video file (Root / Service)
        """.trimIndent()

    override fun categoryComm(): String =
        """
        📞 <b>Calls, SMS & Notifications:</b>
        • /sms &lt;number&gt; &lt;text&gt; — send SMS with SIM selection
        • /sms_inbox [N] — view latest incoming SMS messages
        • /call &lt;number&gt; — initiate voice phone call
        • /callshistory [N] — call logs (incoming, outgoing, missed)
        • /ussd &lt;code&gt; — execute USSD balance or service request (Android 8.0+)
        • /notify [list|clear] — view active status bar notifications
        • /notify_send [id] — forward notification to Telegram chat
        • /fake_notify &lt;title&gt; | &lt;text&gt; — generate mock notification in status bar
        • /answer &lt;notif_id&gt; &lt;text&gt; — quick reply (RemoteInput) to messenger
        • /notify_clear [id|all] — dismiss notification from status bar
        • /notify_rule [add|del|list] — regex filter rules for notifications
        """.trimIndent()

    override fun categoryGeoSec(): String =
        """
        📍 <b>Location & Security:</b>
        • /location — current GPS/Network coordinates with Google Maps link
        • /track [start|stop|status] — continuous background GPS route tracking
        • /shake_detector [on|off|sens] — accelerometer motion alarm
        • /detectmotion [on|off|front|back] — camera motion detector with auto-photo
        • /sound_detector [on|off|sens] — sound/voice detector
        """.trimIndent()

    override fun categoryFiles(): String =
        """
        📁 <b>Files & Applications:</b>
        • /get_file &lt;path&gt; — download any file from Android filesystem
        • /download &lt;url&gt; [path] — remote download from internet to device
        • /ls &lt;path&gt; — list directory contents
        • /ls_interactive &lt;path&gt; — interactive file explorer with inline buttons
        • /apps [system|user|all] — list installed apps and packages
        • /app_launch &lt;package&gt; — launch selected app on screen
        • /install — remote install APK (silent install with Root)
        • /taskmanager (alias: /top) — running processes and RAM usage
        • /kill &lt;package|pid&gt; — force kill application (Root)
        """.trimIndent()

    override fun categorySystem(): String =
        """
        ⚡ <b>System & Root:</b>
        • /toggles (aliases: /panel, /quick_settings) — interactive toggle buttons
        • /system_info — device info summary, RAM, ROM, CPU, Root
        • /uptime — service and system uptime
        • /battery — battery status, level, temperature, voltage
        • /network — network interfaces, IP (IPv4/IPv6), DNS, MAC, MTU
        • /wifi [on|off|toggle] — Wi-Fi status and toggle
        • /bluetooth [on|off|toggle] — Bluetooth status and toggle
        • /flashlight [on|off|toggle] — flashlight (camera LED)
        • /airplane [on|off|toggle] — airplane mode ⚡
        • /hotspot [on|off|toggle] (alias: /tethering) — Wi-Fi Hotspot ⚡
        • /mobile_data [on|off|toggle] (alias: /data) — cellular mobile data ⚡
        • /gps [on|off|toggle] (alias: /location_mode) — GPS toggle ⚡
        • /powersave [on|off|toggle] (alias: /battery_saver) — battery saver mode ⚡
        • /auto_brightness [on|off|toggle] — auto brightness toggle
        • /clipboard [text|clear] — clipboard read and write
        • /sync [on|off|toggle] — account auto-sync toggle
        • /adb [on|off|toggle] (alias: /usb_debugging) — USB debugging (ADB) ⚡
        • /adb_wifi [on|off|toggle|port] — wireless ADB debugging ⚡ (port 5555)
        • /shell &lt;cmd&gt; (alias: /sh) — Linux terminal shell (Root/sh) ⚡
        • /reboot — reboot device ⚡ (10s delay)
        • /shutdown (alias: /poweroff) — power off device ⚡ (10s delay)
        • /recovery — reboot into Recovery ⚡ (10s delay)
        • /fastboot (alias: /bootloader) — reboot into Bootloader ⚡ (10s delay)

        <i>⚡ = requires Root (Superuser) permissions</i>
        """.trimIndent()

    override fun categoryAuto(): String =
        """
        🤖 <b>Automation, Tasker & Network:</b>
        • /tasker &lt;TaskName&gt; [par1] [par2] — launch Tasker tasks
        • /script &lt;code&gt; — execute system shell script
        • /timers [list|add|del] — scheduled & recurring command scheduler
        • /proxy — view proxy status and help
        • /proxy [socks5|http|wss|off] — quick switch or disable proxy
        • /active — active background operations
        • /stop_all — emergency cancel of all operations
        • /log [N] [level] — event log (default 20, max 100, DEBUG/INFO/WARN/ERROR)
        • /backup — export config backup to JSON file
        • /restore_backup [json|file] — restore config from text or file
        • /unsent_files — unsent files queue
        • /bot_status — Telegram and SMS channel status
        • /tg_bot [on|off|toggle] — enable/disable Telegram bot
        • /sms_bot [on|off|toggle] — SMS bot status and switch
        • /bot_toggle &lt;tg|sms&gt; [on|off] — universal communication channel toggle
        • /add_user [id] / /del_user &lt;id&gt; / /list_users — Telegram User ID whitelist
        • /add_phone [num] / /del_phone &lt;num&gt; / /list_phones — trusted SMS numbers without PIN
        • /add_forward [num] / /del_forward &lt;num&gt; / /list_forwards — forward numbers
        • /tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off] — event forwarding
        • /events [on|off|notif|sms|calls|system|list|id] (alias: /subs) — subscriptions menu
        • /whitelist_user [add|del|clear|list|&lt;id&gt;] (alias: /user) — user whitelist management
        • /whitelist_phone [add|del|clear|list|&lt;num&gt;] (alias: /phone) — phone whitelist management
        • /sms_forward [add|del|clear|list|on|off|missed|push|translit|&lt;num&gt;] — forwarding management
        """.trimIndent()

    override fun fullHelp(version: String, rootText: String): String =
        """
        🤖 <b>Remote Bot Android</b> ($version)
        👑 Root status: $rootText

        <b>${categoryBase()}</b>

        <b>${categoryMedia()}</b>

        <b>${categoryComm()}</b>

        <b>${categoryGeoSec()}</b>

        <b>${categoryFiles()}</b>

        <b>${categorySystem()}</b>

        <b>${categoryAuto()}</b>
        """.trimIndent()
}

object BotStringsRu : BotStrings {
    override fun startMenuTitle(version: String, rootStatus: String): String =
        "🤖 <b>Remote Bot Android</b> ($version)\n👑 <b>Root статус:</b> $rootStatus\n📡 <b>Служба:</b> 🟢 Активна"
    override fun startMenuSubtitle(): String = "Выберите категорию команд для навигации:"
    override fun btnBasic(): String = "⚙️ Базовые"
    override fun btnMedia(): String = "📷 Мультимедиа"
    override fun btnComm(): String = "📞 Связь и SMS"
    override fun btnGeoSec(): String = "📍 Геолокация"
    override fun btnFiles(): String = "📁 Файлы и App"
    override fun btnSystem(): String = "⚡ Система & Root"
    override fun btnAutomation(): String = "🤖 Автоматизация"
    override fun btnAllCommands(): String = "📋 Все команды"
    override fun btnStatus(): String = "📊 Статус"
    override fun btnBack(): String = "🔙 Назад в меню"
    override fun btnToggles(): String = "🎛️ Переключатели"
    override fun aboutTitle(version: String, rootStatus: String): String =
        """
        🤖 <b>Remote Bot Android</b>
        
        • <b>Версия:</b> $version
        • <b>Сборка:</b> Clean Architecture (SDK 35 / Android 15)
        • <b>Root статус:</b> $rootStatus
        
        🐙 <b>GitHub:</b> <a href="https://github.com/runalsh/remote-bot-android">github.com/runalsh/remote-bot-android</a>
        ✈️ <b>Telegram автора:</b> <a href="https://t.me/runalsh">@runalsh</a>
        
        ⚖️ Лицензия: Apache License 2.0
        """.trimIndent()

    override fun permissionsTitle(granted: Int, total: Int): String =
        "🛡️ <b>Статус Android-разрешений</b> ($granted/$total)"
    override fun filterLabel(filter: String): String = "Фильтр: <code>$filter</code>"
    override fun groupCriticalTitle(): String = "🔴 <b>Критические (Система/Сеть/Фон):</b>"
    override fun groupConfidentialTitle(): String = "🟡 <b>Конфиденциальные (Камера/Микрофон/СМС):</b>"
    override fun groupStandardTitle(): String = "🟢 <b>Стандартные (Базовые функции):</b>"
    override fun statusGranted(): String = "Выдано"
    override fun statusDenied(): String = "Отклонено"
    override fun noPermissionsFound(): String = "По заданному фильтру ничего не найдено."
    override fun permissionsUsageHint(): String = "💡 <i>Использование: /permissions [critical|confidential|standard|denied]</i>"

    override fun statusTitle(): String = "📊 <b>Сводный статус Remote Bot</b>"
    override fun statusServiceRunning(): String = "🤖 <b>Состояние службы:</b> 🟢 Активен"
    override fun statusRoot(granted: Boolean): String =
        "👑 <b>Root (Superuser):</b> " + if (granted) "✅ Доступен (UID 0)" else "❌ Не предоставлен"
    override fun statusConnectionMode(mode: String): String = "📡 <b>Режим связи:</b> <code>$mode</code>"
    override fun statusTelegramApiTitle(): String = "🌐 <b>Telegram Bot API:</b>"
    override fun statusActiveNode(status: String): String = "• <b>Активный узел:</b> $status"
    override fun statusStrategy(strategy: String): String = "• <b>Стратегия выбора:</b> <code>$strategy</code>"
    override fun statusEndpointsPool(): String = "• <b>Пул эндпоинтов:</b>"

    override fun incomingSmsTitle(from: String, sim: String): String =
        "📩 <b>Входящее SMS</b> (SIM: $sim)\n<b>От:</b> <code>$from</code>"
    override fun incomingCallTitle(from: String, sim: String): String =
        "📞 <b>Входящий звонок</b> (SIM: $sim)\n<b>От:</b> <code>$from</code>"
    override fun missedCallTitle(from: String, sim: String): String =
        "🔴 <b>Пропущенный вызов</b> (SIM: $sim)\n<b>От:</b> <code>$from</code>"
    override fun batteryChargingTitle(level: Int): String = "⚡ <b>Подключена зарядка</b> ($level%)"
    override fun batteryLowTitle(level: Int): String = "🪫 <b>Низкий заряд батареи</b> ($level%)"
    override fun batteryLevel(level: Int): String = "Уровень заряда: $level%"
    override fun actionApproved(): String = "✅ Подтвердить"
    override fun actionRejected(): String = "❌ Отклонить"
    override fun commandNotFound(cmd: String): String = "Неизвестная команда: $cmd"

    override fun categoryBase(): String =
        """
        ⚙️ <b>Базовые команды и Диагностика:</b>
        • /status — сводный статус подключения, пинг Telegram API, FCM/HMS
        • /permissions [filter] — аудит разрешений (critical/confidential/standard/denied)
        • /ping — проверка времени отклика и сетевой задержки
        • /echo &lt;текст&gt; — эхо-тест входящих команд бота
        • /chat_id — узнать Chat ID и User ID текущего чата
        • /about — информация о сборке бота, версии и разработчике
        • /uptime — время непрерывной работы процесса службы и системы Android
        """.trimIndent()

    override fun categoryMedia(): String =
        """
        📷 <b>Камера, Звук и Экран:</b>
        • /photo [front|back] — снимок с фронтальной или основной камеры
        • /videorec [front|back] [секунды] — скрытая видеозапись
        • /gallery [N] — отправка последних N медиафайлов из галереи
        • /rec [секунды] — скрытая аудиозапись с микрофона (голос)
        • /rec_file [секунды] — аудиозапись в виде несжатого файла .wav/.m4a
        • /tts &lt;текст&gt; — воспроизведение текста синтезатором речи на динамик
        • /player [play|pause|next|prev|stop] — управление воспроизведением медиаплеера
        • /volume [0-100|stream] [значение] — регулировка громкости звуковых потоков
        • /ring_mode [normal|silent|vibrate] — переключение профилей звонка
        • /device_search — громкая сирена и вибрация для поиска потерянного телефона
        • /screenshot — моментальный снимок экрана (MediaProjection / Root)
        • /screen_rec [секунды] — запись экрана в видеофайл (Root / Service)
        """.trimIndent()

    override fun categoryComm(): String =
        """
        📞 <b>Связь, SMS & Уведомления:</b>
        • /sms &lt;номер&gt; &lt;текст&gt; — прямая отправка исходящего SMS с выбором SIM
        • /sms_inbox [N] — просмотр последних входящих SMS сообщений
        • /call &lt;номер&gt; — инициация голосового телефонного вызова
        • /callshistory [N] — история входящих, исходящих и пропущенных вызовов
        • /ussd &lt;код&gt; — выполнение USSD-запроса баланса или тарифа (Android 8.0+)
        • /notify [list|clear] — просмотр активных системных уведомлений в шторке
        • /notify_send [id] — принудительная отправка уведомления в чат
        • /fake_notify &lt;title&gt; | &lt;text&gt; — генерация фиктивного уведомления в шторку Android
        • /answer &lt;notif_id&gt; &lt;текст&gt; — отправка быстрого ответа (RemoteInput) в мессенджер
        • /notify_clear [id|all] — удаление уведомления из шторки устройства
        • /notify_rule [add|del|list] — гибкие regex-правила фильтрации входящих уведомлений
        """.trimIndent()

    override fun categoryGeoSec(): String =
        """
        📍 <b>Геолокация & Охрана:</b>
        • /location — текущие координаты GPS/Сеть со ссылкой на карты Google
        • /track [start|stop|status] — непрерывный фоновый GPS-трекинг маршрута
        • /shake_detector [on|off|sens] — скрытая сигнализация на движение акселерометра
        • /detectmotion [on|off|front|back] — обнаружение движения камерой с авто-фото
        • /sound_detector [on|off|sens] — детекция шума/голоса вокруг смартфона
        """.trimIndent()

    override fun categoryFiles(): String =
        """
        📁 <b>Файлы & Приложения:</b>
        • /get_file &lt;путь&gt; — скачивание любого файла из файловой системы Android
        • /download &lt;url&gt; [путь] — удаленная загрузка файлов из интернета на устройство
        • /ls &lt;путь&gt; — просмотр содержимого папки на устройстве
        • /ls_interactive &lt;путь&gt; — интерактивный проводник файлов с Inline-кнопками навигации
        • /apps [system|user|all] — список установленных приложений и пакетов
        • /app_launch &lt;package&gt; — запуск выбранного приложения на экране
        • /install — удаленная установка APK-файла (тихая установка при Root)
        • /taskmanager (алиас: /top) — диспетчер запущенных процессов и потребления RAM
        • /kill &lt;package|pid&gt; — принудительное завершение зависшего приложения (Root)
        """.trimIndent()

    override fun categorySystem(): String =
        """
        ⚡ <b>Система & Root:</b>
        • /toggles (алиасы: /panel, /quick_settings) — интерактивная панель переключателей с кнопками
        • /system_info — сводка об устройстве, RAM, ROM, CPU, Root
        • /uptime — время непрерывной работы процесса службы и системы Android
        • /battery — статус аккумулятора, температура, вольтаж
        • /network — сетевые интерфейсы, IP (IPv4/IPv6), DNS, MAC, MTU
        • /wifi [on|off|toggle] — статус и переключение Wi-Fi
        • /bluetooth [on|off|toggle] — статус и переключение Bluetooth
        • /flashlight [on|off|toggle] — фонарик (вспышка камеры)
        • /airplane [on|off|toggle] — режим полета ⚡
        • /hotspot [on|off|toggle] (алиас: /tethering) — точка доступа Wi-Fi (Hotspot) ⚡
        • /mobile_data [on|off|toggle] (алиас: /data) — мобильная передача данных ⚡
        • /gps [on|off|toggle] (алиас: /location_mode) — геолокация (GPS) ⚡
        • /powersave [on|off|toggle] (алиас: /battery_saver) — режим экономии энергии ⚡
        • /auto_brightness [on|off|toggle] — автояркость экрана
        • /clipboard [текст|clear] — чтение и запись в буфер обмена
        • /sync [on|off|toggle] — автосинхронизация аккаунтов
        • /adb [on|off|toggle] (алиас: /usb_debugging) — режим отладки USB (ADB) ⚡
        • /adb_wifi [on|off|toggle|порт] (алиасы: /adbwifi, /wireless_adb) — отладка по Wi-Fi ⚡ (порт 5555)
        • /shell &lt;команда&gt; (алиас: /sh) — Linux терминал (Root/sh) ⚡
        • /reboot — перезагрузка устройства ⚡ (с паузой 10 сек)
        • /shutdown (алиас: /poweroff) — выключение смартфона ⚡ (с паузой 10 сек)
        • /recovery — перезагрузка в Recovery ⚡ (с паузой 10 сек)
        • /fastboot (алиас: /bootloader) — перезагрузка в Bootloader ⚡ (с паузой 10 сек)

        <i>⚡ = команды, требующие Root-прав (Superuser)</i>
        """.trimIndent()

    override fun categoryAuto(): String =
        """
        🤖 <b>Автоматизация, Tasker & Сеть:</b>
        • /tasker &lt;TaskName&gt; [par1] [par2] — запуск задач в приложении Tasker
        • /script &lt;код&gt; — выполнение системного скрипта/командной строки
        • /timers [list|add|del] — планировщик отложенных и периодических команд
        • /proxy — просмотр текущего статуса и подробная справка с примерами
        • /proxy [socks5|http|wss|off] — быстрая смена или выключение прокси
        • /active — активные фоновые операции
        • /stop_all — экстренная отмена всех операций
        • /log [N] [level] — журнал событий (по умолч: 20 строк, макс: 100, уровни: DEBUG/INFO/WARN/ERROR)
        • /backup — экспорт резервной копии конфигурации в файл .json
        • /restore_backup [json|файл] — восстановление конфигурации из текста или файла
        • /unsent_files — очередь неотправленных файлов
        • /bot_status — текущий статус Telegram и SMS каналов
        • /tg_bot [on|off|toggle] — включение/выключение Telegram бота
        • /sms_bot [on|off|toggle] — интерактивная панель статуса и включение/выключение SMS-бота
        • /bot_toggle &lt;tg|sms&gt; [on|off] — универсальный переключатель каналов связи
        • /add_user [id] / /del_user &lt;id&gt; / /list_users — управление белым списком Telegram User ID
        • /add_phone [номер] / /del_phone &lt;номер&gt; / /list_phones — доверенные SMS-номера без PIN
        • /add_forward [номер] / /del_forward &lt;номер&gt; / /list_forwards — номера пересылки SMS/звонков/push
        • /tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off] — управление доставкой событий в Telegram
        • /events [on|off|notif|sms|calls|system|list|id] (алиас: /subs) — интерактивное меню и рассылка событий
        • /whitelist_user [add|del|clear|list|&lt;id&gt;] (алиас: /user) — полное управление списком Telegram ID
        • /whitelist_phone [add|del|clear|list|&lt;номер&gt;] (алиас: /phone) — полное управление доверенными номерами SMS
        • /sms_forward [add|del|clear|list|on|off|missed|push|translit|&lt;номер&gt;] (алиас: /forward) — комплексное управление пересылкой
        """.trimIndent()

    override fun fullHelp(version: String, rootText: String): String =
        """
        🤖 <b>Remote Bot Android</b> ($version)
        👑 Root статус: $rootText

        <b>${categoryBase()}</b>

        <b>${categoryMedia()}</b>

        <b>${categoryComm()}</b>

        <b>${categoryGeoSec()}</b>

        <b>${categoryFiles()}</b>

        <b>${categorySystem()}</b>

        <b>${categoryAuto()}</b>
        """.trimIndent()
}

object TelegramBotStrings {
    fun get(language: BotLanguage): BotStrings {
        return when (language) {
            BotLanguage.EN -> BotStringsEn
            BotLanguage.RU -> BotStringsRu
            BotLanguage.AUTO -> {
                val sysLang = java.util.Locale.getDefault().language
                if (sysLang.equals("ru", ignoreCase = true)) BotStringsRu else BotStringsEn
            }
        }
    }
}
