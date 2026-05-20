# Design: Backend API Key Support

## Technical Approach

Add one narrow API-key authentication path for `GET /api/authorization/workspace-access/current`
while leaving the existing JWT resource-server path in place for USER and SERVICE_ACCOUNT requests.

The design follows the approved delta specs by introducing only the minimum persisted credential
state required to look up an API key, verify a presented secret using a non-reversible verifier,
bind the credential to a persisted `API_KEY` principal, and reject inactive or revoked keys before
authorization runs.

After authentication succeeds, the request re-enters the same repo-local principal,
workspace-context, authorization, and audit seams already used by the proving slice today. The API
key itself never carries authorization truth.

## Architecture Decisions

### Decision: Persist API-key credentials in a dedicated runtime state table

**Choice**: Add a dedicated `api_key_credentials` table instead of overloading
`service_account_credentials` or inventing a generic credential ledger.

**Alternatives considered**:

- Reuse `service_account_credentials` for API keys.
- Introduce a new generalized `credentials` super-table for all credential types now.

**Rationale**: API keys need lookup-safe identifier fields plus verifier material, which is
materially different from the current service-account JWT reference model. Reusing the
service-account table would blur semantics. A generic ledger would expand scope into a broader
credential-platform redesign that the proposal and specs explicitly defer.

### Decision: Store verifier material, never plaintext or reversible secret data

**Choice**: Persist a stable lookup identifier/prefix plus a one-way secret verifier representation
and active/revoked state.

**Alternatives considered**:

- Store raw API keys.
- Store reversibly encrypted API keys.
- Store only a full-key hash without a lookup field.

**Rationale**: The specs require secure verifier semantics. Plaintext or reversible storage is out.
A verifier-only scheme with a separate lookup key keeps runtime verification feasible without
turning the database into a secret store. The lookup field avoids scanning/verifying every row for
every request.

### Decision: Authenticate API keys through a separate WebFlux authentication adapter, not the JWT converter

**Choice**: Add a narrow API-key authentication web filter/converter that runs alongside the current
JWT-based resource-server path.

**Alternatives considered**:

- Parse API keys through `oauth2ResourceServer().jwt(...)`.
- Pretend API keys are JWT bearer tokens and feed them into `ReactiveJwtDecoder`.
- Replace the whole authentication chain with a generalized multi-transport redesign.

**Rationale**: API keys are lookup + verifier credentials, not signed claim containers. Treating
them like JWTs would produce brittle and misleading semantics. A small parallel adapter preserves
the current JWT path while enabling correct API-key handling.

### Decision: Reuse the existing principal and authorization seams after authentication

**Choice**: Materialize a normal `AuthenticatedPrincipal` with `PrincipalType.API_KEY` and
`CredentialType.API_KEY`, then reuse the existing workspace-context and authorization services
unchanged.

**Alternatives considered**:

- Add API-key-specific authorization code paths.
- Read workspace or permission grants directly from API-key records.

**Rationale**: The current architecture already keeps authorization truth in memberships, roles, and
direct grants. Reusing that path proves API keys are only an authentication transport and
principal-establishment mechanism.

### Decision: Enforce inactive/revoked state authoritatively before principal establishment completes

**Choice**: Perform API-key credential lookup, status check, and verifier comparison before the
request obtains an authenticated principal or enters the protected use case.

**Alternatives considered**:

- Allow principal materialization first and defer state checks to authorization.
- Treat inactive/revoked keys as generic authorization denial.

**Rationale**: The specs require inactive/revoked API keys to be denied before protected access is
granted. This mirrors the current service-account credential-state enforcement model and preserves a
clean distinction between authentication failure and authorization failure.

## Data Flow

### Request authentication flow

```text
Client request
  └─ Authorization: Bearer <value>
        │
        ▼
IdentitySecurityConfiguration
        │
        ├─ If request matches JWT path behavior:
        │     oauth2ResourceServer().jwt(...)
        │     └─ JwtPrincipalAuthenticationConverter
        │
        └─ If request targets proving slice and bearer value matches API-key format:
              ApiKeyAuthenticationWebFilter
                └─ ApiKeyAuthenticationConverter
                     └─ ApiKeyCredentialAuthenticator
                          ├─ extract lookup key / prefix
                          ├─ load persisted credential row
                          ├─ verify presented secret against stored verifier
                          ├─ require ACTIVE status
                          └─ materialize AuthenticatedPrincipal(API_KEY)
                                │
                                ▼
                     AuthenticatedPrincipalAuthenticationToken
                                │
                                ▼
AuthenticatedPrincipalContextWebFilter
WorkspaceContextWebFilter
GetCurrentWorkspaceAccessSummaryQuery
WorkspaceAuthorizationService
AuditHook
```

