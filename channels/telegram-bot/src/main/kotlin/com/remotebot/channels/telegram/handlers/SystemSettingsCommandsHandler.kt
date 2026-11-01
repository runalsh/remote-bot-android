package com.remotebot.channels.telegram.handlers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.BotLanguage
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemSettingsCommandsHandler(
    private val context: Context,
    private val logger: AppLogger,
    private val rootStatusProvider: RootStatusProvider,
    private val radioHandler: DeviceRadioCommandsHandler? = null,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

    override val commandNames: List<String> =
        listOf(
            "toggles",
            "quick_settings",
            "panel",
            "powersave",
            "battery_saver",
            "auto_brightness",
            "clipboard",
            "sync",
            "adb",
            "usb_debugging",
            "adb_wifi",
            "adbwifi",
            "wireless_adb",
        )

    override val isDestructive: Boolean = false

    private val effectiveRadioHandler by lazy {
        radioHandler ?: DeviceRadioCommandsHandler(context, logger, rootStatusProvider, botConfigRepository)
    }

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "toggles", "quick_settings", "panel" -> handleTogglesPanel()
            "powersave", "battery_saver" -> handlePowerSave(command.args)
            "auto_brightness" -> handleAutoBrightness(command.args)
            "clipboard" -> handleClipboard(command.args)
            "sync" -> handleSync(command.args)
            "adb", "usb_debugging" -> handleAdb(command.args)
            "adb_wifi", "adbwifi", "wireless_adb" -> handleAdbWifi(command.args)
            else -> CommandResult.Error(strings.qsUnknownCmd(cmd))
        }
    }

    private fun isRootGranted(): Boolean = SystemControlUtils.isRootGranted(rootStatusProvider)

    private fun isEnglish(): Boolean {
        val lang = botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO
        return when (lang) {
            BotLanguage.EN -> true
            BotLanguage.RU -> false
            BotLanguage.AUTO ->
                !java.util.Locale
                    .getDefault()
                    .language
                    .equals("ru", ignoreCase = true)
        }
    }

    // ==========================================
    // 1. INTERACTIVE TOGGLE SWITCHES PANEL
    // ==========================================
    private suspend fun handleTogglesPanel(): CommandResult =
        withContext(Dispatchers.IO) {
            val isEn = isEnglish()
            val hasRoot = isRootGranted()
            val wifiOn = effectiveRadioHandler.isWifiEnabled()
            val btOn = effectiveRadioHandler.isBluetoothEnabled()
            val flashOn = effectiveRadioHandler.isTorchActive()
            val syncOn = isSyncEnabled()
            val autoBrightnessOn = isAutoBrightnessEnabled()

            val onLabel = strings.qsOnWord()
            val offLabel = strings.qsOffWord()

            val textBuilder = StringBuilder()
            textBuilder.append(strings.qsPanelBase(hasRoot, wifiOn, btOn, flashOn, autoBrightnessOn, syncOn))

            val buttonRows = mutableListOf<List<InlineKeyboardButton>>()

            // Basic switches (always available)
            val wifiBtn = if (wifiOn) "📶 Wi-Fi [$onLabel]" else "📶 Wi-Fi [$offLabel]"
            val btBtn = if (btOn) "🦷 BT [$onLabel]" else "🦷 BT [$offLabel]"
            val flashBtn = strings.qsBtnTorch(flashOn)
            val syncBtn = strings.qsBtnSync(syncOn)

            buttonRows.add(
                listOf(
                    InlineKeyboardButton(wifiBtn, "/wifi toggle"),
                    InlineKeyboardButton(btBtn, "/bluetooth toggle"),
                ),
            )
            buttonRows.add(
                listOf(
                    InlineKeyboardButton(flashBtn, "/flashlight toggle"),
                    InlineKeyboardButton(syncBtn, "/sync toggle"),
                ),
            )

            if (hasRoot) {
                val airplaneOn = effectiveRadioHandler.isAirplaneModeOn()
                val hotspotOn = effectiveRadioHandler.isHotspotEnabled()
                val mobileDataOn = effectiveRadioHandler.isMobileDataEnabled()
                val gpsOn = effectiveRadioHandler.isGpsEnabled()
                val powerSaveOn = isPowerSaveModeOn()
                val adbOn = isAdbEnabled()
                val adbWifiOn = isAdbWifiEnabled()
                val adbWifiPort = if (adbWifiOn) getAdbWifiPort().takeIf { it > 0 } ?: 5555 else 5555
                val localIp = SystemControlUtils.getLocalIpAddress() ?: strings.ipNotFound()

                textBuilder.append(
                    strings.qsPanelRoot(airplaneOn, hotspotOn, mobileDataOn, gpsOn, powerSaveOn, adbOn, adbWifiOn, localIp, adbWifiPort),
                )

                val airplaneBtn = strings.qsBtnAirplane(airplaneOn)
                val hotspotBtn = if (hotspotOn) "📡 Hotspot [$onLabel] ⚡" else "📡 Hotspot [$offLabel] ⚡"
                val dataBtn = strings.qsBtnData(mobileDataOn)
                val gpsBtn = if (gpsOn) "📍 GPS [$onLabel] ⚡" else "📍 GPS [$offLabel] ⚡"
                val powerBtn = strings.qsBtnSaver(powerSaveOn)
                val brightBtn = strings.qsBtnBright(autoBrightnessOn)

                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(airplaneBtn, "/airplane toggle"),
                        InlineKeyboardButton(hotspotBtn, "/hotspot toggle"),
                    ),
                )
                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(dataBtn, "/mobile_data toggle"),
                        InlineKeyboardButton(gpsBtn, "/gps toggle"),
                    ),
                )
                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(powerBtn, "/powersave toggle"),
                        InlineKeyboardButton(brightBtn, "/auto_brightness toggle"),
                    ),
                )
                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(if (adbOn) "🔌 USB ADB [$onLabel] ⚡" else "🔌 USB ADB [$offLabel] ⚡", "/adb toggle"),
                        InlineKeyboardButton(
                            if (adbWifiOn) "📶 ADB-WiFi [$onLabel] ⚡" else "📶 ADB-WiFi [$offLabel] ⚡",
                            "/adb_wifi toggle",
                        ),
                    ),
                )
            } else {
                val tip = strings.qsRootTip()
                textBuilder.append(tip)
                val brightBtn = strings.qsBtnBright(autoBrightnessOn)
                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(brightBtn, "/auto_brightness toggle"),
                    ),
                )
            }

            buttonRows.add(
                listOf(
                    InlineKeyboardButton(strings.qsBtnRefresh(), "/toggles"),
                ),
            )

            val keyboard = InlineKeyboardMarkup(buttonRows)
            CommandResult.Text(textBuilder.toString().trimEnd(), keyboard)
        }

    // ==========================================
    // 2. POWER SAVE [REQUIRES ROOT]
    // ==========================================
    private suspend fun handlePowerSave(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val action = args.firstOrNull()?.lowercase() ?: "status"
            val hasRoot = isRootGranted()

            when (action) {
                "status" -> {
                    val isEnabled = isPowerSaveModeOn()
                    val statusEmoji = if (isEnabled) strings.psStateOn() else strings.radioStateOffW()
                    val rootNote = if (hasRoot) strings.radioRootActive() else strings.radioRootNote()
                    CommandResult.Text(
                        strings.psStatus(statusEmoji, rootNote),
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/powersave",
                            isEnabled,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                "on", "1" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.psNeedRoot(),
                        )
                    }
                    val success = setPowerSave(true)
                    if (success) {
                        CommandResult.Text(
                            strings.psOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/powersave",
                                true,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.psOnFail())
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.psNeedRoot(),
                        )
                    }
                    val success = setPowerSave(false)
                    if (success) {
                        CommandResult.Text(
                            strings.psOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/powersave",
                                false,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.psOffFail())
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.psNeedRoot(),
                        )
                    }
                    val current = isPowerSaveModeOn()
                    val target = !current
                    val success = setPowerSave(target)
                    if (success) {
                        val label = if (target) strings.radioWordOn() + " 🔋" else strings.radioWordOff() + " ⚪"
                        CommandResult.Text(
                            strings.psToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/powersave",
                                target,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.psToggleFail())
                    }
                }
                else -> CommandResult.Error(strings.radioUsage("/powersave [on|off|toggle|status]"))
            }
        }

    private fun isPowerSaveModeOn(): Boolean = SystemControlUtils.isPowerSaveModeOn(context)

    private fun setPowerSave(enable: Boolean): Boolean {
        val mode = if (enable) "1" else "0"
        val cmd = "cmd power set-mode $mode || settings put global low_power $mode"
        return try {
            val res = Shell.cmd(cmd).exec()
            logger.i("SystemControl", "PowerSave changed: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "setPowerSave failed: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // 3. AUTO BRIGHTNESS
    // ==========================================
    private suspend fun handleAutoBrightness(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val action = args.firstOrNull()?.lowercase() ?: "status"

            when (action) {
                "status" -> {
                    val isEnabled = isAutoBrightnessEnabled()
                    val statusEmoji = if (isEnabled) strings.abStateOn() else strings.abStateOff()
                    CommandResult.Text(
                        strings.abStatus(statusEmoji),
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/auto_brightness",
                            isEnabled,
                            hasRoot = isRootGranted(),
                            strings = strings,
                        ),
                    )
                }
                "on", "1" -> {
                    val success = setAutoBrightness(true)
                    if (success) {
                        CommandResult.Text(
                            strings.abOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/auto_brightness",
                                true,
                                hasRoot = isRootGranted(),
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.abOnFail())
                    }
                }
                "off", "0" -> {
                    val success = setAutoBrightness(false)
                    if (success) {
                        CommandResult.Text(
                            strings.abOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/auto_brightness",
                                false,
                                hasRoot = isRootGranted(),
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.abOffFail())
                    }
                }
                "toggle" -> {
                    val current = isAutoBrightnessEnabled()
                    val target = !current
                    val success = setAutoBrightness(target)
                    if (success) {
                        val label = if (target) strings.radioWordOnF() + " ☀️" else strings.radioWordOffF() + " ⚪"
                        CommandResult.Text(
                            strings.abToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/auto_brightness",
                                target,
                                hasRoot = isRootGranted(),
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(
                            strings.abToggleFail(),
                        )
                    }
                }
                else -> CommandResult.Error(strings.radioUsage("/auto_brightness [on|off|toggle|status]"))
            }
        }

    private fun isAutoBrightnessEnabled(): Boolean =
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun setAutoBrightness(enable: Boolean): Boolean {
        val mode = if (enable) "1" else "0"
        val cmd = "settings put system screen_brightness_mode $mode"
        try {
            val res = Shell.cmd(cmd).exec()
            if (res.isSuccess) {
                logger.i("SystemControl", "Auto-brightness changed via shell: enable=$enable")
                return true
            }
        } catch (_: Exception) {
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (enable) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.w("SystemControl", "setAutoBrightness failed: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // 4. SYNC
    // ==========================================
    private fun handleSync(args: List<String>): CommandResult {
        val action = args.firstOrNull()?.lowercase() ?: "status"

        return when (action) {
            "status" -> {
                val isSync = isSyncEnabled()
                val status = if (isSync) strings.radioStateDataOn() else strings.radioStateDataOff()
                CommandResult.Text(
                    strings.syncStatus(status),
                    SystemControlUtils.makeSingleToggleKeyboard("/sync", isSync, hasRoot = isRootGranted(), strings = strings),
                )
            }
            "on", "1" -> {
                ContentResolver.setMasterSyncAutomatically(true)
                logger.i("SystemControl", "Auto-sync enabled")
                CommandResult.Text(
                    strings.syncOnOk(),
                    SystemControlUtils.makeSingleToggleKeyboard("/sync", true, hasRoot = isRootGranted(), strings = strings),
                )
            }
            "off", "0" -> {
                ContentResolver.setMasterSyncAutomatically(false)
                logger.i("SystemControl", "Auto-sync disabled")
                CommandResult.Text(
                    strings.syncOffOk(),
                    SystemControlUtils.makeSingleToggleKeyboard("/sync", false, hasRoot = isRootGranted(), strings = strings),
                )
            }
            "toggle" -> {
                val current = isSyncEnabled()
                val target = !current
                ContentResolver.setMasterSyncAutomatically(target)
                val label = if (target) strings.radioWordOnF() else strings.radioWordOffF()
                logger.i("SystemControl", "Auto-sync toggled: $label")
                CommandResult.Text(
                    strings.syncToggled(label),
                    SystemControlUtils.makeSingleToggleKeyboard("/sync", target, hasRoot = isRootGranted(), strings = strings),
                )
            }
            else -> CommandResult.Error(strings.radioUsage("/sync [on|off|toggle|status]"))
        }
    }

    private fun isSyncEnabled(): Boolean = ContentResolver.getMasterSyncAutomatically()

    // ==========================================
    // 5. CLIPBOARD
    // ==========================================
    private suspend fun handleClipboard(args: List<String>): CommandResult =
        withContext(Dispatchers.Main) {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@withContext CommandResult.Error(strings.cbUnavailable())

            val textArg = args.joinToString(" ").trim()

            if (textArg.isBlank() || textArg.equals("get", ignoreCase = true)) {
                val clip = clipboardManager.primaryClip
                val text =
                    if (clip != null && clip.itemCount > 0) {
                        clip.getItemAt(0).text?.toString()
                    } else {
                        null
                    }

                if (text.isNullOrBlank()) {
                    CommandResult.Text(strings.cbEmpty())
                } else {
                    val keyboard =
                        InlineKeyboardMarkup(
                            listOf(listOf(InlineKeyboardButton(strings.cbClearBtn(), "/clipboard clear"))),
                        )
                    CommandResult.Text(strings.cbContent(text), keyboard)
                }
            } else if (textArg.equals("clear", ignoreCase = true)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                logger.i("SystemControl", "Clipboard cleared")
                CommandResult.Text("🗑 <b>Clipboard cleared!</b>")
            } else {
                val toSet = if (textArg.startsWith("set ", ignoreCase = true)) textArg.substring(4).trim() else textArg
                clipboardManager.setPrimaryClip(ClipData.newPlainText("RemoteBot", toSet))
                logger.i("SystemControl", "Text copied to clipboard (${toSet.length} chars)")
                val keyboard =
                    InlineKeyboardMarkup(
                        listOf(listOf(InlineKeyboardButton(strings.cbClearBtn(), "/clipboard clear"))),
                    )
                CommandResult.Text(strings.cbSet(toSet), keyboard)
            }
        }

    // ==========================================
    // 6. USB DEBUGGING (ADB)
    // ==========================================
    private fun isAdbEnabled(): Boolean =
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            val prop =
                Shell
                    .cmd("getprop init.svc.adbd")
                    .exec()
                    .out
                    .firstOrNull()
                    ?.trim()
            prop == "running"
        }

    private fun setAdbState(enable: Boolean): Boolean {
        val intVal = if (enable) 1 else 0
        Shell
            .cmd(
                "settings put global adb_enabled $intVal",
                "setprop persist.sys.usb.config ${if (enable) "adb" else "none"}",
            ).exec()
        return true
    }

    private suspend fun handleAdb(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val hasRoot = isRootGranted()
            if (!hasRoot) {
                return@withContext CommandResult.Error(
                    strings.adbNeedRoot(),
                )
            }

            val action = args.firstOrNull()?.lowercase() ?: "status"
            when (action) {
                "status" -> {
                    val isEnabled = isAdbEnabled()
                    val statusEmoji = if (isEnabled) strings.radioStateOnG() else strings.radioStateOffR()
                    val text =
                        strings.adbStatus(statusEmoji, if (isEnabled) 1 else 0)
                    CommandResult.Text(
                        text,
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/adb",
                            isEnabled,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                "on", "enable", "1" -> {
                    setAdbState(true)
                    logger.i("SystemControl", "USB debugging (ADB) enabled via Root")
                    CommandResult.Text(
                        strings.adbOnOk(),
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/adb",
                            true,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                "off", "disable", "0" -> {
                    setAdbState(false)
                    logger.i("SystemControl", "USB debugging (ADB) disabled via Root")
                    CommandResult.Text(
                        strings.adbOffOk(),
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/adb",
                            false,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                "toggle" -> {
                    val currentlyOn = isAdbEnabled()
                    val newState = !currentlyOn
                    setAdbState(newState)
                    val msg = if (newState) strings.adbOnOk() else strings.adbOffOk()
                    logger.i("SystemControl", "ADB mode toggled: $newState")
                    CommandResult.Text(
                        msg,
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/adb",
                            newState,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                else -> CommandResult.Error(strings.adbUsage())
            }
        }

    // ==========================================
    // 7. WIRELESS ADB
    // ==========================================
    private fun getAdbWifiPort(): Int {
        val portProp =
            Shell
                .cmd("getprop service.adb.tcp.port")
                .exec()
                .out
                .firstOrNull()
                ?.trim()
        val port = portProp?.toIntOrNull() ?: -1
        return if (port > 0) port else -1
    }

    private fun isAdbWifiEnabled(): Boolean {
        val port = getAdbWifiPort()
        if (port > 0) return true
        return try {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    private fun setAdbWifiState(
        enable: Boolean,
        port: Int = 5555,
    ): Boolean {
        if (enable) {
            Shell
                .cmd(
                    "setprop service.adb.tcp.port $port",
                    "settings put global adb_enabled 1",
                    "settings put global adb_wifi_enabled 1",
                    "stop adbd",
                    "start adbd",
                ).exec()
        } else {
            Shell
                .cmd(
                    "setprop service.adb.tcp.port -1",
                    "settings put global adb_wifi_enabled 0",
                    "stop adbd",
                    "start adbd",
                ).exec()
        }
        return true
    }

    private suspend fun handleAdbWifi(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val hasRoot = isRootGranted()
            if (!hasRoot) {
                return@withContext CommandResult.Error(
                    strings.awifiNeedRoot(),
                )
            }

            val firstArg = args.firstOrNull()?.lowercase() ?: "status"
            val customPort = args.getOrNull(1)?.toIntOrNull() ?: firstArg.toIntOrNull() ?: 5555

            when {
                firstArg == "status" -> {
                    val isEnabled = isAdbWifiEnabled()
                    val port = if (isEnabled) (getAdbWifiPort().takeIf { it > 0 } ?: 5555) else 5555
                    val ip = SystemControlUtils.getLocalIpAddress() ?: strings.ipNotFound()
                    val statusEmoji = if (isEnabled) strings.awifiStateOn(port) else strings.radioStateDataOff()

                    val text =
                        if (isEnabled) {
                            strings.awifiStatusOn(statusEmoji, ip, port)
                        } else {
                            strings.awifiStatusOff(statusEmoji, ip)
                        }
                    CommandResult.Text(
                        text,
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/adb_wifi",
                            isEnabled,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                firstArg in listOf("on", "enable", "1") || firstArg.toIntOrNull() != null -> {
                    setAdbWifiState(true, customPort)
                    val ip = SystemControlUtils.getLocalIpAddress() ?: strings.ipNotFound()
                    logger.i("SystemControl", "Wi-Fi debugging enabled on port $customPort")
                    val text =
                        strings.awifiOnOkA(customPort, ip)
                    CommandResult.Text(
                        text,
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/adb_wifi",
                            true,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                firstArg in listOf("off", "disable", "0") -> {
                    setAdbWifiState(false)
                    logger.i("SystemControl", "Wi-Fi debugging disabled")
                    CommandResult.Text(
                        "🔴 <b>Wi-Fi debugging disabled.</b> ⚡",
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/adb_wifi",
                            false,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                firstArg == "toggle" -> {
                    val currentlyOn = isAdbWifiEnabled()
                    val newState = !currentlyOn
                    setAdbWifiState(newState, customPort)
                    val ip = SystemControlUtils.getLocalIpAddress() ?: strings.ipNotFound()
                    val msg =
                        if (newState) {
                            strings.awifiOnOkB(customPort, ip)
                        } else {
                            "🔴 <b>Wi-Fi debugging disabled.</b> ⚡"
                        }
                    logger.i("SystemControl", "Wi-Fi debugging toggled: $newState")
                    CommandResult.Text(
                        msg,
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/adb_wifi",
                            newState,
                            requiresRoot = true,
                            hasRoot = hasRoot,
                            strings = strings,
                        ),
                    )
                }
                else ->
                    CommandResult.Error(
                        strings.awifiUsage(),
                    )
            }
        }
}
