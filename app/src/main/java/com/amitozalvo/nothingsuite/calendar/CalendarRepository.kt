package com.amitozalvo.nothingsuite.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarEvent(
    val instanceId: Long,
    val eventId: Long,
    val title: String,
    val location: String?,
    val begin: Instant,
    val end: Instant,
    val allDay: Boolean,
    val color: Int,
) {
    fun isOngoingAt(now: Instant): Boolean = !allDay && now >= begin && now < end

    fun beginDate(zone: ZoneId): LocalDate =
        if (allDay) {
            // All-day instances are stored in UTC by the provider
            begin.atZone(ZoneId.of("UTC")).toLocalDate()
        } else {
            begin.atZone(zone).toLocalDate()
        }
}

object CalendarRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Upcoming event instances from all visible calendars, expanded from the
     * Instances table (correct recurrence handling), declined events excluded.
     */
    fun upcomingEvents(
        context: Context,
        from: Instant = Instant.now(),
        window: Duration = Duration.ofDays(14),
        limit: Int = 60,
    ): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()

        val begin = from.toEpochMilli()
        val end = from.plus(window).toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, begin)
            ContentUris.appendId(this, end)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
        )

        val selection = "${CalendarContract.Instances.VISIBLE} = 1"

        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val idxId = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID)
            val idxEventId = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val idxTitle = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val idxLocation = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val idxBegin = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val idxEnd = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val idxAllDay = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val idxColor = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)
            val idxSelfStatus =
                cursor.getColumnIndexOrThrow(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
            val idxStatus = cursor.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)

            while (cursor.moveToNext()) {
                if (cursor.getInt(idxSelfStatus) ==
                    CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                ) continue
                if (cursor.getInt(idxStatus) == CalendarContract.Events.STATUS_CANCELED) continue

                events += CalendarEvent(
                    instanceId = cursor.getLong(idxId),
                    eventId = cursor.getLong(idxEventId),
                    title = cursor.getString(idxTitle)?.takeIf { it.isNotBlank() } ?: "(untitled)",
                    location = cursor.getString(idxLocation)?.takeIf { it.isNotBlank() },
                    begin = Instant.ofEpochMilli(cursor.getLong(idxBegin)),
                    end = Instant.ofEpochMilli(cursor.getLong(idxEnd)),
                    allDay = cursor.getInt(idxAllDay) == 1,
                    color = cursor.getInt(idxColor),
                )
            }
        }

        return events
            .sortedWith(compareBy({ !it.isOngoingAt(from) }, { it.begin.toEpochMilli() }))
            .take(limit)
    }

    /** The next timed (non-all-day) event that hasn't ended yet. */
    fun nextTimedEvent(context: Context, now: Instant = Instant.now()): CalendarEvent? =
        upcomingEvents(context, from = now, window = Duration.ofDays(2), limit = 60)
            .firstOrNull { !it.allDay && it.end > now }

    /** Count of timed events that still start (or are ongoing) today. */
    fun remainingEventsToday(context: Context, now: Instant = Instant.now()): Int {
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        return upcomingEvents(context, from = now, window = Duration.ofDays(1), limit = 60)
            .count { !it.allDay && it.beginDate(zone) == today && it.end > now }
    }
}
