# Delta for Human Authentication

## ADDED Requirements

### Requirement: Keycloak Owns Human Authentication Lifecycle

After cutover, Keycloak MUST be the sole authority for human login, signup and credential creation, email verification, password recovery, and logout. The browser MUST use Spring Security `oauth2Login` through the backend-for-frontend (BFF). Provider tokens MUST NOT be exposed to browser JavaScript. The backend MUST use a relying-party session derived from successful Keycloak authentication; that session MUST NOT act as an identity provider.

#### Scenario: Guest begins human login

- GIVEN a guest requests a protected dashboard or admin resource
- WHEN no valid relying-party session exists
- THEN the browser MUST be redirected through the BFF to Keycloak authentication
- AND the application MUST preserve only a safe, allowlisted return destination
- AND provider tokens MUST NOT be exposed to browser JavaScript

#### Scenario: Keycloak callback succeeds

- GIVEN a guest completes Keycloak authentication
- WHEN Spring Security processes the registered OAuth2 callback
- THEN the BFF MUST establish a relying-party session only after successful authentication and local principal resolution
- AND the browser MUST continue only to a safe, allowlisted destination

#### Scenario: Callback fails or has invalid state

- GIVEN an OAuth2 callback has invalid state, an invalid response, or a failed authentication
- WHEN the BFF processes the callback
- THEN it MUST NOT establish an authenticated session
- AND it MUST return the user to a safe failure destination without exposing tokens or protocol secrets

#### Scenario: Human lifecycle action is owned by Keycloak

- GIVEN a human requests signup, credential creation, email verification, password recovery, or logout
- WHEN the action is performed after cutover
- THEN the action MUST use the Keycloak-owned lifecycle
- AND Profile Tailors MUST NOT issue or consume local human credential lifecycle tokens for that action

### Requirement: Stable Local Principal Resolution

Profile Tailors MUST retain ownership of the local principal and product/account state. A successfully authenticated Keycloak identity MUST resolve through a persisted external-identity association from its validated `(issuer, sub)` pair to an already existing `principal_id`. The `(issuer, sub)` pair MUST uniquely identify the external identity association. This association MUST remain separate from `principals.subject` and `principals.provider`; linking an external identity MUST NOT replace or change either local-principal identity value. Profile Tailors MUST NOT select, link, or merge local principals using email equality. Local principal provisioning MUST remain manual and human signup MUST remain invite-only. Missing, ambiguous, disabled, or disallowed identity mappings MUST fail closed without changing existing links or principals. After a unique link resolves the local principal, authentication MUST continue only when that principal passes existing local authentication and account eligibility. Resource and platform authorization MUST be evaluated under local rules when the principal requests the protected resource.

#### Scenario: Existing linked identity resolves consistently

- GIVEN a validated Keycloak identity has an existing unique issuer-and-subject link
- WHEN that identity authenticates
- THEN the same local principal MUST be resolved
- AND local account, product, workspace, platform-permission, and audit identity MUST remain associated with that principal

#### Scenario: External identity link preserves local principal identity

- GIVEN a validated Keycloak `(issuer, sub)` identity is linked to an existing local `principal_id`
- WHEN Profile Tailors persists or resolves the external-identity association
- THEN the association MUST map that `(issuer, sub)` pair to the existing `principal_id`
- AND `principals.subject` and `principals.provider` MUST remain unchanged

#### Scenario: Email equality cannot merge accounts

- GIVEN a Keycloak identity email matches the email of an unlinked local principal
- WHEN authentication attempts local principal resolution
- THEN the system MUST NOT link or merge the accounts from email equality alone
- AND it MUST require the approved safe linking or migration procedure

#### Scenario: Ambiguous or unsafe identity fails closed

- GIVEN no unique approved local-principal mapping can be established
- WHEN the Keycloak callback is processed
- THEN the BFF MUST NOT establish an authenticated relying-party session
- AND existing local identities and authorization state MUST remain unchanged

#### Scenario: Missing identity mapping fails closed

- GIVEN a validated Keycloak `(issuer, sub)` identity has no persisted external-identity association
- WHEN the Keycloak callback is processed
- THEN the BFF MUST NOT establish an authenticated relying-party session
- AND Profile Tailors MUST NOT select or create a local principal from email or other profile claims

#### Scenario: Disabled or disallowed identity mapping fails closed

