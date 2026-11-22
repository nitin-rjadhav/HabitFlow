# Phase 8 physical-device release acceptance record

Use this form **only with a disposable test habit and synthetic or explicitly consented exercise sessions**. Never attach source record IDs, SQL bind values, personal exercise details, or unredacted Health Connect screens to a PR.

This record supplements automated Android build/unit and emulator checks; it cannot be marked passed from CI alone. Record each scenario as **PASS**, **FAIL**, or **NOT RUN**, with a short observation and a privacy-reviewed evidence reference. An unexecuted scenario is not a pass.

## Test environment

- Tester / date:
- Device model:
- Android version and security patch:
- HabitFlow build SHA / APK workflow URL:
- Health Connect version / provider version:
- Installation: fresh / upgraded from v6 / upgraded from v7 / restored backup
- Test data: synthetic / explicitly consented (never paste records here)

## Scenarios

| ID | Scenario and expected outcome | Result | Redacted evidence / issue |
| --- | --- | --- | --- |
| HC-01 | Fresh launch without provider: no prompt, no crash; manual habit completion works | NOT RUN | |
| HC-02 | Unsupported Android or unavailable provider: actionable Settings state; manual tracking unaffected | NOT RUN | |
| HC-03 | Deny read-exercise permission: no preview/import; error state is accessible | NOT RUN | |
| HC-04 | Grant read-exercise permission only after explicit user action: selected mappings only | NOT RUN | |
| HC-05 | Preview walking, running and cycling across local midnight/DST: correct selected-day duration | NOT RUN | |
| HC-06 | Explicit Apply twice and foreground Sync twice: idempotent result, no duplicate completion | NOT RUN | |
| HC-07 | Existing manual completion remains manual; imported activity never replaces it | NOT RUN | |
| HC-08 | Undo an imported completion; subsequent sync does not silently restore it | NOT RUN | |
| HC-09 | Revoke permission before and during foreground read: safe permission-needed state, no false success receipt | NOT RUN | |
| HC-10 | Disconnect and relaunch: mapping/source identities/receipt cleared, existing actions and undo tombstones retained | NOT RUN | |
| HC-11 | Large font/display size and TalkBack: all controls discoverable, labeled, operable; status announcements understandable | NOT RUN | |
| HC-12 | Upgrade populated v6 database to v8: habits, manual actions and history survive | NOT RUN | |
| HC-13 | Upgrade populated v7 database to v8: mappings and source identity survive, new ledger initializes correctly | NOT RUN | |
| HC-14 | Backup/restore: document what is and is not restored; no unconsented health read after restore | NOT RUN | |
| HC-15 | Offline mode and no provider: ordinary habit features remain usable; no login or health-data upload | NOT RUN | |

## Evidence and release gate

For failures, file a focused issue with build SHA, scenario ID, reproduction steps, expected/actual behavior and **redacted** log/screenshot evidence. Never upload personal records to CI. Check that Logcat does not reveal Health Connect source IDs or SQL bind parameters. Confirm no Health Connect permission is requested on launch and no unattended sync occurs.

**Release gate:** Android build/unit and emulator CI must pass on the proposed release SHA; physical permission grant/deny/revoke, accessibility, upgrade and backup scenarios must be explicitly recorded. CI success alone is insufficient. Phase 7.2 issue #16 and Phase 7.3 remain deferred.
