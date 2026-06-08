# Proposal: Frontend + Backend Session Refresh Auth

## Intent

Replace the current browser-persisted access-token flow with a safer session architecture that keeps
the access token in memory, moves refresh continuity to an HttpOnly cookie, and lets the frontend
recover sessions through backend-driven refresh rather than `localStorage` token reuse.

Today the Vue app persists `accessToken` data in `localStorage` and reuses it directly for
`/api/auth/me`, which increases XSS blast radius and makes session bootstrap depend on long-lived
browser storage. This change introduces a narrow end-to-end session-refresh slice across
`apps/web/app` and `server/smp` so local auth can support secure session continuation, explicit
logout, and one automatic retry path for expired access tokens.

## Scope

### In Scope

- Add backend refresh and logout endpoints at `/api/auth/refresh` and `/api/auth/logout` for the
  local-auth path in `server/smp`.
- Change local register/login success handling so the backend issues an in-memory-consumable access
  token response plus a refresh token stored in a secure HttpOnly cookie.
- Replace frontend `localStorage` token persistence with an in-memory auth state, session bootstrap
  via refresh on app startup, and logout that clears both local state and backend refresh cookie
  state.
- Add a shared authenticated fetch wrapper in `apps/web/app` that sends credentials, retries once
  after a `401` by calling refresh, and then fails closed.
- Update existing auth/profile integration coverage to prove register/login, bootstrap-by-refresh,
  retry-on-401, and logout behavior.

### Out of Scope

- MFA, password reset, email verification, social login, device/session inventory, or broader
  session-management UX.
- Reworking the repo-wide IAM platform model, authorization rules, or workspace protection semantics
  beyond what is required to preserve the current authenticated user flow.
- Cross-tab session synchronization, silent background refresh scheduling, remember-me variations,
  or offline-first auth behavior.
- Generic token-provider abstraction for every future principal type; this change is scoped to the
  current local user auth flow first.

## Approach

Keep the existing local-auth endpoints and `/api/auth/me` contract as the proving surface, but shift
session continuity responsibilities.

On the backend, extend the local-auth flow so login and registration also mint a refresh credential
tied to authoritative server state, return the access token in the response body, and set the
refresh token as an HttpOnly cookie with secure/same-site settings appropriate for the app
environment. Add `/api/auth/refresh` to validate the cookie-backed refresh credential and issue a
fresh access token plus rotated or renewed refresh-cookie state. Add `/api/auth/logout` to
invalidate the current refresh credential and clear the cookie. Update Spring Security and
supporting identity/credential seams so `/api/auth/login`, `/api/auth/register`, and
`/api/auth/refresh` remain public entry points while `/api/auth/logout` and `/api/auth/me` honor the
new session behavior.

On the frontend, remove `localStorage` persistence from the Pinia auth store, keep the access token
only in memory, and bootstrap authenticated state by calling refresh during app startup or guarded
navigation when no access token is present but a refresh cookie may exist. Centralize HTTP calls in
a wrapper that includes `credentials: 'include'`, attaches the current in-memory bearer token when
present, and retries one time after a `401` by invoking refresh before replaying the original
request. Logout must call the backend endpoint first when possible, then clear client memory
regardless of remote outcome.

## Affected Areas

