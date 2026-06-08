# Design: Frontend + Backend Session Refresh Auth

## Technical Approach

Implement the session-refresh slice by extending the existing local-auth path in `server/smp` and
the current Pinia-based auth flow in `apps/web/app` without introducing a second auth stack.

The backend already issues JWT access tokens through `LocalJwtIssuer`, materializes USER principals
from JWTs, and exposes `/api/auth/register`, `/api/auth/login`, and `/api/auth/me`. This change
keeps that model for protected API access, but adds a second credential type for browser session
continuity:

- short-lived JWT access token returned in JSON and held only in frontend memory
- opaque refresh token transported only through an HttpOnly cookie
- authoritative refresh-session persistence in PostgreSQL, validated on refresh and invalidated on
  logout

The frontend keeps using the Pinia auth store as the orchestration point, but removes `localStorage`
persistence. App bootstrap changes from “rehydrate access token from durable storage” to “attempt
`/api/auth/refresh` once, then either establish session in memory or fail closed”. Authenticated
requests are centralized behind one credential-aware fetch wrapper that includes cookies, attaches
the in-memory bearer token, and performs at most one refresh-triggered replay after `401`.

This design maps directly to the proposal and delta specs:

- `identity`: access token only in memory, bootstrap via refresh, one retry after `401`
- `credentials`: refresh credential distinct from access token, server-authoritative,
  cookie-transported
- `platform`: dedicated `/api/auth/refresh` and `/api/auth/logout`, deterministic fail-closed
  behavior

## Architecture Decisions

### Decision: Use opaque, stateful refresh credentials instead of self-contained refresh JWTs

**Choice**: Persist refresh-session records in the backend and issue an opaque random refresh token
whose hash is stored server-side. The browser only receives the opaque token via cookie.

**Alternatives considered**:

- Self-contained refresh JWT validated only by signature and claims
- Long-lived access JWT with no separate refresh credential
- Spring WebSession / server session as the main browser auth mechanism

**Rationale**: The new specs require authoritative invalidation, logout enforcement, and
deterministic node-independent refresh evaluation. A stateful opaque refresh credential matches
existing credential patterns already present in `credentials` (`ApiKeyCredentialStateLookup`,
`ServiceAccountCredentialStateLookup`) and gives immediate support for revocation, expiry, rotation,
and deny-after-logout semantics. A refresh JWT would reintroduce revocation complexity and drift
toward token-trust instead of authoritative state.

### Decision: Keep protected API authentication JWT-first; do not authenticate business APIs from cookies

**Choice**: `/api/auth/refresh` consumes the refresh cookie and returns a new JWT access token; all
protected APIs, including `/api/auth/me`, continue to require bearer access tokens.

**Alternatives considered**:

- Accept refresh cookies directly on protected APIs
- Add cookie-based Spring Security auth for all browser requests
- Replace JWT access-token protection with backend session lookup middleware

**Rationale**: The current backend is already built around JWT resource-server authentication and
`JwtAuthenticatedPrincipalMaterializer`. Keeping `/api/auth/me` and the protected slice bearer-based
avoids reworking `IdentitySecurityConfiguration`, preserves the existing principal-materialization
seam, and confines cookie logic to the dedicated session-continuation endpoints required by the
spec.

### Decision: Rotate refresh credentials on every successful refresh

**Choice**: Successful `/api/auth/refresh` invalidates the current refresh-session record and issues
a new refresh token + cookie together with the new access token.

**Alternatives considered**:

- Reuse the same refresh token until expiry
- Rotate only on login/register, not on refresh
- Sliding expiry with stable token identifier

**Rationale**: Rotation reduces replay window if a refresh cookie leaks through non-XSS channels and
makes refresh continuity explicitly stateful. Because the platform already treats credential
validity as authoritative backend state, “one active refresh credential per browser session lineage”
fits the repo’s current credential semantics well. It also makes logout and revoked-session behavior
easier to reason about in tests.

### Decision: Model refresh credentials as a dedicated credentials sub-slice, not as ad hoc fields inside identity

**Choice**: Put refresh-session issuance/validation/persistence contracts under
`server/smp/src/main/kotlin/com/profiletailors/smp/credentials/**`, while identity handlers
orchestrate their use.

