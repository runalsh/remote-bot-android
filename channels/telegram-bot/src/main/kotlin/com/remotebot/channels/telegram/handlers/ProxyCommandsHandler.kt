package com.remotebot.channels.telegram.handlers

import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.BotLanguage
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.ProxyConfig
import com.remotebot.domain.model.ProxyType
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.ProxyConfigRepository

class ProxyCommandsHandler(
    private val proxyConfigRepository: ProxyConfigRepository,
    private val logger: AppLogger,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

    override val commandNames: List<String> = listOf("proxy")

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult = handleProxy(command.args)

    private suspend fun handleProxy(args: List<String>): CommandResult {
        if (args.isEmpty() || args[0].equals("status", ignoreCase = true)) {
            val cfg = proxyConfigRepository.proxyConfig.value
            val currentStatus =
                if (cfg.type == ProxyType.NONE) {
                    strings.pxStatusOff()
                } else {
                    val authInfo =
                        if (!cfg.username.isNullOrBlank()) {
                            strings.pxAuthWith(cfg.username ?: "")
                        } else {
                            strings.pxAuthNone()
                        }
                    val extraPath = if (cfg.type == ProxyType.WEBSOCKET_WSS) strings.pxWsPath(cfg.wsPath) else ""
                    strings.pxStatusBlock(cfg.type.name, "${cfg.host}:${cfg.port}", authInfo, extraPath)
                }

            val helpText =
                strings.pxHelp(currentStatus)

            return CommandResult.Text(helpText)
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "off" -> {
                proxyConfigRepository.updateProxy(ProxyConfig(type = ProxyType.NONE))
                logger.i("Proxy", "Proxy disabled via Telegram command")
                CommandResult.Text(strings.pxDisabled())
            }
            "socks5", "http" -> {
                if (args.size < 3) {
                    return CommandResult.Error(strings.pxFormat(sub, "[username] [password]"))
                }
                val host = args[1]
                val port = args[2].toIntOrNull() ?: return CommandResult.Error(strings.pxBadPort(args[2]))
                val user = args.getOrNull(3)
                val pass = args.getOrNull(4)
                val type = if (sub == "socks5") ProxyType.SOCKS5 else ProxyType.HTTP

                val newCfg =
                    ProxyConfig(
                        type = type,
                        host = host,
                        port = port,
                        username = user,
                        password = pass,
                    )
                proxyConfigRepository.updateProxy(newCfg)
                logger.i("Proxy", "Proxy set: $type $host:$port")
                CommandResult.Text(strings.pxSet(type.name, "$host:$port"))
            }
            "wss" -> {
                if (args.size < 3) {
                    return CommandResult.Error(strings.pxFormat("wss", "[path]"))
                }
                val host = args[1]
                val port = args[2].toIntOrNull() ?: return CommandResult.Error(strings.pxBadPort(args[2]))
                val path = args.getOrNull(3) ?: "/ws"

                val newCfg =
                    ProxyConfig(
                        type = ProxyType.WEBSOCKET_WSS,
                        host = host,
                        port = port,
                        wsPath = path,
                    )
                proxyConfigRepository.updateProxy(newCfg)
                logger.i("Proxy", "WSS tunnel set: wss://$host:$port$path")
                CommandResult.Text(strings.pxWssSet("wss://$host:$port$path"))
            }
            else -> CommandResult.Error(strings.pxUnknownParam(sub, "status, off, socks5, http, wss"))
        }
    }
}