- GIVEN a Keycloak identity has a missing, disabled, or disallowed external-identity association
- WHEN the Keycloak callback is processed
- THEN the BFF MUST NOT establish an authenticated relying-party session
- AND Profile Tailors MUST NOT select a different principal based on email

#### Scenario: Linked principal must pass local authentication and account eligibility

- GIVEN a validated Keycloak identity resolves through its unique external-identity association to a local principal
- AND that local principal fails an existing local authentication or account eligibility rule
- WHEN the BFF evaluates whether to establish a relying-party session
- THEN Profile Tailors MUST NOT establish the session
- AND email claims MUST NOT change which local principal is evaluated

#### Scenario: Linked principal remains selected when Keycloak email matches another principal

- GIVEN a validated Keycloak identity resolves through its unique external-identity association to local principal A
- AND its email matches local principal B
- WHEN the callback resolves the authenticated principal
- THEN Profile Tailors MUST select only principal A
- AND it MUST NOT select, link, or merge principal B based on the email match

### Requirement: Local Authorization Remains Authoritative

Keycloak identity claims and realm or client roles MUST NOT, by themselves, grant Profile Tailors workspace access, resource access, platform permissions, or product/account eligibility. Profile Tailors MUST authorize using its local principal, local account/product state, workspace membership, resource policy, and platform permissions. Profile Tailors MUST retain the audit identity associated with the local principal.

#### Scenario: Authenticated identity has no local workspace membership

- GIVEN a human is authenticated by Keycloak and resolves to a local principal
- AND the local principal has no membership in the requested workspace
- WHEN the human requests a workspace resource
- THEN Profile Tailors MUST deny access regardless of Keycloak identity or role claims

#### Scenario: Keycloak role does not grant platform permission

- GIVEN a human has a Keycloak realm or client role but lacks the corresponding local platform permission
- WHEN the human requests a platform-admin operation
- THEN Profile Tailors MUST deny the operation

#### Scenario: Local authorization is evaluated for each protected resource request

- GIVEN a human has a relying-party session for a locally eligible principal
- AND that principal lacks authorization for the requested protected resource
- WHEN the human requests that resource
- THEN Profile Tailors MUST deny the request under the applicable local authorization rules

### Requirement: No Local Human Password Authentication After Cutover

After cutover, Profile Tailors MUST NOT authenticate human accounts using locally stored passwords or provide a local-password login path. Local credential-based signup, verification, and recovery paths MUST NOT remain available for human accounts. A transition path, if separately approved, MUST have an explicit end condition and MUST be unavailable after cutover. Rollback after cutover MUST NOT silently re-enable local-password authentication.

#### Scenario: Local password login is unavailable after cutover

- GIVEN the human-authentication cutover is complete
- WHEN a human submits local email/password credentials to a Profile Tailors authentication endpoint
- THEN the request MUST NOT authenticate the human or create a session

#### Scenario: Local credential lifecycle endpoints are unavailable after cutover

- GIVEN the human-authentication cutover is complete
- WHEN a client invokes a local human signup, password recovery/reset, email-verification, or credential login endpoint
- THEN Profile Tailors MUST NOT perform the local credential lifecycle action
- AND it MUST direct supported human flows to the Keycloak-owned lifecycle without disclosing account existence

### Requirement: Relying-Party Session and CSRF Boundaries

The backend session MUST represent the relying party's authenticated state derived from Keycloak authentication, not a second identity provider. The session MUST be protected by secure cookie controls appropriate to the approved deployment topology. Every unsafe request authenticated by a browser session MUST have complete CSRF protection. A missing or invalid session MUST NOT be treated as authenticated. The approved session, CSRF, and logout policies MUST be applied to both browser clients. Only browser navigations that initiate OIDC login or process an OIDC callback MAY redirect through the Keycloak authentication flow. An unauthenticated JSON/API request MUST receive HTTP `401` and MUST NOT receive an HTML or OIDC redirect. If a request presents both a BFF session and a bearer or API-key credential, Profile Tailors MUST reject it as ambiguous. A BFF session MUST NOT substitute for a bearer, API-key, or MCP credential.

The approved provider is Keycloak `26.3.5` at issuer `https://keycloak.ahome.quest/realms/pt`. The dashboard and admin MUST use separate confidential OIDC clients `pt-app` and `pt-admin`. Their BFF callbacks MUST be `https://api.profiletailors.com/login/oauth2/code/pt-app` and `https://api.profiletailors.com/login/oauth2/code/pt-admin`, respectively. Real Keycloak realm/client configuration and the DNS/proxy route to `api.profiletailors.com` remain pending validation; this contract does not claim that either is deployed or reachable.

