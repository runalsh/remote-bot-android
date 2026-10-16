package com.remotebot.domain

import com.remotebot.domain.parser.AgconnectJsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgconnectJsonParserTest {
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

    @Test
    fun `parse valid agconnect json extracts correct fields`() {
        val result = AgconnectJsonParser.parse(validAgconnectJson)
        assertTrue(result.isSuccess, "Парсинг должен завершиться успешно")

        val config = result.getOrThrow()
        assertEquals("105589999", config.appId)
        assertEquals("105589999", config.clientId)
        assertEquals("secret_abc_123", config.clientSecret)
        assertEquals("CBwQAABTESTAPIKEY999", config.apiKey)
        assertEquals("com.remotebot.app", config.packageName)
    }

    @Test
    fun `parse invalid agconnect json fails gracefully`() {
        val result = AgconnectJsonParser.parse("{}")
        assertTrue(result.isFailure, "Пустой JSON должен возвращать ошибку")
    }
}
