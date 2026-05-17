# Design: Backend Credentials Expansion

## Technical Approach

This change adds one new executable actor path only: **service-account bearer authentication** on
the existing backend proving slice at `/api/authorization/workspace-access/current`.

The implementation stays inside the current Spring Security JWT resource-server flow instead of
introducing a second transport. A cryptographically valid bearer token continues through the
existing JWT decoder, is normalized into the repo-local `ValidatedToken`, and is then materialized
into either a `USER` principal or a `SERVICE_ACCOUNT` principal.

For service accounts, authentication becomes **two-step valid**:

1. **Transport/token validity** — the bearer token is accepted by the existing JWT resource-server
   boundary.
2. **Authoritative backend credential validity** — the token must map to an active persisted
   service-account credential record that is not revoked.

Only after both checks succeed does the request reach the existing workspace membership and
authorization flow. This keeps the change narrow:

- no new protected endpoint,
- no new credential transport,
- no generic credential control plane,
- no user-JWT revocation redesign,
- no admin or issuance APIs.

The proving behavior on `/api/authorization/workspace-access/current` will cover exactly three
service-account outcomes:

- **allow** — authenticated service account with active membership and required permission,
- **authorization-controlled deny** — authenticated service account without the required permission,
- **revoked-credential deny** — cryptographically valid service-account bearer token rejected by
  persisted credential state before authorization executes.

## Scope Guardrails

To keep this change intentionally small, the implementation MUST stay within these boundaries:

- Only bearer credentials that are already accepted by the current JWT resource-server path are in
  scope.
- Only `PrincipalType.SERVICE_ACCOUNT` is newly activated.
- Only service-account credential revocation state is newly enforced.
- Only `/api/authorization/workspace-access/current` is the proving endpoint.
- API keys, rotation families, dual-active rollover, end-user JWT revocation, credential
  issuance/admin APIs, and UI/operator surfaces remain deferred.
- No broad credential-platform abstraction layer SHALL be introduced beyond what this path directly
  needs.

## Architecture Decisions

### Decision: Reuse the existing `principals` model for persisted service-account identity

**Choice**: Persist service accounts as rows in the existing `principals` table with
`principal_type = SERVICE_ACCOUNT`, and do not add a dedicated service-account profile table in this
change.

**Alternatives considered**:

- Create a new `service_account_identities` table now.
- Create a full machine-identity aggregate with ownership, metadata, lifecycle flags, and admin
  fields.

**Rationale**: The current schema already supports principal taxonomy through
`principals.principal_type`, `subject`, `provider`, and `display_identity`. Existing membership and
authorization tables already carry `principal_type`, so they can authorize service accounts without
schema redesign. A new profile table would add persistence breadth without enabling a new behavior
needed by this slice.

### Decision: Add one narrow credential-state table for service-account bearer records

**Choice**: Add a dedicated persisted table for service-account credential references and revocation
state, for example `service_account_credentials`.

**Alternatives considered**:

- Store revocation state directly on `principals`.
- Build a generic cross-credential inventory/ledger for JWTs, service accounts, API keys, and future
  credentials.
- Add a blacklist store for all bearer tokens.

**Rationale**: Revocation is about a **credential instance**, not the principal itself. Putting
revocation on `principals` would disable the actor rather than the presented credential. A generic
credential ledger is broader than this change. One service-account-specific credential-state table
gives an authoritative revocation check with minimal schema and no platform redesign.

### Decision: Keep the current Spring Security JWT entry path and branch inside repo-local materialization

**Choice**: Continue using `IdentitySecurityConfiguration` with `oauth2ResourceServer().jwt(...)`,
then extend the repo-local token mapping/materialization path so a validated bearer token can
materialize either a `USER` or a `SERVICE_ACCOUNT` principal.

**Alternatives considered**:

- Add a parallel custom authentication filter for service accounts.
- Introduce API-key-style parsing or a second header format now.
- Replace the resource-server path with token introspection or a custom credential gateway.

