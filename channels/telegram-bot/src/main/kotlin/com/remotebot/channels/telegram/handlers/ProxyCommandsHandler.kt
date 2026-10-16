package com.remotebot.channels.telegram.handlers

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.model.ProxyConfig
import com.remotebot.domain.model.ProxyType
import com.remotebot.domain.repository.ProxyConfigRepository

class ProxyCommandsHandler(
    private val proxyConfigRepository: ProxyConfigRepository,
    private val logger: AppLogger,
) : CommandHandler {
    override val commandNames: List<String> = listOf("proxy")

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult = handleProxy(command.args)

    private suspend fun handleProxy(args: List<String>): CommandResult {
        if (args.isEmpty() || args[0].equals("status", ignoreCase = true)) {
            val cfg = proxyConfigRepository.proxyConfig.value
            val currentStatus =
                if (cfg.type == ProxyType.NONE) {
                    "🌐 <b>Текущий статус:</b> Выключен (Прямое соединение без прокси)"
                } else {
                    val authInfo = if (!cfg.username.isNullOrBlank()) "с авторизацией (<code>${cfg.username}</code>)" else "без авторизации"
                    val extraPath = if (cfg.type == ProxyType.WEBSOCKET_WSS) "\n• WSS путь: <code>${cfg.wsPath}</code>" else ""
                    """
                    🌐 <b>Текущий статус:</b>
                    • Тип: <b>${cfg.type.name}</b>
                    • Адрес: <code>${cfg.host}:${cfg.port}</code>
                    • Авторизация: $authInfo$extraPath
                    """.trimIndent()
                }

            val helpText =
                """
                $currentStatus

                ━━━━━━━━━━━━━━━━━━━━━
                📖 <b>Управление и примеры команд:</b>

                1️⃣ <b>SOCKS5 прокси:</b>
                <code>/proxy socks5 &lt;хост&gt; &lt;порт&gt; [логин] [пароль]</code>
                <i>Пример без пароля:</i>
                <code>/proxy socks5 192.168.1.100 1080</code>
                <i>Пример с авторизацией:</i>
                <code>/proxy socks5 1.2.3.4 10808 myuser mypass</code>

                2️⃣ <b>HTTP прокси:</b>
                <code>/proxy http &lt;хост&gt; &lt;порт&gt; [логин] [пароль]</code>
                <i>Пример:</i>
                <code>/proxy http 192.168.1.50 8080 admin secret</code>

                3️⃣ <b>WSS WebSocket туннель:</b>
                <code>/proxy wss &lt;хост&gt; &lt;порт&gt; [путь]</code>
                <i>Пример:</i>
                <code>/proxy wss myproxy.example.com 443 /telegram-ws</code>

                4️⃣ <b>Отключение прокси:</b>
                <code>/proxy off</code>
                """.trimIndent()

            return CommandResult.Text(helpText)
        }

        val sub = args[0].lowercase()
        return when (sub) {
            "off" -> {
                proxyConfigRepository.updateProxy(ProxyConfig(type = ProxyType.NONE))
                logger.i("Proxy", "Прокси отключен через Telegram команду")
                CommandResult.Text("🌐 Прокси отключен. Используется прямое соединение.")
            }
            "socks5", "http" -> {
                if (args.size < 3) {
                    return CommandResult.Error("Формат: /proxy $sub &lt;host&gt; &lt;port&gt; [username] [password]")
                }
                val host = args[1]
                val port = args[2].toIntOrNull() ?: return CommandResult.Error("Некорректный порт: ${args[2]}")
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
                logger.i("Proxy", "Установлен прокси $type $host:$port")
                CommandResult.Text("✅ Прокси переключен на <b>${type.name}</b> ($host:$port)")
            }
            "wss" -> {
                if (args.size < 3) {
                    return CommandResult.Error("Формат: /proxy wss &lt;host&gt; &lt;port&gt; [path]")
                }
                val host = args[1]
                val port = args[2].toIntOrNull() ?: return CommandResult.Error("Некорректный порт: ${args[2]}")
                val path = args.getOrNull(3) ?: "/ws"

                val newCfg =
                    ProxyConfig(
                        type = ProxyType.WEBSOCKET_WSS,
                        host = host,
                        port = port,
                        wsPath = path,
                    )
                proxyConfigRepository.updateProxy(newCfg)
                logger.i("Proxy", "Установлен WSS туннель wss://$host:$port$path")
                CommandResult.Text("✅ Установлен <b>WSS-туннель</b> (wss://$host:$port$path)")
            }
            else -> CommandResult.Error("Неизвестный параметр /proxy: $sub. Доступно: status, off, socks5, http, wss")
        }
    }
}
