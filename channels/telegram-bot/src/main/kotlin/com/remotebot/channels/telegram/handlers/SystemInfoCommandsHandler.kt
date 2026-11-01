package com.remotebot.channels.telegram.handlers

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import java.util.concurrent.TimeUnit

class SystemInfoCommandsHandler(
    private val context: Context,
    private val logger: AppLogger,
    private val rootStatusProvider: RootStatusProvider,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

    override val commandNames: List<String> =
        listOf(
            "system_info",
            "battery",
            "network",
            "uptime",
        )

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "system_info" -> handleSystemInfo()
            "battery" -> handleBattery()
            "network" -> handleNetwork()
            "uptime" -> handleUptime()
            else -> CommandResult.Error(strings.siUnknownCmd(cmd))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleSystemInfo(): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val model = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})"
                val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"
                val buildId = Build.DISPLAY

                val abis = Build.SUPPORTED_ABIS.joinToString(", ")
                val cpuCores = Runtime.getRuntime().availableProcessors()

                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager?.getMemoryInfo(memInfo)
                val totalRamMb = memInfo.totalMem / (1024 * 1024)
                val availRamMb = memInfo.availMem / (1024 * 1024)
                val usedRamMb = totalRamMb - availRamMb
                val ramPercent = if (totalRamMb > 0) (usedRamMb * 100 / totalRamMb) else 0

                val dataPath = Environment.getDataDirectory()
                val stat = StatFs(dataPath.path)
                val blockSize = stat.blockSizeLong
                val totalBlocks = stat.blockCountLong
                val availBlocks = stat.availableBlocksLong
                val totalRomGb = String.format(Locale.US, "%.1f", (totalBlocks * blockSize).toDouble() / (1024 * 1024 * 1024))
                val availRomGb = String.format(Locale.US, "%.1f", (availBlocks * blockSize).toDouble() / (1024 * 1024 * 1024))

                val dm = context.resources.displayMetrics
                val displayRes = "${dm.widthPixels}x${dm.heightPixels} (${dm.densityDpi} dpi)"

                val uptimeMs = SystemClock.elapsedRealtime()
                val days = TimeUnit.MILLISECONDS.toDays(uptimeMs)
                val hours = TimeUnit.MILLISECONDS.toHours(uptimeMs) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
                val uptimeStr =
                    if (days > 0) {
                        strings.siUptimeDays(days, hours, minutes)
                    } else {
                        strings.siUptimeHours(hours, minutes)
                    }

                val localIp = SystemControlUtils.getLocalIpAddress() ?: "127.0.0.1"
                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = connManager?.activeNetwork
                val caps = connManager?.getNetworkCapabilities(activeNet)
                val netType =
                    when {
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> strings.siNetCellular()
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
                        else -> strings.siNetOff()
                    }

                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                val sensorsCount = sensorManager?.getSensorList(Sensor.TYPE_ALL)?.size ?: 0

                val isRoot = SystemControlUtils.isRootGranted(rootStatusProvider)
                val rootStr = if (isRoot) strings.siRootGranted() else strings.siRootDenied()

                val text =
                    strings.siSummary(
                        model,
                        androidVer,
                        buildId,
                        securityPatch,
                        rootStr,
                        uptimeStr,
                        cpuCores,
                        abis,
                        usedRamMb,
                        totalRamMb,
                        ramPercent.toInt(),
                        availRomGb,
                        totalRomGb,
                        displayRes,
                        sensorsCount,
                        netType,
                        localIp,
                    )

                val keyboard =
                    InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                InlineKeyboardButton(strings.siBtnTogglesPanel(), "/toggles"),
                                InlineKeyboardButton(strings.siBtnBattery(), "/battery"),
                            ),
                            listOf(
                                InlineKeyboardButton(strings.siBtnNetworkInterfaces(), "/network"),
                            ),
                        ),
                    )

                CommandResult.Text(text, keyboard)
            } catch (e: Exception) {
                logger.e("SystemControl", "system_info collection error: ${e.localizedMessage}", e)
                CommandResult.Error(strings.siSysInfoError(e.localizedMessage ?: ""))
            }
        }

    private fun handleBattery(): CommandResult {
        try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)

            if (batteryStatus == null) {
                return CommandResult.Error(strings.siBatteryReadError())
            }

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else level

            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val statusStr =
                when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> strings.siBatCharging()
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> strings.siBatDischarging()
                    BatteryManager.BATTERY_STATUS_FULL -> strings.siBatFull()
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> strings.siBatNotCharging()
                    else -> strings.siBatUnknown()
                }

            val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val plugStr =
                when (chargePlug) {
                    BatteryManager.BATTERY_PLUGGED_AC -> strings.siPlugAc()
                    BatteryManager.BATTERY_PLUGGED_USB -> strings.siPlugUsb()
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> strings.siPlugWireless()
                    else -> strings.siPlugBattery()
                }

            val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthStr =
                when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> strings.siHealthGood()
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> strings.siHealthOverheat()
                    BatteryManager.BATTERY_HEALTH_DEAD -> strings.siHealthDead()
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> strings.siHealthOverVoltage()
                    BatteryManager.BATTERY_HEALTH_COLD -> strings.siHealthCold()
                    else -> strings.siHealthNormal()
                }

            val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val voltageStr =
                if (voltage > 0) {
                    strings.siVoltage(String.format(Locale.US, "%.2f", voltage / 1000.0), voltage)
                } else {
                    strings.siNA()
                }

            val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val tempStr = if (temperature > 0) String.format(Locale.US, "%.1f °C", temperature / 10.0) else "N/A"

            val technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

            val icon =
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    "⚡"
                } else if (batteryPct > 20) {
                    "🔋"
                } else {
                    "🪫"
                }

            val text =
                strings.siBatteryBlock(icon, batteryPct, statusStr, plugStr, tempStr, voltageStr, healthStr, technology)

            val isPowerSave = SystemControlUtils.isPowerSaveModeOn(context)
            val powerSaveText = if (isPowerSave) strings.siPowerSaveOn() else strings.siPowerSaveOff()
            val keyboard =
                InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton(powerSaveText, "/powersave toggle"),
                            InlineKeyboardButton(strings.siBtnRefresh(), "/battery"),
                        ),
                    ),
                )

            return CommandResult.Text(text, keyboard)
        } catch (e: Exception) {
            logger.e("SystemControl", "Battery read error: ${e.localizedMessage}", e)
            return CommandResult.Error(strings.siBatParamsError(e.localizedMessage ?: ""))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleNetwork(): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
                if (interfaces.isEmpty()) {
                    return@withContext CommandResult.Text(strings.siNoInterfaces())
                }

                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = connManager?.activeNetwork
                val caps = connManager?.getNetworkCapabilities(activeNet)
                val linkProps = connManager?.getLinkProperties(activeNet)

                val activeTransport =
                    when {
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi 📶"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> strings.siIfaceCellular()
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet 🔌"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN 🔒"
                        else -> strings.siIfaceAbsent()
                    }

                val dnsList = linkProps?.dnsServers?.mapNotNull { it.hostAddress }?.joinToString(", ") ?: "N/A"

                val sb = StringBuilder()
                sb.append(strings.siNetTitle())
                sb.append(strings.siNetPrimary(activeTransport))
                if (dnsList.isNotBlank() && dnsList != "N/A") {
                    sb.append("• <b>DNS:</b> <code>$dnsList</code>\n")
                }
                sb.append("\n")

                val sorted =
                    interfaces.sortedWith(
                        compareByDescending<NetworkInterface> { it.isUp && !it.isLoopback }
                            .thenByDescending { it.isUp }
                            .thenBy { it.name },
                    )

                for (intf in sorted) {
                    val isUp = intf.isUp
                    val isLoopback = intf.isLoopback
                    val isP2p = intf.isPointToPoint
                    val isVirtual = intf.isVirtual

                    val icon =
                        when {
                            !isUp -> "⚪"
                            isLoopback -> "🔁"
                            intf.name.startsWith("wlan") -> "📶"
                            intf.name.startsWith("rmnet") || intf.name.startsWith("ccmni") -> "📱"
                            intf.name.startsWith("tun") || intf.name.startsWith("ppp") || intf.name.startsWith("p2p") -> "🔒"
                            intf.name.startsWith("eth") -> "🔌"
                            else -> "🌐"
                        }

                    val status = if (isUp) "UP" else "DOWN"
                    val flags = mutableListOf<String>()
                    if (isLoopback) flags.add("loopback")
                    if (isP2p) flags.add("p2p")
                    if (isVirtual) flags.add("virtual")
                    val flagsStr = if (flags.isNotEmpty()) " (${flags.joinToString(", ")})" else ""

                    val macBytes =
                        try {
                            intf.hardwareAddress
                        } catch (_: Exception) {
                            null
                        }
                    val macStr = macBytes?.joinToString(":") { String.format(Locale.US, "%02X", it) }

                    val addrs = intf.inetAddresses.toList()
                    val ipv4List = addrs.filterIsInstance<Inet4Address>().map { it.hostAddress }
                    val ipv6List = addrs.filter { it !is Inet4Address }.mapNotNull { it.hostAddress?.substringBefore("%") }

                    sb.append("$icon <b>${intf.name}</b> [$status$flagsStr]\n")
                    if (!macStr.isNullOrBlank()) {
                        sb.append("  • MAC: <code>$macStr</code>\n")
                    }
                    if (intf.mtu > 0) {
                        sb.append("  • MTU: <code>${intf.mtu}</code>\n")
                    }
                    if (ipv4List.isNotEmpty()) {
                        sb.append("  • IPv4: <code>${ipv4List.joinToString(", ")}</code>\n")
                    }
                    if (ipv6List.isNotEmpty()) {
                        sb.append("  • IPv6: <code>${ipv6List.take(2).joinToString(", ")}</code>\n")
                    }
                    if (ipv4List.isEmpty() && ipv6List.isEmpty()) {
                        sb.append(strings.siNoAddresses())
                    }
                    sb.append("\n")
                }

                logger.i("SystemControl", "Network interfaces collected (/network), found: ${interfaces.size}")
                val keyboard =
                    InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                InlineKeyboardButton("📶 Wi-Fi", "/wifi"),
                                InlineKeyboardButton(strings.siBtnMobileData(), "/mobile_data"),
                            ),
                            listOf(
                                InlineKeyboardButton(strings.siBtnRefresh(), "/network"),
                            ),
                        ),
                    )
                CommandResult.Text(sb.toString().trimEnd(), keyboard)
            } catch (e: Exception) {
                logger.e("SystemControl", "Network interfaces collection error: ${e.localizedMessage}", e)
                CommandResult.Error(strings.siNetError(e.localizedMessage ?: ""))
            }
        }

    private fun handleUptime(): CommandResult {
        val osUptimeMs = SystemClock.elapsedRealtime()
        val osUptimeStr = formatDuration(osUptimeMs)

        val appUptimeMs =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val startMs = android.os.Process.getStartElapsedRealtime()
                if (startMs > 0) SystemClock.elapsedRealtime() - startMs else 0L
            } else {
                0L
            }
        val appUptimeStr = if (appUptimeMs > 0) formatDuration(appUptimeMs) else strings.siNA()

        val text =
            strings.siUptimeText(appUptimeStr, osUptimeStr)

        val keyboard =
            InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton(strings.siBtnStatus(), "/status"),
                        InlineKeyboardButton(strings.siBtnToggles(), "/toggles"),
                    ),
                ),
            )

        return CommandResult.Text(text, keyboard)
    }

    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return "—"
        val seconds = millis / 1000
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        val dUnit = strings.siUnitD()
        val hUnit = strings.siUnitH()
        val mUnit = strings.siUnitM()
        val sUnit = strings.siUnitS()
        return buildString {
            if (days > 0) append("$days$dUnit")
            if (hours > 0) append("$hours$hUnit")
            append("$minutes$mUnit")
            append("$secs$sUnit")
        }.trim()
    }
}