**Rationale**: The current flow already validates bearer JWTs and propagates authenticated principal
context correctly. Reusing it is the smallest move. The new behavior is not “new transport”; it is
“new principal materialization plus backend credential-state enforcement” on top of the current
transport.

### Decision: Treat backend credential state as authoritative only for service-account tokens

**Choice**: Run revocation lookup only when the validated bearer token is classified as a
service-account credential.

**Alternatives considered**:

- Force all JWT-backed requests, including end-user JWTs, through backend credential inventory.
- Add user-JWT blacklist/session invalidation in the same change.

**Rationale**: End-user JWT revocation is a different product and infrastructure problem because
current user auth is externally validated and intentionally stateless. This change needs
authoritative backend invalidation only for the newly activated service-account credential path.

### Decision: Prove behavior on the existing workspace-access slice with 200, 403, and 401 outcomes

**Choice**: Extend the existing H2 and PostgreSQL endpoint integration tests for
`/api/authorization/workspace-access/current` to cover:

- service-account allow (`200`),
- service-account authorization deny (`403`),
- revoked service-account credential deny (`401`).

**Alternatives considered**:

- Add a dedicated service-account-only endpoint.
- Prove behavior only at the unit or converter layer.
- Build admin flows first and defer protected-slice proof.

**Rationale**: The endpoint already proves authentication → principal context → workspace context →
authorization → response. Reusing it demonstrates that service accounts are governed by the same
current path instead of a special bypass.

## Data Flow

### Service-account allow path

```text
HTTP GET /api/authorization/workspace-access/current
    -> Spring Security JWT resource server validates bearer token
    -> SpringJwtValidatedTokenMapper maps Jwt -> ValidatedToken
       (includes service-account classification + credential reference)
    -> Authenticated principal materializer
        -> if USER: existing lookup/materialization path
        -> if SERVICE_ACCOUNT:
             -> ServiceAccountCredentialStateLookup.requireActive(...)
             -> PrincipalIdentityLookup.findBySubject(SERVICE_ACCOUNT, subject, provider)
             -> materialize AuthenticatedPrincipal(SERVICE_ACCOUNT, SERVICE_ACCOUNT)
    -> AuthenticatedPrincipalContextWebFilter stores principal context
    -> WorkspaceContextWebFilter stores active workspace
    -> WorkspaceAccessSummaryController
    -> GetCurrentWorkspaceAccessSummaryHandler
        -> WorkspaceAuthorizationService.decideDetailed(workspace:access:read)
        -> allow through existing membership/role/direct-grant path
    -> 200 OK
```

### Revoked service-account credential deny path

```text
HTTP GET /api/authorization/workspace-access/current
    -> Spring Security JWT resource server validates bearer token
    -> SpringJwtValidatedTokenMapper maps Jwt -> ValidatedToken
    -> Authenticated principal materializer detects SERVICE_ACCOUNT
        -> ServiceAccountCredentialStateLookup.requireActive(...)
        -> credential record is REVOKED
        -> throw authentication failure
    -> request never reaches controller or authorization handler
    -> 401 Unauthorized
```

### Sequence diagram — service-account allow

