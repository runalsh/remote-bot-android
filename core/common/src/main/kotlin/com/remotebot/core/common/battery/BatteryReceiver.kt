package com.remotebot.core.common.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.remotebot.core.common.logger.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class BatteryEvent {
    data class ChargingChanged(
        val isCharging: Boolean,
        val level: Int,
        val plugType: String,
        val voltageMv: Int,
        val temperatureC: Float,
    ) : BatteryEvent()

    data class LowBattery(
        val level: Int,
        val isCharging: Boolean,
    ) : BatteryEvent()
}

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        if (intent == null) return
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                AppLogger.d("BatteryReceiver", "ACTION_POWER_CONNECTED received")
                com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
                    com.remotebot.domain.model.WakeupSource.BATTERY_EVENT,
                )
                processPowerEvent(context, forceCharging = true)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                AppLogger.d("BatteryReceiver", "ACTION_POWER_DISCONNECTED received")
                com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
                    com.remotebot.domain.model.WakeupSource.BATTERY_EVENT,
                )
                processPowerEvent(context, forceCharging = false)
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                processBatteryChanged(intent)
            }
        }
    }

    private fun processPowerEvent(
        context: Context?,
        forceCharging: Boolean,
    ) {
        val batteryStatus: Intent? = context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryStatus != null) {
            processBatteryChanged(batteryStatus, overrideCharging = forceCharging)
        }
    }

    private fun processBatteryChanged(
        intent: Intent,
        overrideCharging: Boolean? = null,
    ) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val batteryPct = (level * 100) / scale

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val tempC = if (temp > 0) temp / 10f else 0f

        val detectedCharging =
            plugged > 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val isCharging = overrideCharging ?: detectedCharging

        val plugTypeStr =
            when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Сеть 220V (AC)"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB порт"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Беспроводная зарядка"
                else -> if (isCharging) "Зарядное устройство" else "Не подключено"
            }

        synchronized(lock) {
            if (isFirstBatteryCheck) {
                isFirstBatteryCheck = false
                lastChargingState = isCharging
                lastPlugType = plugged
                AppLogger.i("BatteryReceiver", "Initial battery state: $batteryPct%, isCharging=$isCharging, plug=$plugTypeStr")
                if (batteryPct <= 20 && !isCharging) {
                    lastNotifiedLowLevel = batteryPct
                    _batteryEvents.tryEmit(BatteryEvent.LowBattery(level = batteryPct, isCharging = false))
                }
                return
            }

            // 1. Проверка изменения статуса зарядки / подключения кабеля
            val chargingStateChanged = lastChargingState != isCharging
            val plugChanged = isCharging && lastPlugType != plugged && plugged > 0

            if (chargingStateChanged || plugChanged) {
                lastChargingState = isCharging
                lastPlugType = plugged
                if (isCharging) {
                    lastNotifiedLowLevel = null // Сброс отслеживания разряда при начале зарядки
                }
                AppLogger.i("BatteryReceiver", "Charging state changed: isCharging=$isCharging, level=$batteryPct%, plug=$plugTypeStr")
                _batteryEvents.tryEmit(
                    BatteryEvent.ChargingChanged(
                        isCharging = isCharging,
                        level = batteryPct,
                        plugType = plugTypeStr,
                        voltageMv = voltage,
                        temperatureC = tempC,
                    ),
                )
            }

            // 2. Проверка низкого заряда (каждые 3% после 20% и ниже)
            if (!isCharging) {
                if (batteryPct <= 20) {
                    val prev = lastNotifiedLowLevel
                    if (prev == null) {
                        // Первое срабатывание при достижении <= 20%
                        lastNotifiedLowLevel = batteryPct
                        AppLogger.i("BatteryReceiver", "Low battery trigger: $batteryPct% (first <= 20%)")
                        _batteryEvents.tryEmit(BatteryEvent.LowBattery(level = batteryPct, isCharging = false))
                    } else if ((prev - batteryPct) >= 3 || (batteryPct <= 2 && prev > batteryPct)) {
                        // Разряд минимум на 3% с последнего уведомления или финальные <= 2%
                        lastNotifiedLowLevel = batteryPct
                        AppLogger.i("BatteryReceiver", "Low battery trigger: $batteryPct% (drop >= 3% from $prev%)")
                        _batteryEvents.tryEmit(BatteryEvent.LowBattery(level = batteryPct, isCharging = false))
                    }
                } else {
                    lastNotifiedLowLevel = null
                }
            } else {
                if (batteryPct > 20 || batteryPct > (lastNotifiedLowLevel ?: 0)) {
                    lastNotifiedLowLevel = null
                }
            }
        }
    }

    companion object {
        private val lock = Any()
        private val _batteryEvents = MutableSharedFlow<BatteryEvent>(extraBufferCapacity = 64)
        val batteryEvents: SharedFlow<BatteryEvent> = _batteryEvents.asSharedFlow()

        private var lastChargingState: Boolean? = null
        private var lastPlugType: Int? = null
        private var lastNotifiedLowLevel: Int? = null
        private var isFirstBatteryCheck: Boolean = true

        fun resetState() {
            synchronized(lock) {
                lastChargingState = null
                lastPlugType = null
                lastNotifiedLowLevel = null
                isFirstBatteryCheck = true
            }
        }

        fun emitSimulatedEvent(event: BatteryEvent) {
            _batteryEvents.tryEmit(event)
        }
    }
}