**Alternatives considered**:

- Put everything inside `identity.application`
- Store refresh state directly in `user_identities`
- Create a separate Gradle module now

**Rationale**: Existing code already treats credential validation/state as a dedicated bounded
context. Refresh tokens are credentials, not identity facts. Keeping them in `credentials` aligns
with current architecture and reduces the risk that browser-session logic leaks into profile
identity concerns.

### Decision: Frontend access token remains store-owned and in-memory only

**Choice**: The Pinia auth store remains the single runtime owner of the current access token and
user session state; no `localStorage`, `sessionStorage`, IndexedDB, or cookie fallback is used for
the access token.

**Alternatives considered**:

- Persist access token in `sessionStorage`
- Keep current `localStorage` approach and add refresh only as backup
- Put token in a non-HttpOnly cookie

**Rationale**: The change exists specifically to reduce XSS blast radius and remove durable browser
token reuse. Store-owned memory state is the smallest change to the existing Vue architecture while
satisfying the new identity requirements.

### Decision: Centralize 401 recovery in a shared request wrapper with in-flight refresh deduplication

**Choice**: Replace direct `fetch` usage in `auth-api.ts` with a small authenticated request layer
that:

- always sends `credentials: 'include'`
- reads the current access token lazily from the auth store/session manager
- retries exactly once per original request after one refresh attempt
- reuses a shared in-flight refresh promise so concurrent `401`s do not stampede `/api/auth/refresh`

**Alternatives considered**:

- Each store/view handles its own refresh-and-retry logic
- Global fetch monkey-patch
- Background timer-based silent refresh before expiry

**Rationale**: The spec requires exactly one retry path and loop prevention. Centralization is the
safest way to enforce that rule, and deduplication prevents duplicate refreshes when multiple
protected calls fail simultaneously after token expiry.

### Decision: Logout is best-effort remote, always-authoritative local cleanup

**Choice**: Frontend logout calls `/api/auth/logout` when possible, but clears in-memory auth state
regardless of remote outcome. Backend logout always clears the refresh cookie, and invalidates the
presented refresh session if one exists.

**Alternatives considered**:

- Fail logout if backend is unavailable
- Clear only local state and skip backend invalidation
- Require valid access token to log out

**Rationale**: The proposal explicitly requires local cleanup even on remote failure. Backend
invalidation remains authoritative when reachable; client cleanup avoids stale UI/auth state.

### Decision: Add explicit auth-session configuration properties for cookie policy and TTLs

**Choice**: Extend `application.yaml` with a dedicated refresh-session property group for cookie
name, refresh TTL, secure flag strategy, same-site policy, and optional domain/path overrides.

**Alternatives considered**:

- Hard-code cookie values in controller code
- Reuse `local-jwt` settings for refresh behavior
- Infer cookie policy entirely from request origin at runtime

**Rationale**: The proposal identifies local-dev cookie/CORS mismatch as a high risk. Explicit
configuration makes environment-specific behavior testable and reviewable, especially for local HTTP
development vs HTTPS deployment.

## Data Flow

### 1. Login / Register success

```text
Browser (AuthView)
   │ POST /api/auth/login or /register
   ▼
LocalAuthController
   │ mediator.send(...)
   ▼
RegisterUserHandler / LoginUserHandler
   │ verify or create user + password credential
   │ issue short-lived access JWT via LocalJwtIssuer
   │ create refresh session via credentials service
   ▼
HTTP response
   ├─ JSON body: { accessToken, tokenType, expiresIn, principalId, email, username }
   └─ Set-Cookie: pt_refresh=opaque-token; HttpOnly; SameSite=...; Secure=...; Path=/api/auth
   ▼
Pinia auth store
   ├─ stores access token in memory only
   └─ fetches /api/auth/me with bearer token
```

### 2. App bootstrap with no access token in memory

```text
SPA startup
   │
   ├─ auth store has no access token in memory
   │
   └─ bootstrapSession()
         │ POST /api/auth/refresh with credentials: include
         ▼
      Backend validates refresh cookie against DB state
         │
         ├─ success -> rotates refresh session, returns new access JWT, sets new cookie
         │             frontend stores token in memory, then GET /api/auth/me
         │
         └─ failure -> clears local state, remains anonymous
```

