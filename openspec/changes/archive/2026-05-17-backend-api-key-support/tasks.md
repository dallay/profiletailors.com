# Tasks: Backend API Key Support

## Review Workload Forecast

| Field                      | Value                                                                                                                                              |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| Review budget              | 400 changed lines                                                                                                                                  |
| Estimated workload         | Medium                                                                                                                                             |
| Chained PRs recommended    | No                                                                                                                                                 |
| Proposed delivery strategy | single-pr                                                                                                                                          |
| Work-unit balance          | 1) schema + lookup seam, 2) auth/materialization wiring, 3) H2/Postgres proving tests; each unit should stay reviewable with tests beside behavior |

## Phase 1: Credential State Foundation

- [x] 1.1 Create
  `server/smp/src/main/resources/db/changelog/credentials/002-create-api-key-credentials.yaml` with
  only lookup key, key prefix, verifier, principal binding, status, revoked timestamp, and
  timestamps for runtime auth.
- [x] 1.2 Update `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` to include
  the new API-key credential changelog without changing unrelated credential breadth.
- [x] 1.3 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ApiKeyCredentialStateLookup.kt`
  with `ActiveApiKeyCredential`, narrow failure reasons, and `requireActive(presentedApiKey)`
  contract.
- [x] 1.4 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ApiKeySecretVerifier.kt`
  and
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcApiKeyCredentialStateLookup.kt`
  to parse the presented key, load one row by lookup key, verify the stored one-way verifier, and
  reject missing/invalid/revoked before principal establishment.

## Phase 2: API-key Authentication and Principal Wiring

- [x] 2.1 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/ApiKeyAuthenticatedPrincipalMaterializer.kt`
  to load the bound persisted principal through `PrincipalIdentityLookup` and materialize
  `AuthenticatedPrincipal` with `PrincipalType.API_KEY`, `CredentialType.API_KEY`, and current
  proving-slice context fields.
- [x] 2.2 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/ApiKeyPrincipalAuthenticationConverter.kt`
  to turn a proving-slice API-key bearer value into `AuthenticatedPrincipalAuthenticationToken`
  without routing through JWT decoding.
- [x] 2.3 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/ApiKeyAuthenticationWebFilter.kt`
  to recognize only `/api/authorization/workspace-access/current` API-key attempts, authenticate
  them, and fall through to the existing JWT path for non-API-key requests.
- [x] 2.4 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`
  to register the API-key filter alongside `oauth2ResourceServer().jwt(...)` and extend
  revoked/inactive credential auditing to emit `REVOKED_CREDENTIAL` for API-key authentication
  failures on the proving slice.

## Phase 3: Proving-Slice Verification

- [x] 3.1 Update
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`
  seed helpers/cleanup for `api_key_credentials` and add H2 scenarios for API-key allow,
  authorization-controlled deny, and revoked-or-inactive 401 deny with runtime audit proof.
- [x] 3.2 Update
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt`
  with the same API-key allow / authorization deny / revoked-or-inactive deny scenarios against
  PostgreSQL.
- [x] 3.3 Keep
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/domain/ValidatedToken.kt` unchanged
  unless a tiny shared helper is strictly required; if touched, limit it to coexistence support and
  avoid forcing API keys into the JWT model.

## Phase 4: Scope Guardrails and Final Pass

- [x] 4.1 Verify the implementation does not add issuance/admin APIs, rotation flows, inventory
  metadata, or credential-platform redesign seams beyond the files listed in this change.
- [x] 4.2 Prepare `openspec/changes/backend-api-key-support/tasks.md` for `sdd-apply` work-unit
  progress updates so each completed unit can be checked off independently.
