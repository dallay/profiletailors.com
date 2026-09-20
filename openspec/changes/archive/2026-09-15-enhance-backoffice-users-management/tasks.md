# Tasks: enhance-backoffice-users-management

## Review Workload Forecast

| Field                   | Value                                                                                  |
|-------------------------|----------------------------------------------------------------------------------------|
| Estimated changed lines | ~1800–2200                                                                             |
| 400-line budget risk    | High                                                                                   |
| Chained PRs recommended | Yes                                                                                    |
| Suggested split         | PR 1 (permissions + schema + domain) → PR 2 (handlers + controllers) → PR 3 (frontend) |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                      | Likely PR | Notes                                                                              |
|------|-------------------------------------------|-----------|------------------------------------------------------------------------------------|
| 1    | Permissions + domain + schema             | PR 1      | Base: main; includes PrincipalStatus, permissions, Flyway migration                |
| 2    | Handlers + controllers + summary endpoint | PR 2      | Base: PR 1; includes DeactivateUserHandler, ReactivateUserHandler, PATCH endpoints |
| 3    | Frontend views + dialogs + tests          | PR 3      | Base: PR 2; includes UsersView enrichments, UserDetailView actions, Vitest, BDD    |

---

## ⚠️ OPEN QUESTIONS — PRE-APPLY BLOCKERS

These questions MUST be resolved before Phase 1 starts. Do not write handler/controller code until
answers are confirmed.

| #    | Question                                                                                                             | Impact              | Owner                |
|------|----------------------------------------------------------------------------------------------------------------------|---------------------|----------------------|
| OQ-1 | **Schema: is there a `status` column on `principals` table?** If not, need Flyway migration before any handler code. | Tasks 2.3, 3.1, 3.2 | Backend schema owner |
| OQ-2 | **API key vs session revocation:** on deactivate, do we invalidate API keys, sessions, or both?                      | Task 3.3            | Backend auth owner   |
| OQ-3 | **operatorId ownership:** how is operatorId injected into DeactivateUserHandler/ReactivateUserHandler?               | Tasks 3.3, 3.4      | Backend architect    |
| OQ-4 | **Audit event mechanism:** existing `AdministrativeAuditPublisher` pattern or new `AdminAuditEvent`?                 | Tasks 3.3, 3.4, 7.2 | Backend architect    |

---

## Phase 1: Domain + Permissions + Schema (PR 1)

### 1.1 Domain: PrincipalStatus enum

- [ ] 1.1 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/domain/PrincipalStatus.kt` with
  `ACTIVE`, `INACTIVE`, `SUSPENDED` enum values
- [ ] 1.2 Add `PrincipalStatusTest.kt` covering enum properties and `valueOf()` behavior

### 1.2 Permissions: Add new PlatformPermission entries

- [ ] 1.3 Add `USERS_DEACTIVATE("platform.users.deactivate")` to `PlatformPermission` enum in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformPermission.kt`
- [ ] 1.4 Add `USERS_REACTIVATE("platform.users.reactivate")` to `PlatformPermission` enum
- [ ] 1.5 Add `platform.users.deactivate` and `platform.users.reactivate` to
  `PLATFORM_ROLE_PERMISSIONS` for `PLATFORM_OWNER` and `PLATFORM_OPERATOR` roles
- [ ] 1.6 Verify `PLATFORM_OWNER` and `PLATFORM_OPERATOR` include new permissions; `SUPPORT_AGENT`
  and `AUDITOR` do not
- [ ] 1.7 Add `PlatformPermissionTest.kt` scenarios for new permission registration and role mapping
  (see `openspec/changes/enhance-backoffice-users-management/specs/admin-authorization/spec.md`)

### 1.3 Schema: Flyway migration (OQ-1)

- [ ] 1.8 Inspect `server/smp/src/main/resources/db/migration/` for existing principals table
  structure
- [ ] 1.9 If `status` column does not exist: create
  `V{version}__add_principal_status_and_row_version.sql` migration adding:
    - `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`
    - `row_version BIGINT NOT NULL DEFAULT 0`
- [ ] 1.10 If `row_version` column does not exist: extend migration or create follow-up migration
- [ ] 1.11 Add R2DBC schema sync annotation or configuration to handle reactive migration

---

## Phase 2: Query Contracts + Summary Endpoint (PR 2)

### 2.1 Query enrichments: AdminUserQuery

