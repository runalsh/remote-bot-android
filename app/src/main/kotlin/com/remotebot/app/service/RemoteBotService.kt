package com.remotebot.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.remotebot.channels.telegram.engine.TelegramBotEngine
import org.koin.android.ext.android.inject

class RemoteBotService : LifecycleService() {
    private val botEngine: TelegramBotEngine by inject()

    companion object {
        const val CHANNEL_ID = "remotebot_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.remotebot.action.START"
        const val ACTION_STOP = "com.remotebot.action.STOP"
        const val ACTION_UPGRADE_TYPES = "com.remotebot.action.UPGRADE_TYPES"

        const val EXTRA_FGS_TYPES = "extra_fgs_types"

        private val _isServiceRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isServiceRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isServiceRunning

        private val _servicePid = kotlinx.coroutines.flow.MutableStateFlow(-1)
        val servicePid: kotlinx.coroutines.flow.StateFlow<Int> = _servicePid

        private val _serviceStartTime = kotlinx.coroutines.flow.MutableStateFlow(0L)
        val serviceStartTime: kotlinx.coroutines.flow.StateFlow<Long> = _serviceStartTime

        fun getServiceUptimeMillis(): Long {
            val startTime = _serviceStartTime.value
            return if (startTime > 0) System.currentTimeMillis() - startTime else 0L
        }

        fun formatUptime(millis: Long): String {
            if (millis <= 0) return "—"
            val seconds = millis / 1000
            val days = seconds / 86400
            val hours = (seconds % 86400) / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return buildString {
                if (days > 0) append("${days}д ")
                if (hours > 0) append("${hours}ч ")
                append("${minutes}мин ")
                append("${secs}с")
            }.trim()
        }

        fun startService(context: Context) {
            val intent =
                Intent(context, RemoteBotService::class.java).apply {
                    action = ACTION_START
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent =
                Intent(context, RemoteBotService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
        }
    }

    private var batteryReceiver: com.remotebot.core.common.battery.BatteryReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerBatteryReceiver()
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiver == null) {
            batteryReceiver =
                com.remotebot.core.common.battery
                    .BatteryReceiver()
            val filter =
                android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
            registerReceiver(batteryReceiver, filter)
        }
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
            batteryReceiver = null
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                _isServiceRunning.value = false
                botEngine.stop()
                unregisterBatteryReceiver()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPGRADE_TYPES -> {
                val additionalTypes = intent.getIntExtra(EXTRA_FGS_TYPES, 0)
                updateForegroundServiceTypes(additionalTypes)
            }
            else -> {
                _isServiceRunning.value = true
                _servicePid.value = android.os.Process.myPid()
                _serviceStartTime.value = System.currentTimeMillis()
                startForegroundWithMessagingType()
                botEngine.start()
            }
        }

        // Автоматический перезапуск сервиса операционной системой при убийстве по OOM
        return START_STICKY
    }

    private fun startForegroundWithMessagingType() {
        val notification = buildServiceNotification()
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    fun updateForegroundServiceTypes(additionalTypes: Int) {
        val baseType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            }
        val combinedTypes = baseType or additionalTypes
        val notification = buildServiceNotification()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, combinedTypes)
    }

    private fun buildServiceNotification(): Notification {
        val appTitle = applicationInfo.loadLabel(packageManager).toString().ifBlank { "Remote Bot" }
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val contentPendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(appTitle)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (contentPendingIntent != null) {
                    setContentIntent(contentPendingIntent)
                }
            }.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Фоновая служба Remote Bot",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Обеспечивает постоянное присутствие и прием удаленных команд"
                }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        _isServiceRunning.value = false
        botEngine.stop()
        unregisterBatteryReceiver()
        super.onDestroy()
    }
}
