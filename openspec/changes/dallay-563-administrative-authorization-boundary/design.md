# Design: `dallay-563-administrative-authorization-boundary`

## Technical Approach

Formalize the Back Office (`/api/admin/**`) authorization boundary by documenting the existing permission model in OpenSpec, hardening the `OperatorAccessResolver.resolve()` default-deny contract, and closing the test gaps identified in the proposal.

The implementation leverages the existing hexagonal structure already in `platformadmin`: `OperatorAccessResolver` as the application service, `PlatformRoleAssignmentRepository` as the port, `PlatformPermission` / `PlatformRole` as the domain model, and `effectivePermissions()` as the derived-permission utility. No new abstractions are introduced.

## Architecture Decisions

### Decision: Default-deny via empty-roles return from `resolve()`

**Choice**: `OperatorAccessResolver.resolve()` already returns `OperatorAccess(principalId, emptySet())` when `findActiveByPrincipalId` yields no assignments — `assignments.map { it.role }.toSet()` on an empty list produces `emptySet()`. No code change is required; the contract is already satisfied.
**Alternatives considered**: Add an explicit `if (assignments.isEmpty()) return OperatorAccess(principalId, emptySet())` guard — rejected as redundant noise.
**Rationale**: The current behavior already enforces default-deny; adding a comment or redundant branch would not reduce runtime behavior but would increase surface area. The behavioral guarantee is already exercised by the fact that `effectivePermissions()` on `emptySet()` yields `emptySet()`, causing every permission check to fail and every controller to throw `PlatformAccessDeniedException` or return 403.

### Decision: Consistent controller authorization pattern

**Choice**: Controllers that detect a missing permission throw `PlatformAccessDeniedException` (wired to 403 via `AdminProblemDetailsHandler`). `AdminDashboardController` is the sole outlier — it returns `ResponseEntity.status(HttpStatus.FORBIDDEN)` instead of throwing. Keep the throw pattern for new/changed code; no mass refactor of `AdminDashboardController`.
**Alternatives considered**: Refactor `AdminDashboardController` to throw — rejected because it is a behavioral change on an already-tested controller and is out of scope.
**Rationale**: The throw-via-`AdminProblemDetailsHandler` pattern is consistent across 7 of 8 controllers, maps cleanly to RFC 7807 Problem Detail responses, and is the pattern against which `AdminProblemDetailsHandlerTest` and all handler tests are written.

### Decision: Test scope — unit at resolver level, integration at controller level

**Choice**: Unit tests mock `PlatformRoleAssignmentRepository` directly. Controller integration tests use `WebTestClient` with a mocked `OperatorAccessResolver`.
**Alternatives considered**: Repository integration tests with a real database — rejected because `R2dbcPlatformRoleAssignmentRepositoryPostgresIntegrationTest` already covers the repository layer; duplicating persistence-layer coverage at this boundary adds no value.
**Rationale**: The resolver is a pure application service with a single dependency; mocking the repository is the right isolation level for a unit test. Controller tests need to exercise the full HTTP-to-handler path including `AdminProblemDetailsHandler` wiring.

## Data Flow