| Area                                                                                                                  | Impact                | Description                                                                                                                              |
|-----------------------------------------------------------------------------------------------------------------------|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `apps/web/app/src/stores/auth.ts`                                                                                     | Modified              | Remove `localStorage` token persistence, keep in-memory access token state, add bootstrap/logout/refresh coordination.                   |
| `apps/web/app/src/lib/auth-api.ts`                                                                                    | Modified              | Replace direct token-passing helpers with credential-aware request utilities, refresh/logout endpoints, and one-retry `401` handling.    |
| `apps/web/app/src/main.ts`                                                                                            | Modified              | Bootstrap session from refresh instead of rehydrating a persisted access token.                                                          |
| `apps/web/app/src/router/index.ts`                                                                                    | Modified              | Align route guards with refresh-backed session bootstrap and fail-closed unauthenticated redirects.                                      |
| `apps/web/app/src/App.vue`                                                                                            | Modified              | Use backend-backed logout flow and reflect refresh/bootstrap loading states if needed.                                                   |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt`               | Modified              | Extend login/register response flow and add `/api/auth/refresh` plus `/api/auth/logout` endpoints or supporting controller methods.      |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/CurrentUserProfileController.kt`      | Possibly Modified     | Keep `/api/auth/me` compatible with the refreshed access-token path and current frontend bootstrap flow.                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthApi.kt`                              | Modified              | Evolve auth application contracts beyond access-token-only issuance to support refresh-aware session operations.                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` | Modified              | Permit refresh endpoint access, enforce logout/session behavior, and wire any cookie/session auth support required by WebFlux security.  |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/**`                                                    | Modified/New          | Add refresh-token issuance, validation, rotation, revocation, and persistence seams for local user sessions.                             |
| `server/smp/src/main/resources/db/**`                                                                                 | Modified              | Add schema changes for refresh-token/session persistence if authoritative server-side refresh state is stored.                           |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt`                   | Modified              | Cover refresh cookie issuance, refresh endpoint behavior, logout invalidation, and protected-profile access after bootstrap.             |
| `apps/web/app/src/**/*.test.*` or frontend test setup                                                                 | Possibly Modified/New | Add focused tests for auth store bootstrap, retry-on-401, and logout cleanup if frontend test harness exists or is introduced minimally. |

## Scope Boundaries

This change is intentionally about session transport and lifecycle for the existing local user auth
path. It does not redefine the platform principal taxonomy, does not broaden authorization scope,
and does not introduce a general-purpose browser session framework. If a proposed implementation
step does not directly support in-memory access tokens, HttpOnly refresh cookies, refresh/logout
endpoints, or retry/bootstrap behavior, it belongs in a later change.

## Risks

| Risk                                                                                           | Likelihood | Mitigation                                                                                                                                          |
|------------------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| Cookie settings break local development due to `Secure`, `SameSite`, domain, or CORS mismatch  | High       | Define environment-aware cookie policy explicitly, verify on local dev origin pair early, and keep frontend requests consistently credentialed.     |
| Refresh endpoint or retry wrapper creates loops or duplicate refresh calls                     | Medium     | Enforce single retry per request, centralize refresh orchestration, and fail closed after one refresh attempt.                                      |
| Backend state model for refresh tokens grows into a broad session-management redesign          | Medium     | Limit persistence/contracts to the minimum local-user session fields required for issue, rotate/invalidate, and lookup.                             |
| Existing tests and frontend assumptions rely on access token presence in persisted storage     | High       | Update startup and route-guard behavior together, and extend integration coverage around bootstrap and logout before removing persistence.          |
| Logout leaves stale refresh credentials server-side or stale UI state client-side              | Medium     | Invalidate refresh state authoritatively on the backend and always clear client memory even if the network request fails.                           |
| `401` retry logic masks real authorization errors or causes surprising UX on non-auth failures | Medium     | Restrict retry to authentication failure paths only, preserve original error details after retry exhaustion, and avoid retry on unrelated statuses. |

## Rollback Plan

If the refresh-based session architecture causes instability, roll back the frontend and backend
changes together as one auth-session unit. Restore the current local-auth behavior where
login/register return access tokens consumed directly by the frontend store, remove the
refresh/logout endpoints and any refresh-token persistence/schema added for this change, and
re-enable the current `localStorage`-backed access-token bootstrap in
`apps/web/app/src/stores/auth.ts` and related request helpers. Because this change is scoped to the
local auth/session path, rollback should not require reverting broader authorization or tenancy
behavior beyond removing any new refresh-session records and cookie handling.

## Dependencies

- Existing local auth flow in `server/smp` for `/api/auth/register`, `/api/auth/login`, and
  `/api/auth/me`.
- Existing Vue/Pinia auth flow in `apps/web/app` that will be migrated away from `localStorage`.
- WebFlux/Spring Security support for cookie-aware refresh handling and any required CORS/credential
  configuration.
- Schema/runtime support for authoritative refresh-session persistence if server-side refresh
  revocation/rotation is implemented.

## Success Criteria

- [ ] The browser no longer persists access tokens in `localStorage`, `sessionStorage`, or
  equivalent durable frontend storage for the app auth flow.
- [ ] Successful register/login returns a usable access token to the SPA and sets a refresh token
  via HttpOnly cookie managed by the backend.
- [ ] App startup and guarded navigation can restore an authenticated session through
  `/api/auth/refresh` when only the refresh cookie remains available.
- [ ] Authenticated API requests use a shared wrapper that retries exactly once after a `401` by
  refreshing the session, then fails closed if refresh cannot recover.
- [ ] Logout clears client auth memory and invalidates the backend refresh session so a later
  bootstrap attempt without re-login is denied.
- [ ] Backend and frontend verification cover the happy path plus at least one denied/expired
  refresh path and one logout path.
