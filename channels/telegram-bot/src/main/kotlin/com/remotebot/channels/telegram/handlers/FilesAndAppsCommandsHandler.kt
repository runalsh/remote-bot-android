package com.remotebot.channels.telegram.handlers

import com.remotebot.domain.repository.BotConfigRepository
import com.remotebot.domain.model.BotLanguage
import com.remotebot.channels.telegram.strings.TelegramBotStrings
import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.repository.AppManagerRepository
import com.remotebot.domain.repository.FileManagerRepository
import java.io.File

class FilesAndAppsCommandsHandler(
    private val fileManagerRepository: FileManagerRepository,
    private val appManagerRepository: AppManagerRepository,
    private val botConfigRepository: BotConfigRepository? = null,
) : CommandHandler {
    private val strings
        get() = TelegramBotStrings.get(botConfigRepository?.config?.value?.botLanguage ?: BotLanguage.AUTO)

    override val commandNames: List<String> =
        listOf(
            "get_file",
            "gf",
            "download",
            "ls",
            "dir",
            "ls_interactive",
            "lsi",
            "apps",
            "app_launch",
            "install",
            "taskmanager",
            "kill",
            "unsent_files",
        )

    override val isDestructive: Boolean = false

    override suspend fun execute(command: IncomingCommand): CommandResult {
        val cmd = command.name.lowercase().removePrefix("/")
        return when (cmd) {
            "get_file", "gf" -> handleGetFile(command.args)
            "download" -> handleDownload(command.args)
            "ls", "dir" -> handleListFiles(command.args)
            "ls_interactive", "lsi" -> handleListFilesInteractive(command.args)
            "apps" -> handleApps(command.args)
            "app_launch" -> handleLaunchApp(command.args)
            "install" -> handleInstallApk(command.args)
            "taskmanager" -> handleTaskManager()
            "kill" -> handleKillProcess(command.args)
            "unsent_files" -> handleUnsentFiles()
            else -> CommandResult.Error(strings.faUnknownCmd(cmd))
        }
    }

    private suspend fun handleGetFile(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                strings.faGetFileUsage(),
            )
        }

        val lastArgIsZip = args.size > 1 && args.last().equals("zip", ignoreCase = true)
        val rawPathParts = if (lastArgIsZip) args.dropLast(1) else args
        val rawPath = rawPathParts.joinToString(" ")
        val path = NavigationCache.resolve(rawPath)
        val forceZip = lastArgIsZip || File(path).isDirectory

        return if (forceZip) {
            fileManagerRepository.createZipArchive(path).fold(
                onSuccess = { zipFile ->
                    CommandResult.File(
                        filePath = zipFile.absolutePath,
                        caption = strings.faArchiveCaption(File(path).name, formatFileSize(zipFile.length())),
                        autoDelete = true,
                    )
                },
                onFailure = {
                    CommandResult.Error(strings.faZipError(it.localizedMessage ?: it.message ?: ""))
                },
            )
        } else {
            fileManagerRepository.getFile(path).fold(
                onSuccess = { file ->
                    CommandResult.File(
                        filePath = file.absolutePath,
                        caption = strings.faFileCaption(file.name, formatFileSize(file.length())),
                    )
                },
                onFailure = {
                    CommandResult.Error(strings.faGetFileError(it.localizedMessage ?: it.message ?: ""))
                },
            )
        }
    }

    private suspend fun handleDownload(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                strings.faDownloadUsage(),
            )
        }

        val url = args[0]
        val targetPath = args.getOrNull(1)

        return fileManagerRepository.downloadFileFromUrl(url, targetPath).fold(
            onSuccess = { downloadedFile ->
                CommandResult.Text(
                    strings.faDownloaded(downloadedFile.absolutePath, formatFileSize(downloadedFile.length())),
                )
            },
            onFailure = {
                CommandResult.Error(strings.faDownloadError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleListFiles(args: List<String>): CommandResult {
        val rawPath = if (args.isNotEmpty()) args.joinToString(" ") else "~"
        val path = NavigationCache.resolve(rawPath)

        return fileManagerRepository.listFiles(path).fold(
            onSuccess = { items ->
                if (items.isEmpty()) {
                    CommandResult.Text(strings.faDirEmpty(path))
                } else {
                    val sb = StringBuilder(strings.faDirContentHeader(path))
                    items.take(40).forEach { item ->
                        val icon = if (item.isDirectory) "📁" else "📄"
                        val sizeStr = if (item.isDirectory) "" else " (${formatFileSize(item.sizeBytes)})"
                        sb.append("$icon <code>${item.name}</code>$sizeStr\n")
                    }
                    if (items.size > 40) {
                        sb.append(strings.faMoreItems(items.size - 40))
                    }
                    CommandResult.Text(sb.toString().trimEnd())
                }
            },
            onFailure = {
                CommandResult.Error(strings.faDirReadError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleListFilesInteractive(args: List<String>): CommandResult {
        val rawPath = if (args.isNotEmpty()) args.joinToString(" ") else "~"
        val path = NavigationCache.resolve(rawPath)

        return fileManagerRepository.listFiles(path).fold(
            onSuccess = { items ->
                val rows = mutableListOf<List<com.remotebot.domain.model.InlineKeyboardButton>>()

                // Navigation buttons at the top (Up one level, Refresh)
                val currentFile = File(path)
                val parentPath = currentFile.parent
                val navRow = mutableListOf<com.remotebot.domain.model.InlineKeyboardButton>()
                if (parentPath != null && parentPath != "/" && path != "~") {
                    val parentToken = NavigationCache.register(parentPath)
                    navRow.add(
                        com.remotebot.domain.model
                            .InlineKeyboardButton(strings.faBtnUp(), callbackData = "/lsi $parentToken"),
                    )
                }
                val currentToken = NavigationCache.register(path)
                navRow.add(
                    com.remotebot.domain.model
                        .InlineKeyboardButton(strings.faBtnRefresh(), callbackData = "/lsi $currentToken"),
                )
                if (path != "~" && path != "/storage/emulated/0" && path != "/sdcard") {
                    navRow.add(
                        com.remotebot.domain.model
                            .InlineKeyboardButton(strings.faBtnZip(), callbackData = "/gf $currentToken zip"),
                    )
                }
                rows.add(navRow)

                // Separate directories and files
                val dirs = items.filter { it.isDirectory }.take(30)
                val files = items.filter { !it.isDirectory }.take(30)

                // Directories 2 per row
                dirs.chunked(2).forEach { chunk ->
                    rows.add(
                        chunk.map { dir ->
                            val shortName = if (dir.name.length > 20) dir.name.take(18) + "…" else dir.name
                            val dirToken = NavigationCache.register(dir.path)
                            com.remotebot.domain.model.InlineKeyboardButton(
                                text = "📁 $shortName",
                                callbackData = "/lsi $dirToken",
                            )
                        },
                    )
                }

                // Files 1 per row for easy download click
                files.forEach { file ->
                    val sizeFormatted = formatFileSize(file.sizeBytes)
                    val shortName = if (file.name.length > 24) file.name.take(22) + "…" else file.name
                    val fileToken = NavigationCache.register(file.path)
                    rows.add(
                        listOf(
                            com.remotebot.domain.model.InlineKeyboardButton(
                                text = "📄 $shortName ($sizeFormatted)",
                                callbackData = "/gf $fileToken",
                            ),
                        ),
                    )
                }

                val totalCount = items.size
                val dirsCount = items.count { it.isDirectory }
                val filesCount = items.count { !it.isDirectory }
                val text =
                    strings.faExplorerHeader(path, dirsCount, filesCount, totalCount)

                CommandResult.Text(
                    message = text,
                    keyboard =
                        com.remotebot.domain.model
                            .InlineKeyboardMarkup(rows),
                )
            },
            onFailure = {
                CommandResult.Error(strings.faDirReadError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleApps(args: List<String>): CommandResult {
        val includeSystem = args.any { it.equals("system", ignoreCase = true) || it.equals("all", ignoreCase = true) }
        val filter = args.firstOrNull { !it.equals("system", ignoreCase = true) && !it.equals("all", ignoreCase = true) }

        val apps = appManagerRepository.getInstalledApps(includeSystem, filter)
        if (apps.isEmpty()) {
            return CommandResult.Text(strings.faNoApps())
        }

        val sb = StringBuilder(strings.faAppsHeader(apps.size))
        apps.take(30).forEach { app ->
            val sysIcon = if (app.isSystemApp) "⚙️" else "📦"
            val ver = if (app.versionName != null) " (v${app.versionName})" else ""
            sb.append("$sysIcon <b>${app.appName}</b>$ver\n   <code>${app.packageName}</code>\n\n")
        }
        if (apps.size > 30) {
            sb.append(strings.faMoreApps(apps.size - 30))
        }
        sb.append(strings.faLaunchHint())
        return CommandResult.Text(sb.toString().trimEnd())
    }

    private suspend fun handleLaunchApp(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                strings.faLaunchUsage(),
            )
        }

        val pkg = args[0]
        return appManagerRepository.launchApp(pkg).fold(
            onSuccess = {
                CommandResult.Text(strings.faAppLaunched(pkg))
            },
            onFailure = {
                CommandResult.Error(strings.faLaunchError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleInstallApk(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Text(
                strings.faInstallHelp(),
            )
        }

        val apkPath = args[0]
        return appManagerRepository.installApk(apkPath).fold(
            onSuccess = {
                CommandResult.Text(strings.faApkInstalled(apkPath))
            },
            onFailure = {
                CommandResult.Error(strings.faInstallError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private suspend fun handleTaskManager(): CommandResult =
        appManagerRepository.getRunningProcesses().fold(
            onSuccess = { processes ->
                if (processes.isEmpty()) {
                    CommandResult.Text(strings.faNoProcesses())
                } else {
                    val sb = StringBuilder(strings.faTaskManagerHeader())
                    processes.take(25).forEach { proc ->
                        val cpuStr = if (proc.cpuUsagePercent > 0) "CPU: ${"%.1f".format(proc.cpuUsagePercent)}%" else ""
                        val memStr = if (proc.memoryUsageMb > 0) "MEM: ${"%.1f".format(proc.memoryUsageMb)}%" else ""
                        val stats = listOf(cpuStr, memStr).filter { it.isNotBlank() }.joinToString(" | ")
                        val statsFormatted = if (stats.isNotBlank()) " ($stats)" else ""

                        sb.append("🔹 <b>PID: ${proc.pid}</b> | <code>${proc.processName}</code>$statsFormatted\n")
                    }
                    sb.append(strings.faKillHint())
                    CommandResult.Text(sb.toString().trimEnd())
                }
            },
            onFailure = {
                CommandResult.Error(strings.faProcessListError(it.localizedMessage ?: it.message ?: ""))
            },
        )

    private suspend fun handleKillProcess(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                strings.faKillUsage(),
            )
        }

        val target = args[0]
        val pid = target.toIntOrNull()
        val pkg = if (pid == null) target else null

        return appManagerRepository.killProcess(pid, pkg).fold(
            onSuccess = {
                CommandResult.Text(strings.faProcessKilled(target))
            },
            onFailure = {
                CommandResult.Error(strings.faKillError(it.localizedMessage ?: it.message ?: ""))
            },
        )
    }

    private fun handleUnsentFiles(): CommandResult {
        val files = fileManagerRepository.unsentFiles.value
        if (files.isEmpty()) {
            return CommandResult.Text(strings.faUnsentEmpty())
        }

        val sb = StringBuilder(strings.faUnsentHeader(files.size))
        files.forEach { file ->
            sb.append(strings.faUnsentLine(file.filePath, file.fileType, file.attempts) + "\n")
        }
        return CommandResult.Text(sb.toString().trimEnd())
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