```
HTTP request (Authorization: Bearer <token>)
  → RequestContextStore.currentPrincipalContext()
  → OperatorAccessResolver.resolve(PrincipalContext)
    → PlatformRoleAssignmentRepository.findActiveByPrincipalId(UUID)
    → List<PlatformRoleAssignment>  (empty → default-deny)
    → OperatorAccess(principalId, roles)  (emptySet → effectivePermissions = emptySet)
  → Controller permission check: requiredPermission in operator.roles.effectivePermissions()
    → true  → handler executes
    → false → throw PlatformAccessDeniedException OR return 403
  → AdminProblemDetailsHandler.handle(PlatformAccessDeniedException)
    → 403 Problem Detail { type: "urn:profiletailors:error:PLATFORM_ACCESS_DENIED", code: "PLATFORM_ACCESS_DENIED" }
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `openspec/specs/admin-authorization/spec.md` | Create | New capability spec documenting the Back Office permission model |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/OperatorAccessResolverTest.kt` | Modify | Add two test cases: principal without role (default-deny), principal with multiple roles |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminOperatorControllerTest.kt` | Modify | Add test: listOperators returns 403 when operator has no role assignment (empty roles) |

**No production code changes are required** — the default-deny behavior is already implemented.

## Interfaces / Contracts

### `OperatorAccessResolver.resolve()` — behavioral contract

```kotlin
@Service
class OperatorAccessResolver(private val roleAssignmentRepository: PlatformRoleAssignmentRepository) {
    suspend fun resolve(principal: PrincipalContext): OperatorAccess {
        val principalId = PlatformPrincipalIds.toUuid(principal.principalId)
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(principalId)
        val roles = assignments.map { it.role }.toSet()
        return OperatorAccess(principalId, roles)
    }
}
```

**Contract**:
- Returns `OperatorAccess(principalId, emptySet())` when `findActiveByPrincipalId` returns an empty list (default-deny).
- Returns `OperatorAccess(principalId, {role1, role2, ...})` when assignments exist.
- Does not throw — permission enforcement is the caller's responsibility.

### `effectivePermissions()` extension

```kotlin
fun Set<PlatformRole>.effectivePermissions(): Set<PlatformPermission> =
    flatMap { PLATFORM_ROLE_PERMISSIONS[it] ?: emptySet() }.toSet()
```

**Contract**: Always returns a `Set<PlatformPermission>`, possibly empty. An empty result means the operator has no permissions.

### `PlatformAccessDeniedException`

```kotlin
class PlatformAccessDeniedException(permission: PlatformPermission) :
    RuntimeException("Platform permission required: ${permission.key}")
```

Mapped to HTTP 403 via `AdminProblemDetailsHandler`.

## Testing Strategy

### Unit tests — `OperatorAccessResolverTest`

| Scenario | Mock | Assertion |
|----------|------|-----------|
| Principal has one `PLATFORM_OWNER` assignment | `findActiveByPrincipalId` → `[assignment(role=OWNER)]` | `roles == {PLATFORM_OWNER}` |
| Principal has no assignment (default-deny) | `findActiveByPrincipalId` → `[]` | `roles == emptySet()` |
| Principal has multiple roles (`OPERATOR` + `AUDITOR`) | `findActiveByPrincipalId` → `[assignment(OPERATOR), assignment(AUDITOR)]` | `roles == {OPERATOR, AUDITOR}` |
| Prefixed principal ID (`user-<uuid>`) is stripped before repo lookup | `findActiveByPrincipalId` called with bare UUID | Repo called with correct UUID |

### Integration tests — `AdminOperatorControllerTest`

| Scenario | Mock | Expected HTTP |
|----------|------|--------------|
| Operator has no role (empty roles) calling `GET /api/admin/operators` | `resolve()` → `OperatorAccess(id, emptySet())` | 403 with `PLATFORM_ACCESS_DENIED` code |
| Operator has `OPERATORS_READ` permission | `resolve()` → `OperatorAccess(id, {PLATFORM_OWNER})` | 200 with operator list |
| No principal context | `resolve()` returns `null` via `resolveOperator()` | 401 |

## Migration / Rollout

No migration required. This change is purely additive: documenting existing behavior and adding tests. The default-deny guarantee is already live.

## Open Questions

- [x] `AdminDashboardController.getDashboard()` returns `ResponseEntity.status(HttpStatus.FORBIDDEN)` instead of throwing `PlatformAccessDeniedException` — this is a known inconsistency with the other 7 admin controllers. It is out of scope for this change (no mass refactor of an already-tested controller). Flagged for future review.
- [ ] Should `AdminDashboardController.getDashboard()` be refactored to throw `PlatformAccessDeniedException` for consistency with the other 7 controllers? If yes, it would be a scope addition to this change.
