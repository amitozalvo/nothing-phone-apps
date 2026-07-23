package com.amitozalvo.nothingsuite.config

object SceneIds {
    const val NEXT_EVENT = "next_event"
    const val ALARM = "alarm"
    const val MEDIA = "media"
    const val AMBIENT = "ambient"

    val DEFAULT_ORDER = listOf(NEXT_EVENT, ALARM, MEDIA, AMBIENT)
}

data class GlyphSettings(
    /** User-sorted scene priority. Ambient is always present as fallback. */
    val sceneOrder: List<String> = SceneIds.DEFAULT_ORDER,
    val enabledScenes: Set<String> = SceneIds.DEFAULT_ORDER.toSet(),
    val eventLeadMinutes: Int = 30,
    /** Keep the next-event scene up while the event is running. */
    val showOngoingEvent: Boolean = false,
    /** Blank the matrix while the phone is unlocked with the screen on. */
    val onlyWhenScreenOff: Boolean = false,
    val alarmWindowMinutes: Int = 30,
    val otpEnabled: Boolean = true,
    val otpTimeoutSeconds: Int = 120,
    /** Packages whose notifications are scanned for OTP codes. */
    val otpSources: Set<String> = setOf("com.google.android.apps.messaging"),
    val chargingToastEnabled: Boolean = true,
    val lowBatteryToastEnabled: Boolean = true,
    val lowBatteryThreshold: Int = 15,
    /** Apps whose notification count shows on the ambient board. */
    val monitoredApps: Set<String> = emptySet(),
    /** Calendars used by the widget and glyph scenes; empty = all visible. */
    val selectedCalendarIds: Set<Long> = emptySet(),
    /** Keep today's already-ended events in the widget (dimmed). */
    val showPastEventsToday: Boolean = true,
) {
    /** Enabled scenes in user order, always ending with the ambient fallback. */
    fun activeSceneOrder(): List<String> {
        val order = sceneOrder.filter { it in enabledScenes && it != SceneIds.AMBIENT }
        return order + SceneIds.AMBIENT
    }
}
