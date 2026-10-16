package com.remotebot.app.data

import android.content.Context
import com.remotebot.domain.model.ProxyConfig
import com.remotebot.domain.model.ProxyType
import com.remotebot.domain.repository.ProxyConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesProxyConfigRepository(
    context: Context,
) : ProxyConfigRepository {
    private val prefs = context.getSharedPreferences("remotebot_proxy_config", Context.MODE_PRIVATE)

    private val _proxyConfig = MutableStateFlow(loadConfig())
    override val proxyConfig: StateFlow<ProxyConfig> = _proxyConfig.asStateFlow()

    private fun loadConfig(): ProxyConfig {
        val typeName = prefs.getString("proxy_type", ProxyType.NONE.name) ?: ProxyType.NONE.name
        val type =
            try {
                ProxyType.valueOf(typeName)
            } catch (_: Exception) {
                ProxyType.NONE
            }
        val host = prefs.getString("proxy_host", "") ?: ""
        val port = prefs.getInt("proxy_port", 1080)
        val user = prefs.getString("proxy_user", null)
        val pass = prefs.getString("proxy_pass", null)
        val wsPath = prefs.getString("proxy_ws_path", "/ws") ?: "/ws"

        return ProxyConfig(
            type = type,
            host = host,
            port = port,
            username = user,
            password = pass,
            wsPath = wsPath,
        )
    }

    override suspend fun updateProxy(config: ProxyConfig) {
        _proxyConfig.value = config

        prefs
            .edit()
            .putString("proxy_type", config.type.name)
            .putString("proxy_host", config.host)
            .putInt("proxy_port", config.port)
            .putString("proxy_user", config.username)
            .putString("proxy_pass", config.password)
            .putString("proxy_ws_path", config.wsPath)
            .apply()
    }
}
