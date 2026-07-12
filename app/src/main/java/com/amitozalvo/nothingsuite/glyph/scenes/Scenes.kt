package com.amitozalvo.nothingsuite.glyph.scenes

import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.config.SceneIds
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import com.amitozalvo.nothingsuite.glyph.MatrixIcons
import com.amitozalvo.nothingsuite.state.ContextSnapshot
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** Countdown + progress toward the next calendar event. */
class NextEventScene : Scene {
    override val id = SceneIds.NEXT_EVENT

    override fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean {
        val event = snapshot.nextEvent ?: return false
        if (event.allDay || event.end <= snapshot.now) return false
        val untilStart = Duration.between(snapshot.now, event.begin)
        return untilStart <= Duration.ofMinutes(settings.eventLeadMinutes.toLong())
    }

    override fun render(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
        tick: Long,
    ) {
        val event = snapshot.nextEvent ?: return
        val ongoing = event.isOngoingAt(snapshot.now)

        val minutes: Long
        val progress: Float
        if (ongoing) {
            minutes = Duration.between(snapshot.now, event.end).toMinutes().coerceAtLeast(0)
            val total = Duration.between(event.begin, event.end).toMinutes().coerceAtLeast(1)
            progress = 1f - minutes.toFloat() / total
        } else {
            minutes = Duration.between(snapshot.now, event.begin).toMinutes().coerceAtLeast(0)
            val lead = settings.eventLeadMinutes.toLong().coerceAtLeast(1)
            progress = 1f - minutes.toFloat() / lead
        }

        if (minutes < 1 && !ongoing) {
            buffer.smallTextCentered(2, "NOW", 255)
        } else {
            buffer.bigTextCentered(1, minutes.coerceAtMost(99).toString(), 255)
            buffer.smallTextCentered(8, if (ongoing) "LEFT" else "MIN", 120)
        }

        Marquee.draw(buffer, 15, snapshot.nextEventTitleRaster, tick)

        buffer.progressBar(1, 22, buffer.size - 2, 3, progress, 255)
    }
}

/** Alarm icon + time within the configured window before the next alarm. */
class AlarmScene : Scene {
    override val id = SceneIds.ALARM

    override fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean {
        val alarm = snapshot.nextAlarm ?: return false
        val until = Duration.between(snapshot.now, alarm)
        return !until.isNegative &&
            until <= Duration.ofMinutes(settings.alarmWindowMinutes.toLong())
    }

    override fun render(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
        tick: Long,
    ) {
        val alarm = snapshot.nextAlarm ?: return
        val local = alarm.atZone(ZoneId.systemDefault()).toLocalTime()
        buffer.sprite((buffer.size - 7) / 2, 3, MatrixIcons.ALARM, 255)
        buffer.smallTextCentered(13, TIME_FORMAT.format(local), 255)
        val minutes = Duration.between(snapshot.now, alarm).toMinutes()
        buffer.smallTextCentered(20, "IN ${minutes.coerceAtLeast(0)}", 100)
    }
}

/** Equalizer + track title while media is playing. */
class MediaScene : Scene {
    override val id = SceneIds.MEDIA

    override fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean =
        snapshot.media?.playing == true

    override fun render(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
        tick: Long,
    ) {
        // 5 equalizer bars, heights vary deterministically with the tick
        val baseline = 12
        for (bar in 0 until 5) {
            val x = 2 + bar * 5
            val h = 3 + ((tick * 7 + bar * 13) % 8).toInt()
            for (w in 0 until 3) {
                buffer.vLine(x + w, baseline - h, baseline, 200)
            }
        }
        Marquee.draw(buffer, 16, snapshot.mediaTitleRaster, tick)
    }
}

/** Fallback: time, date, today's events + monitored notification counts. */
class AmbientScene : Scene {
    override val id = SceneIds.AMBIENT

    override fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean = true

    override fun render(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
        tick: Long,
    ) {
        val zoned = snapshot.now.atZone(ZoneId.systemDefault())

        val weekday = zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
        buffer.smallTextCentered(1, "$weekday ${zoned.dayOfMonth}", 140)

        buffer.bigTextCentered(8, TIME_FORMAT.format(zoned), 255)

        // Status row: events remaining today; monitored app notifications;
        // battery indicator. Counts capped at one digit so the row fits.
        var x = 0
        buffer.sprite(x, 18, MatrixIcons.CALENDAR, 180)
        x += 6
        x += buffer.smallText(x, 18, count(snapshot.remainingEventsToday), 255) + 2
        if (settings.monitoredApps.isNotEmpty()) {
            buffer.sprite(x, 18, MatrixIcons.BELL, 180)
            x += 6
            x += buffer.smallText(x, 18, count(snapshot.monitoredNotificationCount), 255) + 2
        }
        if (snapshot.battery.charging) {
            buffer.sprite(22, 18, MatrixIcons.LIGHTNING, 200)
        } else if (snapshot.battery.percent <= settings.lowBatteryThreshold) {
            val brightness = if (tick % 2 == 0L) 200 else 90
            buffer.smallText(23, 18, "!", brightness)
        }
    }

    private fun count(n: Int): String = n.coerceAtMost(9).toString()
}
