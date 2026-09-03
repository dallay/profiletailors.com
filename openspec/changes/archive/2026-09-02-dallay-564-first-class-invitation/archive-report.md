# Archive Report: First-Class Invitation (DALLAY-564)

## Gate Result

Archive gate passed on 2026-09-02T13:46:04Z.

- `verify-report.md` exists and records verification result PASS with 81/81 in-scope tests green.
- `qa-report.md` exists and records QA verdict PASS.
- QA findings list CRITICAL, P0, P1, P2, and P3 as None.
- No unresolved CRITICAL/P0/P1 findings remain.
- No acceptance-relevant BLOCKED or NOT TESTED scenarios remain.
- Architecture tests, Detekt, Spotless, and diff checks are recorded green in the evidence reports.

## Specs Synced

| Domain | Action | Details |
|---|---|---|
| invitations | Created | Promoted 12 requirements from `openspec/changes/dallay-564-first-class-invitation/specs/invitations/spec.md` to `openspec/specs/invitations/spec.md` |

## Archive Destination

`openspec/changes/archive/2026-09-02-dallay-564-first-class-invitation/`

## Archive Contents

- `proposal.md`
- `specs/invitations/spec.md`
- `design.md`
- `tasks.md`
- `apply-progress.md`
- `verify-report.md`
- `qa-report.md`
- `archive-summary.md`
- `archive-report.md`
- `state.yaml`

## Source of Truth Updated

- `openspec/specs/invitations/spec.md`

## Out of Scope Preserved

DALLAY-565 notifications, DALLAY-566 token generation and handoff, DALLAY-567 provisioning, DALLAY-568 admin commands, DALLAY-570 waitlist conversion, UI, bulk operations, destructive migration, and replacement of `WaitlistInvitation` flows remain outside DALLAY-564.
