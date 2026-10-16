package com.remotebot.core.common.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.ShakeDetectorConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Hardware accelerometer shake detector manager.
 * Calculates acceleration vector magnitude and dynamic deviation
 * from Earth gravity constant (RemoteBot 2.2.4).
 */
class ShakeDetectorManager(
    context: Context,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _shakeConfig = MutableStateFlow(ShakeDetectorConfig())
    val shakeDetectorConfig: StateFlow<ShakeDetectorConfig> = _shakeConfig.asStateFlow()

    private val _shakeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val shakeEvents: Flow<Unit> = _shakeEvents.asSharedFlow()

    private var shakeSensorListener: SensorEventListener? = null
    private var lastShakeTimestamp = 0L

    fun setShakeDetectorEnabled(
        enabled: Boolean,
        sensitivity: Float,
        cooldownSeconds: Int,
    ) {
        _shakeConfig.value =
            ShakeDetectorConfig(
                isEnabled = enabled,
                sensitivity = sensitivity,
                cooldownSeconds = cooldownSeconds,
            )
        if (enabled) {
            startShakeListener(sensitivity, cooldownSeconds)
        } else {
            stopShakeListener()
        }
    }

    private fun startShakeListener(
        threshold: Float,
        cooldownSeconds: Int,
    ) {
        stopShakeListener()
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        var lastValue = 0.0
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    val x = event.values[0].toDouble()
                    val y = event.values[1].toDouble()
                    val z = event.values[2].toDouble()

                    // Acceleration vector magnitude and deviation from Earth gravity (9.80665)
                    val totalG = sqrt(x * x + y * y + z * z)
                    val deltaG = abs(totalG - SensorManager.GRAVITY_EARTH.toDouble())
                    // Smoothed dynamic acceleration accounting for previous delta
                    val accelerationScore = deltaG + ((deltaG - lastValue) * 0.9)
                    lastValue = accelerationScore

                    if (accelerationScore > threshold) {
                        val now = System.currentTimeMillis()
                        val cooldownMs = cooldownSeconds * 1000L
                        if (now - lastShakeTimestamp > cooldownMs) {
                            lastShakeTimestamp = now
                            AppLogger.i(
                                "ShakeDetectorManager",
                                "📳 Тряска зафиксирована! Значение: ${"%.2f".format(
                                    accelerationScore,
                                )} m/s² (порог: $threshold, таймаут: ${cooldownSeconds}с)",
                            )
                            _shakeEvents.tryEmit(Unit)
                        }
                    } else if (accelerationScore > 0.8) {
                        AppLogger.d("ShakeDetectorManager", "📈 Акселерометр: ${"%.2f".format(accelerationScore)} m/s² (порог: $threshold)")
                    }
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) {}
            }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        shakeSensorListener = listener
    }

    private fun stopShakeListener() {
        shakeSensorListener?.let {
            sensorManager.unregisterListener(it)
            shakeSensorListener = null
        }
    }

    fun emitSimulatedShake() {
        AppLogger.i("ShakeDetectorManager", "🛠️ [Debug] Симуляция события встряхивания устройства")
        _shakeEvents.tryEmit(Unit)
    }
}
