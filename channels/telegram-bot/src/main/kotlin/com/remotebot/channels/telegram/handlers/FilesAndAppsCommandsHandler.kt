package com.remotebot.channels.telegram.handlers

import com.remotebot.domain.dispatcher.CommandHandler
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.IncomingCommand
import com.remotebot.domain.repository.AppManagerRepository
import com.remotebot.domain.repository.FileManagerRepository
import java.io.File

class FilesAndAppsCommandsHandler(
    private val fileManagerRepository: FileManagerRepository,
    private val appManagerRepository: AppManagerRepository,
) : CommandHandler {
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
            else -> CommandResult.Error("Неизвестная команда управления файлами/приложениями: $cmd")
        }
    }

    private suspend fun handleGetFile(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                "Формат: <code>/get_file &lt;путь_к_файлу_или_папке&gt; [zip]</code>\nПример: <code>/get_file /sdcard/DCIM/Camera/photo.jpg</code>\nПример для папки: <code>/get_file /sdcard/Documents zip</code>",
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
                        caption = "📦 Архив: <code>${File(path).name}</code> (${formatFileSize(zipFile.length())})",
                        autoDelete = true,
                    )
                },
                onFailure = {
                    CommandResult.Error("❌ Ошибка создания zip-архива: ${it.localizedMessage ?: it.message}")
                },
            )
        } else {
            fileManagerRepository.getFile(path).fold(
                onSuccess = { file ->
                    CommandResult.File(
                        filePath = file.absolutePath,
                        caption = "📄 Файл: <code>${file.name}</code> (${formatFileSize(file.length())})",
                    )
                },
                onFailure = {
                    CommandResult.Error("❌ Ошибка получения файла: ${it.localizedMessage ?: it.message}")
                },
            )
        }
    }

    private suspend fun handleDownload(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                "Формат: <code>/download &lt;URL&gt; [путь_назначения]</code>\nПример: <code>/download https://example.com/file.apk</code>",
            )
        }

        val url = args[0]
        val targetPath = args.getOrNull(1)

        return fileManagerRepository.downloadFileFromUrl(url, targetPath).fold(
            onSuccess = { downloadedFile ->
                CommandResult.Text(
                    "✅ <b>Файл успешно скачан:</b>\n<code>${downloadedFile.absolutePath}</code> (${formatFileSize(
                        downloadedFile.length(),
                    )})",
                )
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка скачивания файла: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleListFiles(args: List<String>): CommandResult {
        val rawPath = if (args.isNotEmpty()) args.joinToString(" ") else "~"
        val path = NavigationCache.resolve(rawPath)

        return fileManagerRepository.listFiles(path).fold(
            onSuccess = { items ->
                if (items.isEmpty()) {
                    CommandResult.Text("📁 Каталог пуст: <code>$path</code>")
                } else {
                    val sb = StringBuilder("📁 <b>Содержимое каталога:</b> <code>$path</code>\n\n")
                    items.take(40).forEach { item ->
                        val icon = if (item.isDirectory) "📁" else "📄"
                        val sizeStr = if (item.isDirectory) "" else " (${formatFileSize(item.sizeBytes)})"
                        sb.append("$icon <code>${item.name}</code>$sizeStr\n")
                    }
                    if (items.size > 40) {
                        sb.append("\n<i>... и еще ${items.size - 40} объектов</i>")
                    }
                    CommandResult.Text(sb.toString().trimEnd())
                }
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка чтения директории: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleListFilesInteractive(args: List<String>): CommandResult {
        val rawPath = if (args.isNotEmpty()) args.joinToString(" ") else "~"
        val path = NavigationCache.resolve(rawPath)

        return fileManagerRepository.listFiles(path).fold(
            onSuccess = { items ->
                val rows = mutableListOf<List<com.remotebot.domain.model.InlineKeyboardButton>>()

                // Навигационные кнопки в самом верху (На уровень выше, Обновить)
                val currentFile = File(path)
                val parentPath = currentFile.parent
                val navRow = mutableListOf<com.remotebot.domain.model.InlineKeyboardButton>()
                if (parentPath != null && parentPath != "/" && path != "~") {
                    val parentToken = NavigationCache.register(parentPath)
                    navRow.add(
                        com.remotebot.domain.model
                            .InlineKeyboardButton("⬆️ Вверх", callbackData = "/lsi $parentToken"),
                    )
                }
                val currentToken = NavigationCache.register(path)
                navRow.add(
                    com.remotebot.domain.model
                        .InlineKeyboardButton("🔄 Обновить", callbackData = "/lsi $currentToken"),
                )
                if (path != "~" && path != "/storage/emulated/0" && path != "/sdcard") {
                    navRow.add(
                        com.remotebot.domain.model
                            .InlineKeyboardButton("📦 Скачать ZIP", callbackData = "/gf $currentToken zip"),
                    )
                }
                rows.add(navRow)

                // Разделяем папки и файлы
                val dirs = items.filter { it.isDirectory }.take(30)
                val files = items.filter { !it.isDirectory }.take(30)

                // Папки по 2 в ряд
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

                // Файлы по 1 в ряд для удобного клика скачивания
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
                    """
                    📂 <b>Проводник:</b> <code>$path</code>
                    <i>Папок: $dirsCount, Файлов: $filesCount (Всего: $totalCount)</i>
                    
                    👉 <i>Клик по папке — переход внутрь. Клик по файлу — отправка в чат.</i>
                    """.trimIndent()

                CommandResult.Text(
                    message = text,
                    keyboard =
                        com.remotebot.domain.model
                            .InlineKeyboardMarkup(rows),
                )
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка чтения директории: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleApps(args: List<String>): CommandResult {
        val includeSystem = args.any { it.equals("system", ignoreCase = true) || it.equals("all", ignoreCase = true) }
        val filter = args.firstOrNull { !it.equals("system", ignoreCase = true) && !it.equals("all", ignoreCase = true) }

        val apps = appManagerRepository.getInstalledApps(includeSystem, filter)
        if (apps.isEmpty()) {
            return CommandResult.Text("📱 Приложений не найдено.")
        }

        val sb = StringBuilder("📱 <b>Установленные приложения (${apps.size}):</b>\n\n")
        apps.take(30).forEach { app ->
            val sysIcon = if (app.isSystemApp) "⚙️" else "📦"
            val ver = if (app.versionName != null) " (v${app.versionName})" else ""
            sb.append("$sysIcon <b>${app.appName}</b>$ver\n   <code>${app.packageName}</code>\n\n")
        }
        if (apps.size > 30) {
            sb.append("<i>... и еще ${apps.size - 30} приложений. Используйте фильтр: /apps &lt;поиск&gt;</i>\n")
        }
        sb.append("\n<i>Запуск: /app_launch &lt;package&gt;</i>")
        return CommandResult.Text(sb.toString().trimEnd())
    }

    private suspend fun handleLaunchApp(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                "Формат: <code>/app_launch &lt;package_name&gt;</code>\nПример: <code>/app_launch com.android.settings</code>",
            )
        }

        val pkg = args[0]
        return appManagerRepository.launchApp(pkg).fold(
            onSuccess = {
                CommandResult.Text("🚀 <b>Приложение запущено:</b> <code>$pkg</code>")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка запуска: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleInstallApk(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Text(
                """
                📦 <b>Установка APK-пакетов</b>

                Доступно два способа:
                1. <b>Отправить файл в чат</b>: просто прикрепите и отправьте <code>.apk</code> файл боту (можно с подписью <code>/install</code>). Бот скачает его и установит автоматически.
                2. <b>Указать локальный путь</b>: 
                <code>/install &lt;путь_к_apk&gt;</code>
                <i>Пример:</i> <code>/install /sdcard/Download/app.apk</code>
                """.trimIndent(),
            )
        }

        val apkPath = args[0]
        return appManagerRepository.installApk(apkPath).fold(
            onSuccess = {
                CommandResult.Text("✅ <b>APK пакет успешно установлен:</b>\n<code>$apkPath</code>")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка установки APK: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private suspend fun handleTaskManager(): CommandResult =
        appManagerRepository.getRunningProcesses().fold(
            onSuccess = { processes ->
                if (processes.isEmpty()) {
                    CommandResult.Text("⚙️ Нет активных процессов.")
                } else {
                    val sb = StringBuilder("⚙️ <b>Диспетчер процессов (TaskManager):</b>\n\n")
                    processes.take(25).forEach { proc ->
                        val cpuStr = if (proc.cpuUsagePercent > 0) "CPU: ${"%.1f".format(proc.cpuUsagePercent)}%" else ""
                        val memStr = if (proc.memoryUsageMb > 0) "MEM: ${"%.1f".format(proc.memoryUsageMb)}%" else ""
                        val stats = listOf(cpuStr, memStr).filter { it.isNotBlank() }.joinToString(" | ")
                        val statsFormatted = if (stats.isNotBlank()) " ($stats)" else ""

                        sb.append("🔹 <b>PID: ${proc.pid}</b> | <code>${proc.processName}</code>$statsFormatted\n")
                    }
                    sb.append("\n<i>Завершить: /kill &lt;PID&gt; или /kill &lt;package&gt;</i>")
                    CommandResult.Text(sb.toString().trimEnd())
                }
            },
            onFailure = {
                CommandResult.Error("❌ Не удалось получить список процессов: ${it.localizedMessage ?: it.message}")
            },
        )

    private suspend fun handleKillProcess(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error(
                "Формат: <code>/kill &lt;PID&gt;</code> или <code>/kill &lt;package_name&gt;</code>\nПример: <code>/kill 1234</code> или <code>/kill com.example.app</code>",
            )
        }

        val target = args[0]
        val pid = target.toIntOrNull()
        val pkg = if (pid == null) target else null

        return appManagerRepository.killProcess(pid, pkg).fold(
            onSuccess = {
                CommandResult.Text("🛑 Процесс / приложение <code>$target</code> принудительно остановлено.")
            },
            onFailure = {
                CommandResult.Error("❌ Ошибка завершения: ${it.localizedMessage ?: it.message}")
            },
        )
    }

    private fun handleUnsentFiles(): CommandResult {
        val files = fileManagerRepository.unsentFiles.value
        if (files.isEmpty()) {
            return CommandResult.Text("📤 Очередь неотправленных файлов пуста.")
        }

        val sb = StringBuilder("📤 <b>Очередь неотправленных файлов (${files.size}):</b>\n\n")
        files.forEach { file ->
            sb.append("• <code>${file.filePath}</code> (Тип: ${file.fileType}, Попыток: ${file.attempts})\n")
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
