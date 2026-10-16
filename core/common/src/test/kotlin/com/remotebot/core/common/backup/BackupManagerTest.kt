package com.remotebot.core.common.backup

import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.ProxyConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupManagerTest {
    @Test
    fun testExportAndImportBackup() {
        val manager = BackupManager()
        val botConfig =
            BotConfig(
                botToken = "123456:ABC-DEF",
                allowedUserIds = setOf(123456789L),
                storageDirectory = "/sdcard/Download",
            )
        val proxyConfig = ProxyConfig()

        val json = manager.exportBackup(botConfig, proxyConfig)
        assertNotNull(json)
        assertTrue(json.isNotBlank())

        val result = manager.parseBackup(json)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("123456:ABC-DEF", data.botConfig.botToken)
        assertEquals(setOf(123456789L), data.botConfig.allowedUserIds)
    }
}
