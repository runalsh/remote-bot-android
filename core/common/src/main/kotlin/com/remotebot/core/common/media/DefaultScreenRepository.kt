package com.remotebot.core.common.media

import android.content.Context
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.core.common.util.StorageHelper
import com.remotebot.domain.model.ScreenRecordParams
import com.remotebot.domain.model.ScreenShotResult
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.repository.ScreenRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DefaultScreenRepository(
    private val context: Context,
    private val rootStatusProvider: RootStatusProvider,
    private val configRepository: BotConfigRepository? = null,
    private val logger: AppLogger? = null,
) : ScreenRepository {
    override suspend fun takeScreenshot(): Result<ScreenShotResult> =
        withContext(Dispatchers.IO) {
            val screenDir = StorageHelper.getStorageSubDir(context, configRepository, "screenshots")
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(screenDir, "scr_$timeStamp.png")

            val hasRoot = rootStatusProvider.isRootGrantedSync()
            logger?.d("ScreenRepository", "Захват скриншота: hasRoot=$hasRoot, outFile=${outFile.name}")

            if (hasRoot) {
                val res =
                    Shell
                        .cmd(
                            "/system/bin/screencap -p \"${outFile.absolutePath}\"",
                            "chmod 666 \"${outFile.absolutePath}\"",
                        ).exec()
                logger?.d("ScreenRepository", "Root screencap exec: code=${res.code}, out=${res.out.joinToString()}")
                if (outFile.exists() && outFile.length() > 0) {
                    outFile.setReadable(true, false)
                    logger?.i("ScreenRepository", "Скриншот успешно создан через Root: ${outFile.length()} байт")
                    return@withContext Result.success(ScreenShotResult(file = outFile, method = "Root screencap"))
                }
            }

            // Try sh non-root fallback
            try {
                logger?.d("ScreenRepository", "Попытка захвата скриншота через Shell process...")
                val process =
                    ProcessBuilder("/system/bin/screencap", "-p", outFile.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                val exitCode = process.waitFor()
                logger?.d("ScreenRepository", "Shell screencap finished: exitCode=$exitCode")

                if (outFile.exists() && outFile.length() > 0) {
                    outFile.setReadable(true, false)
                    logger?.i("ScreenRepository", "Скриншот успешно создан через Shell: ${outFile.length()} байт")
                    return@withContext Result.success(ScreenShotResult(file = outFile, method = "Shell screencap"))
                }
            } catch (e: Exception) {
                logger?.w("ScreenRepository", "Ошибка non-root screencap: ${e.message}")
            }

            logger?.e("ScreenRepository", "Не удалось сделать скриншот (отсутствуют Root/права доступа к screencap)")
            Result.failure(
                IllegalStateException(
                    "Не удалось сделать скриншот: на Android 14+ автономный фоновый захват экрана требует Root-прав (/system/bin/screencap).",
                ),
            )
        }

    override suspend fun recordScreen(params: ScreenRecordParams): Result<File> =
        withContext(Dispatchers.IO) {
            val screenDir = StorageHelper.getStorageSubDir(context, configRepository, "screen_records")
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(screenDir, "scr_rec_$timeStamp.mp4")

            val duration = params.durationSeconds.coerceIn(1, 180)
            val hasRoot = rootStatusProvider.isRootGrantedSync()
            logger?.d("ScreenRepository", "Запись видео экрана: duration=${duration}s, hasRoot=$hasRoot, outFile=${outFile.name}")

            if (hasRoot) {
                val cmd = "/system/bin/screenrecord --time-limit $duration \"${outFile.absolutePath}\""
                val res = Shell.cmd(cmd, "chmod 666 \"${outFile.absolutePath}\"").exec()
                logger?.d("ScreenRepository", "Root screenrecord exec: code=${res.code}")
                if (outFile.exists() && outFile.length() > 0) {
                    outFile.setReadable(true, false)
                    logger?.i("ScreenRepository", "Видео экрана успешно записано через Root: ${outFile.length()} байт")
                    return@withContext Result.success(outFile)
                }
            }

            // Try standard process fallback
            try {
                logger?.d("ScreenRepository", "Попытка записи экрана через Shell process...")
                val process =
                    ProcessBuilder(
                        "/system/bin/screenrecord",
                        "--time-limit",
                        duration.toString(),
                        outFile.absolutePath,
                    ).redirectErrorStream(true).start()
                val exitCode = process.waitFor()
                logger?.d("ScreenRepository", "Shell screenrecord finished: exitCode=$exitCode")

                if (outFile.exists() && outFile.length() > 0) {
                    outFile.setReadable(true, false)
                    logger?.i("ScreenRepository", "Видео экрана успешно записано через Shell: ${outFile.length()} байт")
                    return@withContext Result.success(outFile)
                }
            } catch (e: Exception) {
                logger?.w("ScreenRepository", "Ошибка non-root screenrecord: ${e.message}")
            }

            logger?.e("ScreenRepository", "Не удалось записать видео экрана")
            Result.failure(
                IllegalStateException(
                    "Не удалось записать видео экрана: фоновая запись требует Root-прав (/system/bin/screenrecord).",
                ),
            )
        }
}
