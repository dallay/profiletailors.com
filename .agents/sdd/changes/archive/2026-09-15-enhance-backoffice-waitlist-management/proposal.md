# Proposal: Enhance Backoffice Waitlist Management

## Intent

Harden the waitlist back-office slice to production-readiness: close the high-priority operational
gaps (missing optimistic lock on cancel, absent resend action, unread consent fields), add the
missing status-count summary card, and align the UX pattern with the existing invitations slice so
operators have a consistent, safe administrative experience.

## Scope

### In Scope

| #  | Gap                                                                       | Priority   | Rationale                                                                                                        |
|----|---------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------------|
| G1 | Cancel action sends `expectedVersion` (optimistic lock)                   | **High**   | Prevents silent last-write-wins on concurrent operator edits                                                     |
| G2 | Resend button — backend has `POST .../resend`, i18n key exists            | **High**   | Operators need to re-trigger expired/delayed invitation emails without revoking                                  |
| G3 | Consent fields rendered in entry detail                                   | **High**   | GDPR audit and support triage require visibility into `earlyAccessConsent`, `marketingConsent`, `consentVersion` |
| G4 | Status count summary card (`countByStatus` from `AdminWaitlistQuery`)     | **High**   | Immediate situational awareness; mirrors invitations summary pattern                                             |
| G5 | Vitest specs for `WaitlistView.vue` and `WaitlistEntryView.vue`           | **High**   | Guard regressions as this surface matures                                                                        |
| G6 | `waitlistKey` / `waitlistId` filter input in list view                    | **Medium** | Enables targeted filtering across waitlist sources                                                               |
| G7 | Date-range filters (`joinedFrom`, `joinedTo`, `invitedFrom`, `invitedTo`) | **Medium** | Backend supports them; operators need them for cohort analysis                                                   |
| G8 | Metadata summary display in entry detail                                  | **Medium** | Backend surfaces it; operators currently cannot see it                                                           |

### Out of Scope

- Resend from list row (out for this slice; entry detail resend button covers the need)
- Audit trail linkage (operator name + timestamp per action) — deferred to a separate traceability
  slice
- Entry ID copy/link widget — cosmetic; not a functional gap
- Backend changes — backend is fully capable per exploration
- Cross-waitlist bulk operations

## Capabilities

### New Capabilities

- None. All changes are within the existing `waitlist-management` bounded context.

### Modified Capabilities

- `waitlist-management`: add status-count summary card, optimistic-lock enforcement on cancel,
  resend action, consent field rendering, and filter expansion.

## Approach

Mirror the pattern established by the invitations slice (`InvitationsView.vue`):

1. **Optimistic lock on cancel** — inject `version` into the cancel action payload.
   `WaitlistEntryView.vue` already shows a `version` field; wire it into the `cancelEntry` command
   payload. Backend accepts `expectedVersion`; this change makes the frontend send it.

2. **Resend button** — add a "Resend" button to `WaitlistEntryView.vue` next to the Invite button,
   gated by `INVITED` status. Backend already has `POST .../resend`. Wire to the existing API; no
   new i18n needed (key `platformadmin.waitlist.resend` already exists per exploration).

3. **Consent fields** — add three read-only rows to `WaitlistEntryView.vue` detail section:
   `earlyAccessConsent`, `marketingConsent`, `consentVersion`. All three are nullable; render `–`
   when null.

4. **Status count summary card** — add a `<WaitlistStatusSummaryCard>` component above the list
   table in `WaitlistView.vue`. Consumes `AdminWaitlistQuery.countByStatus()`. Mirrors
   `InvitationsSummaryCard` pattern from the invitations slice.

5. **Filter expansion** — add `waitlistKey` / `waitlistId` `<FormSelect>` and `joinedFrom` /
   `joinedTo` / `invitedFrom` / `invitedTo` `<FormInput type="date">` controls to `WaitlistView.vue`
   filter panel. Backend accepts all parameters; no controller changes needed.

6. **Metadata summary** — add a read-only "Metadata" section in `WaitlistEntryView.vue` that renders
   the `metadataSummary` object as key-value rows.

7. **Vitest specs** — write focused specs: `WaitlistView.spec.ts` (paged list, search, filters,
   summary card) and `WaitlistEntryView.spec.ts` (detail rendering, invite/cancel/resend actions,
   optimistic lock payload).

## Alternatives

| Option                                                      | Pros                                                                                                             | Cons                                                                          |
|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| **A — All gaps in one slice**                               | Single review, full parity with invitations, no future migration                                                 | Larger diff, longer review cycle                                              |
| **B — This proposal (all high + medium in scope, low out)** | Focused, production-safe, consistent with invitations pattern; low gaps are cosmetic or future traceability work | Medium gaps deferred; metadata summary and date filters will need a follow-up |
| **C — Full rewrite of waitlist UI**                         | Clean slate, no legacy debt                                                                                      | Overkill; current foundation is sound, backend is solid                       |

**Recommendation: Option B.** The foundation is sound; we close the operational gaps and add the
medium filters in one pass. Low-priority cosmetic gaps are deferred to a future slice.

## Risks

| Risk                                                                                    | Likelihood | Mitigation                                                                  |
|-----------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------|
| Resend button triggers `expectedVersion` mismatch if version not refreshed after invite | Low        | Resend action uses its own endpoint; no optimistic lock required            |
| Backend accepts `expectedVersion` but no 409 is raised when it mismatches               | Low        | Backend contract is confirmed capable per exploration; verify with BDD test |
| `countByStatus` adds DB load on every list render                                       | Low        | Backend likely already queries it for the paged result; confirm query plan  |
| Date filters + waitlistKey filter combination produces empty result                     | Medium     | Add "no results" empty state with filter reset action                       |

## Dependencies

- Shell slice and invitations slice must be archived before this slice begins apply
- `#659` authz boundary: permission checks on resend must align with `platform.waitlist.resend`
  (confirm it exists in the permission registry)
- Backend `AdminWaitlistQuery.countByStatus` must be accessible to the admin actor (confirmed
  capable per exploration)

## Rollback Plan

Revert the following files to their pre-change state:

| File                                                                                | Revert action                                                                   |
|-------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `apps/web/admin/src/modules/platformadmin/views/waitlist/WaitlistView.vue`          | Remove summary card, filter inputs                                              |
| `apps/web/admin/src/modules/platformadmin/views/waitlist/WaitlistEntryView.vue`     | Remove consent rows, resend button, metadata summary, optimistic lock injection |
| `apps/web/admin/src/modules/platformadmin/views/waitlist/WaitlistView.spec.ts`      | Delete                                                                          |
| `apps/web/admin/src/modules/platformadmin/views/waitlist/WaitlistEntryView.spec.ts` | Delete                                                                          |

No backend or migration rollback required — all backend changes are already in place.

## Success Criteria

- [ ] Cancel action sends `expectedVersion`; backend returns 409 on mismatch
- [ ] Resend button visible on `INVITED` entries; backend returns 200 and re-triggers email
- [ ] Entry detail shows `earlyAccessConsent`, `marketingConsent`, `consentVersion` fields
- [ ] Summary card displays correct counts per status, matching `countByStatus` response
- [ ] `waitlistKey` / `waitlistId` filter narrows results correctly
- [ ] Date-range filters narrow results by `joinedAt` and `invitedAt`
- [ ] Metadata summary renders `metadataSummary` key-value pairs
- [ ] `WaitlistView.spec.ts` and `WaitlistEntryView.spec.ts` pass locally and in CI
- [ ] No new Biome warnings or type errors introduced in changed files
- [ ] `backend-bdd-fast` passes (covers waitlist BDD scenarios)
