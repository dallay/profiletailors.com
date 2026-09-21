# Post-Archive Amendments: dallay-665-bulk-waitlist-invitation

**Date**: 2026-09-16 (reconciliation pass, post-merge to main at HEAD `980ad046`)
**Scope**: Record-only. All original artifacts in this folder (`proposal.md`,
`design.md`, `tasks.md`, `exploration.md`, `verify-report.md`,
`archive-report.md`, `specs/`, `state.yaml`) are byte-identical to the
pre-merge archive. Nothing below rewrites history; it enumerates drift between
the archived record and merged reality (PRs #1061/#1062/#1063) with evidence.

## D1 — Audit atomicity wording (known drift, already fixed in living spec)

- Archived delta `specs/platform-admin-audit/spec.md` line 7 says "Each audit
  MUST commit independently of its entry's state change".
- Merged `BulkInviteWaitlistEntriesHandler` runs `singleHandler.handle` inside
  `transactionRunner.runAtomically` (success audit commits with the mutation)
  and calls `publishEntryAudit` outside the transaction only for
  skipped/failed entries.
- Living `openspec/specs/platform-admin-audit/spec.md` line 111 already states
  the corrected behavior (`SUCCEEDED` atomic; `REJECTED`/`FAILED`
  independent), fixed during post-archive review and merged to main. No
  further sync needed.

## D2 — Active-invitation outcome wording (stale living spec, fixed here)

- Archived delta and (until this pass) living
  `openspec/specs/lead-capture-waitlist/spec.md` said "active-invitation
  entries MUST report `failed`".
- Merged `mapFailure` maps `InvitationAlreadyActiveException` to
  `SKIPPED` / `INVITATION_ALREADY_ACTIVE` / `REJECTED`, matching the archived
  design error-mapping table and the unit test covering the active-invitation
  skip path (no `InvitationIssued` on skip).
- Fix applied in this pass (wording only, behavior and tests unchanged):
  living `lead-capture-waitlist/spec.md` now says active-invitation entries
  report `skipped` with `INVITATION_ALREADY_ACTIVE`. Archived delta kept
  intact as the historical record.

## D3 — Admin UI refinements merged post-archive (no spec sync required)

Merged to main via the review fixup in the PR chain; the archived
`tasks.md` 4.2 / `design.md` UI row / `verify-report.md` UI row predate them.
No UI-behavior spec domain exists for this change, so nothing stays stale:

- Bulk success now refreshes entries AND summary
  (`Promise.all([fetchEntries(), fetchSummary()])` in `WaitlistView.vue`).
- 50-entry client guard (`BULK_INVITE_MAX_ENTRIES`) with `bulkTooMany` copy
  in EN+ES (`apps/web/admin/src/i18n/`), blocking oversized submissions
  without sending the request.
- Bulk `catch` handler surfaces the generic error instead of leaving stale
  bulk state on network failure.
- Covered by `WaitlistView.spec.ts` additions (summary re-fetch assertion,
  generic-error-on-reject, 51-entry client block).

## D4 — Telemetry contract placement (verified, no drift)

The handler-time fixup placing `recordBulkInvite` in
`application/contracts/InvitationTelemetry.kt` matches the archived
`design.md` file-changes row. No action.

## D5 — `lychee.toml` regex escaping (out of change scope, noted for context)

Review-pass hardening of link-exclusion patterns from literal substrings to
escaped regexes; unrelated to bulk-invite behavior. No archive impact.

## State

`state.yaml` intentionally untouched: all 8 phases remain truthfully
complete; this pass is reconciliation, not a new phase.