### 3. Protected request with expired access token

```text
Component / store
   │
   ▼
authenticatedFetch(request, retry=false)
   │ sends Authorization: Bearer <memory token>
   ▼
Protected API returns 401
   │
   ├─ if retry already used -> fail closed
   │
   └─ else await refreshOnce()
          │ POST /api/auth/refresh with cookie
          │
          ├─ success -> store new access token in memory
          │             replay original request exactly once
          │
          └─ failure -> clear auth state, propagate original auth failure
```

### 4. Logout

```text
Browser logout action
   │ POST /api/auth/logout with credentials: include
   ▼
Backend
   ├─ if refresh cookie maps to active session -> mark revoked/logout timestamp
   └─ always send clearing Set-Cookie for refresh token
   ▼
Frontend
   └─ always clears in-memory token + user state and redirects to /login
```

### Sequence diagrams

#### Refresh bootstrap sequence

```text
SPA            AuthStore        /api/auth/refresh      RefreshSessionService        DB
 |                 |                    |                        |                   |
 | app start       |                    |                        |                   |
 |---------------> | bootstrapSession() |                        |                   |
 |                 |------------------->|                        |                   |
 |                 |   cookie only      | validate current token |                   |
 |                 |                    |----------------------->| lookup active     |
 |                 |                    |                        |------------------>|
 |                 |                    |                        |<------------------|
 |                 |                    | rotate + issue JWT     |                   |
 |                 |                    |<-----------------------|                   |
 |                 |<-------------------| 200 + Set-Cookie + JWT |                   |
 |                 | store token        |                        |                   |
 |                 | GET /api/auth/me   |                        |                   |
```

#### Single retry after 401

```text
Caller          RequestWrapper      Protected API      /api/auth/refresh      AuthStore
 |                    |                  |                    |                  |
 | request()          |                  |                    |                  |
 |------------------->|----------------->|                    |                  |
 |                    |   bearer token   |                    |                  |
 |                    |<-----------------| 401                |                  |
 |                    | refreshOnce()    |                    |                  |
 |                    |-------------------------------------->|                  |
 |                    |                                      | 200 / 401         |
 |                    |<--------------------------------------|                  |
 |                    | if 200 update token -----------------------------------> |
 |                    | replay once ---->|                    |                  |
 |                    |<-----------------| 200 / 401          |                  |
 |<-------------------| result/failure    |                    |                  |
```

## File Changes

