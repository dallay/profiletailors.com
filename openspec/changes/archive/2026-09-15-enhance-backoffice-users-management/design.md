# Design: enhance-backoffice-users-management

## Technical Approach

Backend-first implementation following the established `platformadmin` hexagonal slice (Invitation,
WaitlistInvitation) patterns. A new `PrincipalStatus` enum (`ACTIVE`, `INACTIVE`, `SUSPENDED`) is
introduced on the `principals` table alongside a `row_version` column for optimistic locking. The
two mutation endpoints use `PATCH` semantics; the list query is enriched to support
status/principalType filters; a summary endpoint mirrors the existing dashboard pattern.

## Architecture Decisions

### Decision: `version` field lives on the `principals` table, not a separate entity

**Choice**: Add `status` (`VARCHAR`) and `row_version` (`BIGINT`) columns to the `principals` table.
**Alternatives considered**: Separate `principal_states` table; version on `user_identities`.
**Rationale**: Mirrors the `Invitation` aggregate pattern where `version` lives on the aggregate
root. A separate state table would require an extra join on every query. `user_identities` is a
different aggregate.

### Decision: `PrincipalStatus` enum lives in the identity domain, not platformadmin

**Choice**: `com.profiletailors.smp.identity.domain.PrincipalStatus` with `ACTIVE`, `INACTIVE`,
`SUSPENDED`. **Alternatives considered**: In `platformadmin.domain`. **Rationale**: User lifecycle
status is a cross-cutting identity concept; `platformadmin` consumes it but does not own the
definition.

### Decision: Permission check happens in handler, not filter

**Choice**: `DeactivateUserHandler` and `ReactivateUserHandler` enforce
`PlatformPermission.USERS_DEACTIVATE` / `USERS_REACTIVATE` directly, matching the
`AssignPlatformRoleHandler` pattern. **Alternatives considered**: Dedicated WebFilter for these
endpoints. **Rationale**: Consistent with existing mutation handlers; keeps authorization logic
co-located with domain logic.

### Decision: Session/token invalidation is handled inside the handler via the existing
`RefreshSessionGateway`

**Choice**: On deactivation, call `invalidateAllSessionsForPrincipal(principalId)` from within
`DeactivateUserHandler`. **Alternatives considered**: Fire-and-forget domain event; external
token-revocation service. **Rationale**: Existing
`RefreshSessionGateway.invalidateAllSessionsForPrincipal()` already exists in `credentials`
infrastructure. Co-locating the call keeps the effect synchronous and verifiable.

### Decision: Summary endpoint returns a flat map, not a nested structure

**Choice**: `GET /api/admin/users/summary` returns `Map<String, Map<String, Long>>` keyed by
status → principalType → count. **Alternatives considered**: Dedicated `UserSummaryResponse` DTO
with nested `statusCounts` / `typeCounts`. **Rationale**: Matches the pattern used by
`AdminDashboardController`'s quick-stat endpoints; avoids an extra DTO class.

## Data Flow

```
AdminUserController (PATCH /deactivate)
  └─→ DeactivateUserHandler.handle(DeactivateUserCommand)
        ├─→ permission check (PlatformPermission.USERS_DEACTIVATE)
        ├─→ PrincipalRepository.findById(principalId)     ← domain port
        ├─→ principal.deactivate(expectedVersion)        ← domain logic
        ├─→ RefreshSessionGateway.invalidateAll(principalId)
        ├─→ PrincipalRepository.save(principal)         ← writes status + increments version
        └─→ AdminAuditPublisher.publish(USER_DEACTIVATED)

AdminUserController (GET /summary)
  └─→ AdminUserQuery.countByStatusAndType()
        └─→ R2dbcAdminUserQuery (SELECT status, principal_type, COUNT(*) GROUP BY …)
```

## File Changes

