package com.remotebot.core.common.tracker

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.OperationInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class ActiveOperationsTracker(
    private val logger: AppLogger? = null,
) {
    private data class TrackedJob(
        val info: OperationInfo,
        val job: Job? = null,
        val onCancel: (() -> Unit)? = null,
    )

    private val activeJobs = ConcurrentHashMap<String, TrackedJob>()
    private val _operationsFlow = MutableStateFlow<List<OperationInfo>>(emptyList())
    val operationsFlow: StateFlow<List<OperationInfo>> = _operationsFlow.asStateFlow()

    private fun updateState() {
        _operationsFlow.value = activeJobs.values.map { it.info }
    }

    fun register(
        id: String,
        name: String,
        job: Job? = null,
        description: String = "",
        onCancel: (() -> Unit)? = null,
    ) {
        val info = OperationInfo(id = id, name = name, description = description)
        activeJobs[id] = TrackedJob(info, job, onCancel)
        logger?.i("OperationsTracker", "Зарегистрирована операция [$id]: $name ${if (description.isNotBlank()) "($description)" else ""}")
        job?.invokeOnCompletion {
            activeJobs.remove(id)
            logger?.d("OperationsTracker", "Завершена операция [$id]: $name")
            updateState()
        }
        updateState()
    }

    fun unregister(id: String) {
        val removed = activeJobs.remove(id)
        if (removed != null) {
            logger?.d("OperationsTracker", "Снята с учета операция [$id]: ${removed.info.name}")
        }
        updateState()
    }

    fun list(): List<OperationInfo> = activeJobs.values.map { it.info }

    fun cancel(id: String): Boolean {
        val tracked = activeJobs.remove(id) ?: return false
        logger?.w("OperationsTracker", "Отмена операции [$id]: ${tracked.info.name}")
        try {
            tracked.job?.cancel()
        } catch (_: Exception) {
        }
        try {
            tracked.onCancel?.invoke()
        } catch (_: Exception) {
        }
        updateState()
        return true
    }

    fun cancelAll(): Int {
        val count = activeJobs.size
        logger?.w("OperationsTracker", "Отмена ВСЕХ операций (всего $count)")
        for ((_, tracked) in activeJobs) {
            try {
                tracked.job?.cancel()
            } catch (_: Exception) {
            }
            try {
                tracked.onCancel?.invoke()
            } catch (_: Exception) {
            }
        }
        activeJobs.clear()
        updateState()
        return count
    }
}
