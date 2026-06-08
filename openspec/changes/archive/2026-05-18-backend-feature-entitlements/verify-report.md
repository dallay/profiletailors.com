## Verification Report

**Change**: backend-feature-entitlements
**Version**: N/A

---

### Completeness

| Metric           | Value |
|------------------|-------|
| Tasks total      | 11    |
| Tasks complete   | 11    |
| Tasks incomplete | 0     |

All tasks in `openspec/changes/backend-feature-entitlements/tasks.md` are marked complete.

---

### Build & Tests Execution

**Build**: ✅ Passed

- Configured verify command: `./gradlew build`
- Executed in `server/smp`
- Result: `BUILD SUCCESSFUL`

**Tests**: ✅ 41 passed / ❌ 0 failed / ⚠️ 0 skipped

Executed evidence:

- Full configured verify test command: `./gradlew test` → passed
- Targeted rerun for entitlement scope:  
  `./gradlew test --tests "com.profiletailors.smp.authorization.application.WorkspaceAuthorizationServiceTest" --tests "com.profiletailors.smp.authorization.application.GetCurrentWorkspaceAccessSummaryHandlerTest" --tests "com.profiletailors.smp.integration.WorkspaceAccessSummaryEndpointIntegrationTest" --tests "com.profiletailors.smp.integration.WorkspaceAccessSummaryEndpointPostgresIntegrationTest" --rerun-tasks` →
  passed
- Parsed targeted test results:
    - `WorkspaceAuthorizationServiceTest`: 9 passed
    - `GetCurrentWorkspaceAccessSummaryHandlerTest`: 3 passed
    - `WorkspaceAccessSummaryEndpointIntegrationTest`: 15 passed
    - `WorkspaceAccessSummaryEndpointPostgresIntegrationTest`: 14 passed

**Additional evidence**: ✅ `./gradlew detekt` passed

**Coverage**: ➖ Not configured as a positive threshold gate (`coverage_threshold: 0`)

---

### Spec Compliance Matrix

#### Runtime-proven scenarios

