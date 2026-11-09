# Design: Keycloak Human Authentication

## Technical Approach

Use Spring Security WebFlux `oauth2Login` as the browser BFF for two confidential Keycloak clients. The dashboard uses `pt-app`; the admin SPA uses `pt-admin`. Keycloak owns human credential authentication and lifecycle after hard cutover. The BFF stores provider tokens server-side and creates a Profile Tailors relying-party session only after OIDC validation and successful resolution of a manually provisioned local principal.

Use the fixed issuer `https://keycloak.ahome.quest/realms/pt` with Keycloak `26.3.5`. Register the exact callbacks `https://api.profiletailors.com/login/oauth2/code/pt-app` and `https://api.profiletailors.com/login/oauth2/code/pt-admin`. Deployment of the realm, clients, and callback route is not asserted by this design. DNS resolution for `api.profiletailors.com` failed from the user's environment; DNS/proxy routing to these callbacks remains an operational gate.

The `pt-admin` flow requires LoA 2. Configure `Minimum ACR Value = 2` and the dedicated `browser-admin-mfa` browser flow so that only successful password plus OTP authentication yields `acr=2`. The BFF must independently validate issuer, non-empty subject, audience/client identity `pt-admin`, and `acr=2` before creating or accepting admin authentication state. Admin must force step-up despite missing `acr_values`, a request for level 1, or an existing `pt-app` SSO session. The behavior must issue one OTP challenge only. A `pt-app` session alone must never satisfy `/api/admin/**`. The app client has no minimum ACR requirement.

Use an in-memory Spring session store for beta with one application replica. Set 30-minute idle and 8-hour absolute expiry. The host-only cookie is `Secure`, `HttpOnly`, `SameSite=Lax`, and scoped to `/`; verify the final cookie name against Spring configuration before implementation. Obtain CSRF through `GET /api/auth/csrf` and send it in `X-CSRF-TOKEN` on every unsafe cookie-authenticated request, including logout. Apply session and CSRF policy to both browser clients. Critical mutation endpoints additionally require recent authentication (`auth_time` no older than 300 seconds) and MFA; this is step-up authorization independent of admin's baseline LoA 2.

Retain local ownership of principals, account/product state, workspace/resource authorization, platform roles, and audit. Link identities only by a unique validated `(issuer, sub)` association; email MUST NOT be used to link or merge accounts. After that identity link resolves a local principal, synchronize its local email only from validated Keycloak state that marks the email verified and only when the email conflicts with neither another local principal nor another local email. If Keycloak does not mark the email verified or a local conflict exists, do not synchronize it and fail closed according to local eligibility. Provision local principals manually; human signup remains invite-only. Keep local MCP API-key authentication and MCP `workspace-scoped-oauth` on their existing independent paths. Hard cutover retires local human password login, local credential lifecycle, and local refresh-token authentication; it does not create a local-password rollback path.

The current backend uses a reactive security configuration combining resource-server JWT behavior, API-key filtering, and disabled CSRF, while local auth endpoints issue local tokens. Implement the OIDC/BFF integration and Spring Security chains in `identity.infrastructure`; keep domain and application interfaces framework-free. At cutover, retire or migrate every non-MCP bearer/API-key/service-account route and consumer. ADR-0035 is ratified and governs this architecture; the ADR-0009 supersession record and related architecture-document reconciliation are governance follow-up, not an unresolved approval gate for this design. Delivery uses a feature-branch chain of independently reviewable slices.

Security-chain selection must make authentication mode explicit before authorization. Match MCP API-key and workspace-scoped OAuth endpoints to their existing credential-specific chains, independent of the browser-session chain. No non-MCP bearer, API-key, or service-account route may survive cutover. Inventory every existing non-MCP machine consumer and route as implementation/remediation work, then retire each route or migrate its consumer to an approved human or MCP contract before cutover; migration must not retain that route's non-MCP machine authentication. Do not create a non-MCP machine allowlist or global bearer/API-key fallback. Select the browser BFF chain only for browser-facing human resources and the exact OIDC login initiation and callback routes for `pt-app` and `pt-admin`. Only those OIDC browser navigations may redirect to Keycloak. Protected JSON/API requests without accepted authentication return HTTP `401`, never an HTML login response or Keycloak redirect. Reject a request that presents both a BFF session cookie and bearer/API-key credential as ambiguous before either credential can authenticate it. A BFF session never substitutes for MCP authentication. Test chain order and matcher overlap to prove removal of every non-MCP machine credential acceptance without weakening MCP authentication.

