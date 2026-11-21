# Phase 8.2 — Activity mapping implementation

This is a stacked draft based on Phase 8.1 PR #17 and must not be merged before its base.

## Completed in first commit
- Pure, testable model for user-selected walking/running/cycling to a saved habit, with explicit daily minimum duration.
- Exercise-session matching that attributes intervals to the correct user timezone/local day, including DST, deduplicates source records, sums eligible sessions and preserves manual completions.
- No new data access: this commit cannot read Health Connect or change any habit.

## Persistence foundation (new)
- Version 7 Room migration adds an opt-in mapping table keyed by habit ID and an exercise-source provenance table keyed by habit/day/record ID.
- Both tables cascade on habit deletion; provenance also cascades when its mapping is removed/replaced.
- DAO provides reactive mapping list, upsert/remove, and idempotent source-record insertion. This is storage only, not automated action creation.
- Existing backup restoration deletes existing habits and therefore clears mapping/provenance rows before restoring; an export/import format for opt-in mappings is deferred until it can be versioned explicitly.
- Instrumentation tests cover durable mapping lookup, source deduplication, and deletion cascades.

## Mapping selection UI
- Health Connect Settings now allows choosing an existing active habit, walking/running/cycling, and a 1–1440-minute threshold; saving/removing persists in Room.
- Permission request is hidden until at least one explicit mapping is saved. This does not trigger automatic health reads or habit completions.
- Mappings remain local; displayed UI warns that automatic completion is not yet enabled.

## Read-only exercise adapter
- Added a guarded, on-demand Health Connect exercise-session reader with permission checks, paginated reads, deduplication and safe failure outcomes. It is **not invoked by the UI or background work yet**.
- Running includes treadmill; cycling includes stationary biking. Other exercise types are ignored.
- A day's query covers previous midnight to capture cross-midnight exercise. Matching clips records to exact local day.
- Session matcher unions overlapping intervals to avoid double-counting the same workout from watch and phone, with regression tests.

## User-triggered preview and UI navigation
- Adds a **Preview today** action for a saved exercise mapping when Health Connect permission is ready. This is the first connected UI call to the read-only reader, and runs **only on explicit user click**.
- Display of matching duration, record count, threshold eligibility, and existing manual completion is read-only; no Action or provenance rows are written.
- Permission revoke/error/unavailable paths are visible; new unit tests verify a denied/unmapped preview never requests health data.
- Privacy disclosure moved above the mapping editor, and the emulator test scrolls to verify it on small screens rather than expecting it at the bottom of a long page.

## Transactional automatic completion foundation
- Room v8 adds a unique habit/local-day completion ledger linked to the Action row with **ON DELETE SET NULL**. Deleting a previously automatic completion keeps the ledger and prevents a retry from silently recreating it.
- A single Room transaction validates that the exact mapping still exists, the habit is active, the duration threshold is met, and there is no manual completion. It then inserts one Action and source IDs plus the completion ledger atomically.
- Duplicate sync attempts for the same habit/day are idempotent. Removing an exercise mapping leaves existing completions and suppression ledger untouched; deleting a habit or restoring a backup cascades/clears that data.
- This DAO is **not yet called from the UI**; preview remains strictly read-only. Physical-device validation and explicit opt-in sync orchestration are required before enabling the write path.

## Explicit preview-to-completion flow
- After a qualifying preview, the user may deliberately tap **Apply completion to habit**. A second Health Connect permission check runs before the v8 transactional Action/provenance write.
- There are no background writes; the action requires a separate, explicit user tap after a preview.
- Mapping changes, duplicate attempts, archived habits and manual completions are handled by the transactional ledger; revocation blocks writes.
- A removed/modified mapping invalidates pending preview state.

## Phase 8.3 — Dashboard provenance badges
- Dashboard observes only local Room exercise-completion provenance (never directly queries Health Connect on launch).
- A badge reading **Completed via Health Connect** appears for an exercise-derived completion **today** in all three dashboard layouts (five-day, mini-calendar and compact). Ordinary manual completions are unbadged.
- Badge uses exact linked Action ID from the active ledger, not a timestamp heuristic. Deleting that Action nulls the ledger link and hides the badge while preserving the undo tombstone.
- Added pure ID-matching tests and a reactive DAO/ViewModel flow test.

## Remaining before 8.2 completion
1. Validate the Room 6→7 migration and exported schema on CI, then decide if mappings should be included in versioned CSV backup (currently intentionally omitted).
2. Extend explicit read-only preview into an opt-in transactional sync coordinator only after testing the reader with a fake client. No automatic actions yet.
3. Extend the explicit preview/confirmation workflow into opt-in automatic sync. The transactional database write and manual undo suppression exist, but background sync is not implemented.
4. Add database tests: repeated sync, duplicate providers, timezone/DST, manual override, deletion, permission revoke, and restored backup.
5. Complete CI and device tests, and verify privacy copy.
