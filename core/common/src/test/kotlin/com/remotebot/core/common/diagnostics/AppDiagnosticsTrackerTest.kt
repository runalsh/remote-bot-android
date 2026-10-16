package com.remotebot.core.common.diagnostics

import com.remotebot.domain.model.AuthChannel
import com.remotebot.domain.model.ConnectionOutcome
import com.remotebot.domain.model.ServerType
import com.remotebot.domain.model.WakeupSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppDiagnosticsTrackerTest {
    @Before
    fun setUp() {
        AppDiagnosticsTracker.setTrackingEnabled(true)
        AppDiagnosticsTracker.clearHistory()
    }

    @After
    fun tearDown() {
        AppDiagnosticsTracker.clearHistory()
        AppDiagnosticsTracker.setTrackingEnabled(false)
    }

    @Test
    fun testTrackingDisabledIgnoresEvents() {
        AppDiagnosticsTracker.setTrackingEnabled(false)
        assertFalse(AppDiagnosticsTracker.isTracking())

        AppDiagnosticsTracker.recordWakeup(WakeupSource.PUSH_FCM)
        AppDiagnosticsTracker.recordConnection(
            serverId = "srv_1",
            serverName = "Push Gate",
            serverType = ServerType.PUSH_GATEWAY,
            outcome = ConnectionOutcome.SUCCESS,
        )
        AppDiagnosticsTracker.recordAuthFailure(AuthChannel.TELEGRAM, "12345")

        val snapshot = AppDiagnosticsTracker.metricsSnapshot.value
        assertEquals(0, snapshot.sinceStart.wakeupsTotal)
        assertTrue(snapshot.sinceStart.serverStats.isEmpty())
        assertEquals(0, snapshot.sinceStart.tgAuthFailures)
        assertFalse(snapshot.isTrackingEnabled)
    }

    @Test
    fun testWakeupsRecordingAndCategorization() {
        AppDiagnosticsTracker.recordWakeup(WakeupSource.PUSH_FCM)
        AppDiagnosticsTracker.recordWakeup(WakeupSource.PUSH_FCM)
        AppDiagnosticsTracker.recordWakeup(WakeupSource.PUSH_HMS)
        AppDiagnosticsTracker.recordWakeup(WakeupSource.SMS)
        AppDiagnosticsTracker.recordWakeup(WakeupSource.BATTERY_EVENT)

        val snapshot = AppDiagnosticsTracker.metricsSnapshot.value
        val summary = snapshot.last5Min

        assertEquals(5, summary.wakeupsTotal)
        assertEquals(2, summary.wakeupsBySource[WakeupSource.PUSH_FCM])
        assertEquals(1, summary.wakeupsBySource[WakeupSource.PUSH_HMS])
        assertEquals(1, summary.wakeupsBySource[WakeupSource.SMS])
        assertEquals(1, summary.wakeupsBySource[WakeupSource.BATTERY_EVENT])
    }

    @Test
    fun testPerServerConnectionStatsSeparateTracking() {
        // Сервер 1: Push Gateway
        AppDiagnosticsTracker.recordConnection(
            serverId = "gate_1",
            serverName = "EU Gateway",
            serverType = ServerType.PUSH_GATEWAY,
            outcome = ConnectionOutcome.SUCCESS,
        )
        AppDiagnosticsTracker.recordConnection(
            serverId = "gate_1",
            serverName = "EU Gateway",
            serverType = ServerType.PUSH_GATEWAY,
            outcome = ConnectionOutcome.FAILURE,
        )
        AppDiagnosticsTracker.recordConnection(
            serverId = "gate_1",
            serverName = "EU Gateway",
            serverType = ServerType.PUSH_GATEWAY,
            outcome = ConnectionOutcome.TIMEOUT,
        )

        // Сервер 2: Резервный Push Gateway
        AppDiagnosticsTracker.recordConnection(
            serverId = "gate_2",
            serverName = "US Gateway",
            serverType = ServerType.PUSH_GATEWAY,
            outcome = ConnectionOutcome.SUCCESS,
        )

        // Сервер 3: Telegram API официальный
        AppDiagnosticsTracker.recordConnection(
            serverId = "https://api.telegram.org",
            serverName = "api.telegram.org",
            serverType = ServerType.TELEGRAM_API,
            outcome = ConnectionOutcome.SUCCESS,
        )
        AppDiagnosticsTracker.recordConnection(
            serverId = "https://api.telegram.org",
            serverName = "api.telegram.org",
            serverType = ServerType.TELEGRAM_API,
            outcome = ConnectionOutcome.SUCCESS,
        )

        // Сервер 4: Telegram API кастомное зеркало
        AppDiagnosticsTracker.recordConnection(
            serverId = "https://tg-mirror.domain",
            serverName = "tg-mirror.domain",
            serverType = ServerType.TELEGRAM_API,
            outcome = ConnectionOutcome.TIMEOUT,
        )

        val snapshot = AppDiagnosticsTracker.metricsSnapshot.value
        val stats = snapshot.last5Min.serverStats

        assertEquals(4, stats.size)

        val gate1 = stats.find { it.serverId == "gate_1" }!!
        assertEquals("EU Gateway", gate1.serverName)
        assertEquals(ServerType.PUSH_GATEWAY, gate1.serverType)
        assertEquals(1, gate1.successCount)
        assertEquals(1, gate1.failureCount)
        assertEquals(1, gate1.timeoutCount)
        assertEquals(3, gate1.totalCount)

        val gate2 = stats.find { it.serverId == "gate_2" }!!
        assertEquals(1, gate2.successCount)
        assertEquals(0, gate2.failureCount)

        val tgMain = stats.find { it.serverId == "https://api.telegram.org" }!!
        assertEquals(ServerType.TELEGRAM_API, tgMain.serverType)
        assertEquals(2, tgMain.successCount)
        assertEquals(0, tgMain.timeoutCount)

        val tgMirror = stats.find { it.serverId == "https://tg-mirror.domain" }!!
        assertEquals(ServerType.TELEGRAM_API, tgMirror.serverType)
        assertEquals(0, tgMirror.successCount)
        assertEquals(1, tgMirror.timeoutCount)
    }

    @Test
    fun testAuthFailuresSeparateTelegramAndSms() {
        AppDiagnosticsTracker.recordAuthFailure(AuthChannel.TELEGRAM, "999888")
        AppDiagnosticsTracker.recordAuthFailure(AuthChannel.TELEGRAM, "111222")
        AppDiagnosticsTracker.recordAuthFailure(AuthChannel.SMS, "+79990001122")

        val snapshot = AppDiagnosticsTracker.metricsSnapshot.value
        assertEquals(2, snapshot.last5Min.tgAuthFailures)
        assertEquals(1, snapshot.last5Min.smsAuthFailures)
    }

    @Test
    fun testClearHistoryResetsAllMetrics() {
        AppDiagnosticsTracker.recordWakeup(WakeupSource.PUSH_FCM)
        AppDiagnosticsTracker.recordAuthFailure(AuthChannel.SMS, "+79991112233")
        AppDiagnosticsTracker.recordConnection(
            serverId = "srv_1",
            serverName = "Server",
            serverType = ServerType.PUSH_GATEWAY,
            outcome = ConnectionOutcome.SUCCESS,
        )

        AppDiagnosticsTracker.clearHistory()

        val snapshot = AppDiagnosticsTracker.metricsSnapshot.value
        assertEquals(0, snapshot.sinceStart.wakeupsTotal)
        assertEquals(0, snapshot.sinceStart.smsAuthFailures)
        assertTrue(snapshot.sinceStart.serverStats.isEmpty())
    }
}
