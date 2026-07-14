package com.amitozalvo.nothingsuite.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Content-trigger jobs don't survive reboot; re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CalendarChangeJobService.schedule(context)
        }
    }
}