### Authoritative API-key verification flow

```text
Presented API key
   └─ parse into lookup segment + secret segment
         └─ R2dbcApiKeyCredentialStateLookup.findByLookupKey(...)
               └─ api_key_credentials + principals join
                     ├─ no row -> unauthenticated
                     ├─ status != ACTIVE -> revoked/inactive denial
                     └─ row found -> verify secret against verifier
                           ├─ mismatch -> unauthenticated
                           └─ match -> ActiveApiKeyCredential
                                  └─ ApiKeyAuthenticatedPrincipalMaterializer
```

### Current-slice allow/deny behavior

```text
Authenticated API_KEY principal
        │
        ▼
Workspace membership resolution
        │
        ├─ no active membership / no permission / direct deny
        │     └─ 403 authorization denial + audit fact
        │
        └─ explicit allow via role permission or direct allow
              └─ 200 summary response + audit fact

Revoked/inactive API key
        └─ 401 before protected query executes + REVOKED_CREDENTIAL audit fact
```

## File Changes

| File                                                                                                                           | Action                              | Description                                                                                                                                      |
|--------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `openspec/changes/backend-api-key-support/design.md`                                                                           | Create                              | Technical design artifact for this change.                                                                                                       |
| `server/smp/src/main/resources/db/changelog/credentials/002-create-api-key-credentials.yaml`                                   | Create                              | Minimal schema for persisted API-key credential state.                                                                                           |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`                                                          | Modify                              | Include the new API-key credential changelog.                                                                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`          | Modify                              | Register the API-key authentication adapter alongside the existing JWT path and extend revoked-credential audit handling.                        |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/ApiKeyAuthenticationWebFilter.kt`          | Create                              | Narrow proving-slice filter that extracts API-key requests and drives authentication.                                                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/ApiKeyPrincipalAuthenticationConverter.kt` | Create                              | Converts a presented API key into `AuthenticatedPrincipalAuthenticationToken` through lookup + verifier validation.                              |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/ApiKeyAuthenticatedPrincipalMaterializer.kt`        | Create                              | Materializes repo-local `AuthenticatedPrincipal` for `PrincipalType.API_KEY`.                                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ApiKeyCredentialStateLookup.kt`                     | Create                              | Credential-state contract for authoritative API-key lookup and active/revoked enforcement.                                                       |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcApiKeyCredentialStateLookup.kt`             | Create                              | R2DBC implementation joining API-key credential state to persisted principals.                                                                   |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ApiKeySecretVerifier.kt`                            | Create                              | Small abstraction for verifier comparison so storage semantics stay explicit and testable.                                                       |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/domain/ValidatedToken.kt`                                       | Modify minimally or leave unchanged | Keep JWT-specific normalized token behavior unchanged unless a tiny shared abstraction is needed; avoid forcing API keys through this JWT model. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`               | Modify                              | Add H2 proving-slice scenarios for API-key allow, authorization deny, and revoked/inactive deny.                                                 |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt`       | Modify                              | Add PostgreSQL equivalents for the same API-key scenarios.                                                                                       |

## Interfaces / Contracts

### Minimal persisted credential schema

```sql
api_key_credentials (
  id varchar(64) primary key,
  principal_id varchar(64) not null references principals(id),
  lookup_key varchar(255) not null,
  key_prefix varchar(64) not null,
  secret_verifier varchar(255) not null,
  status varchar(32) not null,
  revoked_at timestamp with time zone null,
  created_at timestamp with time zone not null default CURRENT_TIMESTAMP,
  unique (lookup_key)
)
```

### Schema notes

- `principal_id`: binds one API key to one persisted principal.
- `lookup_key`: stable identifier derived from the non-secret portion of the API key, used to fetch
  exactly one candidate record.
- `key_prefix`: human-readable/test-seeding-friendly prefix retained only as minimal metadata for
  diagnostics and future issuance compatibility; not used as authorization truth.
- `secret_verifier`: one-way verifier representation of the secret portion; no plaintext
  persistence.
- `status`: initially narrow enum-like string values `ACTIVE` and `REVOKED`.
- `revoked_at`: authoritative revocation timestamp when status is `REVOKED`.

### API-key state contract

```kotlin
data class ActiveApiKeyCredential(
    val principalId: String,
    val credentialReference: String,
    val subject: String,
    val provider: String?,
)

