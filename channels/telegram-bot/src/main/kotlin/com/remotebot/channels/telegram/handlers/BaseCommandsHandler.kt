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
) : CommandHandler {
    override val commandNames: List<String> = listOf("start", "help", "about", "chat_id", "ping", "echo", "status", "permissions")
    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "permissions" -> handlePermissions(command.args)
            "start", "help" -> {
                val isRoot = rootStatusProvider.isRootGrantedSync()
                val rootStatusLine =
                    if (isRoot) {
                        "👑 <b>Root статус:</b> ✅ <b>АКТИВЕН (UID 0)</b> — все 54 команды доступны"
                    } else {
                        "👑 <b>Root статус:</b> ⚠️ <b>НЕ ПРЕДОСТАВЛЕН</b> (команды /shell, /reboot, /hotspot, /adb_wifi отключены)"
                    }

                CommandResult.Text(
                    """
                    🤖 <b>Remote Bot Android (v2.0 Clean)</b>
                    $rootStatusLine
                    
                    <b>Базовые команды:</b>
                    • /start / /help — приветствие и полный список всех команд
                    • /status — сводный статус подключения, пинг API и доступность Push
                    • /permissions [group] — проверка статуса выданных Android-разрешений
                    • /ping — проверка связи (Pong!)
                    • /echo &lt;текст&gt; — эхо-тест
                    • /chat_id — ID чата и пользователя
                    • /about — информация о сборке и статусе
                    
                    📷 <b>Мультимедиа (Камера, Звук, Экран):</b>
                    • /photo [front|back] [flash_on|off] [rotate] — фото с камеры
                    • /videorec [front|back] [сек] — видеозапись с камеры (кнопки 10-3600 сек, запись до остановки /videorec stop)
                    • /gallery [N] — выгрузка последних N фото из галереи (по умолч: 5 фото, макс: 10)
                    • /rec [сек] [file|voice] — скрытая запись с микрофона (кнопки 10-3600 сек, запись до остановки /rec stop)
                    • /rec_file [сек] — скрытая запись в формате аудиофайла (с кнопками управления)
                    • /rec stop — экстренно остановить текущую аудиозапись
                    • /tts [lang] &lt;текст&gt; — синтез речи через динамик (по умолч: системный язык)
                    • /player (алиас: /playback) — медиаплеер и быстрые звуковые пресеты
                    • /volume [stream] [level] — просмотр и регулировка громкости (0-100%)
                    • /ring_mode [normal|silent|vibrate] — переключение звукового профиля
                    • /device_search — сирена поиска устройства (максимальная громкость)
                    • /sound_detector [1..10|on|off] — интерактивный охранный детектор шума/звука (порог 40..85 dB)
                    • /detectmotion [1..10|on|off] — охранный детектор движения с камеры (порог 1..10, кулдаун 1..60с)
                    • /screenshot — снимок экрана смартфона в чат ⚡
                    • /screen_rec [сек] — видеозапись экрана в чат ⚡ (по умолч: 10 сек, макс: 180)
                    
                    📞 <b>Связь, SMS и Уведомления (Блоки 3, 8):</b>
                    • /sms [sim] &lt;номер&gt; &lt;текст&gt; — отправка SMS с выбором SIM (по умолч: SIM 1)
                    • /sms_inbox [N] — последние входящие/исходящие SMS (по умолч: 10 шт)
                    • /call [sim] &lt;номер&gt; — совершить голосовой вызов (по умолч: SIM 1)
                    • /callshistory [N] — журнал звонков (по умолч: 10 последних вызовов)
                    • /ussd [sim] &lt;код&gt; — отправка USSD-запроса (баланс, тариф)
                    • /notify [N] (алиас: /notifications) — список перехваченных push-уведомлений (по умолч: 10 шт)
                    • /notify_send &lt;заголовок&gt; &lt;текст&gt; — отправить уведомление в шторку Android
                    • /fake_notify &lt;pkg&gt; &lt;заголовок&gt; &lt;текст&gt; (алиас: /simulate_notify) — симуляция уведомления от приложения
                    • /answer &lt;ID&gt; &lt;текст&gt; (алиас: /resp) — быстрый ответ на уведомление (RemoteInput)
                    • /notify_clear &lt;ID&gt; — закрыть/удалить уведомление из шторки
                    • /notify_rule &lt;list|add|del&gt; — правила фильтрации уведомлений
                    
                    📍 <b>Геолокация и Безопасность (Блок 4):</b>
                    • /location [сек|last] (алиас: /trace) — разовое определение координат GPS/LBS (по умолч: 30с, last — мгновенно из кэша)
                    • /track [интервал|stop|status] — периодический GPS-трекинг с отправкой геопозиции (30s..60m, кнопки управления)
                    • /shake_detector [1..10|on|off] — интерактивный охранный датчик тряски (порог 1..30 м/с², интервал 1-60с, кнопки)
                    • /detectmotion [1..10|on|off] [интервал_сек] — охранный детектор движения (шкала 1..10, интервал фото 1-60с, кнопки)
                    
                    📁 <b>Файлы, Приложения и Процессы (Блок 5):</b>
                    • /get_file &lt;путь&gt; [zip] (алиас: /gf) — выгрузка файла или архива папки в чат
                    • /download &lt;URL&gt; [путь] — скачивание файла из сети (по умолч: папка Downloads)
                    • /ls / /dir [путь] — текстовый список файлов в папке (по умолч: /sdcard)
                    • /ls_interactive [путь] (алиас: /lsi) — интерактивный проводник с кнопками папок и файлов
                    • /apps [filter] — список установленных приложений (all, user, system)
                    • /app_launch &lt;pkg&gt; — удаленный запуск приложения
                    • /install &lt;apk&gt; — тихая установка APK-пакета ⚡
                    • /taskmanager — диспетчер задач и потребление CPU/ОЗУ
                    • /kill &lt;PID|pkg&gt; — принудительное завершение процесса ⚡
                    
                    🤖 <b>Автоматизация, Tasker и Скрипты (Блоки 9, 10):</b>
                    • /tasker &lt;TaskName&gt; [par1] [par2] — запуск задач в приложении Tasker
                    • /script &lt;код&gt; — выполнение системного скрипта/командной строки
                    • /timers [list|add|del] — планировщик отложенных и периодических команд
                    
                    <b>Системный контроль и Root (Блок 7):</b>
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
                    • /events [on|off|notif|sms|calls|system|list|id] (алиас: /subs) — интерактивное меню и рассылка событий (режим «Только команды» vs «Подписчик»)
                    • /whitelist_user [add|del|clear|list|<id>] (алиас: /user) — полное управление списком Telegram ID
                    • /whitelist_phone [add|del|clear|list|<номер>] (алиас: /phone) — полное управление доверенными номерами SMS
                    • /sms_forward [add|del|clear|list|on|off|missed|push|translit|<номер>] (алиас: /forward) — комплексное управление пересылкой и параметрами
                    
                    <i>⚡ = команды, требующие Root-прав (Superuser)</i>
                    """.trimIndent(),
                    keyboard =
                        InlineKeyboardMarkup(
                            listOf(
                                listOf(
                                    InlineKeyboardButton("🎛️ Переключатели", "/toggles"),
                                    InlineKeyboardButton("📊 Статус", "/status"),
                                ),
                                listOf(
                                    InlineKeyboardButton("⏱️ Uptime", "/uptime"),
                                    InlineKeyboardButton("📱 Система", "/system_info"),
                                ),
                            ),
                        ),
                )
            }
            "about" -> {
                val isRoot = rootStatusProvider.isRootGrantedSync()
                val rootText = if (isRoot) "✅ Доступен (UID 0)" else "❌ Отсутствует / Не выдан"
                CommandResult.Text(
                    """
                    🤖 <b>Remote Bot Android</b>
                    
                    • <b>Версия:</b> v1.0.0
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
}
