# Profile Tailors — Security Audit Report

**Date:** 2026-08-02  
**Scope:** Full repository (`vsls:/`)  
**Standards:** OWASP Top 10:2025, OWASP API Security Top 10:2023, OWASP ASVS 5.0.0 Level 2  
**Auditor:** Senior Application Security Engineering (automated assisted analysis)

---

## A. Executive Security Summary

### Overall Posture

The Profile Tailors codebase demonstrates **above-average security hygiene** for a pre-1.0 SaaS product. The core authentication, authorization, and cryptographic foundations are solid: passwords are hashed with BCrypt (with correct SHA-256 pre-hashing for long inputs), refresh tokens rotate atomically and store only BCrypt hashes, JWT secrets fail-fast rather than defaulting to a hardcoded value, AES-256/GCM is used for OAuth credential encryption, and all SQL queries are parameterized. Docker images run as non-root with a read-only filesystem and all Linux capabilities dropped.

Two confirmed medium-severity findings have remediation evidence in the current checkout: one
pre-authorized unauthenticated path for an unimplemented proxy endpoint and a predictable default
OAuth state signing secret that did not fail fast. SEC-001's BDD coverage landed in `850668ca`,
and its allowlist removal landed in `e3b78d16`. SEC-002's guard and regression coverage landed in
`eed89343`. A set of deferred architectural
risks is documented separately.

### Evidence anchors

- **SEC-001:** Commit `850668ca` added the endpoint-authorization BDD coverage and `e3b78d16`
  committed the corresponding production allowlist removal. The current
  `IdentitySecurityConfiguration` no longer includes `/api/media/proxy` in the unauthenticated
  allowlist.
- **SEC-002:** Commit `eed89343` added the placeholder-prefix guard and regression coverage in
  `HmacOAuthStateSigner`.

### Findings by Severity

| Severity | Confirmed | Current resolution |
| -------- | --------- | ------------------ |
| Critical | 0         | —                  |
| High     | 0         | —                  |
| Medium   | 2         | SEC-001 and SEC-002 landed |
| Low      | 4         | 2 remediated; 2 deferred |
| Info     | 3         | 0 (ops/config)     |

### Immediate Release Blockers

SEC-001 is landed in `e3b78d16`, with its BDD coverage in `850668ca`. SEC-002 is landed in
`eed89343`. No Critical or High findings were identified; deferred Low and Info risks remain
documented below.

### Main Systemic Risks

1. Auth rate limiter uses raw socket IP, which is the proxy IP in production — IP-keyed limiting is ineffective behind Cloudflare/ingress. Compensated by per-email rate limiting in the password-reset flow.
2. All rate limiting is in-process and single-instance. Documented as acceptable for the current `0.1.0` single-replica deployment, but must be replaced with a distributed store (Redis) before horizontal scaling.
3. Audit hooks are disabled by default (`SMP_PLATFORM_AUDIT_ENABLED=false`). Security events are not persistently recorded in the default configuration.

### Areas Assessed

- Backend: Identity, Authorization, Tenancy, Media, Publishing (LinkedIn OAuth), Governance, Credentials, Observability, Platform
- Shared: Storage (local + S3/R2), Security, Shield, Spring Boot Common
- Frontend: Vue 3 SPA (`apps/web/app`), Astro marketing site (`apps/web/marketing`)
- Infrastructure: Docker Compose dev, Docker Swarm production stack
- CI/CD: All GitHub Actions workflows
- Dependencies: Gradle libs catalog, pnpm workspace

### Areas Not Assessed

- Cloudflare configuration (WAF rules, TLS settings, firewall rules) — requires manual infrastructure verification
- Docker Swarm overlay network ACLs — requires infrastructure verification
- GitHub repository branch protection settings — requires GitHub UI verification
- Actual secret rotation schedules — requires production verification
- Dynamic SAST/fuzzing against a running instance

### Residual Risk

- Rate limiting bypass behind ingress (deferred, documented in DALLAY-513)
- Audit hooks off by default; security events non-observable without opt-in configuration

---

## B. Attack-Surface Map

### Components

