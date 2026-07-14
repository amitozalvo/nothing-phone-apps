# Glimpse — Phone (3)

**[⬇ Download the latest APK](https://github.com/amitozalvo/nothing-phone-apps/releases/latest)** — every push to `main` builds and publishes a release automatically.

> Sideload note: Play Protect may warn because the app requests notification
> access (needed for OTP/media features). Expand the dialog and choose
> "Install anyway", or install via `adb install`.

Two things in one sideloadable app:

1. **Calendar widget** — wide, resizable home screen widget in the Nothing
   design language: dot-matrix date header (weekday white, day in Nothing
   red), scrollable upcoming events from all visible Google Calendar
   calendars. Tap an event to open it, "+" to create one, anywhere else to
   open the calendar app.
2. **Context AOD Glyph Toy** — a context-aware always-on display for the
   rear 25×25 Glyph Matrix. Shows the first *active* scene in your
   configured priority order, interrupted by transient alerts.

See [REQUIREMENTS.md](REQUIREMENTS.md) for the full spec.

## Scenes (sortable in-app)

| Scene | Active when | Shows |
|---|---|---|
| Next event | within lead time (default 30 min) or ongoing | countdown, title marquee, progress bar |
| Upcoming alarm | within window (default 30 min) | alarm icon + time |
| Now playing | media session playing | equalizer + track marquee |
| Ambient board | always (fallback) | time, date, events-today + notification counters |

**Alerts (temporary takeovers):** OTP codes from configured messaging apps
(dismiss with the Glyph Button), charging %, low-battery warnings.
Battery state otherwise only appears as tiny corner indicators, so it never
outranks calendar/OTP content.

## Building

Requirements: JDK 17+, Android SDK (platform 35). The Glyph Matrix SDK
(`app/libs/glyph-matrix-sdk-2.0.aar`) is vendored from Nothing's official
[GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit tests (scene engine, OTP parser, renderer)
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and press Run with your Phone (3)
connected.

## Setup on the phone

1. Open **Glimpse** and grant **Calendar access** and
   **Notification access** (one grant powers OTP detection, notification
   counts and media info — no SMS permission needed).
2. Add the **Calendar** widget to your home screen (wide sizes look best).
3. **Settings → Glyph Interface → Glyph Toys** — enable **Glimpse**,
   and select it as the always-on Glyph Toy.
4. In the app: reorder scenes, set lead times, pick OTP source apps and
   monitored apps. The **Matrix preview** section shows each scene exactly
   as the LED matrix will render it.

## Glyph Button

- **Alarm ringing** — press to snooze (fires the alarm notification's snooze action).
- **Alert showing (OTP, battery)** — press to dismiss.
- **On the ambient board** — press cycles detail views: events today → monitored notifications → back.
- **On the next-event ring** — hold to see the event title scroll.
- **On now playing** — press toggles play/pause.
- **Long press** — force-refresh all context data.

## Project layout

```
app/src/main/java/com/amitozalvo/nothingsuite/
├── calendar/        CalendarProvider queries (Instances table)
├── widget/          Glance widget, dot-matrix header, refresh jobs
├── glyph/           MatrixBuffer, DotFont, TextRaster, toy service
│   └── scenes/      SceneEngine + scenes + toasts (pure Kotlin, tested)
├── notifications/   NotificationListener: OTP, counts, media
├── state/           ContextSnapshot + in-process StateStore
├── config/          GlyphSettings + DataStore repository
└── ui/              Compose settings screen + 25×25 matrix simulator
```