| File                                                                                                                          | Action | Description                                                                                                                                                      |
|-------------------------------------------------------------------------------------------------------------------------------|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/domain/PrincipalStatus.kt`                                        | Create | `ACTIVE`, `INACTIVE`, `SUSPENDED` enum                                                                                                                           |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/command/UserMutationCommands.kt`                 | Create | `DeactivateUserCommand`, `ReactivateUserCommand`                                                                                                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PrincipalStateExceptions.kt`                          | Create | `UserAccountDeactivationConflictException`, `UserAccountReactivationConflictException`, `PrincipalNotDeactivatableException`, `PrincipalNotReactivableException` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformPermission.kt`                                | Modify | Add `USERS_DEACTIVATE`, `USERS_REACTIVATE`; update `PLATFORM_ROLE_PERMISSIONS`                                                                                   |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/PrincipalStateRepository.kt`           | Create | Port interface: `findByIdForUpdate`, `save`                                                                                                                      |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/DeactivateUserHandler.kt`                | Create | Permission check → domain deactivate → session invalidation → audit                                                                                              |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/ReactivateUserHandler.kt`                | Create | Same structure for reactivate                                                                                                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/AdminUserQuery.kt`                     | Modify | Add `countByStatusAndType(): Map<String, Map<String, Long>>`                                                                                                     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/query/ListAdminUsersQuery.kt`                    | Modify | Add `principalType: String?`, `search: String?` fields                                                                                                           |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/model/AdminUserModels.kt`                        | Modify | Add `status`, `version`, `consentReceipt`, `marketingConsent` to both models; add `UsersSummary`                                                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcAdminUserQuery.kt`           | Modify | Filter by `status` (new column), `principalType`, `search`; enrich SELECT; implement `countByStatusAndType`                                                      |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcPrincipalStateRepository.kt` | Create | Implements `PrincipalStateRepository` using R2DBC; `SELECT … FOR UPDATE` for pessimistic safety                                                                  |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminUserController.kt`                  | Modify | Add `PATCH /{principalId}/deactivate`, `PATCH /{principalId}/reactivate`, `GET /summary`; update list/detail responses                                           |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminProblemDetailsHandler.kt`           | Modify | Map new exceptions → `409 Conflict` / `428 Precondition Required`                                                                                                |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcRefreshSessionGateway.kt`                  | Modify | Expose `invalidateAllForPrincipal(principalId: String)` publicly                                                                                                 |
| `server/smp/src/main/resources/db/migration/V{xxx}__add_principal_status_and_version.sql`                                     | Create | Add `status` (`VARCHAR`, default `'ACTIVE'`), `row_version` (`BIGINT`, default `0`) to `principals`; backfill existing rows                                      |
| `apps/web/admin/src/views/UsersView.vue`                                                                                      | Modify | Summary card, status/principalType dropdowns, date-range inputs, new columns, deactivate/reactivate row buttons                                                  |
| `apps/web/admin/src/views/UserDetailView.vue`                                                                                 | Modify | Auth methods list, consent fields, deactivate/reactivate action buttons                                                                                          |
| `apps/web/admin/src/components/dialogs/ConfirmDeactivateDialog.vue`                                                           | Create | Confirm dialog with expected version passthrough                                                                                                                 |
| `apps/web/admin/src/components/dialogs/ConfirmReactivateDialog.vue`                                                           | Create | Same pattern for reactivate                                                                                                                                      |
| `apps/web/admin/src/composables/useUsers.ts`                                                                                  | Create | `useUsers()`, `useUserSummary()`, `useDeactivateUser()`, `useReactivateUser()`                                                                                   |
| `apps/web/admin/src/i18n/index.ts`                                                                                            | Modify | EN + ES keys for summary, filters, dialogs, row actions                                                                                                          |
| `apps/web/admin/src/i18n/types.ts`                                                                                            | Modify | Extend `MessageSchema` with new keys                                                                                                                             |
| `server/smp/src/test/kotlin/…/DeactivateUserHandlerTest.kt`                                                                   | Create | Unit test: success, 409 on version mismatch, 403 on missing permission, idempotent (already inactive → no-op)                                                    |
| `server/smp/src/test/kotlin/…/ReactivateUserHandlerTest.kt`                                                                   | Create | Same structure                                                                                                                                                   |
| `server/smp/src/test/kotlin/…/AdminUserControllerTest.kt`                                                                     | Create | `WebTestClient`: 401/403/409/428 for both mutations, 200 for summary                                                                                             |
| `server/smp/src/test/resources/features/users.feature`                                                                        | Create | BDD: list with filters, detail with auth methods, deactivate → 200, 409, 403; reactivate similarly                                                               |
| `apps/web/admin/src/views/__tests__/UsersView.spec.ts`                                                                        | Create | Vitest: renders summary card, filter dropdowns, row actions                                                                                                      |
| `apps/web/admin/src/views/__tests__/UserDetailView.spec.ts`                                                                   | Create | Vitest: renders auth methods, consent fields, deactivate/reactivate buttons                                                                                      |

## Interfaces / Contracts

```kotlin
// Domain
enum class PrincipalStatus { ACTIVE, INACTIVE, SUSPENDED }

