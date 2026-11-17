# Still → Wellness backend

## Initial setup

Upgrade the backend to the meditation integration image first (Flyway V3 adds scoped permissions). Provision a **separate meditation key** in your own interactive terminal; the secret is printed once. Do not put it in Git, screenshots or chat:

```sh
ssh -t bevan@plenny 'cd ~/docker && docker compose --env-file .env -f public-apps-compose.yml run --rm --no-deps -it wellness-api keys create meditation meditation:write,meditation:read-own,connection:test'
```

Install the update using the same signing identity as your current app. Export history before changing signing identities; Android will reject an update signed differently. Do not uninstall just to bypass that check.

Open Settings → Wellness Sync. Keep `https://wellness-server.bsalter.online`, paste the key and Save connection. Test saved connection checks the meditation identity, permission and supported event schema without uploading history when sync is disabled. Enable background sync and Save connection to upload all saved and future completed practices. “Sync now / retry pending” also releases rejected sessions for another attempt.

## Stored observations

Each `meditation.session.completed` schema-1 event contains the saved session ID, actual saved duration in milliseconds, original completion date and kind (`meditation`, `mindful_timed`, `mindful_open`). The envelope carries the completion instant, original time-zone/offset and uploader installation ID. No start time, mood or statistics are invented.

The existing completed-session table is the durable queue. SQLite V3 adds only per-server acknowledgement/rejection metadata and a history index; upgrades preserve active timers and saved history. Imports trigger backfill. Delivery runs in network-constrained WorkManager jobs, with exponential retry and periodic recovery. A successful acknowledgement must match event ID, status and receipt timestamp. Offline/server failures retain local history; invalid credentials pause until fixed/retried. Rejected payloads remain local and visible in sync counts.

Event IDs derive deterministically from stable session IDs, so retries and portable-history imports do not duplicate backend observations. A different uploading installation is accepted for an otherwise identical meditation event; the backend retains the first uploader's metadata. Changed observation content under the same ID is rejected.

Deleting a session deletes it locally and removes local delivery metadata. It does not delete uploaded observations. A request already in flight may still reach the backend. Re-importing identical history is harmless. There is no download/restore-from-server feature; retain your exported history backups.

## Credentials and privacy

The key is encrypted using Android Keystore AES-GCM. The key field is masked and excluded from saved UI state/autofill; the settings screen blocks screenshots. App backup/extraction stays disabled. Exported history contains no sync configuration or credential. Standard HTTPS trust and hostname verification apply; redirects are not followed and raw server error bodies are not displayed/logged.

## Verification

Automated checks cover version-1/2 database upgrades, completion/test exclusion, imported backfill, stable IDs, per-server delivery, local deletion, offline retries, duplicate acknowledgements, credential rejection, Retry-After, malformed acknowledgements, payload rejection and settings changes during requests. Backend PostgreSQL tests cover all practice kinds, validation, scope isolation and duplicate/conflict behaviour alongside existing alarm tests.

Physical-device acceptance remains: verify Keystore persistence, test the saved connection, upload history, finish a practice offline and reconnect, then confirm a single matching database observation. Do not use the reliability test as an upload test: it intentionally creates no practice history. Android may defer background work under battery restrictions; “Sync now” queues work, it does not promise immediate delivery.
