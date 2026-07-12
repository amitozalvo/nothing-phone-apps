# Nothing Phone (3) — Calendar Widget & Context-Aware Glyph AOD

Requirements and technical approach for a single Android app that ships:

1. A **calendar home screen widget** (backed by Google Calendar)
2. A **context-aware always-on Glyph Toy** for the Phone (3) rear Glyph Matrix

## Target device

- **Nothing Phone (3)** (`Glyph.DEVICE_23112`)
  - Glyph Matrix: 25×25 LED grid, per-pixel brightness 0–255
  - Glyph Button: short press (cycles toys), long press (`EVENT_CHANGE`),
    touch down/up events
  - AOD support: `EVENT_AOD` tick delivered ~once per minute while the toy
    is selected as the always-on display; the bound service may also push
    frames asynchronously (event-driven updates, e.g. an incoming OTP)
- Widget portion runs on any Android device; Glyph Toy activates only on
  Nothing hardware.

## Platform & SDK facts (verified July 2026)

- **Glyph Matrix Developer Kit** (official, no API key required):
  https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
  - Distributed as `glyph-matrix-sdk-2.0.aar` → placed in `app/libs/`
  - Permission: `com.nothing.ketchum.permission.ENABLE`
  - Core classes: `GlyphMatrixManager` (bind, register device, push frames),
    `GlyphMatrixObject.Builder` (image/text, position, rotation 0–360,
    scale 0–200, brightness 0–255), `GlyphMatrixFrame.Builder` (up to 3
    layers: top/mid/low, renders to `int[]`)
  - A Glyph Toy = an exported Android `Service` registered in the manifest
    with meta-data (toy name, preview image, optional AOD + long-press
    flags). NothingOS discovers it automatically; communication happens over
    a `Messenger` returned from `onBind()`.
  - Official Kotlin example (incl. `GlyphMatrixService` wrapper):
    https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project
- **Widgets**: standard Android App Widgets — no Nothing-specific SDK.
  Built with **Jetpack Glance** (Compose-style RemoteViews).
- **Calendar data**: Android **CalendarProvider** (`READ_CALENDAR`).
  Google Calendar syncs to the device provider, so we get the user's real
  calendars/events with no OAuth, work offline, and receive change
  broadcasts (`CalendarContract.CONTENT_URI` observer) for instant refresh.
  The Google Calendar REST API is intentionally NOT used.
- **Notification & OTP data**: a `NotificationListenerService` (user grants
  notification access once). One grant powers both OTP extraction (from
  SMS/messenger notifications, via regex on notification text) and per-app
  notification counts. Avoids requesting raw `READ_SMS`. Fallback option if
  OTP notifications prove unreliable: `RECEIVE_SMS` broadcast receiver.

## Deliverable 1 — Calendar widget

Single widget type, resizable; **designed wide-first** (e.g. default 4×2,
sensible degradation down to 2×2 and up to 5×3).

Layout:
- **Header**: current date (weekday + day number) styled like Nothing's
  native widgets — dot-matrix/NDot numerals, white on true black, **red
  accent** (e.g. today marker / weekday), Nothing spacing and iconography.
- **Body**: scrollable list of upcoming events — time, title, calendar
  color as a subtle accent bar; all-day events pinned at top of their day;
  day separators for events beyond today.
- **"+" icon** placed in the header corner.

Tap behavior:
- Tap an event → opens that event (deep link via
  `CalendarContract.Events` view intent).
- Tap "+" → opens Google Calendar's create-event screen
  (`Intent.ACTION_INSERT` on `Events.CONTENT_URI`).
- Tap anywhere else (header, background, empty state) → opens the
  calendar app (day view at today).

Behavior:
- All visible calendars from CalendarProvider, respecting per-calendar
  visibility; declined events hidden; recurring/multi-day events expanded
  correctly via the `Instances` table.
- Auto-refresh: content observer on calendar changes + scheduled ticks at
  day boundaries and event start/end times.

Design language: match Nothing native widgets — true black background,
monochrome white/grey, red accent, NDot-style numerals, dot texture.

## Deliverable 2 — Context-aware always-on Glyph Toy

A single Glyph Toy service, **AOD-enabled, designed to run as the
always-on display**. Content is organized into two surfaces:

- **Scenes** — persistent full-matrix displays. Each scene has a
  *predefined activation condition* (when it considers itself "active")
  and the user controls **sort order** (drag-to-reorder) plus per-scene
  enable/disable and settings. At any moment the toy shows the first
  scene in the user's order whose activation condition holds; the ambient
  board is always active and acts as the fallback.
