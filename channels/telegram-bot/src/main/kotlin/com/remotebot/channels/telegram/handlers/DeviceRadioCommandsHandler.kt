package com.remotebot.channels.telegram.handlers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.provider.RootStatusProvider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRadioCommandsHandler(
    private val context: Context,
    private val logger: AppLogger,
    private val rootStatusProvider: RootStatusProvider,
) : CommandHandler {
    override val commandNames: List<String> =
        listOf(
            "wifi",
            "bluetooth",
            "flashlight",
            "airplane",
            "hotspot",
            "tethering",
            "mobile_data",
            "data",
            "gps",
            "location_mode",
        )

    override val isDestructive: Boolean = false

    private var isTorchOn: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "wifi" -> handleWifi(command.args)
            "bluetooth" -> handleBluetooth(command.args)
            "flashlight" -> handleFlashlight(command.args)
            "airplane" -> handleAirplane(command.args)
            "hotspot", "tethering" -> handleHotspot(command.args)
            "mobile_data", "data" -> handleMobileData(command.args)
            "gps", "location_mode" -> handleGps(command.args)
            else -> CommandResult.Error("Неизвестная радио/периферийная команда: $cmd")
        }
    }

    private fun isRootGranted(): Boolean = SystemControlUtils.isRootGranted(rootStatusProvider)

    // ==========================================
    // 1. WI-FI
    // ==========================================
    private suspend fun handleWifi(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return@withContext CommandResult.Error("Wi-Fi адаптер не найден на устройстве.")

            val action = args.firstOrNull()?.lowercase() ?: "status"

            when (action) {
                "status" -> {
                    val isEnabled = isWifiEnabled()
                    val statusEmoji = if (isEnabled) "🟢 Включен" else "🔴 Выключен"
                    val wifiInfo = wifiManager.connectionInfo
                    val ssid = wifiInfo?.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" } ?: "Не подключен"
                    val rssi = wifiInfo?.rssi ?: 0
                    val linkSpeed = "${wifiInfo?.linkSpeed ?: 0} Mbps"
                    val ip = SystemControlUtils.formatIp(wifiInfo?.ipAddress ?: 0)

                    val text =
                        """
                        📶 <b>Статус Wi-Fi:</b> $statusEmoji
                        • Сеть (SSID): <code>$ssid</code>
                        • Сигнал (RSSI): <code>$rssi dBm</code>
                        • Скорость соединения: <code>$linkSpeed</code>
                        • Локальный IP: <code>$ip</code>
                        """.trimIndent()

                    val keyboard = SystemControlUtils.makeSingleToggleKeyboard("/wifi", isEnabled, hasRoot = isRootGranted())
                    CommandResult.Text(text, keyboard)
                }

                "on", "enable", "1" -> {
                    val success = setWifiState(wifiManager, enable = true)
                    if (success) {
                        CommandResult.Text(
                            "✅ <b>Wi-Fi успешно включен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/wifi", true, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error(
                            "⚠️ Не удалось включить Wi-Fi.\nНа Android 10+ для прямого управления требуются Root-права (libsu).",
                        )
                    }
                }

                "off", "disable", "0" -> {
                    val success = setWifiState(wifiManager, enable = false)
                    if (success) {
                        CommandResult.Text(
                            "🔴 <b>Wi-Fi успешно выключен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/wifi", false, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error(
                            "⚠️ Не удалось выключить Wi-Fi.\nНа Android 10+ для прямого управления требуются Root-права (libsu).",
                        )
                    }
                }

                "toggle" -> {
                    val current = isWifiEnabled()
                    val target = !current
                    val success = setWifiState(wifiManager, enable = target)
                    if (success) {
                        val label = if (target) "включен" else "выключен"
                        CommandResult.Text(
                            "🔄 <b>Wi-Fi переключен: $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/wifi", target, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error(
                            "⚠️ Не удалось переключить Wi-Fi.\nНа Android 10+ для прямого управления требуются Root-права (libsu).",
                        )
                    }
                }

                else -> CommandResult.Error("Неверный параметр. Используйте: <code>/wifi [on|off|toggle|status]</code>")
            }
        }

    fun isWifiEnabled(): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.isWifiEnabled == true
    }

    private fun setWifiState(
        wifiManager: WifiManager,
        enable: Boolean,
    ): Boolean {
        try {
            val cmd = if (enable) "cmd wifi set-wifi-enabled enabled || svc wifi enable" else "cmd wifi set-wifi-enabled disabled || svc wifi disable"
            val res = Shell.cmd(cmd).exec()
            if (res.isSuccess) {
                logger.i("SystemControl", "Wi-Fi изменен через Root shell: enable=$enable")
                return true
            }
        } catch (_: Exception) {
        }

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return try {
                wifiManager.setWifiEnabled(enable)
            } catch (e: Exception) {
                logger.w("SystemControl", "WifiManager.setWifiEnabled failed: ${e.localizedMessage}")
                false
            }
        }

        return false
    }

    // ==========================================
    // 2. BLUETOOTH
    // ==========================================
    @SuppressLint("MissingPermission")
    private suspend fun handleBluetooth(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter =
                btManager?.adapter
                    ?:
                    @Suppress("DEPRECATION")
                    BluetoothAdapter.getDefaultAdapter()

            if (btAdapter == null) {
                return@withContext CommandResult.Error("Bluetooth адаптер не найден на устройстве.")
            }

            val action = args.firstOrNull()?.lowercase() ?: "status"

            when (action) {
                "status" -> {
                    val isEnabled = isBluetoothEnabled()
                    val statusEmoji = if (isEnabled) "🟢 Включен" else "🔴 Выключен"
                    val name =
                        try {
                            btAdapter.name ?: "N/A"
                        } catch (_: SecurityException) {
                            "Нет разрешения"
                        }
                    val bondedCount =
                        try {
                            btAdapter.bondedDevices?.size ?: 0
                        } catch (_: SecurityException) {
                            0
                        }

                    val text =
                        """
                        🦷 <b>Статус Bluetooth:</b> $statusEmoji
                        • Имя адаптера: <code>$name</code>
                        • Сопряженных устройств: <code>$bondedCount</code>
                        """.trimIndent()

                    CommandResult.Text(
                        text,
                        SystemControlUtils.makeSingleToggleKeyboard("/bluetooth", isEnabled, hasRoot = isRootGranted()),
                    )
                }

                "on", "enable", "1" -> {
                    val success = setBluetoothState(btAdapter, enable = true)
                    if (success) {
                        CommandResult.Text(
                            "✅ <b>Bluetooth успешно включен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/bluetooth", true, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error("⚠️ Не удалось включить Bluetooth (требуются Root права или разрешение).")
                    }
                }

                "off", "disable", "0" -> {
                    val success = setBluetoothState(btAdapter, enable = false)
                    if (success) {
                        CommandResult.Text(
                            "🔴 <b>Bluetooth успешно выключен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/bluetooth", false, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error("⚠️ Не удалось выключить Bluetooth.")
                    }
                }

                "toggle" -> {
                    val current = isBluetoothEnabled()
                    val target = !current
                    val success = setBluetoothState(btAdapter, enable = target)
                    if (success) {
                        val label = if (target) "включен" else "выключен"
                        CommandResult.Text(
                            "🔄 <b>Bluetooth переключен: $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/bluetooth", target, hasRoot = isRootGranted()),
                        )
                    } else {
                        CommandResult.Error("⚠️ Не удалось переключить Bluetooth.")
                    }
                }

                else -> CommandResult.Error("Неверный параметр. Используйте: <code>/bluetooth [on|off|toggle|status]</code>")
            }
        }

    fun isBluetoothEnabled(): Boolean {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter =
            btManager?.adapter
                ?:
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
        return btAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    private fun setBluetoothState(
        adapter: BluetoothAdapter,
        enable: Boolean,
    ): Boolean {
        try {
            val cmd = if (enable) "cmd bluetooth_manager enable || svc bluetooth enable" else "cmd bluetooth_manager disable || svc bluetooth disable"
            val res = Shell.cmd(cmd).exec()
            if (res.isSuccess) {
                logger.i("SystemControl", "Bluetooth изменен через Root shell: enable=$enable")
                return true
            }
        } catch (_: Exception) {
        }

        return try {
            @Suppress("DEPRECATION")
            if (enable) adapter.enable() else adapter.disable()
        } catch (e: Exception) {
            logger.w("SystemControl", "BluetoothAdapter enable/disable failed: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // 3. FLASHLIGHT
    // ==========================================
    fun isTorchActive(): Boolean = isTorchOn

    private suspend fun handleFlashlight(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val cameraManager =
                context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    ?: return@withContext CommandResult.Error("Сервис камеры недоступен.")

            val cameraId =
                findTorchCameraId(cameraManager)
                    ?: return@withContext CommandResult.Error("Камера со вспышкой не найдена.")

            val action = args.firstOrNull()?.lowercase() ?: "status"

            try {
                when (action) {
                    "status" -> {
                        val label = if (isTorchOn) "🟢 Включен" else "⚪ Выключен"
                        CommandResult.Text(
                            "🔦 <b>Фонарик:</b> $label",
                            SystemControlUtils.makeSingleToggleKeyboard("/flashlight", isTorchOn, hasRoot = isRootGranted()),
                        )
                    }
                    "on", "1" -> {
                        cameraManager.setTorchMode(cameraId, true)
                        isTorchOn = true
                        logger.i("SystemControl", "Фонарик включен (камера ID: $cameraId)")
                        CommandResult.Text(
                            "🔦 <b>Фонарик включен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/flashlight", true, hasRoot = isRootGranted()),
                        )
                    }
                    "off", "0" -> {
                        cameraManager.setTorchMode(cameraId, false)
                        isTorchOn = false
                        logger.i("SystemControl", "Фонарик выключен (камера ID: $cameraId)")
                        CommandResult.Text(
                            "💡 <b>Фонарик выключен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/flashlight", false, hasRoot = isRootGranted()),
                        )
                    }
                    "toggle" -> {
                        val target = !isTorchOn
                        cameraManager.setTorchMode(cameraId, target)
                        isTorchOn = target
                        val label = if (target) "включен 🔦" else "выключен 💡"
                        logger.i("SystemControl", "Фонарик переключен: $label")
                        CommandResult.Text(
                            "🔄 <b>Фонарик $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/flashlight", target, hasRoot = isRootGranted()),
                        )
                    }
                    else -> CommandResult.Error("Используйте: <code>/flashlight [on|off|toggle|status]</code>")
                }
            } catch (e: Exception) {
                logger.e("SystemControl", "Ошибка переключения фонарика: ${e.localizedMessage}", e)
                CommandResult.Error("Сбой управления фонариком: ${e.localizedMessage}")
            }
        }

    private fun findTorchCameraId(cameraManager: CameraManager): String? {
        return try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // 4. AIRPLANE MODE [REQUIRES ROOT]
    // ==========================================
    private suspend fun handleAirplane(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val action = args.firstOrNull()?.lowercase() ?: "status"
            val hasRoot = isRootGranted()

            when (action) {
                "status" -> {
                    val isEnabled = isAirplaneModeOn()
                    val statusEmoji = if (isEnabled) "✈️ Включен" else "⚪ Выключен"
                    val rootNote = if (hasRoot) "🟢 Root активен" else "🔒 Требуются Root-права для изменения"
                    CommandResult.Text(
                        "✈️ <b>Режим полета:</b> $statusEmoji\n<i>$rootNote</i>",
                        SystemControlUtils.makeSingleToggleKeyboard("/airplane", isEnabled, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                "on", "1" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для режима полета требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setAirplaneMode(true)
                    if (success) {
                        CommandResult.Text(
                            "✈️ <b>Режим полета включен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/airplane", true, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка переключения режима полета через Root shell.")
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для режима полета требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setAirplaneMode(false)
                    if (success) {
                        CommandResult.Text(
                            "🌐 <b>Режим полета выключен!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/airplane", false, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка переключения режима полета через Root shell.")
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для режима полета требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val current = isAirplaneModeOn()
                    val target = !current
                    val success = setAirplaneMode(target)
                    if (success) {
                        val label = if (target) "включен ✈️" else "выключен 🌐"
                        CommandResult.Text(
                            "🔄 <b>Режим полета $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/airplane", target, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка переключения режима полета через Root shell.")
                    }
                }
                else -> CommandResult.Error("Используйте: <code>/airplane [on|off|toggle|status]</code>")
            }
        }

    fun isAirplaneModeOn(): Boolean = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun setAirplaneMode(enable: Boolean): Boolean {
        val valStr = if (enable) "1" else "0"
        val boolStr = if (enable) "true" else "false"
        val cmd = "cmd connectivity airplane-mode ${if (enable) "enable" else "disable"} || (settings put global airplane_mode_on $valStr && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $boolStr)"
        return try {
            val res = Shell.cmd(cmd).exec()
            logger.i("SystemControl", "Режим полета изменен: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "Сбой setAirplaneMode: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // 5. HOTSPOT / TETHERING [REQUIRES ROOT]
    // ==========================================
    private suspend fun handleHotspot(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val action = args.firstOrNull()?.lowercase() ?: "status"
            val hasRoot = isRootGranted()

            when (action) {
                "status" -> {
                    val isEnabled = isHotspotEnabled()
                    val statusEmoji = if (isEnabled) "📡 Включена" else "⚪ Выключена"
                    val rootNote = if (hasRoot) "🟢 Root активен" else "🔒 Требуются Root-права для изменения"
                    CommandResult.Text(
                        "📡 <b>Точка доступа (Wi-Fi Hotspot):</b> $statusEmoji\n<i>$rootNote</i>",
                        SystemControlUtils.makeSingleToggleKeyboard("/hotspot", isEnabled, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                "on", "1" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для точки доступа требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setHotspot(true)
                    if (success) {
                        CommandResult.Text(
                            "📡 <b>Точка доступа включена!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/hotspot", true, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка запуска точки доступа.")
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для точки доступа требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setHotspot(false)
                    if (success) {
                        CommandResult.Text(
                            "⚪ <b>Точка доступа выключена!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/hotspot", false, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка остановки точки доступа.")
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для точки доступа требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val current = isHotspotEnabled()
                    val target = !current
                    val success = setHotspot(target)
                    if (success) {
                        val label = if (target) "включена 📡" else "выключена ⚪"
                        CommandResult.Text(
                            "🔄 <b>Точка доступа $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/hotspot", target, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка переключения точки доступа.")
                    }
                }
                else -> CommandResult.Error("Используйте: <code>/hotspot [on|off|toggle|status]</code>")
            }
        }

    fun isHotspotEnabled(): Boolean =
        try {
            val res = Shell.cmd("dumpsys wifi | grep -i 'curState=TetheredState\\|mSoftApState=13'").exec()
            res.isSuccess && res.out.isNotEmpty()
        } catch (_: Exception) {
            false
        }

    private fun setHotspot(enable: Boolean): Boolean {
        val cmd =
            if (enable) {
                "cmd connectivity start-tethering wifi || svc wifi startTethering"
            } else {
                "cmd connectivity stop-tethering wifi || svc wifi stopTethering"
            }
        return try {
            val res = Shell.cmd(cmd).exec()
            logger.i("SystemControl", "Hotspot изменен: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "Сбой setHotspot: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // 6. MOBILE DATA [REQUIRES ROOT]
    // ==========================================
    private suspend fun handleMobileData(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val action = args.firstOrNull()?.lowercase() ?: "status"
            val hasRoot = isRootGranted()

            when (action) {
                "status" -> {
                    val isEnabled = isMobileDataEnabled()
                    val statusEmoji = if (isEnabled) "🟢 Включена" else "🔴 Выключена"
                    val rootNote = if (hasRoot) "🟢 Root активен" else "🔒 Требуются Root-права для изменения"
                    CommandResult.Text(
                        "📶 <b>Мобильная передача данных:</b> $statusEmoji\n<i>$rootNote</i>",
                        SystemControlUtils.makeSingleToggleKeyboard("/mobile_data", isEnabled, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                "on", "1" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для переключения мобильных данных требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setMobileData(true)
                    if (success) {
                        CommandResult.Text(
                            "🟢 <b>Мобильные данные включены!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/mobile_data", true, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка включения мобильных данных.")
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для переключения мобильных данных требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setMobileData(false)
                    if (success) {
                        CommandResult.Text(
                            "🔴 <b>Мобильные данные выключены!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/mobile_data", false, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка выключения мобильных данных.")
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для переключения мобильных данных требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val current = isMobileDataEnabled()
                    val target = !current
                    val success = setMobileData(target)
                    if (success) {
                        val label = if (target) "включена 🟢" else "выключена 🔴"
                        CommandResult.Text(
                            "🔄 <b>Мобильная передача данных $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/mobile_data", target, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка переключения мобильных данных.")
                    }
                }
                else -> CommandResult.Error("Используйте: <code>/mobile_data [on|off|toggle|status]</code>")
            }
        }

    fun isMobileDataEnabled(): Boolean = Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1

    private fun setMobileData(enable: Boolean): Boolean {
        val cmd = if (enable) "svc data enable || cmd phone data enable" else "svc data disable || cmd phone data disable"
        return try {
            val res = Shell.cmd(cmd).exec()
            logger.i("SystemControl", "Mobile data изменен: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "Сбой setMobileData: ${e.localizedMessage}")
            false
        }
    }

    // ==========================================
    // 7. GEOLOCATION (GPS / LOCATION) [REQUIRES ROOT]
    // ==========================================
    private suspend fun handleGps(args: List<String>): CommandResult =
        withContext(Dispatchers.IO) {
            val action = args.firstOrNull()?.lowercase() ?: "status"
            val hasRoot = isRootGranted()

            when (action) {
                "status" -> {
                    val isEnabled = isGpsEnabled()
                    val statusEmoji = if (isEnabled) "📍 Включена" else "⚪ Выключена"
                    val rootNote = if (hasRoot) "🟢 Root активен" else "🔒 Требуются Root-права для изменения"
                    CommandResult.Text(
                        "📍 <b>Геолокация (GPS):</b> $statusEmoji\n<i>$rootNote</i>",
                        SystemControlUtils.makeSingleToggleKeyboard("/gps", isEnabled, requiresRoot = true, hasRoot = hasRoot),
                    )
                }
                "on", "1" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для управления GPS требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setGps(true)
                    if (success) {
                        CommandResult.Text(
                            "📍 <b>Геолокация включена!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/gps", true, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка включения GPS.")
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для управления GPS требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val success = setGps(false)
                    if (success) {
                        CommandResult.Text(
                            "⚪ <b>Геолокация выключена!</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/gps", false, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка выключения GPS.")
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            "⚠️ Для управления GPS требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика.",
                        )
                    }
                    val current = isGpsEnabled()
                    val target = !current
                    val success = setGps(target)
                    if (success) {
                        val label = if (target) "включена 📍" else "выключена ⚪"
                        CommandResult.Text(
                            "🔄 <b>Геолокация $label</b>",
                            SystemControlUtils.makeSingleToggleKeyboard("/gps", target, requiresRoot = true, hasRoot = hasRoot),
                        )
                    } else {
                        CommandResult.Error("❌ Ошибка переключения GPS.")
                    }
                }
                else -> CommandResult.Error("Используйте: <code>/gps [on|off|toggle|status]</code>")
            }
        }

    fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm?.isLocationEnabled == true
        } else {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }
    }

    private fun setGps(enable: Boolean): Boolean {
        val cmd =
            if (enable) {
                "cmd location set-location-enabled true || settings put secure location_mode 3"
            } else {
                "cmd location set-location-enabled false || settings put secure location_mode 0"
            }
        return try {
            val res = Shell.cmd(cmd).exec()
            logger.i("SystemControl", "GPS изменен: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "Сбой setGps: ${e.localizedMessage}")
            false
        }
    }
}