For beta, relying-party sessions MUST be held in memory on one application replica, with a 30-minute idle expiry and an 8-hour absolute expiry. The host-only session cookie MUST be scoped to `/` and use `Secure`, `HttpOnly`, and `SameSite=Lax`. The exact Spring cookie name remains pending implementation verification. `GET /api/auth/csrf` MUST provide the CSRF token, and every unsafe cookie-authenticated request, including logout, MUST carry it in `X-CSRF-TOKEN`. These policies apply to both browser clients. Critical mutations MUST additionally require MFA evidence and an `auth_time` no older than 300 seconds; this requirement is separate from the admin baseline `acr=2` requirement.

#### Scenario: Unsafe browser request without valid CSRF proof

- GIVEN a browser has a relying-party session
- WHEN it submits an unsafe authenticated request without valid CSRF protection
- THEN the BFF MUST reject the request without performing the operation

#### Scenario: Browser navigation with absent or invalid session begins login

- GIVEN a browser navigation requests a protected page and its relying-party session is absent, expired, or invalid
- WHEN the BFF evaluates the request
- THEN the BFF MUST treat the request as unauthenticated and begin the approved Keycloak login flow

#### Scenario: Unauthenticated JSON/API request receives unauthorized response

- GIVEN a JSON/API request has no valid authentication credential
- WHEN the request targets a protected API resource
- THEN Profile Tailors MUST return HTTP `401`
- AND it MUST NOT return an HTML login page or redirect to Keycloak

#### Scenario: Request with BFF session and bearer or API-key credential is rejected

- GIVEN a request presents both a BFF session and a bearer or API-key credential
- WHEN Profile Tailors evaluates authentication for the request
- THEN Profile Tailors MUST reject the request as ambiguous
- AND it MUST NOT authenticate the request using either credential

#### Scenario: BFF session does not replace an API credential

- GIVEN a request requires a bearer, API-key, or MCP credential
- AND the request presents only a valid BFF session
- WHEN Profile Tailors evaluates authentication for the request
- THEN Profile Tailors MUST reject the request as unauthenticated
- AND the BFF session MUST NOT satisfy the required credential

#### Scenario: Logout ends the relying-party session

- GIVEN a human has an active relying-party session
- WHEN the human completes the approved logout flow
- THEN the BFF MUST invalidate the relying-party session
- AND the human authentication lifecycle MUST be handled by Keycloak
- AND the approved Keycloak logout and session-revocation policy MUST be applied

#### Scenario: Browser client uses its registered callback

- GIVEN a human starts login from the dashboard or admin browser client
- WHEN Keycloak returns the authorization response to the BFF
- THEN `pt-app` MUST use `https://api.profiletailors.com/login/oauth2/code/pt-app` for dashboard login
- AND `pt-admin` MUST use `https://api.profiletailors.com/login/oauth2/code/pt-admin` for admin login
- AND the callback MUST NOT establish a session if the OIDC response or local principal resolution fails

#### Scenario: Realm or proxy readiness is not assumed

- GIVEN implementation or rollout readiness is evaluated
- WHEN the configured Keycloak realm/client or `api.profiletailors.com` DNS/proxy route has not been validated
- THEN the corresponding readiness gate MUST remain pending
- AND specifications MUST NOT represent the realm or route as deployed or reachable

#### Scenario: Keycloak is unavailable after cutover

- GIVEN the approved human-authentication cutover is complete
- AND Keycloak authentication is unavailable
- WHEN a human starts login or a session must be established
- THEN Profile Tailors MUST fail closed
- AND Profile Tailors MUST NOT fall back to local human credentials
- AND the event MUST NOT be described as a tested outage behavior until acceptance testing is complete

### Requirement: Administrative Authentication Requires LoA 2

The `pt-admin` client MUST require `Minimum ACR Value = 2` and use the dedicated `browser-admin-mfa` flow. Keycloak MUST issue LoA 2 and `acr=2` only after successful password and OTP authentication. The BFF MUST validate the exact issuer `https://keycloak.ahome.quest/realms/pt`, a non-empty subject, the expected audience and admin client identity, and `acr=2` before granting an administrative session or access. Admin authentication MUST require LoA 2 even when the request omits `acr_values` or requests level 1, including when the browser already has a dashboard SSO session. The behavior that causes this step-up and the one-time OTP challenge MUST be acceptance-tested and MUST NOT be described as already tested. The `pt-app` client MUST NOT have a minimum ACR requirement.

