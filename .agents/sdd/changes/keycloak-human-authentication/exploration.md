## Exploration: Keycloak human authentication migration

### Current State

The dashboard and admin use the same local email/password API (`POST /api/auth/login`), access JWTs held in memory, and an HttpOnly `pt_refresh` cookie for session restoration and renewal. The dashboard hydrates via `/api/auth/refresh`; admin separately hydrates and then calls `/api/admin/session` to resolve platform roles. Server authorization is based on the materialized local principal plus database-backed workspace/platform permission checks; Keycloak is not currently the source of human authentication.

The WebFlux security chain currently accepts bearer JWTs through the resource-server mechanism and API keys through a custom filter. CSRF is disabled globally; refresh/logout requests that contain `pt_refresh` are protected by an explicit trusted-Origin/Referer filter. CORS supports configured app and admin origins, credentials, and API headers. Existing identity lifecycle includes local registration, password credentials, email verification, password reset, principal status, invitation-based registration, and transactional workspace provisioning.

ADR-0009 requires short-lived bearer access JWTs plus refresh-token cookies and memory-only frontend token storage. The login-experience spec explicitly excludes SSO and protocol changes, so human Keycloak login conflicts with that existing contract and requires a deliberate replacement or amendment. `workspace-scoped-oauth` is specifically MCP authorization-code/PKCE behavior, not human login, and should remain a separate contract. Product context keeps dashboard and admin as separate SPAs and requires admin platform permissions in addition to authentication.

The CVIX clone contains Keycloak client/resource-server configuration (OIDC issuer, client id/secret, scopes, JWT audiences) and SPA filter exclusions for `/login` and `/oauth2`; inspection found no identity-infrastructure OAuth2 implementation files in the clone's `server/engine/src`. It is a configuration/routing reference only, not a contract for Profile Tailors.

### Affected Areas

- `.agents/sdd/specs/login-experience/spec.md` — currently prohibits SSO and protocol changes.
- `.agents/sdd/specs/registration/spec.md` — registration creates a local principal/workspace and establishes a JWT/refresh-cookie session; compatibility and account linking need definition.
- `.agents/sdd/specs/workspace-scoped-oauth/spec.md` — a distinct MCP OAuth contract; avoid conflating it with human login.
- `docs/architecture/adr/0009-jwt-and-httponly-cookie-authentication.md` — current token/cookie decision will need reconciliation if browser sessions replace refresh tokens.
- `server/smp/.../identity/infrastructure/security/IdentitySecurityConfiguration.kt` and `LocalJwtConfiguration.kt` — resource-server JWT, API-key filters, authorization rules, disabled CSRF, and local token beans.
- `server/smp/.../identity/infrastructure/http/LocalAuthController.kt` and identity application/gateways — local login/register/verification/recovery and token issuance.
- `server/smp/.../identity/infrastructure/security/RefreshSessionOriginValidationWebFilter.kt` — current cookie-CSRF origin protection is scoped to refresh/logout, not a full session-auth CSRF policy.
- `server/smp/.../platformadmin/infrastructure/http/AdminSessionController.kt` and platform-admin permission resolution — authentication must continue to resolve a local principal and must not grant admin access from IdP claims alone.
- `apps/web/app/src/modules/auth/**`, `apps/web/app/src/router/index.ts`, and `apps/web/app/e2e/specs/*login*` / `session-hydration.spec.ts` — password form, memory token store, refresh, protected route, callback handling, and related test expectations.
- `apps/web/admin/src/stores/auth.store.ts`, `apps/web/admin/src/router/index.ts`, and admin login/session tests — separate auth store and role-based route protection.
- `server/smp/src/main/resources/application.yaml`, `application-dev.yaml`, `.env.example`, compose/deployment files, `docs/production-secrets.md`, and auth/release runbooks — issuer/client settings, redirect origins, cookie/session settings, and secret provisioning.

### Approaches

1. **Spring Security `oauth2Login` browser BFF (selected direction)** — use the server-side OAuth2 client flow, maintain the authenticated browser session at the backend, and expose session-oriented endpoints for app/admin. Backend maps the validated OIDC identity to a local principal before existing authorization and resource operations.
   - Pros: matches the requested browser BFF and keeps provider tokens out of browser JavaScript; centralizes callback/state handling in Spring Security.
   - Cons: changes the current stateless bearer/refresh-cookie model; requires explicit session persistence, cookie, CSRF, logout, identity-mapping, and cross-origin topology decisions. Existing clients call separate origins in some environments and currently send bearer tokens.
   - Effort: High.