| File                                                                                                                  | Action                                   | Description                                                                                                                                      |
|-----------------------------------------------------------------------------------------------------------------------|------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `openspec/changes/frontend-backend-session-refresh-auth/design.md`                                                    | Create                                   | Technical design for the session-refresh auth change.                                                                                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthApi.kt`                              | Modify                                   | Evolve response/contracts from access-token-only auth to session auth response plus refresh/logout commands.                                     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt`                         | Modify                                   | Orchestrate refresh-session issuance during register/login and add refresh/logout handlers.                                                      |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt`               | Modify                                   | Set refresh cookie on login/register, add `/refresh` and `/logout` endpoints, and clear cookie on logout/denied refresh.                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/CurrentUserProfileController.kt`      | Possibly modify                          | No contract change expected; may only need annotations/imports if auth docs or response handling are updated.                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` | Modify                                   | Permit `/api/auth/refresh` publicly and allow `/api/auth/logout` under session rules; optionally enable CORS/credentials support for SPA origin. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityBootstrapConfiguration.kt`         | Modify                                   | Wire refresh-session services, token generator/verifier, cookie settings config, and hashing utilities.                                          |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/LocalJwtConfiguration.kt`         | Possibly modify                          | Keep JWT issuance/validation separate; may add no changes unless helper beans are colocated.                                                     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/RefreshSessionGateway.kt`                  | Create                                   | Persistence contract for creating, rotating, finding, revoking refresh sessions.                                                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/RefreshSessionTokenService.kt`             | Create                                   | Generates opaque refresh tokens, hashes them for persistence, and parses token identifiers if split format is used.                              |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/RefreshSessionCookieFactory.kt`            | Create                                   | Builds Set-Cookie values from configuration for set/clear operations.                                                                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/RefreshSessionLifecycleService.kt`         | Create                                   | Application service for issue/refresh/logout lifecycle decisions and rotation logic.                                                             |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/RefreshSessionModels.kt`                   | Create                                   | Domain/application data classes for active session, refresh result, logout result, status enums.                                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcRefreshSessionGateway.kt`          | Create                                   | R2DBC adapter for authoritative refresh-session persistence.                                                                                     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/BCryptRefreshTokenHasher.kt` or similar | Create                                   | Hash/verify opaque refresh token secret before persistence lookup validation.                                                                    |
| `server/smp/src/main/resources/application.yaml`                                                                      | Modify                                   | Add refresh-session TTL and cookie policy settings; possibly SPA origin/CORS settings.                                                           |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`                                                 | Modify                                   | Include new credentials changelog for refresh sessions.                                                                                          |
| `server/smp/src/main/resources/db/changelog/credentials/003-create-refresh-sessions.yaml`                             | Create                                   | Add refresh-session persistence table and indexes.                                                                                               |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`                     | Modify                                   | Cover refresh-session issuance, rotation, and logout behavior at handler level.                                                                  |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthControllerTest.kt`           | Modify                                   | Assert command dispatch plus cookie-setting/clearing behavior and endpoint mapping.                                                              |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt`                   | Modify                                   | End-to-end verify login/register cookie issuance, `/refresh`, `/logout`, denied refresh, and `/me` with refreshed token.                         |
| `apps/web/app/src/lib/auth-api.ts`                                                                                    | Modify                                   | Replace raw request helper with credential-aware request wrapper, refresh endpoint, logout endpoint, and single-retry protected fetch.           |
| `apps/web/app/src/stores/auth.ts`                                                                                     | Modify                                   | Remove durable token persistence; keep memory-only session state; add bootstrap, refresh, logout, and fail-closed cleanup flows.                 |
| `apps/web/app/src/main.ts`                                                                                            | Modify                                   | Await or at least trigger refresh-backed bootstrap instead of `localStorage` hydration.                                                          |
| `apps/web/app/src/router/index.ts`                                                                                    | Modify                                   | Route guards wait for session bootstrap when needed and redirect only after bootstrap fails.                                                     |
| `apps/web/app/src/App.vue`                                                                                            | Modify                                   | Use async backend-backed logout and optionally reflect bootstrap/logout busy state.                                                              |
| `apps/web/app/src/views/AuthView.vue`                                                                                 | Modify                                   | Continue login/register UX, but rely on in-memory session establishment and backend cookie flow.                                                 |
| `apps/web/app/src/lib/session-client.ts`                                                                              | Create                                   | Optional extraction for token holder + refresh dedupe if `auth-api.ts` becomes too crowded.                                                      |
| `apps/web/app/src/stores/auth.spec.ts` or `src/lib/auth-api.spec.ts`                                                  | Create if frontend test harness is added | Focused tests for bootstrap, 401 retry, and logout cleanup.                                                                                      |
| `apps/web/app/package.json`                                                                                           | Possibly modify                          | Add minimal frontend test dependency/script only if required to cover auth behavior.                                                             |

## Interfaces / Contracts

### Backend HTTP contracts

#### Existing login/register response shape remains script-consumable

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "principalId": "user-123",
  "email": "user@example.com",
  "username": "user"
}
```

Response headers also set:

```http
Set-Cookie: pt_refresh=<opaque>; HttpOnly; Path=/api/auth; SameSite=Lax; Secure
```

#### `POST /api/auth/refresh`

Request:

- no JSON body required
- consumes refresh cookie automatically
- no access token required

Success `200`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "principalId": "user-123",
  "email": "user@example.com",
  "username": "user"
}
```

Also sets rotated refresh cookie.

Failure `401`:

- standard problem-details-style auth error body
- clearing refresh cookie is recommended on invalid/expired/revoked refresh to converge browser
  state with backend state

#### `POST /api/auth/logout`

Request:

- no JSON body required
- accepts refresh cookie if present
- access token not required for refresh-backed logout semantics

Success `204 No Content` preferred, with clearing cookie header.

Fallback behavior:

