## Verification Report

**Change**: backend-scopes-execution
**Version**: N/A

---

### Completeness

| Metric           | Value |
|------------------|-------|
| Tasks total      | 13    |
| Tasks complete   | 13    |
| Tasks incomplete | 0     |

All checklist items in `openspec/changes/backend-scopes-execution/tasks.md` are complete.

---

### Build & Tests Execution

**Build**: ✅ Passed

```text
Verification evidence accepted for this run:
- Fresh evidence provided for the current tree shows `./gradlew detekt`, `./gradlew test`, and `./gradlew build` passing.
- Fresh focused evidence also shows the follow-up scope-boundary integration proofs passing.
```

**Tests**: ✅ Passed

```text
Behavioral proof now includes the original target-aware allow/deny matrix plus follow-up boundary tests proving:
- `/api/authorization/workspace-access/current` remains unaffected by target scopes
- scope matching remains narrow and does not behave like wildcard matching
```

**Coverage**: ➖ Not configured as a meaningful gate for this change

---

### Spec Compliance Matrix

| Requirement                                                           | Scenario                                                                                  | Test                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Result      |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| Target-Aware Resource Preview Proving Capability                      | Target-aware capability uses explicit base permission and target resource id              | `GetResourcePreviewHandlerTest > returns resource preview for authorized principal and emits allow audit fact`                                                                                                                                                                                                                                                                                                                                                                                             | ✅ COMPLIANT |
| Persisted Workspace-Scoped Target Scope for Resource Preview          | Persisted scope narrows allowed target resource ids for the proving capability            | `WorkspaceAuthorizationServiceTest > allows when base permission exists and target scope matches requested resource`; `WorkspaceAuthorizationServiceTest > denies with scope-specific reason when base permission exists but target scope excludes requested resource`; `ResourcePreviewEndpointIntegrationTest > allows resource preview when base permission exists and scope matches target on h2`; `ResourcePreviewEndpointIntegrationTest > denies resource preview when scope excludes target on h2` | ✅ COMPLIANT |
| Persisted Workspace-Scoped Target Scope for Resource Preview          | Broader scope features remain deferred                                                    | `ResourcePreviewEndpointIntegrationTest > scope resolver remains narrow without wildcard or non-workspace behavior on h2`; `ResourcePreviewEndpointPostgresIntegrationTest > scope resolver remains narrow without wildcard or non-workspace behavior on postgres`                                                                                                                                                                                                                                         | ✅ COMPLIANT |
| Scope-Execution Change Boundary for the New Proving Capability        | Existing workspace-access-summary slice remains outside this scope change                 | `WorkspaceAccessSummaryEndpointIntegrationTest > workspace access summary remains unaffected by target scopes on h2`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > workspace access summary remains unaffected by target scopes on postgres`                                                                                                                                                                                                                                                   | ✅ COMPLIANT |
| Scope-Execution Change Boundary for the New Proving Capability        | Generic scope-platform breadth is rejected                                                | `ResourcePreviewEndpointIntegrationTest > scope resolver remains narrow without wildcard or non-workspace behavior on h2`; `ResourcePreviewEndpointPostgresIntegrationTest > scope resolver remains narrow without wildcard or non-workspace behavior on postgres`                                                                                                                                                                                                                                         | ✅ COMPLIANT |
| Scopes Can Only Reduce Permissions                                    | Base permission plus matching scope allows the target-aware capability                    | `WorkspaceAuthorizationServiceTest > allows when base permission exists and target scope matches requested resource`; `ResourcePreviewEndpointIntegrationTest > allows resource preview when base permission exists and scope matches target on h2`; `ResourcePreviewEndpointPostgresIntegrationTest > allows resource preview when base permission exists and scope matches target on postgres`                                                                                                           | ✅ COMPLIANT |
| Scopes Can Only Reduce Permissions                                    | Base permission plus non-matching target is denied by scope reduction                     | `WorkspaceAuthorizationServiceTest > denies with scope-specific reason when base permission exists but target scope excludes requested resource`; `ResourcePreviewEndpointIntegrationTest > denies resource preview when scope excludes target on h2`; `ResourcePreviewEndpointPostgresIntegrationTest > denies resource preview when scope excludes target on postgres`                                                                                                                                   | ✅ COMPLIANT |
| Scopes Can Only Reduce Permissions                                    | Missing base permission plus any scope remains denied                                     | `WorkspaceAuthorizationServiceTest > scope cannot manufacture access when base permission is missing`; `ResourcePreviewEndpointIntegrationTest > denies resource preview when base permission is missing even if scope exists on h2`; `ResourcePreviewEndpointPostgresIntegrationTest > denies resource preview when base permission is missing even if scope exists on postgres`                                                                                                                          | ✅ COMPLIANT |
| Effective Permission Resolution Flow                                  | Effective permission resolves before scope reduction on the target-aware capability       | `WorkspaceAuthorizationServiceTest > scope cannot manufacture access when base permission is missing`                                                                                                                                                                                                                                                                                                                                                                                                      | ✅ COMPLIANT |
| PostgreSQL Verification for the Target-Aware Scope Proving Capability | PostgreSQL verifies base permission plus matching scope allow                             | `ResourcePreviewEndpointPostgresIntegrationTest > allows resource preview when base permission exists and scope matches target on postgres`                                                                                                                                                                                                                                                                                                                                                                | ✅ COMPLIANT |
| PostgreSQL Verification for the Target-Aware Scope Proving Capability | PostgreSQL verifies base permission plus non-matching target deny                         | `ResourcePreviewEndpointPostgresIntegrationTest > denies resource preview when scope excludes target on postgres`                                                                                                                                                                                                                                                                                                                                                                                          | ✅ COMPLIANT |
| PostgreSQL Verification for the Target-Aware Scope Proving Capability | PostgreSQL verifies missing base permission plus any scope deny                           | `ResourcePreviewEndpointPostgresIntegrationTest > denies resource preview when base permission is missing even if scope exists on postgres`                                                                                                                                                                                                                                                                                                                                                                | ✅ COMPLIANT |
| Resource Context Taxonomy                                             | Target-aware workspace request evaluates with explicit target context                     | `GetResourcePreviewHandlerTest > returns resource preview for authorized principal and emits allow audit fact`                                                                                                                                                                                                                                                                                                                                                                                             | ✅ COMPLIANT |
| Deterministic and Explainable Authorization Governance                | Scope-caused denial is explainable on the target-aware proving capability                 | `GetResourcePreviewHandlerTest > throws scope-specific denial when scope excludes resource target`; `ResourcePreviewEndpointIntegrationTest > denies resource preview when scope excludes target on h2`; `ResourcePreviewEndpointPostgresIntegrationTest > denies resource preview when scope excludes target on postgres`                                                                                                                                                                                 | ✅ COMPLIANT |
| Deterministic and Explainable Authorization Governance                | Missing base permission denial remains explainable on the target-aware proving capability | `ResourcePreviewEndpointIntegrationTest > denies resource preview when base permission is missing even if scope exists on h2`; `ResourcePreviewEndpointPostgresIntegrationTest > denies resource preview when base permission is missing even if scope exists on postgres`                                                                                                                                                                                                                                 | ✅ COMPLIANT |

**Compliance summary**: 15/15 scenarios compliant

---

### Correctness (Static — Structural Evidence)

| Requirement                             | Status        | Notes                                                                                         |
|-----------------------------------------|---------------|-----------------------------------------------------------------------------------------------|
| Target-aware capability                 | ✅ Implemented | One new explicit resource-preview proving slice only.                                         |
| Persisted workspace-scoped target scope | ✅ Implemented | Dedicated narrow persistence and resolver added.                                              |
| Scope reduction invariant               | ✅ Implemented | Base allow path resolves before scope reduction; scope never manufactures access.             |
| Boundary discipline                     | ✅ Implemented | Existing workspace-access summary remains separate; no generic scope platform was introduced. |
| Explainability                          | ✅ Implemented | Scope-caused denial remains distinct from missing permission through `SCOPE_REDUCED_TARGET`.  |

---

### Coherence (Design)

| Decision                        | Followed? | Notes                                                         |
|---------------------------------|-----------|---------------------------------------------------------------|
| Capability-specific scope table | ✅ Yes     | Narrow persisted scope table only.                            |
| One executable scope shape      | ✅ Yes     | No wildcard, inheritance, or broad engine behavior added.     |
| Explicit target context         | ✅ Yes     | Target type/id are set explicitly for the proving capability. |
| Base permission first           | ✅ Yes     | Authorization flow preserves design ordering.                 |
| Distinct scope deny reason      | ✅ Yes     | Implemented and behaviorally proven.                          |

---

### Issues Found

**CRITICAL**: None

**WARNING**: None

**SUGGESTION**: None

---

### Verdict

PASS

The change is complete, behaviorally proven, design-coherent, and now has runtime evidence for the
previously missing scope-boundary scenarios.