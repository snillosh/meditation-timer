# Repository instructions

- Read [HANDOFF.md](HANDOFF.md), README.md and WELLNESS_SYNC.md before changes.
- Preserve native Kotlin/Compose UI and offline timer/history behaviour. Network
  availability must not control timer completion or persistence.
- Use the Gradle wrapper and the checks in HANDOFF.md; use disposable emulators for
  instrumentation. Do not install over the user's app without authorisation.
- Preserve database upgrades, stable history/event IDs and original time context.
  Never commit or log credentials or change Android backup/signing policy casually.
- Inspect Git status and worktrees before editing; preserve unrelated local work.
- Update HANDOFF.md after substantive changes with exact verification outcomes,
  unresolved work and the next concrete step. Do not claim deployment/device
  acceptance based only on source, tests or CI artifacts.
