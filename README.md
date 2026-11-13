# Still

A small native Kotlin/Compose meditation timer for Pixel 7 Pro / GrapheneOS. Android 12+ (API 31), compile/target API 36. No accounts, Google Play Services, analytics, or cloud storage. Core meditation features work offline; optional ZenQuotes cards require internet.

## Build and install

Open this folder in Android Studio with SDK 36 and a full JDK 21, or:

```sh
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11" # or your Android Studio bundled JDK
# Set ANDROID_HOME or create local.properties with sdk.dir=/path/to/Android/Sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# With an emulator / disposable test device connected:
./gradlew :app:connectedDebugAndroidTest
```

Initial dependency resolution requires internet on the build machine. All core app features work offline; the quote card stays hidden without internet. Grant **Alarms & reminders** from Settings → Timer Health before starting. Notifications are recommended for session visibility and required for visible daily reminders. Denying notification permission does not block timers or the brief foreground bell service.

## History calendar

History opens with a Monday-first monthly calendar, followed by overall statistics and the selected day's sessions. Active days show a dot and total completed minutes. Tap a date to filter the sessions, use the arrows to browse months, or Today to return to the current day. Empty dates show an explicit empty state. Deletion keeps its confirmation and updates calendar totals and statistics. Calendar dates use each session's stored completion date; no database migration is needed.

## Import and export history

In History, tap **Export** to save a `still-history-YYYY-MM-DD.json` backup through Android's document picker. Tap **Import**, select a backup and confirm to merge it. Identical session IDs/content are skipped, so importing the same backup twice does not duplicate sessions. Conflicting IDs or invalid entries reject the whole import without changing existing history. Calendar, totals and streaks update from the merged records. Import does not sound a bell or alter the active timer.

The version-1 JSON format stores each session's ID, completion timestamp (`completedAt`, Unix milliseconds), duration (`durationMillis`), completion-day date (`localDate`, ISO) and original time zone (`zoneId`). It preserves the saved completion date across devices. Backups contain completed history only, with a `still-history` format identifier and export timestamp. Files are limited to 10 MB / 10,000 sessions. Keep backups somewhere you trust: they are plain text, not encrypted. The app needs no storage permission or network permission; the system picker handles the location you choose. Canceling a picker or import confirmation leaves history unchanged.

## Inspirational quotes

