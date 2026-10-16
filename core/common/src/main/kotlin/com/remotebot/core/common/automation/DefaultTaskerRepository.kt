package com.remotebot.core.common.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.TaskerTask
import com.remotebot.domain.repository.TaskerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultTaskerRepository(
    private val context: Context,
) : TaskerRepository {
    companion object {
        const val ACTION_TASK = "net.dinglisch.android.tasker.ACTION_TASK"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_VAR_PREFIX = "var_"
        const val EXTRA_PARAM1 = "par1"
        const val EXTRA_PARAM2 = "par2"
        const val EXTRA_PARAM3 = "par3"

        val TASKER_PACKAGES =
            listOf(
                "net.dinglisch.android.taskerm",
                "net.dinglisch.android.tasker",
            )
    }

    override suspend fun executeTask(task: TaskerTask): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val intent =
                    Intent(ACTION_TASK).apply {
                        putExtra(EXTRA_TASK_NAME, task.taskName)
                        task.param1?.let { putExtra(EXTRA_PARAM1, it) }
                        task.param2?.let { putExtra(EXTRA_PARAM2, it) }
                        task.param3?.let { putExtra(EXTRA_PARAM3, it) }
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    }
                context.sendBroadcast(intent)
                AppLogger.i("DefaultTaskerRepository", "Sent Tasker Intent for task: ${task.taskName}")
            }
        }

    override fun isTaskerInstalled(): Boolean {
        for (pkg in TASKER_PACKAGES) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return false
    }
}
