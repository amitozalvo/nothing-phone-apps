package com.amitozalvo.nothingsuite.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CalendarChangeJobService.schedule(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CalendarChangeJobService.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        CalendarChangeJobService.cancel(context)
    }
}
