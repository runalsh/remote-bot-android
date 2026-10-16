package com.remotebot.core.common.automation

import android.content.Context
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.ScheduledTimer
import com.remotebot.domain.model.ScriptExecutionResult
import com.remotebot.domain.model.SystemTriggerRule
import com.remotebot.domain.repository.AutomationRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DefaultAutomationRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) : AutomationRepository {
    private val _timers = MutableStateFlow<List<ScheduledTimer>>(emptyList())
    override val timers: StateFlow<List<ScheduledTimer>> = _timers.asStateFlow()

    private val _triggerRules = MutableStateFlow<List<SystemTriggerRule>>(emptyList())
    override val triggerRules: StateFlow<List<SystemTriggerRule>> = _triggerRules.asStateFlow()

    private val _timerTriggers = MutableSharedFlow<ScheduledTimer>(extraBufferCapacity = 32)
    override val timerTriggers: Flow<ScheduledTimer> = _timerTriggers.asSharedFlow()

    private val activeTimerJobs = ConcurrentHashMap<String, Job>()

    override suspend fun addTimer(
        commandText: String,
        delayMs: Long,
        intervalMs: Long?,
    ): ScheduledTimer {
        val timer =
            ScheduledTimer(
                id = UUID.randomUUID().toString().take(6),
                commandText = commandText,
                triggerAtMs = System.currentTimeMillis() + delayMs,
                intervalMs = intervalMs,
                isEnabled = true,
            )
        _timers.value = _timers.value + timer
        AppLogger.i(
            "DefaultAutomationRepository",
            "⏰ Добавлен таймер [${timer.id}]: '$commandText', задержка: ${delayMs / 1000}с, интервал: ${intervalMs?.let {
                "${it / 1000}с"
            } ?: "однократно"}",
        )
        scheduleTimerJob(timer, delayMs)
        return timer
    }

    override suspend fun removeTimer(timerId: String) {
        activeTimerJobs.remove(timerId)?.cancel()
        _timers.value = _timers.value.filterNot { it.id == timerId }
        AppLogger.i("DefaultAutomationRepository", "🗑️ Таймер [$timerId] удален")
    }

    override suspend fun setTimerEnabled(
        timerId: String,
        enabled: Boolean,
    ) {
        _timers.value =
            _timers.value.map {
                if (it.id == timerId) it.copy(isEnabled = enabled) else it
            }
        val timer = _timers.value.find { it.id == timerId } ?: return
        if (enabled) {
            val remainingDelay = maxOf(0L, timer.triggerAtMs - System.currentTimeMillis())
            scheduleTimerJob(timer, remainingDelay)
        } else {
            activeTimerJobs.remove(timerId)?.cancel()
        }
    }

    private fun scheduleTimerJob(
        timer: ScheduledTimer,
        initialDelayMs: Long,
    ) {
        activeTimerJobs.remove(timer.id)?.cancel()
        val job =
            scope.launch(Dispatchers.Default) {
                delay(initialDelayMs)
                if (!isActive) return@launch

                _timerTriggers.tryEmit(timer)
                AppLogger.i("DefaultAutomationRepository", "Timer triggered: ${timer.id} -> ${timer.commandText}")

                val interval = timer.intervalMs
                if (interval != null && interval > 0) {
                    while (isActive) {
                        delay(interval)
                        _timerTriggers.tryEmit(timer)
                    }
                } else {
                    // One-shot timer finished
                    _timers.value = _timers.value.filterNot { it.id == timer.id }
                }
            }
        activeTimerJobs[timer.id] = job
    }

    override suspend fun addTriggerRule(rule: SystemTriggerRule) {
        _triggerRules.value = _triggerRules.value + rule
    }

    override suspend fun removeTriggerRule(ruleId: String) {
        _triggerRules.value = _triggerRules.value.filterNot { it.id == ruleId }
    }

    override suspend fun executeScript(script: String): ScriptExecutionResult =
        withContext(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            try {
                // Safe execution via Linux Shell / QuickJS runner
                val shellResult = Shell.cmd(script).exec()
                val elapsed = System.currentTimeMillis() - startMs
                if (shellResult.isSuccess) {
                    ScriptExecutionResult(
                        isSuccess = true,
                        output = shellResult.out.joinToString("\n").ifBlank { "OK (код 0)" },
                        executionTimeMs = elapsed,
                    )
                } else {
                    ScriptExecutionResult(
                        isSuccess = false,
                        output = shellResult.out.joinToString("\n"),
                        error = shellResult.err.joinToString("\n").ifBlank { "Ошибка выполнения (код ${shellResult.code})" },
                        executionTimeMs = elapsed,
                    )
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startMs
                ScriptExecutionResult(
                    isSuccess = false,
                    output = null,
                    error = e.localizedMessage ?: e.message,
                    executionTimeMs = elapsed,
                )
            }
        }
}