2. **Keep the current bearer/refresh contract and add Keycloak token validation to the SPA flow** — SPA authenticates with Keycloak and sends bearer tokens to SMP; local principal mapping remains server-side.
   - Pros: closer to the existing resource-server chain and frontend bearer API model.
   - Cons: not the selected browser BFF; exposes provider access tokens to the browser and retains the token-handling burden ADR-0009 was designed to constrain.
   - Effort: High.

3. **Hybrid migration with temporary local and Keycloak human login** — serve both login paths during transition, with an explicit account-linking/migration policy and eventual local-password retirement.
   - Pros: can reduce cutover risk and preserve recovery for existing accounts.
   - Cons: expands credential attack surface and support burden; dual session protocols and duplicate/linked identities need bounded duration and retirement criteria.
   - Effort: Very high.

### Recommendation

Proceed with the selected browser BFF direction as an SDD proposal, treating this as a human identity/session architecture change rather than a narrow provider hookup. Preserve the local principal as the stable authorization identity: resolve Keycloak subject/issuer through a uniquely constrained external-identity link, then continue using current database-backed workspace memberships and platform roles. Do not equate an OIDC email or role claim with an existing account or authorization grant.

Before proposal/spec completion, resolve the following decisions with the owner:

- **Migration and linking:** migrate existing local users by verified email, require an authenticated local session to link, use a support/admin process, or permit first-login linking? Define duplicate-email, changed-email, unverified-email, disabled/deactivated account, and unlink/recovery behavior. Determine whether local password login/registration/recovery remains available during rollout or is retired, and how existing refresh sessions are invalidated.
- **Session semantics:** use Spring WebSession/session store (and select persistence/replication strategy) or another server-side session mechanism? Define idle/absolute lifetime, renewal, logout/revocation, account disablement propagation, and cookie name/path/domain/SameSite/Secure settings. Session affinity or distributed storage implications are unresolved.
- **CSRF:** define CSRF-token delivery/validation for unsafe cookie-authenticated API calls, including app/admin mutations and logout. Existing trusted-Origin validation is limited to refresh/logout and is not automatically sufficient for a BFF session.
- **Route topology:** decide whether each SPA calls its same-origin backend BFF routes, shares one backend hostname, or calls a cross-origin API with credentials. Define app and admin login entry routes, backend `/oauth2/authorization/{registrationId}` and callback routes, success/failure destinations, safe return-path allowlisting, and whether admin uses a distinct Keycloak client or the same client. Keep `/api/mcp` bearer auth and social-provider OAuth callback paths independent.
- **Principal and authorization mapping:** specify stable OIDC key (`issuer` + `sub`), local principal creation policy, required claims, email verification policy, and whether any Keycloak groups/roles are informational only. Existing platform permissions remain authoritative unless separately decided.
- **Cutover/operations:** define required Keycloak realm/client setup, redirect URI/environment inventory, secret delivery/rotation, local/dev/test identity provider strategy, rollback, and production session behavior across replicas. Decide if ADR-0009 is superseded or revised and update its enforcement evidence.

The change should reconcile login-experience and registration specs and add a distinct human-authentication contract; it should not alter the MCP `workspace-scoped-oauth` behavior. Existing password recovery and registration behavior should not be described as removed until product and migration decisions establish their lifecycle.

### Risks

- A session-based BFF conflicts with ADR-0009's stateless access/refresh token decision and current API/frontend contracts; an explicit architecture decision and test strategy are required.
- Cookies make CSRF protection necessary for every unsafe authenticated route; current origin checks cover only refresh/logout.
- Automatic account matching by email can cause account takeover or unintended identity merges, particularly for unverified or changed email addresses.
- Separate app/admin origins and the API origin may prevent browser session cookies from being shared or sent as intended; CORS, SameSite, redirect, and deployment topology must be validated together.
- Existing platform roles, workspace memberships, account status, and audit principal identity must remain local and authoritative unless an approved policy explicitly changes that boundary.
- Persisted server sessions introduce availability, expiry, revocation, and multi-replica coordination requirements absent from the current stateless access-token path.
- The CVIX reference has useful OIDC configuration and routing examples but no located identity OAuth implementation to copy; avoid treating its realm, claims, or topology as Profile Tailors requirements.

### Ready for Proposal

Yes, for a proposal that records the selected Spring Security browser BFF direction and explicitly carries unresolved account-linking, session/CSRF, and route-topology choices as owner decisions. Do not finalize implementation-level specs for those unresolved behaviors until the product/security owner selects policies. The proposal should identify ADR-0009 and the existing login/registration specs as contracts to reconcile, while keeping MCP OAuth separate.