```text
Client
  -> SecurityWebFilterChain: Bearer token
SecurityWebFilterChain
  -> ReactiveJwtDecoder: validate signature/claims
ReactiveJwtDecoder
  --> JwtPrincipalAuthenticationConverter: Jwt
JwtPrincipalAuthenticationConverter
  -> SpringJwtValidatedTokenMapper: validate(jwt)
SpringJwtValidatedTokenMapper
  --> JwtPrincipalAuthenticationConverter: ValidatedToken(SERVICE_ACCOUNT)
JwtPrincipalAuthenticationConverter
  -> AuthenticatedPrincipalMaterializer: materialize(token)
AuthenticatedPrincipalMaterializer
  -> ServiceAccountCredentialStateLookup: requireActive(credentialReference, subject, issuer)
ServiceAccountCredentialStateLookup
  --> AuthenticatedPrincipalMaterializer: ACTIVE record
AuthenticatedPrincipalMaterializer
  -> PrincipalIdentityLookup: findBySubject(SERVICE_ACCOUNT, subject, issuer)
PrincipalIdentityLookup
  --> AuthenticatedPrincipalMaterializer: principal facts
AuthenticatedPrincipalMaterializer
  --> JwtPrincipalAuthenticationConverter: AuthenticatedPrincipal
JwtPrincipalAuthenticationConverter
  --> SecurityWebFilterChain: Authentication token
SecurityWebFilterChain
  -> WorkspaceAccessSummaryController: authenticated request
WorkspaceAccessSummaryController
  -> GetCurrentWorkspaceAccessSummaryHandler: handle(query)
GetCurrentWorkspaceAccessSummaryHandler
  -> WorkspaceAuthorizationService: decideDetailed(workspace:access:read)
WorkspaceAuthorizationService
  --> GetCurrentWorkspaceAccessSummaryHandler: ALLOW
GetCurrentWorkspaceAccessSummaryHandler
  --> Client: 200 OK
```

### Sequence diagram — service-account revoked deny

```text
Client
  -> SecurityWebFilterChain: Bearer token
SecurityWebFilterChain
  -> ReactiveJwtDecoder: validate signature/claims
ReactiveJwtDecoder
  --> JwtPrincipalAuthenticationConverter: Jwt
JwtPrincipalAuthenticationConverter
  -> SpringJwtValidatedTokenMapper: validate(jwt)
SpringJwtValidatedTokenMapper
  --> JwtPrincipalAuthenticationConverter: ValidatedToken(SERVICE_ACCOUNT)
JwtPrincipalAuthenticationConverter
  -> AuthenticatedPrincipalMaterializer: materialize(token)
AuthenticatedPrincipalMaterializer
  -> ServiceAccountCredentialStateLookup: requireActive(credentialReference, subject, issuer)
ServiceAccountCredentialStateLookup
  --> AuthenticatedPrincipalMaterializer: REVOKED
AuthenticatedPrincipalMaterializer
  --> SecurityWebFilterChain: authentication failure
SecurityWebFilterChain
  --> Client: 401 Unauthorized
```

## File Changes

