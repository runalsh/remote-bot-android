package com.remotebot.app.ui.screens.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionsChecker {
    fun getGrantedAndTotalCount(
        context: Context,
        isRootGranted: Boolean,
    ): Pair<Int, Int> {
        val isInstallApps =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                true
            }

        val isManageStorage =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

        val isWriteSettings =
            try {
                Settings.System.canWrite(context)
            } catch (_: Exception) {
                false
            }
        val isOverlay =
            try {
                Settings.canDrawOverlays(context)
            } catch (_: Exception) {
                false
            }

        var isAccessibility = false
        try {
            val enabledServices =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: ""
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            val expectedSimpleName = com.remotebot.app.service.RemoteBotAccessibilityService::class.java.simpleName
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.contains(context.packageName) && componentName.contains(expectedSimpleName)) {
                    isAccessibility = true
                    break
                }
            }
        } catch (_: Exception) {
        }

        val isNotifListener =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) ||
                try {
                    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
                    enabledListeners.split(":").any { it.startsWith("${context.packageName}/") }
                } catch (_: Exception) {
                    false
                }

        val isSms =
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

        val isCalls =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        val isDnd =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.isNotificationPolicyAccessGranted == true

        val isCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val isMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val isLocation =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val isContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) == true

        val isPostNotif =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }

        val isPhotos =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

        val isAudioMedia =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

        val isBluetooth =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            }

        val isUnrestrictedData =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val status = cm?.restrictBackgroundStatus ?: ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
                status != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            } else {
                true
            }

        val items =
            listOf(
                isRootGranted,
                isInstallApps,
                isManageStorage,
                isAccessibility,
                isWriteSettings,
                isOverlay,
                isNotifListener,
                isSms,
                isCalls,
                isDnd,
                isCamera,
                isMic,
                isLocation,
                isContacts,
                isBatteryIgnored,
                isPostNotif,
                isPhotos,
                isAudioMedia,
                isBluetooth,
                isUnrestrictedData,
            )
        return items.count { it } to items.size
    }
}
