# Codex Task: Build a Minimal Native Android Meditation App (MVP)

## 1. Project overview

Create a new, standalone Android meditation timer application written in Kotlin.

The primary target device is a **Google Pixel 7 Pro running GrapheneOS**, using a modern Android version.

This is a personal, offline-first meditation application. It should be simple, elegant, reliable and easy to extend in future development tasks.

The app is not intended to compete with commercial meditation platforms. It does not need guided meditation content, accounts, subscriptions or cloud services.

**The MVP must include:**

1. A simple meditation timer.
2. Configurable session durations.
3. Gentle start and completion bells.
4. Pause, resume and early termination.
5. Reliable operation while the screen is locked.
6. Meditation session history.
7. Basic statistics and daily streaks.
8. Optional daily meditation reminders.
9. A Settings screen with Timer Health diagnostics.
10. A simple timer reliability test.

Build the application completely, rather than providing only a plan or architectural outline.

## 2. Technology and project structure

Use:

- Kotlin.
- Jetpack Compose with Material 3.
- Modern AndroidX libraries.
- Room for persistent session history, if appropriate.
- DataStore for preferences.
- A simple, maintainable architecture such as MVVM.
- Gradle Kotlin DSL.

Choose a sensible application ID, such as `uk.co.meditationtimer`, unless an existing project configuration specifies otherwise.

Use a modern, stable Android SDK and dependency versions compatible with the installed development environment.

The application must work fully offline.

Do not request the INTERNET permission.

Do not introduce dependency injection frameworks unless genuinely necessary.

Keep the code understandable and avoid unnecessary abstraction.

Organise the project so that additional features can be implemented later without major restructuring.

## 3. Visual design

Create a polished, minimal interface.

Design principles:

- Calm and uncluttered.
- Dark-theme friendly.
- Material 3 styling.
- Soft, muted accent colours.
- Large, readable typography.
- Generous spacing.
- Subtle animations only.
- No distracting graphics or excessive decoration.

Support system dark and light themes, with dark mode looking particularly good on an OLED display.

Use straightforward navigation between:

- Meditation
- History / Statistics
- Settings

Avoid introducing unnecessary screens.

## 4. Meditation timer

### Duration selection

Provide preset durations:

- 5 minutes
- 10 minutes
- 15 minutes
- 20 minutes
- 30 minutes

Also allow a custom duration from 1 to 120 minutes.

Remember the user's most recently selected duration.

### Main screen

Display:

- Selected or remaining duration.
- Large central countdown.
- Subtle circular progress indicator.
- Start button.
- Pause/resume controls while running.
- End Session control.

Keep the UI extremely simple.

### Timer behaviour

The timer must:

1. Count down accurately.
2. Continue when the screen turns off.
3. Continue when the device locks.
4. Continue when the user switches to another app.
5. Survive normal activity recreation and process lifecycle changes as far as supported by the chosen Android architecture.
6. Avoid relying on continuous UI updates for timekeeping.
7. Use a monotonic time source, such as `SystemClock.elapsedRealtime()`, for active elapsed-time calculations.
8. Store enough session state to recover safely.
9. Avoid creating duplicate session completions.
10. Avoid unnecessary background polling.

The user should be able to let the phone lock normally during meditation.

Do not keep the screen awake by default.

Do not request wake-lock permissions unless the chosen implementation genuinely needs them.

### Pause and resume

When paused:

- The countdown stops.
- No meditation time accumulates.
- The session can resume from the remaining duration.
- Paused state is preserved across activity recreation.
- Any pending completion trigger must be cancelled or updated appropriately.

When resumed:

- Continue from the remaining duration.
- Recalculate the expected completion time.
- Ensure only one completion event can occur.

### Ending early

Provide an End Session action.

Ask for confirmation before ending an incomplete session.

An incomplete session:

- Must not count towards completed-session statistics.
- Must not count towards streaks.
- Must not be saved as a completed meditation.

For the MVP, it is acceptable to discard incomplete sessions rather than maintain a separate abandoned-session history.

## 5. Session completion

When the full meditation duration elapses:

