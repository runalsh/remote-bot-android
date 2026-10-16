package com.remotebot.core.proxy

import com.remotebot.domain.model.ProxyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WssTunnelManager(
    private val scope: CoroutineScope,
) {
    companion object {
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectionPool(ConnectionPool(10, 60, TimeUnit.SECONDS))
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = AtomicBoolean(false)
    private var tunnelJob: Job? = null
    var assignedLocalPort: Int = 0
        private set

    fun start(config: ProxyConfig): Int {
        if (isRunning.get()) return assignedLocalPort

        val server = ServerSocket(0) // Случайный свободный порт
        serverSocket = server
        assignedLocalPort = server.localPort
        isRunning.set(true)

        tunnelJob =
            scope.launch(Dispatchers.IO) {
                while (isRunning.get() && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        launch { handleClientSocket(clientSocket, config) }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }

        return assignedLocalPort
    }

    private fun handleClientSocket(
        localSocket: Socket,
        config: ProxyConfig,
    ) {
        val client = sharedClient
        val url = "wss://${config.host}:${config.port}${config.wsPath}"
        val requestBuilder = Request.Builder().url(url)

        config.wsSni?.let { requestBuilder.header("Host", it) }
        config.wsCustomHeader?.let {
            val parts = it.split(":", limit = 2)
            if (parts.size == 2) requestBuilder.header(parts[0].trim(), parts[1].trim())
        }

        val outStream: OutputStream = localSocket.getOutputStream()
        val inStream: InputStream = localSocket.getInputStream()

        val webSocket =
            client.newWebSocket(
                requestBuilder.build(),
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString,
                    ) {
                        try {
                            outStream.write(bytes.toByteArray())
                            outStream.flush()
                        } catch (_: Exception) {
                            webSocket.close(1000, null)
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: okhttp3.Response?,
                    ) {
                        try {
                            localSocket.close()
                        } catch (_: Exception) {
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        try {
                            localSocket.close()
                        } catch (_: Exception) {
                        }
                    }
                },
            )

        // Read from local socket and forward to WebSocket
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                webSocket.send(buffer.toByteString(0, bytesRead))
            }
        } catch (_: Exception) {
        } finally {
            webSocket.close(1000, "Done")
            try {
                localSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        tunnelJob?.cancel()
        assignedLocalPort = 0
    }
}