- if no valid refresh session exists, backend still returns a successful clearing response or a
  deterministic deny depending on chosen controller policy
- recommended implementation: idempotent `204` + clear cookie to simplify frontend logout and avoid
  stale cookie loops

### Backend application contracts

```kotlin
package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.command.CommandWithResult

data class RefreshUserSessionCommand(
    val refreshToken: String?,
) : CommandWithResult<AuthTokens>

data class LogoutUserSessionCommand(
    val refreshToken: String?,
) : CommandWithResult<LogoutUserSessionResult>

data class LogoutUserSessionResult(
    val sessionInvalidated: Boolean,
)
```

```kotlin
package com.profiletailors.smp.credentials.application

import java.time.Instant

data class RefreshSessionIssueRequest(
    val principalId: String,
    val subject: String,
    val email: String,
    val username: String?,
    val issuedAt: Instant,
)

data class IssuedRefreshSession(
    val rawToken: String,
    val sessionId: String,
    val expiresAt: Instant,
)

data class ValidRefreshSession(
    val sessionId: String,
    val principalId: String,
    val subject: String,
    val email: String,
    val username: String?,
    val expiresAt: Instant,
)

interface RefreshSessionGateway {
    suspend fun create(session: NewRefreshSession)
    suspend fun findActiveByLookupKey(lookupKey: String): StoredRefreshSession?
    suspend fun rotate(sessionId: String, replacement: NewRefreshSession, rotatedAt: Instant)
    suspend fun revoke(sessionId: String, revokedAt: Instant, reason: RefreshSessionRevocationReason)
}
```

```kotlin
package com.profiletailors.smp.credentials.application

interface RefreshSessionTokenService {
    fun generate(): GeneratedRefreshToken
    fun hash(secret: String): String
    fun matches(rawSecret: String, verifier: String): Boolean
}

data class GeneratedRefreshToken(
    val rawToken: String,
    val lookupKey: String,
    val secret: String,
)
```

### Refresh-session table proposal

```sql
refresh_sessions (
  id varchar(64) primary key,
  principal_id varchar(64) not null references principals(id),
  lookup_key varchar(255) not null unique,
  secret_verifier varchar(255) not null,
  status varchar(32) not null,
  expires_at timestamp with time zone not null,
  rotated_by_session_id varchar(64) null references refresh_sessions(id),
  rotated_from_session_id varchar(64) null references refresh_sessions(id),
  revoked_at timestamp with time zone null,
  last_used_at timestamp with time zone null,
  created_at timestamp with time zone not null default current_timestamp
)
```

Notes:

- `lookup_key` enables indexed lookup before secret verification, mirroring the API-key pattern.
- `secret_verifier` stores only a one-way verifier.
- `status` supports at least `ACTIVE`, `REVOKED`, `ROTATED`, `EXPIRED`.
- lineage columns make rotation explicit and testable.

### Frontend request wrapper contract

```ts
export interface AuthSessionSnapshot {
  accessToken: string | null
  isAuthenticated: boolean
}

export interface AuthenticatedRequestOptions extends RequestInit {
  retryOn401?: boolean
}

export async function authenticatedRequest<T>(
  path: string,
  options?: AuthenticatedRequestOptions,
): Promise<T>
```

Behavioral contract:

- includes `credentials: 'include'`
- attaches `Authorization` header only when memory token exists
- retries once at most after `401`
- never recursively retries refresh for refresh/logout endpoints themselves

### Frontend store contract adjustments

```ts
interface AuthStateUser {
  principalId: string
  email: string | null
  username: string | null
  displayIdentity: string
}

interface SessionBootstrapResult {
  restored: boolean
}
```

Store methods expected after change:

```ts
loginWithPassword(payload)
registerWithPassword(payload)
bootstrapSession()
refreshSession()
refreshProfile()
logout()
clearSession()
```

## Testing Strategy