| Requirement                                                | Scenario                                                                                  | Test                                                                                                                                                                                                                                                                                                                                                                              | Result      |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| Feature Entitlements Are Separate from Permissions         | Entitled and authorized request is allowed                                                | `WorkspaceAuthorizationServiceTest > allows entitled and authorized principal on current workspace slice()`; `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for entitled authorized member()`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for entitled authorized member on postgres()`         | ✅ COMPLIANT |
| Feature Entitlements Are Separate from Permissions         | Authorized but not entitled request is denied                                             | `WorkspaceAuthorizationServiceTest > denies authorized principal when workspace entitlement is missing()`; `WorkspaceAccessSummaryEndpointIntegrationTest > denies authorized principal when workspace entitlement is missing on h2()`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies authorized principal when workspace entitlement is missing on postgres()` | ✅ COMPLIANT |
| Feature Entitlements Are Separate from Permissions         | Entitled but unauthorized request is denied                                               | `WorkspaceAccessSummaryEndpointIntegrationTest > denies entitled member without required permission()`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies entitled member without required permission on postgres()`                                                                                                                                                | ✅ COMPLIANT |
| Minimal Proving Authorization Slice                        | Authorized and entitled principal retrieves workspace access summary within breadth scope | `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for entitled authorized member()`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for entitled authorized member on postgres()`                                                                                                                      | ✅ COMPLIANT |
| Minimal Proving Authorization Slice                        | Authorized principal without entitlement is denied within breadth scope                   | `WorkspaceAccessSummaryEndpointIntegrationTest > denies authorized principal when workspace entitlement is missing on h2()`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies authorized principal when workspace entitlement is missing on postgres()`                                                                                                            | ✅ COMPLIANT |
| PostgreSQL Verification for Workspace Access Current Slice | PostgreSQL verifies entitled and authorized allow on the existing slice                   | `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for entitled authorized member on postgres()`                                                                                                                                                                                                                                           | ✅ COMPLIANT |
| PostgreSQL Verification for Workspace Access Current Slice | PostgreSQL verifies authorized but non-entitled deny on the existing slice                | `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies authorized principal when workspace entitlement is missing on postgres()`                                                                                                                                                                                                                                         | ✅ COMPLIANT |
| PostgreSQL Verification for Workspace Access Current Slice | PostgreSQL verifies entitled but unauthorized deny on the existing slice                  | `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies entitled member without required permission on postgres()`                                                                                                                                                                                                                                                        | ✅ COMPLIANT |
| Deterministic and Explainable Authorization Governance     | Missing entitlement denial is explainable on the proving slice                            | `GetCurrentWorkspaceAccessSummaryHandlerTest > emits missing entitlement audit fact distinct from missing permission()`; H2/Postgres integration deny assertions with `MISSING_ENTITLEMENT`                                                                                                                                                                                       | ✅ COMPLIANT |
| Deterministic and Explainable Authorization Governance     | Missing permission denial remains explainable on the proving slice                        | `GetCurrentWorkspaceAccessSummaryHandlerTest > throws when authorization is denied and emits deny audit fact()`; H2/Postgres integration deny assertions with `MISSING_PERMISSION`                                                                                                                                                                                                | ✅ COMPLIANT |

#### Structural scope / boundary scenarios

| Requirement                                                                   | Scenario                                                      | Evidence                                                                                                                                     | Result      |
|-------------------------------------------------------------------------------|---------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| Persisted Workspace-Scoped Feature Entitlement for the Existing Proving Slice | Entitlement state exists for one workspace-scoped proving key | `006-create-workspace-entitlements.yaml`, `R2dbcWorkspaceEntitlementResolver.kt`, explicit key in `GetCurrentWorkspaceAccessSummaryQuery.kt` | ✅ COMPLIANT |
| Persisted Workspace-Scoped Feature Entitlement for the Existing Proving Slice | Missing broader commercial semantics remains deferred         | No package/billing/admin/multi-context code introduced; scope guardrails remain in proposal/design/tasks                                     | ✅ COMPLIANT |
| Feature-Entitlement Change Boundary for the Existing Proving Slice            | New endpoint expansion is rejected                            | Only existing `/api/authorization/workspace-access/current` endpoint used; controller unchanged except existing route mediation              | ✅ COMPLIANT |
| Feature-Entitlement Change Boundary for the Existing Proving Slice            | Broader entitlement platform breadth remains deferred         | No admin APIs, no quota semantics, no multi-context resolver, no new protected endpoints                                                     | ✅ COMPLIANT |

**Compliance summary**: 14/14 scenarios compliant for the verified change scope

---

### Correctness (Static — Structural Evidence)

| Requirement                                                                   | Status        | Notes                                                                                                                                               |
|-------------------------------------------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| Persisted Workspace-Scoped Feature Entitlement for the Existing Proving Slice | ✅ Implemented | Added `workspace_entitlements` schema, explicit enabled flag, unique `(workspace_id, entitlement_key)` constraint, and persistence-backed resolver. |
| Feature-Entitlement Change Boundary for the Existing Proving Slice            | ✅ Implemented | Change stays on existing current-slice endpoint and does not introduce broader commercial/admin breadth.                                            |
| Feature Entitlements Are Separate from Permissions                            | ✅ Implemented | `WorkspaceAuthorizationService` evaluates entitlement separately and denies with `MISSING_ENTITLEMENT` before permission allow can succeed.         |
| Minimal Proving Authorization Slice                                           | ✅ Implemented | Existing slice preserved; entitlement added as one additional prerequisite while direct allow/deny and deny-by-default remain intact.               |
| PostgreSQL Verification for Workspace Access Current Slice                    | ✅ Implemented | PostgreSQL integration suite exercises allow, missing entitlement deny, and missing permission deny.                                                |
| Deterministic and Explainable Authorization Governance                        | ✅ Implemented | `AuthorizationReasonCode.MISSING_ENTITLEMENT` added and surfaced through audit facts and exception mapping.                                         |

---

### Coherence (Design)

| Decision                                                                  | Followed?          | Notes                                                                                                                                                                |
|---------------------------------------------------------------------------|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Persist entitlement state in one narrow workspace-entitlement table       | ✅ Yes              | `006-create-workspace-entitlements.yaml` matches the narrow table design.                                                                                            |
| Keep the domain entitlement model minimal                                 | ✅ Yes              | `Entitlement(key, enabled)` remains unchanged in shape.                                                                                                              |
| Resolve one entitlement key authoritatively through `EntitlementResolver` | ✅ Yes              | `R2dbcWorkspaceEntitlementResolver` implements the existing seam.                                                                                                    |
| Combine entitlement and permission in `WorkspaceAuthorizationService`     | ✅ Yes              | Enforcement lives in `WorkspaceAuthorizationService`.                                                                                                                |
| Add a dedicated missing-entitlement reason code                           | ✅ Yes              | `AuthorizationReasonCode.MISSING_ENTITLEMENT` added and exercised.                                                                                                   |
| Tie the proving slice to one explicit entitlement key                     | ✅ Yes              | `CURRENT_WORKSPACE_ACCESS_ENTITLEMENT = "workspace.access.summary"`.                                                                                                 |
| File changes table coherence                                              | ⚠️ Minor deviation | `AuthorizationModels.kt` did not require modification because the existing `Entitlement(key, enabled)` model already satisfied the design intent. No functional gap. |

---

### Issues Found

**CRITICAL** (must fix before archive):
None

**WARNING** (should fix):
None

**SUGGESTION** (nice to have):

- Consider adding an explicit disabled-row integration scenario (`enabled = false`) in a future
  change if the team wants proof for both "row missing" and "row disabled" denial inputs. This is
  not required by the current change scope.

---

### Verdict

PASS

The implementation matches the proposal, specs, design intent, and completed tasks for the narrow
`backend-feature-entitlements` scope, with real test/build evidence proving the existing
workspace-access slice now requires both entitlement and permission.