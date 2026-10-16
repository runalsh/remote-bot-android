package com.remotebot.domain.repository

import com.remotebot.domain.model.FileItem
import com.remotebot.domain.model.InstalledApp
import com.remotebot.domain.model.RunningProcess
import com.remotebot.domain.model.UnsentFile
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface FileManagerRepository {
    suspend fun listFiles(path: String): Result<List<FileItem>>

    suspend fun getFile(path: String): Result<File>

    suspend fun downloadFileFromUrl(
        url: String,
        targetPath: String? = null,
    ): Result<File>

    suspend fun createZipArchive(sourcePath: String): Result<File>

    suspend fun deleteFileOrDirectory(path: String): Result<Unit>

    val unsentFiles: StateFlow<List<UnsentFile>>

    suspend fun queueUnsentFile(unsentFile: UnsentFile)

    suspend fun removeUnsentFile(id: String)

    suspend fun clearUnsentFiles()
}

interface AppManagerRepository {
    suspend fun getInstalledApps(
        includeSystem: Boolean = false,
        filter: String? = null,
    ): List<InstalledApp>

    suspend fun launchApp(packageName: String): Result<Unit>

    suspend fun installApk(apkPath: String): Result<Unit>

    suspend fun getRunningProcesses(): Result<List<RunningProcess>>

    suspend fun killProcess(
        pid: Int?,
        packageName: String?,
    ): Result<Unit>
}
