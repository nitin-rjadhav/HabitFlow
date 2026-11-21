# Phase 8 — Health Connect integration

Status: In progress on draft PR #17 (`feature/phase8-1-health-connect-foundation`) and draft PR #18 (`feature/phase8-2-activity-mapping`). Both remain unmerged. Phase 7.2 (Delete Habit, #16) and Phase 7.3 (full regression) are intentionally deferred, not cancelled.

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
- [x] Create injectable availability/permission gateway (8.1) and guarded read-only exercise reader (8.2).
- [x] Build a Settings entry and opt-in setup screen with availability/permission/error states, single read-only exercise permission, and rationale activity.
- [x] Add unit tests for availability, permission transitions and ViewModel error/refresh behavior.
- [ ] Validate Android build and targeted emulator smoke tests on the latest PR #18 commit; PR #17 passed both. Physical-device permission grant/deny/revoke remains pending.

### 8.2 — Mapping and automatic completion
- [x] Let users select an existing habit and walking/running/cycling mapping.
- [x] Define timezone/DST-aware day matching and daily duration threshold.
- [x] Persist mapping, source provenance and completion ledger locally with Room migrations.
- [x] Deduplicate records and overlap duration; preserve manual actions, tombstone user-deleted auto completions, and handle permission revocation in foreground reads.
- [ ] Validate actual grant/deny/revoke flows on a physical device. Preview, explicit Apply and one-time user-triggered bulk sync are implemented; unattended automatic foreground/background sync is not.
- [x] Unit/instrumented tests for timezone boundaries, duplicates, deletion, idempotence and manual precedence; full migration/backup compatibility and device tests still pending.

### 8.3 — UI integration
- [x] Distinguish manual vs exercise-generated actions with provenance badges in all dashboard layouts and a local audit history in Settings. UI accuracy and TalkBack tests still require device validation.
- [x] Persist last finished foreground sync metadata locally with timestamp and applied/unchanged/uncheckable counts; clears on mapping disconnect.
- [ ] Finish full accessibility review. Mapping buttons now have habit-specific screen-reader descriptions, sync outcomes have polite live-region announcements, and mapped habit action buttons have a small-screen-safe layout (requires targeted TalkBack/device validation).
- [x] Add manual refresh (existing Check again) and mapping disconnect controls, retaining previously applied Actions and local undo ledger while deleting mappings/source IDs. OS-level Health Connect permission must be revoked separately in Android settings.
- [ ] Complete regression testing including no-Health-Connect installs. Emulator UI smoke now checks the new Settings completion history heading.

## Explicitly deferred
- Phase 7.2 UI improvements including #16 Delete Habit.
- Phase 7.3 full existing-feature manual regression.

Phase 8.1 never reads health records. Phase 8.2 reads exercise sessions only on a user-initiated Preview or one-time Sync after Health Connect permission and saved mappings; Apply is a separate explicit option after a qualifying preview. Neither branch uploads health data, requests permission on launch, or runs background synchronization. Phase 8.3 now provides Settings history, mapping disconnect, dashboard badges, and a persisted receipt for finished manual syncs. Android test module dependency wiring is under CI validation; physical-device permission, TalkBack and regression remain unfinished. Keep PRs unmerged pending authorization.
