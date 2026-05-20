# Tasks: Backend Credentials Expansion

## Review Workload Forecast

| Field                      | Value                                                                                                                   |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Review budget              | 400 changed lines unless project config says otherwise                                                                  |
| Estimated workload         | Medium                                                                                                                  |
| Chained PRs recommended    | No                                                                                                                      |
| Proposed delivery strategy | single-pr                                                                                                               |
| Work-unit balance          | Keep one narrow backend slice: schema + auth materialization + endpoint proofs, with tests landing beside each behavior |

## Scope Guardrails

- In scope: persisted `SERVICE_ACCOUNT` support, service-account bearer validation/materialization,
  authoritative revocation deny, and proof on `/api/authorization/workspace-access/current` in H2
  and PostgreSQL.
- Out of scope: API keys, rotation workflows, end-user JWT revocation, and admin/operator surfaces.

## Phase 1: Persist minimal service-account credential state

- [x] 1.1 Update `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` to include
  the service-account credential changelog.
- [x] 1.2 Create
  `server/smp/src/main/resources/db/changelog/credentials/001-create-service-account-credentials.yaml`
  with minimal `service_account_credentials` schema, `ACTIVE|REVOKED` state, and principal binding.
- [x] 1.3 Adjust
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcPrincipalIdentityLookup.kt`
  only as needed so persisted `SERVICE_ACCOUNT` principals resolve through the current identity
  lookup path.

## Phase 2: Add service-account bearer classification and active-credential lookup

- [x] 2.1 Extend
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/domain/ValidatedToken.kt` with the
  minimal service-account hint and credential-reference fields required for this change.
- [x] 2.2 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/security/SpringJwtValidatedTokenMapper.kt`
  to classify service-account bearer JWTs and carry the stable credential reference.
- [x] 2.3 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ServiceAccountCredentialStateLookup.kt`
  for authoritative active/revoked lookup.
- [x] 2.4 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcServiceAccountCredentialStateLookup.kt`
  to require an active credential row by reference, subject, and provider.

## Phase 3: Materialize service accounts through the current auth path and deny revoked credentials

- [x] 3.1 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/JwtAuthenticatedPrincipalMaterializer.kt`
  to branch USER vs `SERVICE_ACCOUNT`, require active service-account credentials, and fail
  authentication on revoked/missing state.
- [x] 3.2 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityBootstrapConfiguration.kt`
  to wire the new credential-state lookup into the materializer.
- [x] 3.3 Adjust
  `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt` only
  if needed to expose runtime proof that distinguishes authorization deny from revoked-credential
  deny on the existing slice.

## Phase 4: Prove allow, authorization deny, and revoked deny on the current workspace-access slice

- [x] 4.1 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/JwtAuthenticatedPrincipalMaterializerTest.kt`
  for service-account materialization success and revoked/missing credential rejection.
- [x] 4.2 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/security/JwtPrincipalAuthenticationConverterTest.kt`
  to prove service-account bearer tokens traverse the current authentication converter path.
- [x] 4.3 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`
  with H2-backed `200 allow`, `403 authorization deny`, and `401 revoked deny` scenarios on
  `/api/authorization/workspace-access/current`.
- [x] 4.4 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt`
  with the same PostgreSQL-backed `200/403/401` service-account scenarios.
- [x] 4.5 Run the backend verification commands needed for this slice and confirm USER behavior
  remains intact while service-account scenarios pass in both database modes.
