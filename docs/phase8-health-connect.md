# Phase 8 — Health Connect integration

Status: In progress on draft PR #17 (`feature/phase8-1-health-connect-foundation`) and draft PR #18 (`feature/phase8-2-activity-mapping`). Both remain unmerged. Phase 7.2 (Delete Habit, #16) and Phase 7.3 (full regression) are intentionally deferred, not cancelled.

## Guardrails
- Keep HabitFlow free, local-first, and functional without Health Connect.
- Health Connect is opt-in. Do not request permissions on app launch.
- Request only the read permissions needed for a user-selected habit/activity mapping.
- Do not upload health records, use analytics on health values, or require an account.
- Never log Room SQL bind arguments or exercise source-record identifiers to Logcat. Diagnostic builds must preserve on-device health privacy.
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
- [x] Validate Android build and targeted emulator smoke tests on PR #18 commit `9ecba2c`; PR #17 also passed both. Newer commits require their own CI checks. Physical-device permission grant/deny/revoke remains pending.

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

## Phase 8 on-device acceptance checklist

These tests require a **real Android device** with Health Connect and consented test
data. Passing GitHub CI and a stock emulator does not verify Health Connect grant,
deny, revoke, real provider records, or TalkBack speech.

1. **No provider / API below 26:** Launch HabitFlow, create and manually complete
   a habit, visit Settings → Health Connect, and confirm the unavailable/setup
   message is clear. No permission prompt should appear on launch.
2. **Permission denied:** Save a test walking/running/cycling mapping; request
   read-only permission and deny it. Preview/Apply/sync must not read or create
   completions. Manual tracking must still work.
3. **Permission granted:** Grant only exercise-session read access; add synthetic
   or explicitly consented sessions for a disposable test habit. Preview today
   shows the correct local date, distinct source IDs and unioned duration.
4. **Explicit completion:** Apply once, verify the dashboard's Health Connect
   provenance label, repeat Apply and one-tap sync, and confirm exactly one
   completion. Undo it and verify it is **not automatically reinserted**.
5. **Revocation during use:** Revoke Health Connect access before and, where
   possible, during an on-demand read. Check for a permission-needed result,
   no crash, no further provider reads, and no fabricated successful sync receipt.
6. **Disconnect and restart:** Remove mappings, close/reopen the app, and verify
   previously completed habits and undo history remain, but saved mappings,
   source IDs and last-sync receipt are cleared. Revoke permission separately
   in Android Health Connect settings.
7. **Accessibility:** With TalkBack and a large font/display size, navigate the
   setup screen, mapping chooser, scoped Preview/Remove controls, permission
   explanation, sync result announcements, and disconnect confirmation.
8. **Database upgrade and backup:** Install an older build with a populated
   v6 database, upgrade to the Phase 8 build and verify habits/actions are
   preserved. Exercise mappings are intentionally not part of the old CSV
   format; document the omission before release.

Record device model, Android version, Health Connect provider version, test
date, actual result and screenshot/log evidence for each item. Do not collect
or share real user health records in CI artifacts.

## Explicitly deferred
- Phase 7.2 UI improvements including #16 Delete Habit.
- Phase 7.3 full existing-feature manual regression.

Phase 8.1 never reads health records. Phase 8.2 reads exercise sessions only on a user-initiated Preview or one-time Sync after Health Connect permission and saved mappings; Apply is a separate explicit option after a qualifying preview. Neither branch uploads health data, requests permission on launch, or runs background synchronization. Phase 8.3 now provides Settings history, mapping disconnect, dashboard badges, and a persisted receipt for finished manual syncs. Android test module dependency wiring passed build/unit and emulator smoke on `9ecba2c`. Receipt boundary and malformed-counter regression tests were added in `c98f09b` and verified green on `708befe`. Partial-failure, duplicate and revocation unit tests passed Android build/unit and emulator smoke on `99780e3` (21 Nov 2026). Subsequent privacy hardening `c17a65f` replaces potentially sensitive Health Connect provider exception messages with a generic UI error; `1f6706e` adds a regression assertion. The privacy fix compile regression was corrected in `29557cd`; Android build/unit and emulator smoke both passed on 22 Nov 2026 (runs 44931481306 and 44931482206). Commit `f51f7ea` adds a final permission recheck after paginated reads and sanitizes repeated-page-token errors; it still requires CI and physical-device revocation testing. Physical-device permission, TalkBack and regression remain unfinished. Keep PRs unmerged pending authorization.


### 22 Nov CI repair checkpoint
The `f51f7ea` permission-recheck edit accidentally committed literal `\\n` sequences inside Kotlin source, so both workflows on `c57b173` failed compiling `HealthConnectExerciseReader.kt` at line 100. Commit `0b46e1d` replaces the escaped text with actual newlines and retains the final permission recheck and generic privacy-safe error. CI for the repair is pending; do not interpret a green emulator build as physical-device permission-revocation validation.

### 22 Nov pagination permission checkpoint
Both Android build/unit and emulator smoke workflows passed on repair commit `c304c53` (runs 44942610621 and 44942610599). Commit `bd10a7f` now checks exercise read permission **before every subsequent Health Connect page request**, as well as after the final page. This avoids unnecessary provider reads when permission is revoked between pages; a concurrent revoke during an individual provider call is still handled by the SecurityException path. Commit `7f9e717` adds strict mapping-kind parsing regression tests. These commits require fresh CI and do not substitute for physical-device revoke testing. No unattended sync or health-data upload was introduced.

### 22 Nov strict mapping-input and CI checkpoint
Both Android cloud build/unit and emulator smoke workflows passed on `d8262e` (runs 44947706414 and 44947706393). Commit `2bf12d8` adds negative parsing tests for whitespace, embedded newline, and provider-specific exercise labels. Persisted mappings must use only canonical `WALKING`, `RUNNING`, or `CYCLING` values; treadmill/stationary variants are normalized only when reading provider records. The new test commit requires fresh CI verification. Physical-device grant/deny/revoke and TalkBack validation remain outstanding; do not merge either draft PR without authorization.