1. Mark the session completed.
2. Persist the completed session exactly once.
3. Play one gentle completion bell, subject to Android audio settings.
4. Stop the bell automatically.
5. Update history and statistics.
6. Show a simple completion screen or message when the user returns to the app.

Completion must not depend on the meditation screen remaining open.

Use a persistent session identifier and idempotent completion logic to prevent duplicate records if multiple callbacks or lifecycle events occur.

A session should count as completed only when the full required active duration has elapsed.

Paused time must not count.

If the process is killed or the device reboots, recover conservatively. Do not fabricate completed sessions or award streak credit without sufficient persisted evidence.

Document any limitations of recovery after a reboot.

## 6. Bell sounds

Provide:

- A start bell when meditation begins.
- An end bell when the session finishes.

Each should play once and stop.

Do not loop the sound.

Use Android's appropriate audio APIs and audio attributes for a gentle meditation experience.

The bells should respect the device's silent/vibrate and applicable volume settings rather than forcibly overriding them.

Do not automatically increase the device volume.

Do not bypass Do Not Disturb.

Do not request notification policy access.

### Audio resources

I will provide the final bell sound files separately.

Structure the application so that start and completion sounds can easily be replaced with local audio resources.

For now:

- Include simple, legally usable placeholder sounds if available.
- Otherwise provide a modest built-in fallback tone.
- Do not download copyrighted audio automatically.
- Do not make network access necessary.

Add a way to test the bell sound from Settings.

Ensure audio players and resources are released correctly.

## 7. Background operation and reliability

This is one of the most important requirements.

Investigate and implement the simplest supported Android architecture that can reliably deliver a short completion bell while the screen is locked.

Consider Android's current restrictions on:

- Background execution.
- Doze.
- Battery optimisation.
- Foreground services.
- Exact alarms.
- Notification permissions.
- Audio playback from the background.
- GrapheneOS compatibility.

Do not assume a simple coroutine delay or `CountDownTimer` will reliably execute in the background after process death.

Do not assume a notification alone guarantees that the bell will sound at the intended time.

Choose a supported mechanism and explain the trade-offs.

A foreground service may be appropriate during an active user-initiated meditation session, but only if it is genuinely needed and uses a valid foreground-service type, lifecycle and notification under the target Android version.

If using exact alarms, justify the need, choose the appropriate Android API, and handle exact-alarm special access correctly. Do not request `USE_EXACT_ALARM` or `SCHEDULE_EXACT_ALARM` without a justified and supported use case.

Avoid combining multiple competing scheduling mechanisms unnecessarily.

**The intended result is a timer that continues accurately and completes reliably while locked, without hacks or excessive permissions.**

If Android imposes unavoidable limitations, document them clearly rather than pretending they do not exist.

## 8. Active session notification

During an active meditation session, provide an appropriate, unobtrusive notification if required by the chosen architecture.

It should display:

- Meditation in progress.
- Remaining duration or expected completion time.
- An action to return to the app.

If practical, provide pause/resume controls through the notification, but do not compromise reliability or greatly increase implementation complexity for this.

Notification state should remain consistent with the actual session.

Remove or update the notification when the session ends.

Respect notification permissions and Android's foreground-service requirements.

## 9. History

Create a History screen listing completed meditation sessions.

Each entry should show:

- Date.
- Completion time.
- Duration.

Display newest sessions first.

Store the data locally and persist it across app restarts.

Allow deletion of individual history entries with confirmation.

Deleting an entry must recalculate statistics and streaks correctly.

Do not include unfinished sessions in the completed history.

Use a sensible database structure that can be extended later.

## 10. Statistics

Display:

- Total completed sessions.
- Total meditation minutes.
- Current daily streak.
- Longest daily streak.
- Total minutes meditated over the last seven days.
- A simple weekly activity chart.

All statistics must be derived from stored completed sessions.

Do not store redundant counters that can become inconsistent with history.

### Daily streak rules

A qualifying day is a local calendar day containing at least one completed meditation session.

Multiple completed sessions on the same day count as one streak day.

The current streak should remain intact during today if the user completed a meditation yesterday.

If the user completes a meditation today, include today in the streak.

If the most recent qualifying day is older than yesterday, the current streak is zero.