| Component      | Technology                               | Entry Points                                    |
| -------------- | ---------------------------------------- | ----------------------------------------------- |
| SMP Backend    | Spring Boot 4 / Kotlin / WebFlux / R2DBC | HTTP on port 7638, management on 9091           |
| Vue SPA        | Vue 3 / Vite / TypeScript                | Browser via HTTPS                               |
| Marketing site | Astro 7 / TypeScript                     | Browser via HTTPS / Cloudflare Pages            |
| PostgreSQL     | PostgreSQL 18                            | Internal only (no published port in production) |
| Cloudflare     | CDN/proxy                                | Public internet                                 |
| Mailpit (dev)  | SMTP stub                                | localhost:1025 (dev only)                       |
| WireMock (dev) | LinkedIn API stub                        | localhost:8089 (dev only)                       |
| LinkedIn API   | OAuth 2.0                                | Outbound from backend                           |
| Unsplash API   | REST                                     | Outbound from backend                           |
| Resend (email) | REST                                     | Outbound from backend                           |
| R2/S3 storage  | S3-compatible object store               | Outbound from backend                           |

### Trust Boundaries

```
Internet
  │
  ├── Cloudflare (WAF, TLS termination)
  │       │
  │       ├── Marketing static site (Cloudflare Pages)
  │       └── Vue SPA (Cloudflare Pages)
  │               │
  │               └── Browser ──HTTPS──▶ API (VITE_API_BASE_URL / /api proxy)
  │
  └── Docker Swarm overlay `edge` network
          │
          ├── Backend SMP (port 7638, exposed via `edge`)
          │       │
          │       ├── overlay `data` network ──▶ PostgreSQL (port 5432, internal only)
          │       └── Outbound HTTPS ──▶ LinkedIn API, Unsplash API, Resend
          │
          └── Management port 9091 (NOT published; internal only via healthcheck)
```

### Authentication Mechanisms

| Path                                                   | Mechanism                                           |
| ------------------------------------------------------ | --------------------------------------------------- |
| `/api/auth/**`                                         | Unauthenticated (with rate limiting)                |
| Most API paths                                         | Bearer JWT (HMAC-HS256, 15-minute TTL)              |
| Service-to-service                                     | API key (BCrypt-verified, stored as hash)           |
| Scheduled jobs                                         | Service account credentials                         |
| Cookie flows (`/api/auth/refresh`, `/api/auth/logout`) | HttpOnly refresh cookie + Origin/Referer CSRF check |

### Sensitive Assets

- Password hashes (BCrypt, stored in `local_password_credentials`)
- Refresh session tokens (BCrypt hashes, stored in `refresh_sessions`)
- Password reset tokens (SHA-256 hashes, stored in `password_reset_tokens`)
- Email verification tokens (SHA-256 hashed, stored in `email_verification_tokens`)
- LinkedIn OAuth credentials (AES-256/GCM encrypted at rest)
- JWT signing secret (`SMP_LOCAL_JWT_SECRET`, Docker Swarm secret)
- Media preview signing secret (`SMP_MEDIA_PREVIEW_SIGNING_SECRET`, Docker Swarm secret)
- LinkedIn state signing secret (`SMP_LINKEDIN_STATE_SIGNING_SECRET`, Docker Swarm secret)
- LinkedIn client secret (`SMP_LINKEDIN_CLIENT_SECRET`, Docker Swarm secret)
- PostgreSQL password (`SMP_DB_PASSWORD`, Docker Swarm secret)

---

## C. Findings Register