- [ ] 2.1 Add `version: Long` to `AdminUserDetail` response model in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/model/AdminUserDetail.kt`
- [ ] 2.2 Add `consentReceipt`, `marketingConsent`, `authenticationMethods` fields to
  `AdminUserDetail`
- [ ] 2.3 Add `workspaceCount`, `lastAuthenticatedAt` to `AdminUserSummary` model
- [ ] 2.4 Update `AdminUserQuery` implementation to fetch enriched fields from R2DBC query

### 2.2 Summary endpoint: GET /api/admin/users/summary

- [ ] 2.5 Create `AdminUsersSummaryResponse` DTO in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/model/`
- [ ] 2.6 Create `GetAdminUsersSummaryQuery` in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/query/`
- [ ] 2.7 Implement summary query using R2DBC `DatabaseClient` with GROUP BY on `status` and
  `principal_type`
- [ ] 2.8 Add `summary()` method to `AdminUserQuery` contract interface
- [ ] 2.9 Add `GET /summary` route to `AdminUserController`
- [ ] 2.10 Add `AdminUserControllerTest` scenarios for `/summary` endpoint

---

## Phase 3: Handler Unit Tests RED (PR 2)

### 3.1 DeactivateUserHandlerTest RED

- [ ] 3.1 Create
  `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/DeactivateUserHandlerTest.kt`
- [ ] 3.2 Write failing test:
  `operator without USERS_DEACTIVATE permission throws PlatformAccessDeniedException`
- [ ] 3.3 Write failing test:
  `deactivate updates principal status to INACTIVE and increments rowVersion` (OQ-1, OQ-2, OQ-3)
- [ ] 3.4 Write failing test: `deactivate emits USER_DEACTIVATED audit event` (OQ-4)
- [ ] 3.5 Write failing test: `version mismatch throws OptimisticLockingFailureException`
- [ ] 3.6 Write failing test:
  `deactivating already INACTIVE user throws InvalidStateTransitionException`

### 3.2 ReactivateUserHandlerTest RED

- [ ] 3.7 Create
  `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/ReactivateUserHandlerTest.kt`
- [ ] 3.8 Write failing test:
  `operator without USERS_REACTIVATE permission throws PlatformAccessDeniedException`
- [ ] 3.9 Write failing test:
  `reactivate updates principal status to ACTIVE and increments rowVersion`
- [ ] 3.10 Write failing test: `reactivate emits USER_REACTIVATED audit event`
- [ ] 3.11 Write failing test: `reactiving non-INACTIVE user throws InvalidStateTransitionException`

---

## Phase 4: Handler GREEN — Commands + Handlers + Controllers (PR 2)

### 4.1 Commands

- [ ] 4.1 Create `DeactivateUserCommand` in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/command/DeactivateUserCommand.kt`
  with fields: `principalId: UUID`, `version: Long`, `operatorId: UUID`
- [ ] 4.2 Create `ReactivateUserCommand` in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/command/ReactivateUserCommand.kt`
  with fields: `principalId: UUID`, `version: Long`, `operatorId: UUID`

### 4.2 Handlers

- [ ] 4.3 Create `DeactivateUserHandler` in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/DeactivateUserHandler.kt`
  following `AssignPlatformRoleHandler` pattern:
    - Permission check using `effectivePermissions()`
    - Fetch principal by ID
    - Validate status transition (only ACTIVE → INACTIVE allowed)
    - Optimistic locking via `row_version` check
    - Update status and increment `row_version`
    - Publish `USER_DEACTIVATED` audit event (OQ-4)