#### Scenario: Admin login follows password and OTP

- GIVEN a user starts admin login through `pt-admin`
- WHEN the user completes password authentication but has not completed OTP
- THEN Keycloak MUST NOT issue admin authentication state with `acr=2`
- AND the BFF MUST NOT grant administrative access
- WHEN the user completes OTP in `browser-admin-mfa`
- THEN Keycloak MAY issue authentication state with `acr=2`
- AND the BFF MUST grant an admin session only after validating issuer, subject, audience, admin client identity, and `acr=2`

#### Scenario: Admin step-up is mandatory with absent or lower requested ACR

- GIVEN a user has an active dashboard SSO session
- WHEN the user starts admin login with no `acr_values` or with a request for level 1
- THEN the admin flow MUST still require password and OTP before issuing LoA 2 admin authentication state
- AND OTP MUST be requested only once during that admin login
- AND the BFF MUST validate issuer `https://keycloak.ahome.quest/realms/pt`, subject, expected audience, admin client identity, and `acr=2`

#### Scenario: Admin token validation fails closed

- GIVEN a token has an unexpected issuer, subject, audience, admin client identity, or `acr` other than `2`
- WHEN the BFF evaluates the token for administrative access
- THEN the BFF MUST deny administrative access
- AND it MUST NOT create an administrative session from that token

#### Scenario: Dashboard client does not require a minimum ACR

- GIVEN a human starts dashboard login through `pt-app`
- WHEN Keycloak evaluates the dashboard authentication request
- THEN `pt-app` MUST NOT require a minimum ACR value

#### Scenario: Dashboard session cannot authorize a direct admin API call

- GIVEN a user has a valid dashboard-only `pt-app` session and no valid LoA 2 `pt-admin` authentication state
- WHEN the user calls `/api/admin/**` directly
- THEN Profile Tailors MUST deny the request

### Requirement: MCP Authentication Remains Separate

Human authentication migration MUST NOT change local MCP API-key authentication or MCP `workspace-scoped-oauth`. MCP API keys MUST remain locally authenticated and workspace-scoped OAuth MUST retain its separate authorization-code/PKCE, audience, workspace, and scope contract. Human BFF sessions MUST NOT substitute for either MCP authentication mechanism.

#### Scenario: MCP API key continues to authenticate independently

- GIVEN an MCP request presents a valid locally managed API key
- WHEN MCP authentication is evaluated after human-authentication cutover
- THEN the existing MCP API-key contract MUST continue to apply independently of the human BFF session

#### Scenario: Workspace-scoped OAuth remains separate

- GIVEN an MCP client starts its workspace-scoped OAuth authorization-code/PKCE flow
- WHEN the flow is processed after human-authentication cutover
- THEN its existing workspace binding, token audience, scope, and membership requirements MUST remain unchanged
- AND a human BFF session MUST NOT satisfy the MCP OAuth contract

### Requirement: Non-MCP Machine Authentication Is Retired at Cutover

After the approved human-authentication cutover, Profile Tailors MUST NOT preserve or accept any
non-MCP bearer, API-key, or service-account authentication route. Before cutover, implementation MUST
inventory all existing non-MCP consumers and routes and identify each for retirement or migration;
this inventory and remediation are cutover work, not an unresolved policy decision. Migrating a
consumer MUST NOT preserve its existing non-MCP machine-authentication route after cutover. An
unauthenticated JSON/API request MUST receive HTTP `401`, not an OIDC redirect. A BFF session MUST
NOT substitute for a machine credential, and a request that combines a BFF session with bearer or
API-key credentials MUST be rejected. MCP API-key authentication and MCP `workspace-scoped-oauth`
remain separate and unchanged.

#### Scenario: Non-MCP machine consumer inventory is completed before cutover

- GIVEN implementation is preparing the human-authentication cutover
- WHEN the non-MCP machine-authentication inventory is reviewed
- THEN every existing non-MCP bearer, API-key, or service-account consumer and route MUST have an identified retirement or migration action
- AND the inventory MUST NOT treat preservation of an existing machine-authentication route as an unresolved policy choice

#### Scenario: Non-MCP machine credentials cannot authenticate after cutover

- GIVEN the approved human-authentication cutover is complete
- AND a request targets a non-MCP JSON/API route
- WHEN the request presents a bearer, API-key, or service-account credential, with or without a BFF session
- THEN Profile Tailors MUST NOT authenticate the request through a non-MCP machine-authentication route
- AND Profile Tailors MUST return HTTP `401` rather than redirecting to OIDC

