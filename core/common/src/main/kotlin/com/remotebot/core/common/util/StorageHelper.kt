package com.remotebot.core.common.util

import android.content.Context
import android.os.Environment
import com.remotebot.domain.repository.BotConfigRepository
import java.io.File

object StorageHelper {
    fun getDefaultStorageDir(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return if (downloadDir != null) {
            File(downloadDir, "RemoteBot")
        } else {
            File("/storage/emulated/0/Download/RemoteBot")
        }
    }

    fun getStorageSubDir(
        context: Context,
        configRepo: BotConfigRepository?,
        subFolder: String,
    ): File {
        val customPath =
            configRepo
                ?.config
                ?.value
                ?.storageDirectory
                ?.trim()
        val baseDir =
            if (!customPath.isNullOrBlank()) {
                File(customPath)
            } else {
                val defaultDir = getDefaultStorageDir()
                if (!defaultDir.exists()) {
                    defaultDir.mkdirs()
                }
                if (defaultDir.exists() && defaultDir.canWrite()) {
                    defaultDir
                } else {
                    File(context.getExternalFilesDir(null) ?: context.cacheDir, "RemoteBot")
                }
            }

        val targetDir = File(baseDir, subFolder)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        try {
            targetDir.setReadable(true, false)
            targetDir.setWritable(true, false)
            targetDir.setExecutable(true, false)
        } catch (_: Exception) {
        }

        return targetDir
    }

    fun resolvePathFromTreeUri(
        context: Context,
        uri: android.net.Uri,
    ): String? =
        try {
            if (android.provider.DocumentsContract.isTreeUri(uri)) {
                val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                if (docId != null) {
                    val split = docId.split(":")
                    val type = split.getOrNull(0) ?: ""
                    val relativePath = if (split.size > 1) split[1] else ""
                    if ("primary".equals(type, ignoreCase = true)) {
                        val base = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
                        if (relativePath.isNotEmpty()) "$base/$relativePath" else base
                    } else if (type.isNotBlank()) {
                        if (relativePath.isNotEmpty()) "/storage/$type/$relativePath" else "/storage/$type"
                    } else {
                        uri.path
                    }
                } else {
                    uri.path
                }
            } else {
                uri.path
            }
        } catch (_: Exception) {
            uri.path
        }
}
