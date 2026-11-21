# Phase 8.2 — Activity mapping implementation

This is a stacked draft based on Phase 8.1 PR #17 and must not be merged before its base.

## Completed in first commit
- Pure, testable model for user-selected walking/running/cycling to a saved habit, with explicit daily minimum duration.
- Exercise-session matching that attributes intervals to the correct user timezone/local day, including DST, deduplicates source records, sums eligible sessions and preserves manual completions.
- No new data access: this commit cannot read Health Connect or change any habit.

## Remaining before 8.2 completion
1. Add local persistence for mapping settings and imported source record provenance with Room migration, backups/restore behavior and delete-cascade policy.
2. Add Health Connect read gateway for the selected exercise sessions only after explicit user opt-in and existing permission, with pagination, revocation handling and SDK guard.
3. Wire mapping selection UI, day-level sync coordinator and idempotent transactional automatic completion. Distinguish manual actions and never overwrite/delete them.
4. Add database tests: repeated sync, duplicate providers, timezone/DST, manual override, deletion, permission revoke, and restored backup.
5. Complete CI and device tests, and verify privacy copy.
