# Verification Report

**Change**: backend-api-key-support
**Version**: N/A
**Verified on**: 2026-05-17

---

### Completeness

| Metric           | Value |
|------------------|-------|
| Tasks total      | 13    |
| Tasks complete   | 13    |
| Tasks incomplete | 0     |

All checklist items in `openspec/changes/backend-api-key-support/tasks.md` are marked complete.

---

### Build & Tests Execution

**Working directory**: `server/smp`

**Tests**: ✅ Passed

- Command: `./gradlew test`
- Exit code: `0`
- JUnit summary: **87 passed / 0 failed / 0 skipped** across **28 suites**
- Notes: Gradle reported deprecated features that will be incompatible with Gradle 10, but the test
  run itself passed.

**Build**: ✅ Passed

- Command: `./gradlew build`
- Exit code: `0`
- Notes: Build completed successfully; Gradle repeated the same deprecation warning.

**Coverage**: ➖ Not configured (`coverage_threshold: 0`)

---

### Spec Compliance Matrix

| Requirement                                                      | Scenario                                                                       | Test                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Result      |
|------------------------------------------------------------------|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| Credentials: JWT, Service Account, and API Key Platform Concepts | API key is validated for the existing proving slice                            | `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for authorized api key principal`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for authorized api key principal on postgres`                                                                                                                                                                                                                                                                                                                                                                                                                                                      | ✅ COMPLIANT |
| Credentials: JWT, Service Account, and API Key Platform Concepts | Revoked API key is denied before protected access                              | `WorkspaceAccessSummaryEndpointIntegrationTest > rejects revoked api key credential before authorization executes`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects revoked api key credential before authorization executes on postgres`; `R2dbcApiKeyCredentialStateLookupTest > rejects api key when stored credential is revoked`                                                                                                                                                                                                                                                                                                                                                            | ✅ COMPLIANT |
| Credentials: JWT, Service Account, and API Key Platform Concepts | API-key management breadth remains deferred in this change                     | No issuance/admin/rotation/inventory routes found; API-key behavior remains constrained to `/api/authorization/workspace-access/current` and credential/auth seams                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | ✅ COMPLIANT |
| Identity: Principal Taxonomy                                     | Persisted API-key principal is available for authenticated requests            | `ApiKeyAuthenticatedPrincipalMaterializerTest > materializes api key principal from active credential`; `R2dbcPrincipalIdentityLookupTest > loads api key principal facts without requiring user identity row`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | ✅ COMPLIANT |
| Identity: Principal Taxonomy                                     | Deferred principal types remain deferred beyond API keys and service accounts  | Static verification only: no executable auth path was added for `SYSTEM`, `INTEGRATION`, or `AGENT`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | ✅ COMPLIANT |
| Identity: JWT-First Identity Materialization for Phase One       | Valid API key materializes an authenticated principal                          | `ApiKeyPrincipalAuthenticationConverterTest > converts api key bearer value into authenticated principal token`; `ApiKeyAuthenticationWebFilterTest > authenticates proving slice api key request and exposes authentication downstream`                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | ✅ COMPLIANT |
| Identity: JWT-First Identity Materialization for Phase One       | Missing, invalid, or non-materializable API key blocks principal establishment | `ApiKeyAuthenticationWebFilterTest > returns unauthorized when api key authentication fails`; `ApiKeyAuthenticatedPrincipalMaterializerTest > rejects materialization when persisted principal is missing`; `R2dbcApiKeyCredentialStateLookupTest > rejects api key when secret verifier does not match`                                                                                                                                                                                                                                                                                                                                                                                                          | ✅ COMPLIANT |
| Platform: Deterministic API Protection Principles                | API key is allowed on the current workspace-access slice                       | `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for authorized api key principal`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for authorized api key principal on postgres`                                                                                                                                                                                                                                                                                                                                                                                                                                                      | ✅ COMPLIANT |
| Platform: Deterministic API Protection Principles                | API key is denied by authorization on the current workspace-access slice       | `WorkspaceAccessSummaryEndpointIntegrationTest > denies api key principal without required permission`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies api key principal without required permission on postgres`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | ✅ COMPLIANT |
| Platform: Deterministic API Protection Principles                | Inactive or revoked API key is denied on the current workspace-access slice    | `WorkspaceAccessSummaryEndpointIntegrationTest > rejects revoked api key credential before authorization executes`; `WorkspaceAccessSummaryEndpointIntegrationTest > rejects inactive api key credential before authorization executes`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects revoked api key credential before authorization executes on postgres`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects inactive api key credential before authorization executes on postgres`; `R2dbcApiKeyCredentialStateLookupTest > rejects api key when stored credential is revoked`; `R2dbcApiKeyCredentialStateLookupTest > rejects api key when stored credential is inactive` | ✅ COMPLIANT |
| Governance: Auditability of Security-Relevant Platform Actions   | Allowed API-key workspace access outcome is audit-ready at runtime             | `WorkspaceAccessSummaryEndpointIntegrationTest > returns workspace access summary for authorized api key principal`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > returns workspace access summary for authorized api key principal on postgres`                                                                                                                                                                                                                                                                                                                                                                                                                                                      | ✅ COMPLIANT |
| Governance: Auditability of Security-Relevant Platform Actions   | Authorization-controlled API-key denial is audit-ready at runtime              | `WorkspaceAccessSummaryEndpointIntegrationTest > denies api key principal without required permission`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > denies api key principal without required permission on postgres`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | ✅ COMPLIANT |
| Governance: Auditability of Security-Relevant Platform Actions   | Revoked or inactive API-key denial is audit-ready at runtime                   | `WorkspaceAccessSummaryEndpointIntegrationTest > rejects revoked api key credential before authorization executes`; `WorkspaceAccessSummaryEndpointIntegrationTest > rejects inactive api key credential before authorization executes`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects revoked api key credential before authorization executes on postgres`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > rejects inactive api key credential before authorization executes on postgres`                                                                                                                                                                                          | ✅ COMPLIANT |
| Governance: Current-Slice Governance Deferral Boundary           | Broader API-key governance feature is deferred                                 | Static verification only: no durable audit storage/query APIs or broader governance surfaces were added                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | ✅ COMPLIANT |

