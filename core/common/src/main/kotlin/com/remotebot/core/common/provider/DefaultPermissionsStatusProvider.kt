package com.remotebot.core.common.provider

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.remotebot.domain.provider.PermissionItemInfo
import com.remotebot.domain.provider.PermissionsStatusProvider
import com.remotebot.domain.provider.RootStatusProvider

class DefaultPermissionsStatusProvider(
    private val context: Context,
    private val rootStatusProvider: RootStatusProvider,
) : PermissionsStatusProvider {
    override fun getPermissionsStatus(): List<PermissionItemInfo> {
        val isRoot = rootStatusProvider.isRootGrantedSync()

        // --- Group 1: Critical System Access ---
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

        val isAccessibility =
            try {
                val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                enabled.contains(context.packageName)
            } catch (_: Exception) {
                false
            }

        // --- Group 2: Communications & Privacy ---
        val isNotifListener =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) ||
                try {
                    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
                    enabled.split(":").any { it.startsWith("${context.packageName}/") }
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

        // --- Group 3: Multimedia & Sensors ---
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

        return listOf(
            // Group: critical
            PermissionItemInfo("root", "Root (Superuser)", "critical", isRoot, "Полный доступ суперпользователя (libsu)"),
            PermissionItemInfo(
                "install_packages",
                "Установка APK",
                "critical",
                isInstallApps,
                "REQUEST_INSTALL_PACKAGES для обновления приложения",
            ),
            PermissionItemInfo(
                "manage_storage",
                "Все файлы (Storage)",
                "critical",
                isManageStorage,
                "MANAGE_EXTERNAL_STORAGE доступ к диску",
            ),
            PermissionItemInfo(
                "accessibility",
                "Спец. возможности",
                "critical",
                isAccessibility,
                "Accessibility Service автоматизация кликов/USSD",
            ),
            PermissionItemInfo(
                "write_settings",
                "Настройки системы",
                "critical",
                isWriteSettings,
                "WRITE_SETTINGS управление автояркостью",
            ),
            PermissionItemInfo("overlay", "Поверх других окон", "critical", isOverlay, "SYSTEM_ALERT_WINDOW системные диалоги"),
            // Group: confidential
            PermissionItemInfo(
                "notification_listener",
                "Чтение уведомлений",
                "confidential",
                isNotifListener,
                "Перехват входящих push-уведомлений",
            ),
            PermissionItemInfo("sms", "SMS (Отправка/Чтение)", "confidential", isSms, "SEND_SMS, RECEIVE_SMS управление через SMS"),
            PermissionItemInfo("calls", "Звонки и журнал", "confidential", isCalls, "CALL_PHONE, READ_CALL_LOG, READ_PHONE_STATE"),
            PermissionItemInfo("dnd", "Режим «Не беспокоить»", "confidential", isDnd, "ACCESS_NOTIFICATION_POLICY профили звука"),
            // Group: standard
            PermissionItemInfo("camera", "Камера", "standard", isCamera, "CAMERA съемка фото и видео"),
            PermissionItemInfo("microphone", "Микрофон (Запись звука)", "standard", isMic, "RECORD_AUDIO скрытая аудиозапись"),
            PermissionItemInfo("location", "Геолокация (GPS)", "standard", isLocation, "ACCESS_FINE_LOCATION координаты и трекинг"),
            PermissionItemInfo("contacts", "Контакты", "standard", isContacts, "READ_CONTACTS определение имен в вызовах/SMS"),
            PermissionItemInfo(
                "battery_opt",
                "Doze Mode (Без ограничений)",
                "standard",
                isBatteryIgnored,
                "Игнорирование оптимизации батареи 24/7",
            ),
            PermissionItemInfo(
                "post_notifications",
                "Уведомления в шторку",
                "standard",
                isPostNotif,
                "POST_NOTIFICATIONS фоновая служба FGS",
            ),
            PermissionItemInfo("media_photos", "Фото и галерея", "standard", isPhotos, "Чтение фото/видео из MediaStore"),
            PermissionItemInfo("media_audio", "Аудиофайлы", "standard", isAudioMedia, "Чтение аудиофайлов для плеера"),
            PermissionItemInfo("bluetooth", "Bluetooth адаптер", "standard", isBluetooth, "BLUETOOTH_CONNECT управление Bluetooth"),
            PermissionItemInfo("data_saver", "Фоновый трафик", "standard", isUnrestrictedData, "Работа в режиме экономии трафика"),
        )
    }
}
