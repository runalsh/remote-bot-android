package com.remotebot.domain

import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.IncomingCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandParsingTest {
    private fun parseRawTelegramMessage(
        rawText: String,
        updateId: Long = 1L,
        senderId: String = "12345",
        chatId: Long = 12345L,
    ): IncomingCommand {
        val trimmed = rawText.trim()
        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val rawCommand = parts.firstOrNull() ?: ""
        val cleanCommandName = rawCommand.substringBefore("@").lowercase()
        val args = if (parts.size > 1) parts.drop(1) else emptyList()

        return IncomingCommand(
            id = "upd_$updateId",
            name = cleanCommandName,
            rawText = rawText,
            args = args,
            source = CommandSource.TELEGRAM,
            senderId = senderId,
            chatId = chatId,
        )
    }

    @Test
    fun testSimpleCommandParsing() {
        val cmd = parseRawTelegramMessage("/ping")
        assertEquals("/ping", cmd.name)
        assertTrue(cmd.args.isEmpty())
    }

    @Test
    fun testCommandWithBotMention() {
        val cmd = parseRawTelegramMessage("/battery@MyRemoteDeviceBot")
        assertEquals("/battery", cmd.name)
        assertTrue(cmd.args.isEmpty())
    }

    @Test
    fun testCommandWithArgumentsAndExtraWhitespace() {
        val cmd = parseRawTelegramMessage("   /adb_wifi    on    5555   ")
        assertEquals("/adb_wifi", cmd.name)
        assertEquals(listOf("on", "5555"), cmd.args)
    }

    @Test
    fun testShellCommandWithComplexArgs() {
        val cmd = parseRawTelegramMessage("/shell ls -la /sdcard/Download")
        assertEquals("/shell", cmd.name)
        assertEquals(listOf("ls", "-la", "/sdcard/Download"), cmd.args)
    }

    @Test
    fun testCaseInsensitiveCommandNormalization() {
        val cmd = parseRawTelegramMessage("/TOGGLES")
        assertEquals("/toggles", cmd.name)
    }
}
