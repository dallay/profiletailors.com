# Proposal: Keycloak Human Authentication

## Intent

Make Keycloak the sole human-authentication authority through a Spring Security `oauth2Login` browser BFF. Profile Tailors retains ownership of local principals, account/product state, workspace and resource authorization, platform permissions, and audit. The backend session is a relying-party session, not an identity provider.

## Scope

### In Scope

- Route dashboard and admin browser authentication through separate confidential Keycloak clients and the backend BFF. Keep provider tokens out of browser JavaScript.
- Resolve human identities only through a unique validated `(issuer, sub)` link to a manually provisioned local principal. Never link or merge accounts by email. After linking, synchronize local email only from validated Keycloak state marked verified and only if no local principal or local email conflict exists; otherwise do not synchronize and fail closed according to local eligibility.
- Keep authorization local. Keycloak identity or role claims alone MUST NOT grant workspace, resource, or platform access.
- Apply the approved admin LoA 2/MFA boundary and fresh-MFA policy for critical mutations.
- Use an invite-only human lifecycle and hard cutover. Invalidate legacy `pt_refresh` sessions and retire local human password login and local credential-based signup, verification, and recovery. Do not retain dual human login or a local-password rollback path.
- Do not preserve non-MCP bearer/API-key/service-account authentication paths after cutover. Inventory those consumers and routes for retirement or migration as an implementation/remediation task. Keep MCP API-key authentication and MCP `workspace-scoped-oauth` separate and unchanged.
- Deliver through a feature-branch chain, with independently reviewable slices.
- Keep the proposal, affected login and lifecycle specifications, technical design, and implementation tasks aligned with owner decisions. Preserve validation and approval gates without presenting them as unresolved policy choices.

### Out of Scope

- Changing MCP API-key authentication or MCP `workspace-scoped-oauth` contracts.
- Moving local principal, product/account, authorization, platform-permission, or audit ownership to Keycloak.
- Claiming that Keycloak clients, callback routes, MFA flows, DNS/proxy routing, or tests are already configured, reachable, or passing.
- Editing ADR-0035 in this proposal. The owner has ratified ADR-0035.

## Approach

### Approved Decisions

| Area | Approved decision |
|---|---|
| BFF and identity provider | Use Spring Security `oauth2Login` with issuer `https://keycloak.ahome.quest/realms/pt` and Keycloak version `26.3.5`. The dashboard uses confidential client `pt-app`; admin uses confidential client `pt-admin`. |
| Callbacks | `pt-app`: `https://api.profiletailors.com/login/oauth2/code/pt-app`. `pt-admin`: `https://api.profiletailors.com/login/oauth2/code/pt-admin`. |
| Admin authentication | Set `Minimum ACR Value = 2` for `pt-admin` and use `browser-admin-mfa`. Password plus OTP yields LoA 2 and `acr=2`. The BFF validates `acr=2` server-side, along with issuer, subject, audience, and admin client identity. Admin step-up must hold when `acr_values` is absent or requests level 1, and when the browser has an existing `pt-app` SSO session. The flow must issue one OTP challenge. `pt-app` has no minimum ACR requirement. |
| Session and cookie | For beta, keep relying-party sessions in memory on one application replica, with 30-minute idle and 8-hour absolute expiry. Use a host-only cookie scoped to `/` with `Secure`, `HttpOnly`, and `SameSite=Lax`. Confirm the exact Spring cookie name during implementation. |
| CSRF | Provide `GET /api/auth/csrf`; send its token in `X-CSRF-TOKEN` on every unsafe cookie-authenticated request, including logout. Apply this to both browser clients. |
| Critical mutations | Require `auth_time` no older than 300 seconds and MFA evidence, in addition to local authorization. This policy is separate from admin's baseline `acr=2` requirement. |
| Provisioning and email | Keep human signup invite-only and local-principal provisioning manual. Link only by validated `(issuer, sub)`. Synchronize email only after the link, from verified Keycloak state, and without a local conflict. |
| Cutover | Perform a hard cutover with no dual human login. Invalidate `pt_refresh` sessions and retire local human credential paths. Rollback MUST NOT restore local human-password authentication. |
| Architecture decision | ADR-0035 is ratified by the owner and governs this authentication change. |
| Delivery | Use a feature-branch chain of independently reviewable delivery slices. |
| Machine authentication | Do not preserve non-MCP bearer/API-key/service-account authentication paths after cutover. Inventory consumers and routes for retirement or migration as implementation/remediation work. Keep MCP API-key authentication and MCP `workspace-scoped-oauth` independent and unchanged; a human BFF session MUST NOT substitute for MCP credentials. |
| Browser and API requests | Only OIDC login/callback browser navigations may redirect to Keycloak. Unauthenticated JSON/API requests return `401`. Reject requests that combine a BFF session with bearer/API-key credentials. |

