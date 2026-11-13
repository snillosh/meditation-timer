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
