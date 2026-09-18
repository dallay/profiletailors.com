# Tasks: Expand Backoffice Invitations Management

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 450–600 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR (backend slice → frontend slice → BDD, one branch) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Backend list slice green | PR 1 | Contract+persistence+controller+JUnit/WebTestClient; base main |
| 2 | Frontend list + BDD green | PR 1 | View+i18n+Vitest+feature; same PR, follows Unit 1 |

## Phase 1: Backend RED (failing tests first)

- [x] 1.1 Add failing `R2dbcAdminInvitationQueryTest.list` cases in `server/smp/.../platformadmin/` (filters, pagination, `created_at DESC`, WAITLIST excluded, no-token keys)
- [x] 1.2 Add failing `AdminInvitationControllerTest.GET /direct` cases (200 shape, 401, 403, 400 oversize, totals)

## Phase 2: Backend GREEN (make RED pass)

- [x] 2.1 Create `platformadmin/application/model/AdminDirectInvitationSummary.kt` (invitationId, email, target, workspaceId, status, expiresAt, version; no token fields)
- [x] 2.2 Add `ListAdminDirectInvitationsQuery` to `platformadmin/application/query/AdminQueries.kt` + `list()` to `platformadmin/application/contracts/AdminInvitationQuery.kt`
- [x] 2.3 Implement list in `platformadmin/infrastructure/persistence/R2dbcAdminInvitationQuery.kt` via `DatabaseClient` (`source='DIRECT'`, status equality, email LIKE on trimmed-lowercased input, fixed `ORDER BY created_at DESC`, `validatePagination`, `PagedResult.of`)
- [x] 2.4 Add `GET /direct` to `platformadmin/infrastructure/http/AdminInvitationController.kt` (`INVITATIONS_READ` guard, `ADMIN_PAGE_MAX_SIZE` check, `InvitationStatus` validation → 400)

## Phase 3: Frontend list + i18n (Vitest RED → GREEN)

- [x] 3.1 Add failing `apps/web/admin/src/views/DirectInvitationsView.spec.ts` list cases (render, filter re-query page 0, empty/loading/error, row actions refresh, `!canRead` zero requests)
- [x] 3.2 Add list section to `apps/web/admin/src/views/DirectInvitationsView.vue` (filters, table email/target/status/expiry, pagination, states, resend/revoke with `expectedVersion`)
- [x] 3.3 Add `directInvitations.list.*` keys EN+ES in `apps/web/admin/src/i18n/index.ts` + `types.ts`

## Phase 4: BDD + quality gates

- [x] 4.1 Add `src/test/resources/features/invitations-direct.feature` list scenarios (list, pagination, combined filters, 401/403, empty) + glue
- [x] 4.2 Run `just backend-test-fast`, `just backend-bdd-fast`, `just admin-test`, `just admin-check`, `just admin-build`, lint; fix Detekt/arch findings in changed code; confirm no migration needed
