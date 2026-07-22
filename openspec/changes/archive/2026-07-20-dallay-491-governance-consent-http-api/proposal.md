# Proposal: Governance Consent HTTP API

## Intent

Complete DALLAY-491 by delivering a production-ready, authenticated HTTP API for workspace-scoped
consent management. The API must preserve existing domain logic, idempotency guarantees, withdrawal
history, waitlist adapter integration, and R2DBC schema while resolving three critical blockers:
Flow materialization bug, missing security context binding, and authorization permission
registration. This enables GDPR-compliant consent collection and audit trail retrieval for Profile
Tailors workspaces.

## Scope

### In Scope

- Fix Flow materialization bug in query handlers (`.toList()` missing)
- Bind `principalId` and `workspaceId` from Spring Security context via `ResourceContextProvider`
- Register and verify `workspace:consent:read` permission in role definitions
- Clarify and test POST idempotent status semantics (201 vs 200)
- Add authorization tests for query handlers with permission verification
- Add Postgres integration tests for reactive query methods
- Add BDD scenarios covering complete HTTP flows
- Complete OpenAPI documentation with schemas, security requirements, examples
- Implement locale format validation and enum error handling
- Add end-to-end integration test with authenticated security context
- TDD enforcement: all implementation changes require failing tests first

### Out of Scope

- DALLAY-496 withdraw-all and bulk operations (deferred to separate change)
- Frontend consent UI components (separate change)
- Consent migration from legacy systems (separate change)
- Real-time consent event streaming (future enhancement)
- Multi-workspace consent aggregation (future enhancement)

## Capabilities

> Research conducted: existing consent domain, repository, and command handlers are complete. No
> changes needed to core consent capabilities. This change only adds HTTP adapter and query read
> models.

### New Capabilities

None — HTTP API is a new adapter over existing consent domain capability.

### Modified Capabilities

None — existing consent recording, withdrawal, and repository queries remain unchanged at the domain
level.

## Approach

**Production-Ready Completion Strategy** — Fix critical bugs, add comprehensive test coverage,
complete documentation, and verify authorization wiring before merge. Governance data is legally
sensitive; incomplete testing or authorization gaps are unacceptable risks.

### Technical Approach

1. **Critical Bug Fixes**:
    - Add `.toList()` to `GetWorkspaceConsentRecordsHandler` and `GetConsentHistoryHandler` Flow
      materialization
    - Clarify POST idempotent status: return 201 when newly created, 200 when already ACTIVE (fix
      inverted logic at line 103)
    - Add proper enum validation error handling (return 400 instead of 500)

2. **Security Context Integration**:
    - Remove `principalId` and `workspaceId` parameters from controller methods
    - Both values derived from `ResourceContextProvider.require()` in handlers (already implemented)
    - Query handlers already call `resourceContextProvider.require().workspaceId` — controllers
      trust this
    - Verify `ResourceContext` is populated by existing auth infrastructure (JWT filter)

3. **Authorization Verification**:
    - Confirm `workspace:consent:read` exists in permission registry
    - Add authorization handler tests with mocked `WorkspaceAuthorizationDecider`
    - Verify permission is assigned to OWNER, ADMIN roles (MEMBER excluded)
    - Test authorization denial path returns 403 with proper problem details

4. **Test Coverage**:
    - Keep existing `ConsentControllerWebTest` (9 tests covering HTTP contracts)
    - Add `GetWorkspaceConsentRecordsHandlerAuthorizationTest` (permission checks)
    - Add `GetConsentHistoryHandlerAuthorizationTest` (permission checks)
    - Add `R2dbcConsentRepositoryQueryIntegrationTest` (Postgres-backed)
    - Add BDD scenario: `consent-api.feature` (record → list → withdraw → history)
    - Add end-to-end integration test with `@SpringBootTest` and real auth context

5. **Documentation**:
    - Complete `@Schema` annotations on request/response DTOs
    - Add `@SecurityRequirement(name = "bearerAuth")` to all endpoints
    - Add request/response examples for typical flows
    - Document error responses (400, 401, 403, 404) with problem detail schemas

6. **Validation Improvements**:
    - Add locale format validation (ISO 639-1 language + optional ISO 3166-1 country)
    - Wrap enum `valueOf()` calls with try-catch, return 400 with clear message
    - Preserve existing `@NotBlank`, `@Size` validations

## Affected Areas

