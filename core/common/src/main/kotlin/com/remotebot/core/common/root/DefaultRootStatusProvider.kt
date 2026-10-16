package com.remotebot.core.common.root

import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.provider.RootStatusProvider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultRootStatusProvider(
    private val logger: AppLogger,
) : RootStatusProvider {
    private val _isRootGranted = MutableStateFlow<Boolean?>(null)
    override val isRootGranted: StateFlow<Boolean?> = _isRootGranted.asStateFlow()

    private val _isRootSimulatedAbsent = MutableStateFlow(false)
    override val isRootSimulatedAbsent: StateFlow<Boolean> = _isRootSimulatedAbsent.asStateFlow()

    private val mutex = Mutex()

    override fun setSimulateRootAbsent(simulatedAbsent: Boolean) {
        _isRootSimulatedAbsent.value = simulatedAbsent
        if (simulatedAbsent) {
            _isRootGranted.value = false
            logger.w("RootStatusProvider", "🛠️ [Debug] Включена эмуляция отсутствия Root-прав")
        } else {
            _isRootGranted.value = null // Сброс для перепроверки
            logger.i("RootStatusProvider", "🛠️ [Debug] Выключена эмуляция отсутствия Root-прав")
        }
    }

    override suspend fun checkRoot(forceRefresh: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (_isRootSimulatedAbsent.value) {
                    _isRootGranted.value = false
                    return@withContext false
                }

                if (!forceRefresh && _isRootGranted.value != null) {
                    return@withContext _isRootGranted.value == true
                }

                logger.d("RootStatusProvider", "Запуск фоновой проверки Root-прав...")
                val granted =
                    try {
                        if (Shell.isAppGrantedRoot() == true) {
                            true
                        } else {
                            val result = Shell.cmd("id").exec()
                            result.isSuccess && (result.out.any { it.contains("uid=0") } || Shell.isAppGrantedRoot() == true)
                        }
                    } catch (e: Exception) {
                        logger.w("RootStatusProvider", "Ошибка при определении Root: ${e.localizedMessage}")
                        false
                    }

                _isRootGranted.value = granted
                logger.i(
                    "RootStatusProvider",
                    "Root статус: ${if (granted) "✅ ВЫДАН (UID 0 / Superuser активен)" else "❌ ОТСУТСТВУЕТ / НЕ ПРЕДОСТАВЛЕН"}",
                )
                granted
            }
        }

    override fun isRootGrantedSync(): Boolean {
        if (_isRootSimulatedAbsent.value) return false
        return _isRootGranted.value ?: (Shell.isAppGrantedRoot() == true)
    }
}
