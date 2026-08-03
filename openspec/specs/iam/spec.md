# IAM Platform Specification

## Purpose

Define the reusable SaaS IAM and workspace platform for `server/smp`. This specification establishes
the bounded contexts, principal taxonomy, resource context model, authorization semantics,
credential mechanisms, and governance requirements that form the platform foundation. See
`docs/architecture/iam-platform.md` for the full architecture documentation.

---

## Requirements

### Requirement: Platform Bounded Contexts

The system MUST define the following bounded contexts for the platform architecture:

| Context           | Ownership                                                                     |
|-------------------|-------------------------------------------------------------------------------|
| **Identity**      | Principal identity semantics, principal taxonomy                              |
| **Tenancy**       | Workspace lifecycle, ownership, membership                                    |
| **Authorization** | Permissions, roles, grants, scopes, policies, effective evaluation            |
| **Credentials**   | Authentication credential and token semantics                                 |
| **Governance**    | Auditing, runtime proof, explainability                                       |
| **Platform**      | Cross-cutting seams: mediator dispatch, context propagation, shared contracts |

Phase one MUST implement only the minimum contracts and behaviors required by the proving slice.
No single context MUST absorb unrelated responsibilities merely for convenience.

### Requirement: Principal Taxonomy

The system MUST define the following principal types: `USER`, `SERVICE_ACCOUNT`, `API_KEY`,
`SYSTEM`, `INTEGRATION`, and `AGENT`. Every authenticated actor MUST be represented as one of
these types. Phase one MUST implement `USER`, `SERVICE_ACCOUNT`, and `API_KEY` principal paths.
`SYSTEM`, `INTEGRATION`, and `AGENT` executable behavior MAY be deferred.

The platform MUST keep principal identity independent from credential transport and authorization
outcome. Identity answers *who* the principal is; credentials answer *how* they authenticated;
authorization answers *what* they may do.

#### Scenario: Phase-one principal materialization

- GIVEN a protected request is authenticated successfully
- WHEN the principal is materialized
- THEN it MUST be represented as a typed principal (USER, SERVICE_ACCOUNT, API_KEY)
- AND it MUST flow through repo-local identity seams, not raw framework types

#### Scenario: Identity remains distinct from credential and authorization

- GIVEN a valid authenticated principal is established
- WHEN authorization is later evaluated
- THEN principal identity, credential type, and authorization outcome MUST remain separate concerns
- AND authentication success alone MUST NOT imply access

### Requirement: Resource Context Taxonomy

The system MUST define the following resource contexts: `GLOBAL`, `USER`, `WORKSPACE`, and
`SYSTEM`. Authorization decisions MUST be evaluated relative to an explicit resource context.
Permissions, grants, scopes, and policies MUST NOT rely on implicit context inference.
Phase one MUST fully support `WORKSPACE` context for the proving slice.

#### Scenario: Workspace-scoped request evaluates in explicit context

- GIVEN a protected capability is workspace-scoped
- WHEN authorization is evaluated
- THEN the platform MUST evaluate in WORKSPACE context
- AND the request MUST supply an explicit workspace identifier

#### Scenario: Missing workspace context is rejected

- GIVEN a workspace-scoped request omits the workspace identifier
- WHEN the request is processed
- THEN the platform MUST reject it
- AND the protected use case MUST NOT execute

### Requirement: Password Minimum Length Enforcement (SEC-009)

The system MUST enforce a 12-character minimum password length (ASVS L2 V2.1.1) at every
enforcement layer: domain/application handlers (`LocalAuthHandlers`, `ResetPasswordHandler` via
`MIN_PASSWORD_LENGTH = 12`), the HTTP contract (`LocalAuthController` `@Size(min=12)` + OpenAPI
`minLength`), and BDD scenarios. Passwords of 11 characters or fewer MUST be rejected; 12+ MUST be
accepted. The frontend `registerSchema` MUST mirror the reset schema with `min(12)` so the register
form validates inline instead of surfacing a backend 422.

