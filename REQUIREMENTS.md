# Nothing Phone (3) — Calendar Widget & Context-Aware Glyph AOD

Requirements and technical approach for a single Android app that ships:

1. A **fully featured calendar home screen widget** (backed by Google Calendar)
2. A **context-aware AOD Glyph Toy** for the Phone (3) rear Glyph Matrix

## Target device

- **Nothing Phone (3)** (`Glyph.DEVICE_23112`)
  - Glyph Matrix: 25×25 LED grid, per-pixel brightness 0–255
  - Glyph Button: short press (cycles toys), long press (`EVENT_CHANGE`),
    touch down/up events
  - AOD support: `EVENT_AOD` tick delivered ~once per minute while the toy
    is selected as the always-on display
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
  Built with **Jetpack Glance** (Compose-style RemoteViews). NothingOS also
  supports surfacing widgets on the lock screen.
- **Calendar data**: Android **CalendarProvider** (`READ_CALENDAR`).
  Google Calendar syncs to the device provider, so we get the user's real
  calendars/events with no OAuth, work offline, and receive change
  broadcasts (`CalendarContract.CONTENT_URI` observer) for instant refresh.
  The Google Calendar REST API is intentionally NOT used.

## Deliverable 1 — Calendar widget ("fully featured")

Data source: all visible calendars from CalendarProvider (Google Calendar
accounts included), respecting per-calendar visibility.

Widget sizes/views (Glance, responsive by size bucket):
- **Agenda view** (tall): scrollable list of upcoming events grouped by day
  — time, title, calendar color accent, location; all-day events pinned.
- **Month grid** (large): current month with event-density dots per day;
  tapping a day shows that day's events; today highlighted.
- **Next-event card** (small): the single next event with countdown
  ("in 25 min"), title, time, location.

Behavior:
- Tap event → open it in Google Calendar (deep link via
  `CalendarContract` intent); tap header/date → open Google Calendar app.
- "+" affordance → Google Calendar new-event screen.
- Auto-refresh: content observer on calendar changes + `AlarmManager`/
  WorkManager tick at day boundaries and event start/end times.
- Declined events hidden; multi-day and recurring events handled via
  the `Instances` table (correct expansion of recurrences).

Design language: Nothing aesthetic — true black background, white/grey
monochrome, red accent, dot-matrix (NDot-style) numerals, generous dot
grid texture. Light/dark follow system, defaulting to dark.

## Deliverable 2 — Context-aware AOD Glyph Toy

A Glyph Toy service with AOD enabled. On every `EVENT_AOD` tick (and on
touch wake) it evaluates context and renders the single most relevant
"scene" on the 25×25 matrix:

Priority order (first match wins):
1. **Imminent/ongoing meeting** — next event starts within 60 min (or is
   ongoing): minutes-until countdown in large dot digits + progress ring;
   ongoing shows remaining time.
2. **Charging** — battery % in dot digits with a fill animation frame.
3. **Low battery** (<15%, not charging) — battery outline + %.
4. **Morning glance** (first tick after 06:00) — today's event count.
5. **Default clock** — HH:MM in dot-matrix digits.

Interactions:
- Touch down/up on Glyph Button: temporarily reveal the *next* scene in
  the list (peek), revert on release.
- Long press (`EVENT_CHANGE`): cycle the default scene.

Constraints honored: AOD ticks arrive ~1/min — scenes must render from
cached state (calendar snapshot refreshed opportunistically, battery via
sticky broadcast); no continuous animation in AOD mode; brightness kept
battery-friendly.

## App structure

Single Kotlin app, single APK (`com.amitozalvo.nothingsuite` — TBD):
- `:app` module; Glance widgets + Glyph Toy service + minimal config
  Activity (permission grant, widget preview, toy scene settings).
- min SDK 34 (Phone (3) ships Android 15), target latest.
- Sideload via ADB/Android Studio; Play Store packaging out of scope for v1.

## Open questions (assumptions in place until answered)

1. "Fully featured" — assumed read-only (view/browse/open); in-widget
   event *creation/editing* UI is out of scope for v1 (tap-through to
   Google Calendar instead). Confirm.
2. AOD scene priorities/thresholds above are a first proposal — tune after
   first on-device trial.
3. Week-view widget variant: not planned for v1; say the word if wanted.

## Testing

- Widget: emulator + any Android phone.
- Glyph Toy: requires the physical Phone (3) (no emulator support);
  a small on-screen 25×25 matrix simulator Activity will be included in
  debug builds for rapid iteration before sideloading.
