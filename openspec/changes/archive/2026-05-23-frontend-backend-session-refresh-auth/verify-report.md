# Verify Report — frontend-backend-session-refresh-auth

**Date:** 2026-05-23  
**Verdict:** ✅ PASS WITH WARNINGS  
**Method:** Static analysis — all implementation files inspected, no live test execution

---

## Summary

The implementation fully satisfies the spec and design. All three spec areas (credentials,
identity, platform) pass. Two warnings are raised: one security posture note (Secure flag
defaults to false) and one integration test inconsistency (expects a hardcoded email in a
dynamic test). No blocking issues.

---

## Requirements Verification

### Credentials Spec (`specs/credentials/spec.md`)

| Requirement                                                                                                                                  | Status     | Evidence                                                                                                                         |
|----------------------------------------------------------------------------------------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------------------------------------------|
| `refresh_sessions` table with `id`, `principal_id`, `lookup_key`, `token_verifier`, `expires_at`, `created_at`, `last_used_at`, `revoked_at` | ✅ PASS     | `credentials/003-create-refresh-sessions.yaml` — all columns present                                                             |
| Migration included in master changelog                                                                                                       | ✅ PASS     | `db.changelog-master.yaml` line 13 includes the file                                                                             |
| `lookup_key` + `token_verifier` separation (opaque token pattern)                                                                            | ✅ PASS     | `RefreshSessionGateway` interface, `R2dbcRefreshSessionGateway`, `BCryptRefreshTokenHasher`                                      |
| BCrypt used for `token_verifier`                                                                                                             | ✅ PASS     | `BCryptRefreshTokenHasher.hash()` calls `BCrypt.hashpw` + `gensalt()`                                                            |
| Cookie: HttpOnly                                                                                                                             | ✅ PASS     | `RefreshSessionCookieFactory.buildSetCookie()` calls `.httpOnly(true)`                                                           |
| Cookie: SameSite configurable                                                                                                                | ✅ PASS     | `properties.sameSite` from config; defaults to `Lax`                                                                             |
| Cookie: Secure configurable                                                                                                                  | ⚠️ WARNING | Default is `false` in `application.yaml`. Acceptable for local dev, but production **must** set `SMP_REFRESH_COOKIE_SECURE=true` |
| Cookie path = `/api/auth`                                                                                                                    | ✅ PASS     | Default `SMP_REFRESH_COOKIE_PATH=/api/auth`                                                                                      |
| Cookie name = `pt_refresh`                                                                                                                   | ✅ PASS     | Default `SMP_REFRESH_COOKIE_NAME=pt_refresh`                                                                                     |
| Token rotation on refresh (revoke old, create new)                                                                                           | ✅ PASS     | `RefreshSessionLifecycleService.rotate()` revokes current session and creates replacement                                        |
| `revoke()` on logout                                                                                                                         | ✅ PASS     | `RefreshSessionLifecycleService.revoke()` called from `LogoutUserSessionHandler`                                                 |
| TTL = 7 days (604800s)                                                                                                                       | ✅ PASS     | `SMP_REFRESH_TTL_SECONDS:604800` in `application.yaml`                                                                           |
| `RefreshSessionNotActiveException` for invalid/expired token                                                                                 | ✅ PASS     | `RefreshSessionGateway.requireActive()` throws exception                                                                         |

---

### Identity Spec (`specs/identity/spec.md`)

