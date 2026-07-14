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
        val calendarIds =
            com.amitozalvo.nothingsuite.config.SettingsRepository.get(context)
                .current().selectedCalendarIds
        provideContent {
            val hasPermission = CalendarRepository.hasPermission(context)
            val events = if (hasPermission) {
                CalendarRepository.upcomingEvents(context, calendarIds = calendarIds)
            } else {
                emptyList()
            }
            WidgetContent(context, hasPermission, events)
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    hasPermission: Boolean,
    events: List<CalendarEvent>,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BLACK)
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(openCalendarAppIntent())),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(context)
            Spacer(modifier = GlanceModifier.height(8.dp))
            when {
                !hasPermission -> PermissionHint()
                events.isEmpty() -> EmptyState()
                else -> EventList(events)
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
                .clickable(actionStartActivity(openCalendarAppIntent())),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(Color(0xFF1A1A1A))
                .cornerRadius(14.dp)
                .clickable(actionStartActivity(newEventIntent())),
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
private fun EventList(events: List<CalendarEvent>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val rows = buildRows(events, today, zone)

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rows) { row ->
            when (row) {
                is WidgetRow.DayHeader -> DaySeparator(row.label, row.isToday)
                is WidgetRow.Event -> EventRow(row.event, zone)
            }
        }
        // Trailing filler so taps below the last event open the calendar
        item {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clickable(actionStartActivity(openCalendarAppIntent())),
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
            .clickable(actionStartActivity(openCalendarAppIntent())),
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
private fun EventRow(event: CalendarEvent, zone: ZoneId) {
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
            Text(
                text = event.title,
                maxLines = 1,
                style = TextStyle(
                    // Pending invitations render dimmer until accepted
                    color = ColorProvider(if (event.pending) GREY else WHITE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            val ongoing = event.isOngoingAt(java.time.Instant.now())
            val subtitle = if (event.allDay) {
                "ALL DAY"
            } else {
                val begin = TIME_FORMAT.format(event.begin.atZone(zone))
                val end = TIME_FORMAT.format(event.end.atZone(zone))
                listOfNotNull("$begin – $end", event.location).joinToString("  ·  ")
            }
            Row {
                if (ongoing) {
                    // Only the NOW marker is red; the rest stays grey
                    Text(
                        text = "NOW · ",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(RED), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    )
                }
                Text(
                    text = subtitle,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(GREY), fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .clickable(actionStartActivity(openCalendarAppIntent())),
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

private fun openCalendarAppIntent(): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        // Canonical "open calendar app at now" URI; ACTION_MAIN with
        // CATEGORY_APP_CALENDAR does not resolve on real devices
        data = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

private fun newEventIntent(): Intent =
    Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

private fun viewEventIntent(event: CalendarEvent): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin.toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
