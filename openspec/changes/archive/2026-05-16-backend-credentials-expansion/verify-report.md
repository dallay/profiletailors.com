## Verification Report

**Change**: backend-credentials-expansion
**Version**: N/A

---

### Completeness

| Metric           | Value |
|------------------|-------|
| Tasks total      | 13    |
| Tasks complete   | 13    |
| Tasks incomplete | 0     |

All tasks in `openspec/changes/backend-credentials-expansion/tasks.md` are marked complete.

---

### Build & Tests Execution

**Tests**: ✅ 67 passed / ❌ 0 failed / ⚠️ 0 skipped

Command: `./gradlew test` (run in `server/smp`)

```text
> Task :test UP-TO-DATE
BUILD SUCCESSFUL in 994ms
```

Parsed JUnit summary: **67 total**, **67 passed**, **0 failed**, **0 errors**, **0 skipped** across
**24** test suites.

Relevant executed suites for this change:

- `JwtAuthenticatedPrincipalMaterializerTest` → 4/4 passed
- `JwtPrincipalAuthenticationConverterTest` → 2/2 passed
- `R2dbcPrincipalIdentityLookupTest` → 3/3 passed
- `WorkspaceAccessSummaryEndpointIntegrationTest` → 9/9 passed
- `WorkspaceAccessSummaryEndpointPostgresIntegrationTest` → 8/8 passed
- `LiquibaseBaselineChangelogTest` → 2/2 passed

**Build**: ✅ Passed

Command: `./gradlew build` (run in `server/smp`)

```text
> Task :build UP-TO-DATE
BUILD SUCCESSFUL in 5s
```

**Coverage**: ➖ Not configured (`rules.verify.coverage_threshold: 0`)

---

### Spec Compliance Matrix

