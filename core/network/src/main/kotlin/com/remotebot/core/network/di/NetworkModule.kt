package com.remotebot.core.network.di

import com.remotebot.core.proxy.WssTunnelManager
import com.remotebot.domain.model.ProxyType
import com.remotebot.domain.repository.ProxyConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.koin.dsl.module
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class DynamicProxySelector(
    private val proxyRepo: ProxyConfigRepository,
    private val wssManager: WssTunnelManager,
) : ProxySelector() {
    @Volatile
    private var lastConfiguredSocksAuth: String? = null

    override fun select(uri: URI?): List<Proxy> {
        val config = proxyRepo.proxyConfig.value
        return when (config.type) {
            ProxyType.NONE -> listOf(Proxy.NO_PROXY)
            ProxyType.HTTP -> {
                if (config.host.isNotBlank() && config.port > 0) {
                    listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port)))
                } else {
                    listOf(Proxy.NO_PROXY)
                }
            }
            ProxyType.SOCKS5 -> {
                if (config.host.isNotBlank() && config.port > 0) {
                    val user = config.username
                    val pass = config.password
                    val currentAuthKey = "$user:$pass"
                    if (!user.isNullOrBlank() && !pass.isNullOrBlank() && currentAuthKey != lastConfiguredSocksAuth) {
                        lastConfiguredSocksAuth = currentAuthKey
                        java.net.Authenticator.setDefault(
                            object : java.net.Authenticator() {
                                override fun getPasswordAuthentication(): java.net.PasswordAuthentication =
                                    java.net.PasswordAuthentication(user, pass.toCharArray())
                            },
                        )
                    }
                    listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(config.host, config.port)))
                } else {
                    listOf(Proxy.NO_PROXY)
                }
            }
            ProxyType.WEBSOCKET_WSS -> {
                if (config.host.isNotBlank() && config.port > 0) {
                    val loopbackPort = wssManager.start(config)
                    listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", loopbackPort)))
                } else {
                    listOf(Proxy.NO_PROXY)
                }
            }
        }
    }

    override fun connectFailed(
        uri: URI?,
        sa: SocketAddress?,
        ioe: IOException?,
    ) {
        // Connection error via selected proxy
    }
}

val networkModule =
    module {
        single {
            com.remotebot.core.network.diagnostic
                .NetworkDiagnosticManager(get())
        }
        single<OkHttpClient> {
            val proxyRepo: ProxyConfigRepository = get()
            val wssManager: WssTunnelManager = get()

            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .proxySelector(DynamicProxySelector(proxyRepo, wssManager))
                .proxyAuthenticator(
                    object : Authenticator {
                        override fun authenticate(
                            route: Route?,
                            response: Response,
                        ): Request? {
                            val config = proxyRepo.proxyConfig.value
                            val user = config.username
                            val pass = config.password
                            return if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                                val credential = Credentials.basic(user, pass)
                                response.request
                                    .newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build()
                            } else {
                                null
                            }
                        }
                    },
                ).build()
        }

        single<HttpClient> {
            HttpClient(OkHttp) {
                engine {
                    preconfigured = get<OkHttpClient>()
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        }
    }
