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
        StateStore.setListenerConnected(false)
        sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        sessionManager = null
        scope?.cancel()
        scope = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publishCounts()
        refreshMedia()

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
    }

    /** Play/pause state changes don't fire the sessions-changed listener. */
    private fun refreshMedia() {
        val msm = sessionManager ?: return
        val component = ComponentName(this, GlyphNotificationListener::class.java)
        runCatching { publishMedia(msm.getActiveSessions(component)) }
    }

    private fun publishCounts() {
        runCatching {
            val counts = activeNotifications
                .filter { it.isClearable || it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 }
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .groupingBy { it.packageName }
                .eachCount()
            StateStore.updateNotificationCounts(counts)
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

    private companion object {
        const val TAG = "GlyphNotifListener"
    }
}
