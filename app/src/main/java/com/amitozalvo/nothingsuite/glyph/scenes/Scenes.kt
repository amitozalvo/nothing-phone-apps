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
        set(value) {
            if (value && !field) captureTickBase = true
            field = value
        }
    private var tickBase = 0L
    private var captureTickBase = false

    override fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean {
        val event = snapshot.nextEvent ?: return false
        if (event.allDay || event.end <= snapshot.now) return false
        if (event.isOngoingAt(snapshot.now)) return settings.showOngoingEvent
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
            if (captureTickBase) { tickBase = tick; captureTickBase = false }
            Marquee.draw(buffer, 8, snapshot.nextEventTitleRaster, tick - tickBase, force = true)
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

        // 7 symmetric bars, 2px wide; animated (slower than the 100ms tick)
        val baseline = 11
        val barTick = tick / 4
        for (bar in 0 until 7) {
            val x = 2 + bar * 3
            val h = if (playing) {
                2 + ((barTick * 5 + bar * 11 + (bar * bar)) % 8).toInt()
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

        Marquee.draw(buffer, 13, snapshot.mediaTitleRaster, tick, force = true)
    }

    private companion object {
        val PAUSED_LINGER: Duration = Duration.ofMinutes(5)
        val PLAY_ICON = listOf("100", "110", "111", "110", "100")
    }
}

/**
 * Chosen design: minimal — the time is the hero. Date above, dot clusters
 * below (bright dots = events left today, dim dots = monitored
 * notifications). The Glyph Button pages through detail views, one per
 * event / notification, each showing its scrolling title.
 */
class AmbientScene : Scene {
    override val id = SceneIds.AMBIENT

    /** 0 = time; then one page per event, then one per notification. */
    var page = 0
        private set
    private var pageUntil: java.time.Instant = java.time.Instant.MIN
    private var tickBase = 0L
    private var captureTickBase = false

    override fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean = true

    fun cycle(snapshot: ContextSnapshot) {
        val pages = 1 + snapshot.todayEventItems.size + snapshot.notificationItems.size
        page = (page + 1) % pages
        pageUntil = snapshot.now.plus(PAGE_TIMEOUT)
        captureTickBase = true
    }

    override fun render(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
        tick: Long,
    ) {
        if (page != 0 && snapshot.now.isAfter(pageUntil)) page = 0
        val events = snapshot.todayEventItems
        val notifs = snapshot.notificationItems
        when {
            page == 0 -> renderTime(buffer, snapshot, settings, tick)
            page - 1 < events.size ->
                renderItem(buffer, MatrixIcons.CALENDAR, events[page - 1], tick)
            page - 1 - events.size < notifs.size ->
                renderItem(buffer, MatrixIcons.BELL, notifs[page - 1 - events.size], tick)
            else -> renderTime(buffer, snapshot, settings, tick)
        }
    }

    private fun renderTime(
        buffer: MatrixBuffer,
        snapshot: ContextSnapshot,
        settings: GlyphSettings,
        tick: Long,
    ) {
        val zoned = snapshot.now.atZone(ZoneId.systemDefault())

        // Low-battery hint at top center (inside the circle)
        if (!snapshot.battery.charging &&
            snapshot.battery.percent <= settings.lowBatteryThreshold
        ) {
            buffer.smallText(12, 0, "!", if ((tick / 5) % 2 == 0L) 180 else 80)
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

    private fun renderItem(
        buffer: MatrixBuffer,
        icon: List<String>,
        item: com.amitozalvo.nothingsuite.state.TitledItem,
        tick: Long,
    ) {
        if (captureTickBase) { tickBase = tick; captureTickBase = false }
        buffer.sprite(10, 0, icon, 160)
        Marquee.draw(buffer, 7, item.titleRaster, tick - tickBase, brightness = 255, force = true)
        item.subtitle?.let { buffer.smallTextCentered(18, it, 120) }
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
        val PAGE_TIMEOUT: Duration = Duration.ofSeconds(8)
    }
}