interface ApiKeyCredentialStateLookup {
    suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential
}

interface ApiKeySecretVerifier {
    fun matches(presentedSecret: String, storedVerifier: String): Boolean
}
```

### Failure model

```kotlin
enum class ApiKeyCredentialFailureReason {
    MISSING,
    INVALID,
    REVOKED,
}

class ApiKeyCredentialNotActiveException(
    val credentialReference: String,
    val principalId: String? = null,
    val principalType: PrincipalType = PrincipalType.API_KEY,
    val reason: ApiKeyCredentialFailureReason,
) : BadCredentialsException(...)
```

### Principal materialization contract

```kotlin
suspend fun materialize(activeCredential: ActiveApiKeyCredential): AuthenticatedPrincipal
```

The materializer loads the bound principal from `principals` using the existing
`PrincipalIdentityLookup`, requiring:

- `principal_type = API_KEY`
- a persisted `subject`
- minimal display identity if available

Resulting principal context:

```kotlin
PrincipalContext(
  principalId = persistedPrincipal.id,
  principalType = PrincipalType.API_KEY,
  subject = persistedPrincipal.subject,
  provider = persistedPrincipal.provider,
  displayIdentity = persistedPrincipal.displayIdentity ?: persistedPrincipal.subject,
  authenticationMethod = "API_KEY",
  issuedCredentialReference = activeCredential.credentialReference,
  attributes = emptyMap(),
)
```

## Detailed Technical Design

### 1. Minimal schema additions for secure API-key credential state

The new table intentionally mirrors the narrowness of `service_account_credentials` while adding the
extra fields API keys actually need.

It does **not** add:

- expiration policy frameworks,
- rotation lineage,
- last-used timestamps,
- labels/tags inventory,
- ownership hierarchies,
- secret reveal support.

The record only answers four runtime questions:

1. Which row should this presented key map to?
2. Does the presented secret verify against stored verifier material?
3. Which persisted principal does this key belong to?
4. Is the credential active or revoked?

### 2. API-key transport and authentication alongside JWT

The current `securityWebFilterChain` is JWT-first through `oauth2ResourceServer().jwt(...)`. That
stays in place.

The API-key expansion adds a small proving-slice-only filter before or at authentication order that:

- checks whether the request targets `/api/authorization/workspace-access/current`,
- reads the `Authorization` header,
- recognizes only the agreed API-key format,
- authenticates through lookup + verifier comparison,
- writes an authenticated token into the reactive security context when successful,
- otherwise falls through to the existing JWT path when the request is not an API-key attempt.

This keeps coexistence practical:

- JWTs still use the existing decoder/converter path.
- API keys never enter `ReactiveJwtDecoder`.
- The proving slice can accept either transport without redefining the rest of the security stack.

### 3. API_KEY principal materialization on the proving slice

After successful credential verification, the system materializes an `AuthenticatedPrincipal` with:

- `credentialType = CredentialType.API_KEY`
- `context.principalType = PrincipalType.API_KEY`
- `authenticationMethod = "API_KEY"`
- `issuedCredentialReference = api-key credential row identifier or lookup reference`

Materialization uses the existing `PrincipalIdentityLookup`, which already supports looking up
arbitrary principal types from `principals`. No new identity ledger is needed.

The API key itself is not passed downstream as identity truth. Only the repo-local principal context
moves into authorization.

### 4. Active/revoked enforcement before authorization

The authoritative check lives in the credential-state lookup layer, not in the authorization
service.

Ordered behavior:

1. Parse presented key.
2. Resolve persisted record by lookup key.
3. If no row exists, fail authentication.
4. If row status is not `ACTIVE`, throw API-key credential exception.
5. If verifier does not match, fail authentication.
6. If principal cannot be materialized as `API_KEY`, fail authentication.
7. Only then allow workspace-context and authorization evaluation to run.

Revoked/inactive outcomes therefore remain authentication failures with a `401`, while
authorization-controlled deny remains a `403` from the existing query/handler path.

### 5. Demonstrating allow, authorization deny, and revoked/inactive deny on the current slice

The existing integration suites already prove these outcomes for USER and SERVICE_ACCOUNT. The
API-key expansion extends the same pattern in both H2 and PostgreSQL suites.

#### Allow scenario

Seed:

- persisted `API_KEY` principal,
- persisted `api_key_credentials` row with `ACTIVE` status,
- active workspace membership,
- role permission `workspace:access:read`.

Request result:

- `200 OK`
- same response shape as existing slice
- audit fact with `decision = ALLOW`, `reasonCode = ROLE_PERMISSION`

#### Authorization-controlled deny scenario

Seed:

- persisted `API_KEY` principal,
- persisted active API-key credential,
- active workspace membership,
- role that lacks `workspace:access:read`.

Request result:

- `403 Forbidden`
- audit fact with `decision = DENY`, authorization reason such as `MISSING_PERMISSION`
- demonstrates deny comes from authorization state, not from credential-type mismatch

#### Revoked/inactive deny scenario

Seed:

- persisted `API_KEY` principal,
- persisted API-key credential row marked `REVOKED` (or other non-active state if implementation
  chooses to support it later, though current design keeps the stored state narrow),
- verifier that would otherwise match.

Request result:

- `401 Unauthorized`
- protected query does not execute
- audit fact with `decision = DENY`, `reasonCode = REVOKED_CREDENTIAL`

Because `AuthorizationReasonCode` already contains `REVOKED_CREDENTIAL`, governance can stay narrow
and reuse the existing runtime proof seam.

### 6. Scope-control rules for this change

To prevent drift, the implementation must reject the following additions unless a later change is
approved:

- issuance endpoints,
- admin CRUD,
- rotation APIs,
- inventory/listing APIs,
- broader reporting metadata,
- durable governance storage,
- generic credential super-model redesign,
- support for endpoints beyond `/api/authorization/workspace-access/current`.

If a helper or field does not directly support the proving slice scenarios above, it should be
deferred.

## Testing Strategy

| Layer       | What to Test                                                   | Approach                                                                                                     |
|-------------|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Unit        | API-key request parsing and lookup-key extraction              | Focused tests for the API-key converter/parser with valid, invalid, and ambiguous inputs.                    |
| Unit        | Verifier comparison and inactive/revoked failure mapping       | Tests for `ApiKeySecretVerifier` and `ApiKeyCredentialStateLookup` failure reasons.                          |
| Unit        | API-key principal materialization                              | Materializer tests similar to existing JWT/service-account materializer coverage.                            |
| Integration | H2 proving slice allow/authorization deny/revoked deny         | Extend `WorkspaceAccessSummaryEndpointIntegrationTest` with seeded `API_KEY` principal + credential rows.    |
| Integration | PostgreSQL proving slice allow/authorization deny/revoked deny | Mirror the same scenarios in `WorkspaceAccessSummaryEndpointPostgresIntegrationTest`.                        |
| E2E         | Not required for this change                                   | Existing integration tests already exercise the protected HTTP slice end-to-end within the backend boundary. |

## Migration / Rollout

Add a single Liquibase changelog for `api_key_credentials` and include it from
`db.changelog-master.yaml`.

No data migration is required because API-key runtime support does not exist yet.

Rollout sequence:

1. Ship schema.
2. Ship authentication adapter and credential lookup.
3. Prove behavior via H2 and PostgreSQL slice tests.

Rollback sequence stays small:

1. Remove or disable API-key authentication adapter.
2. Leave the table inert if already applied, or revert the migration if still safe in pre-promotion
   environments.

## Open Questions

- [ ] What exact API-key wire format should the proving slice accept so it is unambiguous against
  JWT bearer values while still using the `Authorization` header?
- [ ] Should non-active-but-not-revoked API-key rows be represented initially as a second explicit
  stored status, or should this narrow change keep only `ACTIVE` and `REVOKED` while treating all
  non-matching rows as invalid?
- [ ] Which verifier algorithm/library is already preferred in `server/smp` for one-way secret
  verification, if any, so the implementation does not introduce unnecessary dependency breadth?
