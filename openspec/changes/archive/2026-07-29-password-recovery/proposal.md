# Proposal: Password Recovery

## Intent

Users who lose access to their account have no self-service path to regain it. Implement a secure, standalone "Password Recovery" capability in the `identity` bounded context that lets registered users reset their password via a time-limited, single-use email token — without revealing account existence to adversaries.

## Scope

### In Scope
- `password_reset_tokens` table (Liquibase migration `005-...`)
- `PasswordResetToken` domain model + `PasswordResetTokenHasher`
- `RequestPasswordResetCommand/Handler` + `ResetPasswordCommand/Handler`
- `PasswordResetTokenRepository` port + `R2dbcPasswordResetTokenRepository` adapter
- `POST /api/auth/forgot-password` (202) + `POST /api/auth/reset-password` (204) endpoints
- `PasswordResetRequested` domain event → `SendPasswordResetEmailConsumer`
- Revoke all refresh sessions after password change
- RFC 9457 Problem Details for token errors
- Rate limiting: IP bucket + email bucket
- Vue SPA: `ForgotPasswordView.vue`, `ResetPasswordView.vue`, "Forgot password?" link
- i18n EN + ES
- Full test coverage: unit, PostgreSQL, HTTP adapter, BDD, Playwright E2E

### Out of Scope
- Metrics / audit event (`PASSWORD_RESET_COMPLETED`) — deferred to PR 3 (hardening)
- Expired-token cleanup scheduler — deferred to PR 3
- Operational runbook — deferred to PR 3
- Username/account enumeration defenses beyond the 202 response (already covered)
- Social / OAuth login recovery flows

## Capabilities

> Contract between proposal and specs phases.

### New Capabilities
- `password-recovery-api`: Backend token lifecycle — request, validate, consume, reset password, revoke sessions. Covers domain model, CQRS handlers, repository port, HTTP endpoints, rate limiting, event dispatch.
- `password-recovery-ui`: Frontend flows — ForgotPassword and ResetPassword views, API client, router guards, i18n.

### Modified Capabilities
- `iam`: Add token revocation step after password update and document the no-session-issuance post-reset rule.
- `email-verification`: No spec change — used only as an architectural reference pattern (token hasher, hash-only storage). Not reusing the table or model.

## Approach

Mirror the `EmailVerificationTokenHasher` pattern end-to-end:
1. Generate 256-bit random URL-safe token; compute SHA-256 hash; store only the hash.
2. Email raw token to user (never persisted again).
3. On reset: look up by hash WHERE `used_at IS NULL AND expires_at > now()` in a single atomic transaction that also updates the credential password and marks the token used.
4. After commit: dispatch `PasswordResetRequested` event async → email consumer.
5. Revoke all `refresh_sessions` for the principal synchronously in the same transaction scope.
6. Return 204; no session token issued — user must sign in explicitly.

**PR delivery order:** PR 1 (backend) → PR 2 (frontend, depends on PR 1 merge) → PR 3 (hardening, optional decoupled).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/main/resources/db/changelog/` | New | `005-create-password-reset-tokens.yaml` |
| `server/smp/src/main/kotlin/.../identity/domain/` | New | `PasswordResetToken`, `PasswordResetTokenHasher` |
| `server/smp/src/main/kotlin/.../identity/application/` | New | `RequestPasswordResetCommand/Handler`, `ResetPasswordCommand/Handler` |
| `server/smp/src/main/kotlin/.../identity/infrastructure/` | New | `R2dbcPasswordResetTokenRepository`, `SendPasswordResetEmailConsumer`, rate limit adapters |
| `server/smp/src/main/kotlin/.../identity/infrastructure/http/LocalAuthController.kt` | Modified | Add 2 new endpoints |
| `apps/web/app/src/modules/auth/` | New | `ForgotPasswordView.vue`, `ResetPasswordView.vue`, API client, router entries |
| `apps/web/app/src/modules/auth/views/LoginView.vue` | Modified | Add "Forgot password?" link |
| `apps/web/app/src/locales/` | New | EN + ES i18n keys for recovery flows |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Token enumeration via timing | Low | Constant-time hash comparison; 202 always on forgot-password |
| Race condition on token consume | Low | Atomic WHERE clause (`used_at IS NULL AND expires_at > now()`) in single TX |
| Refresh session revocation missing edge cases | Med | Unit test all `revokeAllForPrincipal` paths; BDD scenario covers it |
| Email delivery failure leaving user stuck | Med | Async event with retry semantics; user can re-request after cooldown |
| Rate limit bypass via distributed IPs | Low | Per-email bucket supplements IP bucket |

## Rollback Plan

1. Disable endpoints via feature flag or deploy previous `LocalAuthController` version.
2. Drop `password_reset_tokens` table (Liquibase rollback tag on `005-...`).
3. Remove Vue routes — `ForgotPasswordView` and `ResetPasswordView` are isolated modules with no shared-state side effects.
4. No schema changes to existing tables (additive only).

## Dependencies

- Linear issue: [DALLAY-523](https://linear.app/dallay/issue/DALLAY-523)
- Existing `EmailVerificationTokenHasher` pattern (reference, not reused)
- `RefreshSessionLifecycleService.revokeAllForPrincipal` — must be implemented or extended as part of PR 1
- Email notification infrastructure (`SendPasswordResetEmailConsumer`) — requires SMTP/Resend config already in place

## Success Criteria

- [ ] `POST /api/auth/forgot-password` always returns 202 regardless of email existence
- [ ] Raw token is never logged, stored in plain text, or returned in any response body
- [ ] Token validated via hash lookup; expires in ≤ 30 min; single-use enforced atomically
- [ ] New request invalidates all previous pending tokens for the same email
- [ ] All refresh sessions revoked synchronously after password change
- [ ] No session token issued after reset — user must re-authenticate
- [ ] RFC 9457 Problem Details returned for expired/invalid/used tokens
- [ ] Rate limiting applied on both IP and email dimensions
- [ ] BDD suite passes (`@fast` tag) without Postgres infra
- [ ] Playwright E2E covers happy path + invalid-token error path in both EN and ES
