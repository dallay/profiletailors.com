# Proposal: Establish Administrative Authorization Boundary for Back Office APIs

## Intent

Formalize and harden the administrative authorization boundary for Back Office (`/api/admin/**`) APIs by establishing a permission-based access model that supports evolution beyond a single ADMIN role while enforcing default-deny behavior. Current code has `platformadmin` infrastructure with `PlatformPermission` and `PlatformRole` enums plus `OperatorAccessResolver`, but lacks a formal OpenSpec contract, explicit default-deny enforcement, and independent test coverage for authorized/unauthorized paths.

## Scope

### In Scope
- Define `admin-authorization` as a new OpenSpec capability documenting the Back Office permission model
- Register all 15 existing `PlatformPermission` keys in the permission registry
- Document `PlatformRole` taxonomy: `PLATFORM_OWNER`, `PLATFORM_OPERATOR`, `SUPPORT_AGENT`, `AUDITOR`
- Document `PLATFORM_ROLE_PERMISSIONS` role-to-permission mapping
- Add explicit default-deny policy for admin operations where missing
- Verify `OperatorAccessResolver.resolve()` returns empty/denied when no role assignment exists
- Write unit tests for `OperatorAccessResolver` covering granted, denied, and unresolved principal cases
- Write integration tests for admin controllers covering authorized and unauthorized access paths

### Out of Scope
- Designing full enterprise IAM (delegated to IAM platform spec)
- Frontend route guards as primary enforcement layer
- Implementing speculative operator roles beyond the existing four
- Changes to workspace-scoped authorization (governed by `openspec/specs/iam/spec.md`)

## Approach

### Existing Model (Hexagonal Architecture)

```
platformadmin bounded context
├── domain/
│   ├── PlatformPermission   (15 enum entries)
│   ├── PlatformRole          (4 enum entries: OWNER, OPERATOR, SUPPORT_AGENT, AUDITOR)
│   ├── PLATFORM_ROLE_PERMISSIONS (Map<Role, Set<Permission>>)
│   └── PlatformAccessDeniedException
├── application/
│   └── OperatorAccessResolver  → resolves PrincipalContext → OperatorAccess(principalId, roles)
└── infrastructure/http/
    └── Admin*Controller (8 controllers) — check permission via effectivePermissions()
```

### Required Changes

1. **New OpenSpec capability** `openspec/specs/admin-authorization/spec.md` documenting:
   - Permission registry entries for all 15 `PlatformPermission` keys
   - Role-permission mapping table
   - Default-deny enforcement rule
   - `OperatorAccessResolver` behavioral contract

2. **Default-deny enforcement**:
   - `OperatorAccessResolver.resolve()` MUST return `OperatorAccess` with empty roles when no `PlatformRoleAssignment` exists for the principal
   - Controllers already throw `PlatformAccessDeniedException` when required permission is absent — verify this path is consistent

3. **Test coverage**:
   - Unit tests: `OperatorAccessResolver` — with assignment, without assignment, with revoked assignment
   - Integration tests: admin endpoints with authorized token vs. unauthorized token

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `openspec/specs/admin-authorization/spec.md` | New | Formalizes admin authorization contract |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformPermission.kt` | Modified | Documented in OpenSpec; code unchanged |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformRole.kt` | Modified | Documented in OpenSpec; code unchanged |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/OperatorAccessResolver.kt` | Modified | Add explicit empty-roles default-deny return |
| `server/smp/src/test/kotlin/.../platformadmin/` | New | Authorization unit and integration tests |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Pre-existing compilation errors in `BulkPublishingHandlers.kt` block test execution | High | The issue is in `publishing/` bounded context, not `platformadmin`. Tests can be scoped to `platformadmin` package. |
| `PLATFORM_OWNER` has all permissions — overly broad for initial release | Medium | Phase 1 maps to single-admin; scope limitation documented in proposal. Fine-grained OWNER reduction deferred. |

## Rollback Plan

1. Revert any changes to `OperatorAccessResolver`
2. Remove new test files under `platformadmin` test directory
3. Archive (do not delete) new `openspec/specs/admin-authorization/` capability spec
4. Existing `platformadmin` controllers return to prior behavior (authentication required, permission checks as-implemented)

## Dependencies

- None — worktree is on `feature/dallay-563-establish-administrative-authorization-boundary-for-back`

## Success Criteria

- [ ] `openspec/specs/admin-authorization/spec.md` exists with permission registry, role-permission mapping, and behavioral contract
- [ ] `OperatorAccessResolver.resolve()` returns `OperatorAccess(principalId, emptySet())` when no role assignment exists
- [ ] Unit tests cover: principal with role, principal without role, revoked assignment
- [ ] Integration tests cover: authorized request (has permission) vs. unauthorized request (no permission)
- [ ] `just backend-test-fast --tests "*platformadmin*"` passes (scoped to avoid pre-existing publishing compilation issues)

---

## Evidence: Test Infrastructure Check

```
$ just backend-test-fast

> Task :server:smp:compileKotlin
e: BulkPublishingHandlers.kt:19:49 Unresolved reference 'BulkImportJobRepository'
e: BulkPublishingHandlers.kt:33:1 Class 'ValidateBulkHandler' is not abstract and does not implement abstract member
... (12 additional errors in publishing/ bounded context)

BUILD FAILED — pre-existing compilation errors in publishing/ bounded context

Resolution: Scope test execution to platformadmin package only or fix publishing/ compilation errors separately.
```