#### Scenario: 11-character password rejected at handler

- GIVEN a registration or password-reset attempt with an 11-character password
- WHEN the handler validates it
- THEN the attempt MUST be rejected with a password-policy error

#### Scenario: 12-character password accepted

- GIVEN a registration or password-reset attempt with a 12-character password
- WHEN the handler validates it
- THEN the attempt MUST be accepted

#### Scenario: Register schema parity

- GIVEN the app `registerSchema` enforces `min(12)`
- WHEN a user enters fewer than 12 characters in the register form
- THEN inline validation MUST block submission (no network request)

### Requirement: Authorization Semantics

The system MUST enforce the following authorization rules:

1. **Explicit permission format**: `<domain>:<resource>:<action>` — no implicit inheritance
2. **Role composition**: Roles are compositions of explicit permissions
3. **Direct grants and denials**: Support `ALLOW`/`DENY` effects with optional expiration
4. **Scopes reduce only**: Scopes narrow access; they MUST NEVER create or expand permissions
5. **Explicit deny overrides**: A direct `DENY` MUST override any `ALLOW` path
6. **Feature entitlements separate**: Permissions answer "may the principal act?"; entitlements
   answer "is the feature available to the workspace?"
7. **Deny by default**: Absence of an explicit allow path MUST result in denial
8. **Deterministic evaluation**: Equivalent requests against equivalent state MUST produce
   equivalent outcomes

(Previously: `/api/media/proxy` was included in the Spring Security `permitAll()` list — an
unauthenticated media-proxy path existed outside the explicit allowlist. The allowlist no longer
includes it.)

#### Scenario: Permission is evaluated by explicit identifier

- GIVEN a capability requires `workspace:resource:read`
- WHEN authorization is evaluated
- THEN the platform MUST check that exact identifier
- AND it MUST NOT expand through implicit hierarchy

#### Scenario: Unauthenticated media proxy request is denied (SEC-001)

- GIVEN `/api/media/proxy` is NOT in the public allowlist
- WHEN an unauthenticated request targets `/api/media/proxy`
- THEN the system MUST return 401 Unauthorized
- AND the request MUST NOT reach the media-proxy handler

#### Scenario: Only explicit allowlist endpoints are public

- GIVEN the public allowlist in `IdentitySecurityConfiguration`
- WHEN an endpoint not on that list receives an unauthenticated request
- THEN the request MUST be rejected (401)
- AND an endpoint explicitly on the list MAY be reached without authentication

#### Scenario: Deny overrides role-based allow

- GIVEN a principal has a role allowing the permission
- AND a direct DENY grant exists for the same permission
- WHEN authorization is evaluated
- THEN the platform MUST deny access

#### Scenario: Scope reduces but never creates

- GIVEN a principal has the base permission `workspace:resource:read`
- AND a scope narrows the allowed target set
- WHEN authorization is evaluated for a target outside the scope
- THEN the platform MUST deny access
- AND a principal lacking the base permission MUST be denied even if scope data exists

#### Scenario: Entitlement gates feature access independently

- GIVEN a principal has the required permission
- BUT the workspace is not entitled to the feature
- WHEN the capability is evaluated
- THEN the platform MUST deny access
- AND the denial MUST be attributable to missing entitlement, not missing permission

### Requirement: Credential Mechanisms

The system MUST support the following authentication mechanisms in phase one:

1. **JWT for USER principals**: Short-lived access tokens for protected API access, obtained
   through login or refresh. Frontend MUST hold the access token in memory only.
2. **Refresh credentials**: HttpOnly, Secure, SameSite cookie for local USER session
   continuation. Validated against authoritative backend state. Support authoritative
   invalidation via logout.
3. **Service-account bearer credentials**: Validated against authoritative backend state.
   Support revocation enforcement.
4. **API keys**: Validated through lookup + verifier comparison. Support one narrow
   replacement capability with predecessor/successor semantics and no-overlap cutover.
