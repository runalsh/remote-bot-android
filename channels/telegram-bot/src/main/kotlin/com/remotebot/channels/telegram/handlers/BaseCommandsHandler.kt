package com.remotebot.channels.telegram.handlers

import com.remotebot.channels.telegram.api.TelegramApiClient
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.core.network.diagnostic.NetworkDiagnosticManager
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.provider.PermissionsStatusProvider
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository

class BaseCommandsHandler(
    private val rootStatusProvider: RootStatusProvider,
    private val botConfigRepository: BotConfigRepository? = null,
    private val apiClient: TelegramApiClient? = null,
    private val diagnosticManager: NetworkDiagnosticManager? = null,
    private val permissionsStatusProvider: PermissionsStatusProvider? = null,
    private val appVersion: String = "0.1.0",
) : CommandHandler {
    override val commandNames: List<String> =
        listOf(
            "start",
            "help",
            "about",
            "chat_id",
            "ping",
            "echo",
            "status",
            "permissions",
            "menu_main",
            "menu_all",
            "menu_cat_base",
            "menu_cat_media",
            "menu_cat_comm",
            "menu_cat_geo_sec",
            "menu_cat_files",
            "menu_cat_system",
            "menu_cat_auto",
        )
    override val isDestructive: Boolean = false

    private val strings get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: com.remotebot.domain.model.BotLanguage.AUTO)

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        val versionDisplay = if (appVersion.startsWith("v")) appVersion else "v$appVersion"
        return when (cmd) {
            "permissions" -> handlePermissions(command.args)
            "start", "help", "menu_main" -> handleMainMenu(versionDisplay)
            "menu_all" -> handleFullHelp(versionDisplay)
            "menu_cat_base" -> handleCategoryBase()
            "menu_cat_media" -> handleCategoryMedia()
            "menu_cat_comm" -> handleCategoryComm()
            "menu_cat_geo_sec" -> handleCategoryGeoSec()
            "menu_cat_files" -> handleCategoryFiles()
            "menu_cat_system" -> handleCategorySystem()
            "menu_cat_auto" -> handleCategoryAuto()
            "about" -> {
                val isRoot = rootStatusProvider.isRootGrantedSync()
                val rootText = if (isRoot) strings.bsRootAvail() else strings.bsRootMissing()
                CommandResult.Text(strings.aboutTitle(versionDisplay, rootText))
            }
            "chat_id" -> {
                CommandResult.Text(
                    "Chat ID: <code>${command.chatId}</code>\nUser ID: <code>${command.senderId}</code>",
                )
            }
            "status" -> {
                val isRoot = rootStatusProvider.isRootGrantedSync()
                val config = botConfigRepository?.config?.value
                val activeEndpoint = apiClient?.lastActiveEndpointUrl ?: (config?.customApiRoot ?: "https://api.telegram.org")
                val mode = config?.connectionMode?.name ?: "LONG_POLLING"
                
                // Check ping of active Telegram API endpoint
                val apiDiag = diagnosticManager?.checkHttpEndpoint(activeEndpoint)
                val apiStatusStr =
                    if (apiDiag != null && apiDiag.isReachable) {
                        strings.bsApiOk(apiDiag.latencyMs)
                    } else {
                        strings.bsApiErr(apiDiag?.error ?: strings.bsTimeoutWord())
                    }

                val strategyStr = config?.endpointSelectionStrategy?.name ?: "FAILOVER"
                val candidateRoots = apiClient?.getCandidateRoots() ?: emptyList()

                val text =
                    buildString {
                        appendLine(strings.statusTitle())
                        appendLine()
                        appendLine(strings.statusServiceRunning())
                        appendLine(strings.statusRoot(isRoot))
                        appendLine(strings.statusConnectionMode(mode))
                        appendLine()
                        appendLine(strings.statusTelegramApiTitle())
                        appendLine(strings.statusActiveNode(apiStatusStr))
                        appendLine(strings.bsIndented(activeEndpoint))
                        appendLine(strings.statusStrategy(strategyStr))
                        appendLine(strings.statusEndpointsPool())
                        if (candidateRoots.isNotEmpty()) {
                            candidateRoots.forEach { root ->
                                appendLine(strings.bsEndpointItem(root))
                            }
                        } else {
                            appendLine(strings.bsEndpointItem(activeEndpoint))
                        }
                        appendLine()
                        appendLine(strings.bsPushTitle())
                        val gwUrl = config?.pushGatewayUrl
                        if (!gwUrl.isNullOrBlank()) {
                            val gwDiag = diagnosticManager?.checkHttpEndpoint(gwUrl)
                            val gwStatus =
                                if (gwDiag != null && gwDiag.isReachable) {
                                    strings.bsLatency(gwDiag.latencyMs)
                                } else {
                                    strings.bsTimeout()
                                }
                            appendLine(strings.bsGwLine(gwStatus))
                            appendLine(strings.bsIndented(gwUrl))
                        }
                        val fcmDiag = diagnosticManager?.pingTarget(NetworkDiagnosticManager.FCM_ENDPOINT, 2500)
                        val fcmStatus =
                            if (fcmDiag != null && fcmDiag.isReachable) {
                                strings.bsLatency(fcmDiag.latencyMs)
                            } else {
                                strings.bsUnreachable()
                            }
                        appendLine(strings.bsFcmLine(fcmStatus))

                        val hmsDiag = diagnosticManager?.pingTarget(NetworkDiagnosticManager.HMS_ENDPOINT, 2500)
                        val hmsStatus =
                            if (hmsDiag != null && hmsDiag.isReachable) {
                                strings.bsLatency(hmsDiag.latencyMs)
                            } else {
                                strings.bsUnreachable()
                            }
                        appendLine(strings.bsHmsLine(hmsStatus))
                    }.trimEnd()

                val keyboard =
                    InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                InlineKeyboardButton(strings.btnBack(), "/menu_main"),
                                InlineKeyboardButton(strings.btnToggles(), "/toggles"),
                            ),
                            listOf(
                                InlineKeyboardButton(strings.bsRefreshStatus(), "/status"),
                            ),
                        ),
                    )

                CommandResult.Text(text, keyboard = keyboard)
            }

            "ping" -> {
                val now = System.currentTimeMillis()
                val delta = now - command.receivedAtMs
                CommandResult.Text(strings.bsPing(delta))
            }
            "echo" -> {
                val text = command.args.joinToString(" ")
                                val emptyMsg = strings.bsEchoEmpty()
                val echoPrefix = strings.bsEchoPrefix()
                CommandResult.Text(if (text.isBlank()) emptyMsg else "$echoPrefix$text")
            }
            else -> CommandResult.Error(strings.bsUnknownCmd(cmd))
        }
    }

    private suspend fun handlePermissions(args: List<String>): CommandResult {
        val provider =
            permissionsStatusProvider
                ?: return CommandResult.Error(strings.bsPermProviderMissing())

        val filter = args.firstOrNull()?.lowercase()
        val allPermissions = provider.getPermissionsStatus()

        val filtered =
            when (filter) {
                "critical" -> allPermissions.filter { it.group.equals("critical", ignoreCase = true) }
                "confidential", "privacy" -> allPermissions.filter { it.group.equals("confidential", ignoreCase = true) }
                "standard", "other" -> allPermissions.filter { it.group.equals("standard", ignoreCase = true) }
                "missing", "denied" -> allPermissions.filter { !it.isGranted }
                else -> allPermissions
            }

        val grantedCount = allPermissions.count { it.isGranted }
        val totalCount = allPermissions.size

        val text =
            buildString {
                appendLine(strings.permissionsTitle(grantedCount, totalCount))
                if (!filter.isNullOrBlank()) {
                    appendLine(strings.filterLabel(filter))
                }
                appendLine()

                val grouped = filtered.groupBy { it.group.lowercase() }
                val groupOrder = listOf("critical", "confidential", "standard")
                for (groupKey in groupOrder) {
                    val list = grouped[groupKey] ?: continue
                    val groupTitle =
                        when (groupKey) {
                            "critical" -> strings.groupCriticalTitle()
                            "confidential" -> strings.groupConfidentialTitle()
                            else -> strings.groupStandardTitle()
                        }
                    appendLine(groupTitle)
                    for (item in list) {
                        val icon = if (item.isGranted) "✅" else "❌"
                        appendLine(strings.bsPermLine(icon, strings.permName(item.id), if (item.isGranted) strings.statusGranted() else strings.statusDenied()))
                        val permDesc = strings.permDesc(item.id)
                        if (!item.isGranted && permDesc.isNotBlank()) {
                            appendLine("   ↳ <i>$permDesc</i>")
                        }
                    }
                    appendLine()
                }

                if (filtered.isEmpty()) {
                    appendLine(strings.noPermissionsFound())
                    appendLine()
                }

                appendLine(strings.permissionsUsageHint())
            }.trimEnd()

        return CommandResult.Text(text)
    }

    private fun createCategoryKeyboard(): InlineKeyboardMarkup =
        InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton(strings.btnBack(), "/menu_main"),
                    InlineKeyboardButton(strings.btnStatus(), "/status"),
                ),
            ),
        )

    private fun handleMainMenu(versionDisplay: String): CommandResult {
        val isRoot = rootStatusProvider.isRootGrantedSync()
        val rootText = if (isRoot) strings.bsRootActive0() else strings.bsRootMissing()
        val text =
            """
            ${strings.startMenuTitle(versionDisplay, rootText)}

            ${strings.startMenuSubtitle()}
            """.trimIndent()

        val keyboard =
            InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton(strings.btnBasic(), "/menu_cat_base"),
                        InlineKeyboardButton(strings.btnMedia(), "/menu_cat_media"),
                    ),
                    listOf(
                        InlineKeyboardButton(strings.btnComm(), "/menu_cat_comm"),
                        InlineKeyboardButton(strings.btnGeoSec(), "/menu_cat_geo_sec"),
                    ),
                    listOf(
                        InlineKeyboardButton(strings.btnFiles(), "/menu_cat_files"),
                        InlineKeyboardButton(strings.btnSystem(), "/menu_cat_system"),
                    ),
                    listOf(
                        InlineKeyboardButton(strings.btnAutomation(), "/menu_cat_auto"),
                        InlineKeyboardButton(strings.btnToggles(), "/toggles"),
                    ),
                    listOf(
                        InlineKeyboardButton(strings.btnAllCommands(), "/menu_all"),
                        InlineKeyboardButton(strings.btnStatus(), "/status"),
                    ),
                ),
            )
        return CommandResult.Text(text, keyboard = keyboard)
    }

    private fun handleCategoryBase(): CommandResult {
        return CommandResult.Text(strings.categoryBase(), keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryMedia(): CommandResult {
        return CommandResult.Text(strings.categoryMedia(), keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryComm(): CommandResult {
        return CommandResult.Text(strings.categoryComm(), keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryGeoSec(): CommandResult {
        return CommandResult.Text(strings.categoryGeoSec(), keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryFiles(): CommandResult {
        return CommandResult.Text(strings.categoryFiles(), keyboard = createCategoryKeyboard())
    }

    private fun handleCategorySystem(): CommandResult {
        return CommandResult.Text(strings.categorySystem(), keyboard = createCategoryKeyboard())
    }

    private fun handleCategoryAuto(): CommandResult {
        return CommandResult.Text(strings.categoryAuto(), keyboard = createCategoryKeyboard())
    }

    private fun handleFullHelp(versionDisplay: String): CommandResult {
        val isRoot = rootStatusProvider.isRootGrantedSync()
        val rootText = if (isRoot) strings.bsRootActive0() else strings.bsRootRestricted()
        val keyboard =
            InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton(strings.btnBack(), "/menu_main"),
                        InlineKeyboardButton(strings.btnStatus(), "/status"),
                    ),
                ),
            )
        return CommandResult.Text(strings.fullHelp(versionDisplay, rootText), keyboard = keyboard)
    }
}