| Requirement                                                          | Status | Evidence                                                                                                        |
|----------------------------------------------------------------------|--------|-----------------------------------------------------------------------------------------------------------------|
| Access token stored in memory only (Vue `ref`, never localStorage)   | ✅ PASS | `_accessToken` is `ref<string \| null>(null)` in `auth.ts`; no `localStorage` calls found                       |
| `hydrateSession()` calls `/api/auth/refresh` on app boot             | ✅ PASS | `auth.ts` — `hydrateSession()` calls `refreshSession()` which POSTs to `/api/auth/refresh`                      |
| `credentials: 'include'` on all fetch requests                       | ✅ PASS | `auth-api.ts` `request()` always sets `credentials: 'include'`                                                  |
| `createApiFetch()` performs exactly one silent 401 retry             | ✅ PASS | `auth-api.ts` lines 164–184: single `try/catch`, calls `onRefresh()`, then one retry — no loop, no second retry |
| On failed refresh after 401, calls `onUnauthenticated()` and rejects | ✅ PASS | `auth-api.ts` lines 176–179                                                                                     |
| `refreshSession()` returns `null` on 401 (not thrown)                | ✅ PASS | `auth-api.ts` lines 114–124                                                                                     |
| `logoutSession()` is fire-and-forget safe                            | ✅ PASS | `auth-api.ts` lines 130–136 — catches all errors                                                                |
| JWT TTL = 900s                                                       | ✅ PASS | `SMP_LOCAL_JWT_TTL_SECONDS:900` in `application.yaml`                                                           |
| JWT issuer is a valid URI                                            | ✅ PASS | Default `http://localhost/profiletailors-local` — valid URI format                                              |

---

### Platform Spec (`specs/platform/spec.md`)

| Requirement                                                                                      | Status | Evidence                                                                                                                              |
|--------------------------------------------------------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------------------------------|
| `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` all `permitAll` | ✅ PASS | `IdentitySecurityConfiguration.kt` line 77 — all four paths in `permitAll` rule                                                       |
| `RefreshSessionNotActiveException` maps to HTTP 401                                              | ✅ PASS | `PlatformProblemDetailsHandler` lines 33–35: `@ExceptionHandler(RefreshSessionNotActiveException::class)` → `HttpStatus.UNAUTHORIZED` |
| Logout returns 204 No Content                                                                    | ✅ PASS | `LocalAuthControllerTest` asserts `statusCode == 204`; integration test asserts `isNoContent`                                         |
| Logout clears cookie with `Max-Age=0`                                                            | ✅ PASS | `LocalAuthController` calls `buildClearCookie()` which sets `maxAge(Duration.ZERO)`                                                   |
| Logout is idempotent (revoke missing session does not throw)                                     | ✅ PASS | `LogoutUserSessionHandler` — swallows `RefreshSessionNotActiveException` on logout path                                               |
| Register 409 on duplicate email                                                                  | ✅ PASS | `LocalAuthHandlersTest` asserts `UserAlreadyExistsException`; `PlatformProblemDetailsHandler` maps it to 409                          |
| Login 401 on wrong password                                                                      | ✅ PASS | `LocalAuthHandlersTest` + integration test assert `isUnauthorized`                                                                    |

---

## Test Coverage

| Test                               | Scope                                                                  | Status    |
|------------------------------------|------------------------------------------------------------------------|-----------|
| `LocalAuthHandlersTest`            | Unit — register, login, refresh, duplicate rejection, invalid password | ✅ Present |
| `LocalAuthControllerTest`          | Unit — HTTP layer, cookie set/clear, command dispatch                  | ✅ Present |
| `LocalAuthEndpointIntegrationTest` | Integration — full register→login→refresh→logout flow with H2          | ✅ Present |

### Integration Test Warning

`returns current user profile for issued token` (line 97) registers with `owner@example.com`
but the assertion on line 106 hardcodes `.jsonPath("$.email").isEqualTo("owner@example.com")`.
This will pass because `registerAndExtract()` uses that email, but the test flow is slightly
fragile — email leaks from helper into assertion implicitly.

⚠️ **WARNING (non-blocking):** Prefer extracting expected email from `RegisterResult` to make
intent explicit.

---

## Warnings Summary

| #  | Severity | Location                                | Description                                                                                                                    |
|----|----------|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| W1 | MEDIUM   | `application.yaml` L30                  | `secure: false` is the default. Production deployments must set `SMP_REFRESH_COOKIE_SECURE=true` to enforce HTTPS-only cookie. |
| W2 | LOW      | `LocalAuthEndpointIntegrationTest` L106 | Hardcoded email assertion — minor fragility, not a correctness issue.                                                          |

---

## Verdict

**✅ PASS WITH WARNINGS**

- All spec requirements are satisfied.
- No CRITICAL or HIGH blockers.
- W1 is a deployment concern, not a code defect — must be addressed before production rollout via
  environment config.
- W2 is cosmetic and does not affect correctness.

**Recommended next step:** proceed to `sdd-archive`.
