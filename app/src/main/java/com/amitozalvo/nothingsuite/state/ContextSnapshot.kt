package com.amitozalvo.nothingsuite.state

import com.amitozalvo.nothingsuite.calendar.CalendarEvent
import java.time.Instant

data class OtpMessage(
    val code: String,
    val sourcePackage: String,
    val postedAt: Instant,
    /** Notification key, so dismissal on the phone clears the toast. */
    val notificationKey: String?,
)

data class MediaInfo(
    val title: String,
    val artist: String?,
    val playing: Boolean,
    /** Last moment playback was observed; keeps the scene up briefly after pause. */
    val lastPlayingAt: Instant? = null,
)

/** A currently firing alarm (detected via its full-screen notification). */
data class RingingAlarm(
    val notificationKey: String,
    val label: String?,
)

data class BatteryInfo(
    val percent: Int,
    val charging: Boolean,
)

/** Everything the scene engine needs to decide + render one frame. */
data class ContextSnapshot(
    val now: Instant,
    val nextEvent: CalendarEvent? = null,
    /** Pre-rasterized event title (unicode-safe), for the marquee. */
    val nextEventTitleRaster: Array<BooleanArray>? = null,
    val remainingEventsToday: Int = 0,
    val nextAlarm: Instant? = null,
    val media: MediaInfo? = null,
    val mediaTitleRaster: Array<BooleanArray>? = null,
    val battery: BatteryInfo = BatteryInfo(100, false),
    /** Total notification count across the user's monitored apps. */
    val monitoredNotificationCount: Int = 0,
    val ringingAlarm: RingingAlarm? = null,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
