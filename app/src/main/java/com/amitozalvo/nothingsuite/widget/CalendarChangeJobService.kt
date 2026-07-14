package com.amitozalvo.nothingsuite.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Refreshes the calendar widget when provider data changes (content-trigger
 * job that reschedules itself) and at the next day boundary so the header
 * date and day separators roll over.
 */
class CalendarChangeJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            runCatching { CalendarWidget().updateAll(applicationContext) }
            schedule(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val CONTENT_JOB_ID = 1001
        private const val MIDNIGHT_JOB_ID = 1002

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val component = ComponentName(context, CalendarChangeJobService::class.java)

            scheduler.schedule(
                JobInfo.Builder(CONTENT_JOB_ID, component)
                    .addTriggerContentUri(
                        JobInfo.TriggerContentUri(
                            CalendarContract.CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                        )
                    )
                    .setTriggerContentUpdateDelay(2_000)
                    .setTriggerContentMaxDelay(30_000)
                    .build()
            )

            val zone = ZoneId.systemDefault()
            val nextMidnight = LocalDateTime.of(LocalDate.now(zone).plusDays(1), java.time.LocalTime.MIDNIGHT)
                .atZone(zone).toInstant()
            val delay = Duration.between(java.time.Instant.now(), nextMidnight)
                .toMillis().coerceAtLeast(60_000)
            scheduler.schedule(
                JobInfo.Builder(MIDNIGHT_JOB_ID, component)
                    .setMinimumLatency(delay)
                    .setOverrideDeadline(delay + 10 * 60_000)
                    .build()
            )
        }

        fun cancel(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            scheduler.cancel(CONTENT_JOB_ID)
            scheduler.cancel(MIDNIGHT_JOB_ID)
        }
    }
}
