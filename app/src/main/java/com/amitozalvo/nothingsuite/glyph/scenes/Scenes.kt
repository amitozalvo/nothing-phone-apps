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

/**
 * Chosen design: circular progress ring hugging the matrix edge that fills
 * as the event approaches (or elapses, when ongoing), big minutes centered.
 * While the Glyph Button is held, the countdown swaps for the scrolling
 * event title.
 */
class NextEventScene : Scene {
    override val id = SceneIds.NEXT_EVENT

    /** Set by the toy service while the Glyph Button is held down. */
    var showTitle = false

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

        buffer.ring(progress, track = 45, fill = 255)

        if (showTitle) {
            Marquee.draw(buffer, 9, snapshot.nextEventTitleRaster, tick, force = true)
            return
        }

        when {
            ongoing -> {
                buffer.bigTextCentered(7, minutes.coerceAtMost(99).toString(), 255)
                buffer.smallTextCentered(15, "LEFT", 110)
            }
            minutes < 1 -> buffer.smallTextCentered(10, "NOW", 255)
            else -> {
                buffer.bigTextCentered(7, minutes.coerceAtMost(99).toString(), 255)
                buffer.smallTextCentered(15, "MIN", 110)
            }
        }
    }
}

/**
 * Chosen design: clock icon + alarm time, with a small progress bar that
 * fills across the wait window.
 */
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
        buffer.sprite((buffer.size - 7) / 2, 2, MatrixIcons.ALARM, 255)
        buffer.smallTextCentered(11, TIME_FORMAT.format(local), 255)

        val window = settings.alarmWindowMinutes.toLong().coerceAtLeast(1)
        val until = Duration.between(snapshot.now, alarm).toMinutes().coerceAtLeast(0)
        val progress = 1f - until.toFloat() / window
        buffer.progressBar(6, 18, 13, 3, progress, 220)
    }
}

/**
 * Chosen design: equalizer bars (animated while playing) over an
 * always-flowing track title. The Glyph Button toggles play/pause.
 */
class MediaScene : Scene {
    override val id = SceneIds.MEDIA

    override fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean {
        val media = snapshot.media ?: return false
        if (media.playing) return true
        // Keep the paused scene around briefly so play can be resumed
        val lastPlaying = media.lastPlayingAt ?: return false
        return Duration.between(lastPlaying, snapshot.now) <= PAUSED_LINGER
    }

    override fun render(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
        tick: Long,
    ) {
        val playing = snapshot.media?.playing == true

        // 7 symmetric bars, 2px wide; animated by tick while playing
        val baseline = 11
        for (bar in 0 until 7) {
            val x = 2 + bar * 3
            val h = if (playing) {
                2 + ((tick * 5 + bar * 11 + (bar * bar)) % 8).toInt()
            } else {
                2
            }
            for (w in 0 until 2) {
                buffer.vLine(x + w, baseline - h, baseline, if (playing) 220 else 90)
            }
        }
        if (!playing) {
            // Play triangle hint centered over the flat bars
            buffer.sprite(11, 3, PLAY_ICON, 200)
        }

        Marquee.draw(buffer, 15, snapshot.mediaTitleRaster, tick, force = true)
    }

    private companion object {
        val PAUSED_LINGER: Duration = Duration.ofMinutes(5)
        val PLAY_ICON = listOf("100", "110", "111", "110", "100")
    }
}

/**
 * Chosen design: minimal — the time is the hero. Date above, dot clusters
 * below (bright dots = events left today, dim dots = monitored
 * notifications). The Glyph Button cycles detail views: events → alerts.
 */
class AmbientScene : Scene {
    override val id = SceneIds.AMBIENT

    /** 0 = time, 1 = events detail, 2 = notifications detail. */
    var subView = 0
    var subViewUntil: java.time.Instant = java.time.Instant.MIN

    override fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean = true

    fun cycle(now: java.time.Instant, settings: GlyphSettings) {
        val views = if (settings.monitoredApps.isEmpty()) 2 else 3
        subView = (subView + 1) % views
        subViewUntil = now.plus(SUBVIEW_TIMEOUT)
    }

    override fun render(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
        tick: Long,
    ) {
        if (subView != 0 && snapshot.now.isAfter(subViewUntil)) subView = 0
        when (subView) {
            1 -> renderEvents(buffer, snapshot)
            2 -> renderNotifications(buffer, snapshot)
            else -> renderTime(buffer, snapshot, settings)
        }
    }

    private fun renderTime(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
    ) {
        val zoned = snapshot.now.atZone(ZoneId.systemDefault())

        // Charging / low-battery indicator at top center (inside the circle)
        if (snapshot.battery.charging) {
            buffer.sprite(11, 0, MatrixIcons.LIGHTNING, 140)
        } else if (snapshot.battery.percent <= settings.lowBatteryThreshold) {
            buffer.smallText(12, 0, "!", 180)
        }

        // Weekday + day, tight 1px gap so it fits the circle at this row
        val weekday = zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
        val date = "$weekday ${zoned.dayOfMonth}"
        var x = (buffer.size - tightWidth(date)) / 2
        for (c in date) {
            if (c == ' ') { x += 2; continue }
            x += buffer.smallText(x, 6, c.toString(), 110) + 1
        }

        buffer.bigTextCentered(12, TIME_FORMAT.format(zoned), 255)

        // Dot clusters: events (bright) then notifications (dim)
        val events = snapshot.remainingEventsToday.coerceAtMost(5)
        val notifs = if (settings.monitoredApps.isEmpty()) {
            0
        } else {
            snapshot.monitoredNotificationCount.coerceAtMost(5)
        }
        val width = clusterWidth(events) +
            (if (events > 0 && notifs > 0) 3 else 0) + clusterWidth(notifs)
        var dx = (buffer.size - width) / 2
        dx = drawCluster(buffer, dx, 20, events, 255)
        if (events > 0 && notifs > 0) dx += 3
        drawCluster(buffer, dx, 20, notifs, 100)
    }

    private fun renderEvents(buffer: MatrixBuffer, snapshot: ContextSnapshot) {
        buffer.sprite(10, 3, MatrixIcons.CALENDAR, 160)
        buffer.bigTextCentered(10, snapshot.remainingEventsToday.coerceAtMost(9).toString(), 255)
        val next = snapshot.nextEvent
        if (next != null && !next.allDay) {
            val time = TIME_FORMAT.format(next.begin.atZone(ZoneId.systemDefault()))
            buffer.smallTextCentered(18, time, 120)
        }
    }

    private fun renderNotifications(buffer: MatrixBuffer, snapshot: ContextSnapshot) {
        buffer.sprite(10, 3, MatrixIcons.BELL, 160)
        buffer.bigTextCentered(10, snapshot.monitoredNotificationCount.coerceAtMost(9).toString(), 255)
    }

    private fun tightWidth(text: String): Int =
        text.sumOf { c ->
            if (c == ' ') 2 else com.amitozalvo.nothingsuite.glyph.DotFont.smallGlyph(c)[0].length + 1
        } - 1

    private fun clusterWidth(count: Int): Int = if (count <= 0) 0 else count * 3 - 1

    private fun drawCluster(buffer: MatrixBuffer, x: Int, y: Int, count: Int, brightness: Int): Int {
        var cx = x
        repeat(count) {
            buffer.rect(cx, y, 2, 2, brightness, fill = true)
            cx += 3
        }
        return cx - if (count > 0) 1 else 0
    }

    private companion object {
        val SUBVIEW_TIMEOUT: Duration = Duration.ofSeconds(6)
    }
}
