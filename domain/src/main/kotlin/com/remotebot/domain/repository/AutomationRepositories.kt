package com.remotebot.domain.repository

import com.remotebot.domain.model.ScheduledTimer
import com.remotebot.domain.model.ScriptExecutionResult
import com.remotebot.domain.model.SystemTriggerRule
import com.remotebot.domain.model.TaskerTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TaskerRepository {
    suspend fun executeTask(task: TaskerTask): Result<Unit>

    fun isTaskerInstalled(): Boolean
}

interface AutomationRepository {
    val timers: StateFlow<List<ScheduledTimer>>
    val triggerRules: StateFlow<List<SystemTriggerRule>>
    val timerTriggers: Flow<ScheduledTimer>

    suspend fun addTimer(
        commandText: String,
        delayMs: Long,
        intervalMs: Long? = null,
    ): ScheduledTimer

    suspend fun removeTimer(timerId: String)

    suspend fun setTimerEnabled(
        timerId: String,
        enabled: Boolean,
    )

    suspend fun addTriggerRule(rule: SystemTriggerRule)

    suspend fun removeTriggerRule(ruleId: String)

    suspend fun executeScript(script: String): ScriptExecutionResult
}
