package com.amitozalvo.nothingsuite.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitozalvo.nothingsuite.calendar.CalendarEvent
import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import com.amitozalvo.nothingsuite.glyph.TextRaster
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
import java.time.Instant

/** Sample frames of every scene/alert, rendered by the real engine. */
@Composable
fun ScenePreviews(settings: GlyphSettings) {
    val previews = remember(settings.monitoredApps, settings.lowBatteryThreshold) {
        buildPreviews(settings)
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        previews.forEach { (label, frame) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MatrixPreview(frame = frame, modifier = Modifier.size(140.dp))
                Text(label, color = Color(0xFF8A8A8A), fontSize = 11.sp)
            }
        }
    }
}

private fun buildPreviews(settings: GlyphSettings): List<Pair<String, IntArray>> {
    val now = Instant.now()
    val sampleEvent = CalendarEvent(
        instanceId = 1, eventId = 1,
        title = "Design sync",
        location = null,
        begin = now.plusSeconds(25 * 60),
        end = now.plusSeconds(85 * 60),
        allDay = false,
        color = 0,
    )
    val snapshot = ContextSnapshot(
        now = now,
        nextEvent = sampleEvent,
        nextEventTitleRaster = TextRaster.rasterize(sampleEvent.title),
        remainingEventsToday = 3,
        nextAlarm = now.plusSeconds(20 * 60),
        media = MediaInfo(title = "Song title", artist = "Artist", playing = true),
        mediaTitleRaster = TextRaster.rasterize("Song title - Artist"),
        battery = BatteryInfo(64, false),
        monitoredNotificationCount = 2,
    )
    val cfg = settings.copy(
        monitoredApps = settings.monitoredApps.ifEmpty { setOf("preview") },
    )

    fun render(block: (MatrixBuffer) -> Unit): IntArray {
        val buffer = MatrixBuffer()
        block(buffer)
        return buffer.snapshot()
    }

    return listOf(
        "Next event" to render { NextEventScene().render(it, snapshot, cfg, 0) },
        "Alarm" to render { AlarmScene().render(it, snapshot, cfg, 0) },
        "Now playing" to render { MediaScene().render(it, snapshot, cfg, 3) },
        "Ambient" to render { AmbientScene().render(it, snapshot, cfg, 0) },
        "OTP" to render { OtpToast("482913", now.plusSeconds(60), null).render(it, 0) },
        "Charging" to render { ChargingToast(64, now.plusSeconds(8)).render(it, 0) },
        "Low battery" to render { LowBatteryToast(15, now.plusSeconds(8)).render(it, 0) },
    )
}