### Validation and Release Gates

These are validation or implementation gates, not unresolved policy decisions:

- **Operational routing:** DNS/proxy routing to `api.profiletailors.com` failed in the user's environment. Verify DNS, TLS, proxy routing, and reachability of both exact callbacks before cutover. This remains an operational gate; routing is not claimed as implemented or working.
- **Keycloak deployment and flow:** Validate the realm, issuer discovery, confidential clients, callback registration, emitted claims, admin password-plus-OTP behavior, existing `pt-app` SSO step-up, and one-time OTP behavior in a test realm. No such configuration or test is claimed as complete.
- **Implementation checks:** Verify the final Spring cookie name and configured cookie/TTL behavior; implement and test the CSRF endpoint/header, OIDC validation, local identity mapping, authorization boundaries, critical-mutation checks, cutover invalidation, and machine-auth separation. No tests are claimed as run or passing.
- **Consumer discovery and compatibility remediation:** Inventory existing non-MCP bearer/API-key/service-account consumers and routes, then plan their retirement or migration before cutover. This is implementation/remediation work under the approved policy, not an unresolved decision about whether to preserve those paths.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `.agents/sdd/specs/login-experience/spec.md` | Reconcile | Specify Keycloak BFF login/logout for dashboard and admin, including the separate clients and admin authentication boundary. |
| `.agents/sdd/specs/registration/spec.md` and related identity lifecycle specs | Reconcile | Specify invite-only signup, manual local provisioning, verified-email synchronization after identity linking, and removal of local human credential lifecycle at cutover. |
| Human identity and session contracts under `.agents/sdd/specs/` | Reconcile | Specify `(issuer, sub)` linking, local authorization ownership, session/cookie/CSRF policies, and critical-mutation step-up. |
| `docs/architecture/adr/0035-*` | Ratified | ADR-0035 is ratified by the owner and is the approved architecture decision for this change. |
| `server/smp` authentication and session areas | Planned implementation | Add BFF/OIDC session handling and local identity resolution; retire local human credentials and non-MCP bearer/API-key/service-account authentication paths at cutover; inventory affected consumers for retirement or migration. |
| `apps/web/app` and `apps/web/admin` authentication areas | Planned implementation | Start the appropriate BFF login, hydrate/end sessions, and attach CSRF tokens to unsafe requests. |
| Deployment and authentication operations documentation | Planned validation/update | Configure secrets and realm/client deployment, validate callback DNS/proxy routing, and document operational readiness and cutover. |
| MCP API-key and `.agents/sdd/specs/workspace-scoped-oauth/spec.md` | Preserved | Keep existing machine-authentication contracts unchanged and separate from human BFF sessions. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Implementation drifts from ratified ADR-0035 or approved request-routing behavior. | Medium | Keep implementation, specifications, and tests aligned with ADR-0035 and the approved route strategy. |
| DNS/proxy routing or Keycloak callback configuration prevents login. | High | Resolve and validate the operational route and both registered callbacks; treat the reported DNS failure as an open release gate. |
| Admin flow or BFF validation accepts password-only or dashboard-only authentication. | High | Validate `acr=2` server-side and acceptance-test password-plus-OTP, low/missing `acr_values`, existing app SSO, one OTP challenge, and direct admin API access. |
| Incorrect identity linking or email synchronization changes account ownership. | High | Link only by unique `(issuer, sub)`; never link by email; synchronize only verified, conflict-free email after linking. |
| Cookie-authenticated requests are exposed to CSRF. | High | Implement and test the approved CSRF endpoint/header for all unsafe methods, including logout, on both browser clients. |
| In-memory sessions are unavailable across replicas or restarts. | Medium | Keep beta to one application replica; treat multi-replica or durable session storage as outside this decision and require a later architecture decision. |
| Human cutover leaves legacy refresh credentials usable. | High | Invalidate `pt_refresh` sessions and test that retired local human endpoints cannot authenticate or mint credentials; do not restore local-password login on rollback. |
| Human BFF authentication is accepted as machine authentication or grants local authorization. | Medium | Keep security paths separate and test local authorization and MCP credential boundaries independently. |
| The non-MCP machine-consumer inventory omits a consumer or route that needs retirement or migration. | Medium | Complete and validate the inventory as implementation/remediation work before cutover; do not preserve those paths through an allowlist or global credential fallthrough. |

