## Exploration: backend-credentials-expansion

### Current State

`server/smp` already executes one real backend proving slice end to end for **JWT-backed USER
principals**. The runtime path is:

- Spring Security resource server validates bearer JWTs in
  `identity/infrastructure/security/IdentitySecurityConfiguration.kt`.
- `SpringJwtValidatedTokenMapper` maps a Spring `Jwt` into repo-local `ValidatedToken`.
- `JwtAuthenticatedPrincipalMaterializer` turns that validated token into an
  `AuthenticatedPrincipal` with `PrincipalType.USER`, `CredentialType.JWT`,
  `authenticationMethod = "JWT_BEARER"`, and `issuedCredentialReference = token.tokenId`.
- Identity lookup is persisted only for principals plus user profile facts (`principals`,
  `user_identities`).
- Tenancy and authorization already work against persisted workspace membership, roles, permissions,
  and direct grants, with H2 and PostgreSQL verification.

For credentials breadth specifically, the platform model is ahead of the implementation:

- `openspec/specs/credentials/spec.md` already defines JWT, service accounts, and API keys as
  first-class credential concepts.
- `CredentialType` already includes `JWT`, `SERVICE_ACCOUNT`, and `API_KEY`.
- `PrincipalType` already includes `USER`, `SERVICE_ACCOUNT`, and `API_KEY` (plus other deferred
  actor types).
- The auth foundation design explicitly reserved credentials ownership for JWT/OIDC adapters, API
  key and service-account seams, and token/API-key revocation and rotation seams.

What is still seam-only or absent:

- No service-account principal materialization path exists.
- No API-key validation path exists.
- No credential inventory/persistence model exists beyond JWT request-time mapping and identity
  principal rows.
- No token/API-key revocation store or enforcement exists.
- No rotation semantics exist for long-lived credentials.
- No tests exercise `SERVICE_ACCOUNT` or `API_KEY` principal flows.
- No governance events exist yet for credential issuance/revocation/rotation; governance currently
  proves only authorization outcomes for the existing workspace-access slice.

So the repo is **not** at raw bootstrap anymore, but credentials expansion beyond JWT USER auth is
still mostly conceptual.

### Affected Areas

- `openspec/specs/credentials/spec.md` — source of truth for credential separation and deferred
  service-account / API-key concepts.
- `openspec/specs/identity/spec.md` — principal taxonomy already includes `SERVICE_ACCOUNT` and
  `API_KEY`, but runtime materialization only supports `USER`.
- `openspec/specs/platform/spec.md` — requires stateless deterministic evaluation and future-safe
  invalidation when credential validity changes.
- `openspec/specs/governance/spec.md` — already requires a seam for credential-use/security events,
  but only authorization proof is executable today.
- `openspec/changes/archive/2026-05-15-backend-auth-foundation/design.md` — explicitly reserves
  service-account/API-key and revocation/rotation seams in the Credentials bounded context.
- `openspec/changes/archive/2026-05-15-backend-auth-hardening/design.md` — confirms the previous
  slice intentionally stayed narrow and did not broaden credential capability.
- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/domain/CredentialType.kt` —
  canonical credential taxonomy exists.
- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/domain/ValidatedToken.kt` — current
  normalized token model is JWT-oriented and request-scoped, not credential-lifecycle-oriented.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/FederatedTokenValidator.kt` —
generic validation seam exists but is only used for JWT.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/security/SpringJwtValidatedTokenMapper.kt` —
current only credential adapter.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/JwtAuthenticatedPrincipalMaterializer.kt` —
hard-codes USER/JWT materialization behavior.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/PrincipalIdentityLookup.kt`
and `.../R2dbcPrincipalIdentityLookup.kt` — persisted principal lookup seam is usable for non-user
principal types, but current row shaping is user-biased because only `user_identities` exists.
- `server/smp/src/main/resources/db/changelog/identity/001-create-principals.yaml` — current
  principal table is broad enough to hold non-user actors.
- `server/smp/src/main/resources/db/changelog/identity/002-create-user-identities.yaml` — only
  user-specific detail table today.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` —
security chain is fully JWT resource-server based right now.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/JwtAuthenticatedPrincipalMaterializerTest.kt` —
current test proof only covers JWT USER mapping.

### Approaches

1. **Service-account bearer path first** — add persisted service-account principals and authenticate
   them through the existing bearer/JWT transport with a dedicated materialization path and
   revocation check.
    - Pros: Reuses most existing HTTP/security/runtime plumbing; adds a new real principal type with
      minimal transport churn; creates a natural place to introduce token revocation before API-key
      complexity.
    - Cons: Still does not add API-key capability; if done carelessly it can become "generic machine
      identity platform" work.
    - Effort: Medium

