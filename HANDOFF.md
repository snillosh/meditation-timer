# Agent handoff

Documentation baseline: 18 November 2026. Read this after `AGENTS.md`. Commit IDs
below identify inspected application code, not the later documentation commit.

## Current checkout and code map

Baseline `7185390` on `main`: opt-in offline-first Wellness Sync after configurable
Mindful Minutes, duration presets, Timer Health, calendar and history portability.
An existing untracked `gradle/gradle-daemon-jvm.properties` was present before this
documentation task; preserve it and inspect it before deciding whether it belongs
in a future change.

Production Kotlin: `app/src/main/java/uk/co/meditationtimer/`.

- `TimerEngine.kt`, `Model.kt`: serialized timer transitions, monotonic calculations,
  completion/revision checks. Completion must not depend on a UI coroutine delay.
- `Storage.kt`: SQLite active/history transaction, schema V3 sync metadata and
  DataStore settings. Preserve upgrades from versions 1 and 2.
- `Background.kt`: exact timer callback, reminder receivers and bounded bell service.
- `MindfulModel.kt`, `MindfulReminders.kt`, `MindfulPreferences.kt`,
  `MindfulScreens.kt`: opt-in nudges and timed/open-ended/prompt activities.
- `HistoryBackup.kt`, `HistoryCalendar.kt`: portable history and original local dates.
- `WellnessSession.kt`, `WellnessSync.kt`, `WellnessSyncWorker.kt`: event mapping,
  delivery policy and WorkManager integration; history itself is the durable queue.
- `WellnessSettings.kt`, `WellnessCredential.kt`, `WellnessHttpClient.kt`,
  `WellnessConnection.kt`, `WellnessSyncScreen.kt`: saved server, Keystore key,
  bounded HTTPS, connection checks and settings UI.
- `MainActivity.kt`, `StillApplication.kt`: UI/coordinator and application wiring.

## Build and verification

Use JDK 21 as in CI, Android SDK platform 36/build-tools 36.0.0 and the Gradle
wrapper. Set `ANDROID_HOME` or a local `local.properties` SDK path.

```sh
./gradlew --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug
# Only with an emulator/disposable test installation:
./gradlew :app:connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. JVM/Robolectric tests are under
`app/src/test`; instrumentation tests under `app/src/androidTest`. See
`docs/TESTING.md`, `docs/VERIFICATION.md` and `WELLNESS_SYNC.md` for device checks.
CI uses `.github/workflows/android.yml`; it publishes debug APK artifacts, not a
production install. A different debug signing key cannot update an installed app.
Export history before any authorised signing/install change; do not uninstall to
work around signature mismatch.

## Continue here

Preserve exact-alarm access checks, same-boot monotonic evidence and stale-revision
rejection. Unfinished running/paused sessions are discarded after reboot, never
credited. History is committed before a bounded completion bell; recovery does not
replay bells. Open-ended pauses exclude paused time. Prompt-only and Timer Health
reliability tests never create practice observations.

Read `WELLNESS_SYNC.md` before changing retention, imported history, server-scoped
acknowledgements or deterministic event IDs. No server-to-phone restore exists.
Physical GrapheneOS locked-screen/audio and Wellness Sync acceptance remain to be
verified; historical automated results do not prove audibility or device delivery.

## System map

These are four independent Git repositories, normally checked out as siblings:

| Repository | Owns | Integrates through |
| --- | --- | --- |
| wellness-dashboard | React/TypeScript browser UI, static Caddy container | Same-origin `/dashboard-api/v1` proxy to backend |
| wellness-backend | Java API, authentication, PostgreSQL, Flyway, aggregates | Scoped Android bearer API and separate browser session API |
| meditation-timer | Still Android app, local meditation/Mindful Minutes history | Optional upload as application `meditation` |
| barcode-alarm | Native Android alarms, barcode timers, local completion capture | Optional upload as application `barcode-alarm` |

Android apps remain useful offline. Local completion is recorded first; WorkManager
uploads later over HTTPS to `https://wellness-server.bsalter.online`. PostgreSQL
is the server observation store; the browser never connects to it directly.
The dashboard's configured public address is `https://wellness.bsalter.online`.
These addresses describe configuration, not a verified live deployment.

