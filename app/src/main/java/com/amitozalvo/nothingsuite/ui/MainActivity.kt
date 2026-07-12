package com.amitozalvo.nothingsuite.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import com.amitozalvo.nothingsuite.calendar.CalendarRepository
import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.config.SceneIds
import com.amitozalvo.nothingsuite.config.SettingsRepository
import com.amitozalvo.nothingsuite.state.StateStore
import kotlinx.coroutines.launch

private val NothingRed = Color(0xFFD71921)
private val NothingWhite = Color(0xFFF2F2F2)
private val NothingGrey = Color(0xFF8A8A8A)
private val NothingDark = Color(0xFF141414)

private val SceneNames = mapOf(
    SceneIds.NEXT_EVENT to "Next event",
    SceneIds.ALARM to "Upcoming alarm",
    SceneIds.MEDIA to "Now playing",
    SceneIds.AMBIENT to "Ambient board",
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NothingRed,
                    background = Color.Black,
                    surface = NothingDark,
                    onBackground = NothingWhite,
                    onSurface = NothingWhite,
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings by repo.settings.collectAsState(initial = GlyphSettings())
    val scope = rememberCoroutineScope()

    var calendarGranted by remember {
        mutableStateOf(CalendarRepository.hasPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { calendarGranted = it }
    val listenerConnected by StateStore.listenerConnected.collectAsState()
    val notificationAccess = listenerConnected ||
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("NOTHING SUITE", color = NothingWhite, fontSize = 22.sp, letterSpacing = 3.sp)
        Text(
            "Calendar widget · Context AOD glyph toy",
            color = NothingGrey, fontSize = 13.sp,
        )

        // ---- Permissions ----
        SectionTitle("PERMISSIONS")
        PermissionCard(
            title = "Calendar access",
            description = "Events for the widget and next-event scene",
            granted = calendarGranted,
            onRequest = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
        )
        PermissionCard(
            title = "Notification access",
            description = "OTP codes, notification counts and media info",
            granted = notificationAccess,
            onRequest = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )

        // ---- Scenes ----
        SectionTitle("SCENES · PRIORITY ORDER")
        Text(
            "The toy shows the first active scene. Reorder to decide what wins.",
            color = NothingGrey, fontSize = 12.sp,
        )
        val order = settings.sceneOrder.filter { it != SceneIds.AMBIENT } + SceneIds.AMBIENT
        order.forEachIndexed { index, sceneId ->
            SceneCard(
                sceneId = sceneId,
                name = SceneNames[sceneId] ?: sceneId,
                enabled = sceneId in settings.enabledScenes,
                isFallback = sceneId == SceneIds.AMBIENT,
                canMoveUp = index > 0 && sceneId != SceneIds.AMBIENT,
                canMoveDown = index < order.size - 2 && sceneId != SceneIds.AMBIENT,
                settings = settings,
                onToggle = { enabled ->
                    scope.launch { repo.setSceneEnabled(sceneId, enabled) }
                },
                onMove = { delta ->
                    val movable = order.filter { it != SceneIds.AMBIENT }.toMutableList()
                    val i = movable.indexOf(sceneId)
                    val j = i + delta
                    if (i >= 0 && j in movable.indices) {
                        movable[i] = movable[j].also { movable[j] = movable[i] }
                        scope.launch { repo.setSceneOrder(movable + SceneIds.AMBIENT) }
                    }
                },
                onLeadTimeChange = { minutes ->
                    scope.launch {
                        when (sceneId) {
                            SceneIds.NEXT_EVENT -> repo.setEventLeadMinutes(minutes)
                            SceneIds.ALARM -> repo.setAlarmWindowMinutes(minutes)
                        }
                    }
                },
                onToggleOngoing = { enabled ->
                    scope.launch { repo.setShowOngoingEvent(enabled) }
                },
                onPickApps = null,
            )
        }

        // Monitored apps for the ambient board
        var showAppPicker by remember { mutableStateOf(false) }
        TextButton(onClick = { showAppPicker = true }) {
            Text(
                "Monitored apps (${settings.monitoredApps.size}) — counted on ambient board",
                color = NothingRed, fontSize = 13.sp,
            )
        }
        if (showAppPicker) {
            AppPickerDialog(
                selected = settings.monitoredApps,
                onDismiss = { showAppPicker = false },
                onConfirm = { apps ->
                    scope.launch { repo.setMonitoredApps(apps) }
                    showAppPicker = false
                },
            )
        }

        // ---- Toasts ----
        SectionTitle("ALERTS (TEMPORARY TAKEOVERS)")
        ToggleCard(
            title = "OTP codes",
            description = "Show verification codes from messages. Dismiss with the Glyph Button. Timeout ${settings.otpTimeoutSeconds}s.",
            checked = settings.otpEnabled,
            onToggle = { scope.launch { repo.setOtpEnabled(it) } },
        )
        var showOtpPicker by remember { mutableStateOf(false) }
        if (settings.otpEnabled) {
            TextButton(onClick = { showOtpPicker = true }) {
                Text(
                    "OTP source apps (${settings.otpSources.size})",
                    color = NothingRed, fontSize = 13.sp,
                )
            }
        }
        if (showOtpPicker) {
            AppPickerDialog(
                selected = settings.otpSources,
                onDismiss = { showOtpPicker = false },
                onConfirm = { apps ->
                    scope.launch { repo.setOtpSources(apps) }
                    showOtpPicker = false
                },
            )
        }
        ToggleCard(
            title = "Charging",
            description = "Battery percentage for a few seconds when plugged in",
            checked = settings.chargingToastEnabled,
            onToggle = { scope.launch { repo.setChargingToastEnabled(it) } },
        )
        ToggleCard(
            title = "Low battery",
            description = "Warn once below ${settings.lowBatteryThreshold}% and at 5%",
            checked = settings.lowBatteryToastEnabled,
            onToggle = { scope.launch { repo.setLowBatteryToastEnabled(it) } },
        )

        // ---- Preview ----
        SectionTitle("MATRIX PREVIEW")
        ScenePreviews(settings)

        Spacer(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = NothingGrey, fontSize = 12.sp, letterSpacing = 2.sp)
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = NothingDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = NothingWhite, fontSize = 15.sp)
                Text(description, color = NothingGrey, fontSize = 12.sp)
            }
            if (granted) {
                Text("GRANTED", color = NothingGrey, fontSize = 12.sp, letterSpacing = 1.sp)
            } else {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
                ) { Text("Grant", color = Color.White) }
            }
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = NothingDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = NothingWhite, fontSize = 15.sp)
                Text(description, color = NothingGrey, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = NothingRed),
            )
        }
    }
}