// Application layer — command
data class DeactivateUserCommand(
    val principalId: String,
    val expectedVersion: Long,
    val operatorId: UUID,
    val operatorRoles: Set<PlatformRole>,
)

data class ReactivateUserCommand(
    val principalId: String,
    val expectedVersion: Long,
    val operatorId: UUID,
    val operatorRoles: Set<PlatformRole>,
)

// Application layer — port
interface PrincipalStateRepository {
    suspend fun findByIdForUpdate(principalId: String): PrincipalState?
    suspend fun save(state: PrincipalState): PrincipalState
}

interface PrincipalState {
    val principalId: String
    val status: PrincipalStatus
    val version: Long
    fun deactivate(expectedVersion: Long): PrincipalState  // throws VersionConflict on mismatch
    fun reactivate(expectedVersion: Long): PrincipalState  // throws VersionConflict on mismatch
}

// Query extension
interface AdminUserQuery {
    suspend fun countByStatusAndType(): Map<String, Map<String, Long>>
}
```

## Testing Strategy

| Layer                  | What to Test                                                                               | Approach                                                                                      |
|------------------------|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Handler unit           | DeactivateUserHandler: success path, 409, 403, already-inactive idempotent                 | JUnit 5, fake `PrincipalStateRepository`, fake `RefreshSessionGateway`, fake `AuditPublisher` |
| Handler unit           | ReactivateUserHandler: same                                                                | Same structure                                                                                |
| Controller integration | PATCH deactivate: 200, 401, 403, 409 (version mismatch), 428 (missing version header), 404 | `WebTestClient`, in-memory DB or Testcontainers                                               |
| Controller integration | PATCH reactivate: same                                                                     | Same                                                                                          |
| Controller integration | GET summary: 200 with grouped counts                                                       | `WebTestClient`                                                                               |
| Query integration      | List with status/principalType/search filters                                              | `WebTestClient` against real schema                                                           |
| BDD                    | `users.feature`: all scenarios from spec                                                   | Cucumber with `@smoke @fast` tags, `@postgres` for mutation scenarios                         |
| Vitest                 | UsersView: renders summary, filters, row actions                                           | Unit test with mock API responses                                                             |
| Vitest                 | UserDetailView: renders auth methods, consent, action buttons                              | Unit test with mock API responses                                                             |

Quality gates: `just backend-check` + `just backend-bdd-fast` + `just admin-test` +
`just admin-build`.

## Migration / Rollback

A Flyway migration (`V{xxx}__add_principal_status_and_version.sql`) adds two nullable columns with
defaults (`status = 'ACTIVE'`, `row_version = 0`) and backfills existing rows. Rollback drops the
columns.

If the feature is reverted: drop the migration, remove controller endpoints, handlers, commands, and
exceptions. The existing `AdminUserSummary`/`AdminUserDetail` models revert to their prior shape —
no data loss since the new columns are on the principals table and the application simply stops
reading them.

## Open Questions

- [ ] Does `principals` already have a `status` column? The R2dbcAdminUserQuery comment says
  "principals have no lifecycle status today" — verify the actual schema.
- [ ] Should deactivation also revoke API key credentials via `ApiKeyCredentialStateGateway`? The
  spec says "revoke active sessions"; clarify whether API keys count as sessions.
- [ ] Is there an existing `PrincipalState` aggregate in the identity context that should be updated
  instead of a new domain object in platformadmin? The identity context owns principal lifecycle;
  platformadmin should be an adapter consuming it.
- [ ] Should `USER_DEACTIVATED` / `USER_REACTIVATED` audit events use the existing `AdminAuditEvent`
  mechanism or a domain event published through `EventPublisher`?
