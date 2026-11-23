# HabitFlow Phase 7.3 — UI/UX polish and regression

## Scope and baseline
- Started 23 November 2026 from `main` at `8fe74fa04be6c9f8e838cc36dc2e08d5e5741979`, after PRs #39 and #40 merged.
- Primary branch: `feature/phase7-3-dashboard-calendar-polish`.
- Phase 7.3 completes remaining Material 3 dashboard/calendar/create-edit/accessibility work from [PHASE7_UI_UX_PLAN.md](PHASE7_UI_UX_PLAN.md).
- This is a **new UI/UX increment**, not Phase 8 physical-device acceptance. Phase 8 issue #38 remains a separate release gate.

## Increment 7.3.1 — Dashboard and calendar
- [ ] Audit Five-Day action circles: ensure a minimum 48dp accessible hit target, preserve five-day layout, long-press/toggle behavior, animation, haptics and day-specific semantics.
- [ ] Audit Mini-Calendar: ensure today's completion button has accurate TalkBack role/state and minimum touch target; historical dots remain read-only.
- [ ] Improve calendar/history navigation, selected-day feedback and distinction between completed, missed and future days.
- [ ] Add focused Compose UI tests for the two dashboard layouts and calendar selection; avoid running the known hanging full AddHabitTest suite without isolation.

## Increment 7.3.2 — Create/Edit and accessibility
- [ ] Audit validation, error states and Save/Archive/Delete actions, including the newly merged Delete Habit flow from PR #39.
- [ ] Verify large-font scaling, TalkBack labels, focus order, dynamic/light/dark contrast and touch target sizes.
- [ ] Confirm empty, loading and error UI behavior across dashboard, history and editing.

## Increment 7.3.3 — Regression and acceptance
- [ ] Run Android build and unit tests and emulator smoke tests on each PR.
- [ ] Add or update targeted ViewModel, Compose UI and database tests where behavior changes.
- [ ] Verify local-only storage, archive/unarchive, deletion cascades, completion history and export/import behavior remain unchanged.
- [ ] After approved merges, rerun both workflows on the integrated `main` commit and record outcomes.

## Constraints and merge gates
- Preserve habit history and data semantics; do not introduce database migrations without separate approval.
- Keep HabitFlow strictly free, local-first, without analytics, telemetry, paid services or background data uploads.
- Use focused PRs, record exact CI run links, and **do not merge without user approval**.
- The existing automated CI is a prerequisite; passing CI alone does not replace manual TalkBack and physical-device acceptance.
