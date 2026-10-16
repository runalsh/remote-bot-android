package com.remotebot.core.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicHmsManagerTest {
    private val validAgconnectJson =
        """
        {
          "agcgw": {
            "backurl": "connect-drcn.hispace.hicloud.com",
            "url": "connect-drcn.hispace.hicloud.com"
          },
          "client": {
            "cp_id": "123456789",
            "product_id": "987654321",
            "client_id": "105589999",
            "client_secret": "secret_abc_123",
            "app_id": "105589999",
            "api_key": "CBwQAABTESTAPIKEY999",
            "package_name": "com.remotebot.app"
          },
          "service": {
            "analytics": {
              "channel_id": ""
            }
          },
          "region": "RU"
        }
        """.trimIndent()

    private val alternativeFormatAgconnectJson =
        """
        {
          "app_info": {
            "app_id": "999888777"
          },
          "client": {
            "client_id": "client_999888",
            "client_secret": "sec_456",
            "api_key": "key_789",
            "package_name": "com.remotebot.app"
          }
        }
        """.trimIndent()

    @Test
    fun `parseAgconnectJson extracts all fields correctly`() {
        val result = DynamicHmsManager.parseAgconnectJson(validAgconnectJson)
        assertTrue(result.isSuccess, "Парсинг валидного JSON должен быть успешным")

        val config = result.getOrThrow()
        assertEquals("105589999", config.appId)
        assertEquals("105589999", config.clientId)
        assertEquals("secret_abc_123", config.clientSecret)
        assertEquals("CBwQAABTESTAPIKEY999", config.apiKey)
        assertEquals("com.remotebot.app", config.packageName)
        assertEquals(validAgconnectJson, config.rawJson)
    }

    @Test
    fun `parseAgconnectJson extracts app_id from app_info fallback`() {
        val result = DynamicHmsManager.parseAgconnectJson(alternativeFormatAgconnectJson)
        assertTrue(result.isSuccess)

        val config = result.getOrThrow()
        assertEquals("999888777", config.appId)
        assertEquals("client_999888", config.clientId)
        assertEquals("sec_456", config.clientSecret)
        assertEquals("key_789", config.apiKey)
    }

    @Test
    fun `parseAgconnectJson handles malformed json gracefully`() {
        val result = DynamicHmsManager.parseAgconnectJson("{not a valid json}")
        assertTrue(result.isFailure, "Невалидный JSON должен приводить к ошибке")
    }

    @Test
    fun `parseAgconnectJson handles empty json object`() {
        val result = DynamicHmsManager.parseAgconnectJson("{}")
        assertTrue(result.isFailure, "Пустой JSON объект должен приводить к ошибке отсутствия секции client")
    }

    @Test
    fun `parseAgconnectJson handles missing appId in client and app_info`() {
        val jsonWithoutAppId =
            """
            {
              "client": {
                "client_id": "123",
                "api_key": "abc"
              }
            }
            """.trimIndent()
        val result = DynamicHmsManager.parseAgconnectJson(jsonWithoutAppId)
        assertTrue(result.isFailure, "Отсутствие app_id должно приводить к ошибке")
    }

    @Test
    fun `isInitialized returns false when AGConnectInstance is not configured`() {
        // In uninitialized JUnit JVM environment, call returns false without crashing
        val initialized = DynamicHmsManager.isInitialized()
        assertFalse(initialized, "Без предварительной инициализации должен возвращаться false")
    }
}
