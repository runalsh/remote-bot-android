package com.remotebot.domain

import com.remotebot.domain.model.FileItem
import com.remotebot.domain.model.InstalledApp
import com.remotebot.domain.model.LocationData
import com.remotebot.domain.model.RunningProcess
import com.remotebot.domain.model.ScheduledTimer
import com.remotebot.domain.model.ScriptExecutionResult
import com.remotebot.domain.model.ShakeDetectorConfig
import com.remotebot.domain.model.SystemTriggerRule
import com.remotebot.domain.model.SystemTriggerType
import com.remotebot.domain.model.TaskerTask
import com.remotebot.domain.model.UnsentFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationFilesAndAutomationTest {
    @Test
    fun testLocationDataModel() {
        val loc =
            LocationData(
                latitude = 55.7558,
                longitude = 37.6173,
                accuracyMeters = 5.0f,
                altitudeMeters = 150.0,
                speedMps = 1.2f,
                address = "Moscow, Red Square",
                provider = "gps",
                timestampMs = 1700000000000L,
            )
        assertEquals(55.7558, loc.latitude)
        assertEquals(37.6173, loc.longitude)
        assertEquals(5.0f, loc.accuracyMeters)
        assertEquals("Moscow, Red Square", loc.address)
        assertEquals("gps", loc.provider)
        assertFalse(loc.isDirectGps)
        assertEquals("", loc.determinationMethod)
    }

    @Test
    fun testLocationDataWithSignalAndTracking() {
        val loc =
            LocationData(
                latitude = 55.7558,
                longitude = 37.6173,
                accuracyMeters = 3.5f,
                altitudeMeters = 150.0,
                speedMps = 0.0f,
                address = "Moscow",
                provider = "gps",
                timestampMs = 1700000000000L,
                isDirectGps = true,
                determinationMethod = "🛰️ Спутниковая навигация (GPS/ГЛОНАСС)",
                satellitesUsed = 8,
                satellitesVisible = 14,
                signalStrengthLevel = "Отличный (уверенный прием)",
                cn0DbHz = 38.5f,
                trackingSequenceNumber = 5L,
            )
        assertTrue(loc.isDirectGps)
        assertEquals(8, loc.satellitesUsed)
        assertEquals(14, loc.satellitesVisible)
        assertEquals("Отличный (уверенный прием)", loc.signalStrengthLevel)
        assertEquals(38.5f, loc.cn0DbHz)
        assertEquals(5L, loc.trackingSequenceNumber)
    }

    @Test
    fun testLiveTrackingStatusModel() {
        val status =
            com.remotebot.domain.model.LiveTrackingStatus(
                isActive = true,
                intervalSeconds = 30L,
                startedAtMs = 1000L,
                pointsSent = 3,
            )
        assertTrue(status.isActive)
        assertEquals(30L, status.intervalSeconds)
        assertEquals(3, status.pointsSent)
    }

    @Test
    fun testShakeDetectorConfig() {
        val config = ShakeDetectorConfig(isEnabled = true, sensitivity = 15.0f)
        assertTrue(config.isEnabled)
        assertEquals(15.0f, config.sensitivity)
    }

    @Test
    fun testFileItemModel() {
        val item =
            FileItem(
                name = "document.pdf",
                path = "/sdcard/Documents/document.pdf",
                isDirectory = false,
                sizeBytes = 1048576L,
                lastModifiedMs = 1700000000000L,
                canRead = true,
                canWrite = true,
            )
        assertEquals("document.pdf", item.name)
        assertFalse(item.isDirectory)
        assertEquals(1048576L, item.sizeBytes)
        assertTrue(item.canRead)
    }

    @Test
    fun testInstalledAppModel() {
        val app =
            InstalledApp(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                versionName = "2.24.1.0",
                versionCode = 123456L,
                isSystemApp = false,
                isEnabled = true,
            )
        assertEquals("com.whatsapp", app.packageName)
        assertEquals("WhatsApp", app.appName)
        assertFalse(app.isSystemApp)
        assertTrue(app.isEnabled)
    }

    @Test
    fun testRunningProcessModel() {
        val proc =
            RunningProcess(
                pid = 1234,
                processName = "com.google.android.gms",
                packageName = "com.google.android.gms",
                memoryUsageMb = 45.5,
                cpuUsagePercent = 2.3,
            )
        assertEquals(1234, proc.pid)
        assertEquals(45.5, proc.memoryUsageMb)
        assertEquals(2.3, proc.cpuUsagePercent)
    }

    @Test
    fun testUnsentFileModel() {
        val file =
            UnsentFile(
                id = "uf_1",
                filePath = "/tmp/photo_123.jpg",
                destinationChatId = 123456789L,
                fileType = "photo",
                caption = "Photo alert",
                attempts = 1,
            )
        assertEquals("uf_1", file.id)
        assertEquals("/tmp/photo_123.jpg", file.filePath)
        assertEquals(123456789L, file.destinationChatId)
    }

    @Test
    fun testTaskerTaskModel() {
        val task =
            TaskerTask(
                taskName = "TurnOffWifi",
                param1 = "arg1",
                param2 = "arg2",
            )
        assertEquals("TurnOffWifi", task.taskName)
        assertEquals("arg1", task.param1)
        assertEquals("arg2", task.param2)
    }

    @Test
    fun testScheduledTimerModel() {
        val timer =
            ScheduledTimer(
                id = "t_101",
                commandText = "/photo front",
                triggerAtMs = 1700000060000L,
                intervalMs = 60000L,
                isEnabled = true,
            )
        assertEquals("t_101", timer.id)
        assertEquals("/photo front", timer.commandText)
        assertEquals(60000L, timer.intervalMs)
        assertTrue(timer.isEnabled)
    }

    @Test
    fun testSystemTriggerRuleModel() {
        val rule =
            SystemTriggerRule(
                id = "trig_1",
                type = SystemTriggerType.POWER_CONNECTED,
                actionCommand = "/photo front",
                isEnabled = true,
            )
        assertEquals("trig_1", rule.id)
        assertEquals(SystemTriggerType.POWER_CONNECTED, rule.type)
        assertTrue(rule.isEnabled)
    }

    @Test
    fun testScriptExecutionResultModel() {
        val result =
            ScriptExecutionResult(
                isSuccess = true,
                output = "Result: OK",
                executionTimeMs = 42L,
            )
        assertTrue(result.isSuccess)
        assertEquals("Result: OK", result.output)
        assertEquals(42L, result.executionTimeMs)
    }

    @Test
    fun testDownloadSizeLimitValidation() {
        val maxAllowedBytes = 2L * 1024 * 1024 * 1024 // 2 GB
        val validFileSize = 100L * 1024 * 1024 // 100 MB
        val oversize = 2L * 1024 * 1024 * 1024 + 1000L

        assertTrue(validFileSize <= maxAllowedBytes)
        assertTrue(oversize > maxAllowedBytes)
        assertEquals(2147483648L, maxAllowedBytes)
    }

    @Test
    fun testDiskSpaceMarginValidation() {
        val minSafetyMargin = 100L * 1024 * 1024 // 100 MB
        val usableDiskSpace = 500L * 1024 * 1024 // 500 MB
        val fileSize = 350L * 1024 * 1024 // 350 MB

        val hasEnoughSpace = (usableDiskSpace - fileSize) >= minSafetyMargin
        assertTrue(hasEnoughSpace)

        val largeFileSize = 450L * 1024 * 1024 // 450 MB (leaves only 50 MB < 100 MB margin)
        val hasEnoughSpaceForLarge = (usableDiskSpace - largeFileSize) >= minSafetyMargin
        assertFalse(hasEnoughSpaceForLarge)
    }
}
