# Tasks: Password Recovery

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~2200 (backend ~1400 + frontend ~600 + hardening ~200) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (Backend) → PR 2 (Frontend) → PR 3 (Hardening) |
| Delivery strategy | ask-on-risk |
| Chain strategy | stacked-to-main |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Liquibase + domain + handlers + ports + adapters + HTTP endpoints + backend tests | PR 1 | Standalone, ~1400 lines, includes unit/R2DBC/HTTP/BDD tests |
| 2 | API client + views + router + i18n + frontend tests | PR 2 | ~600 lines, base = `main` after PR 1 merges |
| 3 | Rate limit hardening, audit event, cleanup job, runbook | PR 3 | Optional / decoupled, ~200 lines |

## PR 1 — Backend password recovery

### Phase 1.1: Schema (Liquibase)

- [PR-1.01] Add `server/smp/src/main/resources/db/changelog/identity/005-create-password-reset-tokens.yaml` with table, unique constraint, FK cascade to `principal_identities`, and partial active index (`WHERE used_at IS NULL`)
- [PR-1.02] Add a corresponding rollback changeset (or document the `liquibase rollback` tag) so the table can be dropped safely

### Phase 1.2: Domain layer (pure Kotlin)

- [PR-1.03] Add `identity/domain/PasswordResetToken.kt` data class with `isExpired(now)`, `isUsed()`, `isValid(now)` predicates (matches design contract)
- [PR-1.04] Add `identity/domain/PasswordResetRequested.kt` `DomainEvent` carrying `principalId`, `email`, `rawResetToken` (raw token for dispatch only — never persisted)

### Phase 1.3: Application layer

- [PR-1.05] Add `identity/application/PasswordResetTokenHasher.kt` (object) — 32-byte SecureRandom → URL-safe Base64 no padding → SHA-256 hex hash; 30-min TTL constant — TDD: failing test first (entropy, determinism, uniqueness, hash mismatch)
- [PR-1.06] Add `identity/application/PasswordResetTokenExceptions.kt` sealed hierarchy: `InvalidPasswordResetTokenException`, `ExpiredPasswordResetTokenException`, `UsedPasswordResetTokenException` — all share the public message constant from spec REQ-RP-06
- [PR-1.07] Add `identity/application/PasswordRecoveryDisabledException.kt` for 503 mapping
- [PR-1.08] Add `identity/application/PasswordResetCommands.kt` — `RequestPasswordResetCommand`, `RequestPasswordResetResult`, `ResetPasswordCommand`, `ResetPasswordResult`
- [PR-1.09] Add `identity/application/PasswordResetTokenRepository.kt` port with `invalidateActiveTokens`, `create`, `findByTokenHash`, `consumeAndUpdatePassword` (atomic, returns `Boolean`)
- [PR-1.10] Add `identity/application/RequestPasswordResetHandler.kt` — TDD: failing test first; covers unknown email, OAuth-only identity, local account happy path, email normalization, event publish after commit, disabled-flag → throws `PasswordRecoveryDisabledException`
- [PR-1.11] Add `identity/application/ResetPasswordHandler.kt` — TDD: failing test first; covers unknown/expired/used tokens, password policy (8-128 chars), atomic consume, `revokeAllForPrincipal` after success, no session issuance, disabled-flag

### Phase 1.4: Infrastructure layer

