# Phase 8 — Health Connect integration

Status: In progress on `feature/phase8-1-health-connect-foundation`. Phase 7.2 (Delete Habit, #16) and Phase 7.3 (full regression) are intentionally deferred, not cancelled.

## Guardrails
- Keep HabitFlow free, local-first, and functional without Health Connect.
- Health Connect is opt-in. Do not request permissions on app launch.
- Request only the read permissions needed for a user-selected habit/activity mapping.
- Do not upload health records, use analytics on health values, or require an account.
- Treat availability, installed-provider status, permissions, and read failures as distinct states.
- Manual habit completions always remain possible; never silently overwrite them.
- Imported records must be idempotent (deduplicate by source record ID and mapping), and a revoked permission must not crash the app.
- Verify Android SDK compatibility and official Health Connect setup requirements before adding the dependency or manifest declarations.

## Delivery sequence
### 8.1 — Foundation
- [x] Add the AndroidX Health Connect client dependency and required manifest/package-visibility declarations, verified against supported SDK versions. Compile SDK 36, AGP 8.9.2, Gradle 8.11.1; runtime SDK gate keeps minSdk 21.
- [ ] Create an injectable gateway for availability, permissions, and record reads (availability and permissions implemented; record reads deferred to 8.2).
- [x] Build a Settings entry and opt-in setup screen with availability/permission/error states, single read-only exercise permission, and rationale activity.
- [ ] Add unit tests for availability and permission transitions.
- [ ] Validate Android build, emulator smoke tests (including targeted Health Connect settings/rationale navigation), and supported-device behavior. Physical-device permission grant/deny/revoke remains pending.

### 8.2 — Mapping and automatic completion
- [ ] Let users select a habit and a supported activity type (initially walking/running/cycling).
- [ ] Define explicit daily matching rules (date/time zone, duration threshold).
- [ ] Persist mapping and source-record provenance locally.
- [ ] Deduplicate imported records; support retries, revocation, and manual overrides.
- [ ] Unit tests for timezone boundaries, duplicates, deletion, and re-sync.

### 8.3 — UI integration
- [ ] Distinguish manual vs automatic completions in dashboard and history.
- [ ] Show sync status, last sync, error handling, and accessible explanations.
- [ ] Add manual refresh and disconnect controls; define behavior for previously imported records.
- [ ] Complete regression testing including no-Health-Connect installs.

## Explicitly deferred
- Phase 7.2 UI improvements including #16 Delete Habit.
- Phase 7.3 full existing-feature manual regression.

No exercise records are read, imported, synced, or mapped in 8.1. The permission request is initiated only after a user explicitly selects exercise-session access; no request occurs on launch. Full habit mapping and record access remain in 8.2.