5. **Email verification gating**: Refresh credential issuance and protected features gated
   behind `email_status = VERIFIED`.

#### Scenario: JWT materializes an authenticated USER principal

- GIVEN a request includes a valid JWT
- WHEN authenticated
- THEN the USER principal is materialized through repo-local seams

#### Scenario: Logout invalidates refresh credential

- GIVEN an active refresh-backed session
- WHEN logout completes
- THEN the backend invalidates the refresh credential in authoritative state
- AND later refresh attempts are denied

#### Scenario: API key replacement cutover

- GIVEN an active API key is replaced
- WHEN replacement completes
- THEN the successor key is accepted
- AND the predecessor key is denied
- AND no overlap window exists

#### Scenario: Refresh denied for unverified email

- GIVEN a refresh request with valid credential
- AND the user's `email_status` is `UNVERIFIED`
- WHEN the backend evaluates
- THEN the system MUST deny with 403
- AND the error MUST indicate email verification required

### Requirement: User Identity PII Anonymization

The Identity context MUST support anonymization of PII fields in `user_identities` (`email`,
`username` → `[REDACTED on {timestamp}]`) and `principals.display_identity` (→ `[REDACTED]`). The
operation MUST be idempotent: calling anonymize on an already-anonymized record MUST NOT fail.

#### Scenario: Anonymize replaces PII fields

- GIVEN a `user_identities` row with `email = "user@example.com"` and `username = "johndoe"`
- WHEN `anonymizePii(principalId)` is called
- THEN `email` MUST be `[REDACTED on 2026-07-19T12:00:00Z]`
- AND `username` MUST be `[REDACTED on 2026-07-19T12:00:00Z]`
- AND `principals.display_identity` MUST be `[REDACTED]`

#### Scenario: Double anonymization is idempotent

- GIVEN a `user_identities` row already anonymized
- WHEN `anonymizePii(principalId)` is called again
- THEN the operation MUST succeed without error

### Requirement: PII Correction Through CQRS

The Identity context MUST expose a `CorrectUserIdentityCommand` handler for `email` and `username`.
Validation: `email` MUST match RFC 5322; `username` MUST be 3–50 alphanumeric characters. The
handler MUST return old values as a snapshot for rollback.

#### Scenario: Correction validates email format

- GIVEN a CORRECTION request with `email = "not-an-email"`
- WHEN validation is applied
- THEN the system MUST reject with `invalid_email`

#### Scenario: Correction returns old values

- GIVEN a CORRECTION from `"old@x.com"` to `"new@x.com"`
- WHEN the handler completes
- THEN it MUST return a snapshot containing `"old@x.com"`

### Requirement: Governance and Explainability

The system MUST provide auditability for security-relevant platform actions. The proving slice
MUST produce runtime audit-ready proof for allow and deny outcomes. Authorization outcomes MUST
be attributable to explicit platform facts (membership, role permissions, direct grants, denials,
scopes, entitlements). Equivalent state MUST yield equivalent outcomes.

#### Scenario: Denial is explainable

- GIVEN a principal is denied the proving capability
- AND the principal has the base permission but the scope excludes the target
- WHEN the outcome is examined
- THEN the denial MUST be attributable to scope reduction
- AND it MUST be distinguishable from a denial caused by missing base permission

#### Scenario: Equivalent state yields equivalent outcome

- GIVEN two identical requests against equivalent state
- WHEN authorization is resolved
- THEN the platform MUST produce the same outcome for both

### Requirement: Phase One Proving Slice

