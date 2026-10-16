package com.remotebot.core.common.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.remotebot.core.common.logger.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class IncomingSmsEvent(
    val sender: String,
    val body: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val simSlot: Int? = null,
)

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordWakeup(
            com.remotebot.domain.model.WakeupSource.SMS,
        )

        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val body =
            buildString {
                for (sms in messages) {
                    append(sms.displayMessageBody)
                }
            }
        val timestamp = messages[0].timestampMillis

        val subscriptionId =
            intent
                .getIntExtra("subscription", -1)
                .takeIf { it != -1 } ?: intent
                .getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
                .takeIf { it != -1 }

        val simSlot = if (subscriptionId != null && subscriptionId >= 0) subscriptionId else null

        val event =
            IncomingSmsEvent(
                sender = sender,
                body = body,
                timestampMs = timestamp,
                simSlot = simSlot,
            )

        AppLogger.i(
            "SmsReceiver",
            "📩 Входящее SMS от $sender (сим-слот: ${simSlot ?: "default"}, частей: ${messages.size}, длина: ${body.length}): ${body.take(
                60,
            )}",
        )
        _smsEvents.tryEmit(event)
    }

    companion object {
        private val _smsEvents = MutableSharedFlow<IncomingSmsEvent>(extraBufferCapacity = 64)
        val smsEvents: SharedFlow<IncomingSmsEvent> = _smsEvents.asSharedFlow()

        fun emitSimulatedSms(event: IncomingSmsEvent) {
            AppLogger.i("SmsReceiver", "🛠️ [Debug] Симуляция входящего SMS от ${event.sender}: ${event.body}")
            _smsEvents.tryEmit(event)
        }
    }
}
