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
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.BotLanguage
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRadioCommandsHandler(
    private val context: Context,
    private val logger: AppLogger,
    private val rootStatusProvider: RootStatusProvider,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

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
            else -> CommandResult.Error(strings.radioUnknownCmd(cmd))
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
                    ?: return@withContext CommandResult.Error(strings.radioWifiNotFound())

            val action = args.firstOrNull()?.lowercase() ?: "status"

            when (action) {
                "status" -> {
                    val isEnabled = isWifiEnabled()
                    val statusEmoji = if (isEnabled) strings.radioStateOnG() else strings.radioStateOffR()
                    val wifiInfo = wifiManager.connectionInfo
                    val ssid = wifiInfo?.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" } ?: strings.radioNotConnected()
                    val rssi = wifiInfo?.rssi ?: 0
                    val linkSpeed = "${wifiInfo?.linkSpeed ?: 0} Mbps"
                    val ip = SystemControlUtils.formatIp(wifiInfo?.ipAddress ?: 0)

                    val text =
                        strings.radioWifiStatus(statusEmoji, ssid, rssi, linkSpeed, ip)

                    val keyboard =
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/wifi",
                            isEnabled,
                            hasRoot = isRootGranted(),
                            strings = strings,
                        )
                    CommandResult.Text(text, keyboard)
                }

                "on", "enable", "1" -> {
                    val success = setWifiState(wifiManager, enable = true)
                    if (success) {
                        CommandResult.Text(
                            strings.radioWifiOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard("/wifi", true, hasRoot = isRootGranted(), strings = strings),
                        )
                    } else {
                        CommandResult.Error(
                            strings.radioWifiOnFailLong(),
                        )
                    }
                }

                "off", "disable", "0" -> {
                    val success = setWifiState(wifiManager, enable = false)
                    if (success) {
                        CommandResult.Text(
                            strings.radioWifiOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard("/wifi", false, hasRoot = isRootGranted(), strings = strings),
                        )
                    } else {
                        CommandResult.Error(
                            strings.radioWifiOffFailLong(),
                        )
                    }
                }

                "toggle" -> {
                    val current = isWifiEnabled()
                    val target = !current
                    val success = setWifiState(wifiManager, enable = target)
                    if (success) {
                        val label = if (target) strings.radioWordOn() else strings.radioWordOff()
                        CommandResult.Text(
                            strings.radioWifiToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard("/wifi", target, hasRoot = isRootGranted(), strings = strings),
                        )
                    } else {
                        CommandResult.Error(
                            strings.radioWifiToggleFailLong(),
                        )
                    }
                }

                else -> CommandResult.Error(strings.radioInvalidParam("/wifi [on|off|toggle|status]"))
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
                logger.i("SystemControl", "Wi-Fi changed via Root shell: enable=$enable")
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
                return@withContext CommandResult.Error(strings.radioBtNotFound())
            }

            val action = args.firstOrNull()?.lowercase() ?: "status"

            when (action) {
                "status" -> {
                    val isEnabled = isBluetoothEnabled()
                    val statusEmoji = if (isEnabled) strings.radioStateOnG() else strings.radioStateOffR()
                    val name =
                        try {
                            btAdapter.name ?: "N/A"
                        } catch (_: SecurityException) {
                            strings.radioNoPerm()
                        }
                    val bondedCount =
                        try {
                            btAdapter.bondedDevices?.size ?: 0
                        } catch (_: SecurityException) {
                            0
                        }

                    val text =
                        strings.radioBtStatus(statusEmoji, name, bondedCount)

                    CommandResult.Text(
                        text,
                        SystemControlUtils.makeSingleToggleKeyboard("/bluetooth", isEnabled, hasRoot = isRootGranted(), strings = strings),
                    )
                }

                "on", "enable", "1" -> {
                    val success = setBluetoothState(btAdapter, enable = true)
                    if (success) {
                        CommandResult.Text(
                            strings.radioBtOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard("/bluetooth", true, hasRoot = isRootGranted(), strings = strings),
                        )
                    } else {
                        CommandResult.Error(strings.radioBtOnFail())
                    }
                }

                "off", "disable", "0" -> {
                    val success = setBluetoothState(btAdapter, enable = false)
                    if (success) {
                        CommandResult.Text(
                            strings.radioBtOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard("/bluetooth", false, hasRoot = isRootGranted(), strings = strings),
                        )
                    } else {
                        CommandResult.Error(strings.radioBtOffFail())
                    }
                }

                "toggle" -> {
                    val current = isBluetoothEnabled()
                    val target = !current
                    val success = setBluetoothState(btAdapter, enable = target)
                    if (success) {
                        val label = if (target) strings.radioWordOn() else strings.radioWordOff()
                        CommandResult.Text(
                            strings.radioBtToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard("/bluetooth", target, hasRoot = isRootGranted(), strings = strings),
                        )
                    } else {
                        CommandResult.Error(strings.radioBtToggleFail())
                    }
                }

                else -> CommandResult.Error(strings.radioInvalidParam("/bluetooth [on|off|toggle|status]"))
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
                logger.i("SystemControl", "Bluetooth changed via Root shell: enable=$enable")
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
                    ?: return@withContext CommandResult.Error(strings.radioCamServiceUnavailable())

            val cameraId =
                findTorchCameraId(cameraManager)
                    ?: return@withContext CommandResult.Error(strings.radioNoFlashCamera())

            val action = args.firstOrNull()?.lowercase() ?: "status"

            try {
                when (action) {
                    "status" -> {
                        val label = if (isTorchOn) strings.radioStateOnG() else strings.radioStateOffW()
                        CommandResult.Text(
                            strings.radioTorchState(label),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/flashlight",
                                isTorchOn,
                                hasRoot = isRootGranted(),
                                strings = strings,
                            ),
                        )
                    }
                    "on", "1" -> {
                        cameraManager.setTorchMode(cameraId, true)
                        isTorchOn = true
                        logger.i("SystemControl", "Flashlight turned on (camera ID: $cameraId)")
                        CommandResult.Text(
                            strings.radioTorchOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard("/flashlight", true, hasRoot = isRootGranted(), strings = strings),
                        )
                    }
                    "off", "0" -> {
                        cameraManager.setTorchMode(cameraId, false)
                        isTorchOn = false
                        logger.i("SystemControl", "Flashlight turned off (camera ID: $cameraId)")
                        CommandResult.Text(
                            strings.radioTorchOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard("/flashlight", false, hasRoot = isRootGranted(), strings = strings),
                        )
                    }
                    "toggle" -> {
                        val target = !isTorchOn
                        cameraManager.setTorchMode(cameraId, target)
                        isTorchOn = target
                        val label = if (target) strings.radioWordOn() + " 🔦" else strings.radioWordOff() + " 💡"
                        logger.i("SystemControl", "Flashlight toggled: $label")
                        CommandResult.Text(
                            strings.radioTorchToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/flashlight",
                                target,
                                hasRoot = isRootGranted(),
                                strings = strings,
                            ),
                        )
                    }
                    else -> CommandResult.Error(strings.radioUsage("/flashlight [on|off|toggle|status]"))
                }
            } catch (e: Exception) {
                logger.e("SystemControl", "Flashlight toggle error: ${e.localizedMessage}", e)
                CommandResult.Error(strings.radioTorchFail(e.localizedMessage ?: ""))
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
                    val statusEmoji = if (isEnabled) strings.radioStateAirplaneOn() else strings.radioStateOffW()
                    val rootNote = if (hasRoot) strings.radioRootActive() else strings.radioRootNote()
                    CommandResult.Text(
                        strings.radioAirplaneStatus(statusEmoji, rootNote),
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/airplane",
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
                            strings.radioNeedRootAirplane(),
                        )
                    }
                    val success = setAirplaneMode(true)
                    if (success) {
                        CommandResult.Text(
                            strings.radioAirplaneOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/airplane",
                                true,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioAirplaneRootFail())
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.radioNeedRootAirplane(),
                        )
                    }
                    val success = setAirplaneMode(false)
                    if (success) {
                        CommandResult.Text(
                            strings.radioAirplaneOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/airplane",
                                false,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioAirplaneRootFail())
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.radioNeedRootAirplane(),
                        )
                    }
                    val current = isAirplaneModeOn()
                    val target = !current
                    val success = setAirplaneMode(target)
                    if (success) {
                        val label = if (target) strings.radioWordOn() + " ✈️" else strings.radioWordOff() + " 🌐"
                        CommandResult.Text(
                            strings.radioAirplaneToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/airplane",
                                target,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioAirplaneRootFail())
                    }
                }
                else -> CommandResult.Error(strings.radioUsage("/airplane [on|off|toggle|status]"))
            }
        }

    fun isAirplaneModeOn(): Boolean = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun setAirplaneMode(enable: Boolean): Boolean {
        val valStr = if (enable) "1" else "0"
        val boolStr = if (enable) "true" else "false"
        val cmd = "cmd connectivity airplane-mode ${if (enable) "enable" else "disable"} || (settings put global airplane_mode_on $valStr && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $boolStr)"
        return try {
            val res = Shell.cmd(cmd).exec()
            logger.i("SystemControl", "Airplane mode changed: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "setAirplaneMode failed: ${e.localizedMessage}")
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
                    val statusEmoji = if (isEnabled) strings.radioStateHotspotOn() else strings.radioStateHotspotOff()
                    val rootNote = if (hasRoot) strings.radioRootActive() else strings.radioRootNote()
                    CommandResult.Text(
                        strings.radioHotspotStatus(statusEmoji, rootNote),
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/hotspot",
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
                            strings.radioNeedRootHotspot(),
                        )
                    }
                    val success = setHotspot(true)
                    if (success) {
                        CommandResult.Text(
                            strings.radioHotspotOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/hotspot",
                                true,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioHotspotStartFail())
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.radioNeedRootHotspot(),
                        )
                    }
                    val success = setHotspot(false)
                    if (success) {
                        CommandResult.Text(
                            strings.radioHotspotOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/hotspot",
                                false,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioHotspotStopFail())
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.radioNeedRootHotspot(),
                        )
                    }
                    val current = isHotspotEnabled()
                    val target = !current
                    val success = setHotspot(target)
                    if (success) {
                        val label = if (target) strings.radioWordOnF() + " 📡" else strings.radioWordOffF() + " ⚪"
                        CommandResult.Text(
                            strings.radioHotspotToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/hotspot",
                                target,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioHotspotToggleFail())
                    }
                }
                else -> CommandResult.Error(strings.radioUsage("/hotspot [on|off|toggle|status]"))
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
            logger.i("SystemControl", "Hotspot changed: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "setHotspot failed: ${e.localizedMessage}")
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
                    val statusEmoji = if (isEnabled) strings.radioStateDataOn() else strings.radioStateDataOff()
                    val rootNote = if (hasRoot) strings.radioRootActive() else strings.radioRootNote()
                    CommandResult.Text(
                        strings.radioDataStatus(statusEmoji, rootNote),
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/mobile_data",
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
                            strings.radioNeedRootData(),
                        )
                    }
                    val success = setMobileData(true)
                    if (success) {
                        CommandResult.Text(
                            strings.radioDataOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/mobile_data",
                                true,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioDataOnFail())
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.radioNeedRootData(),
                        )
                    }
                    val success = setMobileData(false)
                    if (success) {
                        CommandResult.Text(
                            strings.radioDataOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/mobile_data",
                                false,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioDataOffFail())
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.radioNeedRootData(),
                        )
                    }
                    val current = isMobileDataEnabled()
                    val target = !current
                    val success = setMobileData(target)
                    if (success) {
                        val label = if (target) strings.radioWordOnF() + " 🟢" else strings.radioWordOffF() + " 🔴"
                        CommandResult.Text(
                            strings.radioDataToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/mobile_data",
                                target,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioDataToggleFail())
                    }
                }
                else -> CommandResult.Error(strings.radioUsage("/mobile_data [on|off|toggle|status]"))
            }
        }

    fun isMobileDataEnabled(): Boolean = Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1

    private fun setMobileData(enable: Boolean): Boolean {
        val cmd = if (enable) "svc data enable || cmd phone data enable" else "svc data disable || cmd phone data disable"
        return try {
            val res = Shell.cmd(cmd).exec()
            logger.i("SystemControl", "Mobile data changed: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "setMobileData failed: ${e.localizedMessage}")
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
                    val statusEmoji = if (isEnabled) strings.radioStateGpsOn() else strings.radioStateHotspotOff()
                    val rootNote = if (hasRoot) strings.radioRootActive() else strings.radioRootNote()
                    CommandResult.Text(
                        strings.radioGpsStatus(statusEmoji, rootNote),
                        SystemControlUtils.makeSingleToggleKeyboard(
                            "/gps",
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
                            strings.radioNeedRootGps(),
                        )
                    }
                    val success = setGps(true)
                    if (success) {
                        CommandResult.Text(
                            strings.radioGpsOnOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/gps",
                                true,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioGpsOnFail())
                    }
                }
                "off", "0" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.radioNeedRootGps(),
                        )
                    }
                    val success = setGps(false)
                    if (success) {
                        CommandResult.Text(
                            strings.radioGpsOffOk(),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/gps",
                                false,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioGpsOffFail())
                    }
                }
                "toggle" -> {
                    if (!hasRoot) {
                        return@withContext CommandResult.Error(
                            strings.radioNeedRootGps(),
                        )
                    }
                    val current = isGpsEnabled()
                    val target = !current
                    val success = setGps(target)
                    if (success) {
                        val label = if (target) strings.radioWordOnF() + " 📍" else strings.radioWordOffF() + " ⚪"
                        CommandResult.Text(
                            strings.radioGpsToggled(label),
                            SystemControlUtils.makeSingleToggleKeyboard(
                                "/gps",
                                target,
                                requiresRoot = true,
                                hasRoot = hasRoot,
                                strings = strings,
                            ),
                        )
                    } else {
                        CommandResult.Error(strings.radioGpsToggleFail())
                    }
                }
                else -> CommandResult.Error(strings.radioUsage("/gps [on|off|toggle|status]"))
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
            logger.i("SystemControl", "GPS changed: enable=$enable, success=${res.isSuccess}")
            res.isSuccess
        } catch (e: Exception) {
            logger.w("SystemControl", "setGps failed: ${e.localizedMessage}")
            false
        }
    }
}