The meditation screen displays a quote and author from [ZenQuotes](https://zenquotes.io/), with a linked attribution. No API key is needed. The app requests a random quote over HTTPS only while that screen is visible and Android reports validated internet. It shows nothing when offline, behind an unvalidated captive portal, or when the request fails. Losing connectivity removes any displayed quote; there is no offline fallback or stored quote history.

Successful responses are kept in memory for up to an hour to limit requests. Failed attempts are throttled for a minute; there is no polling or scheduled retry. Returning to the screen or reconnecting can trigger another attempt. Connect/read timeouts are three seconds each, responses are bounded, redirects/cleartext are disabled, and only plain quote/author fields are rendered. Quote loading never blocks the UI or controls timer completion. The app declares INTERNET and ACCESS_NETWORK_STATE for this feature. Requests send no meditation history or app identifiers; ZenQuotes receives normal connection information such as the device's public IP address. Tapping attribution opens your browser if available. [Provider documentation and free-tier terms](https://docs.zenquotes.io/zenquotes-documentation/).

## Architecture

- `Model.kt`: pure monotonic countdown/state calculations, statistics and local-time reminder calculation.
- `TimerEngine.kt`: one application-scoped, serialized timer coordinator. A persisted session ID and revision reject stale callbacks after pause, resume and cancellation. All transitions use this coordinator. No UI delay performs completion.
- `Storage.kt`: versioned SQLite database for active state and history; DataStore preferences. SQLite is used directly to keep the small atomic active-session/history transaction easy to inspect, without a Room code-generation dependency. The session primary key and conditional transaction prevent duplicate history entries. Statistics derive from history; nothing increments redundant counters.
- `Background.kt`: alarm receivers, inexact reminder scheduling and short media-playback foreground service. A mutex serializes reminder scheduling/delivery. One date marker suppresses repeat reminders on the same local day.
- `ZenQuotes.kt` / `QuoteCard.kt`: foreground-only quote requests, connectivity handling and attributed quote display.
- `HistoryCalendar.kt`: accessible monthly date grid, daily totals and date selection.
- `MainActivity.kt`: Compose Material 3 screens and ViewModel. Foreground-only refreshes render persisted state; rendering does not keep the timer alive. No screen wake lock, background polling, or always-running service.

### Locked-screen completion

A user-started timer schedules **one** `ELAPSED_REALTIME_WAKEUP` alarm using `setExactAndAllowWhileIdle` and a receiver `PendingIntent`. This is justified by the timer's core promise: an audible endpoint at a user-selected time, including Doze. The app uses user-controlled `SCHEDULE_EXACT_ALARM` special access, not automatically granted `USE_EXACT_ALARM`. Start/resume is blocked without access; no misleading inexact substitute is used for meditation.

The receiver validates the session, revision, boot identity and elapsed deadline, then atomically writes completion and removes the active session. It starts a short `mediaPlayback` foreground service solely to play the bell. Exact alarms for user-requested actions permit this background foreground-service start. The service immediately posts its required silent notification, plays once, then releases its player and stops; a five-second bound also stops playback. It is not used to keep a silent timer alive.

The optional ongoing timer notification is separate from completion scheduling. Daily reminders use one self-rescheduling **inexact** `setAndAllowWhileIdle` RTC alarm, recalculated for local wall time after delivery, reboot, clock changes and time-zone changes. Reminders never need exact-alarm access.

Platform references: [alarms and special access](https://developer.android.com/develop/background-work/services/alarms), [background foreground-service exceptions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [media-playback service type](https://developer.android.com/develop/background-work/services/fgs/service-types#media).

### Recovery and limitations

- Activity recreation and ordinary process death retain state. A receiver recreates the coordinator without the UI. Reopening also reconciles an elapsed deadline using persisted monotonic evidence from the same boot; diagnostics identify recovery versus alarm-receiver execution.
- Reboot invalidates monotonic evidence. Running **and paused** unfinished sessions are discarded, never credited. Completed history and preferences survive. Boot identity must be available to start a session. Backups/device transfer are disabled to avoid restoring stale active state on another device.
- Exact-alarm access revocation can terminate the process and cancel alarms. On next launch the app pauses an affected session and explains why. Regranting access reconciles persisted state. Android force-stop disables delivery until the app is reopened; no app can override force-stop or a powered-off phone.
- Doze exact alarms have per-app frequency limits. Repeated short tests or very closely spaced sessions can deliver late. Battery restriction, OS policy, device faults or vendor changes can also delay callbacks. This is not an emergency alarm.
- Completion is saved before attempting audio. A crash in between can leave a completed record without a bell. Recovery does not replay bells for completed records; exactly-once audible output across process crashes cannot be guaranteed.
- Completion and reliability-test bells play once using `USAGE_ALARM` and system alarm volume, including in silent/vibrate mode. Android applies DND alarm exceptions: allow alarms in the DND modes you use. The app never changes volume, bypasses DND or requests notification-policy access. Zero alarm volume still silences completion; the meditation screen warns when muted and Timer Health links to sound/DND settings. The start bell retains notification audio and skips silent/vibrate or DND. System routing and policy may affect what is heard. A successful playback call proves invocation, not audibility. Notification channels control notification visibility; the bell itself uses the audio API.
- Battery diagnostics report a supported background-restriction flag and optimisation exemption only. Android does not expose the precise three-way battery mode reliably. An exemption is never labelled as verified Unrestricted.
- Session dates and zone IDs are captured **when completion is recorded**, including delayed callback/recovery. Midnight attribution uses that local completion date. Changing time zone later does not rewrite history dates. Seven-day totals use those recorded dates; today/yesterday streak evaluation uses the current local date.
- Settings links catch failures and fall back to app details. Some GrapheneOS versions require navigating from app details into battery settings.

## Bells

`app/src/main/res/raw/start_bell.wav` and `completion_bell.wav` are original, quiet synthesized two-second placeholders, dedicated under CC0. No external audio was downloaded. Replace either file with your local bell, retaining its resource base name; update `R.raw` references if changing the base name. Android-supported WAV/OGG/MP3 files work. Keep sounds short (under five seconds), because the service intentionally bounds playback. Test alarm volume, silent/vibrate and DND alarm exceptions on the device after replacing sounds. Completion remains single-play and bounded to five seconds; there is no repeat-until-dismissed alarm.

## Validation

Deterministic unit tests cover countdown, pause/resume, paused-time exclusion, completion eligibility, stale callbacks, boot mismatch, persisted pause recovery, statistics, streak gaps and duplicates, deletion, seven-day windows, midnight/time-zone attribution, and reminder DST/local-time scheduling.

Android instrumentation tests exercise actual SQLite persistence/transactions and coordinator completion/cancellation/recovery with an injected clock. Instrumentation tests clear the app database: use an emulator or disposable test installation, not your personal meditation history.

See [GrapheneOS manual checklist](docs/TESTING.md) and [original requirements](docs/BRIEF.md). Physical GrapheneOS locked-screen playback has **not** been verified by this project build.

## GitHub Actions

Pushes and pull requests to `main` run unit tests, lint and a debug APK build. You can also run the workflow manually. Open [Actions](https://github.com/snillosh/meditation-timer/actions), select a successful run, and download its APK artifact from the summary; extract `app-debug.apk` to install. APKs are retained for 30 days and reports for 14 days. Emulator tests remain a local check. CI caches its debug signing key; if that cache expires, installing a newly signed APK over an earlier build may require uninstalling first (which deletes local history). Local and CI debug signing keys also differ.
