## Verification Report

**Change**: backend-authorization-breadth
**Version**: N/A

---

### Completeness

| Metric           | Value |
|------------------|-------|
| Tasks total      | 9     |
| Tasks complete   | 9     |
| Tasks incomplete | 0     |

All listed tasks in `openspec/changes/backend-authorization-breadth/tasks.md` are marked complete.

---

### Build & Tests Execution

**Build**: ✅ Passed

```text
Command: ./gradlew build --rerun-tasks
Result: BUILD SUCCESSFUL
Notes: build completed successfully; Gradle reported deprecation warnings and generated an incubating problems report, but no build/type-check failures.
```

**Tests**: ✅ 56 passed / ❌ 0 failed / ⚠️ 0 skipped

```text
Command: ./gradlew test --rerun-tasks
Result: BUILD SUCCESSFUL
JUnit summary: 56 tests, 56 passed, 0 failed, 0 errors, 0 skipped
```

**Coverage**: ➖ Not configured as an enforcing threshold (`coverage_threshold: 0` in
`openspec/config.yaml`)

---

### Spec Compliance Matrix

| Requirement                                                             | Scenario                                                                                          | Test                                                                                                                                                                                                                                          | Result      |
|-------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| Persisted Workspace-Scoped Direct Grants for the Existing Proving Slice | Direct allow grants access on the existing proving slice                                          | `WorkspaceAccessSummaryEndpointIntegrationTest > allows member through persisted direct allow grant()`                                                                                                                                        | ✅ COMPLIANT |
| Persisted Workspace-Scoped Direct Grants for the Existing Proving Slice | Direct deny overrides role-based allow on the existing proving slice                              | `WorkspaceAccessSummaryEndpointIntegrationTest > direct deny overrides role based allow on h2()`                                                                                                                                              | ✅ COMPLIANT |
| Persisted Workspace-Scoped Direct Grants for the Existing Proving Slice | Expired persisted direct grant is ignored on the existing proving slice                           | `WorkspaceAccessSummaryEndpointIntegrationTest > expired direct grant is ignored on h2()`                                                                                                                                                     | ✅ COMPLIANT |
| Direct-Grant Breadth Boundary for the Existing Proving Slice            | Deferred breadth expansion is rejected                                                            | Static scope evidence only; no runtime test applicable for this change-control statement                                                                                                                                                      | ⚠️ PARTIAL  |
| Direct-Grant Breadth Boundary for the Existing Proving Slice            | Separate deny-rule subsystem remains deferred                                                     | Static scope evidence only; no separate deny subsystem introduced                                                                                                                                                                             | ⚠️ PARTIAL  |
| Minimal Proving Authorization Slice                                     | Authorized principal retrieves workspace access summary through direct allow within breadth scope | `WorkspaceAccessSummaryEndpointIntegrationTest > allows member through persisted direct allow grant()`                                                                                                                                        | ✅ COMPLIANT |
| Minimal Proving Authorization Slice                                     | Role-only allow path remains valid within breadth scope                                           | `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for authorized member()` and `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for authorized member on postgres()` | ✅ COMPLIANT |
| PostgreSQL Verification for Workspace Access Current Slice              | PostgreSQL verifies direct allow on the existing slice                                            | `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > allows member through persisted direct allow grant on postgres()`                                                                                                                    | ✅ COMPLIANT |
| PostgreSQL Verification for Workspace Access Current Slice              | PostgreSQL verifies direct deny override on the existing slice                                    | `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > direct deny overrides role based allow on postgres()`                                                                                                                                | ✅ COMPLIANT |
| PostgreSQL Verification for Workspace Access Current Slice              | PostgreSQL verifies expired grant exclusion on the existing slice                                 | `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > expired direct grant is ignored on postgres()`                                                                                                                                       | ✅ COMPLIANT |

**Compliance summary**: 8/10 scenarios compliant, 2/10 partial, 0 failing, 0 untested.

---

### Correctness (Static — Structural Evidence)

| Requirement                                                             | Status        | Notes                                                                                                                                                                                                                                                                    |
|-------------------------------------------------------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Persisted Workspace-Scoped Direct Grants for the Existing Proving Slice | ✅ Implemented | Liquibase adds `workspace_direct_grants`; `R2dbcDirectGrantResolver` resolves persisted grants by workspace/principal; service filters expired grants and applies deny-over-allow precedence; H2 and PostgreSQL endpoint tests prove direct allow/deny/expired behavior. |
| Direct-Grant Breadth Boundary for the Existing Proving Slice            | ✅ Implemented | No scope enforcement, entitlements, admin workflows, extra endpoints, or separate deny-rule subsystem were added in the changed files for this scope.                                                                                                                    |
| Minimal Proving Authorization Slice                                     | ✅ Implemented | Existing `/api/authorization/workspace-access/current` slice remains the only protected surface exercised; role-only allow path still verified alongside direct-grant scenarios.                                                                                         |
| PostgreSQL Verification for Workspace Access Current Slice              | ✅ Implemented | PostgreSQL integration test class covers authorized role path plus direct allow, direct deny override, and expired-grant denial against Testcontainers PostgreSQL.                                                                                                       |

---

### Coherence (Design)

| Decision                                                       | Followed?           | Notes                                                                                                                                                                |
|----------------------------------------------------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Persist direct grants in one dedicated authorization table     | ✅ Yes               | `005-create-workspace-direct-grants.yaml` creates a dedicated table with FK relationships, unique constraint, and lookup index.                                      |
| Resolve grants by current principal + current workspace only   | ✅ Yes               | `R2dbcDirectGrantResolver` filters by `workspace_id`, `principal_id`, and `principal_type`; non-workspace contexts return empty.                                     |
| Integrate through the existing `DirectGrantResolver` seam only | ✅ Yes               | `AuthorizationBootstrapConfiguration` wires `R2dbcDirectGrantResolver` into the existing seam; `WorkspaceAuthorizationService` contract remains unchanged.           |
| Preserve current evaluation breadth and precedence semantics   | ✅ Yes               | Service still requires membership first, ignores expired grants, applies direct deny before direct allow, then falls back to role permission.                        |
| File changes align with design                                 | ⚠️ Slight deviation | `PlatformBootstrapConfiguration.kt` also changed to provide an `ObjectMapper` bean needed by the new resolver. This is a narrow enabling change, not a scope breach. |

---

### Issues Found

**CRITICAL** (must fix before archive):
None.

**WARNING** (should fix):

- H2 and PostgreSQL endpoint test logs contain `UnsupportedOperationException` after successful
  `200 OK` responses (`ServerHttpResponse already committed`). The test suite still passes, but this
  indicates a runtime-side follow-up issue outside the narrow direct-grant breadth goal.
- Two spec scenarios under the breadth-boundary requirement are governance/scope-control statements
  and are only evidenced statically, not by dedicated runtime tests.

**SUGGESTION** (nice to have):

- Consider adding a focused test for `R2dbcDirectGrantResolver` itself to pin JSON conditions
  decoding and workspace-context short-circuit behavior directly at the adapter boundary.

---

### Verdict

PASS WITH WARNINGS

The persisted direct-grant breadth expansion is implemented and proven end to end for the scoped
`/api/authorization/workspace-access/current` slice on both H2 and PostgreSQL, with no critical gaps
for this change scope.