@Composable
private fun SceneCard(
    sceneId: String,
    name: String,
    enabled: Boolean,
    isFallback: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    settings: GlyphSettings,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onLeadTimeChange: ((Int) -> Unit)?,
    onToggleOngoing: ((Boolean) -> Unit)? = null,
    onPickApps: (() -> Unit)?,
) {
    Card(colors = CardDefaults.cardColors(containerColor = NothingDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowUp, "Move up",
                        tint = if (canMoveUp) NothingWhite else Color(0xFF333333),
                    )
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, "Move down",
                        tint = if (canMoveDown) NothingWhite else Color(0xFF333333),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = NothingWhite, fontSize = 15.sp)
                when (sceneId) {
                    SceneIds.NEXT_EVENT -> {
                        LeadTimeStepper(
                            label = "Lead time",
                            minutes = settings.eventLeadMinutes,
                            onChange = onLeadTimeChange,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("During event: ", color = NothingGrey, fontSize = 12.sp)
                            TextButton(
                                onClick = { onToggleOngoing?.invoke(!settings.showOngoingEvent) },
                            ) {
                                Text(
                                    if (settings.showOngoingEvent) "SHOWN" else "HIDDEN",
                                    color = if (settings.showOngoingEvent) NothingRed else NothingGrey,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    SceneIds.ALARM -> LeadTimeStepper(
                        label = "Window",
                        minutes = settings.alarmWindowMinutes,
                        onChange = onLeadTimeChange,
                    )
                    SceneIds.MEDIA -> Text(
                        "While media is playing", color = NothingGrey, fontSize = 12.sp,
                    )
                    SceneIds.AMBIENT -> Text(
                        "Always-on fallback · time, date, counters",
                        color = NothingGrey, fontSize = 12.sp,
                    )
                }
            }
            if (isFallback) {
                Text("ALWAYS", color = NothingGrey, fontSize = 11.sp, letterSpacing = 1.sp)
            } else {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = NothingRed),
                )
            }
        }
    }
}

@Composable
private fun LeadTimeStepper(label: String, minutes: Int, onChange: ((Int) -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", color = NothingGrey, fontSize = 12.sp)
        TextButton(
            onClick = { onChange?.invoke((minutes - 15).coerceAtLeast(15)) },
            modifier = Modifier.size(32.dp),
        ) { Text("−", color = NothingRed) }
        Text("$minutes min", color = NothingWhite, fontSize = 12.sp)
        TextButton(
            onClick = { onChange?.invoke((minutes + 15).coerceAtMost(120)) },
            modifier = Modifier.size(32.dp),
        ) { Text("+", color = NothingRed) }
    }
}

@Composable
private fun AppPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
    var current by remember { mutableStateOf(selected) }

    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = NothingDark)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select apps", color = NothingWhite, fontSize = 16.sp)
                Spacer(modifier = Modifier.size(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    apps.forEach { (pkg, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = pkg in current,
                                onCheckedChange = { checked ->
                                    current = if (checked) current + pkg else current - pkg
                                },
                            )
                            Text(label, color = NothingWhite, fontSize = 14.sp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = NothingGrey) }
                    TextButton(onClick = { onConfirm(current) }) { Text("Done", color = NothingRed) }
                }
            }
        }
    }
}