- **Toasts & overlays** — short-lived event-driven interrupts (toasts)
  that temporarily take over the matrix and then revert, and tiny
  persistent **corner indicators** composited onto the ambient board
  (layers: the SDK's frame builder supports 3 compositing layers).
  Battery information deliberately lives here, NOT as a scene, so it
  never outranks calendar/OTP content.

### Scenes (default order; user-sortable)

1. **Next event** *(activation: within configured lead time, default
   30 min, of the next calendar event, or event ongoing)*
   - **Progress bar** of time elapsed toward event start, minutes-until
     in dot digits, and the **event title as a scrolling marquee**.
   - While ongoing: progress bar of time remaining.
2. **Upcoming alarm** *(activation: within configured window, default
   30 min, of the next alarm — `AlarmManager.getNextAlarmClock()`)*
   - Alarm icon + alarm time.
3. **Now playing** *(activation: active media session)*
   - Small equalizer animation + track title marquee (MediaSession via
     NotificationListener).
4. **Ambient status board** *(always active — fallback scene)*
   - Date + time in dot-matrix digits, plus a compact icon row:
     - calendar icon + count of remaining events today
     - notification icon + count of notifications from **user-configured
       apps** (chosen in the config screen from installed apps)

### Toasts (transient takeovers; each toggleable)

1. **OTP from messages** *(configurable: source apps, timeout default
   2 min — the only toast that persists until dismissed/expired)*
   - When a notification matching an OTP pattern (4–8 digit code from
     SMS/messaging apps) arrives, immediately push the code to the
     matrix (digits paged/scrolled if needed).
   - **Dismissed by pressing the Glyph Button**; auto-expires on timeout
     or when the notification is dismissed on the phone.
   - Highest priority — interrupts any scene or toast.
2. **Charging toast** (~8 s): on plug-in, battery % with fill animation,
   then revert to the active scene.
3. **Low-battery toast** (~8 s): fires once when crossing thresholds
   (default 15% and 5%, not charging) — battery outline + %.

### Corner indicators (on ambient board only)

- **Charging**: small lightning glyph beside the time while plugged in.
- **Low battery**: dim pulsing battery outline while below threshold.

Explicitly out of scope: missed-call badge (NothingOS essential
notifications already covers it) and timer countdown (belongs as its own
dedicated toy, not a scene here).

### Interactions

- **Glyph Button press (touch down/up)**: dismisses the current toast
  (OTP, battery); when no toast is showing, a press on the ambient board
  peeks at the next scene in the user's order.
- **Long press (`EVENT_CHANGE`)**: cycles through enabled scenes manually.

### Configuration screen (in-app)

- **Sortable scene list** (drag-to-reorder) with enable/disable toggles
  and per-scene settings (event lead time, alarm window, monitored apps).
- Toast settings: OTP sources + timeout, charging/low-battery toasts
  on/off, thresholds.
- Permission onboarding: calendar permission, notification access grant,
  battery-optimization exemption for reliable event-driven updates.
- Live 25×25 **matrix preview** of each scene/toast with sample data.

### Constraints honored

- AOD ticks ~1/min: time/progress scenes re-render from cached state; no
  continuous animation while in AOD (marquee/animation frames only during
  event-driven moments like OTP arrival or while interacted with).
- Event-driven pushes (OTP, charging state change) happen from the bound
  service as notifications/broadcasts arrive.
- Brightness kept battery-friendly; matrix content is monochrome
  brightness levels only (no color on the Glyph Matrix).

## App structure

Single Kotlin app, single APK (`com.amitozalvo.nothingsuite` — TBD):
- `:app` module: Glance calendar widget + Glyph Toy service +
  NotificationListenerService + configuration Activity.
- min SDK 34 (Phone (3) ships Android 15), target latest.
- Sideload via ADB/Android Studio; Play Store packaging out of scope for v1.

## Open questions (assumptions in place until answered)

1. OTP source: NotificationListener-based extraction assumed (no `READ_SMS`
   permission). If codes are missed (e.g. RCS quirks), fall back to an SMS
   receiver — confirm acceptable.
2. Widget default size 4×2 wide — confirm.

## Testing

- Widget: emulator + any Android phone.
- Glyph Toy: requires the physical Phone (3) (no emulator support);
  debug builds include an on-screen 25×25 matrix simulator Activity for
  rapid iteration before sideloading.
