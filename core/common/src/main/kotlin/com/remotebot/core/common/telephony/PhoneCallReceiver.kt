package com.remotebot.core.common.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.CallType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                lastState = TelephonyManager.CALL_STATE_RINGING
                savedNumber = incomingNumber
                AppLogger.i("PhoneCallReceiver", "📞 Входящий звонок: ${incomingNumber ?: "номер скрыт"}")
                com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
                    com.remotebot.domain.model.WakeupSource.PHONE_CALL,
                )
                _callEvents.tryEmit(
                    PhoneCallEvent(
                        number = incomingNumber,
                        type = CallType.INCOMING,
                    ),
                )
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    isIncomingAnswered = true
                    AppLogger.i("PhoneCallReceiver", "📲 Входящий звонок принят: ${savedNumber ?: "номер скрыт"}")
                } else {
                    AppLogger.d("PhoneCallReceiver", "📤 Исходящий вызов или поднятие трубки")
                }
                lastState = TelephonyManager.CALL_STATE_OFFHOOK
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
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
            }
        }
    }

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var savedNumber: String? = null
        private var isIncomingAnswered = false

        private val _callEvents = MutableSharedFlow<PhoneCallEvent>(extraBufferCapacity = 64)
        val callEvents: SharedFlow<PhoneCallEvent> = _callEvents.asSharedFlow()
    }
}
