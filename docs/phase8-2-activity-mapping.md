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

## Remaining before 8.2 completion
1. Validate the Room 6→7 migration and exported schema on CI, then decide if mappings should be included in versioned CSV backup (currently intentionally omitted).
2. Wire the new on-demand Health Connect read gateway to explicit user refresh/sync only after permission, with rate-limit and revocation handling, and exercise reads tested with a fake client.
3. Implement day-level sync coordinator and idempotent transactional automatic completion with manual-override controls. Mapping selection UI is implemented but requires UI tests.
4. Add database tests: repeated sync, duplicate providers, timezone/DST, manual override, deletion, permission revoke, and restored backup.
5. Complete CI and device tests, and verify privacy copy.