| Endpoint                                                | What it proves                                                                                                  |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `GET /api/authorization/workspace-access/current`       | Combined permissions + entitlements + direct grants + denials for USER, SERVICE_ACCOUNT, and API_KEY principals |
| `GET /api/authorization/resources/{resourceId}/preview` | Target-aware scope reduction for workspace-scoped authorization                                                 |
| `POST /api/auth/login`                                  | JWT issuance + refresh credential creation                                                                      |
| `POST /api/auth/refresh`                                | Refresh credential validation + access token renewal                                                            |
| `POST /api/auth/logout`                                 | Authoritative refresh credential invalidation                                                                   |
| `POST /api/auth/register`                               | User registration + domain event emission                                                                       |
| `GET /api/auth/verify-email`                            | Email verification token consumption                                                                            |
| `POST /api/auth/resend-verification`                    | Email verification resend                                                                                       |
| API key replacement                                     | Predecessor/successor cutover without overlap                                                                   |

The proving slice MUST validate the platform model end-to-end without requiring full platform
breadth. All endpoints MUST enforce deny-by-default, explicit-over-implicit, and deterministic
authorization behavior. Stateless scaling, safe caching, and equivalent-node behavior MUST be
preserved across all proving-slice endpoints.

### Requirement: Existing Authentication Remains Available

Registration availability MUST NOT alter login, refresh, or authentication of existing users in
either configuration state.

#### Scenario: Existing user authenticates while registration is disabled

- GIVEN registration is disabled and an existing user has valid credentials
- WHEN the user logs in and subsequently refreshes the session
- THEN login and refresh MUST follow their existing successful contracts

#### Scenario: Existing user authenticates while registration is enabled

- GIVEN registration is enabled and an existing user has valid credentials
- WHEN the user logs in
- THEN authentication MUST follow its existing successful contract

## Out of Scope

The following capabilities are explicitly deferred beyond phase one:

- SYSTEM, INTEGRATION, and AGENT principal executable behavior
- GLOBAL, USER, and SYSTEM resource contexts (model-only)
- Generic scope engines, wildcard matching, inheritance
- Full governance workflows, audit persistence, compliance dashboards
- Broad credential management APIs (inventory, CRUD, search)
- Service-account rotation or replacement
- Dual-active rollover windows or grace periods
- Package, billing, subscription, or quota modeling
- Multi-workspace batch evaluation or automatic workspace switching
- Generalized deny-rule subsystem beyond direct DENY grants
- Full RBAC-to-ABAC policy engine

---

## Media Governance Permission Additions

The following requirements were added as part of the media copyright takedown change
(archived `2026-07-22`).

### Requirement: Governance Media Permissions

The permission registry MUST register two new `PermissionKey` entries:
`workspace:governance:media-read` and `workspace:governance:media-takedown`. Both SHALL follow the
`<domain>:<resource>:<action>` format established by the existing IAM platform, with dashes for
multi-word actions.

| Permission Key                        | Default Roles              | Purpose                  |
|---------------------------------------|----------------------------|--------------------------|
| `workspace:governance:media-read`     | `OWNER`, `ADMIN`, `MEMBER` | View takedown reports    |
| `workspace:governance:media-takedown` | `OWNER`, `ADMIN`           | Approve/reject takedowns |

(Previously: no `governance:media` permission keys existed.)

#### Scenario: Permission keys registered in PermissionRegistry

- GIVEN the IAM platform starts
- WHEN the permission registry initializes
- THEN `workspace:governance:media-read` SHALL be a valid permission key
- AND `workspace:governance:media-takedown` SHALL be a valid permission key

#### Scenario: Takedown action gated by permission

- GIVEN a user with `workspace:governance:media-read` but NOT `workspace:governance:media-takedown`
- WHEN calling `POST .../approve` or `POST .../reject` on a takedown report
- THEN the `GovernanceAuthorizationService` SHALL deny access
- AND the endpoint SHALL return `403 Forbidden`

#### Scenario: Owner/Admin can take takedown action

- GIVEN a user with role `OWNER` or `ADMIN` in the workspace
- WHEN calling `POST .../approve` or `POST .../reject` on a takedown report
- THEN the `GovernanceAuthorizationService` SHALL grant access
- AND the action SHALL proceed
