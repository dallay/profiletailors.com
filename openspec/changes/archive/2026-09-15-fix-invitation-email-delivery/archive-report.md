# Archive Report: fix-invitation-email-delivery

## Change

- Change: `fix-invitation-email-delivery`
- Archived: 2026-09-15
- Mode: `openspec` per `openspec/config.yaml` (`persistence.mode: openspec`, `artifact_policy: openspec-only`)
- Destination: `openspec/changes/archive/2026-09-15-fix-invitation-email-delivery/`
- Branch: `feature/fix-invitation-email-delivery-1`
- Worktree: `/Users/acosta/Dev/dallay/worktrees/fix-invitation-email-delivery`

## Acceptance Gate

Archive gate PASSED.

- `verify-report.md` exists and records the local verification evidence and limitations.
- `qa-report.md` exists and records `PASS WITH WARNINGS`.
- Direct invitation BDD passed 16/16 in both fast and PostgreSQL lanes, with JUnit XML evidence.
- Focused handler/consumer tests passed 35/35.
- Focused PostgreSQL invitation transaction integration passed 12/12.
- Focused PostgreSQL notification repository integration passed 7/7.
- `backend-check` passed locally.
- No unresolved P0, P1, or CRITICAL acceptance issue is recorded.
- The remaining `PublishingWorkerTransactionPostgresIntegrationTest` initialization failure is an unrelated open P2 warning (`QA-001`), which the repository archive policy permits.
- The raw-token handler-to-event-to-consumer handoff remains explicitly documented as a temporary, non-canonical DALLAY-566 follow-up.
- No deployed, CI, PR, merge, or production acceptance evidence is claimed.

## Specs Synced

| Domain | Action | Details |
|---|---|---|
| `invitations` | Updated | Added direct invitation transaction atomicity, target-aware workspace-name and 404 semantics, and handler-originated initial/resend delivery identity requirements. Existing requirements preserved. |
| `email-notifications` | Updated | Added post-commit direct-event delivery, initial/resend idempotency, provider-owned notification status, and the temporary DALLAY-566 raw-token handoff boundary. Existing requirements preserved. |

## Archive Contents

- `proposal.md` ✅
- `exploration.md` ✅
- `specs/invitations/spec.md` ✅
- `specs/email-notifications/spec.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (all task checkboxes complete)
- `verify-report.md` ✅
- `qa-report.md` ✅ (original `PASS WITH WARNINGS` acceptance evidence preserved)
- `state.yaml` ✅ (`current_phase: archive`, `next: none`)
- `archive-report.md` ✅

## Verification

- Active change directory no longer exists at `openspec/changes/fix-invitation-email-delivery/`.
- Archived change directory exists at `openspec/changes/archive/2026-09-15-fix-invitation-email-delivery/`.
- All change artifacts are present in the archive directory.
- Canonical spec changes pass `git diff --check`.
- DALLAY-567 QA artifacts were not modified.
- No other worktree was modified.
- No commit, push, submit, rebase, merge, or branch switch was performed.
