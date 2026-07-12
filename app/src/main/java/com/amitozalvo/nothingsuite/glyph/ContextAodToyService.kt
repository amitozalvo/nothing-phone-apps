package com.amitozalvo.nothingsuite.glyph

import android.app.AlarmManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.provider.CalendarContract
import android.util.Log
import com.amitozalvo.nothingsuite.calendar.CalendarRepository
import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.config.SettingsRepository
import com.amitozalvo.nothingsuite.config.SceneIds
import com.amitozalvo.nothingsuite.glyph.scenes.AlarmRingingToast
import com.amitozalvo.nothingsuite.glyph.scenes.AlarmScene
import com.amitozalvo.nothingsuite.glyph.scenes.AmbientScene
import com.amitozalvo.nothingsuite.glyph.scenes.ChargingToast
import com.amitozalvo.nothingsuite.glyph.scenes.LowBatteryToast
import com.amitozalvo.nothingsuite.glyph.scenes.MediaScene
import com.amitozalvo.nothingsuite.glyph.scenes.NextEventScene
import com.amitozalvo.nothingsuite.glyph.scenes.OtpToast
import com.amitozalvo.nothingsuite.glyph.scenes.SceneEngine
import com.amitozalvo.nothingsuite.glyph.scenes.TextToast
import com.amitozalvo.nothingsuite.notifications.GlyphNotificationListener
import com.amitozalvo.nothingsuite.state.BatteryInfo
import com.amitozalvo.nothingsuite.state.ContextSnapshot
import com.amitozalvo.nothingsuite.state.StateStore
import com.amitozalvo.nothingsuite.state.TitledItem
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The "Context AOD" Glyph Toy. While bound by NothingOS it renders the
 * scene engine's output on the 25×25 matrix, reacting to AOD ticks, Glyph
 * Button events, notification/OTP state, battery and calendar changes.
 */
class ContextAodToyService : Service() {

    private val nextEventScene = NextEventScene()
    private val ambientScene = AmbientScene()
    private val engine = SceneEngine(
        listOf(nextEventScene, AlarmScene(), MediaScene(), ambientScene)
    )
    private var manager: GlyphMatrixManager? = null
    private var scope: CoroutineScope? = null

    private var settings = GlyphSettings()
    private var snapshot = ContextSnapshot(now = Instant.now())
    private var tick = 0L
    private var lastFrame: IntArray? = null
    private var lastBatteryPercent = 100
    private var refreshJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val animator = object : Runnable {
        override fun run() {
            tick++
            renderAndPush()
            mainHandler.postDelayed(this, ANIMATION_INTERVAL_MS)
        }
    }

