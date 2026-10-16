package com.remotebot.channels.telegram.handlers

import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.core.network.diagnostic.NetworkDiagnosticManager
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.provider.PermissionsStatusProvider
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository

class BaseCommandsHandler(
    private val rootStatusProvider: RootStatusProvider,
    private val botConfigRepository: BotConfigRepository? = null,
    private val apiClient: TelegramApiClient? = null,
    private val diagnosticManager: NetworkDiagnosticManager? = null,
    private val permissionsStatusProvider: PermissionsStatusProvider? = null,
    private val appVersion: String = "0.1.0",
) : CommandHandler {
    override val commandNames: List<String> =
        listOf(
            "start",
            "help",
            "about",
            "chat_id",
            "ping",
            "echo",
            "status",
            "permissions",
            "menu_main",
            "menu_all",
            "menu_cat_base",
            "menu_cat_media",
            "menu_cat_comm",
            "menu_cat_geo_sec",
            "menu_cat_files",
            "menu_cat_system",
            "menu_cat_auto",
        )
    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        val versionDisplay = if (appVersion.startsWith("v")) appVersion else "v$appVersion"
        return when (cmd) {
            "permissions" -> handlePermissions(command.args)
            "start", "help", "menu_main" -> handleMainMenu(versionDisplay)
            "menu_all" -> handleFullHelp(versionDisplay)
            "menu_cat_base" -> handleCategoryBase()
            "menu_cat_media" -> handleCategoryMedia()
            "menu_cat_comm" -> handleCategoryComm()
            "menu_cat_geo_sec" -> handleCategoryGeoSec()
            "menu_cat_files" -> handleCategoryFiles()
            "menu_cat_system" -> handleCategorySystem()
            "menu_cat_auto" -> handleCategoryAuto()
            "about" -> {
                val isRoot = rootStatusProvider.isRootGrantedSync()
                val rootText = if (isRoot) "✅ Доступен (UID 0)" else "❌ Отсутствует / Не выдан"
                CommandResult.Text(
                    """
                    🤖 <b>Remote Bot Android</b>
                    
                    • <b>Версия:</b> $versionDisplay
                    • <b>Сборка:</b> Clean Architecture (SDK 35 / Android 15)
                    • <b>Root статус:</b> $rootText
                    
                    🐙 <b>GitHub:</b> <a href="https://github.com/runalsh/remote-bot-android">github.com/runalsh/remote-bot-android</a>
                    ✈️ <b>Telegram автора:</b> <a href="https://t.me/runalsh">@runalsh</a>
                    
                    ⚖️ Лицензия: Apache License 2.0
                    """.trimIndent(),
                )
            }
            "chat_id" -> {
                CommandResult.Text(
                    "Chat ID: <code>${command.chatId}</code>\nUser ID: <code>${command.senderId}</code>",
                )
            }
            "status" -> {
                val isRoot = rootStatusProvider.isRootGrantedSync()
                val config = botConfigRepository?.config?.value
                val activeEndpoint = apiClient?.lastActiveEndpointUrl ?: (config?.customApiRoot ?: "https://api.telegram.org")
                val mode = config?.connectionMode?.name ?: "LONG_POLLING"

                // Проверяем пинг активного Telegram API эндпоинта
                val apiDiag = diagnosticManager?.checkHttpEndpoint(activeEndpoint)
                val apiStatusStr =
                    if (apiDiag != null && apiDiag.isReachable) {
                        "🟢 Доступен (${apiDiag.latencyMs}ms)"
                    } else {
                        "🔴 Ошибка (${apiDiag?.error ?: "Таймаут"})"
                    }

                val strategyStr = config?.endpointSelectionStrategy?.name ?: "FAILOVER"
                val candidateRoots = apiClient?.getCandidateRoots() ?: emptyList()

                val text =
                    buildString {
                        appendLine("📊 <b>Сводный статус Remote Bot</b>")
                        appendLine()
                        appendLine("🤖 <b>Состояние службы:</b> 🟢 Активен")
                        appendLine("👑 <b>Root (Superuser):</b> ${if (isRoot) "✅ Доступен (UID 0)" else "❌ Не предоставлен"}")
                        appendLine("📡 <b>Режим связи:</b> <code>$mode</code>")
                        appendLine()
                        appendLine("🌐 <b>Telegram Bot API:</b>")
                        appendLine("• <b>Активный узел:</b> $apiStatusStr")
                        appendLine("  <code>$activeEndpoint</code>")
                        appendLine("• <b>Стратегия выбора:</b> <code>$strategyStr</code>")
                        appendLine("• <b>Пул эндпоинтов:</b>")
                        if (candidateRoots.isNotEmpty()) {
                            candidateRoots.forEach { root ->
                                appendLine("  • <code>$root</code>")
                            }
                        } else {
                            appendLine("  • <code>$activeEndpoint</code>")
                        }
                        appendLine()
                        appendLine("🚀 <b>Сетевая доступность Push сервисов:</b>")
                        val gwUrl = config?.pushGatewayUrl
                        if (!gwUrl.isNullOrBlank()) {
                            val gwDiag = diagnosticManager?.checkHttpEndpoint(gwUrl)
                            val gwStatus = if (gwDiag != null && gwDiag.isReachable) "🟢 ${gwDiag.latencyMs}ms" else "🔴 Таймаут"
                            appendLine("• <b>Webhook Push Gateway:</b> $gwStatus")
                            appendLine("  <code>$gwUrl</code>")
                        }
                        val fcmDiag = diagnosticManager?.pingTarget(NetworkDiagnosticManager.FCM_ENDPOINT, 2500)
                        val fcmStatus = if (fcmDiag != null && fcmDiag.isReachable) "🟢 ${fcmDiag.latencyMs}ms" else "🔴 Недоступен"
                        appendLine("• <b>Google FCM:</b> $fcmStatus (<code>fcm.googleapis.com</code>)")

                        val hmsDiag = diagnosticManager?.pingTarget(NetworkDiagnosticManager.HMS_ENDPOINT, 2500)
                        val hmsStatus = if (hmsDiag != null && hmsDiag.isReachable) "🟢 ${hmsDiag.latencyMs}ms" else "🔴 Недоступен"
                        appendLine("• <b>Huawei HMS:</b> $hmsStatus (<code>push-api.cloud.huawei.com</code>)")
                    }.trimEnd()

                CommandResult.Text(text)
            }

            "ping" -> {
                val now = System.currentTimeMillis()
                val delta = now - command.receivedAtMs
                CommandResult.Text("🏓 Pong! Задержка обработки: ${delta}ms")
            }
            "echo" -> {
                val text = command.args.joinToString(" ")
                CommandResult.Text(if (text.isBlank()) "Пустой ввод." else "📢 Эхо: $text")
            }
            else -> CommandResult.Error("Неизвестная сервисная команда: $cmd")
        }
    }

    private suspend fun handlePermissions(args: List<String>): CommandResult {
        val provider =
            permissionsStatusProvider
                ?: return CommandResult.Error("Провайдер разрешений не инициализирован.")

        val filter = args.firstOrNull()?.lowercase()
        val allPermissions = provider.getPermissionsStatus()

        val filtered =
            when (filter) {
                "critical" -> allPermissions.filter { it.group.equals("critical", ignoreCase = true) }
                "confidential", "privacy" -> allPermissions.filter { it.group.equals("confidential", ignoreCase = true) }
                "standard", "other" -> allPermissions.filter { it.group.equals("standard", ignoreCase = true) }
                "missing", "denied" -> allPermissions.filter { !it.isGranted }
                else -> allPermissions
            }

        val grantedCount = allPermissions.count { it.isGranted }
        val totalCount = allPermissions.size

        val text =
            buildString {
                appendLine("🛡️ <b>Статус Android-разрешений</b> ($grantedCount/$totalCount)")
                if (!filter.isNullOrBlank()) {
                    appendLine("Фильтр: <code>$filter</code>")
                }
                appendLine()

                val grouped = filtered.groupBy { it.group.lowercase() }
                val groupOrder = listOf("critical", "confidential", "standard")
                for (groupKey in groupOrder) {
                    val list = grouped[groupKey] ?: continue
                    val groupTitle =
                        when (groupKey) {
                            "critical" -> "🔴 <b>Критические (Система/Сеть/Фон):</b>"
                            "confidential" -> "🟡 <b>Конфиденциальные (Камера/Микрофон/СМС):</b>"
                            else -> "🟢 <b>Стандартные (Базовые функции):</b>"
                        }
                    appendLine(groupTitle)
                    for (item in list) {
                        val icon = if (item.isGranted) "✅" else "❌"
                        appendLine("$icon <b>${item.name}:</b> ${if (item.isGranted) "Выдано" else "Отклонено"}")
                        if (!item.isGranted && item.description.isNotBlank()) {
                            appendLine("   ↳ <i>${item.description}</i>")
                        }
                    }
                    appendLine()
                }

                if (filtered.isEmpty()) {
                    appendLine("По заданному фильтру ничего не найдено.")
                    appendLine()
                }

                appendLine("💡 <i>Использование: /permissions [critical|confidential|standard|denied]</i>")
            }.trimEnd()

        return CommandResult.Text(text)
    }

    private fun createCategoryKeyboard(): InlineKeyboardMarkup =
        InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton("🔙 Назад в меню", "/menu_main"),
                    InlineKeyboardButton("📊 Статус", "/status"),
                ),
            ),
        )

    private fun handleMainMenu(versionDisplay: String): CommandResult {
        val isRoot = rootStatusProvider.isRootGrantedSync()
        val rootText = if (isRoot) "✅ АКТИВЕН (UID 0)" else "❌ Отсутствует / Не выдан"
        val text =
            """
            🤖 <b>Remote Bot Android</b> ($versionDisplay)
            👑 <b>Root статус:</b> $rootText
            📡 <b>Служба:</b> 🟢 Активна

            Выберите категорию команд для навигации:
            """.trimIndent()

        val keyboard =
            InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton("⚙️ Базовые", "/menu_cat_base"),
                        InlineKeyboardButton("📷 Мультимедиа", "/menu_cat_media"),
                    ),
                    listOf(
                        InlineKeyboardButton("📞 Связь и SMS", "/menu_cat_comm"),
                        InlineKeyboardButton("📍 Геолокация", "/menu_cat_geo_sec"),
                    ),
                    listOf(
                        InlineKeyboardButton("📁 Файлы и App", "/menu_cat_files"),
                        InlineKeyboardButton("⚡ Система & Root", "/menu_cat_system"),
                    ),
                    listOf(
                        InlineKeyboardButton("🤖 Автоматизация", "/menu_cat_auto"),
                        InlineKeyboardButton("🎛️ Переключатели", "/toggles"),
                    ),
                    listOf(
                        InlineKeyboardButton("📜 Полный список", "/menu_all"),
                        InlineKeyboardButton("📊 Статус", "/status"),
                    ),
                ),
            )
        return CommandResult.Text(text, keyboard = keyboard)
    }

    private fun handleCategoryBase(): CommandResult {
        val text =
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
        return CommandResult.Text(text, keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryMedia(): CommandResult {
        val text =
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
        return CommandResult.Text(text, keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryComm(): CommandResult {
        val text =
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
        return CommandResult.Text(text, keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryGeoSec(): CommandResult {
        val text =
            """
            📍 <b>Геолокация & Охрана:</b>
            • /location — текущие координаты GPS/Сеть со ссылкой на карты Google
            • /track [start|stop|status] — непрерывный фоновый GPS-трекинг маршрута
            • /shake_detector [on|off|sens] — скрытая сигнализация на движение акселерометра
            • /detectmotion [on|off|front|back] — обнаружение движения камерой с авто-фото
            • /sound_detector [on|off|sens] — детекция шума/голоса вокруг смартфона
            """.trimIndent()
        return CommandResult.Text(text, keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryFiles(): CommandResult {
        val text =
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
        return CommandResult.Text(text, keyboard = createCategoryKeyboard())
    }

    private fun handleCategorySystem(): CommandResult {
        val text =
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
        return CommandResult.Text(text, keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryAuto(): CommandResult {
        val text =
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
        return CommandResult.Text(text, keyboard = createCategoryKeyboard())
    }

    private fun handleFullHelp(versionDisplay: String): CommandResult {
        val isRoot = rootStatusProvider.isRootGrantedSync()
        val rootText = if (isRoot) "✅ АКТИВЕН (UID 0) — все 54 команды доступны" else "⚠️ ОГРАНИЧЕН (без Root) — системные команды недоступны"
        val text =
            """
            🤖 <b>Remote Bot Android</b> ($versionDisplay)
            👑 Root статус: $rootText

            <b>Базовые команды:</b>
            • /start / /help — приветствие и интерактивное меню
            • /status — сводный статус подключения, пинг Telegram API, FCM/HMS
            • /permissions [filter] — детальный аудит прав Android
            • /ping — проверка времени отклика и сетевой задержки
            • /echo &lt;текст&gt; — эхо-тест входящих команд бота
            • /chat_id — узнать Chat ID и User ID текущего чата
            • /about — информация о сборке бота, версии и разработчике

            <b>Медиа, Звук и Камера:</b>
            • /photo [front|back] — моментальный снимок с фронтальной или основной камеры
            • /videorec [front|back] [секунды] — скрытая видеозапись
            • /gallery [N] — отправка последних N медиафайлов из галереи устройства
            • /rec [секунды] — скрытая аудиозапись с микрофона (голос)
            • /rec_file [секунды] — аудиозапись в виде несжатого файла .wav/.m4a
            • /tts &lt;текст&gt; — воспроизведение текста синтезатором речи на динамик смартфона
            • /player [play|pause|next|prev|stop] — управление воспроизведением медиаплеера
            • /volume [0-100|stream] [значение] — регулировка громкости звуковых потоков
            • /ring_mode [normal|silent|vibrate] — переключение профилей звонка
            • /device_search — громкая сирена и вибрация для поиска потерянного телефона
            • /screenshot — моментальный снимок экрана (MediaProjection / Root)
            • /screen_rec [секунды] — запись экрана в видеофайл (Root / Service)

            <b>Связь, SMS & Уведомления:</b>
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

            <b>Геолокация и Охрана:</b>
            • /location — текущие координаты GPS/Сеть со ссылкой на карты Google
            • /track [start|stop|status] — непрерывный фоновый GPS-трекинг маршрута
            • /shake_detector [on|off|sens] — скрытая сигнализация на движение акселерометра
            • /detectmotion [on|off|front|back] — обнаружение движения камерой с авто-фото
            • /sound_detector [on|off|sens] — детекция шума/голоса вокруг смартфона

            <b>Файлы и Приложения:</b>
            • /get_file &lt;путь&gt; — скачивание любого файла из файловой системы Android
            • /download &lt;url&gt; [путь] — удаленная загрузка файлов из интернета на устройство
            • /ls &lt;путь&gt; — просмотр содержимого папки на устройстве
            • /ls_interactive &lt;путь&gt; — интерактивный проводник файлов с Inline-кнопками навигации
            • /apps [system|user|all] — список установленных приложений и пакетов
            • /app_launch &lt;package&gt; — запуск выбранного приложения на экране
            • /install — удаленная установка APK-файла (тихая установка при Root)
            • /taskmanager (алиас: /top) — диспетчер запущенных процессов и потребления RAM
            • /kill &lt;package|pid&gt; — принудительное завершение зависшего приложения (Root)

            <b>Автоматизация, Tasker и Скрипты:</b>
            • /tasker &lt;TaskName&gt; [par1] [par2] — запуск задач в приложении Tasker
            • /script &lt;код&gt; — выполнение системного скрипта/командной строки
            • /timers [list|add|del] — планировщик отложенных и периодических команд

            <b>Системный контроль и Root:</b>
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

            <b>Управление ботом и сетью:</b>
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
            • /add_user [id] / /del_user &lt;id&gt; / /list_users — управление белым списком Telegram User ID (по умолч: ваш ID)
            • /add_phone [номер] / /del_phone &lt;номер&gt; / /list_phones — доверенные SMS-номера для управления без PIN
            • /add_forward [номер] / /del_forward &lt;номер&gt; / /list_forwards — номера пересылки SMS/звонков/push
            • /tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off] — управление доставкой событий в Telegram
            • /events [on|off|notif|sms|calls|system|list|id] (алиас: /subs) — интерактивное меню и рассылка событий
            • /whitelist_user [add|del|clear|list|&lt;id&gt;] (алиас: /user) — полное управление списком Telegram ID
            • /whitelist_phone [add|del|clear|list|&lt;номер&gt;] (алиас: /phone) — полное управление доверенными номерами SMS
            • /sms_forward [add|del|clear|list|on|off|missed|push|translit|&lt;номер&gt;] (алиас: /forward) — комплексное управление пересылкой

            <i>⚡ = команды, требующие Root-прав (Superuser)</i>
            """.trimIndent()

        val keyboard =
            InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton("🔙 Главное меню", "/menu_main"),
                        InlineKeyboardButton("🎛️ Переключатели", "/toggles"),
                    ),
                ),
            )
        return CommandResult.Text(text, keyboard = keyboard)
    }
}

