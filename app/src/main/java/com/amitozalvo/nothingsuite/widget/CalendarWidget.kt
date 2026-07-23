package com.amitozalvo.nothingsuite.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.amitozalvo.nothingsuite.calendar.CalendarEvent
import com.amitozalvo.nothingsuite.calendar.CalendarRepository
import com.amitozalvo.nothingsuite.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val BLACK = Color(0xF2000000)
private val WHITE = Color(0xFFF2F2F2)
private val GREY = Color(0xFF8A8A8A)
private val RED = Color(0xFFD71921)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

class CalendarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings =
            com.amitozalvo.nothingsuite.config.SettingsRepository.get(context).current()
        provideContent {
            val hasPermission = CalendarRepository.hasPermission(context)
            val now = Instant.now()
            val events = if (hasPermission) {
                // With past events shown, the list covers today from midnight
                val from = if (settings.showPastEventsToday) {
                    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                } else {
                    now
                }
                CalendarRepository.upcomingEvents(
                    context, from = from,
                    calendarIds = settings.selectedCalendarIds,
                ).filter { settings.showPastEventsToday || it.end > now }
            } else {
                emptyList()
            }
            WidgetContent(context, hasPermission, events, settings.eventLeadMinutes)
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    hasPermission: Boolean,
    events: List<CalendarEvent>,
    leadMinutes: Int,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BLACK)
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(openCalendarAppIntent(androidx.glance.LocalContext.current))),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(context)
            Spacer(modifier = GlanceModifier.height(8.dp))
            when {
                !hasPermission -> PermissionHint()
                events.isEmpty() -> EmptyState()
                else -> EventList(events, leadMinutes)
            }
        }
    }
}

@Composable
private fun Header(context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(DateHeaderRenderer.render(context)),
            contentDescription = LocalDate.now().toString(),
            modifier = GlanceModifier.height(22.dp)
                .clickable(actionStartActivity(openCalendarAppIntent(androidx.glance.LocalContext.current))),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(Color(0xFF1A1A1A))
                .cornerRadius(14.dp)
                .clickable(actionStartActivity(newEventIntent(androidx.glance.LocalContext.current))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(com.amitozalvo.nothingsuite.R.drawable.ic_add),
                contentDescription = "New event",
                modifier = GlanceModifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EventList(events: List<CalendarEvent>, leadMinutes: Int) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val rows = buildRows(events, today, zone)

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rows) { row ->
            when (row) {
                is WidgetRow.DayHeader -> DaySeparator(row.label, row.isToday)
                is WidgetRow.Event -> EventRow(row.event, zone, leadMinutes)
            }
        }
        // Trailing filler so taps below the last event open the calendar
        item {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clickable(actionStartActivity(openCalendarAppIntent(androidx.glance.LocalContext.current))),
            ) {}
        }
    }
}

private sealed interface WidgetRow {
    data class DayHeader(val label: String, val isToday: Boolean = false) : WidgetRow
    data class Event(val event: CalendarEvent) : WidgetRow
}

private fun buildRows(
    events: List<CalendarEvent>,
    today: LocalDate,
    zone: ZoneId,
): List<WidgetRow> {
    val rows = mutableListOf<WidgetRow>()
    var lastDate: LocalDate? = null
    for (event in events) {
        val date = event.beginDate(zone)
        if (date != lastDate) {
            if (date == today) {
                rows += WidgetRow.DayHeader("TODAY", isToday = true)
            } else {
                rows += WidgetRow.DayHeader(dayLabel(date, today))
            }
            lastDate = date
        }
        rows += WidgetRow.Event(event)
    }
    return rows
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today.plusDays(1) -> "TOMORROW"
    else -> {
        val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
        "${weekday.uppercase(Locale.getDefault())} ${date.dayOfMonth}"
    }
}

@Composable
private fun DaySeparator(label: String, isToday: Boolean = false) {
    Column(
        modifier = GlanceModifier.fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
            .clickable(actionStartActivity(openCalendarAppIntent(androidx.glance.LocalContext.current))),
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(if (isToday) RED else GREY),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun EventRow(event: CalendarEvent, zone: ZoneId, leadMinutes: Int) {
    val now = Instant.now()
    val ongoing = event.isOngoingAt(now)
    val past = !event.allDay && event.end <= now
    val minutesUntil = java.time.Duration.between(now, event.begin).toMinutes()
    // "SOON" not minutes: widget refreshes aren't minute-accurate, and a
    // stale number lies while SOON stays true. The Glyph ring has the
    // live countdown.
    val marker = when {
        ongoing -> "NOW"
        !event.allDay && minutesUntil in 0..leadMinutes.toLong() -> "SOON"
        else -> null
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(actionStartActivity(viewEventIntent(event))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val barColor = Color(event.color or 0xFF000000.toInt())
        if (event.pending) {
            // Dotted accent bar: invitation not accepted yet
            Column {
                repeat(3) { i ->
                    if (i > 0) Spacer(modifier = GlanceModifier.height(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .width(3.dp)
                            .height(6.dp)
                            .cornerRadius(2.dp)
                            .background(barColor),
                    ) {}
                }
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(30.dp)
                    .cornerRadius(2.dp)
                    .background(barColor),
            ) {}
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.title,
                    maxLines = 1,
                    // Pending invitations and finished events render dimmer
                    style = TextStyle(
                        color = ColorProvider(if (event.pending || past) GREY else WHITE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (marker != null) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = marker,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(RED),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            val subtitle = if (event.allDay) {
                "ALL DAY"
            } else {
                val begin = TIME_FORMAT.format(event.begin.atZone(zone))
                val end = TIME_FORMAT.format(event.end.atZone(zone))
                listOfNotNull("$begin – $end", event.location).joinToString("  ·  ")
            }
            Text(
                text = subtitle,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(if (past) Color(0xFF555555) else GREY),
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .clickable(actionStartActivity(openCalendarAppIntent(androidx.glance.LocalContext.current))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No upcoming events",
            style = TextStyle(color = ColorProvider(GREY), fontSize = 12.sp),
        )
    }
}

@Composable
private fun PermissionHint() {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .clickable(androidx.glance.action.actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Tap to grant calendar access",
            style = TextStyle(color = ColorProvider(RED), fontSize = 12.sp),
        )
    }
}

private const val OUTLOOK_PACKAGE = "com.microsoft.office.outlook"

/**
 * Open the user's calendar app. Tries the canonical calendar time URI
 * (Google Calendar / AOSP), then the system calendar-app selector, then
 * Outlook — so taps work whichever calendar app the user lives in.
 */
private fun openCalendarAppIntent(context: Context): Intent {
    val timeIntent = Intent(Intent.ACTION_VIEW).apply {
        data = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val pm = context.packageManager
    if (pm.resolveActivity(timeIntent, 0) != null) return timeIntent

    val selector = Intent.makeMainSelectorActivity(
        Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR,
    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    if (pm.resolveActivity(selector, 0) != null) return selector

    return pm.getLaunchIntentForPackage(OUTLOOK_PACKAGE)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    } ?: timeIntent
}

private fun newEventIntent(context: Context): Intent {
    val insert = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    return if (context.packageManager.resolveActivity(insert, 0) != null) {
        insert
    } else {
        openCalendarAppIntent(context)
    }
}

private fun viewEventIntent(event: CalendarEvent): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin.toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