| Requirement                                                        | Scenario                                                                           | Test                                                                                                                                                                                                                                                                                                                                  | Result      |
|--------------------------------------------------------------------|------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| Credentials — JWT, Service Account, and API Key Platform Concepts  | Service-account bearer credential is validated for the proving slice               | `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for authorized service account`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for authorized service account on postgres`                                                                              | ✅ COMPLIANT |
| Credentials — JWT, Service Account, and API Key Platform Concepts  | Revoked service-account credential is denied before protected access               | `WorkspaceAccessSummaryEndpointIntegrationTest > rejects revoked service-account credential before authorization executes`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects revoked service-account credential before authorization executes on postgres`                                                            | ✅ COMPLIANT |
| Credentials — JWT, Service Account, and API Key Platform Concepts  | API-key expansion remains deferred in this change                                  | No runtime test; verified by static scope evidence only (no API-key transport/storage/request handling added)                                                                                                                                                                                                                         | ⚠️ PARTIAL  |
| Identity — Principal Taxonomy                                      | Persisted service-account principal is available for authenticated requests        | `R2dbcPrincipalIdentityLookupTest > loads service-account principal facts without requiring user identity row`; `JwtPrincipalAuthenticationConverterTest > converts service-account jwt into authentication carrying service-account principal`                                                                                       | ✅ COMPLIANT |
| Identity — Principal Taxonomy                                      | Deferred principal types remain deferred beyond service accounts                   | No runtime test; verified by static scope evidence only (no executable API_KEY/SYSTEM/INTEGRATION/AGENT path added)                                                                                                                                                                                                                   | ⚠️ PARTIAL  |
| Identity — JWT-First Identity Materialization for Phase One        | Valid service-account bearer credential materializes an authenticated principal    | `JwtAuthenticatedPrincipalMaterializerTest > materializes service-account principal when credential is active`; `JwtPrincipalAuthenticationConverterTest > converts service-account jwt into authentication carrying service-account principal`                                                                                       | ✅ COMPLIANT |
| Identity — JWT-First Identity Materialization for Phase One        | Missing or invalid service-account credential blocks principal establishment       | `JwtAuthenticatedPrincipalMaterializerTest > rejects service-account principal when credential is revoked`; `JwtAuthenticatedPrincipalMaterializerTest > rejects service-account principal when credential reference is missing`                                                                                                      | ✅ COMPLIANT |
| Platform — Stateless Scaling, Caching, and Invalidation Principles | Revoked service-account credential cannot retain cached access                     | `WorkspaceAccessSummaryEndpointIntegrationTest > rejects revoked service-account credential before authorization executes`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects revoked service-account credential before authorization executes on postgres`                                                            | ✅ COMPLIANT |
| Platform — Deterministic API Protection Principles                 | Service account is allowed on the current workspace-access slice                   | `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for authorized service account`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for authorized service account on postgres`                                                                              | ✅ COMPLIANT |
| Platform — Deterministic API Protection Principles                 | Service account is denied by authorization on the current workspace-access slice   | `WorkspaceAccessSummaryEndpointIntegrationTest > denies service account without required permission`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies service account without required permission on postgres`                                                                                                        | ✅ COMPLIANT |
| Platform — Deterministic API Protection Principles                 | Revoked service-account credential is denied on the current workspace-access slice | `WorkspaceAccessSummaryEndpointIntegrationTest > rejects revoked service-account credential before authorization executes`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects revoked service-account credential before authorization executes on postgres`                                                            | ✅ COMPLIANT |
| Platform — Deterministic API Protection Principles                 | Rotation and broad credential management remain deferred                           | No runtime test; verified by static scope evidence only                                                                                                                                                                                                                                                                               | ⚠️ PARTIAL  |
| Governance — Auditability of Security-Relevant Platform Actions    | Allowed service-account workspace access outcome is audit-ready at runtime         | `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for authorized service account`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for authorized service account on postgres`                                                                              | ✅ COMPLIANT |
| Governance — Auditability of Security-Relevant Platform Actions    | Authorization-controlled service-account denial is audit-ready at runtime          | `WorkspaceAccessSummaryEndpointIntegrationTest > denies service account without required permission`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies service account without required permission on postgres`                                                                                                        | ✅ COMPLIANT |
| Governance — Auditability of Security-Relevant Platform Actions    | Revoked service-account credential denial is audit-ready at runtime                | `WorkspaceAccessSummaryEndpointIntegrationTest > rejects revoked service-account credential before authorization executes`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects revoked service-account credential before authorization executes on postgres` (both assert `AuthorizationReasonCode.REVOKED_CREDENTIAL`) | ✅ COMPLIANT |
| Governance — Current-Slice Governance Deferral Boundary            | Broader credential governance feature is deferred                                  | No runtime test; verified by static scope evidence only                                                                                                                                                                                                                                                                               | ⚠️ PARTIAL  |

**Compliance summary**: 12/16 scenarios compliant, 4 partial, 0 failing

---

### Correctness (Static — Structural Evidence)

| Requirement                                   | Status        | Notes                                                                                                                                                                                                                                              |
|-----------------------------------------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Credentials requirement                       | ✅ Implemented | `ValidatedToken`, `SpringJwtValidatedTokenMapper`, `ServiceAccountCredentialStateLookup`, `R2dbcServiceAccountCredentialStateLookup`, and the new changelog implement bearer-based service-account auth with authoritative revocation enforcement. |
| Identity principal taxonomy + materialization | ✅ Implemented | `R2dbcPrincipalIdentityLookup` resolves `SERVICE_ACCOUNT`; `JwtAuthenticatedPrincipalMaterializer` branches USER vs SERVICE_ACCOUNT and rejects missing/revoked credentials.                                                                       |
| Platform deterministic protection             | ✅ Implemented | The existing workspace-access slice still derives 200/403 from workspace authorization state, while revoked credentials fail with 401 before protected execution.                                                                                  |
| Governance runtime proof                      | ✅ Implemented | `IdentitySecurityConfiguration` records revoked-credential audit facts with `AuthorizationReasonCode.REVOKED_CREDENTIAL`, and integration tests assert those facts for both H2 and PostgreSQL.                                                     |
| Deferred scope boundaries                     | ✅ Implemented | No API-key transport, rotation workflow, broad management API, or new protected endpoint was added.                                                                                                                                                |

---

### Coherence (Design)

| Decision                                                                        | Followed?   | Notes                                                                                                                                                                                                                    |
|---------------------------------------------------------------------------------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Reuse `principals` model for persisted service-account identity                 | ✅ Yes       | `R2dbcPrincipalIdentityLookup` and integration seeds use `principals.principal_type = SERVICE_ACCOUNT` with no new profile table.                                                                                        |
| Add one narrow credential-state table                                           | ✅ Yes       | `service_account_credentials` changelog added with principal binding and ACTIVE/REVOKED state.                                                                                                                           |
| Keep current Spring Security JWT entry path                                     | ✅ Yes       | `IdentitySecurityConfiguration` still uses `oauth2ResourceServer().jwt(...)`; branching happens in repo-local mapping/materialization.                                                                                   |
| Treat backend credential state as authoritative only for service-account tokens | ✅ Yes       | Revocation lookup runs only when `principalTypeHint == SERVICE_ACCOUNT`.                                                                                                                                                 |
| Prove behavior on existing workspace-access slice with 200/403/401 outcomes     | ✅ Yes       | H2 and PostgreSQL integration tests cover allow, authorization deny, and revoked deny on the existing endpoint.                                                                                                          |
| Produce runtime audit-ready proof for revoked-denial on the existing slice      | ✅ Yes       | Revoked-denial audit facts are emitted from `IdentitySecurityConfiguration` and asserted in both integration suites.                                                                                                     |
| Focused credential-state lookup tests                                           | ⚠️ Deviated | Design testing strategy mentioned dedicated repository-level lookup tests; behavior is covered through materializer and endpoint tests, but there is no standalone `R2dbcServiceAccountCredentialStateLookup` test file. |

---

### Issues Found

**CRITICAL** (must fix before archive):

- None

**WARNING** (should fix):

- Four deferred/non-executable scenarios are validated only by static scope evidence, not by runtime
  tests. That is reasonable for deferral boundaries, but strict scenario-to-runtime-proof coverage
  is necessarily partial there.
- The design called for focused credential-state lookup tests, but the current suite relies on
  materializer and endpoint coverage instead of a dedicated repository-level test for
  `R2dbcServiceAccountCredentialStateLookup`.
- `./gradlew test` and `./gradlew build` both passed, but both were mostly `UP-TO-DATE`; this is
  still a real command execution, just not a clean rebuild from scratch.

**SUGGESTION** (nice to have):

- Add one explicit endpoint test for a missing persisted service-account credential row returning
  `401 Unauthorized`, not only missing credential reference at materializer level.
- Add a focused `R2dbcServiceAccountCredentialStateLookup` test covering ACTIVE vs REVOKED vs
  missing rows directly.

---

### Verdict

PASS WITH WARNINGS

The change is complete, builds cleanly, passes the executed backend test suite, and satisfies the
implemented service-account auth, authorization, revocation, and runtime-audit requirements on the
existing workspace-access slice.