2. **API-key authentication first** — add API-key storage/lookup/validation and let API keys
   authenticate the current proving slice.
    - Pros: Adds a clearly different credential form instead of another bearer token variant;
      creates a direct use case for rotation/revocation.
    - Cons: Requires new transport handling, hashing/storage rules, header conventions, and probably
      a parallel authentication filter chain; larger blast radius than it looks.
    - Effort: Medium/High

3. **Credential control plane first (revocation/rotation model without new runtime principal type)
   ** — add persisted credential records and revocation semantics for JWT/token references now,
   leaving service-account/API-key execution for later.
    - Pros: Smallest purely infrastructural step; creates a foundation for future credentials.
    - Cons: Weak user value on its own; risks becoming seam-heavy again because current JWTs are
      externally validated and mostly stateless.
    - Effort: Medium

4. **Service accounts + API keys + full rotation/revocation together** — broad credentials platform
   wave.
    - Pros: Maximum breadth.
    - Cons: This is exactly the sprawl trap. It would force principal lifecycle decisions, secret
      storage rules, transport design, revocation semantics, admin workflows, and new verification
      surfaces all at once.
    - Effort: High

### Recommendation

Recommend **Approach 1: service-account bearer path first, with narrow revocation support for that
path**.

This is the smallest coherent next change because it adds **one new executable actor capability**
while staying close to the current architecture:

- It upgrades a deferred principal type (`SERVICE_ACCOUNT`) into a real authenticated path.
- It can reuse the existing bearer-token entrypoint, principal context propagation, workspace
  context, and authorization proving slice.
- It creates a meaningful place to introduce **credential revocation** as executable behavior,
  because a service-account token or token reference can be checked against authoritative backend
  state before access is granted.
- It avoids the wider transport and secret-management surface that API keys would force immediately.

#### What belongs in this change

- Persisted **service-account principal support** using the existing `principals` model, plus any
  minimal companion table needed for service-account metadata.
- A **service-account credential validation/materialization path** that can authenticate a bearer
  credential into `PrincipalType.SERVICE_ACCOUNT` and `CredentialType.SERVICE_ACCOUNT`.
- A **revocation check** for that credential path so an otherwise valid service-account credential
  can be blocked by backend state.
- End-to-end proof on an existing protected slice (preferably the current workspace-access slice)
  that:
    - a service account can authenticate,
    - workspace membership / authorization still governs access,
    - a revoked service-account credential is denied.
- Minimal audit-ready proof that credential use / revocation denial is observable through existing
  governance seams if needed by the scoped design.

#### What should remain deferred

- **API-key authentication**.
    - Reason: it introduces a distinct secret format, storage/hashing concerns, lookup strategy, and
      request parsing path. That is a different credential surface, not just more of the same
      change.
- **Rotation workflows / key families / dual-active rollover**.
    - Reason: rotation only becomes meaningful once a long-lived managed credential form exists. For
      service-account bearer support, revocation is the smaller real capability; full rotation
      semantics can follow after one non-user credential path is live.
- **General credential management APIs or admin UX**.
    - Reason: runtime capability should precede management breadth.
- **End-user JWT revocation for external IdP tokens**.
    - Reason: current USER JWT flow is resource-server based and externally validated; introducing
      first-class blacklist/session invalidation for all JWTs is a bigger product and infrastructure
      decision.
- **Generic multi-credential platform abstractions beyond what the service-account path actually
  needs**.
    - Reason: avoid rebuilding identity/credentials around theoretical future breadth.

### Risks

- Service-account support can sprawl into full machine-identity lifecycle management if creation,
  scoping, rotation, ownership, and administration are all pulled in at once.
- If revocation is defined too generically now, the team may over-design a universal credential
  ledger before one additional credential path is behaviorally proven.
- Reusing bearer transport for service accounts is the smallest move, but the exact token
  shape/issuer boundary must be clear; otherwise the implementation may blur USER JWT and
  service-account credential semantics.
- If service accounts are allowed into workspace authorization without explicit ownership/membership
  rules, identity breadth could accidentally bypass tenancy discipline.
- Deferring API keys is correct for scope control, but it means the proposal must be explicit that "
  credentials expansion" here does not mean every credential form lands now.

### Ready for Proposal

Yes — propose `backend-credentials-expansion` as a **narrow credentials breadth change focused on
executable SERVICE_ACCOUNT authentication plus authoritative credential revocation on the existing
protected backend slice**. Keep API-key transport, full rotation workflows, end-user JWT revocation,
and broad credential-management surfaces explicitly deferred.
