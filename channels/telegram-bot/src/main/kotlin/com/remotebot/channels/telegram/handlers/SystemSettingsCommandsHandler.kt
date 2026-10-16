package com.remotebot.channels.telegram.handlers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.provider.RootStatusProvider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemSettingsCommandsHandler(
    private val context: Context,
    private val logger: AppLogger,
    private val rootStatusProvider: RootStatusProvider,
    private val radioHandler: DeviceRadioCommandsHandler? = null,
) : CommandHandler {
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
        radioHandler ?: DeviceRadioCommandsHandler(context, logger, rootStatusProvider)
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
            else -> CommandResult.Error("Неизвестная команда системных настроек: $cmd")
        }
    }

    private fun isRootGranted(): Boolean = SystemControlUtils.isRootGranted(rootStatusProvider)

    // ==========================================
    // 1. ИНТЕРАКТИВНАЯ ПАНЕЛЬ ПЕРЕКЛЮЧАТЕЛЕЙ
    // ==========================================
    private suspend fun handleTogglesPanel(): CommandResult =
        withContext(Dispatchers.IO) {
            val hasRoot = isRootGranted()
            val wifiOn = effectiveRadioHandler.isWifiEnabled()
            val btOn = effectiveRadioHandler.isBluetoothEnabled()
            val flashOn = effectiveRadioHandler.isTorchActive()
            val syncOn = isSyncEnabled()
            val autoBrightnessOn = isAutoBrightnessEnabled()

            val textBuilder = StringBuilder()
            textBuilder.append("🎛 <b>Панель быстрых переключателей (Quick Settings)</b>\n")
            textBuilder.append("<i>Статус Superuser: ${if (hasRoot) "🟢 Предоставлен (UID 0)" else "⚪ Обычный режим (без Root)"}</i>\n\n")

            textBuilder.append("• Wi-Fi: ${if (wifiOn) "🟢 ВКЛ" else "⚪ ВЫКЛ"}\n")
            textBuilder.append("• Bluetooth: ${if (btOn) "🟢 ВКЛ" else "⚪ ВЫКЛ"}\n")
            textBuilder.append("• Фонарик: ${if (flashOn) "🟢 ВКЛ" else "⚪ ВЫКЛ"}\n")
            textBuilder.append("• Автояркость: ${if (autoBrightnessOn) "☀️ ВКЛ" else "⚪ ВЫКЛ"}\n")
            textBuilder.append("• Синхронизация: ${if (syncOn) "🔄 ВКЛ" else "⚪ ВЫКЛ"}\n")

            val buttonRows = mutableListOf<List<InlineKeyboardButton>>()

            // Базовые переключатели (доступны всегда)
            buttonRows.add(
                listOf(
                    InlineKeyboardButton(if (wifiOn) "📶 Wi-Fi [ВКЛ]" else "📶 Wi-Fi [ВЫКЛ]", "/wifi toggle"),
                    InlineKeyboardButton(if (btOn) "🦷 BT [ВКЛ]" else "🦷 BT [ВЫКЛ]", "/bluetooth toggle"),
                ),
            )
            buttonRows.add(
                listOf(
                    InlineKeyboardButton(if (flashOn) "🔦 Фонарик [ВКЛ]" else "🔦 Фонарик [ВЫКЛ]", "/flashlight toggle"),
                    InlineKeyboardButton(if (syncOn) "🔄 Синхр [ВКЛ]" else "🔄 Синхр [ВЫКЛ]", "/sync toggle"),
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
                val localIp = SystemControlUtils.getLocalIpAddress() ?: "IP_НЕ_ОПРЕДЕЛЕН"

                textBuilder.append("• Режим полета: ${if (airplaneOn) "✈️ ВКЛ" else "⚪ ВЫКЛ"} ⚡\n")
                textBuilder.append("• Точка доступа: ${if (hotspotOn) "📡 ВКЛ" else "⚪ ВЫКЛ"} ⚡\n")
                textBuilder.append("• Моб. данные: ${if (mobileDataOn) "📶 ВКЛ" else "⚪ ВЫКЛ"} ⚡\n")
                textBuilder.append("• Геолокация (GPS): ${if (gpsOn) "📍 ВКЛ" else "⚪ ВЫКЛ"} ⚡\n")
                textBuilder.append("• Энергосбережение: ${if (powerSaveOn) "🔋 ВКЛ" else "⚪ ВЫКЛ"} ⚡\n")
                textBuilder.append("• Отладка по USB (ADB): ${if (adbOn) "🔌 ВКЛ" else "⚪ ВЫКЛ"} ⚡\n")
                textBuilder.append("• Отладка по Wi-Fi: ${if (adbWifiOn) "📶 ВКЛ (<code>$localIp:$adbWifiPort</code>)" else "⚪ ВЫКЛ"} ⚡\n")

                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(if (airplaneOn) "✈️ Самолёт [ВКЛ] ⚡" else "✈️ Самолёт [ВЫКЛ] ⚡", "/airplane toggle"),
                        InlineKeyboardButton(if (hotspotOn) "📡 Hotspot [ВКЛ] ⚡" else "📡 Hotspot [ВЫКЛ] ⚡", "/hotspot toggle"),
                    ),
                )
                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(if (mobileDataOn) "📱 Данные [ВКЛ] ⚡" else "📱 Данные [ВЫКЛ] ⚡", "/mobile_data toggle"),
                        InlineKeyboardButton(if (gpsOn) "📍 GPS [ВКЛ] ⚡" else "📍 GPS [ВЫКЛ] ⚡", "/gps toggle"),
                    ),
                )
                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(if (powerSaveOn) "🔋 Энерго [ВКЛ] ⚡" else "🔋 Энерго [ВЫКЛ] ⚡", "/powersave toggle"),
                        InlineKeyboardButton(if (autoBrightnessOn) "☀️ Автоярк [ВКЛ]" else "☀️ Автоярк [ВЫКЛ]", "/auto_brightness toggle"),
                    ),
                )
                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(if (adbOn) "🔌 USB ADB [ВКЛ] ⚡" else "🔌 USB ADB [ВЫКЛ] ⚡", "/adb toggle"),
                        InlineKeyboardButton(if (adbWifiOn) "📶 ADB-WiFi [ВКЛ] ⚡" else "📶 ADB-WiFi [ВЫКЛ] ⚡", "/adb_wifi toggle"),
                    ),
                )
            } else {
                textBuilder.append(
                    "\n<i>💡 Предоставьте Root в приложении (вкладка Диагностика), чтобы разблокировать тумблеры: Самолёт, Hotspot, Моб. данные, GPS, Энергосбережение, USB/Wi-Fi Debugging.</i>\n",
                )
                buttonRows.add(
                    listOf(
                        InlineKeyboardButton(if (autoBrightnessOn) "☀️ Автоярк [ВКЛ]" else "☀️ Автоярк [ВЫКЛ]", "/auto_brightness toggle"),
                    ),
                )
            }

            buttonRows.add(
                listOf(
                    InlineKeyboardButton("🔄 Обновить панель", "/toggles"),
                ),
            )

            val keyboard = InlineKeyboardMarkup(buttonRows)
            CommandResult.Text(textBuilder.toString().trimEnd(), keyboard)
        }

    // ==========================================
    // 2. ЭКОНОМИЯ ЭНЕРГИИ (POWER SAVE) [ТРЕБУЕТ ROOT]
    // ==========================================
    private suspend fun handlePowerSave(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val action = args.firstOrNull()?.lowercase() ?: "status"
            val hasRoot = isRootGranted()

            when (action) {
                "status" -> {
                    val isEnabled = isPowerSaveModeOn()
                    val statusEmoji = if (isEnabled) "🔋 Включен" else "⚪ Выключен"
                    val rootNote = if (hasRoot) "🟢 Root активен" else "🔒 Требуются Root-права для изменения"
                    CommandResult.Text(
                        "🔋 <b>Режим энергосбережения:</b> $statusEmoji\n<i>$rootNote</i>",
                        SystemControlUtils.makeSingleToggleKeyboard("/powersave", isEnabled, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                "on", "1" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для управления энергосбережением требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setPowerSave(true)
                    if (success) {
                        CommandResult.Text(
                            "🔋 <b>Режим энергосбережения включен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/powersave", true, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка включения энергосбережения.")
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для управления энергосбережением требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setPowerSave(false)
                    if (success) {
                        CommandResult.Text(
                            "⚪ <b>Режим энергосбережения выключен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/powersave", false, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка выключения энергосбережения.")
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для управления энергосбережением требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val current = isPowerSaveModeOn()
                    val target = !current
                    val success = setPowerSave(target)
                    if (success) {
                        val label = if (target) "включен 🔋" else "выключен ⚪"
                        CommandResult.Text(
                            "🔄 <b>Режим энергосбережения $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/powersave", target, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка переключения энергосбережения.")
                    }
                }
                else -> CommandResult.Error("Используйте: <code>/powersave [on|off|toggle|status]</code>")
            }
        }

    private fun isPowerSaveModeOn(): Boolean = SystemControlUtils.isPowerSaveModeOn(context)

    private fun setPowerSave(enable: Boolean): Boolean {
        val mode = if (enable) "1" else "0"
        val cmd = "cmd power set-mode $mode || settings put global low_power $mode"
        return try {
            val res = Shell.cmd(cmd).exec()
            logger.i("SystemControl", "PowerSave изменен: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "Сбой setPowerSave: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // 3. АВТОЯРКОСТЬ (AUTO BRIGHTNESS)
    // ==========================================
    private suspend fun handleAutoBrightness(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val action = args.firstOrNull()?.lowercase() ?: "status"

            when (action) {
                "status" -> {
                    val isEnabled = isAutoBrightnessEnabled()
                    val statusEmoji = if (isEnabled) "☀️ Включена" else "⚪ Выключена (Ручная)"
                    CommandResult.Text(
                        "☀️ <b>Автояркость экрана:</b> $statusEmoji",
                        SystemControlUtils.makeSingleToggleKeyboard("/auto_brightness", isEnabled, hasRoot = isRootGranted()),
                    )
                }
                "on", "1" -> {
                    val success = setAutoBrightness(true)
                    if (success) {
                        CommandResult.Text(
                            "☀️ <b>Автояркость включена!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/auto_brightness", true, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error("⚠️ Не удалось включить автояркость.\nТребуются права изменения системных настроек или Root.")
                    }
                }
                "off", "0" -> {
                    val success = setAutoBrightness(false)
                    if (success) {
                        CommandResult.Text(
                            "⚪ <b>Автояркость выключена (ручной режим)!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/auto_brightness", false, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error("⚠️ Не удалось выключить автояркость.\nТребуются права изменения системных настроек или Root.")
                    }
                }
                "toggle" -> {
                    val current = isAutoBrightnessEnabled()
                    val target = !current
                    val success = setAutoBrightness(target)
                    if (success) {
                        val label = if (target) "включена ☀️" else "выключена ⚪"
                        CommandResult.Text(
                            "🔄 <b>Автояркость $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/auto_brightness", target, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error(
                            "⚠️ Не удалось переключить автояркость.\nТребуются права изменения системных настроек или Root.",
                        )
                    }
                }
                else -> CommandResult.Error("Используйте: <code>/auto_brightness [on|off|toggle|status]</code>")
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
                logger.i("SystemControl", "Автояркость изменена через shell: enable=$enable")
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
            logger.w("SystemControl", "Сбой setAutoBrightness: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // 4. СИНХРОНИЗАЦИЯ (SYNC)
    // ==========================================
    private fun handleSync(args: List<String>): CommandResult {
        val action = args.firstOrNull()?.lowercase() ?: "status"

        return when (action) {
            "status" -> {
                val isSync = isSyncEnabled()
                val status = if (isSync) "🟢 Включена" else "🔴 Выключена"
                CommandResult.Text(
                    "🔄 <b>Автосинхронизация аккаунтов:</b> $status",
                    SystemControlUtils.makeSingleToggleKeyboard("/sync", isSync, hasRoot = isRootGranted()),
                )
            }
            "on", "1" -> {
                ContentResolver.setMasterSyncAutomatically(true)
                logger.i("SystemControl", "Автосинхронизация включена")
                CommandResult.Text(
                    "✅ <b>Автосинхронизация аккаунтов включена!</b>",
                    SystemControlUtils.makeSingleToggleKeyboard("/sync", true, hasRoot = isRootGranted()),
                )
            }
            "off", "0" -> {
                ContentResolver.setMasterSyncAutomatically(false)
                logger.i("SystemControl", "Автосинхронизация выключена")
                CommandResult.Text(
                    "🔴 <b>Автосинхронизация аккаунтов выключена!</b>",
                    SystemControlUtils.makeSingleToggleKeyboard("/sync", false, hasRoot = isRootGranted()),
                )
            }
            "toggle" -> {
                val current = isSyncEnabled()
                val target = !current
                ContentResolver.setMasterSyncAutomatically(target)
                val label = if (target) "включена" else "выключена"
                logger.i("SystemControl", "Автосинхронизация переключена: $label")
                CommandResult.Text(
                    "🔄 <b>Автосинхронизация аккаунтов $label!</b>",
                    SystemControlUtils.makeSingleToggleKeyboard("/sync", target, hasRoot = isRootGranted()),
                )
            }
            else -> CommandResult.Error("Используйте: <code>/sync [on|off|toggle|status]</code>")
        }
    }

    private fun isSyncEnabled(): Boolean = ContentResolver.getMasterSyncAutomatically()

    // ==========================================
    // 5. БУФЕР ОБМЕНА (CLIPBOARD)
    // ==========================================
    private suspend fun handleClipboard(args: List<String>): CommandResult =
        withContext(Dispatchers.Main) {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@withContext CommandResult.Error("Буфер обмена недоступен.")

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
                    CommandResult.Text("📋 <b>Буфер обмена пуст.</b>")
                } else {
                    val keyboard =
                        InlineKeyboardMarkup(
                            listOf(listOf(InlineKeyboardButton("🗑 Очистить буфер", "/clipboard clear"))),
                        )
                    CommandResult.Text("📋 <b>Содержимое буфера обмена:</b>\n<code>$text</code>", keyboard)
                }
            } else if (textArg.equals("clear", ignoreCase = true)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                logger.i("SystemControl", "Буфер обмена очищен")
                CommandResult.Text("🗑 <b>Буфер обмена очищен!</b>")
            } else {
                val toSet = if (textArg.startsWith("set ", ignoreCase = true)) textArg.substring(4).trim() else textArg
                clipboardManager.setPrimaryClip(ClipData.newPlainText("RemoteBot", toSet))
                logger.i("SystemControl", "В буфер обмена скопирован текст (${toSet.length} симв)")
                val keyboard =
                    InlineKeyboardMarkup(
                        listOf(listOf(InlineKeyboardButton("🗑 Очистить буфер", "/clipboard clear"))),
                    )
                CommandResult.Text("📋 <b>Текст успешно скопирован в буфер устройства:</b>\n<code>$toSet</code>", keyboard)
            }
        }

    // ==========================================
    // 6. ОТЛАДКА ПО USB (ADB)
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
                    "❌ <b>Требуются права Superuser (Root)!</b>\n" +
                        "Управление режимом отладки USB (ADB) невозможно без прав root.\n" +
                        "Предоставьте доступ в приложении на вкладке «Диагностика».",
                )
            }

            val action = args.firstOrNull()?.lowercase() ?: "status"
            when (action) {
                "status" -> {
                    val isEnabled = isAdbEnabled()
                    val statusEmoji = if (isEnabled) "🟢 Включен" else "🔴 Выключен"
                    val text =
                        """
                        🔌 <b>Режим отладки по USB (ADB):</b> $statusEmoji
                        • Доступ: <code>Root (Superuser)</code>
                        • Глобальная настройка: <code>adb_enabled = ${if (isEnabled) 1 else 0}</code>
                        """.trimIndent()
                    CommandResult.Text(
                        text,
                        SystemControlUtils.makeSingleToggleKeyboard("/adb", isEnabled, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                "on", "enable", "1" -> {
                    setAdbState(true)
                    logger.i("SystemControl", "Режим отладки по USB (ADB) включен через Root")
                    CommandResult.Text(
                        "✅ <b>Режим отладки по USB (ADB) включен!</b> ⚡",
                        SystemControlUtils.makeSingleToggleKeyboard("/adb", true, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                "off", "disable", "0" -> {
                    setAdbState(false)
                    logger.i("SystemControl", "Режим отладки по USB (ADB) выключен через Root")
                    CommandResult.Text(
                        "🔴 <b>Режим отладки по USB (ADB) выключен.</b> ⚡",
                        SystemControlUtils.makeSingleToggleKeyboard("/adb", false, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                "toggle" -> {
                    val currentlyOn = isAdbEnabled()
                    val newState = !currentlyOn
                    setAdbState(newState)
                    val msg = if (newState) "✅ <b>Режим отладки по USB (ADB) включен!</b> ⚡" else "🔴 <b>Режим отладки по USB (ADB) выключен.</b> ⚡"
                    logger.i("SystemControl", "Переключен режим ADB: $newState")
                    CommandResult.Text(
                        msg,
                        SystemControlUtils.makeSingleToggleKeyboard("/adb", newState, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                else -> CommandResult.Error("Использование: <code>/adb [status|on|off|toggle]</code>")
            }
        }

    // ==========================================
    // 7. ОТЛАДКА ПО WI-FI (WIRELESS ADB)
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
                    "❌ <b>Требуются права Superuser (Root)!</b>\n" +
                        "Включение отладки по Wi-Fi на системном уровне требует прав root.\n" +
                        "Предоставьте доступ в приложении на вкладке «Диагностика».",
                )
            }

            val firstArg = args.firstOrNull()?.lowercase() ?: "status"
            val customPort = args.getOrNull(1)?.toIntOrNull() ?: firstArg.toIntOrNull() ?: 5555

            when {
                firstArg == "status" -> {
                    val isEnabled = isAdbWifiEnabled()
                    val port = if (isEnabled) (getAdbWifiPort().takeIf { it > 0 } ?: 5555) else 5555
                    val ip = SystemControlUtils.getLocalIpAddress() ?: "IP_НЕ_ОПРЕДЕЛЕН"
                    val statusEmoji = if (isEnabled) "🟢 Включена (порт $port)" else "🔴 Выключена"

                    val text =
                        if (isEnabled) {
                            """
                            📶 <b>Режим отладки по Wi-Fi (Wireless ADB):</b> $statusEmoji
                            • Локальный IP: <code>$ip</code>
                            • Порт: <code>$port</code>
                            
                            💡 <b>Команда подключения с ПК:</b>
                            <code>adb connect $ip:$port</code>
                            
                            💡 <b>Команда отключения:</b>
                            <code>adb disconnect $ip:$port</code>
                            """.trimIndent()
                        } else {
                            """
                            📶 <b>Режим отладки по Wi-Fi (Wireless ADB):</b> $statusEmoji
                            • Порт по умолчанию: <code>5555</code>
                            • Текущий IP: <code>$ip</code>
                            
                            Используйте кнопку ниже или команду <code>/adb_wifi on</code> для запуска ADB через TCP/IP.
                            """.trimIndent()
                        }
                    CommandResult.Text(
                        text,
                        SystemControlUtils.makeSingleToggleKeyboard("/adb_wifi", isEnabled, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                firstArg in listOf("on", "enable", "1") || firstArg.toIntOrNull() != null -> {
                    setAdbWifiState(true, customPort)
                    val ip = SystemControlUtils.getLocalIpAddress() ?: "IP_НЕ_ОПРЕДЕЛЕН"
                    logger.i("SystemControl", "Отладка по Wi-Fi включена на порту $customPort")
                    val text =
                        """
                        ✅ <b>Отладка по Wi-Fi успешно включена!</b> ⚡
                        • Порт: <code>$customPort</code>
                        • IP-адрес устройства: <code>$ip</code>
                        
                        💡 <b>Для подключения с компьютера выполните:</b>
                        <code>adb connect $ip:$customPort</code>
                        """.trimIndent()
                    CommandResult.Text(
                        text,
                        SystemControlUtils.makeSingleToggleKeyboard("/adb_wifi", true, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                firstArg in listOf("off", "disable", "0") -> {
                    setAdbWifiState(false)
                    logger.i("SystemControl", "Отладка по Wi-Fi выключена")
                    CommandResult.Text(
                        "🔴 <b>Отладка по Wi-Fi выключена.</b> ⚡",
                        SystemControlUtils.makeSingleToggleKeyboard("/adb_wifi", false, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                firstArg == "toggle" -> {
                    val currentlyOn = isAdbWifiEnabled()
                    val newState = !currentlyOn
                    setAdbWifiState(newState, customPort)
                    val ip = SystemControlUtils.getLocalIpAddress() ?: "IP_НЕ_ОПРЕДЕЛЕН"
                    val msg =
                        if (newState) {
                            """
                            ✅ <b>Отладка по Wi-Fi успешно включена!</b> ⚡
                            • Порт: <code>$customPort</code>
                            • IP-адрес: <code>$ip</code>
                            
                            💡 <b>Подключение:</b> <code>adb connect $ip:$customPort</code>
                            """.trimIndent()
                        } else {
                            "🔴 <b>Отладка по Wi-Fi выключена.</b> ⚡"
                        }
                    logger.i("SystemControl", "Переключена отладка Wi-Fi: $newState")
                    CommandResult.Text(
                        msg,
                        SystemControlUtils.makeSingleToggleKeyboard("/adb_wifi", newState, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                else ->
                    CommandResult.Error(
                        "Использование: <code>/adb_wifi [status|on|off|toggle|порт]</code>\nПример: <code>/adb_wifi on 5555</code>",
                    )
            }
        }
}