| File                                                                                                                            | Action | Description                                                                                                                                             |
|---------------------------------------------------------------------------------------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `openspec/changes/backend-credentials-expansion/design.md`                                                                      | Create | Technical design for the narrow service-account credential expansion.                                                                                   |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`                                                           | Modify | Include the new credentials changelog for service-account credential state.                                                                             |
| `server/smp/src/main/resources/db/changelog/credentials/001-create-service-account-credentials.yaml`                            | Create | Add persisted credential reference and revocation state for service-account bearer credentials.                                                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/domain/ValidatedToken.kt`                                        | Modify | Add the minimal typed fields needed to classify bearer tokens and carry a stable credential reference into materialization.                             |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/security/SpringJwtValidatedTokenMapper.kt`        | Modify | Map service-account hint claims and credential reference from the decoded JWT into `ValidatedToken`.                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ServiceAccountCredentialStateLookup.kt`              | Create | Define the narrow application seam for authoritative active/revoked credential lookup.                                                                  |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcServiceAccountCredentialStateLookup.kt`      | Create | Query persisted service-account credential state by credential reference plus principal facts.                                                          |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/JwtAuthenticatedPrincipalMaterializer.kt`            | Modify | Extend materialization to branch between USER and SERVICE_ACCOUNT and to reject revoked service-account credentials.                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityBootstrapConfiguration.kt`                   | Modify | Wire the materializer with the new credential-state lookup dependency.                                                                                  |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcPrincipalIdentityLookup.kt`                     | Modify | Preserve current USER behavior while remaining able to load persisted `SERVICE_ACCOUNT` principal facts cleanly.                                        |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt`                                   | Modify | Add one narrow authorization reason for revoked credential denial only if runtime proof is needed on this path; otherwise no broad governance redesign. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/JwtAuthenticatedPrincipalMaterializerTest.kt`        | Modify | Add unit coverage for service-account materialization and revoked-credential rejection.                                                                 |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/security/JwtPrincipalAuthenticationConverterTest.kt` | Modify | Prove the converter carries a service-account principal through the current authentication path.                                                        |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`                | Modify | Add H2-backed service-account allow, authorization deny, and revoked-credential deny scenarios on the existing endpoint.                                |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt`        | Modify | Add PostgreSQL-backed service-account allow, authorization deny, and revoked-credential deny scenarios on the same endpoint.                            |

## Interfaces / Contracts

### Persisted credential-state model

The minimal new table should represent a **managed service-account credential instance**, not a
generic credential platform.

```sql
service_account_credentials (
  id varchar(64) primary key,
  principal_id varchar(64) not null references principals(id),
  provider varchar(255) not null,
  credential_reference varchar(255) not null,
  status varchar(32) not null, -- ACTIVE | REVOKED
  revoked_at timestamp with time zone null,
  created_at timestamp with time zone not null default current_timestamp,
  unique (provider, credential_reference)
)
```

### Token materialization contract

`ValidatedToken` should carry only the extra fields needed for this branch.

```kotlin
data class ValidatedToken(
    val credentialType: CredentialType,
    val tokenValue: String,
    val subject: String,
    val issuer: String,
    val audience: Set<String>,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
    val tokenId: String? = null,
    val claims: Map<String, String> = emptyMap(),
    val principalTypeHint: PrincipalType = PrincipalType.USER,
    val credentialReference: String? = tokenId,
)
```

Contract notes:

- `principalTypeHint` is derived from a narrow service-account claim convention, such as
  `principal_type=SERVICE_ACCOUNT` or `actor_type=service_account`.
- `credentialReference` is the stable lookup key for revocation, normally JWT `jti` in this change.
- User tokens continue to materialize exactly as today unless explicitly marked as service-account
  tokens.

### Service-account credential-state lookup seam

```kotlin
interface ServiceAccountCredentialStateLookup {
    suspend fun requireActive(
        credentialReference: String,
        subject: String,
        provider: String,
    ): ActiveServiceAccountCredential
}