Use the local calendar date associated with the completed session.

Store enough date/time information to avoid incorrect streak calculations after time-zone changes.

Handle daylight saving time and local date boundaries correctly.

For sessions spanning midnight, attribute the session to the local date on which it completed.

## 11. Optional daily reminders

Provide a simple reminder setting.

Requirements:

- Disabled by default.
- User can enable or disable reminders.
- User can select a reminder time.
- Show a notification encouraging the user to meditate.
- Tapping the notification opens the app.
- Respect notification permission.
- Restore reminder scheduling after reboot where appropriate.
- Handle time-zone changes.
- Prevent duplicate reminders.

Reminders are not emergency alarms.

A small delivery delay due to Android battery management is acceptable.

Prefer Android's normal background scheduling mechanisms.

Do not require exact alarm access solely for daily reminders.

Do not wake the screen or launch a full-screen activity for reminders.

## 12. Settings screen

Create a Settings screen with:

### Meditation preferences

- Default meditation duration.
- Daily reminder toggle.
- Daily reminder time.
- Bell sound test.

### Timer Health

Include a dedicated **Timer Health** section.

This should inspect relevant Android permissions, settings and application resources.

Each item should show:

- A clear status.
- A brief explanation.
- Whether it is required, recommended or informational.
- An action to open the appropriate Android settings screen where possible.

### Health checks

Implement the following where applicable:

**Notifications**

Check whether notification permission is granted.

If not granted, offer the appropriate runtime permission request or Android app settings shortcut.

**Notification channels**

Check whether relevant notification channels are enabled.

If disabled, offer a shortcut to the corresponding notification channel settings.

**Battery optimisation**

Determine whether the app is subject to normal battery optimisation or has an exemption, using supported Android APIs.

Explain the difference between:

- Restricted
- Optimised
- Unrestricted

Where Android does not expose the precise user-selected battery mode, do not invent a status.

Recommend Unrestricted battery behaviour when appropriate for reliable background completion.

Offer a shortcut to the relevant Android battery settings.

Do not automatically change battery settings.

Do not falsely report that the app has Unrestricted battery access when it cannot verify that state.

**Exact alarm access**

Only include an actionable exact-alarm permission check if the chosen timer architecture actually uses exact alarms.

If needed, check access using supported APIs such as `canScheduleExactAlarms()`.

Offer the relevant Android settings shortcut if access is missing.

Do not request exact alarm permissions merely to populate this health screen.

**Foreground service**

If the architecture uses a foreground service, report whether the app is configured to use it correctly.

Do not invent a user-grantable foreground-service permission screen where none exists.

**Bell audio**

Check that the required audio resource exists and can be loaded.

Provide a Test Bell action.

**Reminder status**

Show whether reminders are enabled, their scheduled time and any notification-related problems.

**Active session**

Show whether a session is active or paused and its expected completion time, if applicable.

### Status presentation

Use simple indicators:

- Ready
- Needs attention
- Recommended
- Not applicable

Do not present every optional recommendation as a critical error.

Refresh health checks when the screen opens and when the app returns from Android Settings.

Use safe Intent handling and fallbacks for settings pages that are unavailable on a particular Android version or GrapheneOS build.

Never assume an Intent is supported without checking or handling failure.

Do not ask for unnecessary permissions.

Specifically, the application should not need camera, microphone, location, contacts, accessibility or overlay permissions.

## 13. Timer reliability test

Add a **Test Timer Reliability** option under Timer Health.

This should allow the user to:

1. Start a 60-second test timer.
2. Lock the phone.
3. Wait for the timer to complete.
4. Hear a single test completion bell.
5. Return to the app and inspect the test result.

The test must use the same underlying background completion mechanism as real meditation sessions.

Test sessions must not count towards meditation history, statistics or streaks.

Record useful diagnostic information locally, including:

- Expected completion time.
- Actual completion callback time, when observable.
- Whether the completion handler ran.
- Whether audio playback was attempted successfully.
- Any relevant scheduling or permission errors.

Distinguish between what the app can verify and what it cannot.

For example, successfully invoking an audio API does not prove that the user actually heard the bell.

