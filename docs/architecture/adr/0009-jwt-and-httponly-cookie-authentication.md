# ADR-0009: JWT & HttpOnly Cookie Authentication

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Security
- Supersedes: None
- Superseded by: None

## Context

Profile Tailors requires a secure, stateless authentication mechanism that is resilient to
Cross-Site Scripting (XSS) and Cross-Site Request Forgery (CSRF).

## Decision drivers

- Security (preventing token theft via JS).
- Statelessness (scale the backend without session affinity).
- SPA Support (seamless experience for Vue dashboard).

## Decision

Authentication MUST use a dual-token strategy:

1. **Access Token (JWT)**: Short-lived (e.g., 15 min). Carried in the `Authorization: Bearer`
   header. MUST be kept only in memory on the frontend (never in LocalStorage/SessionStorage).
2. **Refresh Token**: Long-lived. Carried in a secure, **HttpOnly**, **SameSite=Lax** (or Strict)
   cookie. MUST be used only at the dedicated `/api/auth/refresh` endpoint.

The backend MUST support a "Silent Refresh" flow where the frontend can obtain a new Access Token
without user interaction as long as the Refresh cookie is valid.

## Scope and boundaries

- `com.profiletailors.smp.identity`: Issue and validate tokens.
- `apps/web/app`: Manage memory-based token storage and silent refresh logic.

## Alternatives considered

### LocalStorage Token Storage

- Disadvantages: Vulnerable to XSS.
- Reason rejected: Security risk is too high for a platform handling social media credentials.

## Consequences

### Positive

- High security posture.
- Seamless user experience after browser restarts (via Refresh cookie).

### Negative

- Complexity in frontend (silent refresh logic).
- Complexity in backend (managing refresh session state).

## Compliance and enforcement

- Security reviews of authentication handlers.
- Automated tests for HttpOnly cookie presence.

## Verification

- `RefreshSessionCookieFactory.kt` issues cookies with `httpOnly = true` (lines 16 and 26, confirmed
  by tests in `RefreshSessionCookieFactoryTest.kt` at line 31).
- Vue `auth.ts` keeps access tokens in memory only (line 59: `_accessToken` ref) and never persists
  them to `localStorage`.

## Migration or remediation

None required; already implemented.

## Revisit conditions

- Discovery of a vulnerability in the current token strategy.
- Move to a third-party Identity Provider (e.g., Auth0, Clerk) that enforces a different model.
