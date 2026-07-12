package com.amitozalvo.nothingsuite

import com.amitozalvo.nothingsuite.calendar.CalendarEvent
import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import com.amitozalvo.nothingsuite.glyph.PixelFont
import com.amitozalvo.nothingsuite.glyph.TitleGraphic
import com.amitozalvo.nothingsuite.glyph.scenes.AlarmRingingToast
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
import com.amitozalvo.nothingsuite.state.TitledItem
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
            media = MediaInfo("Track", "Artist", true, now),
            battery = BatteryInfo(12, charging = true),
            monitoredNotificationCount = 2,
        )
        val cfg = GlyphSettings(monitoredApps = setOf("x"), showOngoingEvent = true)
        val snapshotWithItems = snapshot.copy(
            todayEventItems = listOf(
                TitledItem(
                    "Design sync", "12:12",
                    TitleGraphic(PixelFont.rasterizeOrNull("Design sync")!!, rtl = false),
                ),
            ),
            notificationItems = listOf(
                TitledItem(
                    "פגישה", null,
                    TitleGraphic(PixelFont.rasterizeOrNull("פגישה")!!, rtl = true),
                ),
            ),
        )

        fun dump(name: String, block: (MatrixBuffer) -> Unit) {
            val b = MatrixBuffer()
            block(b)
            b.maskCircle()
            println("=== $name ===")
            for (y in 0 until b.size) {
                println((0 until b.size).joinToString("") { x ->
                    when {
                        !MatrixBuffer.inCircle(x, y) -> " "
                        b.get(x, y) > 180 -> "█"
                        b.get(x, y) > 90 -> "▓"
                        b.get(x, y) > 0 -> "░"
                        else -> "·"
                    }
                })
            }
        }

        val ambient = AmbientScene()
        dump("AMBIENT time+charging") { ambient.render(it, snapshot, cfg, 0) }
        dump("AMBIENT low-batt") {
            ambient.render(
                it, snapshot.copy(battery = BatteryInfo(10, false)), cfg, 0,
            )
        }
        dump("AMBIENT events view") {
            ambient.cycle(snapshotWithItems)
            ambient.render(it, snapshotWithItems, cfg, 0)
        }
        dump("AMBIENT notifs view") {
            ambient.cycle(snapshotWithItems)
            ambient.render(it, snapshotWithItems, cfg, 0)
        }
        ambient.cycle(snapshotWithItems) // back to time view

        val nextEvent = NextEventScene()
        dump("NEXT EVENT 25min ring") { nextEvent.render(it, snapshot, cfg, 0) }
        dump("NEXT EVENT ongoing") {
            nextEvent.render(
                it, snapshot.copy(nextEvent = event.copy(begin = now.minusSeconds(600))), cfg, 0,
            )
        }
        dump("ALARM in 18") { AlarmScene().render(it, snapshot, cfg, 0) }
        dump("ALARM RINGING") { AlarmRingingToast("key").render(it, 0) }
        dump("MEDIA playing") { MediaScene().render(it, snapshot, cfg, 2) }
        dump("MEDIA paused") {
            MediaScene().render(
                it, snapshot.copy(media = MediaInfo("Track", "Artist", false, now)), cfg, 0,
            )
        }
        dump("OTP 6-digit split") { OtpToast("482913", now.plusSeconds(60), null).render(it, 0) }
        dump("OTP 4-digit") { OtpToast("4829", now.plusSeconds(60), null).render(it, 0) }
        dump("CHARGING 64") { ChargingToast(64, now.plusSeconds(8)).render(it, 0) }
        dump("LOW BATT 15") { LowBatteryToast(15, now.plusSeconds(8)).render(it, 0) }
    }
}
