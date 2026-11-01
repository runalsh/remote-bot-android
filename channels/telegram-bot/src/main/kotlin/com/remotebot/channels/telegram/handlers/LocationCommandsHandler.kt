package com.remotebot.channels.telegram.handlers

import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.channels.telegram.strings.BotStrings
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.domain.model.BotLanguage
import com.remotebot.domain.repository.BotConfigRepository
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
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {

    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

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
            else -> CommandResult.Error(strings.locUnknownCmd(cmd))
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
                    strings.locAcquiring(timeout.toString()),
                )
            } catch (_: Exception) {
            }
        }

        val opId = "location_fetch_${System.currentTimeMillis()}"
        operationsTracker?.register(
            id = opId,
            name = strings.locOpName(timeout.toString()),
            description = strings.locOpDesc(timeout.toString()),
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
                    val text = formatLocationReport(loc, isLive = false, strings = strings)
                    CommandResult.Text(text, keyboard = buildSingleLocationKeyboard(strings))
                },
                onFailure = {
                    CommandResult.Error(strings.locAcquireError(it.localizedMessage ?: it.message ?: ""))
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
                    strings.locTrackInactive(),
                    keyboard = buildTrackingKeyboard(false, strings),
                )
            }
            locationRepository.stopLiveTracking()
            operationsTracker?.unregister("location_live")
            val durationMs = if (status.startedAtMs > 0) System.currentTimeMillis() - status.startedAtMs else 0L
            val durMinutes = durationMs / 60000L
            val durSeconds = (durationMs % 60000L) / 1000L
            return CommandResult.Text(
                strings.locTrackStopped(status.pointsSent.toString(), durMinutes.toString(), durSeconds.toString()),
                keyboard = buildTrackingKeyboard(false, strings),
            )
        }

        // 2. Tracking status: /track status | /track info
        if (first in listOf("status", "info")) {
            val status = locationRepository.liveTrackingStatus.value
            val isAct = status.isActive
            val stateText = strings.locTrackPanelState(isAct)
            val intervalText = formatIntervalReadable(status.intervalSeconds)
            val durText =
                if (isAct && status.startedAtMs > 0) {
                    val durationMs = System.currentTimeMillis() - status.startedAtMs
                    strings.locDurMinSec(durationMs / 60000L, (durationMs % 60000L) / 1000L)
                } else {
                    "—"
                }

            return CommandResult.Text(
                strings.locTrackStatus(stateText, intervalText, status.pointsSent.toString(), durText),
                keyboard = buildTrackingKeyboard(isAct, strings),
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
                name = strings.locTrackOpName(readable),
                description = strings.locTrackOpDesc(readable),
                onCancel = { locationRepository.stopLiveTracking() },
            )

            return CommandResult.Text(
                strings.locTrackStarted(readable),
                keyboard = buildTrackingKeyboard(true, strings),
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
                name = strings.locTrackOpName(readable),
                description = strings.locTrackOpDesc(readable),
                onCancel = { locationRepository.stopLiveTracking() },
            )

            return CommandResult.Text(
                strings.locTrackStarted(readable),
                keyboard = buildTrackingKeyboard(true, strings),
            )
        }

        // No arguments or /track panel — tracking control panel
        val isAct = locationRepository.isLiveTrackingActive()
        val curStatus = locationRepository.liveTrackingStatus.value
        val stateText = strings.locTrackPanelStateInt(isAct, formatIntervalReadable(curStatus.intervalSeconds))

        return CommandResult.Text(
            strings.locTrackPanel(stateText),
            keyboard = buildTrackingKeyboard(isAct, strings),
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
            seconds < 60L -> strings.locDurSec(seconds)
            seconds % 3600L == 0L -> strings.locDurHour(seconds / 3600L)
            seconds % 60L == 0L -> strings.locDurMin(seconds / 60L)
            else -> strings.locDurMinSec(seconds / 60L, seconds % 60L)
        }

    companion object {
        fun formatLocationReport(
            loc: LocationData,
            isLive: Boolean = false,
            strings: BotStrings = TelegramBotStrings.get(BotLanguage.AUTO),
        ): String {
            val gmapsUrl = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            val osmUrl = "https://www.openstreetmap.org/?mlat=${loc.latitude}&mlon=${loc.longitude}#map=17/${loc.latitude}/${loc.longitude}"
            val timeStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(loc.timestampMs))
            val addressText = if (!loc.address.isNullOrBlank()) strings.locAddrBlock(loc.address ?: "") else ""

            val modeHeader =
                if (isLive) {
                    val seqStr = if (loc.trackingSequenceNumber != null) strings.locSeqStr(loc.trackingSequenceNumber.toString()) else ""
                    strings.locTitleTracked(seqStr)
                } else {
                    strings.locTitleDevice()
                }

            // Detection method
            val methodText =
                if (loc.determinationMethod.isNotBlank()) {
                    loc.determinationMethod
                } else if (loc.isDirectGps) {
                    strings.locStateGps()
                } else {
                    strings.locStateLbs()
                }

            // GPS signal
            val signalText =
                buildString {
                    if (loc.isDirectGps) {
                        val lvl = loc.signalStrengthLevel ?: strings.locSignalDefault()
                        append(strings.locSignalGps(lvl))
                        val cn0 = loc.cn0DbHz
                        if (cn0 != null && cn0 > 0f) {
                            append(" (${"%.1f".format(cn0)} dB-Hz)")
                        }
                        if (loc.satellitesUsed != null || loc.satellitesVisible != null) {
                            val used = loc.satellitesUsed ?: 0
                            val total = loc.satellitesVisible ?: used
                            append(strings.locSatsFragment(used.toString(), total.toString()))
                        }
                    } else {
                        append(strings.locSignalGpsLabel())
                        append(loc.signalStrengthLevel ?: strings.locSignalNoGps())
                    }
                }

            val altSpeedText =
                if (loc.altitudeMeters != 0.0 || loc.speedMps > 0f) {
            strings.locAltitudeSpeed("%.1f".format(loc.altitudeMeters), "%.1f".format(loc.speedMps * 3.6f))
                } else {
                    ""
                }

            return strings.locResultBody("${loc.latitude}, ${loc.longitude}", "%.1f".format(loc.accuracyMeters), methodText, timeStr, addressText, gmapsUrl, osmUrl)
        }

        fun buildSingleLocationKeyboard(strings: BotStrings = TelegramBotStrings.get(BotLanguage.AUTO)): InlineKeyboardMarkup {
            val row1 =
                listOf(
                    InlineKeyboardButton(strings.locBtnAgain(), callbackData = "/location"),
                    InlineKeyboardButton(strings.locBtnInstant(), callbackData = "/location last"),
                )
            val row2 =
                listOf(
                    InlineKeyboardButton(strings.locBtnStartTrack(), callbackData = "/track"),
                )
            return InlineKeyboardMarkup(listOf(row1, row2))
        }

        fun buildTrackingKeyboard(isTrackingActive: Boolean, strings: BotStrings = TelegramBotStrings.get(BotLanguage.AUTO)): InlineKeyboardMarkup {
            val row1 =
                listOf(
                    InlineKeyboardButton(strings.locBtn30s(), callbackData = "/track 30s"),
                    InlineKeyboardButton(strings.locBtn1m(), callbackData = "/track 1m"),
                    InlineKeyboardButton(strings.locBtn5m(), callbackData = "/track 5m"),
                )
            val row2 =
                listOf(
                    InlineKeyboardButton(strings.locBtn15m(), callbackData = "/track 15m"),
                    InlineKeyboardButton(strings.locBtn30m(), callbackData = "/track 30m"),
                    InlineKeyboardButton(strings.locBtn60m(), callbackData = "/track 60m"),
                )
            val row3 =
                if (isTrackingActive) {
                    listOf(
                        InlineKeyboardButton(strings.locBtnStopTrack(), callbackData = "/track stop"),
                        InlineKeyboardButton(strings.locBtnStatus(), callbackData = "/track status"),
                    )
                } else {
                    listOf(
                        InlineKeyboardButton(strings.locBtnSingle(), callbackData = "/location"),
                    )
                }
            return InlineKeyboardMarkup(listOf(row1, row2, row3))
        }

        fun buildLocationKeyboard(isTrackingActive: Boolean, strings: BotStrings = TelegramBotStrings.get(BotLanguage.AUTO)): InlineKeyboardMarkup = buildTrackingKeyboard(isTrackingActive, strings)
    }

    private suspend fun handleGps(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            val isEnabled = locationRepository.isGpsEnabled()
            val status = if (isEnabled) strings.locGpsOn() else strings.locGpsOff()
            return CommandResult.Text(strings.locGpsStatus(status))
        }

        val target =
            when (args[0].lowercase()) {
                "on", "1", "true", "enable" -> true
                "off", "0", "false", "disable" -> false
                "toggle" -> !locationRepository.isGpsEnabled()
                else -> return CommandResult.Error(strings.locGpsUsage())
            }

        return locationRepository.setGpsEnabled(target).fold(
            onSuccess = {
                val stateText = strings.locGpsOnOff(target)
                CommandResult.Text(strings.locGpsToggled(stateText))
            },
            onFailure = {
                CommandResult.Error(strings.locGpsToggleError(it.localizedMessage ?: it.message ?: ""))
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
            name = strings.locShakeOpName(lvl.toString(), cd.toString()),
            description = strings.locShakeOpDesc("%.1f".format(threshold), cd.toString()),
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
                val label = strings.locShakeCdLabel(sec.toString(), activeCooldown == sec)
                InlineKeyboardButton(label, callbackData = "/shake_detector cd $sec")
            }

        val rowActions = mutableListOf<InlineKeyboardButton>()
        if (cfg.isEnabled) {
            rowActions.add(InlineKeyboardButton(strings.locShakeBtnOff(), callbackData = "/shake_detector off"))
        } else {
            rowActions.add(
                InlineKeyboardButton(
                    strings.locShakeBtnOn(activeLevel.toString(), activeCooldown.toString()),
                    callbackData = "/shake_detector on $activeLevel $activeCooldown",
                ),
            )
        }
        rowActions.add(InlineKeyboardButton(strings.locShakeBtnRefresh(), callbackData = "/shake_detector panel $activeLevel $activeCooldown"))

        val statusText =
            if (cfg.isEnabled) {
                strings.locShakeActive("%.1f".format(cfg.sensitivity), activeCooldown.toString())
            } else {
                strings.locShakeOff()
            }

        val text =
            strings.locShakePanel(statusText, activeLevel.toString(), "%.1f".format(currentThreshold), activeCooldown.toString())

        return CommandResult.Text(
            message = text,
            keyboard = InlineKeyboardMarkup(listOf(row1, row2, rowCooldown, rowActions)),
        )
    }
}