#### Scenario: Mixed BFF session and bearer or API-key credentials are rejected

- GIVEN a request presents both a BFF session and a bearer or API-key credential
- WHEN Profile Tailors evaluates authentication for the request
- THEN Profile Tailors MUST reject the request as ambiguous
- AND it MUST NOT authenticate the request using either credential

#### Scenario: MCP API-key authentication remains unchanged and isolated

- GIVEN an MCP request presents a valid locally managed MCP API key
- WHEN MCP authentication is evaluated after human-authentication cutover
- THEN the existing MCP API-key contract MUST authenticate the request independently of non-MCP machine-authentication retirement
- AND a BFF session MUST NOT replace the MCP API key

#### Scenario: MCP workspace-scoped OAuth remains unchanged and isolated

- GIVEN an MCP client uses the existing workspace-scoped OAuth authorization-code/PKCE contract
- WHEN the request is evaluated after human-authentication cutover
- THEN its existing workspace binding, audience, scope, and membership requirements MUST remain unchanged
- AND a BFF session MUST NOT satisfy the MCP OAuth contract

### Requirement: Human Login and Logout Ownership

After the approved human-authentication cutover, Keycloak MUST own human browser login and logout
through the Spring Security `oauth2Login` BFF and a relying-party backend session. Profile Tailors
MUST establish the relying-party session only after successful Keycloak authentication and local
principal resolution. Logout MUST invalidate the relying-party session and remain part of the
Keycloak-owned human authentication lifecycle. Local principal and local authorization requirements
remain unchanged.

#### Scenario: Authenticated human returns to a protected route

- GIVEN a human has completed Keycloak authentication and has a valid relying-party session
- WHEN the human requests a protected dashboard or admin route
- THEN Profile Tailors MUST resolve the linked local principal and apply local authorization before returning protected content
- AND an admin route MUST additionally require validated `pt-admin` authentication state with `acr=2`

### Requirement: Local Refresh-Token Authentication Is Retired After Cutover

After the approved human-authentication cutover, `POST /api/auth/refresh` MUST NOT exchange a legacy
`pt_refresh` token for local access credentials, a replacement local refresh token, or another local
authenticated session. Existing local refresh sessions and tokens MUST be invalidated or rejected so
they cannot authenticate a human or mint local access credentials after cutover. The approved
cutover MUST invalidate or reject existing sessions and tokens and MUST NOT retain a local human
credential transition window. A Keycloak BFF session MUST NOT be converted into local access or
refresh credentials through the local refresh endpoint.

#### Scenario: Legacy refresh token is presented after cutover

- GIVEN the approved human-authentication cutover is complete
- WHEN a client presents a legacy `pt_refresh` token to `POST /api/auth/refresh`
- THEN the request MUST NOT authenticate the human or mint local access credentials
- AND no replacement local refresh token MUST be issued
- AND the response behavior MUST follow the approved cutover policy

#### Scenario: Existing local refresh session is presented after cutover

- GIVEN the approved human-authentication cutover is complete
- AND a local refresh session existed before cutover
- WHEN the session is presented to any local refresh-token authentication path
- THEN the session MUST NOT authenticate a human or mint local access credentials
- AND its invalidation or rejection MUST follow the approved cutover policy

#### Scenario: BFF session is not exchanged for local credentials

- GIVEN a human has an authenticated Keycloak BFF session
- WHEN a client calls `POST /api/auth/refresh`
- THEN the BFF session MUST NOT be exchanged for local access credentials or a local refresh token

### Requirement: Critical Mutations Require Fresh MFA

Critical mutations MUST require MFA evidence and an `auth_time` no older than 300 seconds, in addition to the applicable local authorization. This freshness policy MUST remain separate from the admin baseline requirement for `acr=2`.

#### Scenario: Critical mutation lacks fresh MFA

- GIVEN a human is authenticated but the authentication time is older than 300 seconds or MFA evidence is absent
- WHEN the human requests a critical mutation
- THEN Profile Tailors MUST deny the mutation

#### Scenario: Critical mutation has fresh MFA and local authorization

- GIVEN a human has MFA evidence and an `auth_time` no older than 300 seconds
- AND the local principal has the required local authorization
- WHEN the human requests a critical mutation
- THEN Profile Tailors MUST allow the mutation subject to the remaining applicable policy