    private val toyEventHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                GlyphToy.MSG_GLYPH_TOY -> {
                    when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                        GlyphToy.EVENT_AOD -> refreshSnapshot()
                        GlyphToy.EVENT_ACTION_DOWN -> onButtonDown()
                        GlyphToy.EVENT_ACTION_UP -> onButtonUp()
                        GlyphToy.EVENT_CHANGE -> onLongPress()
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(toyEventHandler)

    private val managerCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            manager?.register(Glyph.DEVICE_23112)
            refreshSnapshot()
            mainHandler.post(animator)
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    if (settings.chargingToastEnabled) {
                        engine.postToast(
                            ChargingToast(batteryPercent(), toastExpiry())
                        )
                    }
                    refreshSnapshot()
                }
                Intent.ACTION_POWER_DISCONNECTED -> refreshSnapshot()
                Intent.ACTION_BATTERY_CHANGED -> {
                    val percent = batteryPercent()
                    val charging = isCharging()
                    if (settings.lowBatteryToastEnabled && !charging) {
                        for (threshold in listOf(settings.lowBatteryThreshold, 5)) {
                            if (percent <= threshold && lastBatteryPercent > threshold) {
                                engine.postToast(LowBatteryToast(percent, toastExpiry()))
                            }
                        }
                    }
                    if (percent != lastBatteryPercent) {
                        lastBatteryPercent = percent
                        refreshSnapshot()
                    }
                }
            }
        }
    }

    private val calendarObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = refreshSnapshot()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        this.scope = scope

        GlyphMatrixManager.getInstance(applicationContext)?.let {
            manager = it
            it.init(managerCallback)
        }

        lastBatteryPercent = batteryPercent()

        registerReceiver(batteryReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        })
        if (CalendarRepository.hasPermission(this)) {
            contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI, true, calendarObserver
            )
        }

        scope.launch {
            SettingsRepository.get(applicationContext).settings.collect {
                settings = it
                refreshSnapshot()
            }
        }
        scope.launch {
            StateStore.otp.collect { otp ->
                if (otp != null && settings.otpEnabled) {
                    engine.postToast(
                        OtpToast(
                            code = otp.code,
                            expiresAt = otp.postedAt.plusSeconds(
                                settings.otpTimeoutSeconds.toLong()
                            ),
                            notificationKey = otp.notificationKey,
                        )
                    )
                } else if (otp == null) {
                    engine.clearOtpToast()
                }
                renderAndPush(force = true)
            }
        }
        scope.launch {
            StateStore.media.collect { refreshSnapshot() }
        }
        scope.launch {
            StateStore.notificationCounts.collect { refreshSnapshot() }
        }
        scope.launch {
            StateStore.ringingAlarm.collect { ringing ->
                if (ringing != null) {
                    engine.postToast(AlarmRingingToast(ringing.notificationKey))
                } else {
                    engine.clearRingingAlarmToast()
                }
                renderAndPush(force = true)
            }
        }

        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        mainHandler.removeCallbacks(animator)
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { contentResolver.unregisterContentObserver(calendarObserver) }
        scope?.cancel()
        scope = null
        manager?.turnOff()
        manager?.unInit()
        manager = null
        return false
    }

    private fun onButtonDown() {
        val toast = engine.currentToast

        // Ringing alarm: the button snoozes
        if (toast is AlarmRingingToast) {
            val snoozed = GlyphNotificationListener.instance?.snoozeRingingAlarm() == true
            if (snoozed) {
                engine.clearRingingAlarmToast()
                engine.postToast(TextToast("ZZZ", Instant.now().plusSeconds(2)))
            }
            renderAndPush(force = true)
            return
        }

        // Any other toast: dismiss
        if (engine.dismissToastByButton()) {
            if (toast is OtpToast) StateStore.clearOtp()
            renderAndPush(force = true)
            return
        }

        // No toast: the button acts on the current scene
        when (engine.selectScene(snapshot.copy(now = Instant.now()), settings).id) {
            SceneIds.MEDIA -> {
                val playing = GlyphNotificationListener.instance?.toggleMediaPlayback()
                if (playing != null) {
                    engine.postToast(
                        TextToast(if (playing) "PLAY" else "PAUSE", Instant.now().plusSeconds(1))
                    )
                }
            }
            SceneIds.NEXT_EVENT -> nextEventScene.showTitle = true
            SceneIds.AMBIENT -> ambientScene.cycle(snapshot.copy(now = Instant.now()))
        }
        renderAndPush(force = true)
    }

    private fun onButtonUp() {
        if (nextEventScene.showTitle) {
            nextEventScene.showTitle = false
            renderAndPush(force = true)
        }
    }

    private fun onLongPress() {
        // Long press: force-refresh all context and re-render
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        refreshJob?.cancel()
        refreshJob = scope?.launch(Dispatchers.IO) {
            val newSnapshot = buildSnapshot()
            mainHandler.post {
                snapshot = newSnapshot
                renderAndPush(force = true)
            }
        }
    }

    private fun buildSnapshot(): ContextSnapshot {
        val now = Instant.now()
        val nextEvent = CalendarRepository.nextTimedEvent(this, now)
        val media = StateStore.media.value
        val counts = StateStore.notificationCounts.value
        val alarmManager = getSystemService(AlarmManager::class.java)

        val zone = java.time.ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val timeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val todayEvents = CalendarRepository
            .upcomingEvents(this, from = now, window = java.time.Duration.ofDays(1))
            .filter { !it.allDay && it.beginDate(zone) == today && it.end > now }
            .take(MAX_DETAIL_ITEMS)
            .map { event ->
                TitledItem(
                    title = event.title,
                    subtitle = timeFormat.format(event.begin.atZone(zone)),
                    titleRaster = TextRaster.rasterize(event.title),
                )
            }
        val notifications = StateStore.notificationTitles.value
            .filter { (pkg, _) -> pkg in settings.monitoredApps }
            .take(MAX_DETAIL_ITEMS)
            .map { (_, title) ->
                TitledItem(title = title, subtitle = null, titleRaster = TextRaster.rasterize(title))
            }

        return ContextSnapshot(
            now = now,
            nextEvent = nextEvent,
            nextEventTitleRaster = nextEvent?.let { TextRaster.rasterize(it.title) },
            remainingEventsToday = CalendarRepository.remainingEventsToday(this, now),
            nextAlarm = alarmManager?.nextAlarmClock?.let {
                Instant.ofEpochMilli(it.triggerTime)
            },
            media = media,
            mediaTitleRaster = media?.let {
                TextRaster.rasterize(listOfNotNull(it.title, it.artist).joinToString(" - "))
            },
            battery = BatteryInfo(batteryPercent(), isCharging()),
            monitoredNotificationCount = settings.monitoredApps.sumOf { counts[it] ?: 0 },
            todayEventItems = todayEvents,
            notificationItems = notifications,
            ringingAlarm = StateStore.ringingAlarm.value,
        )
    }

    private fun renderAndPush(force: Boolean = false) {
        val gmm = manager ?: return
        // Keep "now" fresh between snapshot rebuilds so clocks don't lag
        val current = snapshot.copy(now = Instant.now())
        val frame = engine.renderFrame(current, settings, tick)
        if (!force && lastFrame?.contentEquals(frame) == true) return
        lastFrame = frame
        runCatching { gmm.setMatrixFrame(frame) }
            .onFailure { Log.w(TAG, "setMatrixFrame failed", it) }
    }

    private fun batteryPercent(): Int =
        getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

    private fun isCharging(): Boolean =
        getSystemService(BatteryManager::class.java)?.isCharging ?: false

    private fun toastExpiry(): Instant = Instant.now().plus(TOAST_DURATION)

    private companion object {
        const val TAG = "ContextAodToy"
        const val ANIMATION_INTERVAL_MS = 100L
        const val MAX_DETAIL_ITEMS = 3
        val TOAST_DURATION: Duration = Duration.ofSeconds(8)
    }
}