## Architecture Decisions

### Decision: Use two confidential OIDC clients and fixed callbacks

**Choice**: Configure `pt-app` and `pt-admin` against issuer `https://keycloak.ahome.quest/realms/pt`, Keycloak `26.3.5`, with callbacks `https://api.profiletailors.com/login/oauth2/code/pt-app` and `https://api.profiletailors.com/login/oauth2/code/pt-admin`.

**Alternatives considered**: One shared client; SPA-managed bearer tokens; custom OIDC protocol handling.

**Rationale**: Separate clients give the dashboard and administrative surfaces distinct OIDC identities and allow the admin client to enforce stronger authentication. Spring Security's authorization-code handling keeps provider tokens out of browser JavaScript and avoids a custom protocol implementation.

### Decision: Enforce administrator LoA 2 in Keycloak and the BFF

**Choice**: Set `Minimum ACR Value = 2` on `pt-admin`, use `browser-admin-mfa`, and issue `acr=2` only after password and OTP. Require the BFF to validate issuer, subject, audience, `azp`/client identity as applicable to the token contract, and `acr=2` before accepting admin authentication. Apply the same requirement if ACR is omitted or requested at level 1, including an existing `pt-app` SSO session. Configure the flow to request OTP once. Do not set a minimum ACR on `pt-app`.

**Alternatives considered**: Rely only on client-side route guards; trust the requested `acr_values`; permit an app session to carry over to admin; rely only on Keycloak's client setting without BFF checks.

**Rationale**: Admin authorization is a server-side boundary. A lower or absent request value and an existing SSO session must not downgrade the admin authentication requirement. Independent BFF validation prevents an incorrectly configured or mismatched token from creating admin state. The specific Keycloak flow behavior and one-time OTP behavior require acceptance testing; they are not verified by this design.

### Decision: Preserve local principal and authorization ownership

**Choice**: Resolve validated `(issuer, sub)` to one manually provisioned local principal. Do not use email to link or merge accounts. After linking, synchronize the local email only from validated Keycloak state that marks it verified and only if it conflicts with no other local principal or local email; otherwise do not synchronize and fail closed according to local eligibility. Keep local account/product status, membership, resource policy, platform permissions, and audit authoritative. Reject missing, ambiguous, disabled, or otherwise disallowed mappings without mutation.

**Alternatives considered**: Use Keycloak subject as product identity; map Keycloak roles directly to local access; link identities by email; auto-provision on signup.

**Rationale**: Local product state and permissions remain Profile Tailors contracts. Email equality is not a safe identity-linking key. Requiring verified Keycloak state and a conflict-free local email prevents unverified or conflicting provider data from changing local identity data. Public or automatic provisioning would conflict with invite-only signup and manual principal provisioning.

### Decision: Use an in-memory beta session with explicit limits

**Choice**: Store relying-party session state in memory for beta and operate one application replica. Enforce 30-minute idle and 8-hour absolute TTL. Use a `Secure`, `HttpOnly`, `SameSite=Lax`, host-only cookie scoped to `/`. Verify the precise Spring cookie name before implementation.

**Alternatives considered**: Stateless browser JWTs; shared/distributed session persistence for beta; provider tokens in browser storage.

**Rationale**: The approved beta scope is one replica and in-memory session state. Explicit TTL and cookie attributes define the accepted browser boundary. A single replica is an availability/scaling constraint, not a production multi-replica design.

### Decision: Use an explicit CSRF token endpoint and header

**Choice**: Expose `GET /api/auth/csrf`; require the returned token in `X-CSRF-TOKEN` on every unsafe cookie-authenticated request. Keep the authentication cookie HttpOnly. Apply CSRF validation to unsafe methods, including logout.

**Alternatives considered**: Disable CSRF; rely only on Origin/Referer checks; expose the authentication cookie to JavaScript.

**Rationale**: Browsers attach session cookies automatically. An explicit token/header contract protects cookie-authenticated mutations without exposing the session credential. Origin checks may be defense in depth but do not replace CSRF tokens.

