package com.remotebot.channels.telegram.handlers

import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.LocationData
import com.remotebot.domain.repository.LocationRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationCommandsHandler(
    private val locationRepository: LocationRepository,
    private val apiClient: TelegramApiClient? = null,
    private val operationsTracker: com.remotebot.core.common.tracker.ActiveOperationsTracker? = null,
) : CommandHandler {
    override val commandNames: List<String> =
        listOf(
            "location",
            "gps",
            "shake_detector",
            "track",
            "trace",
        )

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "location", "trace" -> handleLocation(command)
            "track" -> handleTrack(command)
            "gps" -> handleGps(command.args)
            "shake_detector" -> handleShakeDetector(command.args)
            else -> CommandResult.Error("Неизвестная команда геолокации: $cmd")
        }
    }

    /**
     * Single-shot geolocation request:
     * /location [sec|last]
     * /trace [sec|last]
     */
    private suspend fun handleLocation(command: IncomingCommand): CommandResult {
        val args = command.args

        // Single-shot coordinate query (supports /location last, /location [sec])
        val isLast = args.firstOrNull()?.equals("last", ignoreCase = true) == true
        val timeout = if (isLast) 2 else args.firstOrNull()?.toIntOrNull()?.coerceIn(3, 60) ?: 30

        val targetChatId = command.chatId
        if (!isLast && command.source == CommandSource.TELEGRAM && targetChatId != null && targetChatId != 0L) {
            try {
                apiClient?.sendMessage(
                    targetChatId,
                    "🛰️ Определение местоположения... Это может занять до $timeout секунд.",
                )
            } catch (_: Exception) {
            }
        }

        val opId = "location_fetch_${System.currentTimeMillis()}"
        operationsTracker?.register(
            id = opId,
            name = "🛰️ Поиск геолокации (${timeout}с)",
            description = "Определение GPS/LBS координат (таймаут ${timeout}с)",
        )

        return try {
            locationRepository.getCurrentLocation(timeout).fold(
                onSuccess = { loc ->
                    if (command.source == CommandSource.TELEGRAM && targetChatId != null && targetChatId != 0L) {
                        try {
                            apiClient?.sendLocation(targetChatId, loc.latitude, loc.longitude)
                        } catch (_: Exception) {
                        }
                    }
                    val text = formatLocationReport(loc, isLive = false)
                    CommandResult.Text(text, keyboard = buildSingleLocationKeyboard())
                },
                onFailure = {
                    CommandResult.Error("❌ Ошибка определения координат: ${it.localizedMessage ?: it.message}")
                },
            )
        } finally {
            operationsTracker?.unregister(opId)
        }
    }

    /**
     * Periodic geolocation tracking:
     * /track [interval|stop|status]
     */
    private fun handleTrack(command: IncomingCommand): CommandResult {
        val args = command.args
        val first = args.firstOrNull()?.lowercase()

        // 1. Stop tracking: /track stop | /track off | /track cancel
        if (first in listOf("stop", "off", "cancel", "disable")) {
            val status = locationRepository.liveTrackingStatus.value
            if (!status.isActive) {
                return CommandResult.Text(
                    "ℹ️ Периодический трекинг в данный момент не активен.",
                    keyboard = buildTrackingKeyboard(false),
                )
            }
            locationRepository.stopLiveTracking()
            operationsTracker?.unregister("location_live")
            val durationMs = if (status.startedAtMs > 0) System.currentTimeMillis() - status.startedAtMs else 0L
            val durMinutes = durationMs / 60000L
            val durSeconds = (durationMs % 60000L) / 1000L
            return CommandResult.Text(
                """
                ⏹ <b>Периодический трекинг остановлен.</b>
                
                • Отправлено точек: <b>${status.pointsSent}</b>
                • Время работы трекера: <b>${durMinutes}м ${durSeconds}с</b>
                """.trimIndent(),
                keyboard = buildTrackingKeyboard(false),
            )
        }

        // 2. Tracking status: /track status | /track info
        if (first in listOf("status", "info")) {
            val status = locationRepository.liveTrackingStatus.value
            val isAct = status.isActive
            val stateText = if (isAct) "🟢 <b>АКТИВЕН</b>" else "⏹ <b>ОСТАНОВЛЕН</b>"
            val intervalText = formatIntervalReadable(status.intervalSeconds)
            val durText =
                if (isAct && status.startedAtMs > 0) {
                    val durationMs = System.currentTimeMillis() - status.startedAtMs
                    "${durationMs / 60000L}м ${(durationMs % 60000L) / 1000L}с"
                } else {
                    "—"
                }

            return CommandResult.Text(
                """
                📊 <b>Статус периодического трекинга (/track):</b>
                
                • Состояние: $stateText
                • Интервал отправки: <b>$intervalText</b>
                • Отправлено точек: <b>${status.pointsSent}</b>
                • Время работы: <b>$durText</b>
                """.trimIndent(),
                keyboard = buildTrackingKeyboard(isAct),
            )
        }

        // 3. Start tracking: /track [30s|1m|5m...] or panel without arguments
        if (first != null && first !in listOf("on", "start", "panel")) {
            val intervalSeconds = parseIntervalSeconds(first) ?: 60L
            val clampedInterval = intervalSeconds.coerceIn(10L, 86400L)
            locationRepository.startLiveTracking(clampedInterval)
            val readable = formatIntervalReadable(clampedInterval)
            operationsTracker?.register(
                id = "location_live",
                name = "🛰️ Периодический GPS-трекинг ($readable)",
                description = "Интервал $readable",
                onCancel = { locationRepository.stopLiveTracking() },
            )

            return CommandResult.Text(
                """
                🛰️ <b>Периодический трекинг запущен!</b>
                
                ⏱ Интервал отправки: <b>каждые $readable</b>
                📍 Координаты будут периодически отправляться в этот чат до остановки.
                
                <i>Для остановки нажмите кнопку ниже или отправьте команду <code>/track stop</code>.</i>
                """.trimIndent(),
                keyboard = buildTrackingKeyboard(true),
            )
        }

        // If argument is on / start with duration: /track start 5m
        if (first in listOf("on", "start") && args.size >= 2) {
            val intervalSeconds = parseIntervalSeconds(args[1]) ?: 60L
            val clampedInterval = intervalSeconds.coerceIn(10L, 86400L)
            locationRepository.startLiveTracking(clampedInterval)
            val readable = formatIntervalReadable(clampedInterval)
            operationsTracker?.register(
                id = "location_live",
                name = "🛰️ Периодический GPS-трекинг ($readable)",
                description = "Интервал $readable",
                onCancel = { locationRepository.stopLiveTracking() },
            )

            return CommandResult.Text(
                """
                🛰️ <b>Периодический трекинг запущен!</b>
                
                ⏱ Интервал отправки: <b>каждые $readable</b>
                📍 Координаты будут периодически отправляться в этот чат до остановки.
                
                <i>Для остановки нажмите кнопку ниже или отправьте команду <code>/track stop</code>.</i>
                """.trimIndent(),
                keyboard = buildTrackingKeyboard(true),
            )
        }

        // No arguments or /track panel — tracking control panel
        val isAct = locationRepository.isLiveTrackingActive()
        val curStatus = locationRepository.liveTrackingStatus.value
        val stateText = if (isAct) "🟢 <b>АКТИВЕН</b> (${formatIntervalReadable(curStatus.intervalSeconds)})" else "⏹ <b>ОСТАНОВЛЕН</b>"

        return CommandResult.Text(
            """
            🛰️ <b>Управление периодическим трекингом (/track):</b>
            
            • Текущий статус: $stateText
            • Выберите интервал отправки геопозиции с помощью кнопок:
            """.trimIndent(),
            keyboard = buildTrackingKeyboard(isAct),
        )
    }

    private fun parseIntervalSeconds(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val s = str.trim().lowercase()
        if (s.endsWith("s") || s.endsWith("сек") || s.endsWith("sec")) {
            return s.filter { it.isDigit() }.toLongOrNull()
        }
        if (s.endsWith("m") || s.endsWith("мин") || s.endsWith("min")) {
            return (s.filter { it.isDigit() }.toLongOrNull() ?: 1L) * 60L
        }
        if (s.endsWith("h") || s.endsWith("ч") || s.endsWith("час") || s.endsWith("hour")) {
            return (s.filter { it.isDigit() }.toLongOrNull() ?: 1L) * 3600L
        }
        return s.toLongOrNull()
    }

    private fun formatIntervalReadable(seconds: Long): String =
        when {
            seconds < 60L -> "$seconds сек"
            seconds % 3600L == 0L -> "${seconds / 3600L} ч"
            seconds % 60L == 0L -> "${seconds / 60L} мин"
            else -> "${seconds / 60L} мин ${seconds % 60L} сек"
        }

    companion object {
        fun formatLocationReport(
            loc: LocationData,
            isLive: Boolean = false,
        ): String {
            val gmapsUrl = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            val osmUrl = "https://www.openstreetmap.org/?mlat=${loc.latitude}&mlon=${loc.longitude}#map=17/${loc.latitude}/${loc.longitude}"
            val timeStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(loc.timestampMs))
            val addressText = if (!loc.address.isNullOrBlank()) "\n🏠 <b>Адрес:</b> ${loc.address}" else ""

            val modeHeader =
                if (isLive) {
                    val seqStr = if (loc.trackingSequenceNumber != null) " [Точка #${loc.trackingSequenceNumber}]" else ""
                    "🛰️ <b>Периодический трекинг геопозиции$seqStr:</b>"
                } else {
                    "📍 <b>Геолокация устройства:</b>"
                }

            // Detection method
            val methodText =
                if (loc.determinationMethod.isNotBlank()) {
                    loc.determinationMethod
                } else if (loc.isDirectGps) {
                    "🛰️ Спутниковый GPS/ГЛОНАСС"
                } else {
                    "📡 Косвенные признаки (Вышки сотовой связи / Wi-Fi)"
                }

            // GPS signal
            val signalText =
                buildString {
                    if (loc.isDirectGps) {
                        val lvl = loc.signalStrengthLevel ?: "Уверенный приём"
                        append("📶 <b>Сигнал GPS:</b> $lvl")
                        val cn0 = loc.cn0DbHz
                        if (cn0 != null && cn0 > 0f) {
                            append(" (${"%.1f".format(cn0)} dB-Hz)")
                        }
                        if (loc.satellitesUsed != null || loc.satellitesVisible != null) {
                            val used = loc.satellitesUsed ?: 0
                            val total = loc.satellitesVisible ?: used
                            append(" | 🛰️ Спутники: $used в решении / $total видимых")
                        }
                    } else {
                        append("📶 <b>Сигнал GPS:</b> ")
                        append(loc.signalStrengthLevel ?: "❌ Спутниковый сигнал отсутствует (поиск по LBS/Wi-Fi)")
                    }
                }

            val altSpeedText =
                if (loc.altitudeMeters != 0.0 || loc.speedMps > 0f) {
                    "\n⛰️ <b>Высота:</b> ${"%.1f".format(
                        loc.altitudeMeters,
                    )} м | 🏃 <b>Скорость:</b> ${"%.1f".format(loc.speedMps * 3.6f)} км/ч"
                } else {
                    ""
                }

            return """
                $modeHeader
                
                🌐 <b>Координаты:</b> <code>${loc.latitude}, ${loc.longitude}</code>
                🎯 <b>Точность:</b> ±${"%.1f".format(loc.accuracyMeters)} м
                📡 <b>Способ определения:</b> $methodText
                $signalText$altSpeedText
                🕒 <b>Время фиксации:</b> $timeStr$addressText
                
                🗺️ <a href="$gmapsUrl">Google Карты</a> | <a href="$osmUrl">OpenStreetMap</a>
                """.trimIndent()
        }

        fun buildSingleLocationKeyboard(): InlineKeyboardMarkup {
            val row1 =
                listOf(
                    InlineKeyboardButton("🔄 Запросить снова", callbackData = "/location"),
                    InlineKeyboardButton("⚡ Мгновенно (кэш)", callbackData = "/location last"),
                )
            val row2 =
                listOf(
                    InlineKeyboardButton("🛰️ Запустить трекинг (/track)", callbackData = "/track"),
                )
            return InlineKeyboardMarkup(listOf(row1, row2))
        }

        fun buildTrackingKeyboard(isTrackingActive: Boolean): InlineKeyboardMarkup {
            val row1 =
                listOf(
                    InlineKeyboardButton("⏱ 30 сек", callbackData = "/track 30s"),
                    InlineKeyboardButton("⏱ 1 мин", callbackData = "/track 1m"),
                    InlineKeyboardButton("⏱ 5 мин", callbackData = "/track 5m"),
                )
            val row2 =
                listOf(
                    InlineKeyboardButton("⏱ 15 мин", callbackData = "/track 15m"),
                    InlineKeyboardButton("⏱ 30 мин", callbackData = "/track 30m"),
                    InlineKeyboardButton("⏱ 60 мин", callbackData = "/track 60m"),
                )
            val row3 =
                if (isTrackingActive) {
                    listOf(
                        InlineKeyboardButton("⏹ Остановить трекинг", callbackData = "/track stop"),
                        InlineKeyboardButton("📊 Статус", callbackData = "/track status"),
                    )
                } else {
                    listOf(
                        InlineKeyboardButton("📍 Разовый запрос (/location)", callbackData = "/location"),
                    )
                }
            return InlineKeyboardMarkup(listOf(row1, row2, row3))
        }

        fun buildLocationKeyboard(isTrackingActive: Boolean): InlineKeyboardMarkup = buildTrackingKeyboard(isTrackingActive)
    }

    private suspend fun handleGps(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val isEnabled = locationRepository.isGpsEnabled()
            val status = if (isEnabled) "✅ <b>ВКЛЮЧЕН</b>" else "❌ <b>ВЫКЛЮЧЕН</b>"
            return CommandResult.Text("🛰️ <b>GPS модуль:</b> $status\nПереключение: <code>/gps on</code> | <code>/gps off</code>")
        }

        val target =
            when (args[0].lowercase()) {
                "on", "1", "true", "enable" -> true
                "off", "0", "false", "disable" -> false
                "toggle" -> !locationRepository.isGpsEnabled()
                else -> return CommandResult.Error("Формат: <code>/gps on</code> или <code>/gps off</code>")
            }

        return locationRepository.setGpsEnabled(target).fold(
            onSuccess = {
                val stateText = if (target) "включен ✅" else "выключен ❌"
                CommandResult.Text("🛰️ GPS модуль успешно $stateText")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка переключения GPS: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private var selectedShakeSensitivityLevel: Int = 5
    private var selectedShakeCooldownSeconds: Int = 10

    private fun handleShakeDetector(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return buildShakeDetectorPanel(selectedShakeSensitivityLevel, selectedShakeCooldownSeconds)
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "set" -> {
                val lvl = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: selectedShakeSensitivityLevel
                val cd = args.getOrNull(2)?.toIntOrNull() ?: selectedShakeCooldownSeconds
                selectedShakeSensitivityLevel = lvl
                selectedShakeCooldownSeconds = cd
                val threshold = levelToThreshold(lvl)
                enableShake(threshold, lvl, cd)
                buildShakeDetectorPanel(lvl, cd)
            }
            "cd", "cooldown", "interval" -> {
                val cd = args.getOrNull(1)?.toIntOrNull() ?: 10
                selectedShakeCooldownSeconds = cd
                val cfg = locationRepository.shakeDetectorConfig.value
                val curLvl = if (cfg.isEnabled) thresholdToLevel(cfg.sensitivity) else selectedShakeSensitivityLevel
                if (cfg.isEnabled) {
                    val threshold = levelToThreshold(curLvl)
                    enableShake(threshold, curLvl, cd)
                }
                buildShakeDetectorPanel(curLvl, cd)
            }
            "panel" -> {
                val lvl = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: selectedShakeSensitivityLevel
                val cd = args.getOrNull(2)?.toIntOrNull() ?: selectedShakeCooldownSeconds
                buildShakeDetectorPanel(lvl, cd)
            }
            "on", "start", "enable", "run" -> {
                val lvl = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: selectedShakeSensitivityLevel
                val cd = args.getOrNull(2)?.toIntOrNull() ?: selectedShakeCooldownSeconds
                selectedShakeSensitivityLevel = lvl
                selectedShakeCooldownSeconds = cd
                val threshold = levelToThreshold(lvl)
                enableShake(threshold, lvl, cd)
                buildShakeDetectorPanel(lvl, cd)
            }
            "off", "stop", "disable" -> {
                disableShake()
                buildShakeDetectorPanel(selectedShakeSensitivityLevel, selectedShakeCooldownSeconds)
            }
            else -> {
                val directLvl = sub.toIntOrNull()
                if (directLvl != null) {
                    val clamped = directLvl.coerceIn(1, 10)
                    val cd = args.getOrNull(1)?.toIntOrNull() ?: selectedShakeCooldownSeconds
                    selectedShakeSensitivityLevel = clamped
                    selectedShakeCooldownSeconds = cd
                    val threshold = levelToThreshold(clamped)
                    enableShake(threshold, clamped, cd)
                    buildShakeDetectorPanel(clamped, cd)
                } else {
                    buildShakeDetectorPanel(selectedShakeSensitivityLevel, selectedShakeCooldownSeconds)
                }
            }
        }
    }

    private fun enableShake(
        threshold: Float,
        lvl: Int,
        cd: Int,
    ) {
        locationRepository.setShakeDetectorEnabled(true, threshold, cd)
        operationsTracker?.register(
            id = "shake_detector",
            name = "📳 Датчик тряски ($lvl/10, ${cd}с)",
            description = "Порог ${"%.1f".format(threshold)} m/s², интервал ${cd}с",
            onCancel = { locationRepository.setShakeDetectorEnabled(false) },
        )
    }

    private fun disableShake() {
        locationRepository.setShakeDetectorEnabled(false)
        operationsTracker?.unregister("shake_detector")
    }

    // Sensitivity scale calibrated to original RemoteBot (from 30.0 m/s² down to 1.0 m/s² at level 10)
    private val sensitivityLevels = floatArrayOf(30.0f, 25.0f, 20.0f, 15.0f, 10.0f, 7.0f, 5.0f, 3.0f, 2.0f, 1.0f)

    private fun levelToThreshold(level: Int): Float {
        val idx = (level.coerceIn(1, 10)) - 1
        return sensitivityLevels[idx]
    }

    private fun thresholdToLevel(threshold: Float): Int {
        var closestIdx = 0
        var minDiff = Float.MAX_VALUE
        for (i in sensitivityLevels.indices) {
            val diff = kotlin.math.abs(sensitivityLevels[i] - threshold)
            if (diff < minDiff) {
                minDiff = diff
                closestIdx = i
            }
        }
        return closestIdx + 1
    }

    private fun buildShakeDetectorPanel(
        selectedLevel: Int = selectedShakeSensitivityLevel,
        selectedCooldown: Int = selectedShakeCooldownSeconds,
    ): CommandResult.Text {
        val cfg = locationRepository.shakeDetectorConfig.value
        val activeLevel = if (cfg.isEnabled) thresholdToLevel(cfg.sensitivity) else selectedLevel
        val activeCooldown = if (cfg.isEnabled) cfg.cooldownSeconds else selectedCooldown
        val currentThreshold = levelToThreshold(activeLevel)

        val row1 =
            (1..5).map { lvl ->
                val label = if (activeLevel == lvl) "$lvl ✅" else "$lvl"
                InlineKeyboardButton(label, callbackData = "/shake_detector set $lvl $activeCooldown")
            }
        val row2 =
            (6..10).map { lvl ->
                val label = if (activeLevel == lvl) "$lvl ✅" else "$lvl"
                InlineKeyboardButton(label, callbackData = "/shake_detector set $lvl $activeCooldown")
            }

        val cooldownIntervals = listOf(1, 2, 5, 10, 15, 30, 60)
        val rowCooldown =
            cooldownIntervals.map { sec ->
                val label = if (activeCooldown == sec) "${sec}с ✅" else "${sec}с"
                InlineKeyboardButton(label, callbackData = "/shake_detector cd $sec")
            }

        val rowActions = mutableListOf<InlineKeyboardButton>()
        if (cfg.isEnabled) {
            rowActions.add(InlineKeyboardButton("⏹ Выключить", callbackData = "/shake_detector off"))
        } else {
            rowActions.add(
                InlineKeyboardButton(
                    "▶️ Включить ($activeLevel/10, ${activeCooldown}с)",
                    callbackData = "/shake_detector on $activeLevel $activeCooldown",
                ),
            )
        }
        rowActions.add(InlineKeyboardButton("🔄 Обновить", callbackData = "/shake_detector panel $activeLevel $activeCooldown"))

        val statusText =
            if (cfg.isEnabled) {
                "✅ <b>АКТИВЕН</b> (Порог: ${"%.1f".format(cfg.sensitivity)} m/s², Интервал: ${activeCooldown}с)"
            } else {
                "❌ <b>ОТКЛЮЧЕН</b>"
            }

        val text =
            """
            📳 <b>Охранный датчик тряски (Акселерометр)</b>
            
            • Статус: $statusText
            • Текущий уровень: <b>$activeLevel / 10</b> (Порог: ${"%.1f".format(currentThreshold)} m/s²)
            • Интервал отправки тревоги: <b>$activeCooldown сек</b> (защита от спама)
            
            <i>Работает непрерывно до явной остановки. При фиксации тряски отправляет тревогу не чаще выбранного интервала.</i>
            <i>Шкала: 1 = грубый (30 m/s², сильные удары/падения), 10 = ультрачувствительный (1 m/s², малейшее движение).</i>
            
            👇 <b>Чувствительность (1..10):</b>
            """.trimIndent()

        return CommandResult.Text(
            message = text,
            keyboard = InlineKeyboardMarkup(listOf(row1, row2, rowCooldown, rowActions)),
        )
    }
}
