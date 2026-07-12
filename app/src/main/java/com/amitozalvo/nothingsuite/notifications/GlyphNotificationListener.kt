package com.amitozalvo.nothingsuite.notifications

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.config.SettingsRepository
import com.amitozalvo.nothingsuite.state.MediaInfo
import com.amitozalvo.nothingsuite.state.OtpMessage
import com.amitozalvo.nothingsuite.state.RingingAlarm
import com.amitozalvo.nothingsuite.state.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Single notification-access entry point: OTP codes from configured
 * messaging apps, per-app notification counts for the ambient board, and
 * active media session tracking. Everything is published to [StateStore].
 */
class GlyphNotificationListener : NotificationListenerService() {

    private var scope: CoroutineScope? = null
    private var settings = GlyphSettings()
    private var sessionManager: MediaSessionManager? = null

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            publishMedia(controllers)
        }

    override fun onListenerConnected() {
        Log.d(TAG, "listener connected")
        instance = this
        StateStore.setListenerConnected(true)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        this.scope = scope
        scope.launch {
            SettingsRepository.get(applicationContext).settings.collect {
                settings = it
                publishCounts()
            }
        }

        val component = ComponentName(this, GlyphNotificationListener::class.java)
        sessionManager = getSystemService(MediaSessionManager::class.java)?.also { msm ->
            runCatching {
                msm.addOnActiveSessionsChangedListener(sessionListener, component)
                publishMedia(msm.getActiveSessions(component))
            }.onFailure { Log.w(TAG, "media sessions unavailable", it) }
        }

        publishCounts()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        StateStore.setListenerConnected(false)
        sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        sessionManager = null
        scope?.cancel()
        scope = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publishCounts()
        refreshMedia()
        detectRingingAlarm(sbn)

        if (!settings.otpEnabled) return
        if (sbn.packageName !in settings.otpSources) return

        val extras = sbn.notification.extras
        val text = buildString {
            extras.getCharSequence(Notification.EXTRA_TITLE)?.let { append(it).append(' ') }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { append(it).append(' ') }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { append(it) }
        }

        OtpExtractor.extract(text)?.let { code ->
            Log.d(TAG, "OTP detected from ${sbn.packageName}")
            StateStore.postOtp(
                OtpMessage(
                    code = code,
                    sourcePackage = sbn.packageName,
                    postedAt = Instant.now(),
                    notificationKey = sbn.key,
                )
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        publishCounts()
        refreshMedia()
        StateStore.clearOtpForNotification(sbn.key)
        StateStore.clearRingingAlarmForNotification(sbn.key)
    }

    /**
     * A firing alarm posts a CATEGORY_ALARM notification with a
     * full-screen intent (upcoming-alarm reminders don't have one).
     */
    private fun detectRingingAlarm(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_ALARM && n.fullScreenIntent != null) {
            StateStore.setRingingAlarm(
                RingingAlarm(
                    notificationKey = sbn.key,
                    label = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                )
            )
        }
    }

    /** Fire the alarm notification's snooze action (Glyph Button press). */
    fun snoozeRingingAlarm(): Boolean {
        val key = StateStore.ringingAlarm.value?.notificationKey ?: return false
        val sbn = runCatching { activeNotifications }.getOrNull()
            ?.firstOrNull { it.key == key } ?: return false
        val actions = sbn.notification.actions?.toList().orEmpty()
        val snooze = actions.firstOrNull { action ->
            SNOOZE_WORDS.any { action.title?.toString()?.contains(it, ignoreCase = true) == true }
        } ?: actions.firstOrNull() ?: return false
        return runCatching { snooze.actionIntent.send() }
            .onSuccess { StateStore.setRingingAlarm(null) }
            .isSuccess
    }

    /** Toggle the active media session. Returns the new playing state. */
    fun toggleMediaPlayback(): Boolean? {
        val msm = sessionManager ?: return null
        val component = ComponentName(this, GlyphNotificationListener::class.java)
        val sessions = runCatching { msm.getActiveSessions(component) }.getOrNull() ?: return null
        val controller = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull() ?: return null
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) controller.transportControls.pause()
        else controller.transportControls.play()
        return !playing
    }

    /** Play/pause state changes don't fire the sessions-changed listener. */
    private fun refreshMedia() {
        val msm = sessionManager ?: return
        val component = ComponentName(this, GlyphNotificationListener::class.java)
        runCatching { publishMedia(msm.getActiveSessions(component)) }
    }

    private fun publishCounts() {
        runCatching {
            val relevant = activeNotifications
                .filter { it.isClearable || it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 }
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            StateStore.updateNotificationCounts(
                relevant.groupingBy { it.packageName }.eachCount()
            )
            StateStore.updateNotificationTitles(
                relevant.sortedByDescending { it.postTime }.mapNotNull { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)
                        ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                    title?.toString()?.takeIf { it.isNotBlank() }?.let { sbn.packageName to it }
                }
            )
        }.onFailure { Log.w(TAG, "failed reading notifications", it) }
    }

    private fun publishMedia(controllers: List<MediaController>?) {
        val active = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull()

        val metadata = active?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (active == null || title.isNullOrBlank()) {
            StateStore.updateMedia(null)
            return
        }
        StateStore.updateMedia(
            MediaInfo(
                title = title,
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                playing = active.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        )
    }

    companion object {
        private const val TAG = "GlyphNotifListener"
        private val SNOOZE_WORDS = listOf("snooze", "נודניק", "דחיית", "דחה")

        /** Live listener instance for command calls (same process). */
        @Volatile
        var instance: GlyphNotificationListener? = null
            private set
    }
}