### Decision: Require fresh MFA for critical mutations

**Choice**: Critical mutation endpoints require `auth_time` no older than 300 seconds and evidence of MFA. Admin baseline access separately requires `acr=2`.

**Alternatives considered**: Apply no step-up; use session age as a substitute for authentication freshness; treat admin's ACR requirement as sufficient for all critical mutations.

**Rationale**: Sensitive mutations need recent authentication, including in the app client where no minimum ACR is configured. The policy separates freshness and MFA evidence from the admin role boundary.

### Decision: Hard-cut over local human credentials

**Choice**: At cutover, invalidate or reject legacy local refresh sessions and tokens, disable local human password login and local credential signup/verification/recovery, and ensure `POST /api/auth/refresh` cannot mint local credentials from a legacy token or BFF session. Keep human signup invite-only and local principal provisioning manual. Rollback must not restore local human-password authentication.

**Alternatives considered**: Indefinite dual authentication; automatic email linking; local-password fallback after cutover.

**Rationale**: Keycloak is the sole human authentication authority after cutover. Removing the local credential path prevents a second authentication authority from remaining available. Manual provisioning and invitation checks preserve local authorization ownership.

### Decision: Retire non-MCP machine authentication at cutover

**Choice**: Keep local MCP API-key authentication and the existing independent MCP `workspace-scoped-oauth` contract unchanged. Retire all non-MCP bearer, API-key, and service-account authentication routes at cutover. Inventory the current consumers/routes and identify a retirement or migration action for each before cutover. A migrated consumer must not retain a non-MCP machine-authentication route. A BFF session cannot satisfy MCP authentication.

**Alternatives considered**: Route machine authentication through the human BFF; combine all credentials in one permissive security chain; preserve non-MCP machine routes through an allowlist or global credential fallback.

**Rationale**: MCP credentials have separate audience, workspace, scope, and lifecycle contracts and remain isolated from human sessions. Removing all other machine credential paths avoids retaining a second non-human API surface through an allowlist. Inventory is required to make retirement or migration complete and safe; it is not a policy choice about preserving routes.

### Decision: Route browser redirects and API failures through distinct security matchers

**Choice**: Match MCP routes to their existing credential-specific security chains before the browser-session chain. Match browser-facing human resources to the BFF/session chain, and limit OIDC redirects to the registered login initiation routes and exact callbacks for `pt-app` and `pt-admin`. Remove all non-MCP bearer/API-key/service-account authentication matchers and credential acceptance at cutover. Use a non-redirecting `401` entry point for unauthenticated JSON/API requests. Reject requests that carry both a BFF session and bearer/API-key credential as ambiguous before either credential can authenticate them. Never allow the BFF session to satisfy MCP authentication.

**Alternatives considered**: One catch-all chain with a redirecting entry point; selecting authentication based on whichever credential filter succeeds first; allowing the session to supplement or replace MCP credentials; retaining any non-MCP machine credential matcher.

**Rationale**: Separate matchers and entry points preserve browser login navigation while ensuring APIs receive protocol-appropriate errors. Explicit ambiguity rejection prevents identity confusion when clients send credentials from multiple authentication domains. Priority matching and overlap tests preserve MCP boundaries while proving that no non-MCP machine credential path remains.

### Decision: Keep the framework boundary in identity infrastructure

**Choice**: Place Spring Security, WebFlux OIDC/BFF, callback validation, session, cookie, and security-chain configuration in `identity.infrastructure`. Keep domain and application interfaces framework-free and expose only contracts required for local-principal resolution and authorization. ADR-0035 is ratified and governs the change.

**Alternatives considered**: Put Spring Security types or adapters in domain/application; retain the former local human bearer-token model alongside the BFF after cutover.

**Rationale**: The infrastructure layer owns framework integration under the existing hexagonal architecture. Framework-free contracts preserve domain/application portability, while ratified ADR-0035 authorizes the human-authentication architecture.

## Data Flow

### Dashboard login

