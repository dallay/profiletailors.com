## Exploration: Back Office slice 2 — Waitlist management (hardened/expanded)

### Current State

The waitlist back-office area has a working foundation: `WaitlistView.vue` (paged list with email search + status filter), `WaitlistEntryView.vue` (detail + invite/revoke actions), `AdminWaitlistController`, `R2dbcAdminWaitlistQuery`, and BDD coverage in `platform-admin.feature`. Previous slices (shell, invitations) are archived.

### WaitlistView.vue — What it does

| Feature | Status |
|---------|--------|
| Paged list (page/size/sort: joinedAt desc) | ✅ |
| Email substring search | ✅ |
| Status filter (PENDING/INVITED/CONVERTED/CANCELLED) | ✅ |
| Row-level invite action | ✅ |
| Row-level cancel action with reason dialog | ✅ |
| Previous/Next pagination | ✅ |
| Loading and error states | ✅ |
| Click-through to entry detail | ✅ |
| Empty-state copy (`common.noData`) | ✅ (implicit via table) |
| Status count summary card | ❌ |
| Resend action from list | ❌ |
| Date-range filters (joinedFrom/joinedTo, invitedFrom/invitedTo) | ❌ |
| `waitlistKey` or `waitlistId` filter | ❌ |
| Vitest spec | ❌ |

### WaitlistEntryView.vue — What it does

| Feature | Status |
|---------|--------|
| Entry detail fields (email, status, source, locale) | ✅ |
| Invite action | ✅ |
| Revoke action (revokes invitation, keeps entry) | ✅ |
| Invitation history list | ✅ |
| Version field (optimistic lock) | ❌ |
| Consent fields display (earlyAccessConsent, marketingConsent, consentVersion) | ❌ |
| `metadataSummary` display | ❌ |
| `waitlistKey` / `waitlistId` display | ❌ |
| `entryId` display (linked or copied) | ❌ |
| Resend action | ❌ |
| Audit trail linkage (operator who acted, timestamp) | ❌ |
| Vitest spec | ❌ |

### Backend API Surface — What exists

| Endpoint | Path | Status |
|----------|------|--------|
| List entries | `GET /api/admin/waitlist-entries` | ✅ (supports all filters) |
| Get entry detail | `GET /api/admin/waitlist-entries/{entryId}` | ✅ |
| Invite entry | `POST /api/admin/waitlist-entries/{entryId}/invitations` | ✅ |
| Cancel entry | `POST /api/admin/waitlist-entries/{entryId}/cancel` | ✅ |
| Waitlist summary (status counts) | `GET /api/admin/waitlist-entries/summary` | ❌ |
| Resend invitation | `POST /api/admin/invitations/{id}/resend` (via InvitationController) | ✅ (existing, waitlist-aware) |

Query `AdminWaitlistQuery`:
- `list(ListAdminWaitlistEntriesQuery)` — ✅ full filter set (status, waitlistId, waitlistKey, email, date ranges)
- `findById(entryId)` — ✅ returns `AdminWaitlistEntryDetail` with consent + metadata + invitationHistory
- `countByStatus()` — ✅ in interface, needs verification on persistence side

### Contrast with Invitations Slice Pattern

The `DirectInvitationsView.vue` (post-dallay-567) has:

