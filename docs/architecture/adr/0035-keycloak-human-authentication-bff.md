# ADR-0035: Delegate Human Authentication to Keycloak Through a Reactive BFF

- Status: Accepted
- Date: 2026-11-09
- Last revised: 2026-11-09
- Decision owners: Principal Architect
- Scope: `server/smp` identity/security, `apps/web/app`, `apps/web/admin`, Keycloak operations
- Supersedes: [ADR-0009](./0009-jwt-and-httponly-cookie-authentication.md)
- Superseded by: None
- Related:
  - OpenSpec: [Keycloak human authentication](../../../.agents/sdd/changes/keycloak-human-authentication/proposal.md), [Human authentication contract](../../../.agents/sdd/changes/keycloak-human-authentication/specs/human-authentication/spec.md), [Technical design](../../../.agents/sdd/changes/keycloak-human-authentication/design.md)
  - C4: [Container model](../c4/02-container.md), [Component model](../c4/03-component.md)
  - Issues/PRs: None

## Context

Profile Tailors currently authenticates human users with locally managed passwords, issues bearer
JWTs, and renews browser sessions with locally managed refresh tokens in HttpOnly cookies. ADR-0009
requires this dual-token model. The dashboard keeps access tokens in memory and calls the API with
bearer authentication. The platform-admin SPA has a separate client-side authentication store.

The backend also authenticates non-human credentials, including MCP API keys and service-account
credentials. MCP workspace-scoped OAuth has a separate issuer, audience, workspace, and scope
contract. Workspace membership, resource permissions, platform permissions, account state, and
audit identity are stored and evaluated by Profile Tailors.

The user has selected Keycloak as the sole human-authentication authority after cutover, with Spring
Security WebFlux `oauth2Login` through a browser BFF. The backend session is a relying-party session
derived from Keycloak authentication. Local MCP API-key authentication remains an explicit
exception. This changes ADR-0009's accepted human authentication model, but does not change
authorization ownership.

Account linking uses the validated `(issuer, sub)` pair and local principals remain manually
provisioned. Invite-only signup is approved. The BFF session is beta-only, in-memory, and limited to
one backend replica. Its idle timeout is 30 minutes and its absolute lifetime is 8 hours. The
host-only cookie is `Secure`, `HttpOnly`, `SameSite=Lax`, and scoped to `/`. Browser-session unsafe
requests use the approved CSRF endpoint and `X-CSRF-TOKEN` header. These decisions authorize
implementation, not production cutover. Keycloak realm behavior, the OTP acceptance flow, callback
DNS/proxy reachability, and Spring's exact session-cookie name remain unverified operational gates.

## Decision Drivers

- Make Keycloak the single authority for human credentials and authentication lifecycle.
- Keep provider tokens out of browser JavaScript.
- Preserve stable local principal identity and Profile Tailors authorization and audit decisions.
- Keep human browser authentication separate from machine/API-key and MCP workspace OAuth paths.
- Use the existing Spring Boot 4, Spring Security, WebFlux, Kotlin, and R2DBC architecture.
- Prevent unsafe account merges and cookie-authenticated CSRF.

## Decision

1. After an approved cutover, Keycloak MUST own human login, signup and credential creation, email
   verification, password recovery, logout, and any enabled human identity federation. Profile
   Tailors MUST NOT authenticate human accounts with locally stored passwords after cutover.
2. Human browser sign-in MUST use Spring Security reactive `oauth2Login` through the backend-for-
   frontend. The backend MUST establish a relying-party session only after successful OIDC
   authentication and successful local principal resolution. The session MUST NOT act as an
   independent identity provider. Provider tokens MUST NOT be exposed to browser JavaScript.
3. A human OIDC identity MUST map to one stable local Profile Tailors principal through a unique
   association keyed by the validated issuer and subject. Email equality alone MUST NOT link or
   merge accounts. Missing, ambiguous, or disallowed mappings MUST fail closed without changing
   existing principals or authorization data.
4. Profile Tailors MUST remain authoritative for local principal and product/account state, workspace
   membership, resource authorization, platform permissions, and audit identity. Keycloak identity,
   group, and role claims MUST NOT independently grant Profile Tailors permissions.
5. Every unsafe browser-session-authenticated request MUST require complete CSRF protection. Origin
   or Referer validation MAY supplement CSRF protection but MUST NOT replace it. The browser MUST
   obtain a CSRF token from `GET /api/auth/csrf` and send it in `X-CSRF-TOKEN`.
