package com.remotebot.domain

import com.remotebot.domain.model.CallLogEntry
import com.remotebot.domain.model.CallType
import com.remotebot.domain.model.CapturedNotification
import com.remotebot.domain.model.NotificationActionButton
import com.remotebot.domain.model.NotificationRule
import com.remotebot.domain.model.SmsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TelephonyCommandsTest {
    @Test
    fun testSmsMessageModel() {
        val sms =
            SmsMessage(
                address = "+79991234567",
                body = "Test SMS Body",
                dateMs = 1700000000000L,
                isIncoming = true,
                simSlot = 1,
            )
        assertEquals("+79991234567", sms.address)
        assertEquals("Test SMS Body", sms.body)
        assertEquals(1700000000000L, sms.dateMs)
        assertTrue(sms.isIncoming)
        assertEquals(1, sms.simSlot)
    }

    @Test
    fun testCallLogEntryModel() {
        val call =
            CallLogEntry(
                number = "+79997654321",
                name = "Test Contact",
                type = CallType.MISSED,
                dateMs = 1700000000000L,
                durationSec = 0,
            )
        assertEquals("+79997654321", call.number)
        assertEquals("Test Contact", call.name)
        assertEquals(CallType.MISSED, call.type)
        assertEquals(0, call.durationSec)
    }

    @Test
    fun testCallTypes() {
        val types = CallType.entries
        assertTrue(types.contains(CallType.INCOMING))
        assertTrue(types.contains(CallType.OUTGOING))
        assertTrue(types.contains(CallType.MISSED))
        assertTrue(types.contains(CallType.REJECTED))
        assertTrue(types.contains(CallType.BLOCKED))
        assertTrue(types.contains(CallType.UNKNOWN))
    }

    @Test
    fun testCapturedNotificationModel() {
        val notif =
            CapturedNotification(
                id = "1",
                key = "0|com.whatsapp|1|null|1000",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "John Doe",
                text = "Hello there!",
                timestampMs = 1700000000000L,
                hasReplyAction = true,
            )
        assertEquals("1", notif.id)
        assertEquals("com.whatsapp", notif.packageName)
        assertEquals("WhatsApp", notif.appName)
        assertEquals("John Doe", notif.title)
        assertEquals("Hello there!", notif.text)
        assertTrue(notif.hasReplyAction)
    }

    @Test
    fun testNotificationActionButtonModel() {
        val actionApprove = NotificationActionButton(0, "Подтвердить")
        val actionReject = NotificationActionButton(1, "Отклонить")
        val notif2Fa =
            CapturedNotification(
                id = "7272",
                key = "0|ru.multifactor.app|7272|null|10588",
                packageName = "ru.multifactor.app",
                appName = "Multifactor",
                title = "Multifactor",
                text = "Подключение к VPN?",
                actions = listOf(actionApprove, actionReject),
            )
        assertEquals(2, notif2Fa.actions.size)
        assertEquals(0, notif2Fa.actions[0].index)
        assertEquals("Подтвердить", notif2Fa.actions[0].title)
        assertEquals(1, notif2Fa.actions[1].index)
        assertEquals("Отклонить", notif2Fa.actions[1].title)
    }

    @Test
    fun testNotificationRuleMatching() {
        val ruleExclude =
            NotificationRule(
                id = "r1",
                packageName = "com.android.systemui",
                isExclude = true,
            )
        assertEquals("r1", ruleExclude.id)
        assertEquals("com.android.systemui", ruleExclude.packageName)
        assertTrue(ruleExclude.isExclude)
        assertFalse(ruleExclude.isRegex)

        val ruleRegex =
            NotificationRule(
                id = "r2",
                packageName = "com.whatsapp",
                titlePattern = "^(Promo|Ad).*",
                isRegex = true,
                isExclude = true,
            )
        assertTrue(ruleRegex.isRegex)
        assertNotNull(ruleRegex.titlePattern)
    }

    @Test
    fun testUssdResponseFormatting() {
        val rawResponse = "  Баланс: 450.25 руб. Подключен тариф 'Smart'. \n\n "
        val cleaned = rawResponse.trim()
        assertEquals("Баланс: 450.25 руб. Подключен тариф 'Smart'.", cleaned)
        assertTrue(cleaned.contains("450.25"))
    }

    @Test
    fun testNotificationRuleEvaluationLogic() {
        fun matchesRule(
            rule: NotificationRule,
            pkg: String,
            title: String?,
            text: String?,
        ): Boolean {
            val pkgMatch = rule.packageName == null || rule.packageName.equals(pkg, ignoreCase = true)
            val titleMatch =
                rule.titlePattern == null ||
                    if (rule.isRegex) {
                        title != null && Regex(rule.titlePattern!!).containsMatchIn(title)
                    } else {
                        title != null && title.contains(rule.titlePattern!!, ignoreCase = true)
                    }
            val textMatch =
                rule.bodyPattern == null ||
                    if (rule.isRegex) {
                        text != null && Regex(rule.bodyPattern!!).containsMatchIn(text)
                    } else {
                        text != null && text.contains(rule.bodyPattern!!, ignoreCase = true)
                    }
            return pkgMatch && titleMatch && textMatch
        }

        val ruleSpam =
            NotificationRule(
                id = "spam1",
                packageName = null,
                bodyPattern = "(?i)распродажа|скидка|кредит",
                isRegex = true,
                isExclude = true,
            )

        assertTrue(matchesRule(ruleSpam, "com.bank.app", "Уведомление", "Вам одобрен кредит на 100 000 руб"))
        assertFalse(matchesRule(ruleSpam, "com.bank.app", "Код", "Код для входа: 1234"))
    }

    @Test
    fun testCallDurationFormatting() {
        val durationSec = 145L // 2 min 25 sec
        val formatted = "${durationSec / 60}м ${durationSec % 60}с"
        assertEquals("2м 25с", formatted)
    }
}
