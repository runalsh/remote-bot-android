package com.remotebot.core.common.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.CallType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class PhoneCallEvent(
    val number: String?,
    val type: CallType,
    val timestampMs: Long = System.currentTimeMillis(),
)

class PhoneCallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        synchronized(lock) {
            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val cleanNumber = incomingNumber?.takeIf { it.isNotBlank() }
                    if (cleanNumber != null) {
                        savedNumber = cleanNumber
                    }

                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        // Already ringing
                        if (cleanNumber != null && !incomingCallEmitted) {
                            // Real caller ID arrived in secondary broadcast
                            pendingRingingJob?.cancel()
                            pendingRingingJob = null
                            incomingCallEmitted = true
                            AppLogger.i("PhoneCallReceiver", "📞 Входящий звонок: $cleanNumber")
                            com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
                                com.remotebot.domain.model.WakeupSource.PHONE_CALL,
                            )
                            _callEvents.tryEmit(
                                PhoneCallEvent(
                                    number = cleanNumber,
                                    type = CallType.INCOMING,
                                ),
                            )
                        }
                        // Ignore duplicate RINGING broadcast for active call
                        return
                    }

                    lastState = TelephonyManager.CALL_STATE_RINGING
                    com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
                        com.remotebot.domain.model.WakeupSource.PHONE_CALL,
                    )

                    if (cleanNumber != null) {
                        // Number is already known in first broadcast
                        incomingCallEmitted = true
                        AppLogger.i("PhoneCallReceiver", "📞 Входящий звонок: $cleanNumber")
                        _callEvents.tryEmit(
                            PhoneCallEvent(
                                number = cleanNumber,
                                type = CallType.INCOMING,
                            ),
                        )
                    } else {
                        // Number is null in initial broadcast (common on Android 9-15).
                        // Wait briefly for secondary broadcast with caller ID before emitting "hidden number".
                        incomingCallEmitted = false
                        AppLogger.d("PhoneCallReceiver", "📞 Входящий звонок (номер пока не определен, ожидание...)")
                        pendingRingingJob?.cancel()
                        pendingRingingJob =
                            receiverScope.launch {
                                delay(700)
                                synchronized(lock) {
                                    if (lastState == TelephonyManager.CALL_STATE_RINGING && !incomingCallEmitted) {
                                        incomingCallEmitted = true
                                        AppLogger.i("PhoneCallReceiver", "📞 Входящий звонок: номер скрыт")
                                        _callEvents.tryEmit(
                                            PhoneCallEvent(
                                                number = savedNumber,
                                                type = CallType.INCOMING,
                                            ),
                                        )
                                    }
                                }
                            }
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    pendingRingingJob?.cancel()
                    pendingRingingJob = null
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        isIncomingAnswered = true
                        AppLogger.i("PhoneCallReceiver", "📲 Входящий звонок принят: ${savedNumber ?: "номер скрыт"}")
                    } else {
                        AppLogger.d("PhoneCallReceiver", "📤 Исходящий вызов или поднятие трубки")
                    }
                    lastState = TelephonyManager.CALL_STATE_OFFHOOK
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    pendingRingingJob?.cancel()
                    pendingRingingJob = null
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        // Missed call
                        AppLogger.w("PhoneCallReceiver", "❌ Пропущенный звонок от: ${savedNumber ?: "номер скрыт"}")
                        _callEvents.tryEmit(
                            PhoneCallEvent(
                                number = savedNumber,
                                type = CallType.MISSED,
                            ),
                        )
                    } else if (isIncomingAnswered) {
                        AppLogger.i("PhoneCallReceiver", "⏹️ Разговор завершен: ${savedNumber ?: "номер скрыт"}")
                    }
                    lastState = TelephonyManager.CALL_STATE_IDLE
                    isIncomingAnswered = false
                    savedNumber = null
                    incomingCallEmitted = false
                }
            }
        }
    }

    companion object {
        private val lock = Any()
        private val receiverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private var pendingRingingJob: Job? = null
        private var incomingCallEmitted = false

        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var savedNumber: String? = null
        private var isIncomingAnswered = false

        private val _callEvents = MutableSharedFlow<PhoneCallEvent>(extraBufferCapacity = 64)
        val callEvents: SharedFlow<PhoneCallEvent> = _callEvents.asSharedFlow()
    }
}
