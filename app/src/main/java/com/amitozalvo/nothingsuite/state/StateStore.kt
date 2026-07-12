package com.amitozalvo.nothingsuite.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-process bus between the NotificationListenerService (writer) and the
 * Glyph Toy service / UI (readers). Both run in the same process.
 */
object StateStore {

    private val _otp = MutableStateFlow<OtpMessage?>(null)
    val otp: StateFlow<OtpMessage?> = _otp

    private val _notificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val notificationCounts: StateFlow<Map<String, Int>> = _notificationCounts

    private val _media = MutableStateFlow<MediaInfo?>(null)
    val media: StateFlow<MediaInfo?> = _media

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected

    fun postOtp(otp: OtpMessage) {
        _otp.value = otp
    }

    fun clearOtp() {
        _otp.value = null
    }

    /** Clear the OTP if it came from the given (now removed) notification. */
    fun clearOtpForNotification(key: String) {
        if (_otp.value?.notificationKey == key) _otp.value = null
    }

    fun updateNotificationCounts(counts: Map<String, Int>) {
        _notificationCounts.value = counts
    }

    fun updateMedia(media: MediaInfo?) {
        _media.value = media
    }

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }
}