```text
Dashboard SPA
  ── start login ──→ Spring oauth2Login for pt-app
  ── authorization code ──→ Keycloak realm pt
  ←─ callback https://api.profiletailors.com/login/oauth2/code/pt-app
Spring validates OIDC response/state
  ── validated (issuer, sub) ──→ local principal resolution
  ── unique, manually provisioned principal ──→ evaluate validated email state
  ── verified email with no local conflict ──→ synchronize local email
  ── eligible principal ──→ in-memory relying-party session
  ←─ Secure, HttpOnly, SameSite=Lax host-only cookie ── browser
```

The `pt-app` client does not require a minimum ACR. The BFF still validates issuer and subject and resolves the local principal before creating a session. After resolution by `(issuer, sub)`, it synchronizes the local email only when validated Keycloak state marks it verified and no other local principal or local email conflicts. An unverified email or local conflict prevents synchronization and fails closed according to local eligibility. Email is never used for linking or merging. No provider token is returned to browser JavaScript.

### Admin login and step-up

```text
Admin SPA ── start login ──→ Spring oauth2Login for pt-admin
  ── authorization request ──→ Keycloak browser-admin-mfa
Keycloak requires password + OTP, including with existing pt-app SSO
  ── successful MFA only ──→ admin token/authentication state with acr=2
  ── callback https://api.profiletailors.com/login/oauth2/code/pt-admin
BFF validates issuer + subject + audience/client identity + acr=2
  ── local principal + local admin permission check ──→ admin session/access
```

Keycloak must force the password-plus-OTP step even when the request omits `acr_values` or requests level 1. The admin login acceptance tests must prove the one-OTP behavior and the existing-dashboard-SSO case. An existing `pt-app` session alone is insufficient for direct `/api/admin/**` access.

### Browser request, CSRF, and critical mutation

```text
SPA ── GET /api/auth/csrf ──→ BFF returns CSRF token
SPA ── session cookie + X-CSRF-TOKEN on unsafe request ──→ BFF
BFF validates session and CSRF
  ── local principal/account/membership/permission checks ──→ application operation
Critical mutation additionally checks auth_time ≤ 300 seconds and MFA
```

An absent or expired session is unauthenticated. Logout invalidates the relying-party session and follows the approved Keycloak logout policy. Session TTL is 30 minutes idle and 8 hours absolute. The browser cookie is host-only, scoped to `/`, `Secure`, `HttpOnly`, and `SameSite=Lax`.

### Machine authentication

```text
MCP API-key request ──→ existing local API-key authentication
MCP workspace OAuth ──→ existing separate authorization-code/PKCE contract
Human browser request ──→ OIDC BFF session + local authorization
Non-MCP machine consumer ──→ inventory → retire route or migrate consumer before cutover
Unauthenticated JSON/API request ──→ 401; no OIDC redirect
```

The pre-cutover inventory is a migration/remediation task, not a route-allowlist design. Record each non-MCP machine consumer, route/method, credential type, owner, and disposition. Retire the route or migrate the consumer to an approved human or MCP contract before cutover; do not retain non-MCP bearer, API-key, or service-account acceptance. MCP API-key authentication and MCP `workspace-scoped-oauth` remain separate and unchanged. The inventory is pending and is not claimed complete. A BFF session never substitutes for MCP credentials.

### Security-chain and matcher selection

```text
Request
  ├─ MCP route ──→ existing MCP credential-specific chain, unchanged
  ├─ BFF session + bearer/API-key presented ──→ reject as ambiguous
  ├─ registered OIDC login-initiation or exact callback route ──→ OIDC flow may redirect
  ├─ browser-facing human resource ──→ BFF session chain + local authorization
  └─ protected JSON/API route without accepted auth ──→ 401, no HTML or Keycloak redirect
```

The inventory must precede cutover and result in an evidenced retirement or migration action for every existing non-MCP machine consumer and route. Do not derive an allowlist. Configure explicit request matchers and chain order so MCP routes reach their unchanged credential-specific paths, while no non-MCP bearer/API-key/service-account matcher or global credential fallthrough can authenticate a request. Browser-resource matchers must not capture API paths. Only OIDC login initiation and exact callback browser navigations may redirect to Keycloak. A callback failure must not create a session. Reject mixed BFF-session plus bearer/API-key requests before either credential is accepted. Unauthenticated JSON/API requests return `401`.

### Signup, verification, and recovery