data class ActiveServiceAccountCredential(
    val principalId: String,
    val credentialReference: String,
)
```

Contract notes:

- `requireActive(...)` MUST fail when the credential record is missing or revoked.
- The lookup MUST bind credential reference to the persisted `SERVICE_ACCOUNT` principal, not only
  to a free-floating token identifier.
- This seam is intentionally service-account-specific; it is not a generic credential registry
  contract.

### Principal materialization outcome

For the new path, the materialized principal should look like:

```kotlin
AuthenticatedPrincipal(
    context = PrincipalContext(
        principalId = persistedPrincipalId,
        principalType = PrincipalType.SERVICE_ACCOUNT,
        subject = token.subject,
        provider = token.issuer,
        displayIdentity = persistedDisplayIdentity,
        authenticationMethod = "JWT_BEARER",
        issuedCredentialReference = token.credentialReference,
        attributes = token.claims,
    ),
    credentialType = CredentialType.SERVICE_ACCOUNT,
)
```

That preserves the existing downstream contract: authorization still consumes `PrincipalContext`,
not credential-specific logic.

## Revocation Enforcement Design

Revocation is checked **authoritatively** in the current authentication path by introducing a single
backend lookup step for service-account tokens.

The enforcement rule is:

1. If a bearer token is materialized as `USER`, keep the current path.
2. If a bearer token is materialized as `SERVICE_ACCOUNT`, require:
    - a credential reference,
    - a persisted `service_account_credentials` row,
    - `status = ACTIVE`,
    - principal binding that matches the token subject/provider.
3. If any of those checks fail, authentication fails with `401 Unauthorized`.

This is intentionally **not** a broad credential-platform redesign because:

- it does not create universal revocation across all credential types,
- it does not require a general credential administration API,
- it does not introduce rotation workflows,
- it does not change workspace authorization rules,
- it does not alter end-user JWT semantics.

## Proving Slice Coverage

The existing `/api/authorization/workspace-access/current` slice will demonstrate three outcomes for
service accounts:

### 1. Allow

- Service-account bearer token is cryptographically valid.
- Persisted service-account credential record is `ACTIVE`.
- Persisted principal exists as `SERVICE_ACCOUNT`.
- Active workspace membership exists.
- Required permission is granted through current role/direct-grant rules.
- Result: `200 OK`.

### 2. Authorization-controlled deny

- Service-account bearer token is cryptographically valid.
- Persisted service-account credential record is `ACTIVE`.
- Persisted principal and active workspace membership exist.
- Required permission is not granted.
- Result: existing authorization deny path returns `403 Forbidden`.

### 3. Revoked-credential deny

- Service-account bearer token is cryptographically valid.
- Persisted service-account credential record is `REVOKED`.
- Authentication fails before controller/authorization execution.
- Result: `401 Unauthorized`.

This keeps the proof honest:

- `200` proves service-account authentication works.
- `403` proves authorization still governs authenticated service accounts.
- `401` proves backend credential revocation is authoritative.

## Testing Strategy

| Layer       | What to Test                                       | Approach                                                                                                                                                 |
|-------------|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Unit        | Token classification and principal materialization | Extend `JwtAuthenticatedPrincipalMaterializerTest` to cover USER passthrough, SERVICE_ACCOUNT materialization, and revoked/missing credential rejection. |
| Unit        | Credential-state lookup                            | Add focused repository tests for active vs revoked vs missing `service_account_credentials` rows.                                                        |
| Integration | Current authentication path with service accounts  | Extend `JwtPrincipalAuthenticationConverterTest` and Spring integration flow to prove a decoded JWT can become `PrincipalType.SERVICE_ACCOUNT`.          |
| Integration | Existing proving endpoint on H2                    | Extend `WorkspaceAccessSummaryEndpointIntegrationTest` with service-account `200`, `403`, and `401` cases.                                               |
| Integration | Existing proving endpoint on PostgreSQL            | Extend `WorkspaceAccessSummaryEndpointPostgresIntegrationTest` with the same service-account `200`, `403`, and `401` cases.                              |
| E2E         | None                                               | Not needed for this narrow backend change.                                                                                                               |

### Test data shape

The new endpoint scenarios should seed:

- one `principals` row with `principal_type = SERVICE_ACCOUNT`,
- one `workspace_memberships` row for that principal,
- role/permission rows matching the current proving slice,
- one `service_account_credentials` row with either `ACTIVE` or `REVOKED` status,
- a stubbed JWT whose claims identify the token as a service-account bearer and provide the same
  credential reference as the seeded row.

## Migration / Rollout

A small schema migration is required.

Rollout order:

1. Add the `service_account_credentials` changelog and include it in the master changelog.
2. Deploy code that can still authenticate USER tokens exactly as today.
3. Seed service-account principals, memberships, and active credential records only for environments
   that need the new path.
4. Begin using service-account bearer tokens on the existing protected endpoint.

No backfill is required for current USER behavior.

## Deferred by Design

The following items are explicitly out of scope and MUST stay deferred:

- API-key credential parsing, hashing, storage, and lookup.
- Rotation workflows, credential families, dual-active rollover, and automated replacement flows.
- End-user JWT revocation, blacklist, or session invalidation.
- Service-account issuance APIs, revocation APIs, admin consoles, and operator UI.
- Broad governance persistence/reporting or a generalized credential event framework.
- Generic machine-identity abstractions beyond this concrete service-account bearer path.

## Open Questions

- [ ] Confirm the exact JWT claim convention that will mark a bearer token as a service-account
  token (`principal_type`, `actor_type`, or equivalent). The design assumes one explicit claim and
  does not require a broader token taxonomy redesign.
- [ ] Confirm whether revoked-credential denial needs a dedicated runtime audit fact on this path,
  or whether HTTP `401` proof in integration coverage is sufficient for this narrow change.
