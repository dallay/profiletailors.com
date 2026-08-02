# Delta for IAM

## ADDED Requirements

### Requirement: Password Minimum Length Enforcement (SEC-009)

The system MUST enforce a 12-character minimum password length (ASVS L2 V2.1.1) at every enforcement layer: domain/application handlers (`LocalAuthHandlers`, `ResetPasswordHandler` via `MIN_PASSWORD_LENGTH = 12`), the HTTP contract (`LocalAuthController` `@Size(min=12)` + OpenAPI `minLength`), and BDD scenarios. Passwords of 11 characters or fewer MUST be rejected; 12+ MUST be accepted. The frontend `registerSchema` MUST mirror the reset schema with `min(12)` so the register form validates inline instead of surfacing a backend 422.

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

## MODIFIED Requirements

### Requirement: Authorization Semantics

The system MUST enforce the following authorization rules:

1. **Explicit permission format**: `<domain>:<resource>:<action>` — no implicit inheritance
2. **Role composition**: Roles are compositions of explicit permissions
3. **Direct grants and denials**: Support `ALLOW`/`DENY` effects with optional expiration
4. **Scopes reduce only**: Scopes narrow access; they MUST NEVER create or expand permissions
5. **Explicit deny overrides**: A direct `DENY` MUST override any `ALLOW` path
6. **Feature entitlements separate**: Permissions answer "may the principal act?"; entitlements answer "is the feature available to the workspace?"
7. **Deny by default**: Absence of an explicit allow path MUST result in denial
8. **Deterministic evaluation**: Equivalent requests against equivalent state MUST produce equivalent outcomes

(Previously: `/api/media/proxy` was included in the Spring Security `permitAll()` list — an unauthenticated media-proxy path existed outside the explicit allowlist. The allowlist no longer includes it.)

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

## TDD Requirement

Every scenario MUST have a failing-first test. In-tree tests: `ResetPasswordHandlerTest` (11-char rejection), BDD `registration.feature` (11 rejected / 12 accepted), `LocalAuthEndpointIntegrationTest` (12+ test passwords), BDD `security-endpoint-authorization.feature` (SEC-001 endpoint auth, 5 scenarios), app `schemas.test.ts` + `registration.spec.ts`. New regression required: ES password messages assert "12".