| ID      | Severity | Confidence | Component                                                          | Finding                                                                                                                          | CWE     | OWASP     | ASVS    | Exploitable                            | Status                  |
| ------- | -------- | ---------- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------- | ------- | --------- | ------- | -------------------------------------- | ----------------------- |
| SEC-001 | Medium   | High       | `IdentitySecurityConfiguration`                                    | Pre-authorized unauthenticated path `/api/media/proxy` with no controller implementation                                         | CWE-306 | A01, API5 | V4.2.1  | Future (when implemented without auth) | **FIXED**               |
| SEC-002 | Medium   | High       | `application.yaml` / `HmacOAuthStateSigner`                        | LinkedIn OAuth state signing secret defaults to predictable `CHANGE_ME_LINKEDIN_STATE`; no fail-fast                             | CWE-798 | A07, API2 | V3.5.3  | Yes (if default not changed)           | **FIXED**               |
| SEC-003 | Low      | High       | `AuthRateLimitWebFilter`                                           | Auth rate limiter keys on socket IP — behind Cloudflare/ingress, all users share the proxy's IP bucket                           | CWE-307 | A07       | V11.1.7 | Partial (rate limit bypass at scale)   | Deferred (DALLAY-513)   |
| SEC-004 | Low      | Medium     | `application.yaml`                                                 | `application.rate-limit.auth.enabled: false` and `business.enabled: false` disable shield rate limiting for business endpoints   | CWE-770 | A09       | V11.1.4 | Low                                    | **FIXED** (doc)         |
| SEC-005 | Low      | Medium     | `application.yaml`                                                 | `SMP_PLATFORM_AUDIT_ENABLED` defaults to `false`; security events are not persistently recorded in the default configuration     | CWE-778 | A09       | V7.4.1  | Low (ops risk)                         | Deferred (ops decision) |
| SEC-006 | Info     | High       | `application.yaml`                                                 | DB password default `CHANGE_ME_gK2fcFZg5cgVu9U` in dev Compose; not a production risk but increases dev misconfiguration surface | CWE-258 | A02       | V2.1.1  | No (dev env only)                      | Accepted                |
| SEC-007 | Info     | Medium     | `AuthRateLimitWebFilter`                                           | Rate limit is per-process; multi-instance deployment without Redis coordination would allow bypass                               | CWE-362 | A07       | V11.1.7 | No (0.1.0 single-replica)              | Deferred (documented)   |
| SEC-008 | Info     | High       | `SecurityResponseHeadersWebFilter`                                 | CSP `default-src 'self'` may be too restrictive for future CDN assets on the SPA; no script-src exception for analytics          | CWE-16  | A02       | V14.4.3 | No                                     | Hardening opportunity   |
| SEC-009 | Low      | High       | `LocalAuthHandlers`, `ResetPasswordHandler`, `RegisterUserRequest` | Password minimum enforced at 8 characters; ASVS L2 V2.1.1 requires ≥ 12 characters                                               | CWE-521 | A07       | V2.1.1  | Yes (weak passwords admitted)          | **FIXED**               |

---

## D. Detailed Finding Reports

### SEC-001: Pre-Authorized Unauthenticated Path for Unimplemented Proxy Endpoint (Remediated)

**Title:** `/api/media/proxy` exempted from authentication with no implementation  
**Severity:** Medium  
**Confidence:** High  
**Component:** `IdentitySecurityConfiguration.kt`  
**Location:** `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`, line ~150

**Current status:** **FIXED**. The current public allowlist does not contain `/api/media/proxy`.
The BDD regression coverage was added in commit `850668ca`, and the corresponding production
configuration deletion was committed in `e3b78d16`.

**Attacker prerequisites:** None — unauthenticated internet access

**Attacker-controlled input:** HTTP GET request to `/api/media/proxy` with arbitrary query parameters

**Trust boundary crossed:** Internet → API (bypasses authentication gate)

**Historical execution path:**  
`IdentitySecurityConfiguration.securityWebFilterChain` contains:
```kotlin
it.pathMatchers(
    HttpMethod.GET,
    "/actuator/health",
    "/actuator/prometheus",
    "/api/capabilities/public",
    "/api/media/proxy",          // ← this path
    "/api/media/assets/*/preview",
    "/api/media/assets/*/content",
).permitAll()
```
No Spring MVC controller currently handles `GET /api/media/proxy`. A 404 is returned today. However, the security configuration pre-exempts this path from authentication. When a controller is added (SSRF proxy for preview URL fetching is the likely intent), it will be unauthenticated unless the developer explicitly overrides this.

**Historical defenses:** The path returned 404 because no controller existed. That did not provide
an authorization invariant for a future implementation.