## Rollback Plan

Before cutover, retain a deployable pre-cutover release for recovery only. Define and test cutover rollback triggers and session invalidation before production rollout. After cutover, a rollback may restore a prior application release only if it does not restore local human-password authentication; otherwise stop and obtain an explicitly approved emergency exception before action. Revoke or expire relying-party sessions as required by the approved policy. Keep MCP API-key authentication and MCP `workspace-scoped-oauth` unchanged.

## Dependencies

- Ratified ADR-0035 as the approved architecture decision for this change.
- A complete inventory of non-MCP bearer/API-key/service-account consumers and routes, with retirement or migration remediation identified before cutover.
- A deployed and validated Keycloak `26.3.5` realm, issuer, clients, admin flow, secrets, and exact callback registrations; none are asserted as already deployed.
- Successful DNS, TLS, and proxy routing to `api.profiletailors.com`; DNS/proxy validation remains blocked in the user's environment.
- Implementation and acceptance tests after the remaining approval, architecture, and operational gates are resolved.

## Success Criteria

- [ ] The dashboard and admin use separate confidential BFF clients with the approved issuer, version, and exact callback URIs; provider tokens are not exposed to browser JavaScript.
- [ ] Admin authentication requires server-validated `acr=2`, password plus OTP, and local platform authorization; dashboard-only state cannot access admin APIs.
- [ ] Local identity linking uses only validated `(issuer, sub)`; local email synchronization occurs only after linking from verified Keycloak state with no local conflict.
- [ ] Human signup is invite-only with manual local-principal provisioning, and cutover removes local human credential authentication and invalidates legacy `pt_refresh` sessions.
- [ ] Beta session, cookie, CSRF, and critical-mutation requirements match the approved decisions and pass implementation tests.
- [ ] Local authorization remains authoritative; non-MCP bearer/API-key/service-account authentication paths are not preserved after cutover and their consumers are inventoried for retirement or migration; MCP API-key and MCP `workspace-scoped-oauth` behavior remains separate and unchanged.
- [ ] Human BFF sessions never substitute for MCP credentials; mixed BFF-session plus bearer/API-key requests are rejected, and unauthenticated JSON/API requests return `401`.
- [ ] ADR-0035 remains the ratified architecture decision, and the implementation is delivered through a feature-branch chain; realm/client/callback/flow and DNS/proxy gates are validated before cutover.
- [ ] No client, route, MFA flow, DNS/proxy path, or test is described as implemented or passing without evidence.