6. Local MCP API-key authentication MUST remain independently authenticated by Profile Tailors.
   MCP workspace-scoped OAuth MUST remain a separate contract. A human BFF session MUST NOT satisfy
   either MCP authentication contract. After cutover, Profile Tailors MUST NOT accept bearer JWT,
   API-key, or service-account credentials on non-MCP routes. Existing non-MCP consumers MUST be
   inventoried and retired or migrated before cutover; no non-MCP machine-route allowlist will be
   created.
7. Any temporary coexistence with local human credentials MUST have an approved end condition and
   MUST be removed at cutover. A post-cutover application rollback MUST NOT silently restore local
   human-password authentication.
8. The beta session MUST use in-memory storage on one backend replica, a 30-minute idle timeout, and
   an 8-hour absolute lifetime. The host-only session cookie MUST be `Secure`, `HttpOnly`,
   `SameSite=Lax`, and scoped to `/`. Unsafe browser-session requests MUST use `GET /api/auth/csrf`
   and send `X-CSRF-TOKEN`. Production cutover MUST wait for verification of session behavior,
   cookie attributes, Keycloak clients/flows, and callback routing.
9. The dashboard and admin MUST use separate confidential OIDC clients, `pt-app` and `pt-admin`,
   with callbacks `https://api.profiletailors.com/login/oauth2/code/pt-app` and
   `https://api.profiletailors.com/login/oauth2/code/pt-admin`. The admin client MUST require
   `Minimum ACR Value = 2` and the `browser-admin-mfa` flow; the BFF MUST validate issuer, subject,
   audience/client, and `acr=2` before granting administrative access. The `pt-app` client MUST NOT
   impose a minimum ACR.
10. Only browser OIDC login/callback navigations MAY redirect to Keycloak. Unauthenticated JSON/API
    requests MUST receive HTTP `401`; a request with both a BFF session and bearer/API-key
    credentials MUST be rejected as ambiguous.
11. ADR-0035 supersedes ADR-0009. ADR-0009's historical decision remains unchanged; only its status
    and supersession link are updated. Reconcile the login, registration, recovery, verification, C4,
    and operational contracts with this decision.

## Scope and Boundaries

The Identity infrastructure configures the reactive OAuth2 client, validates the OIDC login
response, resolves the external identity, and establishes the relying-party session. Domain and
application layers remain framework-agnostic and expose only the contracts needed to resolve the
local principal. The Profile Tailors principal remains the identity consumed by tenancy,
authorization, governance, and audit.

The browser BFF flow applies to the dashboard and platform-admin surfaces, which are the current
product surfaces with human login flows. The public marketing surface does not provide a human login
flow. After cutover, non-MCP bearer JWT, API-key, and service-account authentication paths are not
retained. Existing consumers must be inventoried and migrated or decommissioned before cutover. The
MCP API-key exception and MCP workspace-scoped OAuth contract remain separate and unchanged. Social-
platform OAuth connections remain separate from human login.

## Alternatives Considered

### Keep local human passwords and dual-token sessions

- Description: Retain Profile Tailors password authentication, locally issued access JWTs, and
  refresh-token rotation.
- Advantages: Preserves the current stateless API and frontend behavior with limited migration work.
- Disadvantages: Contradicts the user-approved requirement that Keycloak own all human
  authentication and lifecycle functions.
- Reason rejected: Local human authentication must end at the approved cutover.

### Authenticate in the SPA with Authorization Code and PKCE

- Description: Let each SPA perform OIDC directly and send Keycloak bearer tokens to the API.
- Advantages: Preserves a bearer-oriented API and avoids a backend browser session.
- Disadvantages: Exposes provider access tokens to browser JavaScript and does not follow the
  selected BFF direction.
- Reason rejected: The user selected a Spring Security browser BFF to keep provider tokens outside
  browser JavaScript.

### Move workspace and platform authorization into Keycloak

- Description: Use Keycloak groups or roles as the authority for workspace/resource and platform
  access.
- Advantages: Centralizes identity and authorization administration in the identity provider.
- Disadvantages: Replaces Profile Tailors' local membership, resource policy, entitlement, platform
  permission, and audit ownership with a different authorization model.
- Reason rejected: The approved product boundary keeps Profile Tailors responsible for authorization
  of its workspaces and resources.

### Retain a local human-password fallback after cutover

- Description: Keep local passwords available if Keycloak or the BFF is unavailable.
- Advantages: Provides an independent human-login path during an identity-provider outage.
- Disadvantages: Creates a second human-authentication authority and can bypass Keycloak lifecycle
  and security policy.