**Impact:** If a proxy endpoint is later implemented that fetches arbitrary URLs without authentication, it becomes an unauthenticated SSRF vector
**Likelihood:** Medium (endpoint is on the roadmap based on its presence in the config)  
**CWE:** CWE-306 (Missing Authentication for Critical Function)  
**OWASP:** A01:2025 Broken Access Control, API5:2023 Broken Function Level Authorization  
**ASVS:** V4.2.1

**Remediation applied:** `/api/media/proxy` was removed from the `permitAll()` block. If the endpoint
must serve unauthenticated users (e.g., for email preview images), it may be added back only after
the controller is implemented and its authorization requirements are explicitly designed.

**Regression test:** `850668ca` added the security BDD scenario confirming
`GET /api/media/proxy` returns 401 for unauthenticated requests.

---

### SEC-002: Predictable Default LinkedIn OAuth State Signing Secret (Remediated)

**Title:** `SMP_LINKEDIN_STATE_SIGNING_SECRET` defaults to `CHANGE_ME_LINKEDIN_STATE`  
**Severity:** Medium  
**Confidence:** High  
**Component:** `application.yaml`, `HmacOAuthStateSigner`  
**Location:** `server/smp/src/main/resources/application.yaml` line ~127

**Current status:** **FIXED**. Commit `eed89343` added the placeholder-prefix guard and tests. The
application now rejects the default value when the LinkedIn signer is created.

**Attacker prerequisites:** LinkedIn integration enabled; secret not overridden in production

**Attacker-controlled input:** Crafted OAuth `state` parameter in the LinkedIn callback URL

**Trust boundary crossed:** LinkedIn OAuth callback → application trust of state payload

**Historical execution path before `eed89343`:**
```yaml
publishing:
  linkedin:
    state-signing-secret: ${SMP_LINKEDIN_STATE_SIGNING_SECRET:CHANGE_ME_LINKEDIN_STATE}
```
Before the guard was added, `HmacOAuthStateSigner.init` only required `secret.isNotBlank()`. The
string `CHANGE_ME_LINKEDIN_STATE` was non-blank, so the application started without error. An
attacker who knew the default secret could generate valid HMAC-signed state parameters, bypassing
the CSRF protection of the OAuth flow and linking a victim's LinkedIn account to an
attacker-controlled workspace.

**Comparison:** The JWT secret (`LocalJwtSecretResolver`) fails fast with `error(...)` if no secret is configured — there is no fallback to a guessable string. The LinkedIn state secret lacks this protection.

**Current defense:** `HmacOAuthStateSigner.init` rejects blank secrets and case-insensitive
`CHANGE_ME`, `changeme`, `placeholder`, and `test-` prefixes. The regression tests and wiring for
the accepted `bdd-` and `smp-` test-secret prefixes landed in `eed89343`.

**Impact:** OAuth CSRF — attacker can complete the LinkedIn OAuth flow on behalf of a victim and link their own LinkedIn account to an arbitrary workspace, or steal the victim's OAuth authorization code
**Likelihood:** Medium (requires LinkedIn integration enabled AND operator oversight of secret rotation)
**CWE:** CWE-798 (Use of Hard-Coded Credentials)  
**OWASP:** A07:2025 Authentication Failures, API2:2023 Broken Authentication  
**ASVS:** V3.5.3

**Remediation applied:** The signer now fails fast when the resolved secret is blank or matches a
known placeholder prefix. See the `HmacOAuthStateSigner.kt` change in the Remediation Changelog.

**Regression test:** `eed89343` added unit tests in `HmacOAuthStateSignerTest.kt` asserting that the
signer throws `IllegalArgumentException` for `CHANGE_ME_LINKEDIN_STATE`, `CHANGE_ME_*`,
`changeme*`, `placeholder*`, and `test-*` prefixed secrets.

---

## E. ASVS 5.0.0 Level 2 Traceability Matrix (Selected Controls)