After cutover, registration entry is invitation-gated and delegates credential creation to Keycloak. Profile Tailors does not create a local principal from signup; an administrator must provision the local principal, and callback resolution must find its approved `(issuer, sub)` link. Email verification and password recovery remain within Keycloak. The app must not submit legacy local verification or password-reset tokens, create a relying-party session from such tokens, or expose whether an account exists. Login failures use a generic safe error; network failures remain retryable. These UI and endpoint behaviors do not enable public signup or local human credentials.

## File Changes

These are planned implementation targets, not files changed in this design phase. Final scope must follow the reconciled specs and code review.

| File or area | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` | Modify | Separate browser OIDC/session security from MCP credential-specific security; order explicit chains so MCP remains unchanged, browser resources use BFF sessions, and JSON/API failures return 401. Remove all non-MCP bearer/API-key/service-account acceptance and require CSRF on unsafe session requests. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security` | Modify | Define ordered matchers for MCP routes, browser-session resources, OIDC initiation/callback routes, and non-redirecting JSON/API failures; reject combined BFF-session and bearer/API-key credentials. Explicitly ensure non-MCP machine credential routes do not survive cutover. |
| Existing non-MCP machine consumers and route/security inventory sources | Inspect before implementation; then retire or migrate | Inventory each consumer, exact route/method, credential contract, owner, and disposition. Complete retirement or migration before cutover; do not create an allowlist or retain non-MCP machine authentication. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/LocalJwtConfiguration.kt` and local-auth wiring | Modify/retire by cutover scope | Retain only components still required by non-human contracts; prevent local human login/refresh after hard cutover. Audit consumers before removal. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/{domain,application,infrastructure}` | Add/modify | Resolve the validated issuer/subject to a unique existing local principal; after linking, synchronize email only from validated verified Keycloak state when no local principal/email conflict exists; preserve manual provisioning, no-email-linking, and fail-closed behavior. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt` | Modify | Retire local human login, registration, verification, recovery, and refresh credential behavior after cutover. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminSessionController.kt` and `/api/admin/**` authorization | Modify/review | Require local platform permission plus validated `pt-admin` authentication state with `acr=2`; reject app-only sessions. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security` | Add | Configure callback registration, admin authentication validation, session policy, CSRF token endpoint/header contract, and freshness/MFA checks at critical mutation boundaries. |
| `apps/web/app/src/modules/auth/**`, `apps/web/app/src/router/index.ts` | Modify | Start `pt-app` BFF login; hydrate and end session; obtain CSRF token and attach `X-CSRF-TOKEN` to unsafe requests; use safe return paths. |
| `apps/web/admin/src/stores/auth.store.ts`, `apps/web/admin/src/router/index.ts` | Modify | Start the separate `pt-admin` flow and use BFF session state; do not treat dashboard login as admin authentication. |
| `server/smp/src/main/resources/application*.yaml`, environment templates, deployment and auth runbooks | Modify after operational gates | Configure issuer, confidential client secrets, exact redirect URIs, session TTL, cookie policy, DNS/proxy routing, secret rotation, and readiness. Do not edit Keycloak configuration as part of this design task. |
| `server/smp/src/test/**`, `apps/web/{app,admin}/**` test areas | Add/modify | Add backend unit/security/integration/BDD and frontend tests described below. All acceptance cases remain unverified until run. |
| `.agents/sdd/changes/keycloak-human-authentication/specs/{human-authentication,login-experience,registration,password-recovery-ui,email-verification,email-verification-ui}/spec.md` | Delta contracts | Define BFF identity/session and authorization boundaries, invite-only/manual provisioning, and Keycloak-owned login, signup, verification, and recovery after cutover. |
| `.agents/sdd/specs/workspace-scoped-oauth/spec.md` and MCP API-key contracts | No change | Keep machine authentication contracts independent and unchanged. |
| `docs/architecture/adr/0035-keycloak-human-authentication-bff.md` | Ratified architecture | Governs the change. ADR-0009 supersession metadata and architecture-document reconciliation are governance follow-up; they are not runtime validation gates. |

## Interfaces / Contracts

| Contract | Requirement |
|---|---|
| Keycloak issuer | `https://keycloak.ahome.quest/realms/pt`; realm deployment/readiness still requires validation. |
| Keycloak version | `26.3.5`; installed realm and flow behavior requires acceptance validation. |
| Dashboard OIDC client | Confidential client `pt-app`; no minimum ACR requirement. Callback: `https://api.profiletailors.com/login/oauth2/code/pt-app`. |
| Admin OIDC client | Confidential client `pt-admin`; `Minimum ACR Value = 2`; dedicated `browser-admin-mfa` flow. Callback: `https://api.profiletailors.com/login/oauth2/code/pt-admin`. |
| Admin authentication evidence | BFF validates issuer, non-empty subject, expected audience/client identity, and exact `acr=2`; reject any mismatch before admin session/access. The client identity claim to validate must match the configured token contract. |
| Admin step-up | Password plus OTP required for LoA 2 even when `acr_values` is absent or requests level 1, and with pre-existing `pt-app` SSO. OTP is presented once per admin login. |
| External identity and email | Resolve/link only by validated `(issuer, sub)`; never link or merge by email. After linking, synchronize local email only from validated Keycloak state marked verified and only without conflict with another local principal or local email. Otherwise do not synchronize and fail closed according to local eligibility. |
| Authorization | Local principal, product/account state, workspace/resource policy, and platform permissions remain authoritative. `/api/admin/**` rejects `pt-app`-only authentication. |
| Session | In-memory beta, one replica; idle TTL 30 minutes; absolute TTL 8 hours; host-only `/` cookie with `Secure`, `HttpOnly`, `SameSite=Lax`. Verify exact cookie name against Spring before implementation. |
| CSRF | `GET /api/auth/csrf`; unsafe requests send `X-CSRF-TOKEN`; validate all cookie-authenticated unsafe methods, including logout. |
| Critical mutation step-up | `auth_time` age at most 300 seconds plus MFA evidence. |
| Human lifecycle | Keycloak owns login, invite-only signup/credential creation, verification, recovery, and logout after hard cutover. Local human login/lifecycle/refresh credentials are not available after cutover. |
| Signup and lifecycle UI | Registration is invitation-gated and Keycloak-owned; principal creation remains manual. Legacy verification and recovery entry points do not submit local tokens after cutover and use safe, non-enumerating outcomes. |
| Machine credentials | Local MCP API-key authentication and MCP `workspace-scoped-oauth` remain separate and unchanged. |
| Non-MCP machine routes | No non-MCP bearer/API-key/service-account route survives cutover. Inventory consumers and routes, then retire or migrate each before cutover. Migration does not preserve non-MCP machine authentication. |
| Request routing and failure behavior | Only registered OIDC login-initiation and callback browser navigations may redirect to Keycloak. Unauthenticated protected JSON/API requests return HTTP `401` without HTML or redirect. A request with both a BFF session and bearer/API-key credential is rejected as ambiguous. The BFF session never substitutes for MCP authentication. |

## Testing Strategy

All checks below are planned. No test has been run or passed as part of this design update.

| Layer | What to test | Approach |
|---|---|---|
| Identity resolution and email sync | Stable unique `(issuer, sub)` mapping; no email-based linking; manual provisioning; synchronize email only after identity linking from validated verified Keycloak state and only without conflict with another local principal or local email; unverified/conflicting email is not synchronized and fails closed according to local eligibility. | Unit tests for domain/application behavior with fakes; PostgreSQL integration tests for uniqueness and transaction semantics. These behaviors are planned and have not been tested. |
| OIDC and session | Both client registrations use exact issuer/client/callback values; valid callback creates session only after local mapping; bad state, issuer, subject, client, or local mapping creates no session. | Reactive Spring Security tests and `WebTestClient`; use test OIDC registrations/provider fixtures, never production credentials. |
| Admin ACR / MFA | `pt-admin` requires password and OTP; password-only does not issue `acr=2`; omitted `acr_values`, requested level 1, and existing `pt-app` SSO still force OTP; exactly one OTP challenge; wrong issuer/subject/audience/client/ACR denies; `pt-app` does not require minimum ACR. | Keycloak `26.3.5` acceptance tests against a disposable/test realm and BFF integration tests. OTP behavior and the app-session-to-admin step-up remain pending and unverified. |
| Admin API isolation | A `pt-app` session cannot access `/api/admin/**`; only validated `pt-admin` state with `acr=2` plus local platform permission grants access. | Security-chain and authorization tests with direct `/api/admin/**` requests, not only SPA route tests. App-session-to-admin and direct API coverage remain pending and unverified. |
| Non-MCP machine-consumer inventory and remediation | Every existing non-MCP bearer/API-key/service-account consumer and route has an identified retirement or migration action before cutover. No allowlist is created and no migrated consumer retains a non-MCP machine-authentication route. | Review route/security configuration and consumer integrations; map each consumer to exact routes, methods, credentials, owner, and retirement/migration disposition. Verify completion with owners before cutover. Inventory and remediation are pending; no completion is claimed. |
| Security matcher and chain selection | MCP routes select their unchanged credential-specific security behavior; no non-MCP machine credential is accepted after cutover; only registered OIDC initiation and exact callback routes can redirect; unauthenticated JSON/API requests return 401 without HTML; mixed session plus bearer/API-key credentials are rejected; BFF session alone cannot authenticate MCP. | Reactive Spring Security/WebTestClient tests assert chain selection, response status, `Location`, and content type. Cover matcher overlap/order, MCP API-key and workspace OAuth regressions, non-MCP routes with bearer/API-key/service-account credentials, combined credentials, session-only MCP requests, exact OIDC routes, and unauthenticated JSON/API requests. These are planned tests and have not been run. |
| CSRF/session | CSRF endpoint returns token; unsafe methods including logout reject missing/invalid token and accept valid token; idle and absolute expiry are enforced; cookie attributes and host/path scope are correct. | Reactive `WebTestClient` tests and browser tests. Verify actual cookie name from Spring configuration before implementation. |
| Critical mutations | Old `auth_time` or absent MFA evidence denies critical mutation; fresh authentication with MFA and local authorization permits it. | Focused application/security tests for each critical mutation boundary. |
| Human lifecycle/cutover | Local login/register/verify/recovery/refresh cannot authenticate, mutate credentials, or mint local access/refresh credentials after cutover; BFF session is never exchanged for local JWTs; invitations and manual provisioning remain enforced. | Backend integration tests and Cucumber scenarios for observable endpoints; frontend tests for lifecycle entry points and safe generic failures. |
| Verification and recovery UI | Keycloak verification links stay in the Keycloak lifecycle; legacy local verification and reset tokens are not submitted or treated as authentication; recovery does not disclose account existence. | Frontend unit and browser tests for post-cutover routes, generic errors, and retryable network failures. |
| MCP regression | Local API keys continue to work; BFF session cannot satisfy API-key or workspace OAuth; existing OAuth audience, workspace, and scope behavior remains. | Existing MCP suites plus focused security matcher regression tests. |
| Frontend app/admin | Separate login starts correct client; session hydration/logout; CSRF token/header; safe return path; admin step-up and denial states. | Vitest and Playwright critical flows for both surfaces. |
| Operational readiness | Issuer discovery and realm/client configuration; exact callback registration; DNS/proxy delivery to `api.profiletailors.com`; TLS and callback reachability. | Environment-specific readiness checks. DNS/proxy is currently pending and remains an open operational gate; do not report it as passed. |

The backend behavior requires `just backend-check` and `just backend-bdd-fast`; persistence changes also require PostgreSQL integration tests after infrastructure startup. Frontend changes require app/admin type-check, lint, unit tests, builds, and critical Playwright flows. None of these commands is represented as passing here.

## Migration / Rollout

Use a hard cutover. Do not retain a human local-password transition window. Before routing human login to Keycloak:

1. Use the feature-branch chain to deliver independently reviewable implementation slices.
2. Validate the Keycloak `26.3.5` realm, issuer, confidential clients, admin flow, ACR behavior, and token claims in a test environment.
3. Resolve and verify DNS/proxy routing for `api.profiletailors.com` and both exact callbacks. The reported DNS failure keeps this gate open.
4. Verify Spring's exact session cookie name and the configured cookie attributes, TTL, CSRF endpoint, and header behavior.
5. Manually provision/link local principals by validated `(issuer, sub)` under the approved identity-linking procedure. After linking, synchronize email only from validated Keycloak state marked verified and only when there is no conflict with another local principal or local email; otherwise do not synchronize and fail closed according to local eligibility. Verify local account, workspace, platform permissions, and audit continuity. Do not use email equality as a link or merge key.
6. Route `pt-app` and `pt-admin` separately. Acceptance-test admin password-plus-OTP, including missing/lower `acr_values` and existing app SSO. Verify OTP is requested once.
7. Inventory existing non-MCP bearer/API-key/service-account consumers and routes. Record each consumer, route/method scope, credential contract, owner, and retirement or migration action. Complete that retirement or migration before cutover; do not derive an allowlist or retain a non-MCP machine-authentication route.
8. Configure security chains and matchers so MCP API-key and workspace-scoped OAuth paths remain unchanged and isolated, while all non-MCP bearer/API-key/service-account acceptance is removed. Verify mixed BFF-session plus bearer/API-key requests are rejected, BFF sessions cannot satisfy MCP auth, and unauthenticated JSON/API requests return 401 without redirect.
9. Invalidate or reject legacy local refresh sessions/tokens, disable local human credential endpoints, and confirm rollback cannot restore local human-password authentication.
10. Keep MCP API-key and workspace OAuth paths unchanged and verify both independently.
11. During Keycloak outage, fail closed for new human authentication and do not enable local-password fallback. Treat outage handling as pending acceptance evidence until tested.

The beta session store is in-memory and requires one application replica. Multi-replica deployment or durable session storage is outside this approved beta choice and requires a later architecture decision. Ratified architecture does not establish runtime readiness: no production cutover is authorized until realm/client/OTP, callback DNS/proxy, cookie/session, and acceptance gates are validated, and non-MCP machine consumer remediation is complete.

## Open Questions

- [ ] Inventory every existing non-MCP bearer/API-key/service-account consumer and route; identify and complete retirement or migration actions before cutover. This implementation/remediation work remains pending and is not claimed complete.
- [ ] Validate the Keycloak `26.3.5` realm, issuer discovery, confidential clients, `Minimum ACR Value = 2`, `browser-admin-mfa`, emitted client/ACR claims, missing/lower `acr_values` behavior, existing SSO step-up, and one-time OTP behavior in a test realm. OTP and app-session-to-admin acceptance tests remain pending.
- [ ] Resolve and validate DNS/proxy routing from the internet-facing callback host to `https://api.profiletailors.com/login/oauth2/code/pt-app` and `/pt-admin`. DNS did not resolve from the user's environment; this remains a release gate.
- [ ] Confirm the exact Spring session cookie name and verify cookie attributes and scope in the deployed Spring configuration.

Architecture is ratified by ADR-0035. Other approved choices include issuer and Keycloak version, separate confidential clients and exact callbacks, admin LoA 2 behavior, one-replica in-memory beta sessions and TTLs, cookie attributes, CSRF endpoint/header, critical-mutation freshness/MFA policy, invite-only/manual provisioning, local authorization ownership, email synchronization only after `(issuer, sub)` linking from validated verified Keycloak state without local conflict, hard cutover, and feature-branch-chain delivery. Runtime operations remain unverified: realm/client/OTP behavior, DNS/proxy routing, exact Spring cookie behavior, and associated acceptance tests remain pending. Non-MCP machine-consumer inventory and retirement/migration are required before cutover.

## References

- `.agents/sdd/changes/keycloak-human-authentication/proposal.md`
- `.agents/sdd/changes/keycloak-human-authentication/specs/human-authentication/spec.md`
- `.agents/sdd/changes/keycloak-human-authentication/specs/login-experience/spec.md`
- `.agents/sdd/changes/keycloak-human-authentication/specs/registration/spec.md`
- `.agents/sdd/changes/keycloak-human-authentication/specs/password-recovery-ui/spec.md`
- `.agents/sdd/changes/keycloak-human-authentication/specs/email-verification/spec.md`
- `.agents/sdd/changes/keycloak-human-authentication/specs/email-verification-ui/spec.md`
- `docs/architecture/adr/0035-keycloak-human-authentication-bff.md` (ratified architecture decision)
- `docs/architecture/adr/0009-jwt-and-httponly-cookie-authentication.md` (supersession metadata follow-up)
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/LocalJwtConfiguration.kt`
