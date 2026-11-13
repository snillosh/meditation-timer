# Verification — 13 November 2026

Environment: SDK/target 36, Android Gradle Plugin 9.2.0, Gradle 9.6, JDK 21. Pixel 7 Pro AVD running API 37. No physical GrapheneOS device connected.

- Debug APK compiled successfully.
- 20 deterministic JVM unit tests passed.
- 10 Android instrumentation tests passed (real SQLite persistence and coordinator state transitions, including injected-clock recovery and stored local completion dates).
- Android lint completed with zero errors. Remaining warnings concern newer dependency/target versions, application-context singleton detection and optional KTX style suggestions; they are not suppressed globally.
- Light and dark Compose screens visually inspected in the emulator. System-bar icon contrast corrected after inspection.
- Manifest has no INTERNET permission, no wake-lock, DND-policy, camera, microphone, location, accessibility or overlay permissions. Bell service is non-exported and declares mediaPlayback; receiver is non-exported, alarms use explicit immutable PendingIntents. Backup/transfer excludes local state.

## Actual 60-second background smoke test

Used the Settings reliability-test UI with exact-alarm and notification access enabled. Sent Home and Sleep, then `am kill` after the start bell ended. Confirmed no application PID before the deadline and `mWakefulness=Asleep`. Did **not** force-stop. After the endpoint, read the database via `run-as` without opening the activity:

```text
Expected: 2026-11-13T12:50:26.023Z
Callback: 2026-11-13T12:50:26.227Z
Monotonic delay: 210 ms
Completion handler: ran via alarm receiver.
Audio: Playback invoked successfully; notification volume 5.
Active session: none
Completed meditation history rows: 0
Screen: still asleep
```

This establishes background receiver execution and an accepted playback call on this AVD. The emulator was run without host audio. It does not establish a heard bell, physical lock-screen audio behaviour, deep-Doze delivery or GrapheneOS-specific battery policy. Perform [the device checklist](TESTING.md) before relying on it for personal sessions.

## Alarm-audio follow-up

Completion and reliability-test playback now use `USAGE_ALARM`; the start bell retains notification audio and its quiet-mode checks. Completion remains single-play with the existing automatic release and five-second bound. No permission or volume/DND mutation was added. Timer Health reports alarm volume and current DND alarm eligibility (or an unknown status if unavailable), with safe settings shortcuts. A muted alarm-volume warning also appears before starting meditation.

Validation: debug build, 20 JVM tests, 13 emulator instrumentation tests, and lint (zero errors) passed. The three added instrumentation tests verify alarm usage/stream routing, no app-side suppression of completion for silent/vibrate/DND, and unchanged start-bell suppression. These tests do not prove audibility. The background smoke test above predates this audio change; physical silent/vibrate and DND allow/block checks remain required as described in TESTING.md.

## History calendar follow-up

Added a Monday-first monthly grid above the overall statistics, with previous/next navigation, Today, selected/today indicators, daily dots/minute totals and accessible date labels. The selected date filters the sessions below statistics; empty days have an explicit message. Month/date and navigation-tab state survive activity recreation. Existing confirmed deletion recalculates all totals from history. No storage migration or new app permission is needed.

Validation: debug compilation, 23 JVM tests, 15 emulator instrumentation tests and lint (zero errors) passed. Added tests cover Monday alignment, leap days, six-week months, daily aggregation, date selection, month navigation, Today, selected-day session filtering and empty dates. Compose interaction tests explicitly use Espresso 3.7.0 because the transitive 3.5.0 dependency calls a removed hidden input API on the API 37 emulator. Sample data used for visual inspection exists only in the disposable emulator, not in app source or production storage.

## History import/export follow-up

Added system-document-picker JSON export and confirmed merge import in History. The versioned backup includes completed-session IDs, timestamps, durations and original completion dates/zones only. Validation rejects unsupported/malformed/oversized backups before database writes. Imports are transactional: identical records are skipped, conflicting IDs roll back the entire merge, and active-timer IDs cannot be imported. No storage or network permission was added.

Validation: debug build, 28 JVM tests, 17 emulator instrumentation tests and lint (zero errors) passed. New tests cover backup round trips, empty backups, malformed/trailing data, unsupported versions, invalid fields/date-zone relationships, size/count limits, repeat imports after reopening storage, atomic conflict rollback and preservation of an active timer. System document-provider cancellation and unavailable/read-only locations remain on the manual checklist.

## ZenQuotes follow-up

Added foreground-only ZenQuotes cards on Meditation with plain quote/author fields and a clickable provider attribution. Quotes disappear on loss of validated connectivity or request failure, with no offline fallback. Successful responses are reused in memory for one hour, failed attempts throttled for a minute, and network callbacks unregister when the screen stops. HTTPS requests have connect/read timeouts and a 64 KB response bound; no timer/history data is sent. This feature intentionally adds INTERNET and ACCESS_NETWORK_STATE permissions, superseding the original MVP's no-network configuration; core timer/history features remain offline.

Validation: debug compilation, 33 JVM tests, 18 emulator instrumentation tests and lint (zero errors) passed. New tests cover parsing, invalid/rate-limit responses, cache expiry, failure/timeout throttling, expired-response suppression, attribution interaction and complete removal of unavailable quote UI. A live Pixel 7 Pro AVD check displayed the quote/attribution online, then removed the card when airplane mode was enabled. Connectivity was restored afterward. Physical GrapheneOS network controls remain on the device checklist.