- [PR-1.12] Add `identity/infrastructure/R2dbcPasswordResetTokenRepository.kt` adapter — TDD: TestContainers Postgres integration test covering insert, lookup, uniqueness violation, `invalidateActiveTokens` only marks unused, `consumeAndUpdatePassword` atomic (success / expired-0 rows / used-0 rows), concurrent double-consume returns exactly one `true`, FK cascade on principal delete
- [PR-1.13] Add `identity/infrastructure/email/EmailTemplates.kt::passwordResetEmail(username, token, publicAppUrl)` — HTML with CTA, raw-token URL, 30-min expiry line, "ignore if not you" note, no temp/current password
- [PR-1.14] Add `identity/infrastructure/email/SendPasswordResetEmailConsumer.kt` event listener (`@Subscribe` on `PasswordResetRequested`); log success/failure with `principalId` + masked email only (never raw token)
- [PR-1.15] Modify `identity/infrastructure/http/LocalAuthController.kt` — add `POST /api/auth/forgot-password` (returns 202) and `POST /api/auth/reset-password` (returns 204), plus `ForgotPasswordRequest` and `ResetPasswordRequest` DTOs with Jakarta validation
- [PR-1.16] Modify `identity/infrastructure/http/IdentityProblemDetailsHandler.kt` — add 4 mappings: `InvalidPasswordResetTokenException` → 400 `INVALID_PASSWORD_RESET_TOKEN`, `ExpiredPasswordResetTokenException` → 400 `EXPIRED_PASSWORD_RESET_TOKEN`, `UsedPasswordResetTokenException` → 400 `USED_PASSWORD_RESET_TOKEN`, `PasswordRecoveryDisabledException` → 503 `PASSWORD_RECOVERY_DISABLED`. The 3 token-error mappings share an identical `detail` string per REQ-RP-06
- [PR-1.17] Modify `identity/infrastructure/security/IdentitySecurityConfiguration.kt` — add `/api/auth/forgot-password` and `/api/auth/reset-password` to the existing `permitAll` path matchers
- [PR-1.18] Extend `identity/application/RefreshSessionLifecycleService` with `suspend fun revokeAllForPrincipal(principalId: String)`; verify R2DBC implementation propagates the caller's reactive transaction context (reuse existing `TransactionalOperator` injection)
- [PR-1.19] Add 3 rate limit buckets to `identity/infrastructure/InMemoryRateLimitAdapter` (or new `AuthRateLimitWebFilter`): `PASSWORD_RESET_REQUEST_IP` (5/15m), `PASSWORD_RESET_REQUEST_EMAIL` (3/30m, key = normalized email, increments even on unknown email), `PASSWORD_RESET_ATTEMPT_IP` (10/15m) — short-circuit with 429 + code `AUTH_RATE_LIMIT_EXCEEDED`
- [PR-1.20] Add `app.identity.password-recovery.enabled` configuration property (default `true`) wired to `passwordRecoveryEnabled` lambda injected into both handlers

### Phase 1.5: BDD feature files

- [PR-1.21] Add `server/smp/src/test/resources/features/auth/identity-request-password-reset.feature` — covers happy path, unknown email, OAuth-only, normalization, validation, token lifecycle, concurrency, transaction-rollback, notification content, security (no raw token leak), rate limit, disabled
- [PR-1.22] Add `server/smp/src/test/resources/features/auth/identity-reset-password.feature` — covers happy path + login-with-new, old-password rejected, sessions revoked, access tokens not renewed, unknown/expired/used/invalidated tokens, edge-of-expiration, modified-character, password policy (min/max), validation, security, transaction rollback paths, concurrency, rate limit, disabled
- [PR-1.23] Add `server/smp/src/test/resources/features/auth/identity-password-reset-persistence.feature` — schema, uniqueness, FK cascade, lookup by hash, lookup by raw fails, invalidate-active, atomic consume (unused/expired/used), double-consume concurrency, retention cleanup contract
- [PR-1.24] Add `server/smp/src/test/resources/features/auth/identity-password-reset-notifications.feature` — post-commit dispatch, no dispatch on rollback, retry, terminal failure, template (reset URL with `publicAppUrl`, escaping), telemetry excludes raw token / new password / URL query
- [PR-1.25] Add `server/smp/src/test/resources/features/auth/identity-password-reset-security.feature` — enumeration defenses (status, body, OAuth-disclosure), token strength + uniqueness, hash-only leak safety, no ambient auth, CORS, replay, brute-force rate limit, timing, audit (PII-free)
- [PR-1.26] Add BDD glue steps (`*BddSteps.kt`) for all scenarios — every scenario carries `@fast` (and `@postgres` only when the persistence feature requires Postgres-variant infra); reuse existing `BddDatabaseSupport`, `BddSteps`, `WebTestClient` patterns

### Phase 1.6: Test verification + open PR

- [PR-1.27] Run `just backend-check` and confirm green (detekt + unit tests)
- [PR-1.28] Run `just backend-bdd-fast` and confirm green
- [PR-1.29] Run `just backend-bdd-postgres` and confirm green (covers the persistence + concurrency scenarios that need real Postgres locking)
- [PR-1.30] Branch `feat/dallay-523-password-recovery-backend`, push, open PR — base = `main`

## PR 2 — Frontend password recovery (depends on PR 1 merged)

### Phase 2.1: API client

- [PR-2.01] Add `requestPasswordReset(email: string): Promise<void>` and `resetPassword({token, newPassword}): Promise<void>` to `apps/web/app/src/modules/auth/api/auth.ts` — TDD: Vitest contract test asserts request body shape, status handling, and that the token is never stored to localStorage / sessionStorage / analytics
- [PR-2.02] Wire both functions to the existing `requestRaw` helper with the `application/vnd.api.v1+json` Accept header (match the rest of the auth module)

### Phase 2.2: Views

