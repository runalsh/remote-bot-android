package com.remotebot.domain

import com.remotebot.domain.model.AppBackupData
import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.ConnectionMode
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.model.ProxyConfig
import com.remotebot.domain.model.ProxyType
import com.remotebot.domain.model.SubscriptionEventType
import com.remotebot.domain.model.TgResponse
import com.remotebot.domain.model.TgUpdate
import com.remotebot.domain.model.UserSubscription
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainModelsTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = false
        }

    @Test
    fun testIncomingCommandCreation() {
        val cmd =
            IncomingCommand(
                id = "upd_123",
                name = "/ping",
                rawText = "/ping param1 param2",
                args = listOf("param1", "param2"),
                source = CommandSource.TELEGRAM,
                senderId = "987654321",
                chatId = 123456L,
            )

        assertEquals("/ping", cmd.name)
        assertEquals(2, cmd.args.size)
        assertEquals("987654321", cmd.senderId)
        assertEquals(123456L, cmd.chatId)
        assertEquals(CommandSource.TELEGRAM, cmd.source)
    }

    @Test
    fun testProxyConfigDefaultsAndSerialization() {
        val cfg =
            ProxyConfig(
                type = ProxyType.WEBSOCKET_WSS,
                host = "proxy.example.com",
                port = 443,
                wsPath = "/tg-tunnel",
                username = "user",
                password = "secret_password",
            )

        val encoded = json.encodeToString(cfg)
        val decoded = json.decodeFromString<ProxyConfig>(encoded)

        assertEquals(ProxyType.WEBSOCKET_WSS, decoded.type)
        assertEquals("proxy.example.com", decoded.host)
        assertEquals(443, decoded.port)
        assertEquals("/tg-tunnel", decoded.wsPath)
        assertEquals("user", decoded.username)
        assertEquals("secret_password", decoded.password)
    }

    @Test
    fun testTelegramUpdateMessageJsonParsing() {
        val sampleJson =
            """
            {
                "ok": true,
                "result": [
                    {
                        "update_id": 100500,
                        "message": {
                            "message_id": 42,
                            "from": {
                                "id": 11223344,
                                "is_bot": false,
                                "first_name": "Admin",
                                "username": "admin_user"
                            },
                            "chat": {
                                "id": 11223344,
                                "type": "private"
                            },
                            "date": 1700000000,
                            "text": "/battery"
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<TgResponse<List<TgUpdate>>>(sampleJson)

        assertTrue(response.ok)
        assertNotNull(response.result)
        assertEquals(1, response.result.size)

        val update = response.result[0]
        assertEquals(100500L, update.updateId)
        assertEquals("/battery", update.message?.text)
        assertEquals(11223344L, update.message?.from?.id)
        assertEquals("admin_user", update.message?.from?.username)
        assertNull(update.callbackQuery)
    }

    @Test
    fun testTelegramCallbackQueryJsonParsing() {
        val sampleJson =
            """
            {
                "ok": true,
                "result": [
                    {
                        "update_id": 100501,
                        "callback_query": {
                            "id": "cb_query_999",
                            "from": {
                                "id": 55667788,
                                "is_bot": false,
                                "first_name": "Owner"
                            },
                            "message": {
                                "message_id": 100,
                                "chat": {
                                    "id": 55667788,
                                    "type": "private"
                                },
                                "date": 1700000005,
                                "text": "🎛 Quick Settings"
                            },
                            "data": "/wifi toggle"
                        }
                    }
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<TgResponse<List<TgUpdate>>>(sampleJson)

        assertTrue(response.ok)
        assertNotNull(response.result)
        val update = response.result[0]
        assertEquals(100501L, update.updateId)
        assertNull(update.message)
        assertNotNull(update.callbackQuery)

        val cb = update.callbackQuery
        assertEquals("cb_query_999", cb.id)
        assertEquals(55667788L, cb.from.id)
        assertEquals("/wifi toggle", cb.data)
        assertEquals(100L, cb.message?.messageId)
    }

    @Test
    fun testInlineKeyboardMarkupSerialization() {
        val keyboard =
            InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton(text = "📶 Wi-Fi [ВКЛ]", callbackData = "/wifi toggle"),
                        InlineKeyboardButton(text = "🦷 BT [ВКЛ]", callbackData = "/bluetooth toggle"),
                    ),
                    listOf(
                        InlineKeyboardButton(text = "🔄 Обновить", callbackData = "/toggles"),
                    ),
                ),
            )

        val encoded = json.encodeToString(keyboard)
        assertTrue(encoded.contains("\"inline_keyboard\""))
        assertTrue(encoded.contains("\"callback_data\":\"/wifi toggle\""))
        assertTrue(encoded.contains("\"text\":\"📶 Wi-Fi [ВКЛ]\""))

        val decoded = json.decodeFromString<InlineKeyboardMarkup>(encoded)
        assertEquals(2, decoded.inlineKeyboard.size)
        assertEquals(2, decoded.inlineKeyboard[0].size)
        assertEquals(1, decoded.inlineKeyboard[1].size)
        assertEquals("/toggles", decoded.inlineKeyboard[1][0].callbackData)
    }

    @Test
    fun testTelegramErrorResponseParsing() {
        val sampleJson =
            """
            {
                "ok": false,
                "error_code": 401,
                "description": "Unauthorized: bot token is invalid"
            }
            """.trimIndent()

        val response = json.decodeFromString<TgResponse<List<TgUpdate>>>(sampleJson)

        assertFalse(response.ok)
        assertNull(response.result)
        assertEquals(401, response.errorCode)
        assertEquals("Unauthorized: bot token is invalid", response.description)
    }

    @Test
    fun testAppBackupDataSerializationRoundtrip() {
        val botConfig =
            BotConfig(
                botToken = "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11",
                allowedUserIds = setOf(11223344L, 99887766L),
                allowedPhoneNumbers = setOf("+79991234567"),
                connectionMode = ConnectionMode.LONG_POLLING,
                smsPin = "1234",
                smsPinRequiredAlways = true,
                storageDirectory = "/storage/emulated/0/Download/RemoteBot",
            )
        val proxyConfig =
            ProxyConfig(
                type = ProxyType.SOCKS5,
                host = "10.0.0.1",
                port = 1080,
                username = "admin",
            )
        val backup =
            AppBackupData(
                version = "2.0.0",
                exportedAtMs = 1700000000000L,
                botConfig = botConfig,
                proxyConfig = proxyConfig,
            )

        val encoded = json.encodeToString(backup)
        val decoded = json.decodeFromString<AppBackupData>(encoded)

        assertEquals("2.0.0", decoded.version)
        assertEquals(1700000000000L, decoded.exportedAtMs)
        assertEquals("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11", decoded.botConfig.botToken)
        assertEquals(setOf(11223344L, 99887766L), decoded.botConfig.allowedUserIds)
        assertEquals(setOf("+79991234567"), decoded.botConfig.allowedPhoneNumbers)
        assertTrue(decoded.botConfig.smsPinRequiredAlways)
        assertEquals("/storage/emulated/0/Download/RemoteBot", decoded.botConfig.storageDirectory)
        assertEquals(ProxyType.SOCKS5, decoded.proxyConfig.type)
        assertEquals("10.0.0.1", decoded.proxyConfig.host)
        assertEquals(1080, decoded.proxyConfig.port)
    }

    @Test
    fun testBotConfigWhitelistLogic() {
        val config =
            BotConfig(
                botToken = "test_token",
                allowedUserIds = setOf(100L, 200L),
                allowedPhoneNumbers = setOf("+79991112233"),
            )

        // Telegram User ID check
        assertTrue(config.allowedUserIds.contains(100L))
        assertTrue(config.allowedUserIds.contains(200L))
        assertFalse(config.allowedUserIds.contains(300L))

        // Phone number check
        assertTrue(config.allowedPhoneNumbers.contains("+79991112233"))
        assertFalse(config.allowedPhoneNumbers.contains("+79990000000"))
    }

    @Test
    fun testBotConfigExperimental2FaDefaultAndToggle() {
        val defaultConfig = BotConfig()
        assertFalse(defaultConfig.enable2FaInterception, "2FA interception should be disabled by default")

        val enabledConfig = defaultConfig.copy(enable2FaInterception = true)
        assertTrue(enabledConfig.enable2FaInterception, "2FA interception can be toggled on")
    }

    @Test
    fun testSmsSecurityTwoModes() {
        val config =
            BotConfig(
                botToken = "test_token",
                allowedPhoneNumbers = setOf("+79991112233"),
                smsPin = "1234",
            )

        fun isTrustedPhone(phone: String): Boolean {
            val cleanPhone = phone.filter { it.isDigit() }
            return config.allowedPhoneNumbers.any {
                val cleanAllowed = it.filter { c -> c.isDigit() }
                cleanPhone == cleanAllowed ||
                    (cleanPhone.length >= 10 && cleanAllowed.length >= 10 && cleanPhone.takeLast(10) == cleanAllowed.takeLast(10))
            }
        }

        fun parseSmsCommand(
            sender: String,
            body: String,
        ): String? {
            val text = body.trim()
            val pin = config.smsPin?.trim()
            val isTrusted = isTrustedPhone(sender)

            // SMS считается командой ТОЛЬКО если начинается с префикса "rb" (регистронезависимо: rb, RB, Rb, rB)
            val prefixRegex = "^(?i)rb(\\s+|/|:|$)(.*)".toRegex()
            val match = prefixRegex.find(text) ?: return null
            val delimiter = match.groupValues[1]
            val remainder = match.groupValues[2].trim()
            val content = if (delimiter == "/") "/$remainder" else remainder

            // Mode 1: PIN + Command from ANY number
            if (!pin.isNullOrBlank() && content.startsWith(pin)) {
                val afterPin = content.removePrefix(pin).trim()
                return if (afterPin.isNotEmpty()) {
                    if (afterPin.startsWith("/")) afterPin else "/$afterPin"
                } else {
                    "/status"
                }
            }
            // Mode 2: Trusted number without PIN
            if (isTrusted) {
                return if (content.isNotEmpty()) {
                    if (content.startsWith("/")) content else "/$content"
                } else {
                    "/status"
                }
            }
            return null
        }

        // Case 1: Все 4 варианта регистра префикса (rb, RB, Rb, rB) с PIN от ЛЮБОГО номера
        assertEquals("/wifi on", parseSmsCommand("+79009990000", "rb 1234 /wifi on"))
        assertEquals("/wifi on", parseSmsCommand("+79009990000", "RB 1234 /wifi on"))
        assertEquals("/wifi on", parseSmsCommand("+79009990000", "Rb 1234 /wifi on"))
        assertEquals("/wifi on", parseSmsCommand("+79009990000", "rB 1234 /wifi on"))
        assertEquals("/reboot", parseSmsCommand("+15551234567", "rb 1234 reboot"))
        assertEquals("/status", parseSmsCommand("+79009990000", "RB 1234 /status"))
        assertEquals("/wifi on", parseSmsCommand("+79009990000", "RB\n1234 /wifi on"))
        assertEquals("/wifi on", parseSmsCommand("+79009990000", "Rb: 1234 /wifi on"))

        // Case 2: Все 4 варианта регистра префикса (rb, RB, Rb, rB) от доверенного номера БЕЗ PIN
        assertEquals("/wifi on", parseSmsCommand("+79991112233", "rb /wifi on"))
        assertEquals("/wifi on", parseSmsCommand("+79991112233", "RB /wifi on"))
        assertEquals("/wifi on", parseSmsCommand("+79991112233", "Rb /wifi on"))
        assertEquals("/wifi on", parseSmsCommand("+79991112233", "rB /wifi on"))
        assertEquals("/wifi on", parseSmsCommand("+79991112233", "RB/wifi on"))
        assertEquals("/status", parseSmsCommand("89991112233", "rb status"))
        assertEquals("/ping", parseSmsCommand("+79991112233", "RB ping"))
        assertEquals("/status", parseSmsCommand("+79991112233", "rb"))
        assertEquals("/status", parseSmsCommand("+79991112233", "RB"))

        // Case 3: Trusted number WITH PIN with 'rb ' -> SUCCESS (Mode 1 triggers)
        assertEquals("/ping", parseSmsCommand("+79991112233", "rb 1234 /ping"))

        // Case 4: No 'rb ' prefix -> REJECTED (null, forwarded to TG)
        assertNull(parseSmsCommand("+79991112233", "/wifi on"))
        assertNull(parseSmsCommand("+79009990000", "1234 /wifi on"))
        assertNull(parseSmsCommand("+79991112233", "ping"))

        // Case 5: Untrusted number with 'rb ' WITHOUT PIN -> REJECTED (null, forwarded to TG)
        assertNull(parseSmsCommand("+79009990000", "rb /wifi on"))
        assertNull(parseSmsCommand("+79009990000", "rb reboot"))

        // Case 6: Untrusted number with 'rb ' and WRONG PIN -> REJECTED (null)
        assertNull(parseSmsCommand("+79009990000", "rb 9999 /wifi on"))

        // Case 7: Regular SMS messages -> REJECTED (null, forwarded to TG)
        assertNull(parseSmsCommand("+79009990000", "Привет, как дела?"))
        assertNull(parseSmsCommand("+79991112233", "Код подтверждения: 58291"))
    }

    @Test
    fun testNotificationForwardingFiltersAndDedup() {
        val config =
            BotConfig(
                botToken = "token123",
                notificationForwardingApps = setOf("org.telegram.messenger", "com.whatsapp", "com.bank.app"),
                notificationStopWords = listOf("спам", "код подтверждения", "OTP"),
                notificationDedupTimeoutSeconds = 15,
            )

        // 1. Serialization / Deserialization
        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<BotConfig>(encoded)
        assertEquals(config.notificationForwardingApps, decoded.notificationForwardingApps)
        assertEquals(config.notificationStopWords, decoded.notificationStopWords)
        assertEquals(15, decoded.notificationDedupTimeoutSeconds)

        // 2. Whitelist logic
        fun isAppForwarded(pkg: String): Boolean = config.notificationForwardingApps.contains(pkg)
        assertTrue(isAppForwarded("org.telegram.messenger"))
        assertTrue(isAppForwarded("com.whatsapp"))
        assertTrue(isAppForwarded("com.bank.app"))
        assertFalse(isAppForwarded("com.google.android.youtube"))
        assertFalse(isAppForwarded("com.android.systemui"))

        // 3. Stop-words filter logic
        fun containsStopWord(
            title: String?,
            text: String?,
        ): Boolean {
            val combined = "${title ?: ""} ${text ?: ""}"
            return config.notificationStopWords.any { word ->
                word.isNotBlank() && combined.contains(word.trim(), ignoreCase = true)
            }
        }
        // Matched stop words
        assertTrue(containsStopWord("Банк", "Ваш код подтверждения: 12345"))
        assertTrue(containsStopWord("Акция", "Это не спам, скидка 50%!"))
        assertTrue(containsStopWord("Security", "Your otp is 9988"))
        // Clean notifications (not matching stop words)
        assertFalse(containsStopWord("Мама", "Привет, перезвони мне"))
        assertFalse(containsStopWord("Telegram", "Новое сообщение от Ивана"))
        assertFalse(containsStopWord("Служба доставки", "Курьер прибыл"))

        // 4. Deduplication timeout logic
        val recentMap = mutableMapOf<String, Long>()

        fun shouldForward(
            pkg: String,
            title: String?,
            text: String?,
            currentTimeMs: Long,
        ): Boolean {
            if (!isAppForwarded(pkg)) return false
            if (containsStopWord(title, text)) return false

            val dedupKey = "$pkg|${title ?: ""}|${text ?: ""}"
            val timeoutMs = config.notificationDedupTimeoutSeconds * 1000L
            val lastSeen = recentMap[dedupKey]
            if (lastSeen != null && (currentTimeMs - lastSeen) < timeoutMs) {
                return false // duplicate suppressed
            }
            recentMap[dedupKey] = currentTimeMs
            return true
        }

        val baseTime = 1000000L

        // First notification arrives -> allowed
        assertTrue(shouldForward("com.whatsapp", "Иван", "Привет", baseTime))

        // Duplicate arrives 5 seconds later (within 15s window) -> suppressed
        assertFalse(shouldForward("com.whatsapp", "Иван", "Привет", baseTime + 5000L))

        // Different notification arrives 8 seconds later -> allowed
        assertTrue(shouldForward("com.whatsapp", "Иван", "Как дела?", baseTime + 8000L))

        // First duplicate arrives 16 seconds later (exceeded 15s timeout) -> allowed
        assertTrue(shouldForward("com.whatsapp", "Иван", "Привет", baseTime + 16000L))

        // Unselected app -> rejected immediately
        assertFalse(shouldForward("com.google.android.youtube", "Video", "New upload", baseTime + 20000L))

        // Selected app with stop word -> rejected
        assertTrue(containsStopWord("Банк", "Ваш OTP: 5544"))
        assertFalse(shouldForward("com.bank.app", "Банк", "Ваш OTP: 5544", baseTime + 20000L))

        // 5. Rate Limiting sliding window logic (max N events per minute)
        assertEquals(30, config.forwardRateLimitPerMinute)
        val rateLimitTimestamps = mutableMapOf<String, MutableList<Long>>()

        fun checkRateLimit(
            key: String,
            limitPerMinute: Int,
            currentTimeMs: Long,
        ): Boolean {
            if (limitPerMinute <= 0) return true
            val list = rateLimitTimestamps.getOrPut(key) { mutableListOf() }
            list.removeAll { currentTimeMs - it > 60_000L }
            if (list.size >= limitPerMinute) {
                return false // limit exceeded
            }
            list.add(currentTimeMs)
            return true
        }

        val testLimit = 3 // 3 в минуту для теста
        val testSender = "+79991112233"
        val startWindow = 2000000L

        // Первые 3 сообщения проходят
        assertTrue(checkRateLimit(testSender, testLimit, startWindow))
        assertTrue(checkRateLimit(testSender, testLimit, startWindow + 1000L))
        assertTrue(checkRateLimit(testSender, testLimit, startWindow + 2000L))

        // 4-е сообщение в ту же минуту блокируется
        assertFalse(checkRateLimit(testSender, testLimit, startWindow + 5000L))

        // Сообщение от другого отправителя проходит
        assertTrue(checkRateLimit("+79998887766", testLimit, startWindow + 6000L))

        // Спустя 61 секунду старые сообщения выпадают из окна -> снова проходит
        assertTrue(checkRateLimit(testSender, testLimit, startWindow + 61000L))

        // 6. Test replyUnauthorizedResponseEnabled default & toggle
        assertTrue(config.replyUnauthorizedResponseEnabled)
        val silentConfig = config.copy(replyUnauthorizedResponseEnabled = false)
        assertFalse(silentConfig.replyUnauthorizedResponseEnabled)
        val encodedSilent = json.encodeToString(silentConfig)
        val decodedSilent = json.decodeFromString<BotConfig>(encodedSilent)
        assertFalse(decodedSilent.replyUnauthorizedResponseEnabled)
    }

    @Test
    fun testSmsAndNotificationReplyHandling() {
        // 1. JSON parsing of reply_to_message in TgUpdate
        val jsonWithReply =
            """
            {
                "update_id": 200300,
                "message": {
                    "message_id": 105,
                    "chat": { "id": 12345, "type": "private" },
                    "date": 1700000000,
                    "text": "Скоро буду",
                    "reply_to_message": {
                        "message_id": 104,
                        "chat": { "id": 12345, "type": "private" },
                        "date": 1699999900,
                        "text": "📨 Входящее SMS от <code>+79991112233</code> [SIM 2] (20:50:00):\n«Ты где?»"
                    }
                }
            }
            """.trimIndent()

        val update = json.decodeFromString<TgUpdate>(jsonWithReply)
        val msg = update.message
        assertNotNull(msg)
        assertEquals("Скоро буду", msg.text)
        assertNotNull(msg.replyToMessage)

        val replyText = msg.replyToMessage?.text ?: ""
        val smsRegex = """Входящее SMS от (?:<code>)?([+0-9A-Za-z_-]+)(?:</code>)?""".toRegex()
        val smsMatch = smsRegex.find(replyText)
        assertNotNull(smsMatch)
        assertEquals("+79991112233", smsMatch.groupValues[1])

        val simRegex = """\[SIM (\d+)\]""".toRegex()
        val simSlot =
            simRegex
                .find(replyText)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        assertEquals(2, simSlot)

        // 2. Notification reply test
        val notifReplyText = "🔔 WhatsApp (20:51:00)\nМама\n«Перезвони мне»\n💬 Ответить: /answer 77 <текст>"
        val answerRegex = """/answer\s+(\d+)""".toRegex()
        val notifMatch = answerRegex.find(notifReplyText)
        assertNotNull(notifMatch)
        assertEquals("77", notifMatch.groupValues[1])
    }

    @Test
    fun testBotConfigFcmFieldsSerialization() {
        val config =
            BotConfig(
                botToken = "12345:TOKEN",
                connectionMode = ConnectionMode.PUSH_WAKEUP,
                googleServicesJson = "{\"project_info\":{\"project_id\":\"test-proj\"}}",
                fcmAppId = "1:12345:android:abcdef",
                fcmApiKey = "AIzaSyTestKey123",
                fcmProjectId = "test-proj",
                fcmSenderId = "1234567890",
            )

        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<BotConfig>(encoded)

        assertEquals("test-proj", decoded.fcmProjectId)
        assertEquals("1:12345:android:abcdef", decoded.fcmAppId)
        assertEquals("AIzaSyTestKey123", decoded.fcmApiKey)
        assertEquals("1234567890", decoded.fcmSenderId)
        assertEquals(ConnectionMode.PUSH_WAKEUP, decoded.connectionMode)
        assertNotNull(decoded.googleServicesJson)
    }

    @Test
    fun testBotConfigBatteryForwardingFieldsSerialization() {
        val config =
            BotConfig(
                botToken = "12345:TOKEN",
                tgForwardBatteryChargingEnabled = true,
                tgForwardBatteryLowEnabled = true,
                smsForwardBatteryChargingEnabled = true,
                smsForwardBatteryLowEnabled = true,
            )

        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<BotConfig>(encoded)

        assertTrue(decoded.tgForwardBatteryChargingEnabled)
        assertTrue(decoded.tgForwardBatteryLowEnabled)
        assertTrue(decoded.smsForwardBatteryChargingEnabled)
        assertTrue(decoded.smsForwardBatteryLowEnabled)
    }

    @Test
    fun testLowBatteryStepCalculation() {
        fun shouldNotify(
            prev: Int?,
            current: Int,
        ): Boolean {
            if (current > 20) return false
            if (prev == null) return true
            return (prev - current) >= 3 || (current <= 2 && prev > current)
        }

        var lastReported: Int? = null

        // Starts at 25% (normal)
        assertFalse(shouldNotify(lastReported, 25))

        // Drops to 20% -> First trigger at <= 20%
        assertTrue(shouldNotify(lastReported, 20))
        lastReported = 20

        // Drops to 19% -> difference is 1% (< 3%), do not notify
        assertFalse(shouldNotify(lastReported, 19))

        // Drops to 18% -> difference is 2% (< 3%), do not notify
        assertFalse(shouldNotify(lastReported, 18))

        // Drops to 17% -> difference is 3% (>= 3%), notify!
        assertTrue(shouldNotify(lastReported, 17))
        lastReported = 17

        // Drops to 14% -> notify!
        assertTrue(shouldNotify(lastReported, 14))
        lastReported = 14

        // Drops to 11% -> notify!
        assertTrue(shouldNotify(lastReported, 11))
        lastReported = 11

        // Drops to 8% -> notify!
        assertTrue(shouldNotify(lastReported, 8))
        lastReported = 8

        // Drops to 5% -> notify!
        assertTrue(shouldNotify(lastReported, 5))
        lastReported = 5

        // Drops to 2% -> notify!
        assertTrue(shouldNotify(lastReported, 2))
        lastReported = 2

        // Drops to 1% -> critical final step, notify!
        assertTrue(shouldNotify(lastReported, 1))
        lastReported = 1

        // Charged back to 25% -> resets
        lastReported = null
        assertFalse(shouldNotify(lastReported, 25))
    }

    @Test
    fun test2FaNotificationKeyboardGenerationByConfigFlag() {
        val actions =
            listOf(
                com.remotebot.domain.model.NotificationActionButton(0, "Подтвердить"),
                com.remotebot.domain.model.NotificationActionButton(1, "Отклонить"),
            )

        fun buildKeyboard(config: BotConfig): List<InlineKeyboardButton> {
            if (!config.enable2FaInterception || actions.isEmpty()) {
                return emptyList()
            }
            return actions.map { act ->
                val label =
                    when (act.title.lowercase()) {
                        "подтвердить", "approve", "accept", "yes", "да", "разрешить" -> "✅ ${act.title}"
                        "отклонить", "reject", "deny", "no", "нет", "запретить" -> "❌ ${act.title}"
                        else -> act.title
                    }
                InlineKeyboardButton(label, callbackData = "notif_act:123:${act.index}")
            }
        }

        // 1. По умолчанию выключено -> кнопок нет
        val defaultCfg = BotConfig()
        val buttonsDefault = buildKeyboard(defaultCfg)
        assertTrue(buttonsDefault.isEmpty(), "Кнопки 2FA не должны создаваться при выключенном флаге")

        // 2. Включено в настройках -> кнопки созданы с эмодзи
        val enabledCfg = defaultCfg.copy(enable2FaInterception = true)
        val buttonsEnabled = buildKeyboard(enabledCfg)
        assertEquals(2, buttonsEnabled.size)
        assertEquals("✅ Подтвердить", buttonsEnabled[0].text)
        assertEquals("notif_act:123:0", buttonsEnabled[0].callbackData)
        assertEquals("❌ Отклонить", buttonsEnabled[1].text)
        assertEquals("notif_act:123:1", buttonsEnabled[1].callbackData)
    }

    @Test
    fun testNotificationFifoEvictionAnd2FaProtection() {
        val recentMap = java.util.concurrent.ConcurrentHashMap<String, String>()
        val fifoQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val active2FaMap = java.util.concurrent.ConcurrentHashMap<String, String>()

        fun postNotif(id: String, is2Fa: Boolean) {
            recentMap[id] = "data_$id"
            fifoQueue.add(id)
            if (is2Fa) {
                active2FaMap[id] = "2fa_$id"
            }
            while (fifoQueue.size > 1000) {
                val oldest = fifoQueue.poll() ?: break
                if (!active2FaMap.containsKey(oldest)) {
                    recentMap.remove(oldest)
                }
            }
        }

        // Добавляем 2FA уведомление на шаге 100
        for (i in 1..99) postNotif(i.toString(), is2Fa = false)
        postNotif("100", is2Fa = true)
        for (i in 101..1500) postNotif(i.toString(), is2Fa = false)

        // Проверяем:
        // 1. Самое свежее уведомление "1500" на месте (нет бага моментального вытеснения)
        assertTrue(recentMap.containsKey("1500"), "Свежее уведомление не должно вытесняться")

        // 2. Старое обычное уведомление "50" вытеснено по FIFO
        assertFalse(recentMap.containsKey("50"), "Старое обычное уведомление должно вытесниться")

        // 3. 2FA уведомление "100" защищено и сохранено в 2FA кэше
        assertTrue(active2FaMap.containsKey("100"), "2FA уведомление защищено в выделенном кэше")
        assertTrue(recentMap.containsKey("100"), "2FA уведомление не удалено из recentMap")
    }

    @Test
    fun testUserSubscriptionSerialization() {
        val sub =
            UserSubscription(
                userId = 123456789L,
                receiveNotifications = true,
                receiveSms = false,
                receiveCalls = true,
                receiveSystem = false,
            )
        val encoded = json.encodeToString(sub)
        val decoded = json.decodeFromString<UserSubscription>(encoded)

        assertEquals(123456789L, decoded.userId)
        assertTrue(decoded.receiveNotifications)
        assertFalse(decoded.receiveSms)
        assertTrue(decoded.receiveCalls)
        assertFalse(decoded.receiveSystem)

        assertTrue(decoded.isSubscribedTo(SubscriptionEventType.NOTIFICATIONS))
        assertFalse(decoded.isSubscribedTo(SubscriptionEventType.SMS))
        assertTrue(decoded.isSubscribedTo(SubscriptionEventType.CALLS))
        assertFalse(decoded.isSubscribedTo(SubscriptionEventType.SYSTEM))
    }

    @Test
    fun testBotConfigUserSubscriptionFiltering() {
        val subscriberId = 111L
        val commandsOnlyId = 222L
        val unauthorizedId = 999L

        val config =
            BotConfig(
                allowedUserIds = setOf(subscriberId, commandsOnlyId),
                userSubscriptions =
                    mapOf(
                        commandsOnlyId to
                            UserSubscription(
                                userId = commandsOnlyId,
                                receiveNotifications = false,
                                receiveSms = false,
                                receiveCalls = false,
                                receiveSystem = false,
                            ),
                    ),
            )

        // 1. Подписчик с дефолтными настройками получает notif, sms, calls, но не system
        assertTrue(config.isUserSubscribedTo(subscriberId, SubscriptionEventType.NOTIFICATIONS))
        assertTrue(config.isUserSubscribedTo(subscriberId, SubscriptionEventType.SMS))
        assertTrue(config.isUserSubscribedTo(subscriberId, SubscriptionEventType.CALLS))
        assertFalse(config.isUserSubscribedTo(subscriberId, SubscriptionEventType.SYSTEM))

        // 2. Оператор («Только команды») не получает никаких фоновых событий
        assertFalse(config.isUserSubscribedTo(commandsOnlyId, SubscriptionEventType.NOTIFICATIONS))
        assertFalse(config.isUserSubscribedTo(commandsOnlyId, SubscriptionEventType.SMS))
        assertFalse(config.isUserSubscribedTo(commandsOnlyId, SubscriptionEventType.CALLS))
        assertFalse(config.isUserSubscribedTo(commandsOnlyId, SubscriptionEventType.SYSTEM))

        // 3. Неавторизованный пользователь не получает ничего
        assertFalse(config.isUserSubscribedTo(unauthorizedId, SubscriptionEventType.NOTIFICATIONS))
        assertFalse(config.isUserSubscribedTo(unauthorizedId, SubscriptionEventType.SMS))
    }

    @Test
    fun testOpenBotModeSubscriptions() {
        val openUserId = 555L
        val customUserId = 777L

        val config =
            BotConfig(
                allowedUserIds = emptySet(), // Открытый режим (белый список пуст)
                userSubscriptions =
                    mapOf(
                        customUserId to
                            UserSubscription(
                                userId = customUserId,
                                receiveNotifications = false,
                                receiveSms = false,
                                receiveCalls = false,
                                receiveSystem = true, // Только системные события
                            ),
                    ),
            )

        // Пользователь без явной записи получает стандартный набор: Notif, SMS, Calls
        assertTrue(config.isUserSubscribedTo(openUserId, SubscriptionEventType.NOTIFICATIONS))
        assertTrue(config.isUserSubscribedTo(openUserId, SubscriptionEventType.SMS))
        assertTrue(config.isUserSubscribedTo(openUserId, SubscriptionEventType.CALLS))
        assertFalse(config.isUserSubscribedTo(openUserId, SubscriptionEventType.SYSTEM))

        // Пользователь с явной подпиской получает только то, на что подписан
        assertFalse(config.isUserSubscribedTo(customUserId, SubscriptionEventType.NOTIFICATIONS))
        assertFalse(config.isUserSubscribedTo(customUserId, SubscriptionEventType.SMS))
        assertFalse(config.isUserSubscribedTo(customUserId, SubscriptionEventType.CALLS))
        assertTrue(config.isUserSubscribedTo(customUserId, SubscriptionEventType.SYSTEM))
    }

    @Test
    fun testSelectiveSubscriptions() {
        val smsOnlyId = 101L
        val notifOnlyId = 102L
        val allOnId = 103L

        val config =
            BotConfig(
                allowedUserIds = setOf(smsOnlyId, notifOnlyId, allOnId),
                userSubscriptions =
                    mapOf(
                        smsOnlyId to
                            UserSubscription(
                                userId = smsOnlyId,
                                receiveNotifications = false,
                                receiveSms = true,
                                receiveCalls = false,
                                receiveSystem = false,
                            ),
                        notifOnlyId to
                            UserSubscription(
                                userId = notifOnlyId,
                                receiveNotifications = true,
                                receiveSms = false,
                                receiveCalls = false,
                                receiveSystem = false,
                            ),
                        allOnId to
                            UserSubscription(
                                userId = allOnId,
                                receiveNotifications = true,
                                receiveSms = true,
                                receiveCalls = true,
                                receiveSystem = true,
                            ),
                    ),
            )

        // Проверяем «Только SMS»
        assertFalse(config.isUserSubscribedTo(smsOnlyId, SubscriptionEventType.NOTIFICATIONS))
        assertTrue(config.isUserSubscribedTo(smsOnlyId, SubscriptionEventType.SMS))
        assertFalse(config.isUserSubscribedTo(smsOnlyId, SubscriptionEventType.CALLS))
        assertFalse(config.isUserSubscribedTo(smsOnlyId, SubscriptionEventType.SYSTEM))

        // Проверяем «Только Push-уведомления»
        assertTrue(config.isUserSubscribedTo(notifOnlyId, SubscriptionEventType.NOTIFICATIONS))
        assertFalse(config.isUserSubscribedTo(notifOnlyId, SubscriptionEventType.SMS))
        assertFalse(config.isUserSubscribedTo(notifOnlyId, SubscriptionEventType.CALLS))
        assertFalse(config.isUserSubscribedTo(notifOnlyId, SubscriptionEventType.SYSTEM))

        // Проверяем «Всё включено (включая System)»
        assertTrue(config.isUserSubscribedTo(allOnId, SubscriptionEventType.NOTIFICATIONS))
        assertTrue(config.isUserSubscribedTo(allOnId, SubscriptionEventType.SMS))
        assertTrue(config.isUserSubscribedTo(allOnId, SubscriptionEventType.CALLS))
        assertTrue(config.isUserSubscribedTo(allOnId, SubscriptionEventType.SYSTEM))
    }

    @Test
    fun testUserSubscriptionTogglesAndImmutability() {
        val initial = UserSubscription(userId = 333L)
        // Дефолты: Notif=true, SMS=true, Calls=true, System=false
        assertTrue(initial.receiveNotifications)
        assertTrue(initial.receiveSms)
        assertTrue(initial.receiveCalls)
        assertFalse(initial.receiveSystem)

        // Симуляция действия all_off (Только управление)
        val allOff =
            initial.copy(
                receiveNotifications = false,
                receiveSms = false,
                receiveCalls = false,
                receiveSystem = false,
            )
        assertFalse(allOff.receiveNotifications)
        assertFalse(allOff.receiveSms)
        assertFalse(allOff.receiveCalls)
        assertFalse(allOff.receiveSystem)

        // Симуляция действия all_on
        val allOn =
            allOff.copy(
                receiveNotifications = true,
                receiveSms = true,
                receiveCalls = true,
                receiveSystem = false,
            )
        assertTrue(allOn.receiveNotifications)
        assertTrue(allOn.receiveSms)
        assertTrue(allOn.receiveCalls)
        assertFalse(allOn.receiveSystem)

        // Точечное переключение системных событий
        val systemToggled = allOn.copy(receiveSystem = !allOn.receiveSystem)
        assertTrue(systemToggled.receiveSystem)
    }

    @Test
    fun testBotConfigRepositoryDefaultSubscriptionMethod() {
        val testUserId = 444L
        val testConfig =
            BotConfig(
                allowedUserIds = setOf(testUserId),
                userSubscriptions =
                    mapOf(
                        testUserId to
                            UserSubscription(
                                userId = testUserId,
                                receiveNotifications = false,
                                receiveSms = true,
                                receiveCalls = false,
                                receiveSystem = true,
                            ),
                    ),
            )

        val repo =
            object : com.remotebot.domain.repository.BotConfigRepository {
                override val config = kotlinx.coroutines.flow.MutableStateFlow(testConfig)

                override suspend fun updateConfig(update: (BotConfig) -> BotConfig) {}

                override fun isUserAllowed(userId: Long): Boolean = true

                override fun isPhoneAllowed(phone: String): Boolean = true
            }

        // Проверяем, что дефолтный метод интерфейса делегирует в конфиг
        assertFalse(repo.isUserSubscribedTo(testUserId, SubscriptionEventType.NOTIFICATIONS))
        assertTrue(repo.isUserSubscribedTo(testUserId, SubscriptionEventType.SMS))
        assertFalse(repo.isUserSubscribedTo(testUserId, SubscriptionEventType.CALLS))
        assertTrue(repo.isUserSubscribedTo(testUserId, SubscriptionEventType.SYSTEM))
    }
}

