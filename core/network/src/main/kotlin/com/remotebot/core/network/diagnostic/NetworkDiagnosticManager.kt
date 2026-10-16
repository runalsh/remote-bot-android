package com.remotebot.core.network.diagnostic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

data class DiagnosticTarget(
    val name: String,
    val host: String,
    val port: Int = 443,
)

data class DiagnosticResult(
    val target: DiagnosticTarget,
    val isReachable: Boolean,
    val latencyMs: Long,
    val error: String? = null,
)

class NetworkDiagnosticManager(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        // Key Telegram DCs (Data Centers 1-5)
        val TELEGRAM_DCS =
            listOf(
                DiagnosticTarget("Telegram DC1 (Miami)", "149.154.175.53", 443),
                DiagnosticTarget("Telegram DC2 (Amsterdam)", "149.154.167.51", 443),
                DiagnosticTarget("Telegram DC3 (Miami)", "149.154.175.100", 443),
                DiagnosticTarget("Telegram DC4 (Amsterdam)", "149.154.167.91", 443),
                DiagnosticTarget("Telegram DC5 (Singapore)", "91.108.56.130", 443),
            )

        val DEFAULT_API_ROOT = DiagnosticTarget("Telegram Bot API (api.telegram.org)", "api.telegram.org", 443)
        val FCM_ENDPOINT = DiagnosticTarget("Google FCM Gateway", "fcm.googleapis.com", 443)
        val HMS_ENDPOINT = DiagnosticTarget("Huawei HMS Gateway", "push-api.cloud.huawei.com", 443)
    }

    /**
     * Measure TCP connection latency (connect handshake) to host
     */
    suspend fun pingTarget(
        target: DiagnosticTarget,
        timeoutMs: Int = 3000,
    ): DiagnosticResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(target.host, target.port), timeoutMs)
                    val latency = System.currentTimeMillis() - start
                    DiagnosticResult(target, isReachable = true, latencyMs = latency)
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                DiagnosticResult(target, isReachable = false, latencyMs = latency, error = e.localizedMessage ?: e.javaClass.simpleName)
            }
        }

    /**
     * Concurrently check list of DCs and endpoints
     */
    suspend fun checkTargets(
        targets: List<DiagnosticTarget>,
        timeoutMs: Int = 3000,
    ): List<DiagnosticResult> =
        withContext(Dispatchers.IO) {
            targets
                .map { target ->
                    async { pingTarget(target, timeoutMs) }
                }.awaitAll()
        }

    /**
     * Measure HTTP request latency via configured OkHttpClient (including SOCKS5/WSS proxy)
     */
    suspend fun checkHttpEndpoint(url: String): DiagnosticResult =
        withContext(Dispatchers.IO) {
            val uri =
                try {
                    URI(url)
                } catch (
                    e: Exception,
                ) {
                    return@withContext DiagnosticResult(DiagnosticTarget(url, url), false, -1, "Invalid URL")
                }
            val host = uri.host ?: url
            val port = if (uri.port > 0) uri.port else (if (uri.scheme == "http") 80 else 443)
            val target = DiagnosticTarget(url, host, port)

            val start = System.currentTimeMillis()
            try {
                val request =
                    okhttp3.Request
                        .Builder()
                        .url(if (url.startsWith("http")) url else "https://$url")
                        .head()
                        .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - start
                    DiagnosticResult(target, isReachable = response.isSuccessful || response.code in 200..499, latencyMs = latency)
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                DiagnosticResult(target, isReachable = false, latencyMs = latency, error = e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
}