Do not claim the test passed merely because the UI countdown reached zero.

Keep the test simple and avoid creating an elaborate diagnostics framework.

## 14. Data persistence

Use appropriate local storage for:

- Completed meditation sessions.
- Current active session state.
- Selected duration.
- Reminder preferences.
- Relevant timer recovery metadata.

Persist enough information to restore a session after normal activity recreation and supported process recovery.

Use a consistent session state model, such as:

- Idle
- Running
- Paused
- Completed

Make state transitions explicit.

Prevent races between pause, resume, early termination and automatic completion.

Ensure that a cancelled session cannot later complete due to a stale scheduled callback.

Ensure that a completed session cannot be inserted into history twice.

## 15. Android compatibility

The primary test environment is GrapheneOS on a Pixel 7 Pro.

Use standard Android APIs.

Do not depend on Google Play Services.

Do not require a Google account.

Do not depend on proprietary Google frameworks.

Avoid background activity launches, screen wake-up tricks, accessibility services and overlay permissions.

Use the system's normal lock-screen and battery-management behaviour.

Target a modern Android version and ensure the app's permission declarations, foreground-service usage and background execution behaviour comply with that version.

The app should remain functional without network connectivity.

## 16. Testing

Add unit tests for:

- Countdown calculations.
- Pause/resume behaviour.
- Exclusion of paused time.
- Session completion.
- Early termination.
- Duplicate completion prevention.
- Recovery from persisted state.
- Stale callback handling.
- History persistence.
- Statistics calculations.
- Current streak.
- Longest streak.
- Multiple sessions on one day.
- Missed streak days.
- Midnight boundaries.
- Time-zone changes.
- Reminder scheduling logic.

Add appropriate integration or instrumentation tests for Android-specific behaviour where feasible.

Use injectable clock abstractions or controllable time sources for deterministic tests.

Ensure that background timer tests do not rely solely on Compose UI rendering.

### Manual test checklist

Document how to test on the actual GrapheneOS device:

1. Normal five-minute meditation.
2. Lock screen during meditation.
3. Switch to another app.
4. Pause and resume.
5. End a session early.
6. Complete a session while the app is backgrounded.
7. Complete a session with the screen locked.
8. Test with normal battery optimisation.
9. Test with Unrestricted battery mode.
10. Test with notification permission denied.
11. Test with silent/vibrate mode.
12. Test daily reminders.
13. Reboot and inspect recovery behaviour.
14. Confirm history and streaks remain correct.

Clearly identify tests that require physical-device verification.

## 17. Build and delivery

Implement the complete Android Studio project.

Then:

1. Run Gradle compilation.
2. Run unit tests.
3. Fix compilation errors.
4. Fix failing tests.
5. Check AndroidManifest permissions and component declarations.
6. Check notification channel configuration.
7. Verify that the project does not require internet access.
8. Provide a concise summary of the architecture.
9. Explain any unavoidable Android background-execution limitations.
10. Provide instructions for replacing the placeholder bell sounds.
11. Provide the manual GrapheneOS testing checklist.

Do not claim that locked-screen behaviour has been verified on the physical device unless it has actually been tested.

## 18. Explicit non-goals

Do not implement:

- Guided meditations.
- Background music.
- Nature sounds.
- Breathing exercises.
- User accounts.
- Cloud synchronisation.
- Subscription features.
- Social features.
- Achievements or gamification.
- AI features.
- Wearable integrations.
- Home-screen widgets.
- Complicated analytics dashboards.
- Automatic screen wake-up.
- Full-screen alarm interfaces.

Do not add unnecessary features just because they are common in meditation apps.

## Final priority order

Prioritise the work in this order:

1. Correct and reliable timer behaviour.
2. Correct session persistence and completion.
3. Reliable, gentle bell playback.
4. Timer Health diagnostics and the reliability test.
5. Accurate history, statistics and streaks.
6. Daily reminders.
7. Visual polish.

If a requirement conflicts with Android platform restrictions, use a supported implementation and clearly explain the limitation.

**Build a complete, simple, reliable meditation MVP that can be extended through future Codex prompts without unnecessary complexity.**