package com.remotebot.core.common.apps

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.InstalledApp
import com.remotebot.domain.model.RunningProcess
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.AppManagerRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DefaultAppManagerRepository(
    private val context: Context,
    private val rootStatusProvider: RootStatusProvider,
) : AppManagerRepository {
    private val packageManager: PackageManager = context.packageManager

    @SuppressLint("QueryPermissionsNeeded")
    override suspend fun getInstalledApps(
        includeSystem: Boolean,
        filter: String?,
    ): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            if (rootStatusProvider.isRootGrantedSync()) {
                // Allow MIUI/HyperOS OP_GET_INSTALLED_APPS (10008) if blocked by vendor
                try {
                    Shell.cmd("appops set ${context.packageName} 10008 allow").exec()
                } catch (_: Exception) {
                }
            }

            val packages =
                try {
                    packageManager.getInstalledPackages(0)
                } catch (_: Exception) {
                    try {
                        packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            val list = mutableListOf<InstalledApp>()

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val isSystem =
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (!includeSystem && isSystem) continue

                val appName =
                    try {
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        pkg.packageName
                    }

                if (!filter.isNullOrBlank()) {
                    val matchesName = appName.contains(filter, ignoreCase = true)
                    val matchesPkg = pkg.packageName.contains(filter, ignoreCase = true)
                    if (!matchesName && !matchesPkg) continue
                }

                @Suppress("DEPRECATION")
                val vCode =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        pkg.longVersionCode
                    } else {
                        pkg.versionCode.toLong()
                    }

                list.add(
                    InstalledApp(
                        packageName = pkg.packageName,
                        appName = appName,
                        versionName = pkg.versionName,
                        versionCode = vCode,
                        isSystemApp = isSystem,
                        isEnabled = appInfo.enabled,
                    ),
                )
            }

            if (list.isEmpty()) {
                val apps =
                    try {
                        packageManager.getInstalledApplications(0)
                    } catch (_: Exception) {
                        emptyList()
                    }
                for (appInfo in apps) {
                    val isSystem =
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    if (!includeSystem && isSystem) continue

                    val appName =
                        try {
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (_: Exception) {
                            appInfo.packageName
                        }

                    if (!filter.isNullOrBlank()) {
                        val matchesName = appName.contains(filter, ignoreCase = true)
                        val matchesPkg = appInfo.packageName.contains(filter, ignoreCase = true)
                        if (!matchesName && !matchesPkg) continue
                    }

                    list.add(
                        InstalledApp(
                            packageName = appInfo.packageName,
                            appName = appName,
                            versionName = "1.0",
                            versionCode = 1L,
                            isSystemApp = isSystem,
                            isEnabled = appInfo.enabled,
                        ),
                    )
                }
            }

            // Fallback: if list is empty or vendor restrictions (MIUI) hid user apps
            if ((list.isEmpty() || (!includeSystem && list.all { it.isSystemApp })) && rootStatusProvider.isRootGrantedSync()) {
                val cmd = if (includeSystem) "pm list packages" else "pm list packages -3"
                val res = Shell.cmd(cmd).exec()
                if (res.isSuccess) {
                    val knownPkgs = list.map { it.packageName }.toSet()
                    for (line in res.out) {
                        val pkgName = line.trim().removePrefix("package:").trim()
                        if (pkgName.isBlank() || knownPkgs.contains(pkgName)) continue
                        try {
                            val pkg = packageManager.getPackageInfo(pkgName, 0)
                            val appInfo = pkg.applicationInfo
                            val isSystem =
                                appInfo != null &&
                                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                            if (!includeSystem && isSystem) continue
                            val appName =
                                if (appInfo != null) {
                                    try {
                                        packageManager.getApplicationLabel(appInfo).toString()
                                    } catch (_: Exception) {
                                        pkgName
                                    }
                                } else {
                                    pkgName
                                }
                            if (!filter.isNullOrBlank()) {
                                val matchesName = appName.contains(filter, ignoreCase = true)
                                val matchesPkg = pkgName.contains(filter, ignoreCase = true)
                                if (!matchesName && !matchesPkg) continue
                            }
                            @Suppress("DEPRECATION")
                            val vCode =
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    pkg.longVersionCode
                                } else {
                                    pkg.versionCode.toLong()
                                }
                            list.add(
                                InstalledApp(
                                    packageName = pkgName,
                                    appName = appName,
                                    versionName = pkg.versionName,
                                    versionCode = vCode,
                                    isSystemApp = isSystem,
                                    isEnabled = appInfo?.enabled ?: true,
                                ),
                            )
                        } catch (_: Exception) {
                            if (!includeSystem) {
                                list.add(
                                    InstalledApp(
                                        packageName = pkgName,
                                        appName = pkgName,
                                        versionName = "1.0",
                                        versionCode = 1L,
                                        isSystemApp = false,
                                        isEnabled = true,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            list.sortedBy { it.appName.lowercase() }
        }

    override suspend fun launchApp(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return@runCatching
                }

                // Fallback via Root monkey / am start
                if (rootStatusProvider.isRootGrantedSync()) {
                    val result = Shell.cmd("monkey -p $packageName -c android.intent.category.LAUNCHER 1").exec()
                    if (result.isSuccess) return@runCatching
                }

                throw IllegalStateException("Не удалось запустить приложение: $packageName (нет Launch Intent)")
            }
        }

    override suspend fun installApk(apkPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apkFile = File(apkPath)
                if (!apkFile.exists()) {
                    throw IllegalStateException("APK файл не найден: $apkPath")
                }

                if (!rootStatusProvider.isRootGrantedSync() &&
                    com.topjohnwu.superuser.Shell
                        .isAppGrantedRoot() != true
                ) {
                    throw IllegalStateException("Тихая установка APK требует Root-прав.")
                }

                // Copy to temporary system directory /data/local/tmp/,
                // as PackageManager (SELinux) lacks access to /storage/emulated/0/ in its namespace
                val tmpApkPath = "/data/local/tmp/remotebot_install_${System.currentTimeMillis()}.apk"
                val copyCmd = "cp -f \"${apkFile.absolutePath}\" \"$tmpApkPath\" && chmod 666 \"$tmpApkPath\""
                val copyResult = Shell.cmd(copyCmd).exec()
                val targetInstallPath = if (copyResult.isSuccess && File(tmpApkPath).exists()) tmpApkPath else apkFile.absolutePath

                // Attempt installation with extended flags (-r replace, -d downgrade, -t test/debug, -g grant permissions)
                val installCommands =
                    listOf(
                        "pm install -r -d -t -g \"$targetInstallPath\"",
                        "cmd package install -r -d -t -g \"$targetInstallPath\"",
                        "pm install -r -d -t \"$targetInstallPath\"",
                        "pm install -r -d -t --bypass-low-target-sdk-block \"$targetInstallPath\"",
                    )

                var installSuccess = false
                var lastOutput = ""

                for (cmd in installCommands) {
                    val result = Shell.cmd(cmd).exec()
                    val outStr = (result.out + result.err).filter { it.isNotBlank() }.joinToString("\n").trim()
                    lastOutput = outStr
                    if (result.isSuccess && (outStr.contains("Success", ignoreCase = true) || outStr.isBlank())) {
                        installSuccess = true
                        AppLogger.i("DefaultAppManagerRepository", "Команда '$cmd' выполнена успешно: $outStr")
                        break
                    }
                    if (outStr.contains("Success", ignoreCase = true)) {
                        installSuccess = true
                        break
                    }
                }

                // Clean up temporary APK
                Shell.cmd("rm -f \"$tmpApkPath\"").exec()

                if (!installSuccess) {
                    val errDetails = if (lastOutput.isNotBlank()) lastOutput else "Процесс завершился с ошибкой (код не 0)"
                    throw IllegalStateException("Ошибка установки APK ($errDetails)")
                }
                AppLogger.i("DefaultAppManagerRepository", "Successfully installed APK: $apkPath")
            }
        }

    override suspend fun getRunningProcesses(): Result<List<RunningProcess>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val list = mutableListOf<RunningProcess>()

                if (rootStatusProvider.isRootGrantedSync()) {
                    val result = Shell.cmd("ps -A -o PID,NAME,%CPU,%MEM").exec()
                    if (result.isSuccess) {
                        for (line in result.out) {
                            val parts = line.trim().split("\\s+".toRegex())
                            if (parts.size >= 4 && parts[0] != "PID") {
                                val pid = parts[0].toIntOrNull() ?: continue
                                val name = parts[1]
                                val cpu = parts[2].toDoubleOrNull() ?: 0.0
                                val mem = parts[3].toDoubleOrNull() ?: 0.0
                                list.add(
                                    RunningProcess(
                                        pid = pid,
                                        processName = name,
                                        packageName = name.substringBefore(":"),
                                        memoryUsageMb = mem,
                                        cpuUsagePercent = cpu,
                                    ),
                                )
                            }
                        }
                        return@runCatching list.sortedByDescending { it.cpuUsagePercent }.take(30)
                    }
                }

                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningApps = am.runningAppProcesses ?: emptyList()
                for (proc in runningApps) {
                    list.add(
                        RunningProcess(
                            pid = proc.pid,
                            processName = proc.processName,
                            packageName = proc.pkgList?.firstOrNull() ?: proc.processName,
                        ),
                    )
                }
                list
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun killProcess(
        pid: Int?,
        packageName: String?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                AppLogger.w("DefaultAppManagerRepository", "⚠️ Запрос завершения процесса: PID=$pid, package=$packageName")
                if (packageName != null) {
                    if (rootStatusProvider.isRootGrantedSync()) {
                        val res = Shell.cmd("am force-stop $packageName").exec()
                        AppLogger.i("DefaultAppManagerRepository", "Завершено через am force-stop $packageName: success=${res.isSuccess}")
                    } else {
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        am.killBackgroundProcesses(packageName)
                        AppLogger.i("DefaultAppManagerRepository", "Завершен фоновый процесс через ActivityManager: $packageName")
                    }
                }
                if (pid != null && rootStatusProvider.isRootGrantedSync()) {
                    val res = Shell.cmd("kill -9 $pid").exec()
                    AppLogger.i("DefaultAppManagerRepository", "Выполнен kill -9 $pid: success=${res.isSuccess}")
                }
            }
        }
}