| Feature | Invitations | Waitlist |
|---------|------------|----------|
| Paged list with status+email filters | ✅ | ✅ (same) |
| Status count summary | ❌ | ❌ (missing) |
| Row actions: resend | ✅ | ❌ (missing) |
| Row actions: revoke | ✅ | ✅ (cancel, different) |
| Detail view with full history | N/A | ✅ |
| Consent/metadata in detail | N/A | ❌ (backend has it, frontend doesn't render) |
| Optimistic lock on actions | ✅ (version field) | ❌ (no version in cancel dialog) |
| Vitest spec | ✅ | ❌ |

### Gaps for "Complete Operational Flow"

**High priority:**

1. **Cancel action needs optimistic lock** — `WaitlistView.vue` cancel dialog sends no `expectedVersion`. Backend `CancelWaitlistEntryHandler` accepts `expectedVersion` in command but the view never sends it. Concurrent cancel attempts will silently overwrite.

2. **Resend action is missing from waitlist UI** — Backend has `ResendWaitlistInvitationHandler` and `POST /api/admin/invitations/{id}/resend`. Frontend has the i18n key (`waitlist.resend`) but no button in either view.

3. **Consent fields not rendered** — `AdminWaitlistEntryDetail` exposes `earlyAccessConsent`, `marketingConsent`, `consentVersion`. `WaitlistEntryView` skips all three. These are relevant for GDPR/account deletion review.

4. **Status count summary card missing** — `AdminWaitlistQuery.countByStatus()` exists; view doesn't call or display it. An operator needs this to know how many PENDING entries exist without filtering.

5. **Vitest specs missing** — No `WaitlistView.spec.ts` or `WaitlistEntryView.spec.ts`. Invitations slice has `DirectInvitationsView.spec.ts`.

**Medium priority:**

6. **Date-range filters not exposed** — Query supports `joinedFrom/joinedTo/invitedFrom/invitedTo`. View doesn't expose them.

7. **`waitlistKey`/`waitlistId` filter not exposed** — Useful for multi-waitlist scenarios.

8. **`metadataSummary` not displayed** — Backend provides it in detail; view ignores it.

9. **`entryId` not shown** — Useful for copy/reference. `WaitlistEntryView` navigates by `entryId` but doesn't display it.

10. **Audit trail not linked** — `AdminWaitlistEntryDetail.invitationHistory` has `createdBy` (operator UUID) but no operator display name. Admin view has an Audit view; could link or replicate operator lookup.

11. **Resend action needs UI surface** — `POST /api/admin/invitations/{invitationId}/resend` takes an invitation ID, not entry ID. Entry detail would need to fetch the active invitation ID first or expose the invitation row's resend button inline.

**Low priority (future-ready):**

12. **Sort controls** — Currently hardcoded to `joinedAt desc`. Could expose a sort dropdown (`invitedAt`, `status`, `email`).

13. **Source filter** — Waitlist has a `source` field; no filter for it.

### Risks

- **Resend routing gap** — Resend is at `POST /api/admin/invitations/{id}/resend` (invitation ID, not entry ID). The waitlist entry detail has an invitation list but the resend UX requires knowing the active invitation ID first. This is a frontend UX + API discovery problem.
- **Optimistic lock on cancel** — The cancel dialog doesn't send `expectedVersion`. While the backend handles the command parameter, the frontend will silently overwrite concurrent edits.
- **CountByStatus persistence** — Interface declares it; need to verify `R2dbcAdminWaitlistQuery` implements it with actual DB count.
- **Invitation revocation vs entry cancellation** — `WaitlistEntryView` revoke revokes the invitation but keeps the entry; cancel revokes the entry AND all invitations. These two are semantically different and both exist. Users may confuse them.

### Affected Areas

- `apps/web/admin/src/views/WaitlistView.vue` — add summary card, optimistic lock, resend, filters, spec
- `apps/web/admin/src/views/WaitlistEntryView.vue` — add consent fields, metadata, resend, entryId, spec
- `apps/web/admin/src/i18n/index.ts` — add missing keys (consent, metadata, summary)
- `server/smp/src/main/kotlin/.../platformadmin/infrastructure/http/AdminWaitlistController.kt` — add summary endpoint
- `server/smp/src/test/kotlin/.../AdminWaitlistControllerTest.kt` — add resend/cancel optimistic-lock test
- `server/smp/src/test/resources/features/platform-admin.feature` — add resend, cancel-with-lock, summary scenarios

### Recommendation

Scope the change as two phases within one SDD:

1. **Foundation** — add optimistic lock to cancel, add status summary, add resend to entry detail, render consent fields, add Vitest specs. These are all additive with no breaking changes.
2. **Polish** — date-range filters, `waitlistKey` filter, `metadataSummary` display, entryId display. Lower urgency.

Start with a proposal that covers both phases but locks only the foundation scope for sprint 1.

### Ready for Proposal

**Yes.** The codebase is well-understood. The gaps are concrete and actionable. The invitations slice (dallay-567) provides the established pattern to follow. Recommend proceeding to `sdd-propose` with scope: optimistic-lock cancel, status summary, resend UX, consent display, and Vitest coverage.
