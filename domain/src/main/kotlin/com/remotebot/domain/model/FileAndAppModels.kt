package com.remotebot.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val lastModifiedMs: Long = 0,
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
)

@Serializable
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long = 0,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
)

@Serializable
data class RunningProcess(
    val pid: Int,
    val processName: String,
    val packageName: String?,
    val memoryUsageMb: Double = 0.0,
    val cpuUsagePercent: Double = 0.0,
)

@Serializable
data class UnsentFile(
    val id: String,
    val filePath: String,
    val destinationChatId: Long,
    val fileType: String, // photo, video, voice, doc
    val caption: String? = null,
    val attempts: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
)