- [PR-2.03] Add `apps/web/app/src/modules/auth/views/ForgotPasswordView.vue` — TDD: Vitest with validation (empty, malformed), loading state, double-submit guard, generic confirmation, 429 rate-limit localized error, 503 disabled-state message
- [PR-2.04] Add `apps/web/app/src/modules/auth/views/ResetPasswordView.vue` — TDD: Vitest with no-token state (renders invalid-link panel + link to `/forgot-password`), mismatching-passwords state, policy-violation state, success state with link to `/login`, double-submit guard, generic 400 token-error (no invalid/expired/used distinction in UI per REQ-UI-11), 429 rate-limit, 503 disabled
- [PR-2.05] Modify `apps/web/app/src/modules/auth/views/LoginView.vue` to add a keyboard-reachable "Forgot password?" link that navigates to `/forgot-password`

### Phase 2.3: Router + guards

- [PR-2.06] Register `/forgot-password` and `/reset-password` as guest-only routes in `apps/web/app/src/modules/auth/router/routes.ts` (same `guestOnly` guard that protects `/login` and `/register`)
- [PR-2.07] Verify the guest-only guard logic does not block a still-authenticated visitor with a valid `?token=` from reaching `/reset-password`; adjust the guard predicate if needed

### Phase 2.4: i18n

- [PR-2.08] Add EN keys in `apps/web/app/src/locales/en.json`: `auth.forgotPassword.*`, `auth.resetPassword.*`, `auth.login.forgotPasswordLink`, `auth.errors.authRateLimited`, `auth.errors.invalidPassword`, `auth.errors.passwordRecoveryDisabled`
- [PR-2.09] Add ES keys in `apps/web/app/src/locales/es.json` matching the EN namespace shape (Spanish copy is longer — never use fixed-width containers per AGENTS.md)

### Phase 2.5: Test verification + open PR

- [PR-2.10] Run `just frontend-test` (Vitest unit tests) and confirm green
- [PR-2.11] Run `just frontend-test-e2e` (Playwright) and confirm green — coverage: link from login, request happy path, unknown-email shows generic confirmation, reset page with missing token, password mismatch, invalid/used/expired token, successful reset + redirect to login
- [PR-2.12] Branch `feat/dallay-523-password-recovery-frontend`, push, open PR — base = `main` after PR 1 merges

## PR 3 — Hardening (optional, can be deferred)

### Phase 3.1: Rate limiting hardening

- [PR-3.01] If the deployment runs more than one backend instance, replace `InMemoryRateLimitAdapter` with a distributed-aware implementation (Redis or equivalent) so per-IP and per-email buckets hold across instances
- [PR-3.02] Add per-IP and per-email metric counters (`password_reset_request_total{result}` and `password_reset_attempt_total{result}`) — labels exclude PII (no email, no token, no IP)

### Phase 3.2: Audit + observability

- [PR-3.03] Emit a `PASSWORD_RESET_COMPLETED` audit event from `ResetPasswordHandler` carrying `principalId`, `action = "PASSWORD_RESET_COMPLETED"`, `occurredAt` — no token, no password, no password hash
- [PR-3.04] Add OpenTelemetry spans around `consumeAndUpdatePassword` and `SendPasswordResetEmailConsumer` (attributes: `principal_id_hash`, `notification_type`, `delivery_status`; never raw token / URL / email)
- [PR-3.05] Document an operational runbook at `docs/runbooks/password-recovery.md` covering: failed-reset spike triage, rate-limit false positives, email provider outage, how to flush a stuck token, how to disable recovery in production via `app.identity.password-recovery.enabled=false`

### Phase 3.3: Operational

- [PR-3.06] Add a scheduled job that deletes `password_reset_tokens` rows whose `expires_at < now() - retention` (default retention 7 days post-expiry); confirm job is idempotent and does not touch used tokens inside the audit window
- [PR-3.07] Add an admin query endpoint (auth-gated, RBAC `identity:read`) to inspect active reset tokens per principal — returns `expires_at`, `requested_at`, never the hash or raw token

### Phase 3.4: Open PR 3

- [PR-3.08] Branch `feat/dallay-523-password-recovery-hardening`, push, open PR — base = `main` after PR 1 (PR 2 merge not required)

## Acceptance checklist (overall)

- [ ] `POST /api/auth/forgot-password` always returns 202
- [ ] `POST /api/auth/reset-password` returns 204 on success
- [ ] Raw token never persisted in DB
- [ ] Raw token never logged
- [ ] Token expires in 30 minutes
- [ ] Single-use enforced atomically
- [ ] Previous tokens invalidated on new request
- [ ] Password + token consume atomic
- [ ] All refresh sessions revoked after reset
- [ ] No session token issued after reset
- [ ] RFC 9457 Problem Details for token errors
- [ ] Rate limiting on IP + email
- [ ] EN + ES translations
- [ ] Full coverage: unit, PostgreSQL, HTTP, BDD, E2E
