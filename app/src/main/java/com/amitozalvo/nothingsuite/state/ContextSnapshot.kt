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

/** A titled item shown on the ambient detail pages (event or notification). */
data class TitledItem(
    val title: String,
    /** e.g. an event's start time; null for notifications. */
    val subtitle: String?,
    val titleRaster: Array<BooleanArray>?,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

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
    /** Today's remaining events, for the ambient detail pages (max ~3). */
    val todayEventItems: List<TitledItem> = emptyList(),
    /** Monitored apps' notifications, for the ambient detail pages (max ~3). */
    val notificationItems: List<TitledItem> = emptyList(),
    val ringingAlarm: RingingAlarm? = null,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
