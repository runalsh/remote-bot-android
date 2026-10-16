package com.remotebot.domain

import com.remotebot.domain.model.FcmPushPayload
import com.remotebot.domain.model.TgUpdate
import com.remotebot.domain.parser.GoogleServicesJsonParser
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleServicesJsonParserTest {
    private val sampleGoogleServicesJson =
        """
        {
          "project_info": {
            "project_number": "1029384756",
            "project_id": "remotebot-test-project",
            "storage_bucket": "remotebot-test-project.appspot.com"
          },
          "client": [
            {
              "client_info": {
                "mobilesdk_app_id": "1:1029384756:android:client1app",
                "android_client_info": {
                  "package_name": "com.example.otherapp"
                }
              },
              "oauth_client": [],
              "api_key": [
                {
                  "current_key": "AIzaSyOtherAppKey999"
                }
              ],
              "services": {}
            },
            {
              "client_info": {
                "mobilesdk_app_id": "1:1029384756:android:client2remotebot",
                "android_client_info": {
                  "package_name": "com.remotebot.app"
                }
              },
              "oauth_client": [],
              "api_key": [
                {
                  "current_key": "AIzaSyRemoteBotOfficialKey888"
                }
              ],
              "services": {}
            }
          ],
          "configuration_version": "1"
        }
        """.trimIndent()

    @Test
    fun testParseValidGoogleServicesJsonMatchingPackage() {
        val result = GoogleServicesJsonParser.parse(sampleGoogleServicesJson, targetPackage = "com.remotebot.app")

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()

        assertEquals("remotebot-test-project", config.projectId)
        assertEquals("1029384756", config.gcmSenderId)
        assertEquals("1:1029384756:android:client2remotebot", config.appId)
        assertEquals("AIzaSyRemoteBotOfficialKey888", config.apiKey)
    }

    @Test
    fun testParseGoogleServicesJsonFallbackToFirstClient() {
        val result = GoogleServicesJsonParser.parse(sampleGoogleServicesJson, targetPackage = null)

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()

        assertEquals("remotebot-test-project", config.projectId)
        assertEquals("1029384756", config.gcmSenderId)
        assertEquals("1:1029384756:android:client1app", config.appId)
        assertEquals("AIzaSyOtherAppKey999", config.apiKey)
    }

    @Test
    fun testMissingProjectInfoThrowsFailure() {
        val badJson =
            """
            {
              "client": []
            }
            """.trimIndent()

        val result = GoogleServicesJsonParser.parse(badJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("project_info") == true)
    }

    @Test
    fun testMissingClientsThrowsFailure() {
        val badJson =
            """
            {
              "project_info": {
                "project_number": "123",
                "project_id": "proj"
              },
              "client": []
            }
            """.trimIndent()

        val result = GoogleServicesJsonParser.parse(badJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("client") == true)
    }

    @Test
    fun testMissingApiKeyThrowsFailure() {
        val badJson =
            """
            {
              "project_info": {
                "project_number": "123",
                "project_id": "proj"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:123:android:xyz"
                  },
                  "api_key": []
                }
              ]
            }
            """.trimIndent()

        val result = GoogleServicesJsonParser.parse(badJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("api_key") == true)
    }

    @Test
    fun testMalformedJsonThrowsFailure() {
        val brokenJson = "not a valid json { broken"
        val result = GoogleServicesJsonParser.parse(brokenJson)
        assertTrue(result.isFailure)
    }

    @Test
    fun testFcmPushWakeupPayloadParsing() {
        val payloadMap =
            mapOf(
                "action" to "wakeup",
                "update_id" to "554433",
            )

        val action = payloadMap["action"]
        val updateId = payloadMap["update_id"]?.toLongOrNull()

        assertEquals("wakeup", action)
        assertEquals(554433L, updateId)
    }

    @Test
    fun testFcmPushUpdateJsonPayloadParsing() {
        val json = Json { ignoreUnknownKeys = true }
        val updateJsonContent =
            """
            {
              "update_id": 998877,
              "message": {
                "message_id": 42,
                "chat": { "id": 111, "type": "private" },
                "date": 1700000000,
                "text": "/location"
              }
            }
            """.trimIndent()

        val payload =
            FcmPushPayload(
                action = "update",
                updateId = 998877L,
                updateJson = updateJsonContent,
            )

        assertEquals("update", payload.action)
        assertEquals(998877L, payload.updateId)

        val parsedUpdate = json.decodeFromString<TgUpdate>(payload.updateJson!!)
        assertEquals(998877L, parsedUpdate.updateId)
        assertEquals("/location", parsedUpdate.message?.text)
        assertEquals(111L, parsedUpdate.message?.chat?.id)
    }
}