`ServerConfiguration` is a separate infrastructure repo owning production image
pins and home-server proxy/Compose configuration. Publishing an image or APK does
not deploy it. Do not change that repo, the server, DNS, or the user's phone as an
incidental part of an application change.

## Contracts that must survive a handoff

- Android: `POST /api/v1/events`, `GET /api/v1/events/{eventId}` and
  `GET /api/v1/connection`. Keys are application-scoped. Alarm keys use
  `alarm:write`, `alarm:read-own`, `connection:test`; meditation keys use
  `meditation:write`, `meditation:read-own`, `connection:test`. Provision locally
  using backend commands, never a public admin endpoint. Keep secrets out of docs,
  chat, source, command arguments and logs.
- Events retain stable IDs across retries, original occurrence instant and available
  zone/offset context. An accepted upload returns 201; an identical retry returns
  200 with the original receipt timestamp; changed immutable content returns 409.
  Do not regenerate IDs to hide a conflict.
- Schema-1 `alarm.occurrence.completed` represents final alarm completion, including
  verification when configured. Barcode timers, snoozes, intermediate scans and
  deletion are excluded. Completion is a wake-up proxy, not measured waking time.
- Schema-1 `meditation.session.completed` supports `meditation`, `mindful_timed`,
  `mindful_open`. Stable saved session IDs determine event UUIDs. Identical imported
  history can retry from a different installation; backend keeps the first uploader.
  Reliability tests, prompt-only nudges and discarded sessions are excluded.
  Local history deletion does not remove uploaded observations.
- Backend also supports `phone.power.connected`, `phone.power.disconnected`,
  `sleep.bedtime.estimated` for Barcode Alarm, using existing alarm scopes.
  Night windows are 20:00–03:00 in the original IANA zone. Estimates retain their
  named night and algorithm version; raw observations remain immutable. Check the
  actual Android branch before assuming capture exists there.
- Browser accounts share one personal dataset. Cookie auth is independent of app
  keys: Secure, HttpOnly, host-only, SameSite=Strict, fixed one-day sessions.
  Login checks exact configured Origin; logout also checks the session CSRF token.
  Never put passwords, session tokens or Android keys in browser storage.
- Overview uses seven calendar days including today in the requested browser zone,
  independently of event-list pagination. Meditation minutes exclude Mindful Minutes;
  practice count/days include all practice kinds. Earliest daily alarm completion is
  the wake proxy; latest bedtime estimate is grouped by payload night. Sleep charts
  default to explicitly labelled demo times; demo observations are never uploaded.

Detailed contracts live in backend `docs/API.md`, `MEDITATION.md`, `CHARGING.md`,
`DASHBOARD.md` and the source/tests. Older phase records are historical evidence,
not a statement of current checkout or deployment state.

## Start and finish each agent session

1. Read `AGENTS.md`, this file and the relevant feature document. Inspect
   `git status --short`, `git branch -vv`, `git log -8 --oneline` and
   `git worktree list` in every affected repo before editing. Preserve unrelated work.
2. Establish the requested outcome and compare local code with relevant sibling
   contracts. Cached `origin/*` refs are not proof of current remote state; fetch
   when needed. Do not reset, switch or merge another agent's worktree casually.
3. Make a focused change and run the checks below. Use disposable databases/emulators;
   never use personal observations or the user's phone as automated test fixtures.
4. Update this handoff with the resulting commit/branch, exact commands and outcomes,
   blockers and the next concrete step. Separate implemented, tested, published,
   deployed and physically accepted states. Do not copy old test counts as new results.
5. Follow this repo's Git working agreement. Never force-push. Record actual blockers
   and leave unrelated files intact. A successful build is not deployment acceptance.

## Cross-repository continuation as of 18 November 2026

The next recorded integration review is real-device end-to-end acceptance (backend
Phase 6). Existing docs do not establish that it happened. With explicit device/server
authorisation, verify same-signature app upgrades, offline capture/restart/reconnect,
exactly one backend observation, browser login and live aggregates, and unchanged
alarm/timer delivery with sync disabled or failing. Use each app's physical checklist.
Charging additionally requires its Android implementation and compatible backend
image. Verify actual CI digests, deployed versions and installed signatures before
claiming this system is running the latest code.

## Documentation verification

This handoff was checked against local source, build configuration, CI workflows,
existing feature documents and Git refs. No application tests, live server checks
or physical-device acceptance were performed for this documentation-only change.
