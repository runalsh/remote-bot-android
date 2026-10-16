package com.remotebot.domain.provider

import kotlinx.coroutines.flow.StateFlow

interface RootStatusProvider {
    val isRootGranted: StateFlow<Boolean?>
    val isRootSimulatedAbsent: StateFlow<Boolean>

    suspend fun checkRoot(forceRefresh: Boolean = false): Boolean

    fun isRootGrantedSync(): Boolean

    fun setSimulateRootAbsent(simulatedAbsent: Boolean)
}
