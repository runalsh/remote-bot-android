package com.remotebot.core.proxy

import com.remotebot.domain.model.ProxyConfig
import com.remotebot.domain.model.ProxyType
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ProxyClientFactory {
    fun createClient(
        config: ProxyConfig,
        loopbackWssPort: Int? = null,
    ): OkHttpClient {
        val builder =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)

        when (config.type) {
            ProxyType.NONE -> {
                // Direct connection
            }
            ProxyType.HTTP -> {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port))
                builder.proxy(proxy)
                val user = config.username
                val pass = config.password
                if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                    builder.proxyAuthenticator(
                        object : Authenticator {
                            override fun authenticate(
                                route: Route?,
                                response: Response,
                            ): Request? {
                                val credential = Credentials.basic(user, pass)
                                return response.request
                                    .newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build()
                            }
                        },
                    )
                }
            }
            ProxyType.SOCKS5 -> {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(config.host, config.port))
                builder.proxy(proxy)
                val user = config.username
                val pass = config.password
                if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                    java.net.Authenticator.setDefault(
                        object : java.net.Authenticator() {
                            override fun getPasswordAuthentication(): java.net.PasswordAuthentication =
                                java.net.PasswordAuthentication(user, pass.toCharArray())
                        },
                    )
                }
            }
            ProxyType.WEBSOCKET_WSS -> {
                // If WSS tunnel is active, route traffic to local loopback port
                if (loopbackWssPort != null && loopbackWssPort > 0) {
                    val localProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", loopbackWssPort))
                    builder.proxy(localProxy)
                }
            }
        }

        return builder.build()
    }
}
