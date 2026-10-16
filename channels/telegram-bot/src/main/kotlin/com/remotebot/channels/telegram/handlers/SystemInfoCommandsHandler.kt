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
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.provider.RootStatusProvider
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
) : CommandHandler {
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
            else -> CommandResult.Error("Неизвестная команда системной информации: $cmd")
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
                val uptimeStr = if (days > 0) "${days}д ${hours}ч ${minutes}м" else "${hours}ч ${minutes}м"

                val localIp = SystemControlUtils.getLocalIpAddress() ?: "127.0.0.1"
                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = connManager?.activeNetwork
                val caps = connManager?.getNetworkCapabilities(activeNet)
                val netType =
                    when {
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Мобильная сеть (LTE/5G)"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
                        else -> "Отключена"
                    }

                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                val sensorsCount = sensorManager?.getSensorList(Sensor.TYPE_ALL)?.size ?: 0

                val isRoot = SystemControlUtils.isRootGranted(rootStatusProvider)
                val rootStr = if (isRoot) "🟢 Предоставлен (UID 0)" else "🔴 Нет / Отказано"

                val text =
                    """
                    📱 <b>Сводка системы (System Info)</b>
                    
                    <b>Устройство:</b>
                    • Модель: <code>$model</code>
                    • ОС: <code>$androidVer</code>
                    • Сборка: <code>$buildId</code>
                    • Патч безопасности: <code>$securityPatch</code>
                    • Root доступ: <b>$rootStr</b>
                    • Аптайм: <code>$uptimeStr</code>
                    
                    <b>Аппаратная часть:</b>
                    • CPU: <code>$cpuCores ядер ($abis)</code>
                    • RAM: <code>$usedRamMb / $totalRamMb МБ ($ramPercent% занято)</code>
                    • ROM: <code>$availRomGb ГБ свободно из $totalRomGb ГБ</code>
                    • Экран: <code>$displayRes</code>
                    • Датчики: <code>$sensorsCount доступно</code>
                    
                    <b>Связь и Сеть:</b>
                    • Тип подключения: <code>$netType</code>
                    • Локальный IP: <code>$localIp</code>
                    """.trimIndent()

                val keyboard =
                    InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                InlineKeyboardButton("🎛 Панель переключателей", "/toggles"),
                                InlineKeyboardButton("🔋 Батарея", "/battery"),
                            ),
                            listOf(
                                InlineKeyboardButton("🌐 Сетевые интерфейсы", "/network"),
                            ),
                        ),
                    )

                CommandResult.Text(text, keyboard)
            } catch (e: Exception) {
                logger.e("SystemControl", "Ошибка сбора system_info: ${e.localizedMessage}", e)
                CommandResult.Error("Ошибка получения системной информации: ${e.localizedMessage}")
            }
        }

    private fun handleBattery(): CommandResult {
        try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)

            if (batteryStatus == null) {
                return CommandResult.Error("Не удалось прочитать состояние батареи.")
            }

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else level

            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val statusStr =
                when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "⚡ Заряжается"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "🔋 Разряжается"
                    BatteryManager.BATTERY_STATUS_FULL -> "🟢 Полный заряд (100%)"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "⚪ Не заряжается"
                    else -> "Неизвестно"
                }

            val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val plugStr =
                when (chargePlug) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "Сеть 220V (AC)"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB порт"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Беспроводная зарядка"
                    else -> "От батареи (без кабеля)"
                }

            val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthStr =
                when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "✅ Отличное (Good)"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "🔥 Перегрев (Overheat)"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "💀 Неисправна (Dead)"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "⚠️ Перенапряжение"
                    BatteryManager.BATTERY_HEALTH_COLD -> "❄️ Переохлаждение"
                    else -> "Нормальное"
                }

            val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val voltageStr = if (voltage > 0) String.format(Locale.US, "%.2f В (%d мВ)", voltage / 1000.0, voltage) else "N/A"

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
                """
                $icon <b>Состояние аккумулятора (Battery)</b>
                
                • Уровень заряда: <b>$batteryPct%</b>
                • Статус: <b>$statusStr</b>
                • Источник питания: <code>$plugStr</code>
                • Температура: <code>$tempStr</code>
                • Напряжение: <code>$voltageStr</code>
                • Здоровье батареи: <code>$healthStr</code>
                • Технология: <code>$technology</code>
                """.trimIndent()

            val isPowerSave = SystemControlUtils.isPowerSaveModeOn(context)
            val keyboard =
                InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton(
                                if (isPowerSave) "🔋 Энергосбережение [ВКЛ]" else "🔋 Энергосбережение [ВЫКЛ]",
                                "/powersave toggle",
                            ),
                            InlineKeyboardButton("🔄 Обновить", "/battery"),
                        ),
                    ),
                )

            return CommandResult.Text(text, keyboard)
        } catch (e: Exception) {
            logger.e("SystemControl", "Ошибка считывания батареи: ${e.localizedMessage}", e)
            return CommandResult.Error("Сбой чтения параметров батареи: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleNetwork(): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
                if (interfaces.isEmpty()) {
                    return@withContext CommandResult.Text("🌐 <b>Сетевые интерфейсы не найдены.</b>")
                }

                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = connManager?.activeNetwork
                val caps = connManager?.getNetworkCapabilities(activeNet)
                val linkProps = connManager?.getLinkProperties(activeNet)

                val activeTransport =
                    when {
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi 📶"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Мобильная сеть (LTE/5G) 📱"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet 🔌"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN 🔒"
                        else -> "Отсутствует ⚪"
                    }

                val dnsList = linkProps?.dnsServers?.mapNotNull { it.hostAddress }?.joinToString(", ") ?: "N/A"

                val sb = StringBuilder()
                sb.append("🌐 <b>Сетевые интерфейсы устройства</b>\n\n")
                sb.append("• <b>Основное подключение:</b> $activeTransport\n")
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
                        sb.append("  • IP: <i>нет адресов</i>\n")
                    }
                    sb.append("\n")
                }

                logger.i("SystemControl", "Сбор сетевых интерфейсов выполнен (/network), найдено: ${interfaces.size}")
                val keyboard =
                    InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                InlineKeyboardButton("📶 Wi-Fi", "/wifi"),
                                InlineKeyboardButton("📱 Моб. данные", "/mobile_data"),
                            ),
                            listOf(
                                InlineKeyboardButton("🔄 Обновить", "/network"),
                            ),
                        ),
                    )
                CommandResult.Text(sb.toString().trimEnd(), keyboard)
            } catch (e: Exception) {
                logger.e("SystemControl", "Ошибка сбора сетевых интерфейсов: ${e.localizedMessage}", e)
                CommandResult.Error("Сбой получения сетевых интерфейсов: ${e.localizedMessage}")
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
        val appUptimeStr = if (appUptimeMs > 0) formatDuration(appUptimeMs) else "Н/Д"

        val text =
            buildString {
                appendLine("⏱️ <b>Время непрерывной работы (Uptime)</b>")
                appendLine()
                appendLine("🤖 <b>Служба бота:</b> <code>$appUptimeStr</code>")
                appendLine("📱 <b>Система Android:</b> <code>$osUptimeStr</code>")
            }.trimEnd()

        val keyboard =
            InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton("📊 Статус", "/status"),
                        InlineKeyboardButton("🎛️ Переключатели", "/toggles"),
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
        return buildString {
            if (days > 0) append("${days}д ")
            if (hours > 0) append("${hours}ч ")
            append("${minutes}м ")
            append("${secs}с")
        }.trim()
    }
}
