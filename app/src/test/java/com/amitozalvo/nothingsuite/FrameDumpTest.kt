package com.amitozalvo.nothingsuite

import com.amitozalvo.nothingsuite.calendar.CalendarEvent
import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import com.amitozalvo.nothingsuite.glyph.scenes.AlarmScene
import com.amitozalvo.nothingsuite.glyph.scenes.AmbientScene
import com.amitozalvo.nothingsuite.glyph.scenes.ChargingToast
import com.amitozalvo.nothingsuite.glyph.scenes.LowBatteryToast
import com.amitozalvo.nothingsuite.glyph.scenes.MediaScene
import com.amitozalvo.nothingsuite.glyph.scenes.NextEventScene
import com.amitozalvo.nothingsuite.glyph.scenes.OtpToast
import com.amitozalvo.nothingsuite.state.BatteryInfo
import com.amitozalvo.nothingsuite.state.ContextSnapshot
import com.amitozalvo.nothingsuite.state.MediaInfo
import org.junit.Test
import java.time.Instant

/** Dumps every scene as ASCII art for visual layout verification. */
class FrameDumpTest {

    @Test
    fun dumpAllScenes() {
        val now = Instant.parse("2026-07-12T11:47:00Z")
        val event = CalendarEvent(
            1, 1, "Design sync", null,
            now.plusSeconds(25 * 60), now.plusSeconds(85 * 60), false, 0,
        )
        val snapshot = ContextSnapshot(
            now = now,
            nextEvent = event,
            remainingEventsToday = 3,
            nextAlarm = now.plusSeconds(18 * 60),
            media = MediaInfo("Track", "Artist", true),
            battery = BatteryInfo(12, charging = true),
            monitoredNotificationCount = 12,
        )
        val cfg = GlyphSettings(monitoredApps = setOf("x"))

        fun dump(name: String, block: (MatrixBuffer) -> Unit) {
            val b = MatrixBuffer()
            block(b)
            println("=== $name ===")
            for (y in 0 until b.size) {
                println((0 until b.size).joinToString("") { x ->
                    when {
                        b.get(x, y) > 180 -> "█"
                        b.get(x, y) > 90 -> "▓"
                        b.get(x, y) > 0 -> "░"
                        else -> "·"
                    }
                })
            }
        }

        dump("AMBIENT charging") { AmbientScene().render(it, snapshot, cfg, 0) }
        dump("AMBIENT low-batt") {
            AmbientScene().render(
                it, snapshot.copy(battery = BatteryInfo(10, false)), cfg, 0,
            )
        }
        dump("NEXT EVENT 25min") { NextEventScene().render(it, snapshot, cfg, 0) }
        dump("NEXT EVENT ongoing") {
            NextEventScene().render(
                it, snapshot.copy(nextEvent = event.copy(begin = now.minusSeconds(600))), cfg, 0,
            )
        }
        dump("ALARM") { AlarmScene().render(it, snapshot, cfg, 0) }
        dump("MEDIA") { MediaScene().render(it, snapshot, cfg, 2) }
        dump("OTP 6-digit") { OtpToast("482913", now.plusSeconds(60), null).render(it, 0) }
        dump("OTP 4-digit") { OtpToast("4829", now.plusSeconds(60), null).render(it, 0) }
        dump("CHARGING 64") { ChargingToast(64, now.plusSeconds(8)).render(it, 0) }
        dump("LOW BATT 15") { LowBatteryToast(15, now.plusSeconds(8)).render(it, 0) }
    }
}
