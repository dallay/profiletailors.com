## Exploration: Back Office slice 3 — Direct-invitations management

### Current State

`DirectInvitationsView.vue` is a create-form-only page: email + target
(`EXISTING_WORKSPACE` / `NEW_WORKSPACE`) + optional workspaceId, then a
last-created card with resend / revoke actions. There is NO listing, NO
search/filter/pagination, NO detail lookup, NO empty/loading list states.
`canRead` (`platform.invitations.read`) is checked but only gates an
access-denied message — no read call is ever made from the view.

Backend (`AdminInvitationController`, `/api/admin/invitations`) exposes only:
`POST /direct` (create, 201, `{invitationId, status, expiresAt, version}` —
NO token), `POST /{id}/direct-revoke` (optimistic-lock `expectedVersion`),
`POST /{id}/direct-resend` (token rotation, version bump),
`GET /{id}` (single fetch, waitlist-shaped `AdminInvitationSummary`).
`AdminInvitationQuery` has only `findById`; `InvitationRepository` has
`findById / findBySourceReferenceId / findByCandidateKey(+ForUpdate) /
hasActiveInvitationFor` — no paged list. `R2dbcAdminInvitationQuery`
implements only `findById`.

Contrast: waitlist, users, and audit all follow the established
`list(query): PagedResult<T>` + `GET` collection (`page/size/sort/filters`)
pattern (`AdminWaitlistController.listEntries`,
`R2dbcAdminWaitlistQuery.list`). Direct invitations are the only live admin
area without a collection read.

Supporting seams already exist and need no new work: audit events
`INVITATION_CREATED / REVOKED / RESENT` (dallay-568, no raw tokens),
post-commit email via `InvitationIssued / DirectInvitationResent`
(dallay-565, `SendInvitationEmailConsumer`), duplicate-active guard
(409, expired rows never block), BDD `invitations-direct.feature`
(create/revoke/resend incl. 401/403/404/409), frontend spec covering form
validation, create, resend, revoke, no-token rendering, 409 message.

### Affected Areas

- `server/smp/.../platformadmin/application/contracts/AdminInvitationQuery.kt` — add list operation
- `server/smp/.../platformadmin/application/query/ListAdminDirectInvitationsQuery.kt` — new (mirror `ListAdminWaitlistEntriesQuery`)
- `server/smp/.../platformadmin/application/model/` — new direct-shaped summary (email, target, workspaceId, status, expiresAt, version); existing `AdminInvitationSummary` is waitlist-shaped (`waitlistEntryId`, delivery fields) and does not fit
- `server/smp/.../platformadmin/infrastructure/persistence/R2dbcAdminInvitationQuery.kt` — implement list against `invitations` table, `source = DIRECT`
- `server/smp/.../platformadmin/infrastructure/http/AdminInvitationController.kt` — new `GET /direct?page&size&status&email…` guarded by `INVITATIONS_READ`
- `apps/web/admin/src/views/DirectInvitationsView.vue` — add list section (table, status filter, email search, pagination) following `WaitlistView.vue` / `UsersView.vue` patterns
- `apps/web/admin/src/i18n/index.ts` + `es` block — list/filter/pagination/empty-state keys
- `apps/web/admin/src/views/DirectInvitationsView.spec.ts` — list rendering, filters, error/empty states
- `server/smp/src/test/resources/features/platformadmin/invitations-direct.feature` — list scenarios (auth, pagination, filters)
- `openspec/specs/` — platform-admin invitation capability deltas (propose phase to confirm)

### Approaches

1. **Full management: backend list + frontend table** — new paged `GET`
   collection on the `invitations` table (`source = DIRECT`, filters
   `status` + `email`, sort `issuedAt desc`) plus a list section in the view
   reusing the Waitlist/Users table patterns; row actions resend/revoke reuse
   existing endpoints.
   - Pros: closes the real gap; consistent with every other live admin area; reads existing table, no migration; BDD-extensible
   - Cons: largest surface (query + controller + view + i18n + tests)
   - Effort: Medium

2. **Frontend-only lookup by ID** — add an ID/email lookup reusing `GET /{id}`
   plus keep the create flow; no backend change.
   - Pros: smallest change; no backend review needed
   - Cons: no discovery or operational overview; operators must paste UUIDs; does not match "complete management"; `GET /{id}` returns waitlist-shaped summary
   - Effort: Low

3. **Full management + detail route + audit linkage** — approach 1 plus a
   per-invitation detail view (status timeline, delivery state, linked audit
   events via `AuditView` filter).
   - Pros: true operational parity (detail + history + audit trace)
   - Cons: depends on whether the audit query supports entity/invitation-id filtering (unverified); highest effort; detail route is a new nav surface
   - Effort: High

### Recommendation

Approach 1. It is the minimal change that makes direct-invitations
operationally complete (list → filter → act on rows with existing
resend/revoke), follows the proven waitlist/users collection pattern, and
needs no schema migration. Defer approach 3's detail/audit linkage to a
follow-up once the audit entity-filter question is answered in propose/spec.

### Risks

- `AdminInvitationSummary` shape mismatch: direct invitations need their own summary type; reusing the waitlist shape would leak `waitlistEntryId`/delivery semantics into direct rows
- Audit linkage scope creep: `INVITATION_*` events exist but per-invitation audit lookup is unverified — keep out of this slice
- Permission surface: list endpoint must enforce `INVITATIONS_READ`; row actions keep their existing `create/resend/revoke` gates — no new permissions needed
- User gave only "3" with no objective lines: full-management scope assumed; confirm in propose that listing + filters + row actions (no new backend commands) is the accepted scope

### Ready for Proposal

Yes — propose `expand-backoffice-invitations-management` with approach 1 scope:
paged direct-invitation listing (status filter, email search), row-level
resend/revoke via existing endpoints, empty/loading/error states, EN+ES i18n,
BDD + Vitest coverage. Explicitly out: new invitation commands, detail route,
audit deep-link, notification redelivery controls.