- [ ] 4.4 Create `ReactivateUserHandler` in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/ReactivateUserHandler.kt`:
    - Permission check: `USERS_REACTIVATE`
    - Only INACTIVE → ACTIVE transition allowed
    - Optimistic locking and audit event publishing
- [ ] 4.5 Implement API key/session revocation logic based on OQ-2 resolution

### 4.3 Controllers

- [ ] 4.6 Add `PATCH /{principalId}/deactivate` route to `AdminUserController`:
    - Extract `If-Match` header for version
    - Extract `operatorId` from request context (OQ-3)
    - Call `DeactivateUserHandler.handle()`
    - Return `200 OK` or `409 Conflict` on version mismatch
- [ ] 4.7 Add `PATCH /{principalId}/reactivate` route to `AdminUserController`
- [ ] 4.8 Add `AdminUserControllerTest` scenarios for both PATCH endpoints (success, 401, 403, 404,
  409)
- [ ] 4.9 Verify controller tests pass

---

## Phase 5: Backend Integration Tests (PR 2)

### 5.1 Repository mocks and integration

- [ ] 5.1 Update `PrincipalRepository` test doubles to support `status` and `row_version` fields
- [ ] 5.2 Add repository test for `findByIdWithLock` or equivalent pessimistic lock pattern
- [ ] 5.3 Add repository test for status update with version increment

### 5.2 Handler verification

- [ ] 5.4 Verify `DeactivateUserHandlerTest` all scenarios pass
- [ ] 5.5 Verify `ReactivateUserHandlerTest` all scenarios pass
- [ ] 5.6 Run `just backend-check` (Detekt, tests, build) — all must pass

---

## Phase 6: Frontend Unit Tests RED (PR 3)

### 6.1 UsersView Vitest RED

- [ ] 6.1 Create `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/AdminUsersBddSteps.kt`
  first for BDD context
- [ ] 6.2 Create `apps/web/admin/src/views/UsersView.spec.ts`
- [ ] 6.3 Write failing test: `summary card renders with status counts`
- [ ] 6.4 Write failing test: `status filter dropdown triggers API call with status param`
- [ ] 6.5 Write failing test:
  `principalType filter dropdown triggers API call with principalType param`
- [ ] 6.6 Write failing test: `workspaceCount column renders in table`
- [ ] 6.7 Write failing test: `authenticationMethods column renders in table`

### 6.2 UserDetailView Vitest RED

- [ ] 6.8 Create `apps/web/admin/src/views/UserDetailView.spec.ts`
- [ ] 6.9 Write failing test: `detail view renders authenticationMethods list`
- [ ] 6.10 Write failing test: `detail view renders consent status section`
- [ ] 6.11 Write failing test: `deactivate button visible for ACTIVE users with permission`
- [ ] 6.12 Write failing test: `reactivate button visible for INACTIVE users with permission`

### 6.3 DeactivateDialog Vitest RED

- [ ] 6.13 Create `apps/web/admin/src/components/DeactivateUserDialog.spec.ts`
- [ ] 6.14 Write failing test: `renders confirmation with user email`
- [ ] 6.15 Write failing test: `emits confirm with version on submit`
- [ ] 6.16 Write failing test: `emits close on cancel`
- [ ] 6.17 Write failing test: `displays loading state during PATCH request`

### 6.4 ReactivateDialog Vitest RED

- [ ] 6.18 Create `apps/web/admin/src/components/ReactivateUserDialog.spec.ts`
- [ ] 6.19 Write failing test: `renders confirmation with user email`
- [ ] 6.20 Write failing test: `emits confirm with version on submit`

---

## Phase 7: Frontend GREEN — Views + Dialogs (PR 3)

### 7.1 UsersView enrichments

- [ ] 7.1 Add `status`, `principalType`, `createdFrom`, `createdTo` reactive refs and filter state
- [ ] 7.2 Add `summary` reactive ref for summary card data
- [ ] 7.3 Add `fetchSummary()` function calling `GET /api/admin/users/summary`
- [ ] 7.4 Add summary card UI above table with counts by status and principalType
- [ ] 7.5 Add status dropdown filter with ACTIVE/INACTIVE/SUSPENDED options
- [ ] 7.6 Add principalType dropdown filter with options from API
- [ ] 7.7 Add date-range inputs for createdFrom/createdTo
- [ ] 7.8 Update `fetchUsers()` to include all filter params in API call
- [ ] 7.9 Add `workspaceCount` column to table
- [ ] 7.10 Add `authenticationMethods` column to table (join with comma separator)
- [ ] 7.11 Add `lastAuthenticatedAt` column formatting (date only, "Never" for null)
- [ ] 7.12 Add platform roles badge display

### 7.2 UserDetailView actions

- [ ] 7.13 Add `version` to `AdminUserDetail` interface
- [ ] 7.14 Add `consentReceipt`, `marketingConsent` to interface and fetch
- [ ] 7.15 Add `authenticationMethods` rendering as list with icons
- [ ] 7.16 Add consent section rendering consent receipt status and marketing consent toggle
- [ ] 7.17 Add `DeactivateUserDialog` component import and usage
- [ ] 7.18 Add `ReactivateUserDialog` component import and usage
- [ ] 7.19 Add `deactivating`, `reactivating` ref state for dialog loading
- [ ] 7.20 Add `deactivateError`, `reactivateError` ref state for error display
- [ ] 7.21 Add `handleDeactivate()` function calling `PATCH /api/admin/users/{id}/deactivate`
- [ ] 7.22 Add `handleReactivate()` function calling `PATCH /api/admin/users/{id}/reactivate`
- [ ] 7.23 Add conditional rendering: show Deactivate button if status=ACTIVE, show Reactivate
  button if status=INACTIVE
- [ ] 7.24 Add permission check: only show action buttons if operator has
  USERS_DEACTIVATE/USERS_REACTIVATE

### 7.3 Dialog components

- [ ] 7.25 Create `DeactivateUserDialog.vue` following `RevokeInvitationDialog.vue` pattern:
    - Props: `open`, `principalId`, `email`, `expectedVersion`, `pending`, `error`
    - Emits: `confirm`, `close`
    - Confirmation text with email
    - Cancel and Confirm buttons
- [ ] 7.26 Create `ReactivateUserDialog.vue` following same pattern
- [ ] 7.27 Add i18n keys for dialog titles, confirm text, button labels (EN + ES)

### 7.4 Frontend verification

- [ ] 7.28 Verify all UsersView.spec.ts tests pass
- [ ] 7.29 Verify all UserDetailView.spec.ts tests pass
- [ ] 7.30 Verify DeactivateUserDialog.spec.ts tests pass
- [ ] 7.31 Verify ReactivateUserDialog.spec.ts tests pass
- [ ] 7.32 Run `just admin-check` — type-check must pass
- [ ] 7.33 Run `just admin-test` — all Vitest tests must pass

---

## Phase 8: BDD Integration Tests (PR 3)

### 8.1 Feature file

- [ ] 8.1 Create `server/smp/src/test/resources/features/admin-user-management.feature` with
  scenarios:
    - `@smoke @admin-users @fast @postgres` tag
    - Background: authenticated platform operator with `PLATFORM_OPERATOR`
    - Scenario: List users returns 200 with pagination
    - Scenario: Filter users by ACTIVE status
    - Scenario: Filter users by INACTIVE status
    - Scenario: Get user summary returns counts by status
    - Scenario: Deactivate user returns 200 and updates status
    - Scenario: Deactivate without permission returns 403
    - Scenario: Deactivate with stale version returns 409
    - Scenario: Reactivate user returns 200 and updates status
    - Scenario: Reactivate without permission returns 403

### 8.2 BDD steps

- [ ] 8.2 Extend `PlatformAdminScenarioState` with `lastPrincipalId`, `lastPrincipalStatus`,
  `lastPrincipalVersion`
- [ ] 8.3 Create `AdminUserManagementBddSteps.kt` in
  `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/`
- [ ] 8.4 Implement `Given` steps: "a user with status ACTIVE/INACTIVE exists", "a suspended user
  exists"
- [ ] 8.5 Implement `When` steps: "the operator deactivates the user", "the operator reactivates the
  user", "the operator requests users summary"
- [ ] 8.6 Implement `Then` steps: "the user status should be INACTIVE/ACTIVE", "the response should
  contain summary with N active users"
- [ ] 8.7 Verify all BDD scenarios pass

---

## Phase 9: Quality Gates (PR 3)

### 9.1 Backend gates

- [ ] 9.1 Run `just backend-check` — Detekt + tests must pass (excluding slow BDD by design)
- [ ] 9.2 Run `just backend-bdd-fast` — BDD smoke suite must pass
- [ ] 9.3 Run `just infra-up && just backend-bdd-postgres` — PostgreSQL BDD suite (if changes affect
  persistence)

### 9.2 Frontend gates

- [ ] 9.4 Run `just admin-check` — type-check must pass
- [ ] 9.5 Run `just admin-test` — Vitest suite must pass
- [ ] 9.6 Run `just admin-build` — production build must succeed

### 9.3 Full pipeline

- [ ] 9.7 Run `just ci-local` — full local CI pipeline must pass
- [ ] 9.8 Inspect diff for: no new suppressions, no weakened rules, no baseline additions

---

## Phase 10: Documentation + Cleanup (PR 3)

### 10.1 Documentation

- [ ] 10.1 Update `openspec/changes/enhance-backoffice-users-management/state.yaml` with completed
  phases
- [ ] 10.2 If ADR needed for OQ-1 (schema decision) or OQ-2 (revocation scope), create in
  `docs/architecture/adr/`
- [ ] 10.3 Update `docs/architecture/adr/README.md` index if new ADRs added

### 10.2 Cleanup

- [ ] 10.4 Remove any temporary debug code or println statements
- [ ] 10.5 Verify no commented-out code or TODO comments in changed files
- [ ] 10.6 Ensure all new files follow zero-comment policy

---

## File Inventory

### New Backend Files (12)

| File                                                           | Purpose                                |
|----------------------------------------------------------------|----------------------------------------|
| `identity/domain/PrincipalStatus.kt`                           | Enum: ACTIVE, INACTIVE, SUSPENDED      |
| `platformadmin/domain/DeactivateUserHandler.kt`                | Deactivate user with permission + lock |
| `platformadmin/domain/ReactivateUserHandler.kt`                | Reactivate user with permission + lock |
| `platformadmin/application/command/DeactivateUserCommand.kt`   | Command DTO                            |
| `platformadmin/application/command/ReactivateUserCommand.kt`   | Command DTO                            |
| `platformadmin/application/query/GetAdminUsersSummaryQuery.kt` | Summary query                          |
| `platformadmin/application/model/AdminUsersSummaryResponse.kt` | Summary DTO                            |
| `platformadmin/test/handler/DeactivateUserHandlerTest.kt`      | Handler unit tests                     |
| `platformadmin/test/handler/ReactivateUserHandlerTest.kt`      | Handler unit tests                     |
| `platformadmin/test/http/AdminUserControllerTest.kt` (update)  | Controller tests                       |
| `features/admin-user-management.feature`                       | BDD feature                            |
| `bdd/glue/AdminUserManagementBddSteps.kt`                      | BDD step definitions                   |

### Modified Backend Files (6)

| File                                                       | Changes                                  |
|------------------------------------------------------------|------------------------------------------|
| `platformadmin/domain/PlatformPermission.kt`               | Add USERS_DEACTIVATE, USERS_REACTIVATE   |
| `platformadmin/domain/PlatformRole.kt`                     | Update PLATFORM_ROLE_PERMISSIONS mapping |
| `platformadmin/application/model/AdminUserDetail.kt`       | Add version, consentReceipt, etc.        |
| `platformadmin/application/model/AdminUserSummary.kt`      | Add workspaceCount, lastAuthenticatedAt  |
| `platformadmin/application/contracts/AdminUserQuery.kt`    | Add summary() method                     |
| `platformadmin/infrastructure/http/AdminUserController.kt` | Add PATCH endpoints + summary            |

### New Frontend Files (5)

| File                                                | Purpose             |
|-----------------------------------------------------|---------------------|
| `admin/src/components/DeactivateUserDialog.vue`     | Confirmation dialog |
| `admin/src/components/ReactivateUserDialog.vue`     | Confirmation dialog |
| `admin/src/views/UsersView.spec.ts`                 | List view tests     |
| `admin/src/views/UserDetailView.spec.ts`            | Detail view tests   |
| `admin/src/components/DeactivateUserDialog.spec.ts` | Dialog tests        |
| `admin/src/components/ReactivateUserDialog.spec.ts` | Dialog tests        |

### Modified Frontend Files (2)

| File                                 | Changes                                 |
|--------------------------------------|-----------------------------------------|
| `admin/src/views/UsersView.vue`      | Filters, summary card, enriched columns |
| `admin/src/views/UserDetailView.vue` | Actions, consent, authMethods rendering |

### Schema (1)

| File                                                                | Changes                      |
|---------------------------------------------------------------------|------------------------------|
| `db/migration/V{version}__add_principal_status_and_row_version.sql` | Status + row_version columns |

---

## Implementation Order Rationale

1. **Phase 1 (Domain + Permissions + Schema)** first because all other work depends on
   `PrincipalStatus` enum, the permission constants, and the schema columns existing.
2. **Phase 2 (Query + Summary)** next because the frontend summary card needs the endpoint before it
   can write tests.
3. **Phase 3 (Handler Tests RED)** before handler implementation enforces TDD discipline.
4. **Phase 4 (Handler GREEN)** after tests are written — make them pass.
5. **Phase 5 (Integration)** verifies handlers work with repositories and controllers.
6. **Phase 6 (Frontend Tests RED)** before frontend implementation.
7. **Phase 7 (Frontend GREEN)** after tests are written.
8. **Phase 8 (BDD)** end-to-end validation after all units work.
9. **Phase 9 (Gates)** full CI validation.
10. **Phase 10 (Docs)** final cleanup.

---

## Next Step

**Ready for implementation** — chain PRs recommended. Before starting Phase 1, resolve the 4 open
questions (OQ-1 through OQ-4) with the backend team. Once resolved, begin with `sdd-apply` for Phase
1.
