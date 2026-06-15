# Tasks: Frontend + Backend Session Refresh Auth

## Review Workload Forecast

| Field                      | Value                                                                                                                                           |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Review budget              | 400 changed lines unless project config says otherwise                                                                                          |
| Estimated workload         | High                                                                                                                                            |
| Chained PRs recommended    | Yes                                                                                                                                             |
| Proposed delivery strategy | stacked-prs                                                                                                                                     |
| Work-unit balance          | Slice 1 backend refresh-session foundation, slice 2 backend auth endpoints/security/tests, slice 3 frontend session bootstrap/retry/tests/build |

## Phase 1: Backend Refresh-Session Foundation

- [ ] 1.1 Add `refresh_sessions` schema in
  `server/smp/src/main/resources/db/changelog/credentials/003-create-refresh-sessions.yaml` and
  include it from `db.changelog-master.yaml`.
- [ ] 1.2 Add refresh-session models/contracts in
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/RefreshSessionModels.kt`
  and `RefreshSessionGateway.kt` for create/find/rotate/revoke flows.
- [ ] 1.3 Implement token/cookie support in `RefreshSessionTokenService.kt`,
  `RefreshSessionCookieFactory.kt`, and `BCryptRefreshTokenHasher.kt` with explicit set/clear
  semantics.
- [ ] 1.4 Implement persistence and lifecycle wiring in `R2dbcRefreshSessionGateway.kt`,
  `RefreshSessionLifecycleService.kt`, and
  `identity/infrastructure/IdentityBootstrapConfiguration.kt`.
- [ ] 1.5 Add refresh-session config in `server/smp/src/main/resources/application.yaml` for cookie
  name/path, `SameSite`, secure strategy, and TTLs.

## Phase 2: Backend Auth Flow and HTTP Integration

- [ ] 2.1 Extend
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthApi.kt` with
  refresh/logout commands and session-auth response contracts.
- [ ] 2.2 Update `LocalAuthHandlers.kt` so register/login issue refresh sessions, refresh rotates
  them, and logout revokes them idempotently.
- [ ] 2.3 Update `identity/infrastructure/http/LocalAuthController.kt` to set refresh cookies on
  login/register, add `POST /api/auth/refresh`, and clear cookies on denied refresh/logout.
- [ ] 2.4 Adjust `identity/infrastructure/security/IdentitySecurityConfiguration.kt` so
  `/api/auth/refresh` stays public, `/api/auth/logout` supports cookie-backed logout, and SPA
  credentials/CORS remain compatible.
- [ ] 2.5 Verify `CurrentUserProfileController.kt` still consumes bearer JWTs unchanged after
  refresh-driven reissuance.

## Phase 3: Frontend Session Cutover

- [ ] 3.1 Refactor `apps/web/app/src/stores/auth.ts` to remove durable token persistence, own the
  access token in memory only, and add `bootstrapSession`, `refreshSession`, `logout`, and
  `clearSession` fail-closed flows.
- [ ] 3.2 Rework `apps/web/app/src/lib/auth-api.ts` and, if needed, add `src/lib/session-client.ts`
  for credentialed requests, shared in-flight refresh dedupe, and single `401` replay.
- [ ] 3.3 Update `apps/web/app/src/main.ts` and `src/router/index.ts` so startup/guards attempt
  refresh bootstrap before redirecting anonymous users.
- [ ] 3.4 Update `apps/web/app/src/views/AuthView.vue` and `src/App.vue` to rely on backend
  cookie-backed login/logout flow and reflect bootstrap/logout busy states.

## Phase 4: Verification and Regression Coverage

- [ ] 4.1 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`
  for login/register issuance, valid refresh rotation, denied refresh, and idempotent logout
  scenarios.
- [ ] 4.2 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthControllerTest.kt`
  for Set-Cookie metadata, clear-cookie behavior, and refresh/logout endpoint mappings.
- [ ] 4.3 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt`
  for login/register cookie issuance, bootstrap-by-refresh, denied/expired refresh, logout
  invalidation, and `/api/auth/me` with refreshed JWT.
- [ ] 4.4 Add frontend auth regression tests in `apps/web/app/src/stores/auth.spec.ts` or
  `src/lib/auth-api.spec.ts`; update `apps/web/app/package.json` only if a minimal test harness is
  required.
- [ ] 4.5 Run backend build/tests and frontend build/tests for the auth slice, plus manual local-dev
  verification of cookie/CORS/reload/logout behavior across SPA and API origins.
