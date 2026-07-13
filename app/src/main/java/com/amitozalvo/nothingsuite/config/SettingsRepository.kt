package com.amitozalvo.nothingsuite.config

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "glyph_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SCENE_ORDER = stringPreferencesKey("scene_order")
        val ENABLED_SCENES = stringSetPreferencesKey("enabled_scenes")
        val EVENT_LEAD_MINUTES = intPreferencesKey("event_lead_minutes")
        val SHOW_ONGOING_EVENT = booleanPreferencesKey("show_ongoing_event")
        val ONLY_WHEN_SCREEN_OFF = booleanPreferencesKey("only_when_screen_off")
        val ALARM_WINDOW_MINUTES = intPreferencesKey("alarm_window_minutes")
        val OTP_ENABLED = booleanPreferencesKey("otp_enabled")
        val OTP_TIMEOUT_SECONDS = intPreferencesKey("otp_timeout_seconds")
        val OTP_SOURCES = stringSetPreferencesKey("otp_sources")
        val CHARGING_TOAST = booleanPreferencesKey("charging_toast_enabled")
        val LOW_BATTERY_TOAST = booleanPreferencesKey("low_battery_toast_enabled")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val MONITORED_APPS = stringSetPreferencesKey("monitored_apps")
    }

    private val defaults = GlyphSettings()

    val settings: Flow<GlyphSettings> = context.dataStore.data.map { p ->
        GlyphSettings(
            sceneOrder = p[Keys.SCENE_ORDER]?.split(',')?.filter { it.isNotBlank() }
                ?.let { saved -> saved + SceneIds.DEFAULT_ORDER.filter { it !in saved } }
                ?: defaults.sceneOrder,
            enabledScenes = p[Keys.ENABLED_SCENES] ?: defaults.enabledScenes,
            eventLeadMinutes = p[Keys.EVENT_LEAD_MINUTES] ?: defaults.eventLeadMinutes,
            showOngoingEvent = p[Keys.SHOW_ONGOING_EVENT] ?: defaults.showOngoingEvent,
            onlyWhenScreenOff = p[Keys.ONLY_WHEN_SCREEN_OFF] ?: defaults.onlyWhenScreenOff,
            alarmWindowMinutes = p[Keys.ALARM_WINDOW_MINUTES] ?: defaults.alarmWindowMinutes,
            otpEnabled = p[Keys.OTP_ENABLED] ?: defaults.otpEnabled,
            otpTimeoutSeconds = p[Keys.OTP_TIMEOUT_SECONDS] ?: defaults.otpTimeoutSeconds,
            otpSources = p[Keys.OTP_SOURCES] ?: defaults.otpSources,
            chargingToastEnabled = p[Keys.CHARGING_TOAST] ?: defaults.chargingToastEnabled,
            lowBatteryToastEnabled = p[Keys.LOW_BATTERY_TOAST] ?: defaults.lowBatteryToastEnabled,
            lowBatteryThreshold = p[Keys.LOW_BATTERY_THRESHOLD] ?: defaults.lowBatteryThreshold,
            monitoredApps = p[Keys.MONITORED_APPS] ?: defaults.monitoredApps,
        )
    }

    suspend fun current(): GlyphSettings = settings.first()

    suspend fun setSceneOrder(order: List<String>) {
        context.dataStore.edit { it[Keys.SCENE_ORDER] = order.joinToString(",") }
    }

    suspend fun setSceneEnabled(sceneId: String, enabled: Boolean) {
        context.dataStore.edit { p ->
            val current = p[Keys.ENABLED_SCENES] ?: defaults.enabledScenes
            p[Keys.ENABLED_SCENES] = if (enabled) current + sceneId else current - sceneId
        }
    }

    suspend fun setEventLeadMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.EVENT_LEAD_MINUTES] = minutes }
    }

    suspend fun setShowOngoingEvent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ONGOING_EVENT] = enabled }
    }

    suspend fun setOnlyWhenScreenOff(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ONLY_WHEN_SCREEN_OFF] = enabled }
    }

    suspend fun setAlarmWindowMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.ALARM_WINDOW_MINUTES] = minutes }
    }

    suspend fun setOtpEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OTP_ENABLED] = enabled }
    }

    suspend fun setOtpTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.OTP_TIMEOUT_SECONDS] = seconds }
    }

    suspend fun setOtpSources(packages: Set<String>) {
        context.dataStore.edit { it[Keys.OTP_SOURCES] = packages }
    }

    suspend fun setChargingToastEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHARGING_TOAST] = enabled }
    }

    suspend fun setLowBatteryToastEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOW_BATTERY_TOAST] = enabled }
    }

    suspend fun setLowBatteryThreshold(threshold: Int) {
        context.dataStore.edit { it[Keys.LOW_BATTERY_THRESHOLD] = threshold }
    }

    suspend fun setMonitoredApps(packages: Set<String>) {
        context.dataStore.edit { it[Keys.MONITORED_APPS] = packages }
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
