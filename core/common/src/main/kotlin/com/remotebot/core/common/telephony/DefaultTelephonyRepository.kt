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
                    "📤 Sending SMS to $address (SIM: ${simSlot ?: "default"}, chars: ${message.length})...",
                )
                val smsManager = getSmsManagerForSlot(simSlot)
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    AppLogger.d("DefaultTelephonyRepository", "SMS split into ${parts.size} parts (multipart)")
                    smsManager.sendMultipartTextMessage(address, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(address, null, message, null, null)
                }
                AppLogger.i("DefaultTelephonyRepository", "✅ SMS queued for sending: $address")
            }.onFailure {
                AppLogger.e("DefaultTelephonyRepository", "❌ SMS send error to $address: ${it.message}")
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

                AppLogger.d("DefaultTelephonyRepository", "Reading SMS messages (limit: $limit)...")
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
                AppLogger.i("DefaultTelephonyRepository", "SMS messages read: ${list.size}")
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

                AppLogger.d("DefaultTelephonyRepository", "Requesting call log (limit: $limit)...")
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
                AppLogger.i("DefaultTelephonyRepository", "Call log read successfully: ${list.size} entries")
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
                    "📞 Initiating a phone call to $number (SIM: ${simSlot ?: "default"})...",
                )
                val encodedNumber = Uri.encode(number)
                val intent =
                    Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$encodedNumber")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                context.startActivity(intent)
                AppLogger.d("DefaultTelephonyRepository", "Call to $number handed to Android telephony stack successfully")
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun sendUssd(
        code: String,
        simSlot: Int?,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                AppLogger.i("DefaultTelephonyRepository", "📟 Sending USSD request: $code (SIM: ${simSlot ?: "default"})...")
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                suspendCancellableCoroutine { continuation ->
                    val callback =
                        object : TelephonyManager.UssdResponseCallback() {
                            override fun onReceiveUssdResponse(
                                telephonyManager: TelephonyManager?,
                                request: String?,
                                response: CharSequence?,
                            ) {
                                val cleaned = response?.toString()?.trim() ?: "Empty USSD response"
                                AppLogger.i("DefaultTelephonyRepository", "✅ USSD response received for $code: $cleaned")
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
                                        TelephonyManager.USSD_RETURN_FAILURE -> "USSD request failed in the network"
                                        else -> "USSD error code: $failureCode"
                                    }
                                AppLogger.w("DefaultTelephonyRepository", "❌ USSD error for $code: $reason")
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
    override suspend fun resolveContactName(phoneNumber: String): String? =
        withContext(Dispatchers.IO) {
            val cleanPhone = phoneNumber.trim()
            if (cleanPhone.isBlank()) return@withContext null
            try {
                // 1. PhoneLookup (standard Android caller ID lookup)
                val uri =
                    Uri.withAppendedPath(
                        android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(cleanPhone),
                    )
                val lookupName =
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
                if (!lookupName.isNullOrBlank()) return@withContext lookupName

                // 2. Fallback: match by normalized digits in CommonDataKinds.Phone
                val digitsOnly = cleanPhone.filter { it.isDigit() }
                if (digitsOnly.length >= 7) {
                    val lastDigits = digitsOnly.takeLast(7)
                    val phoneUri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val fallbackName =
                        context.contentResolver
                            .query(
                                phoneUri,
                                arrayOf(
                                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                                ),
                                "${android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                                arrayOf("%$lastDigits%"),
                                null,
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    cursor.getString(
                                        cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                                    )
                                } else {
                                    null
                                }
                            }
                    if (!fallbackName.isNullOrBlank()) return@withContext fallbackName
                }
                null
            } catch (e: Exception) {
                AppLogger.d("DefaultTelephonyRepository", "Failed to resolve contact for $cleanPhone: ${e.message}")
                null
            }
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
