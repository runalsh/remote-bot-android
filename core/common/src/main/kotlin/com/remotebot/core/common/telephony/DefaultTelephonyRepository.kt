package com.remotebot.core.common.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.CallLogEntry
import com.remotebot.domain.model.CallType
import com.remotebot.domain.model.SmsMessage
import com.remotebot.domain.repository.TelephonyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class DefaultTelephonyRepository(
    private val context: Context,
) : TelephonyRepository {
    @SuppressLint("MissingPermission")
    override suspend fun sendSms(
        address: String,
        message: String,
        simSlot: Int?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                AppLogger.i(
                    "DefaultTelephonyRepository",
                    "📤 Отправка SMS на $address (SIM: ${simSlot ?: "default"}, символов: ${message.length})...",
                )
                val smsManager = getSmsManagerForSlot(simSlot)
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    AppLogger.d("DefaultTelephonyRepository", "SMS разбито на ${parts.size} частей (multipart)")
                    smsManager.sendMultipartTextMessage(address, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(address, null, message, null, null)
                }
                AppLogger.i("DefaultTelephonyRepository", "✅ SMS успешно передано в очередь отправки: $address")
            }.onFailure {
                AppLogger.e("DefaultTelephonyRepository", "❌ Ошибка при отправке SMS на $address: ${it.message}")
            }
        }

    @SuppressLint("Range")
    override suspend fun getRecentSms(
        limit: Int,
        simSlot: Int?,
    ): Result<List<SmsMessage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val list = mutableListOf<SmsMessage>()
                val uri = Telephony.Sms.CONTENT_URI
                val projection =
                    arrayOf(
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                    )
                val sortOrder = "${Telephony.Sms.DATE} DESC"

                AppLogger.d("DefaultTelephonyRepository", "Чтение SMS сообщений (лимит: $limit)...")
                context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                    while (cursor.moveToNext() && list.size < limit) {
                        val address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS)) ?: "Unknown"
                        val body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY)) ?: ""
                        val date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE))
                        val type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE))
                        val isIncoming = (type == Telephony.Sms.MESSAGE_TYPE_INBOX)

                        list.add(
                            SmsMessage(
                                address = address,
                                body = body,
                                dateMs = date,
                                isIncoming = isIncoming,
                                simSlot = simSlot,
                            ),
                        )
                    }
                }
                AppLogger.i("DefaultTelephonyRepository", "Прочитано SMS сообщений: ${list.size}")
                list
            }
        }

    @SuppressLint("Range")
    override suspend fun getCallHistory(limit: Int): Result<List<CallLogEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val list = mutableListOf<CallLogEntry>()
                val uri = CallLog.Calls.CONTENT_URI
                val projection =
                    arrayOf(
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION,
                    )
                val sortOrder = "${CallLog.Calls.DATE} DESC"

                AppLogger.d("DefaultTelephonyRepository", "Запрос журнала звонков (лимит: $limit)...")
                context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                    while (cursor.moveToNext() && list.size < limit) {
                        val number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)) ?: "Unknown"
                        var name = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME))
                        if (name.isNullOrBlank() && number.isNotBlank() && number != "Unknown") {
                            name = resolveContactName(number)
                        }
                        val typeInt = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))
                        val date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE))
                        val duration = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION))

                        val callType =
                            when (typeInt) {
                                CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                                CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                                CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                                CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                                CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                                else -> CallType.UNKNOWN
                            }

                        list.add(
                            CallLogEntry(
                                number = number,
                                name = name,
                                type = callType,
                                dateMs = date,
                                durationSec = duration,
                            ),
                        )
                    }
                }
                AppLogger.i("DefaultTelephonyRepository", "Журнал звонков успешно прочитан: ${list.size} записей")
                list
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun makeCall(
        number: String,
        simSlot: Int?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                AppLogger.i(
                    "DefaultTelephonyRepository",
                    "📞 Инициация телефонного звонка на номер $number (SIM: ${simSlot ?: "default"})...",
                )
                val encodedNumber = Uri.encode(number)
                val intent =
                    Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$encodedNumber")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                context.startActivity(intent)
                AppLogger.d("DefaultTelephonyRepository", "Звонок на $number успешно передан в стек телефонии Android")
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun sendUssd(
        code: String,
        simSlot: Int?,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                AppLogger.i("DefaultTelephonyRepository", "📟 Отправка USSD запроса: $code (SIM: ${simSlot ?: "default"})...")
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                suspendCancellableCoroutine { continuation ->
                    val callback =
                        object : TelephonyManager.UssdResponseCallback() {
                            override fun onReceiveUssdResponse(
                                telephonyManager: TelephonyManager?,
                                request: String?,
                                response: CharSequence?,
                            ) {
                                val cleaned = response?.toString()?.trim() ?: "Пустой USSD ответ"
                                AppLogger.i("DefaultTelephonyRepository", "✅ Получен USSD ответ для $code: $cleaned")
                                if (continuation.isActive) {
                                    continuation.resume(cleaned)
                                }
                            }

                            override fun onReceiveUssdResponseFailed(
                                telephonyManager: TelephonyManager?,
                                request: String?,
                                failureCode: Int,
                            ) {
                                val reason =
                                    when (failureCode) {
                                        TelephonyManager.USSD_RETURN_FAILURE -> "Сбой выполнения USSD-запроса сетью"
                                        else -> "Код ошибки USSD: $failureCode"
                                    }
                                AppLogger.w("DefaultTelephonyRepository", "❌ Ошибка USSD для $code: $reason")
                                if (continuation.isActive) {
                                    continuation.resume("USSD failed: $reason")
                                }
                            }
                        }

                    telephonyManager.sendUssdRequest(code, callback, Handler(Looper.getMainLooper()))
                }
            }
        }

    @SuppressLint("Range")
    private fun resolveContactName(phoneNumber: String): String? =
        try {
            val uri =
                Uri.withAppendedPath(
                    android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber),
                )
            context.contentResolver
                .query(
                    uri,
                    arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            AppLogger.d("DefaultTelephonyRepository", "Не удалось сопоставить контакт для $phoneNumber: ${e.message}")
            null
        }

    @SuppressLint("MissingPermission")
    private fun getSmsManagerForSlot(simSlot: Int?): SmsManager {
        if (simSlot != null && simSlot > 0) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subList = subscriptionManager?.activeSubscriptionInfoList
            val subInfo = subList?.find { it.simSlotIndex == (simSlot - 1) }
            if (subInfo != null) {
                return SmsManager.getSmsManagerForSubscriptionId(subInfo.subscriptionId)
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
}