| ASVS ID | Control Summary                                  | Applicability | Status                       | Evidence                                                                                                                                                           |
| ------- | ------------------------------------------------ | ------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| V2.1.1  | Passwords ≥ 12 chars, validated                  | YES           | PASS                         | `LocalAuthController.RegisterUserRequest @Size(min=12)`, `LocalAuthHandlers.MIN_PASSWORD_LENGTH=12`, `ResetPasswordHandler.MIN_PASSWORD_LENGTH=12` — SEC-009 fixed |
| V2.1.7  | Passwords checked against known-compromised list | YES           | NOT_APPLICABLE (L3)          | Not required at L2                                                                                                                                                 |
| V2.1.9  | No composition rules (special chars forced)      | YES           | PASS                         | Regex allows all chars                                                                                                                                             |
| V2.2.1  | Anti-automation on auth                          | YES           | PASS                         | `AuthRateLimitWebFilter`, per-email rate limiting                                                                                                                  |
| V2.2.2  | Soft lockout ≤ 15 attempts                       | YES           | PARTIAL                      | Rate limiting present; lockout style differs                                                                                                                       |
| V2.3.1  | Reset tokens expire ≤ 10 minutes                 | YES           | PARTIAL                      | 30-minute TTL (ASVS recommends ≤ 10 min for L3; L2 says short-lived)                                                                                               |
| V2.4.1  | Passwords stored with bcrypt/scrypt/argon2       | YES           | PASS                         | `BCryptPasswordHasher`                                                                                                                                             |
| V2.5.1  | Timing-safe token comparison                     | YES           | PASS                         | `MessageDigest.isEqual`, BCrypt.checkpw                                                                                                                            |
| V3.2.1  | Refresh tokens rotated                           | YES           | PASS                         | `R2dbcRefreshSessionGateway.rotate()`                                                                                                                              |
| V3.2.2  | Refresh token reuse detection                    | YES           | PASS                         | ROTATED status check                                                                                                                                               |
| V3.4.1  | Cookie: HttpOnly                                 | YES           | PASS                         | `RefreshSessionCookieFactory.httpOnly = true`                                                                                                                      |
| V3.4.2  | Cookie: Secure                                   | YES           | PASS                         | `SMP_REFRESH_COOKIE_SECURE=true` in prod                                                                                                                           |
| V3.4.3  | Cookie: SameSite                                 | YES           | PASS                         | Lax (default), configurable                                                                                                                                        |
| V3.5.1  | OAuth state parameter used                       | YES           | PASS                         | `HmacOAuthStateSigner`                                                                                                                                             |
| V3.5.2  | OAuth PKCE or state nonce                        | YES           | PASS                         | State includes nonce + HMAC signature                                                                                                                              |
| V3.5.3  | OAuth state unpredictable                        | YES           | PASS                         | `HmacOAuthStateSigner.init` rejects blank and placeholder-prefixed secrets; SEC-002 fixed in `eed89343`                                                          |
| V4.1.1  | Access control decisions enforced server-side    | YES           | PASS                         | `WorkspaceAuthorizationService`                                                                                                                                    |
| V4.1.2  | Deny by default                                  | YES           | PASS                         | `anyExchange().authenticated()` catch-all                                                                                                                          |
| V4.2.1  | All paths require authentication by default      | YES           | PASS                         | `/api/media/proxy` is absent from the current public allowlist; BDD coverage landed in `850668ca` and the production deletion in `e3b78d16` |
| V5.1.1  | All inputs parameterized                         | YES           | PASS                         | R2DBC `.bind()` throughout                                                                                                                                         |
| V5.2.1  | Input validation before processing               | YES           | PASS                         | Bean Validation annotations on all DTOs                                                                                                                            |
| V7.1.1  | No credentials logged                            | YES           | PASS                         | No credential logging found                                                                                                                                        |
| V7.4.1  | Security events recorded                         | YES           | FAIL (SEC-005)               | Audit hooks disabled by default                                                                                                                                    |
| V9.1.1  | TLS required for sensitive endpoints             | YES           | MANUAL_VERIFICATION_REQUIRED | Cloudflare/HTTPS config not in repo                                                                                                                                |
| V11.1.4 | Rate limiting on API                             | YES           | PARTIAL                      | Auth endpoints rate limited; business endpoints not (SEC-004)                                                                                                      |
| V11.1.7 | Rate limiting per-client                         | YES           | PARTIAL                      | Keyed on socket IP (SEC-003)                                                                                                                                       |
| V13.2.3 | CORS restrictive allowedOrigins                  | YES           | PASS                         | Empty default, explicit per-env                                                                                                                                    |
| V13.4.1 | Security headers set                             | YES           | PASS                         | `SecurityResponseHeadersWebFilter`                                                                                                                                 |
| V14.1.1 | Secrets not in source                            | YES           | PASS                         | Env-var pattern throughout; fail-fast on blanks                                                                                                                    |
| V14.3.1 | Stack traces not exposed                         | YES           | PASS                         | `ProblemDetail` handlers return no stack trace                                                                                                                     |
| V14.4.3 | CSP set                                          | YES           | PASS                         | `default-src 'self'; frame-ancestors 'none'`                                                                                                                       |
| V14.4.4 | X-Content-Type-Options                           | YES           | PASS                         | `nosniff`                                                                                                                                                          |
| V14.4.6 | X-Frame-Options / frame-ancestors                | YES           | PASS                         | `frame-ancestors 'none'` in CSP                                                                                                                                    |
| V14.5.1 | Actuator not exposed publicly                    | YES           | PASS                         | Separate port 9091, internal network only                                                                                                                          |

