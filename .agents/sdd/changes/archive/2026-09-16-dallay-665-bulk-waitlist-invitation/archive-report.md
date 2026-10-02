# Archive Report: dallay-665-bulk-waitlist-invitation

**Change**: dallay-665-bulk-waitlist-invitation (GitHub issue #665)
**Date**: 2026-09-16 **Mode**: openspec **Verdict carried**: PASS WITH WARNINGS (no CRITICAL, no
scope creep)

## W1 Reconciliation

Warning W1 from `verify-report.md` (audit delta-spec wording vs implementation) was
reconciled during archival with a wording-only fix — behavior and tests unchanged:

- Requirement now reads: `REJECTED` plus stable code per `skipped` entry, `FAILED`
  plus stable code per `failed` entry (including unexpected errors).
- CONVERTED-entry scenario now expects a `FAILED` audit event carrying
  `ENTRY_ALREADY_CONVERTED`, matching the design error-mapping table and
  `BulkInviteWaitlistEntriesHandler.mapFailure` (verified by unit test
  `publishes failed audit for failed entries outside the entry transaction`).

Fixed in: `openspec/changes/dallay-665-bulk-waitlist-invitation/specs/platform-admin-audit/spec.md`
(before sync, so the main spec received the corrected text).

## Specs Synced

| Domain                | Action  | Details                                                                                  |
|-----------------------|---------|------------------------------------------------------------------------------------------|
| invitations           | Updated | 3 added: Bulk invitation envelope, Bulk reuses single-entry issuance, Bulk observability |
| lead-capture-waitlist | Updated | 1 added: Bulk eligibility is PENDING-only                                                |
| platform-admin-audit  | Updated | 1 added: Per-entry bulk audit (W1-reconciled wording)                                    |
| admin-authorization   | Updated | 1 added: Bulk fail-fast permission check                                                 |

No MODIFIED or REMOVED requirements; all pre-existing requirements preserved.
No spec index file exists under `openspec/specs/` — nothing else to update.

## Archive Location

`openspec/changes/archive/2026-09-16-dallay-665-bulk-waitlist-invitation/`

Contents: proposal.md, specs/ (4 domains), design.md, tasks.md (13/13 complete),
exploration.md, verify-report.md, state.yaml (finalized), archive-report.md.

## Source of Truth Updated

- `openspec/specs/invitations/spec.md`
- `openspec/specs/lead-capture-waitlist/spec.md`
- `openspec/specs/platform-admin-audit/spec.md`
- `openspec/specs/admin-authorization/spec.md`
