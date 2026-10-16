package com.remotebot.domain

import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.TgUpdate
import com.remotebot.domain.push.PushWakeupHandler
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PushMessageProcessingAndRetryTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Имитация логики обработки входящих Push сообщений
    class TestPushWakeupHandler : PushWakeupHandler {
        val processedUpdates = mutableListOf<TgUpdate>()
        val receivedWakeupTargetIds = mutableListOf<Long>()

        override suspend fun processPushUpdate(update: TgUpdate) {
            processedUpdates.add(update)
        }

        override suspend fun handlePushWakeup(targetUpdateId: Long) {
            receivedWakeupTargetIds.add(targetUpdateId)
        }
    }

    @Test
    fun testPushRetryPolicyConfigDefaultsAndBounds() {
        val defaultConfig = BotConfig()
        assertEquals(3, defaultConfig.pushRetryMaxAttempts)
        assertEquals(5, defaultConfig.pushRetryDelaySeconds)

        // Пресет «Агрессивный»
        val aggressive = defaultConfig.copy(pushRetryMaxAttempts = 3, pushRetryDelaySeconds = 1)
        assertEquals(3, aggressive.pushRetryMaxAttempts)
        assertEquals(1, aggressive.pushRetryDelaySeconds)

        // Пресет «Умеренный»
        val moderate = defaultConfig.copy(pushRetryMaxAttempts = 5, pushRetryDelaySeconds = 5)
        assertEquals(5, moderate.pushRetryMaxAttempts)
        assertEquals(5, moderate.pushRetryDelaySeconds)

        // Пресет «Экономный»
        val eco = defaultConfig.copy(pushRetryMaxAttempts = 10, pushRetryDelaySeconds = 30)
        assertEquals(10, eco.pushRetryMaxAttempts)
        assertEquals(30, eco.pushRetryDelaySeconds)

        // Граничные значения слайдеров
        val clampedMin =
            defaultConfig.copy(
                pushRetryMaxAttempts = 0.coerceIn(1, 10),
                pushRetryDelaySeconds = 0.coerceIn(1, 60),
            )
        assertEquals(1, clampedMin.pushRetryMaxAttempts)
        assertEquals(1, clampedMin.pushRetryDelaySeconds)

        val clampedMax =
            defaultConfig.copy(
                pushRetryMaxAttempts = 99.coerceIn(1, 10),
                pushRetryDelaySeconds = 99.coerceIn(1, 60),
            )
        assertEquals(10, clampedMax.pushRetryMaxAttempts)
        assertEquals(60, clampedMax.pushRetryDelaySeconds)
    }

    @Test
    fun testParseAndProcessWakeupPushPayload() {
        val handler = TestPushWakeupHandler()

        // Симуляция получения data push с action = "wakeup" и update_id
        val data =
            mapOf(
                "action" to "wakeup",
                "update_id" to "12345678",
            )

        val action = data["action"]
        val updateIdStr = data["update_id"]
        val updateId = updateIdStr?.toLongOrNull() ?: 0L

        if (action == "wakeup" && updateId > 0) {
            kotlinx.coroutines.runBlocking {
                handler.handlePushWakeup(updateId)
            }
        }

        assertEquals(1, handler.receivedWakeupTargetIds.size)
        assertEquals(12345678L, handler.receivedWakeupTargetIds[0])
    }

    @Test
    fun testParseAndProcessUpdatePushPayloadWithJson() {
        val handler = TestPushWakeupHandler()

        val sampleUpdateJson =
            """
            {
                "update_id": 998877,
                "message": {
                    "message_id": 101,
                    "date": 1728640000,
                    "chat": {
                        "id": 1234567,
                        "type": "private"
                    },
                    "from": {
                        "id": 1234567,
                        "is_bot": false,
                        "first_name": "Admin"
                    },
                    "text": "/status"
                }
            }
            """.trimIndent()

        val data =
            mapOf(
                "action" to "update",
                "update_json" to sampleUpdateJson,
            )

        val action = data["action"]
        val updateJson = data["update_json"]

        if ((action == "wakeup" || action == "update") && !updateJson.isNullOrBlank()) {
            val update = json.decodeFromString<TgUpdate>(updateJson)
            kotlinx.coroutines.runBlocking {
                handler.processPushUpdate(update)
            }
        }

        assertEquals(1, handler.processedUpdates.size)
        val update = handler.processedUpdates[0]
        assertEquals(998877L, update.updateId)
        assertNotNull(update.message)
        assertEquals(101L, update.message?.messageId)
        assertEquals("/status", update.message?.text)
        assertEquals(1234567L, update.message?.from?.id)
    }

    @Test
    fun testMalformedPushJsonHandling() {
        val handler = TestPushWakeupHandler()

        val data =
            mapOf(
                "action" to "update",
                "update_json" to "{invalid_json_format",
            )

        val action = data["action"]
        val updateJson = data["update_json"]

        var errorCaught = false
        if (action == "update" && !updateJson.isNullOrBlank()) {
            try {
                val update = json.decodeFromString<TgUpdate>(updateJson)
                kotlinx.coroutines.runBlocking {
                    handler.processPushUpdate(update)
                }
            } catch (e: Exception) {
                errorCaught = true
            }
        }

        assertTrue(errorCaught)
        assertEquals(0, handler.processedUpdates.size)
    }
}
