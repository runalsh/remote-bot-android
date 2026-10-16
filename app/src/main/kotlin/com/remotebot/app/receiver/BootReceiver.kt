package com.remotebot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.remotebot.app.service.RemoteBotService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> {
                RemoteBotService.startService(context)
            }
        }
    }
}
