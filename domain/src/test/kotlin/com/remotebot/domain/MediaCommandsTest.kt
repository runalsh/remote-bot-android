package com.remotebot.domain

import com.remotebot.domain.model.AudioRecordingParams
import com.remotebot.domain.model.AudioStreamType
import com.remotebot.domain.model.CameraLens
import com.remotebot.domain.model.CommandResult
import com.remotebot.domain.model.FlashMode
import com.remotebot.domain.model.PhotoParams
import com.remotebot.domain.model.RingerMode
import com.remotebot.domain.model.ScreenRecordParams
import com.remotebot.domain.model.VideoParams
import com.remotebot.domain.model.VolumeInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaCommandsTest {
    @Test
    fun testPhotoParamsDefaults() {
        val params = PhotoParams()
        assertEquals(CameraLens.BACK, params.lens)
        assertEquals(FlashMode.OFF, params.flash)
        assertEquals(0, params.rotationDegrees)
    }

    @Test
    fun testCustomPhotoParams() {
        val params =
            PhotoParams(
                lens = CameraLens.FRONT,
                flash = FlashMode.ON,
                rotationDegrees = 180,
            )
        assertEquals(CameraLens.FRONT, params.lens)
        assertEquals(FlashMode.ON, params.flash)
        assertEquals(180, params.rotationDegrees)
    }

    @Test
    fun testVideoParams() {
        val params = VideoParams(lens = CameraLens.FRONT, durationSeconds = 30)
        assertEquals(CameraLens.FRONT, params.lens)
        assertEquals(30, params.durationSeconds)
    }

    @Test
    fun testAudioRecordingParams() {
        val params = AudioRecordingParams(durationSeconds = 60)
        assertEquals(60, params.durationSeconds)
    }

    @Test
    fun testScreenRecordParams() {
        val params = ScreenRecordParams(durationSeconds = 15)
        assertEquals(15, params.durationSeconds)
    }

    @Test
    fun testVolumeInfo() {
        val info = VolumeInfo(stream = AudioStreamType.MEDIA, currentVolume = 10, maxVolume = 15)
        assertEquals(AudioStreamType.MEDIA, info.stream)
        assertEquals(10, info.currentVolume)
        assertEquals(15, info.maxVolume)
    }

    @Test
    fun testRingerModes() {
        val modes = RingerMode.entries
        assertTrue(modes.contains(RingerMode.NORMAL))
        assertTrue(modes.contains(RingerMode.SILENT))
        assertTrue(modes.contains(RingerMode.VIBRATE))
    }

    @Test
    fun testMultimediaCommandResults() {
        val photoResult = CommandResult.Photo("/tmp/photo.jpg", caption = "Photo Caption", autoDelete = true)
        assertEquals("/tmp/photo.jpg", photoResult.filePath)
        assertEquals("Photo Caption", photoResult.caption)
        assertTrue(photoResult.autoDelete)

        val videoResult = CommandResult.Video("/tmp/video.mp4", caption = "Video Caption", durationSeconds = 10)
        assertEquals("/tmp/video.mp4", videoResult.filePath)
        assertEquals(10, videoResult.durationSeconds)

        val voiceResult = CommandResult.Voice("/tmp/voice.m4a", durationSeconds = 5)
        assertEquals("/tmp/voice.m4a", voiceResult.filePath)
        assertEquals(5, voiceResult.durationSeconds)

        val mediaGroup = CommandResult.MediaGroup(listOf("/tmp/1.jpg", "/tmp/2.jpg"), caption = "Group")
        assertEquals(2, mediaGroup.filePaths.size)
        assertEquals("Group", mediaGroup.caption)
    }

    @Test
    fun testSendAliasesRecognition() {
        val sendCommands =
            listOf(
                "/photo_send",
                "/videorec_send",
                "/rec_send",
                "/screenshot_send",
                "/screen_rec_send",
            )
        for (cmd in sendCommands) {
            val normalized = cmd.removePrefix("/").lowercase()
            assertTrue(normalized.endsWith("_send"))
        }
    }
}
