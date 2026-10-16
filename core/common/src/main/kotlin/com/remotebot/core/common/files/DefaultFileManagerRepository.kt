package com.remotebot.core.common.files

import android.content.Context
import android.os.Environment
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.FileItem
import com.remotebot.domain.model.UnsentFile
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.FileManagerRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DefaultFileManagerRepository(
    private val context: Context,
    private val rootStatusProvider: RootStatusProvider,
) : FileManagerRepository {
    private val _unsentFiles = MutableStateFlow<List<UnsentFile>>(emptyList())
    override val unsentFiles: StateFlow<List<UnsentFile>> = _unsentFiles.asStateFlow()

    override suspend fun listFiles(path: String): Result<List<FileItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetPath =
                    when {
                        path.isBlank() || path == "~" -> Environment.getExternalStorageDirectory().absolutePath
                        path.startsWith("/") -> path
                        path.startsWith("~/") -> "${Environment.getExternalStorageDirectory().absolutePath}/${path.removePrefix("~/")}"
                        else -> "${Environment.getExternalStorageDirectory().absolutePath}/$path"
                    }

                val targetFile = File(targetPath)
                val directFiles = if (targetFile.exists() && targetFile.canRead()) targetFile.listFiles() else null
                if (directFiles != null) {
                    return@runCatching directFiles
                        .map { file ->
                            FileItem(
                                name = file.name,
                                path = file.absolutePath,
                                isDirectory = file.isDirectory,
                                sizeBytes = if (file.isDirectory) 0 else file.length(),
                                lastModifiedMs = file.lastModified(),
                                canRead = file.canRead(),
                                canWrite = file.canWrite(),
                            )
                        }.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
                }

                // Fallback to Root if regular access is denied or returned null
                val result = Shell.cmd("toybox ls -la \"$targetPath\" || ls -la \"$targetPath\"").exec()
                if (result.isSuccess) {
                    val list = mutableListOf<FileItem>()
                    for (line in result.out) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("total") || trimmed.isBlank()) continue
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size >= 8) {
                            val isDir = parts[0].startsWith("d")
                            val name =
                                if (parts[5].matches("""\d{4}-\d{2}-\d{2}""".toRegex())) {
                                    parts.subList(7, parts.size).joinToString(" ")
                                } else if (parts.size >= 9 && parts[7].matches("""\d{2}:\d{2}|\d{4}""".toRegex())) {
                                    parts.subList(8, parts.size).joinToString(" ")
                                } else {
                                    parts.subList(7, parts.size).joinToString(" ")
                                }
                            if (name == "." || name == "..") continue
                            val filePath = if (targetPath.endsWith("/")) "$targetPath$name" else "$targetPath/$name"
                            list.add(
                                FileItem(
                                    name = name,
                                    path = filePath,
                                    isDirectory = isDir,
                                    sizeBytes = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
                                    canRead = true,
                                    canWrite = true,
                                ),
                            )
                        }
                    }
                    return@runCatching list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
                }

                throw IllegalStateException("Каталог недоступен для чтения: $targetPath")
            }
        }

    override suspend fun getFile(path: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetPath =
                    when {
                        path.isBlank() || path == "~" -> Environment.getExternalStorageDirectory().absolutePath
                        path.startsWith("/") -> path
                        path.startsWith("~/") -> "${Environment.getExternalStorageDirectory().absolutePath}/${path.removePrefix("~/")}"
                        else -> "${Environment.getExternalStorageDirectory().absolutePath}/$path"
                    }

                val file = File(targetPath)
                if (file.exists() && file.canRead()) {
                    return@runCatching file
                }

                // If root needed to extract file
                val tmpDest = File(context.cacheDir, "extracted_${file.name}")
                val result = Shell.cmd("cp \"$targetPath\" \"${tmpDest.absolutePath}\" && chmod 666 \"${tmpDest.absolutePath}\"").exec()
                if (result.isSuccess && tmpDest.exists()) {
                    return@runCatching tmpDest
                }

                throw IllegalStateException("Файл не существует или нет прав доступа: $targetPath")
            }
        }

    override suspend fun downloadFileFromUrl(
        url: String,
        targetPath: String?,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val maxAllowedBytes = 2L * 1024 * 1024 * 1024 // 2 ГБ (лимит для Telegram Bot API)
                val minSafetyDiskMargin = 100L * 1024 * 1024 // 100 МБ резерв для стабильности ОС

                AppLogger.i("DefaultFileManagerRepository", "🌐 Начало загрузки файла: $url")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ошибка ${connection.responseCode}: ${connection.responseMessage}")
                }

                val contentLength =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        connection.contentLengthLong
                    } else {
                        connection.contentLength.toLong()
                    }

                if (contentLength > maxAllowedBytes) {
                    val sizeMb = contentLength / (1024 * 1024)
                    throw IllegalStateException("Размер файла ($sizeMb МБ) превышает лимит 2 ГБ")
                }

                val fileName = url.substringAfterLast("/").substringBefore("?").ifBlank { "download_${System.currentTimeMillis()}" }
                val destination =
                    if (!targetPath.isNullOrBlank()) {
                        val targetFile = File(targetPath)
                        if (targetFile.isDirectory) File(targetFile, fileName) else targetFile
                    } else {
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    }

                val parentDir = destination.parentFile ?: context.filesDir
                parentDir.mkdirs()

                val usableSpace = parentDir.usableSpace
                if (contentLength > 0 && (usableSpace - contentLength) < minSafetyDiskMargin) {
                    val freeMb = usableSpace / (1024 * 1024)
                    val needMb = contentLength / (1024 * 1024)
                    throw IllegalStateException(
                        "Недостаточно места на диске (свободно: $freeMb МБ, требуется: $needMb МБ + 100 МБ резерва)",
                    )
                }

                var totalBytesRead = 0L
                try {
                    connection.inputStream.use { input ->
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                totalBytesRead += bytesRead
                                if (totalBytesRead > maxAllowedBytes) {
                                    destination.delete()
                                    throw IllegalStateException("Превышен максимальный лимит 2 ГБ в процессе загрузки")
                                }
                                output.write(buffer, 0, bytesRead)
                            }
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    destination.delete()
                    AppLogger.e("DefaultFileManagerRepository", "❌ Сбой загрузки $url: ${e.message}")
                    throw e
                }

                AppLogger.i(
                    "DefaultFileManagerRepository",
                    "✅ Файл успешно скачан (${totalBytesRead / 1024} КБ): ${destination.absolutePath}",
                )
                destination
            }
        }

    override suspend fun createZipArchive(sourcePath: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) {
                    throw IllegalStateException("Исходный путь не существует: $sourcePath")
                }

                AppLogger.i("DefaultFileManagerRepository", "📦 Архивация в ZIP: ${sourceFile.absolutePath}...")
                val zipFile = File(context.cacheDir, "${sourceFile.nameWithoutExtension}_archive.zip")
                if (zipFile.exists()) zipFile.delete()

                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    if (sourceFile.isDirectory) {
                        zipDirectory(sourceFile, sourceFile.name, zos)
                    } else {
                        zipSingleFile(sourceFile, zos)
                    }
                }

                AppLogger.i(
                    "DefaultFileManagerRepository",
                    "✅ ZIP архив успешно создан (${zipFile.length() / 1024} КБ): ${zipFile.absolutePath}",
                )
                zipFile
            }
        }

    private fun zipDirectory(
        folder: File,
        parentFolder: String,
        zos: ZipOutputStream,
    ) {
        val files = folder.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(file, "$parentFolder/${file.name}", zos)
                continue
            }
            FileInputStream(file).use { fis ->
                val entry = ZipEntry("$parentFolder/${file.name}")
                zos.putNextEntry(entry)
                fis.copyTo(zos)
                zos.closeEntry()
            }
        }
    }

    private fun zipSingleFile(
        file: File,
        zos: ZipOutputStream,
    ) {
        FileInputStream(file).use { fis ->
            val entry = ZipEntry(file.name)
            zos.putNextEntry(entry)
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    override suspend fun deleteFileOrDirectory(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                AppLogger.w("DefaultFileManagerRepository", "🗑️ Удаление файла/директории: $path")
                val file = File(path)
                if (file.exists() && file.deleteRecursively()) {
                    AppLogger.i("DefaultFileManagerRepository", "✅ Успешно удалено (нативно): $path")
                    return@runCatching
                }
                if (rootStatusProvider.isRootGrantedSync()) {
                    val res = Shell.cmd("rm -rf \"$path\"").exec()
                    if (res.isSuccess) {
                        AppLogger.i("DefaultFileManagerRepository", "✅ Успешно удалено (через Root): $path")
                        return@runCatching
                    }
                }
                AppLogger.e("DefaultFileManagerRepository", "❌ Не удалось удалить: $path")
                throw IllegalStateException("Не удалось удалить: $path")
            }
        }

    override suspend fun queueUnsentFile(unsentFile: UnsentFile) {
        _unsentFiles.value = _unsentFiles.value + unsentFile
    }

    override suspend fun removeUnsentFile(id: String) {
        _unsentFiles.value = _unsentFiles.value.filterNot { it.id == id }
    }

    override suspend fun clearUnsentFiles() {
        _unsentFiles.value = emptyList()
        AppLogger.i("DefaultFileManagerRepository", "Очередь неотправленных файлов очищена")
    }
}
