package com.remotebot.channels.telegram.handlers

import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.CommandSource
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.provider.RootStatusProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaseCommandsHandlerTest {

    private class FakeRootStatusProvider(private val isRoot: Boolean = true) : RootStatusProvider {
        override val isRootGranted: StateFlow<Boolean?> = MutableStateFlow(isRoot)
        override val isRootSimulatedAbsent: StateFlow<Boolean> = MutableStateFlow(false)
        override suspend fun checkRoot(forceRefresh: Boolean): Boolean = isRoot
        override fun isRootGrantedSync(): Boolean = isRoot
        override fun setSimulateRootAbsent(simulatedAbsent: Boolean) {}
    }

    private fun createHandler(isRoot: Boolean = true): BaseCommandsHandler {
        return BaseCommandsHandler(
            rootStatusProvider = FakeRootStatusProvider(isRoot),
            appVersion = "2.1.0",
        )
    }

    private fun createCommand(name: String, args: List<String> = emptyList()): IncomingCommand {
        return IncomingCommand(
            id = "test_cmd_1",
            name = name,
            rawText = name,
            args = args,
            source = CommandSource.TELEGRAM,
            senderId = "123456",
            chatId = 123456L,
        )
    }

    @Test
    fun testMainMenuGenerationForStartAndHelp() {
        runBlocking {
            val handler = createHandler()
            for (cmdName in listOf("start", "help", "menu_main")) {
                val result = handler.execute(createCommand(cmdName))
                assertTrue(result is CommandResult.Text, "Result for $cmdName should be Text")
                val textResult = result as CommandResult.Text
                assertTrue(textResult.message.contains("Remote Bot Android"), "Must contain bot title")
                assertTrue(textResult.message.contains("v2.1.0"), "Must contain version")
                assertTrue(textResult.message.contains("Root статус:</b> ✅ АКТИВЕН"), "Must reflect root status")

                val keyboard = textResult.keyboard
                assertNotNull(keyboard, "Main menu must have keyboard")
                val allButtons = keyboard.inlineKeyboard.flatten()

                val expectedCallbacks = listOf(
                    "/menu_cat_base",
                    "/menu_cat_media",
                    "/menu_cat_comm",
                    "/menu_cat_geo_sec",
                    "/menu_cat_files",
                    "/menu_cat_system",
                    "/menu_cat_auto",
                    "/toggles",
                    "/menu_all",
                    "/status",
                )
                for (expected in expectedCallbacks) {
                    assertTrue(
                        allButtons.any { it.callbackData == expected },
                        "Main menu keyboard missing button with callbackData $expected",
                    )
                }
            }
        }
    }

    @Test
    fun testCategoryBaseContainsRequiredCommandsAndBack() {
        runBlocking {
            val handler = createHandler()
            val result = handler.execute(createCommand("menu_cat_base"))
            assertTrue(result is CommandResult.Text)
            val textResult = result as CommandResult.Text
            val required = listOf("/status", "/permissions", "/ping", "/echo", "/chat_id", "/about", "/uptime")
            for (cmd in required) {
                assertTrue(textResult.message.contains(cmd), "Base category missing $cmd")
            }
            val backBtn = textResult.keyboard?.inlineKeyboard?.flatten()?.find { it.callbackData == "/menu_main" }
            assertNotNull(backBtn, "Must contain Back to main menu button")
        }
    }

    @Test
    fun testCategoryMediaContainsRequiredCommandsAndBack() {
        runBlocking {
            val handler = createHandler()
            val result = handler.execute(createCommand("menu_cat_media"))
            assertTrue(result is CommandResult.Text)
            val textResult = result as CommandResult.Text
            val required = listOf(
                "/photo", "/videorec", "/gallery", "/rec", "/rec_file",
                "/tts", "/player", "/volume", "/ring_mode", "/device_search",
                "/screenshot", "/screen_rec",
            )
            for (cmd in required) {
                assertTrue(textResult.message.contains(cmd), "Media category missing $cmd")
            }
            val backBtn = textResult.keyboard?.inlineKeyboard?.flatten()?.find { it.callbackData == "/menu_main" }
            assertNotNull(backBtn, "Must contain Back to main menu button")
        }
    }

    @Test
    fun testCategoryCommContainsRequiredCommandsAndBack() {
        runBlocking {
            val handler = createHandler()
            val result = handler.execute(createCommand("menu_cat_comm"))
            assertTrue(result is CommandResult.Text)
            val textResult = result as CommandResult.Text
            val required = listOf(
                "/sms", "/sms_inbox", "/call", "/callshistory", "/ussd",
                "/notify", "/notify_send", "/fake_notify", "/answer", "/notify_clear", "/notify_rule",
            )
            for (cmd in required) {
                assertTrue(textResult.message.contains(cmd), "Comm category missing $cmd")
            }
            val backBtn = textResult.keyboard?.inlineKeyboard?.flatten()?.find { it.callbackData == "/menu_main" }
            assertNotNull(backBtn, "Must contain Back to main menu button")
        }
    }

    @Test
    fun testCategoryGeoSecContainsRequiredCommandsAndBack() {
        runBlocking {
            val handler = createHandler()
            val result = handler.execute(createCommand("menu_cat_geo_sec"))
            assertTrue(result is CommandResult.Text)
            val textResult = result as CommandResult.Text
            val required = listOf("/location", "/track", "/shake_detector", "/detectmotion", "/sound_detector")
            for (cmd in required) {
                assertTrue(textResult.message.contains(cmd), "GeoSec category missing $cmd")
            }
            val backBtn = textResult.keyboard?.inlineKeyboard?.flatten()?.find { it.callbackData == "/menu_main" }
            assertNotNull(backBtn, "Must contain Back to main menu button")
        }
    }

    @Test
    fun testCategoryFilesContainsRequiredCommandsAndBack() {
        runBlocking {
            val handler = createHandler()
            val result = handler.execute(createCommand("menu_cat_files"))
            assertTrue(result is CommandResult.Text)
            val textResult = result as CommandResult.Text
            val required = listOf(
                "/get_file", "/download", "/ls", "/ls_interactive",
                "/apps", "/app_launch", "/install", "/taskmanager", "/kill",
            )
            for (cmd in required) {
                assertTrue(textResult.message.contains(cmd), "Files category missing $cmd")
            }
            val backBtn = textResult.keyboard?.inlineKeyboard?.flatten()?.find { it.callbackData == "/menu_main" }
            assertNotNull(backBtn, "Must contain Back to main menu button")
        }
    }

    @Test
    fun testCategorySystemContainsRequiredCommandsAndBack() {
        runBlocking {
            val handler = createHandler()
            val result = handler.execute(createCommand("menu_cat_system"))
            assertTrue(result is CommandResult.Text)
            val textResult = result as CommandResult.Text
            val required = listOf(
                "/toggles", "/system_info", "/uptime", "/battery", "/network",
                "/wifi", "/bluetooth", "/flashlight", "/airplane", "/hotspot",
                "/mobile_data", "/gps", "/powersave", "/auto_brightness", "/clipboard",
                "/sync", "/adb", "/adb_wifi", "/shell", "/reboot", "/shutdown", "/recovery", "/fastboot",
            )
            for (cmd in required) {
                assertTrue(textResult.message.contains(cmd), "System category missing $cmd")
            }
            val backBtn = textResult.keyboard?.inlineKeyboard?.flatten()?.find { it.callbackData == "/menu_main" }
            assertNotNull(backBtn, "Must contain Back to main menu button")
        }
    }

    @Test
    fun testCategoryAutoContainsRequiredCommandsAndBack() {
        runBlocking {
            val handler = createHandler()
            val result = handler.execute(createCommand("menu_cat_auto"))
            assertTrue(result is CommandResult.Text)
            val textResult = result as CommandResult.Text
            val required = listOf(
                "/tasker", "/script", "/timers", "/proxy", "/active", "/stop_all",
                "/log", "/backup", "/restore_backup", "/unsent_files", "/bot_status",
                "/tg_bot", "/sms_bot", "/bot_toggle", "/add_user", "/add_phone",
                "/add_forward", "/tg_forward", "/events", "/whitelist_user", "/whitelist_phone", "/sms_forward",
            )
            for (cmd in required) {
                assertTrue(textResult.message.contains(cmd), "Auto category missing $cmd")
            }
            val backBtn = textResult.keyboard?.inlineKeyboard?.flatten()?.find { it.callbackData == "/menu_main" }
            assertNotNull(backBtn, "Must contain Back to main menu button")
        }
    }

    @Test
    fun testMenuAllContainsFullDumpAndBack() {
        runBlocking {
            val handler = createHandler()
            val result = handler.execute(createCommand("menu_all"))
            assertTrue(result is CommandResult.Text)
            val textResult = result as CommandResult.Text
            assertTrue(textResult.message.contains("Базовые команды:"), "Must contain base header")
            assertTrue(textResult.message.contains("Медиа, Звук и Камера:"), "Must contain media header")
            assertTrue(textResult.message.contains("Связь, SMS & Уведомления:"), "Must contain comm header")
            assertTrue(textResult.message.contains("Геолокация и Охрана:"), "Must contain geo header")
            assertTrue(textResult.message.contains("Файлы и Приложения:"), "Must contain files header")
            assertTrue(textResult.message.contains("Автоматизация, Tasker и Скрипты:"), "Must contain auto header")
            assertTrue(textResult.message.contains("Системный контроль и Root:"), "Must contain system header")

            val backBtn = textResult.keyboard?.inlineKeyboard?.flatten()?.find { it.callbackData == "/menu_main" }
            assertNotNull(backBtn, "Must contain Back to main menu button")
        }
    }

    @Test
    fun testAll54CommandsCoveredInCategories() {
        runBlocking {
            val handler = createHandler()
            val categoryCommands = listOf(
                "menu_cat_base",
                "menu_cat_media",
                "menu_cat_comm",
                "menu_cat_geo_sec",
                "menu_cat_files",
                "menu_cat_system",
                "menu_cat_auto",
            )
            val combinedText = buildString {
                for (cat in categoryCommands) {
                    val res = handler.execute(createCommand(cat)) as CommandResult.Text
                    appendLine(res.message)
                }
            }

            // Complete list of 54 primary bot commands from FEATURES.md / REWRITE.md
            val all54PrimaryCommands = listOf(
                "/status", "/permissions", "/ping", "/echo", "/chat_id", "/about", "/uptime",
                "/photo", "/videorec", "/gallery", "/rec", "/rec_file", "/tts", "/player", "/volume", "/ring_mode", "/device_search", "/screenshot", "/screen_rec",
                "/sms", "/sms_inbox", "/call", "/callshistory", "/ussd", "/notify", "/notify_send", "/fake_notify", "/answer", "/notify_clear", "/notify_rule",
                "/location", "/track", "/shake_detector", "/detectmotion", "/sound_detector",
                "/get_file", "/download", "/ls", "/ls_interactive", "/apps", "/app_launch", "/install", "/taskmanager", "/kill",
                "/toggles", "/system_info", "/battery", "/network", "/wifi", "/bluetooth", "/flashlight", "/airplane", "/hotspot", "/mobile_data", "/gps", "/powersave", "/auto_brightness", "/clipboard", "/sync", "/adb", "/adb_wifi", "/shell", "/reboot", "/shutdown", "/recovery", "/fastboot",
                "/tasker", "/script", "/timers", "/proxy", "/active", "/stop_all", "/log", "/backup", "/restore_backup", "/unsent_files", "/bot_status", "/tg_bot", "/sms_bot", "/bot_toggle", "/add_user", "/add_phone", "/add_forward", "/tg_forward", "/events", "/whitelist_user", "/whitelist_phone", "/sms_forward"
            )

            for (cmd in all54PrimaryCommands) {
                assertTrue(combinedText.contains(cmd), "Command $cmd is missing from categorized menus!")
            }
        }
    }
}
