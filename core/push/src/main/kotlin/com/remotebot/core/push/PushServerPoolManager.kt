package com.remotebot.core.push

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.network.diagnostic.DiagnosticResult
import com.remotebot.core.network.diagnostic.NetworkDiagnosticManager
import com.remotebot.domain.model.BotConfig
import com.remotebot.domain.model.EndpointSelectionStrategy
import com.remotebot.domain.model.PushServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Менеджер пула независимых Push-серверов.
 * Реализует выбор активного сервера по стратегиям FAILOVER, ROUND_ROBIN и LOWEST_LATENCY,
 * аналогично механизму зеркал Telegram Bot API.
 */
class PushServerPoolManager(
    private val configProvider: () -> BotConfig,
    private val diagnosticManager: NetworkDiagnosticManager? = null,
    private val logger: AppLogger? = null,
) {
    @Volatile
    private var currentServerIndex = 0

    @Volatile
    var lastActiveServerId: String? = null
        private set

    /**
     * Возвращает список всех серверов с учетом одиночных legacy-полей (если pushServers пуст)
     */
    fun getAllServers(): List<PushServerConfig> {
        val cfg = configProvider()
        val list = mutableListOf<PushServerConfig>()

        // Пользовательский пул серверов
        list.addAll(cfg.pushServers)

        // Обратная совместимость с одиночным pushGatewayUrl
        val legacyUrl = cfg.pushGatewayUrl
        if (!legacyUrl.isNullOrBlank() && list.none { it.gatewayUrl.trimEnd('/') == legacyUrl.trimEnd('/') }) {
            list.add(
                0,
                PushServerConfig(
                    id = "legacy_main",
                    name = "Основной шлюз",
                    gatewayUrl = legacyUrl,
                    secretKey = cfg.pushSecretKey ?: "",
                    provider = cfg.pushProvider,
                    googleServicesJson = cfg.googleServicesJson,
                    agconnectServicesJson = cfg.agconnectServicesJson,
                ),
            )
        }

        return list
    }

    /**
     * Возвращает упорядоченный список серверов в соответствии со стратегией
     */
    suspend fun getCandidateServers(): List<PushServerConfig> =
        withContext(Dispatchers.IO) {
            val servers = getAllServers()
            if (servers.isEmpty()) return@withContext emptyList()

            val cfg = configProvider()
            when (cfg.pushServerSelectionStrategy) {
                EndpointSelectionStrategy.FAILOVER -> {
                    if (currentServerIndex in servers.indices && currentServerIndex > 0) {
                        val active = servers[currentServerIndex]
                        listOf(active) + servers.filterIndexed { index, _ -> index != currentServerIndex }
                    } else {
                        servers
                    }
                }
                EndpointSelectionStrategy.ROUND_ROBIN -> {
                    synchronized(this) {
                        val idx = (currentServerIndex % servers.size).coerceAtLeast(0)
                        currentServerIndex = (currentServerIndex + 1) % servers.size
                        val reordered = mutableListOf<PushServerConfig>()
                        for (i in servers.indices) {
                            reordered.add(servers[(idx + i) % servers.size])
                        }
                        reordered
                    }
                }
                EndpointSelectionStrategy.LOWEST_LATENCY -> {
                    val diag = diagnosticManager
                    if (diag == null) {
                        servers
                    } else {
                        val pingResults: List<Pair<PushServerConfig, DiagnosticResult>> =
                            servers
                                .map { server ->
                                    async {
                                        val res = diag.checkHttpEndpoint(server.gatewayUrl)
                                        server to res
                                    }
                                }.awaitAll()

                        val sorted =
                            pingResults
                                .sortedWith(
                                    compareBy<Pair<PushServerConfig, DiagnosticResult>> { !it.second.isReachable }
                                        .thenBy { it.second.latencyMs },
                                ).map { it.first }

                        logger?.d(
                            "PushServerPool",
                            "LOWEST_LATENCY сортировка серверов: ${sorted.joinToString { "${it.name.ifBlank { it.gatewayUrl }}" }}",
                        )
                        sorted
                    }
                }
            }
        }

    fun markSuccess(
        server: PushServerConfig,
        latencyMs: Long? = null,
    ) {
        lastActiveServerId = server.id
        val servers = getAllServers()
        val idx = servers.indexOfFirst { it.id == server.id }
        if (idx >= 0) {
            currentServerIndex = idx
        }
        PushStatsTracker.recordDelivery(latencyMs)
        val srvName = server.name.ifBlank { server.gatewayUrl }
        com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordConnection(
            serverId = server.id.ifBlank { server.gatewayUrl },
            serverName = srvName,
            serverType = com.remotebot.domain.model.ServerType.PUSH_GATEWAY,
            outcome = com.remotebot.domain.model.ConnectionOutcome.SUCCESS,
        )
        logger?.d("PushServerPool", "Успешная доставка/ответ через Push-сервер: $srvName")
    }

    fun markFailure(
        server: PushServerConfig,
        errorMsg: String,
    ) {
        PushStatsTracker.recordFailure(errorMsg)
        val isTimeout =
            errorMsg.contains("timeout", ignoreCase = true) ||
                errorMsg.contains("timed out", ignoreCase = true)
        val outcome =
            if (isTimeout) {
                com.remotebot.domain.model.ConnectionOutcome.TIMEOUT
            } else {
                com.remotebot.domain.model.ConnectionOutcome.FAILURE
            }
        val srvName = server.name.ifBlank { server.gatewayUrl }
        com.remotebot.core.common.diagnostics.AppDiagnosticsTracker.recordConnection(
            serverId = server.id.ifBlank { server.gatewayUrl },
            serverName = srvName,
            serverType = com.remotebot.domain.model.ServerType.PUSH_GATEWAY,
            outcome = outcome,
        )
        logger?.w("PushServerPool", "Ошибка Push-сервера [$srvName]: $errorMsg")
        val servers = getAllServers()
        if (servers.size > 1) {
            currentServerIndex = (currentServerIndex + 1) % servers.size
            logger?.i("PushServerPool", "Переключение на следующий Push-сервер (индекс: $currentServerIndex)")
        }
    }
}
