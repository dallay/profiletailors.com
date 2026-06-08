## Exploration: backend-api-key-support

### Current State

`server/smp` is no longer seam-only for machine credentials in general, but **API keys specifically
are still deferred**.

What already exists and is executable:

- USER authentication works through Spring Security JWT resource-server flow.
- SERVICE_ACCOUNT authentication is now real on `/api/authorization/workspace-access/current`.
- Service-account bearer tokens are classified in `SpringJwtValidatedTokenMapper`, materialized in
  `JwtAuthenticatedPrincipalMaterializer`, and checked against authoritative backend credential
  state through `ServiceAccountCredentialStateLookup`.
- Persisted service-account credential state exists in `service_account_credentials` with `ACTIVE` /
  `REVOKED` enforcement.
- Authorization remains credential-agnostic after principal materialization: workspace membership,
  roles, permissions, and direct grants decide access.
- H2 and PostgreSQL-backed tests already prove service-account allow, authorization deny, and
  revoked-credential deny outcomes on the existing proving slice.
- Governance/audit seams already distinguish revoked credential denial via
  `AuthorizationReasonCode.REVOKED_CREDENTIAL`.

What exists only as model/seam for API keys:

- `CredentialType.API_KEY` exists.
- `PrincipalType.API_KEY` exists.
- Main specs still name API keys as first-class credential and principal concepts.
- No API-key table, hash store, prefix/lookup model, or revocation state exists.
- No API-key request parser or authentication filter exists.
- No API-key principal materialization path exists.
- No tests prove API-key authentication on the proving slice.

Important reality check: because the current security boundary is still
`oauth2ResourceServer().jwt(...)`, API keys cannot be added by configuration alone. They require a *
*new credential transport/adapter path**, unlike the last service-account change which reused JWT
bearer transport.

### Affected Areas

- `openspec/specs/credentials/spec.md` — currently still says API key auth/storage/lookup/request
  handling are deferred; this is the main spec surface to advance.
- `openspec/specs/identity/spec.md` — API_KEY exists in taxonomy but has no executable behavior yet.
- `openspec/specs/platform/spec.md` — current proving-slice protection is already shared across USER
  and SERVICE_ACCOUNT; API key support should extend the same slice, not create a bypass.
- `openspec/specs/governance/spec.md` — runtime audit semantics already exist and should classify
  API-key allow/deny without broad governance expansion.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` —
current auth chain is JWT-only and is the main place where API-key transport integration would need
to happen.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/JwtPrincipalAuthenticationConverter.kt` —
current converter is JWT-specific and cannot directly authenticate API-key requests.

- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/domain/ValidatedToken.kt` — current
  normalized credential object is token/bearer oriented; API-key support may need either careful
  extension or a parallel normalized credential shape.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/JwtAuthenticatedPrincipalMaterializer.kt` —
currently branches USER vs SERVICE_ACCOUNT only.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcPrincipalIdentityLookup.kt` —
already broad enough to load an `API_KEY` principal if one exists in `principals`.

- `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` — will need a new
  credentials changelog if API-key persistence is introduced.
-

`server/smp/src/main/resources/db/changelog/credentials/001-create-service-account-credentials.yaml` —
useful reference for narrow credential-instance state, but not enough for API-key storage because
API keys need lookup-safe secret handling.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt` —
existing proving-slice test harness can be extended for API-key allow/deny scenarios.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` —
same for PostgreSQL proof.

### Approaches

1. **Full API-key management wave** — add issuance, listing, revocation, rotation, metadata,
   ownership, and authentication together.
    - Pros: complete product surface.
    - Cons: scope explosion; forces lifecycle/admin decisions, secret display rules, and operational
      UX too early.
    - Effort: High

2. **Minimal executable API-key authentication on the existing proving slice** — add only the
   smallest persistence and transport needed to accept an API key, materialize an API_KEY principal,
   enforce active/revoked state, and run the existing workspace-access endpoint.
    - Pros: delivers real API-key capability; preserves the proving-slice discipline; extends
      existing authorization/audit path instead of inventing a second protected feature.
    - Cons: still requires a new authentication adapter because JWT-only transport cannot carry it;
      needs careful secret-storage design.
    - Effort: Medium

3. **Seam-first API-key prep only** — define contracts/tables without executable request handling.
    - Pros: smallest code change.
    - Cons: weak value; repeats the same “model exists but behavior is deferred” problem we are
      explicitly trying to leave behind.
    - Effort: Low/Medium

### Recommendation

Recommend **Approach 2: minimal executable API-key authentication on the existing proving slice**.

This is the smallest coherent next change because it adds **one real new credential capability**
without drifting into credential-management sprawl:

- authenticate a presented API key,
- map it to a persisted API-key principal,
- enforce active/revoked backend state,
- reuse the same `/api/authorization/workspace-access/current` endpoint,
- continue to let membership/roles/direct grants decide authorization.

That keeps the change focused on **runtime authentication capability**, not on full lifecycle
management.

#### What belongs in this change

- A persisted API-key credential record with **secure lookup semantics**:
    - stable key identifier/prefix for lookup,
    - hashed secret material or verifier representation,
    - principal binding,
    - active/revoked state,
    - creation/revocation timestamps.
- Minimal persisted `API_KEY` principal support using the existing `principals` model.
- One request authentication path for API keys on the proving slice, preferably a narrow custom
  WebFlux authentication converter/filter that coexists with the current JWT flow.
- Repo-local credential normalization/materialization into `PrincipalType.API_KEY` and
  `CredentialType.API_KEY`.
- Authoritative revocation enforcement for API keys before authorization executes.
- H2 and PostgreSQL verification on `/api/authorization/workspace-access/current` covering at least:
    - API-key allow,
    - API-key authorization deny,
    - revoked or inactive API-key deny.
- Runtime audit-ready proof for API-key allow/deny on the same existing slice, reusing current
  governance seams where possible.

#### What should remain deferred

- API-key issuance endpoints or admin CRUD APIs.
- API-key naming/labeling UX beyond minimal persisted metadata needed for tests and runtime
  identity.
- Rotation families, dual-active rollover, expiration policies, last-used tracking, partial-secret
  reveal flows, and bulk revocation tooling.
- Multi-key ownership models, workspace-scoped issuance consoles, and organization-wide credential
  inventory.
- Broad unification of service-account and API-key records into a generic credential platform unless
  the design proves that is directly necessary.
- New protected endpoints just to prove API keys.

### Risks

- The biggest technical trap is treating API keys like JWTs. They are not. API keys need lookup +
  secret verification, so reusing the current JWT-only path too aggressively could create a brittle
  or insecure design.
- If the team pulls issuance and management into the same change, scope will balloon fast and dilute
  the proving slice.
- Secret storage must be designed carefully; plaintext persistence or reversible storage would be an
  architectural regression.
- If principal modeling is over-generalized now, the change could turn into a generic credential
  ledger redesign instead of a focused API-key capability.
- If the API-key transport format is not made explicit in proposal/design, the implementation may
  create ambiguity with bearer JWT/service-account flows.

### Ready for Proposal

Yes — propose `backend-api-key-support` as a **narrow runtime API-key authentication change for the
existing workspace-access proving slice**, with secure persisted key lookup/verifier state,
principal materialization, revocation enforcement, and H2/PostgreSQL verification. Keep issuance,
lifecycle management, rotation, and broader credential inventory explicitly deferred.
