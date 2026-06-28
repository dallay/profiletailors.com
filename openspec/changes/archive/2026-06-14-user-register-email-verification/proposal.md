# Proposal: User Registration with Email Verification

## Intent

Users are fully authenticated immediately upon registration — no email verification step exists.
This creates a security gap: unverified emails can access the platform, receive notifications, and
perform actions. We need to gate authentication behind email verification while keeping the
registration UX smooth.

## Architecture Decision Record

**ADR: Domain Event + Notification Handler for Email Verification**

|                  |                                                                                                                                                                                                                                                                 |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Status**       | Accepted                                                                                                                                                                                                                                                        |
| **Context**      | Registration creates a user and immediately issues JWT+refresh. No email verification exists. The shared bus provides `DomainEvent`, `EventConsumer`, and `Mediator.publish` but they are unused by the identity context.                                       |
| **Decision**     | Use Domain Event + Notification Handler pattern. `RegisterUserHandler` emits `UserRegistered` domain event. A `NotificationConsumer` in the identity infrastructure layer consumes the event and dispatches a verification email via SMTP.                      |
| **Alternatives** | (1) Direct dispatch — simpler but loses `DomainEvent` semantic and decouples email logic into the handler. (2) Inline sync — blocks on SMTP, couples registration to email availability.                                                                        |
| **Consequences** | Email sending is async and decoupled. New notification infrastructure (Spring Boot Starter Mail + `SmtpEmailSender` adapter) required. Login and refresh flows must check `email_status` before issuing sessions. Existing users migrated to `VERIFIED` status. |

## Scope

### In Scope

- Database migration: add `email_status` column to `user_identities`, create
  `email_verification_tokens` table
- `UserRegistered` domain event emitted by `RegisterUserHandler`
- `SendVerificationEmailUseCase` + `SmtpEmailSender` adapter within identity infrastructure
- `POST /api/auth/verify-email` endpoint (token-based verification)
- `POST /api/auth/resend-verification` endpoint
- Login guard: reject unverified emails with 403 + specific error
- Refresh guard: reject unverified emails
- Migration marks existing users as `VERIFIED`
- Spring Boot Starter Mail dependency + SMTP config

### Out of Scope

- Frontend email verification UI (deferred — API-only for now)
- Email template design/styling (plain text templates only)
- Rate limiting on resend (future work)
- Social login email verification (deferred)
- Admin override for email status

## Capabilities

### New Capabilities

- `email-verification`: Email verification lifecycle — token generation, email dispatch, token
  consumption, email status tracking
- `email-notifications`: Email sending infrastructure via SMTP — `SmtpEmailSender` adapter, Spring
  Boot Starter Mail integration

### Modified Capabilities

- `identity`: Registration flow now emits `UserRegistered` domain event instead of immediately
  issuing auth session; login/refresh check `email_status`
- `credentials`: Refresh credential issuance is gated behind email verification status

## Approach

Follow the recommended Domain Event + Notification Handler pattern from exploration. Leverage
existing `DomainEvent` and `EventConsumer` primitives in `shared/bus`. Keep all email
infrastructure within `identity.infrastructure` to respect hexagonal boundaries.

Registration flow becomes: Register → persist `UNVERIFIED` → emit `UserRegistered` event →
return 201 with "verify your email" message (NO tokens issued). Verification flow:
`GET /api/auth/verify-email?token=...` → validate token → set `email_status=VERIFIED` → issue
JWT+refresh session.

Login flow: authenticate credentials → check `email_status` → if `UNVERIFIED` → reject 403.
Refresh flow: validate refresh credential → check `email_status` → if `UNVERIFIED` → reject 403.

## Affected Areas