| Area                                                                                   | Impact   | Description                                                                |
|----------------------------------------------------------------------------------------|----------|----------------------------------------------------------------------------|
| `server/smp/.../governance/application/GetWorkspaceConsentRecordsHandler.kt`           | Modified | Add `.toList()` to Flow, remove workspace param                            |
| `server/smp/.../governance/application/GetConsentHistoryHandler.kt`                    | Modified | Add `.toList()` to Flow, remove workspace param                            |
| `server/smp/.../governance/infrastructure/http/ConsentController.kt`                   | Modified | Remove `principalId`/`workspaceId` params, fix status logic, enum handling |
| `server/smp/.../authorization/infrastructure/PermissionRegistry.kt`                    | Modified | Register `workspace:consent:read` if missing                               |
| `server/smp/.../authorization/infrastructure/RoleDefinitions.kt`                       | Modified | Assign permission to OWNER, ADMIN roles                                    |
| `server/smp/src/test/kotlin/.../GetWorkspaceConsentRecordsHandlerAuthorizationTest.kt` | New      | Authorization denial tests                                                 |
| `server/smp/src/test/kotlin/.../GetConsentHistoryHandlerAuthorizationTest.kt`          | New      | Authorization denial tests                                                 |
| `server/smp/src/test/kotlin/.../R2dbcConsentRepositoryQueryIntegrationTest.kt`         | New      | Postgres query integration tests                                           |
| `server/smp/src/test/resources/features/consent-api.feature`                           | New      | BDD scenarios for HTTP flows                                               |
| `server/smp/src/test/kotlin/.../ConsentApiE2ETest.kt`                                  | New      | End-to-end with real auth context                                          |

## Risks

| Risk                                                                     | Likelihood  | Mitigation                                                                          |
|--------------------------------------------------------------------------|-------------|-------------------------------------------------------------------------------------|
| Flow materialization bug causes runtime crash                            | High (100%) | Add `.toList()` immediately, verify with unit tests before any other changes        |
| Authorization permission not registered → API always fails or leaks data | High        | Search codebase for existing permission patterns, add if missing, verify with tests |
| ResourceContext not populated by auth filter → NPE on every request      | Medium      | Add integration test with real Spring Security context, verify JWT filter chain     |
| Enum validation returns 500 instead of 400 → poor UX                     | Medium      | Wrap `valueOf()` in try-catch, add validation tests for invalid enums               |
| Postgres integration tests fail in CI (missing env var)                  | Low         | Use existing test container patterns, verify `SMP_POSTGRES_TEST_PASSWORD` is set    |
| Idempotent status semantics unclear → client confusion                   | Low         | Clarify with tests: 201 = new record, 200 = already active                          |

## Rollback Plan

1. **Revert HTTP adapter layer**: Remove `ConsentController`, query handlers, and all HTTP-related
   infrastructure
2. **Preserve domain layer**: Keep `ConsentRepository`, `RecordConsentHandler`,
   `WithdrawConsentHandler`, R2DBC implementation intact
3. **Database schema unchanged**: No migrations needed — tables and indexes remain for future retry
4. **Authorization unchanged**: Keep `workspace:consent:read` permission (no harm if unused)
5. **Verification**: Run `just backend-test-fast` to ensure domain logic still works, check waitlist
   adapter still records consent
6. **Timeline**: Rollback possible within 5 minutes via `git revert` + redeploy

## Dependencies

- Existing Spring Security infrastructure (JWT filter, `ResourceContext` population)
- Existing authorization module (`WorkspaceAuthorizationDecider`, permission registry)
- Postgres test environment with `SMP_POSTGRES_TEST_PASSWORD` configured
- Cucumber BDD test infrastructure (already present in codebase)

## Success Criteria

- [ ] All three critical bugs fixed: Flow materialization, security context binding, authorization
  registration
- [ ] `workspace:consent:read` permission exists in registry and assigned to OWNER, ADMIN roles
- [ ] All existing tests pass (ConsentControllerWebTest, domain tests)
- [ ] Authorization tests added and passing for both query handlers
- [ ] Postgres integration tests added and passing for repository queries
- [ ] BDD scenario `consent-api.feature` added and passing
- [ ] End-to-end integration test with real auth context added and passing
- [ ] OpenAPI documentation complete with schemas, security, and examples
- [ ] Enum validation returns 400 with clear error message
- [ ] POST idempotent behavior tested: 201 for new, 200 for existing ACTIVE
- [ ] `just ci-full` passes (includes Postgres BDD)
- [ ] Code review approval with security focus on authorization logic
