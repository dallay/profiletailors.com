# Proposal: Bulk Waitlist Invitation Operations (#665)

## Intent

Add synchronous bulk invite over the single-entry path, with per-entry results and partial success.

## Scope

### In Scope
- `POST /api/admin/waitlist-entries/invitations:bulk` (≤50 IDs), sync, HTTP 200 envelope + summary
- `BulkInviteWaitlistEntriesHandler` reusing single-entry logic; per-entry transaction
- Outcomes `invited|skipped|failed`; `INVITED` → `skipped`
- Per-entry audit + `InvitationIssued` per success; reuse `WAITLIST_INVITE`, no new permission
- BDD + admin bulk selection behind `platform.waitlist.invite`

### Out of Scope
- Async jobs, generic bulk framework, segmentation/scoring, schema changes
- Revoke-and-reinvite default; dual-write retirement (follow-up); new permission
- RFC §§19,20,34,45 (not found in-repo; non-binding unless supplied)

## Capabilities

### New Capabilities
- None (orchestrates existing behavior)

### Modified Capabilities
- `invitations`: bulk issuance reuses WAITLIST lifecycle; never raw tokens
- `lead-capture-waitlist`: PENDING-only eligibility; `invite()` unchanged
- `platform-admin-audit`: one event per entry (SUCCEEDED/REJECTED+code/FAILED)
- `admin-authorization`: up-front `WAITLIST_INVITE` check, fail-fast 403

## Approach

Orchestrating handler: cap → permission check → per-entry single-path calls in own transactions → collect results. Rejected: single-transaction batch (all-or-nothing; audit rolls back) and async jobs (unjustified surface).
- Domain: `invite()` owns PENDING→INVITED; CONVERTED/CANCELLED→`failed`. Revoke-and-reinvite deferred (token churn, riskier retry).
- Dual-write preserved for parity as legacy debt; retire jointly later, never diverge.
- API: `{entryIds:[≤50]}` → `200 {results:[{entryId,outcome,invitationId?,code?}], summary}`; IDs + codes only (ADR-0020/0021).
- Observability: per-entry counters + one bulk counter; retry yields `skipped`, never duplicates.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `.../handler/BulkInvite…Handler.kt` | New | Loop + results |
| `.../http/AdminWaitlistController.kt` | Modified | Bulk route; single route untouched |
| `.../command/AdminCommands.kt` | Modified | Bulk command |
| `platform-admin.feature`, `WaitlistView.vue` | Modified | BDD + bulk selection UI |
| `invitations`, `lead-capture-waitlist`, `platform-admin-audit`, `admin-authorization` | Modified | Delta specs |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Audit lost in entry rollback | Med | Audit outside entry txn |
| Token/email leak in envelope | Low | IDs + codes only |
| Single vs bulk contention | Med | CAS guards + contention BDD |
| Latency/token storm | Low | Hard cap 50 |

## Rollback Plan

Remove bulk route + handler; single flow, queries, single-invite UI untouched. No schema change.

## Dependencies

- #666 single flow, #667 queries (present); follow-up: joint dual-write retirement

## Success Criteria

- [ ] ≤50 PENDING → all `invited`, one audit + `InvitationIssued` each
- [ ] Mixed batch → 200 partial with correct summary
- [ ] No `WAITLIST_INVITE` → 403 before any entry touched
- [ ] Gherkin: `GIVEN 3 PENDING + 1 INVITED + 1 CONVERTED WHEN bulk invite THEN 3 invited, 1 skipped, 1 failed AND retry yields skips`
