package com.amitozalvo.nothingsuite

import com.amitozalvo.nothingsuite.calendar.CalendarEvent
import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.config.SceneIds
import com.amitozalvo.nothingsuite.glyph.scenes.OtpToast
import com.amitozalvo.nothingsuite.glyph.scenes.ChargingToast
import com.amitozalvo.nothingsuite.glyph.scenes.SceneEngine
import com.amitozalvo.nothingsuite.state.BatteryInfo
import com.amitozalvo.nothingsuite.state.ContextSnapshot
import com.amitozalvo.nothingsuite.state.MediaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SceneEngineTest {

    private val now: Instant = Instant.parse("2026-07-12T10:00:00Z")

    private fun event(minutesAway: Long, durationMinutes: Long = 60) = CalendarEvent(
        instanceId = 1, eventId = 1, title = "Meeting", location = null,
        begin = now.plusSeconds(minutesAway * 60),
        end = now.plusSeconds((minutesAway + durationMinutes) * 60),
        allDay = false, color = 0,
    )

    @Test
    fun ambientIsFallback() {
        val engine = SceneEngine.createDefault()
        val scene = engine.selectScene(ContextSnapshot(now = now), GlyphSettings())
        assertEquals(SceneIds.AMBIENT, scene.id)
    }

    @Test
    fun nextEventWinsWithinLeadTime() {
        val engine = SceneEngine.createDefault()
        val snapshot = ContextSnapshot(now = now, nextEvent = event(minutesAway = 25))
        assertEquals(SceneIds.NEXT_EVENT, engine.selectScene(snapshot, GlyphSettings()).id)
    }

    @Test
    fun nextEventInactiveBeyondLeadTime() {
        val engine = SceneEngine.createDefault()
        val snapshot = ContextSnapshot(now = now, nextEvent = event(minutesAway = 90))
        assertEquals(SceneIds.AMBIENT, engine.selectScene(snapshot, GlyphSettings()).id)
    }

    @Test
    fun userOrderDecidesBetweenActiveScenes() {
        val engine = SceneEngine.createDefault()
        val snapshot = ContextSnapshot(
            now = now,
            nextEvent = event(minutesAway = 10),
            media = MediaInfo("Track", null, playing = true),
        )
        val mediaFirst = GlyphSettings(
            sceneOrder = listOf(SceneIds.MEDIA, SceneIds.NEXT_EVENT, SceneIds.ALARM, SceneIds.AMBIENT),
        )
        assertEquals(SceneIds.MEDIA, engine.selectScene(snapshot, mediaFirst).id)
        assertEquals(SceneIds.NEXT_EVENT, engine.selectScene(snapshot, GlyphSettings()).id)
    }

    @Test
    fun disabledSceneIsSkipped() {
        val engine = SceneEngine.createDefault()
        val snapshot = ContextSnapshot(now = now, nextEvent = event(minutesAway = 10))
        val settings = GlyphSettings(
            enabledScenes = setOf(SceneIds.ALARM, SceneIds.MEDIA, SceneIds.AMBIENT),
        )
        assertEquals(SceneIds.AMBIENT, engine.selectScene(snapshot, settings).id)
    }

    @Test
    fun toastOutranksScenes() {
        val engine = SceneEngine.createDefault()
        val snapshot = ContextSnapshot(now = now, nextEvent = event(minutesAway = 10))
        engine.postToast(OtpToast("123456", now.plusSeconds(60), null))
        val frame = engine.renderFrame(snapshot, GlyphSettings(), 0)
        assertEquals(625, frame.size)
        assertTrue(engine.currentToast is OtpToast)
    }

    @Test
    fun otpToastNotReplacedByBatteryToast() {
        val engine = SceneEngine.createDefault()
        engine.postToast(OtpToast("123456", now.plusSeconds(60), null))
        engine.postToast(ChargingToast(50, now.plusSeconds(8)))
        assertTrue(engine.currentToast is OtpToast)
    }

    @Test
    fun expiredToastIsCleared() {
        val engine = SceneEngine.createDefault()
        engine.postToast(OtpToast("123456", now.minusSeconds(1), null))
        engine.renderFrame(ContextSnapshot(now = now), GlyphSettings(), 0)
        assertEquals(null, engine.currentToast)
    }

    @Test
    fun buttonDismissesOtp() {
        val engine = SceneEngine.createDefault()
        engine.postToast(OtpToast("123456", now.plusSeconds(60), null))
        assertTrue(engine.dismissToastByButton())
        assertEquals(null, engine.currentToast)
        assertFalse(engine.dismissToastByButton())
    }

    @Test
    fun ongoingEventShownOnlyWhenEnabled() {
        val engine = SceneEngine.createDefault()
        val snapshot = ContextSnapshot(now = now, nextEvent = event(minutesAway = -10))
        assertEquals(
            SceneIds.AMBIENT,
            engine.selectScene(snapshot, GlyphSettings(showOngoingEvent = false)).id,
        )
        assertEquals(
            SceneIds.NEXT_EVENT,
            engine.selectScene(snapshot, GlyphSettings(showOngoingEvent = true)).id,
        )
    }

    @Test
    fun alarmSceneActiveWithinWindow() {
        val engine = SceneEngine.createDefault()
        val snapshot = ContextSnapshot(now = now, nextAlarm = now.plusSeconds(15 * 60))
        assertEquals(SceneIds.ALARM, engine.selectScene(snapshot, GlyphSettings()).id)
    }

    @Test
    fun renderedFramesHaveValidBrightness() {
        val engine = SceneEngine.createDefault()
        val snapshot = ContextSnapshot(
            now = now,
            nextEvent = event(minutesAway = 5),
            battery = BatteryInfo(12, false),
            remainingEventsToday = 4,
        )
        val frame = engine.renderFrame(snapshot, GlyphSettings(), 7)
        assertEquals(625, frame.size)
        assertTrue(frame.all { it in 0..255 })
        assertTrue(frame.any { it > 0 })
    }
}
