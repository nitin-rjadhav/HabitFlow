# Phase 7 — Material 3 UI/UX redesign

## Branch and dependency
- Branch: `feature/phase7-ui-ux-redesign`
- Based on merged Phase 6 commit `d4ac399e20b5417db90b5faed31da2842cff0614`.
- Phase 6 PR #12 was approved and squash-merged into main.
- Open Phase 7 review PRs against main.

## Implementation increments
1. **Design system foundation:** audit current Compose Material theme, typography, spacing, shape and color tokens; introduce reusable tokens while preserving existing settings and dark mode.
2. **Home dashboard:** improve habit hierarchy, completion affordances, empty states and accessibility labels.
3. **Insights:** refine monthly summary, comparison, weekly activity and streak presentation without changing metric semantics.
4. **Calendar/history:** improve navigation, selection feedback and readable day states.
5. **Create/edit habits:** consistent fields, validation, error messages and primary actions.
6. **Polish:** contrast, TalkBack, font scaling, touch targets, screenshots and regression coverage.

## Quality gates
- Keep each increment small and reviewable.
- Preserve existing habit data, storage and analytics behavior; no migration without separate approval.
- Add Compose previews or screenshots where feasible.
- Run Android unit tests and emulator smoke tests; do not merge on red CI.
- Review TalkBack semantics, 48dp touch targets, dynamic text sizing, dark mode and empty/loading/error states.