| Layer          | What to Test                                                                                | Approach                                                                                                                         |
|----------------|---------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Unit           | Register/login handlers issue access token and request refresh-session creation             | Extend `LocalAuthHandlersTest` with fake refresh-session service and fixed clock assertions.                                     |
| Unit           | Refresh handler denies missing/invalid/revoked/expired session and rotates valid session    | Add handler tests around refresh lifecycle service and JWT issuance.                                                             |
| Unit           | Logout handler revokes active refresh session and remains idempotent when session is absent | Test explicit result flags and revocation calls.                                                                                 |
| Unit           | Cookie factory emits correct set/clear cookie attributes for configured environment         | Focused tests on `ResponseCookie` generation, including `HttpOnly`, `SameSite`, `Secure`, path, and max-age clearing.            |
| Web/controller | `/login` and `/register` return body + Set-Cookie                                           | Extend `LocalAuthControllerTest` or add WebFlux slice tests to assert response cookie metadata.                                  |
| Web/controller | `/refresh` succeeds with cookie and clears cookie on invalid refresh                        | Controller tests using fake mediator responses and cookie inspection.                                                            |
| Integration    | End-to-end register/login, `/me`, refresh rotation, logout invalidation                     | Extend `LocalAuthEndpointIntegrationTest` with H2-backed refresh-session persistence and `WebTestClient` cookie exchange.        |
| Integration    | Invalid/expired refresh cannot recover session                                              | Persist revoked/expired refresh state and assert `401` + no access token issued.                                                 |
| Integration    | Logout prevents subsequent refresh after access token loss                                  | Login, logout, drop access token, call `/refresh`, expect deny.                                                                  |
| Frontend unit  | Auth store bootstrap succeeds from refresh when memory is empty                             | Add minimal Vitest coverage if introduced; mock `fetch` responses and assert in-memory-only state.                               |
| Frontend unit  | Shared wrapper retries exactly once after `401`                                             | Mock protected request -> `401`, refresh -> `200`, replay -> `200`, and verify one refresh + one replay only.                    |
| Frontend unit  | Failed refresh clears session and does not loop                                             | Mock `401` then refresh `401`, assert logout/clearSession and no second refresh.                                                 |
| Manual/browser | Local dev cookie + CORS behavior between `apps/web/app` origin and backend origin           | Verify login sets cookie, reload restores session, logout clears it, and protected calls recover once after forced token expiry. |

## Migration / Rollout

1. **Schema first**
    - Add `refresh_sessions` Liquibase changelog and include it in `db.changelog-master.yaml`.
    - No backfill is required because existing browser access tokens are not migrated into refresh
      sessions.

2. **Backend deploy compatibility**
    - Deploy backend support for refresh/logout and cookie issuance before or together with frontend
      changes.
    - Existing frontend login/register callers continue to work because the JSON auth response
      remains compatible.

3. **Frontend cutover**
    - Remove `localStorage` hydration and switch startup to refresh bootstrap.
    - On first deploy after cutover, users with only an old persisted access token but no refresh
      cookie will be logged out once and must sign in again. This is acceptable and should be called
      out in rollout notes.

4. **Safe failure posture**
    - If refresh configuration is wrong, the frontend remains fail-closed: no token persistence
      fallback is reintroduced.
    - Support teams can diagnose cookie/CORS issues via login success + missing bootstrap/refresh
      continuity.

5. **Rollback**
    - Roll frontend and backend back together if the session-refresh unit is unstable.
    - Optionally leave the new DB table in place unused; no destructive rollback migration is
      required immediately.

## Open Questions

- [ ] Should `/api/auth/logout` be fully idempotent (`204` even when no valid refresh session
  exists) or return `401` for missing/invalid refresh state? The frontend is simpler with idempotent
  `204`, but this should be agreed explicitly.
- [ ] What exact access-token TTL and refresh-session TTL should production use? Current
  `local-jwt.ttl-seconds` defaults to `3600`, which is too long if the token is intended to be
  “short-lived”.
- [ ] What exact `SameSite` policy is required for deployment? `Lax` is likely correct for same-site
  SPA/API origins; `None` would require `Secure` and should be chosen only if cross-site deployment
  is expected.
- [ ] Do we want to scope the refresh cookie path to `/api/auth` only, or broader `/api`?
  `/api/auth` is tighter and sufficient for refresh/logout/login flows.
- [ ] Is minimal frontend auth testing sufficient for this iteration, or should the repo add Vitest
  now to make retry/bootstrapping logic regression-safe?