- Reason rejected: The user requires Keycloak to be the sole human-authentication authority after
  cutover. Any pre-cutover transition must be bounded and approved.

## Consequences

### Positive

- Keycloak owns human credential and authentication lifecycle after cutover.
- Browser clients do not receive Keycloak provider tokens.
- Local principal identity, workspace authorization, platform permissions, and audit remain under
  Profile Tailors control.
- Machine/API-key and MCP OAuth contracts remain separate from browser sessions.
- Non-MCP machine credential routes are retired after their consumers are inventoried and migrated
  or decommissioned.

### Negative

- The BFF introduces a server-side relying-party session, with beta operation limited to one backend
  replica and explicit expiry and revocation behavior.
- Cookie-authenticated requests require complete CSRF handling in both browser surfaces.
- Existing users and local credential lifecycle behavior require a controlled migration and cutover.
- Login and callback availability depend on Keycloak and approved route/client configuration.

### Risks

- An unsafe identity-linking process can merge accounts or grant access to the wrong local principal.
- Session or cookie configuration may fail across the separate app, admin, and API origins.
- Incomplete chain matchers may let a human session satisfy a machine-authenticated endpoint or
  may redirect API clients through browser login.
- A rollback may accidentally re-enable local human passwords after cutover.

### Accepted Trade-offs

- The stateless access-token model in ADR-0009 is replaced for human browser access by a
  backend-managed relying-party session. Non-MCP machine bearer authentication is also removed at
  cutover; MCP remains separate.
- Profile Tailors keeps local authorization even though Keycloak becomes the human authentication
  authority.

## Compliance and Enforcement

- Reactive security tests MUST verify browser BFF, bearer-resource-server, MCP API-key, and
  workspace-scoped OAuth paths remain isolated.
- Security tests MUST verify non-MCP bearer JWT, API-key, and service-account credentials are
  rejected after cutover while MCP API-key and workspace-scoped OAuth behavior remains unchanged.
- Identity tests MUST verify unique issuer/subject mapping, fail-closed behavior, and absence of
  email-only account linking.
- WebFlux tests MUST verify CSRF protection for unsafe session-authenticated requests and session
  invalidation according to the approved lifecycle.
- Authorization tests MUST verify that Keycloak roles alone do not grant local workspace or
  platform access.
- Cutover tests MUST verify local human-password login and local credential lifecycle paths cannot
  authenticate or mutate human credentials after cutover.
- Changes MUST preserve existing Hexagonal Architecture and Spring Modulith checks. No architecture
  test or security gate may be weakened to accommodate the integration.

## Verification

- The design was checked against the existing identity/security configuration, authorization
  model, login/registration contracts, ADR-0009, and the CVIX `server/engine` OAuth2 configuration
  reference.
- Spring Security 7.0 reactive documentation confirms the `oauth2Login` authorization-code flow,
  reactive WebSession-backed CSRF token repository, cookie-backed CSRF option, and reactive
  OIDC-initiated logout support. These capabilities do not select Profile Tailors' session store,
  token delivery, topology, or revocation policy.
- ADR-0035 was ratified by the Principal Architect on 2026-11-09. No implementation or runtime
  behavior has been verified. Keycloak MFA, DNS/proxy routing, and exact Spring cookie-name checks
  remain pending before production cutover.

## Migration or Remediation

1. Inventory every non-MCP bearer, API-key, and service-account consumer; migrate or decommission each
   before cutover.
2. Reconcile product and architecture specifications and operational documentation with the
   approved values.
3. Implement and test identity mapping and BFF security before changing the human login entry
   points.
4. Verify workspace/resource authorization and admin permission behavior remain local.
5. Migrate existing identities and cut over only after approved readiness checks pass.
6. Disable local human-password login and credential lifecycle endpoints at cutover. Keep MCP
   API-key authentication and workspace-scoped OAuth unchanged.

## Follow-up Actions

- [x] Ratify ADR-0035 and supersede ADR-0009.
- [x] Select identity linking, manual principal provisioning, session, cookie, CSRF, client, and cutover policies.
- [ ] Inventory and retire or migrate non-MCP machine-authentication consumers before cutover.
- [ ] Verify Keycloak MFA, callback DNS/proxy routing, and Spring cookie behavior before cutover.

## Revisit Conditions

Revisit this decision if Keycloak no longer meets human identity requirements, if the browser
topology cannot safely support the BFF session, if a security review identifies a material flaw in
the selected model, or if Profile Tailors changes ownership of workspace/resource authorization.
