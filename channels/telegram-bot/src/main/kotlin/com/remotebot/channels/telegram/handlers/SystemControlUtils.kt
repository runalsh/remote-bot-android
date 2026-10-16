package com.remotebot.channels.telegram.handlers

import android.content.Context
import android.os.PowerManager
import com.remotebot.domain.model.InlineKeyboardButton
import com.remotebot.domain.model.InlineKeyboardMarkup
import com.remotebot.domain.provider.RootStatusProvider
import com.topjohnwu.superuser.Shell
import java.net.Inet4Address
import java.net.NetworkInterface

internal object SystemControlUtils {
    fun isRootGranted(rootStatusProvider: RootStatusProvider): Boolean =
        rootStatusProvider.isRootGrantedSync() ||
            try {
                Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
            } catch (_: Exception) {
                false
            }

    fun makeSingleToggleKeyboard(
        cmd: String,
        isCurrentlyOn: Boolean,
        requiresRoot: Boolean = false,
        hasRoot: Boolean = false,
    ): InlineKeyboardMarkup {
        val toggleBtnText =
            when {
                requiresRoot && !hasRoot -> if (isCurrentlyOn) "🔒 Выключить (Root)" else "🔒 Включить (Root)"
                requiresRoot && hasRoot -> if (isCurrentlyOn) "🔴 Выключить ⚡" else "🟢 Включить ⚡"
                else -> if (isCurrentlyOn) "🔴 Выключить" else "🟢 Включить"
            }

        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton(toggleBtnText, "$cmd toggle"),
                    InlineKeyboardButton("🎛 Все тумблеры", "/toggles"),
                ),
            ),
        )
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun formatIp(ipInt: Int): String =
        if (ipInt == 0) "0.0.0.0" else "${ipInt and 0xFF}.${ipInt shr 8 and 0xFF}.${ipInt shr 16 and 0xFF}.${ipInt shr 24 and 0xFF}"

    fun isPowerSaveModeOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isPowerSaveMode == true
    }
}