| Area                                                                                 | Impact   | Description                                                                                         |
|--------------------------------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------|
| `server/smp/.../identity/application/LocalAuthHandlers.kt`                           | Modified | `RegisterUserHandler` emits event, does not issue tokens; login/refresh handlers check email status |
| `server/smp/.../identity/application/LocalAuthApi.kt`                                | Modified | New command/result types for verification                                                           |
| `server/smp/.../identity/application/LocalAuthExceptions.kt`                         | Modified | `UnverifiedEmailException`                                                                          |
| `server/smp/.../identity/infrastructure/http/LocalAuthController.kt`                 | Modified | New endpoints: verify-email, resend-verification                                                    |
| `server/smp/.../identity/infrastructure/http/IdentityProblemDetailsHandler.kt`       | Modified | Handle `UnverifiedEmailException`                                                                   |
| `server/smp/.../identity/infrastructure/R2dbcIdentityRegistrationGateway.kt`         | Modified | Persist `email_status`, generate verification token                                                 |
| `server/smp/.../identity/infrastructure/R2dbcPrincipalIdentityLookup.kt`             | Modified | Include email status in queries                                                                     |
| `server/smp/.../identity/infrastructure/IdentityBootstrapConfiguration.kt`           | Modified | Wire new beans                                                                                      |
| `server/smp/.../identity/infrastructure/security/IdentitySecurityConfiguration.kt`   | Modified | Permit `/api/auth/verify-email`                                                                     |
| New: `server/smp/.../identity/infrastructure/email/SmtpEmailSender.kt`               | New      | SMTP adapter                                                                                        |
| New: `server/smp/.../identity/infrastructure/email/SendVerificationEmailConsumer.kt` | New      | Domain event consumer                                                                               |
| New: `server/smp/.../identity/infrastructure/email/EmailTemplates.kt`                | New      | Plain text templates                                                                                |
| `server/smp/.../db/migration/identity/004-add-email-verification.yaml`               | New      | Schema migration                                                                                    |
| `server/smp/build.gradle.kts`                                                        | Modified | Add Spring Boot Starter Mail dependency                                                             |

## Risks

| Risk                                       | Likelihood | Mitigation                                                                  |
|--------------------------------------------|------------|-----------------------------------------------------------------------------|
| Email delivery failures block registration | Med        | Resend endpoint; plain-text fallback; log failures                          |
| Token security (replay, expiry)            | Low        | Single-use, 24h TTL, SHA-256 hashed (follow existing token hasher patterns) |
| Existing user migration breaks sessions    | Low        | Backfill `email_status=VERIFIED` for all existing rows in same migration    |
| SMTP config missing in dev/test            | High       | Use `MockEmailSender` for tests; conditional SMTP bean                      |
| Login guard regression for existing users  | Low        | Existing users are `VERIFIED`; only new registrations affected              |

## Rollback Plan

1. Revert database migration (drop `email_status` column, drop `email_verification_tokens` table)
2. Revert handler changes (restore immediate token issuance on registration)
3. Remove email infrastructure beans and dependency
4. Run `./gradlew :server:smp:test` to confirm clean revert
5. If migration already deployed with existing unverified users: manual SQL update to set all
   `email_status = 'VERIFIED'` before rollback

## Dependencies

- Spring Boot Starter Mail (new)
- Existing `shared/bus` DomainEvent infrastructure (no changes)
- Existing `shared/common` Email value object (no changes)
- SMTP server for production (configurable, dev can use mock)

## Acceptance Criteria

| ID    | Criterion                                                                           | Validation       |
|-------|-------------------------------------------------------------------------------------|------------------|
| AC-1  | Registration returns 201 without issuing JWT/refresh                                | Integration test |
| AC-2  | `email_status` is `UNVERIFIED` for new registrations                                | Unit test        |
| AC-3  | Existing users are migrated to `VERIFIED`                                           | Migration test   |
| AC-4  | `GET /api/auth/verify-email?token=...` sets status to `VERIFIED` and returns tokens | Integration test |
| AC-5  | Login with `UNVERIFIED` email returns 403                                           | Integration test |
| AC-6  | Login with `VERIFIED` email succeeds normally                                       | Integration test |
| AC-7  | Refresh with `UNVERIFIED` email returns 403                                         | Integration test |
| AC-8  | Resend verification sends new token, invalidates old                                | Integration test |
| AC-9  | Verification token is single-use (second attempt fails)                             | Unit test        |
| AC-10 | Existing test suite passes with zero regressions                                    | `./gradlew test` |

## Success Criteria

- [ ] New users must verify email before accessing protected endpoints
- [ ] Existing users are not disrupted (all migrated to VERIFIED)
- [ ] Verification token is secure (hashed, single-use, TTL)
- [ ] Email sending is async and decoupled from registration
- [ ] All existing tests pass