**Compliance summary**: **14 / 14 scenarios compliant**

---

### Correctness (Static — Structural Evidence)

| Requirement                                         | Status        | Notes                                                                                                                                                                                      |
|-----------------------------------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Minimal API-key credential persistence              | ✅ Implemented | `002-create-api-key-credentials.yaml` adds only the designed runtime fields: principal binding, lookup key, key prefix, secret verifier, status, revoked timestamp, and created timestamp. |
| Secure lookup + verifier comparison                 | ✅ Implemented | `R2dbcApiKeyCredentialStateLookup` splits lookup/secret, loads one candidate row by `lookup_key`, validates `principal_type = 'API_KEY'`, and checks the BCrypt verifier.                  |
| Revoked/inactive enforcement before authorization   | ✅ Implemented | Non-`ACTIVE` rows fail in `requireActive(...)` before principal materialization; security config turns these into `401` outcomes for the protected slice.                                  |
| Dedicated API-key auth adapter                      | ✅ Implemented | `ApiKeyAuthenticationWebFilter` and `ApiKeyPrincipalAuthenticationConverter` run alongside `oauth2ResourceServer().jwt(...)`; API keys are not forced through the JWT decoder.             |
| Repo-local `API_KEY` principal materialization      | ✅ Implemented | `ApiKeyAuthenticatedPrincipalMaterializer` loads the bound persisted principal and returns `AuthenticatedPrincipal` with `PrincipalType.API_KEY` and `CredentialType.API_KEY`.             |
| Authorization truth remains outside the API key     | ✅ Implemented | Integration tests prove allow/deny still come from workspace membership + role permission state on the existing query path.                                                                |
| Runtime audit proof for allow/deny/revoked/inactive | ✅ Implemented | Integration tests assert `AuthorizationDecisionAuditFact` for allow, permission deny, revoked deny, and inactive deny.                                                                     |
| Scope boundary preserved                            | ✅ Implemented | No new controllers, issuance endpoints, rotation flows, inventory features, or governance expansion were introduced.                                                                       |
| `ValidatedToken` remains JWT-focused                | ✅ Implemented | `ValidatedToken.kt` remains unchanged and API-key support was built in parallel instead of deforming the JWT model.                                                                        |

---

### Coherence (Design)

| Decision                                               | Followed? | Notes                                                                                                                       |
|--------------------------------------------------------|-----------|-----------------------------------------------------------------------------------------------------------------------------|
| Dedicated `api_key_credentials` table                  | ✅ Yes     | Implemented with its own changelog and included in the master changelog.                                                    |
| Store verifier material, never plaintext               | ✅ Yes     | BCrypt-based verifier abstraction and hashed test seed data are used.                                                       |
| Separate WebFlux adapter instead of JWT reuse          | ✅ Yes     | The web filter recognizes only proving-slice API-key attempts and lets non-API-key bearer tokens continue through JWT flow. |
| Reuse existing principal and authorization seams       | ✅ Yes     | API-key auth culminates in a normal `AuthenticatedPrincipal` and existing workspace/authorization flow.                     |
| Enforce state before principal establishment completes | ✅ Yes     | `requireActive(...)` rejects missing/invalid/inactive/revoked credentials before materialization.                           |
| Prove allow/deny/revoked behavior on H2 and PostgreSQL | ✅ Yes     | Both integration suites cover allow, authorization deny, revoked deny, and inactive deny.                                   |
| Keep scope limited to current proving slice            | ✅ Yes     | All executable behavior remains tied to `/api/authorization/workspace-access/current`.                                      |

---

### Issues Found

**CRITICAL** (must fix before archive):

- None.

**WARNING** (should fix):

- Gradle reported deprecated features during `./gradlew test` and `./gradlew build`; this is not
  blocking the change, but it should be cleaned up before a Gradle 10 upgrade.

**SUGGESTION** (nice to have):

- Add a dedicated `./gradlew test --warning-mode all` cleanup pass in a future maintenance change so
  the exact deprecation source is documented and removed.

---

### Verdict

**PASS WITH WARNINGS**

Implementation is complete and behaviorally compliant with the delta specs and design; only
non-blocking Gradle deprecation warnings remain.