---

## F. Remediation Changelog

### SEC-001 — Remove pre-authorized unauthenticated proxy path

**Files changed:**
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`

**Security invariant introduced:** `GET /api/media/proxy` requires authentication unless explicitly permitted again at implementation time.

**Evidence:** The current `IdentitySecurityConfiguration` omits the proxy path from
`permitAll()`. Commit `850668ca` adds the corresponding unauthenticated-401 BDD scenario, and
commit `e3b78d16` contains the production configuration deletion.

**Tests added:**
- `server/smp/src/test/resources/features/security-endpoint-authorization.feature` — BDD: 401 on `/api/media/proxy`, `/api/media/assets/*`, `/api/workspaces/**`; 200 only on actuator health/prometheus and capabilities/public (5 scenarios, `@security @smoke @fast`)
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/SecurityAuthorizationBddSteps.kt`

**Compatibility impact:** None — the endpoint has no implementation.  
**Deployment impact:** None.  
**Residual risk:** None for the current unimplemented endpoint. Any future proxy implementation
must define its authentication and SSRF protections explicitly before adding a public allowlist
entry.

---

### SEC-002 — Fail-fast on predictable LinkedIn OAuth state signing secret

**Files changed:**
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/HmacOAuthStateSigner.kt`

**Security invariant introduced:** Application refuses to start if the OAuth state secret matches the placeholder or is blank.

**Evidence:** Commit `eed89343` adds the guard, test-secret wiring, and placeholder-prefix
regression tests.

**Tests added:**
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/HmacOAuthStateSignerTest.kt` — placeholder-prefix rejection (`CHANGE_ME`, `changeme`, `placeholder`, `test-`) and strong-secret acceptance

**Compatibility impact:** Any deployment that has not set `SMP_LINKEDIN_STATE_SIGNING_SECRET` (or uses the `CHANGE_ME_LINKEDIN_STATE` placeholder) will fail to start when the LinkedIn feature is used. Operators must set a strong secret before deploying.
**Deployment impact:** Operators must set `SMP_LINKEDIN_STATE_SIGNING_SECRET` to a strong random value (≥ 32 bytes of entropy, base64 encoded).
**Residual risk:** If LinkedIn integration is disabled (`SMP_LINKEDIN_CLIENT_ID` blank), this code path is not triggered and the check may not run at startup.

---

### SEC-009 — Raise password minimum length to 12 (ASVS L2 V2.1.1)

**Files changed:**
- `server/smp/src/main/kotlin/.../identity/application/LocalAuthHandlers.kt` — `MIN_PASSWORD_LENGTH 8 → 12`
- `server/smp/src/main/kotlin/.../identity/application/ResetPasswordHandler.kt` — `MIN_PASSWORD_LENGTH 8 → 12`
- `server/smp/src/main/kotlin/.../identity/infrastructure/http/LocalAuthController.kt` — `@Size(min=12)`, `@Schema(minLength=12)`
- `apps/web/app/src/shared/lib/validation/schemas.ts` — Zod `min(12)`
- `apps/web/app/src/modules/auth/presentation/ResetPasswordView.vue` — `minlength="12"`
- `apps/web/app/src/shared/i18n/locales/en/auth.ts`, `en/passwordRecovery.ts`, `es/passwordRecovery.ts` — i18n strings

**Security invariant introduced:** Passwords shorter than 12 characters are rejected at registration and password reset. Existing hashed passwords are unaffected.

**Tests added:**
- `server/smp/src/test/resources/features/auth/registration.feature` — BDD: 11-char rejected, 12-char accepted
- `server/smp/src/test/kotlin/.../identity/application/ResetPasswordHandlerTest.kt` — unit: 11-char rejected
- `apps/web/app/src/shared/lib/validation/schemas.test.ts` — boundary updated to 12/128

**Compatibility impact:** Users with passwords 8–11 characters can still log in and use refresh tokens. New registrations and password resets require ≥ 12 characters.
**Deployment impact:** None — no configuration changes required.  
**Residual risk:** None for the backend enforcement. Existing 8–11-char account passwords remain valid until users voluntarily reset their password. No forced password rotation is performed.

---

## G. Deferred-Risk Register

### DR-001: Auth Rate Limiter Keys on Socket IP (Proxy IP) Behind Cloudflare

**Trigger:** Horizontal scaling or Cloudflare/ingress deployment  
**Owner:** Backend platform team  
**Recommended action:** Implement `ForwardedHeaderFilter` trust (DALLAY-513), validate proxy IP ranges, then switch `clientIdentifier()` to use the trusted `X-Forwarded-For` first IP
**Severity:** Medium (currently compensated by per-email rate limiting)  
**Compensating controls:** Per-email rate limiting in `RequestPasswordResetHandler`, 20 req/min per-IP filter still prevents fast brute-force against unknown accounts
**Release impact:** Safe to ship in single-replica `0.1.0`; must be resolved before multi-replica or direct-exposure deployment

---

### DR-002: In-Process Rate Limiter — Not Shared Across Replicas

**Trigger:** Horizontal scaling (multiple SMP replicas)  
**Owner:** Backend platform team  
**Recommended action:** Enable `application.rate-limit.store.distributed-enabled=true` and configure Redis before any horizontal scaling
**Severity:** Medium (rate limits per-instance, not global)  
**Compensating controls:** Documented in `application.yaml`; single-replica deployment makes this a non-issue today
**Release impact:** Must not scale beyond one replica without this change

---

### DR-003: Audit Hooks Disabled by Default

**Trigger:** Incident response / compliance audit  
**Owner:** DevSecOps / compliance team  
**Recommended action:** Enable `SMP_PLATFORM_AUDIT_ENABLED=true` in production and ensure audit records are persisted to a separate, tamper-evident store
**Severity:** Low (operational risk)  
**Compensating controls:** Application logs provide partial observability; structured log events are emitted
**Release impact:** No functional impact; security monitoring coverage reduced

---

### DR-004: Business-Endpoint Rate Limiting Disabled by Default

**Trigger:** API abuse or DoS against authenticated endpoints  
**Owner:** Backend platform team  
**Recommended action:** Enable `application.rate-limit.business.enabled=true` and configure appropriate bucket capacities once rate-limit store is deployed
**Severity:** Low  
**Compensating controls:** Database-layer constraints, media upload rate limiting (`MediaRateLimitRepository`), authentication requirement on all business endpoints
**Release impact:** Business endpoints may be subject to excessive request rates from compromised accounts

---

### DR-005: LinkedIn Integration Security Depends on Correct Secret Rotation

**Trigger:** Production deployment of LinkedIn integration  
**Owner:** Operators / DevSecOps  
**Recommended action:** Rotate `SMP_LINKEDIN_STATE_SIGNING_SECRET`, `SMP_LINKEDIN_CLIENT_SECRET`, and `PUBLISHING_CREDENTIALS_ENCRYPTION_KEY` before each deployment; use Docker Swarm secrets (already configured)
**Severity:** Medium (if secrets not rotated before deploy)  
**Compensating controls:** Docker Swarm secret injection, SEC-002 fix adds fail-fast for state secret
**Release impact:** LinkedIn integration must not be enabled without secret rotation
