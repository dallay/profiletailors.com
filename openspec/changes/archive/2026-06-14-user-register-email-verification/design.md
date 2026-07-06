# Design: User Registration with Email Verification

## Technical Approach

Gate authentication behind email verification using the existing Domain Event (`UserRegistered`) +
Notification Handler pattern. `RegisterUserHandler` emits the event instead of issuing tokens. An
`EventConsumer` in `identity.infrastructure.email` asynchronously dispatches the verification email
via SMTP. Login and refresh handlers check `email_status` before issuing sessions. Verification
tokens follow the same hashing pattern as refresh tokens (SHA-256, single-use, 24h TTL).

```
Registration → emit UserRegistered → async EmailConsumer → SMTP
                    ↓
              Login/Refresh → check email_status → reject if UNVERIFIED
```

## Architecture Decisions

### Decision: Event Consumer (not NotificationHandler)

| Option                                      | Tradeoff                                                                                                                                            | Decision                                                                 |
|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `EventConsumer<DomainEvent>` + `@Subscribe` | Leverages existing `EventConfiguration` auto-wiring; follows pattern from `shared/bus`. Consistent with how the bus infrastructure scans consumers. | **Chosen** — matches how pipeline / event infrastructure is wired today. |
| `NotificationHandler<UserRegistered>`       | Different registration mechanism, no auto-scanning in place.                                                                                        | Rejected — would require new wiring.                                     |

### Decision: Email status as column on `user_identities`, not separate table

| Option                                     | Tradeoff                                                   | Decision                                          |
|--------------------------------------------|------------------------------------------------------------|---------------------------------------------------|
| Column `email_status` on `user_identities` | Single query to check, simple migration, no join overhead. | **Chosen** — status is intrinsic to the identity. |
| Separate `email_verification_status` table | Normalized but adds join for every auth check.             | Rejected — auth query performance matters.        |

### Decision: SHA-256 hashing for tokens (same pattern as refresh tokens)

| Option                   | Tradeoff                                                                                              | Decision                                                  |
|--------------------------|-------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| SHA-256 hash + DB lookup | Deterministic, same pattern as `RefreshTokenHasher` in credentials context. No additional dependency. | **Chosen** — consistent with existing credential hashing. |
| BCrypt                   | Slower, overkill for single-use token.                                                                | Rejected — tokens are not passwords.                      |
| Raw token in DB          | Leaks token on DB compromise.                                                                         | Rejected — security risk.                                 |

### Decision: Async email via domain event, not direct SMTP call

| Option                      | Tradeoff                                                                              | Decision                                      |
|-----------------------------|---------------------------------------------------------------------------------------|-----------------------------------------------|
| DomainEvent + EventConsumer | Decoupled, non-blocking registration. SMTP failure does not impact user registration. | **Chosen** — matches proposal ADR.            |
| Direct SMTP in handler      | Simpler but blocks registration on email delivery.                                    | Rejected — couples reg to email availability. |

## Data Flow

```
User ──POST /register──→ RegisterUserHandler
                            │
                            ├─ persist user with email_status=UNVERIFIED
                            ├─ generate token, store hashed
                            ├─ emit UserRegistered event (async via EventEmitter)
                            └─ return 201 { message: "verify your email" }

EventConsumer ──UserRegistered──→ SendVerificationEmailConsumer
                                     │
                                     ├─ lookup unhashed token (in-memory)
                                     ├─ render EmailTemplate
                                     └─ SmtpEmailSender.send(to, subject, body)

User ──GET /verify-email?token=──→ VerifyEmailHandler
                                      │
                                      ├─ hash(token) → look up in email_verification_tokens
                                      ├─ verify not expired, not used
                                      ├─ set email_status=VERIFIED
                                      ├─ mark token as used
                                      ├─ issue JWT + refresh tokens
                                      └─ return 200 { accessToken, ..., email_status }

User ──POST /login──→ LoginUserHandler
                         │
                         ├─ authenticate credentials
                         ├─ check email_status == VERIFIED
                         ├─ if UNVERIFIED: return 403
                         └─ if VERIFIED: issue tokens

User ──POST /refresh──→ RefreshUserSessionHandler
                           │
                           ├─ validate refresh token
                           ├─ check email_status == VERIFIED
                           ├─ if UNVERIFIED: return 403
                           └─ if VERIFIED: issue new JWT
```

## File Changes

| File                                                                    | Action | Description                                                                                                                                        |
|-------------------------------------------------------------------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `db/changelog/identity/004-add-email-verification.yaml`                 | Create | Migration: add `email_status`, create `email_verification_tokens`                                                                                  |
| `db/changelog/db.changelog-master.yaml`                                 | Modify | Include new identity migration                                                                                                                     |
| `.../identity/application/LocalAuthApi.kt`                              | Modify | Add `VerifyEmailCommand`, `ResendVerificationCommand`, `RegistrationResult`, `AuthTokens.emailStatus`                                              |
| `.../identity/application/LocalAuthExceptions.kt`                       | Modify | Add `UnverifiedEmailException`                                                                                                                     |
| `.../identity/application/LocalAuthHandlers.kt`                         | Modify | `RegisterUserHandler` emits event + returns no tokens; login/refresh guard with email check; add `VerifyEmailHandler`, `ResendVerificationHandler` |
| `.../identity/application/IdentityRegistrationGateway.kt`               | Modify | Add `createEmailVerificationToken`                                                                                                                 |
| `.../identity/application/PrincipalIdentityLookup.kt`                   | Modify | Add `findByEmailWithStatus` or include `emailStatus` in `PrincipalIdentityFacts`                                                                   |
| `.../identity/infrastructure/R2dbcIdentityRegistrationGateway.kt`       | Modify | Insert `email_status`, insert into `email_verification_tokens`                                                                                     |
| `.../identity/infrastructure/R2dbcPrincipalIdentityLookup.kt`           | Modify | Include `email_status` in queries                                                                                                                  |
| `.../identity/infrastructure/http/LocalAuthController.kt`               | Modify | Add `GET /verify-email`, `POST /resend-verification`                                                                                               |
| `.../identity/infrastructure/http/IdentityProblemDetailsHandler.kt`     | Modify | Handle `UnverifiedEmailException` (403)                                                                                                            |
| `.../identity/infrastructure/security/IdentitySecurityConfiguration.kt` | Modify | Permit verification endpoints without auth                                                                                                         |
| `.../identity/infrastructure/IdentityBootstrapConfiguration.kt`         | Modify | Wire email-related beans                                                                                                                           |
| `.../identity/infrastructure/email/SmtpEmailSender.kt`                  | Create | SMTP adapter implementing `EmailSender` port                                                                                                       |
| `.../identity/infrastructure/email/SendVerificationEmailConsumer.kt`    | Create | `EventConsumer<UserRegistered>` with `@Subscribe`                                                                                                  |
| `.../identity/infrastructure/email/EmailTemplates.kt`                   | Create | Plain text verification email template                                                                                                             |
| `.../identity/infrastructure/email/MockEmailSender.kt`                  | Create | Test double for dev/test profile                                                                                                                   |
| `.../identity/application/EmailSender.kt`                               | Create | Port interface in application layer                                                                                                                |
| `.../identity/domain/EmailVerificationToken.kt`                         | Create | Token VO with hash, expiry, consumed state                                                                                                         |
| `.../identity/domain/UserRegistered.kt`                                 | Create | Domain event: `UserRegistered(email, principalId, username)`                                                                                       |
| `.../identity/domain/EmailStatus.kt`                                    | Create | Enum: `UNVERIFIED`, `VERIFIED`                                                                                                                     |
| `build.gradle.kts`                                                      | Modify | Add `spring-boot-starter-mail`                                                                                                                     |

## Database Schema

### New migration: `identity/004-add-email-verification.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: identity-004-add-email-verification
      author: acosta
      changes:
        - addColumn:
            tableName: user_identities
            columns:
              - column:
                  name: email_status
                  type: varchar(16)
                  defaultValue: UNVERIFIED
                  constraints:
                    nullable: false
        - createTable:
            tableName: email_verification_tokens
            columns:
              - column:
                  name: id
                  type: bigserial
                  constraints:
                    primaryKey: true
              - column:
                  name: email
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: token_hash
                  type: varchar(64)
                  constraints:
                    nullable: false
              - column:
                  name: expires_at
                  type: timestamp with time zone
                  constraints:
                    nullable: false
              - column:
                  name: used_at
                  type: timestamp with time zone
                  constraints:
                    nullable: true
              - column:
                  name: created_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
        - addUniqueConstraint:
            tableName: email_verification_tokens
            columnNames: token_hash
            constraintName: uq_email_verification_tokens_hash
        - createIndex:
            indexName: idx_email_verification_tokens_email
            tableName: email_verification_tokens
            columns:
              - column:
                  name: email
        - sql:
            sql: UPDATE user_identities SET email_status = 'VERIFIED'
```

## Domain Model

```
EmailStatus : enum { UNVERIFIED, VERIFIED }

EmailVerificationToken(
    email: String,
    tokenHash: String,
    expiresAt: Instant,
    usedAt: Instant? = null,
) {
    fun isValid(now: Instant): Boolean =
        usedAt == null && now.isBefore(expiresAt)
}

UserRegistered(
    principalId: String,
    email: String,
    username: String?,
) : BaseDomainEvent()
```

State machine for email lifecycle:

```
REGISTER → [UNVERIFIED]
                ↓ (verify token presented + valid)
                ↓
           [VERIFIED]
                ↓ (resend requested)
           UNVERIFIED (new token, old invalidated)
```

## API Contract

### `GET /api/auth/verify-email?token=...` (permit all)

| Attribute                    | Value                                               |
|------------------------------|-----------------------------------------------------|
| Method                       | GET                                                 |
| Path                         | `/api/auth/verify-email`                            |
| Query                        | `token` (required)                                  |
| Success                      | `200 OK` — `AuthTokens` body + `Set-Cookie` refresh |
| Error (invalid/expired/used) | `400 Bad Request` — `ProblemDetail`                 |

**Success response:**

```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "principalId": "user-xxx",
  "email": "user@example.com",
  "username": "user",
  "emailStatus": "VERIFIED",
  "workspaceId": "ws-xxx"
}
```

### `POST /api/auth/resend-verification` (permit all)

| Attribute              | Value                                                              |
|------------------------|--------------------------------------------------------------------|
| Method                 | POST                                                               |
| Path                   | `/api/auth/resend-verification`                                    |
| Body                   | `{ "email": "user@example.com" }`                                  |
| Success                | `202 Accepted` — no body (returns same for valid & invalid emails) |
| Error (invalid format) | `400 Bad Request`                                                  |

### Modified endpoints

| Endpoint         | Change                                                                               |
|------------------|--------------------------------------------------------------------------------------|
| `POST /register` | Returns `201` with `{ "emailStatus": "UNVERIFIED" }` and no tokens. No `Set-Cookie`. |
| `POST /login`    | Returns `403` with `UnverifiedEmailException` when `email_status != VERIFIED`        |
| `POST /refresh`  | Returns `403` with `UnverifiedEmailException` when `email_status != VERIFIED`        |

### New exception

- `UnverifiedEmailException` → mapped to `403 Forbidden` with `ProblemDetail` title
  `"Email verification required"`.

### AuthTokens DTO change

Add `emailStatus: String` field to `AuthTokens`.

## Email Adapter Pattern

```
application/EmailSender.kt (port)
    ↑                    ↑
    |                    |
SmtpEmailSender      MockEmailSender
(infrastructure)     (infrastructure)
- spring.mail.*      - logs to console
- Active profile     - @Profile("dev","test")
```

**Port interface:**

```kotlin
interface EmailSender {
    suspend fun send(to: String, subject: String, body: String): EmailSendResult
}

data class EmailSendResult(val success: Boolean, val error: String? = null)
```

**Adapter selection:** Conditional beans via `@Profile`. `MockEmailSender` registered for `dev` and
`test` profiles. `SmtpEmailSender` registered for all others (including production). Wiring in
`IdentityBootstrapConfiguration`.

**Configuration properties (new in `application.yaml`):**

```yaml
app:
  email:
    sender: noreply@profiletailors.com
    verification-subject-prefix: "[Profile Tailors]"
```

## Security Considerations

| Concern                    | Mitigation                                                                |
|----------------------------|---------------------------------------------------------------------------|
| Token replay               | Single-use — `used_at` set on verification; second attempt returns 400    |
| Token theft during transit | SHA-256 hashed in DB (already hashed on validation path)                  |
| Token theft from DB        | Only hash stored; raw token held in-memory only                           |
| Token expiry               | 24h TTL via `expires_at` column                                           |
| Email enumeration (resend) | Same `202 Accepted` for valid and invalid emails                          |
| Email enumeration (verify) | Same `400` error shape for invalid/expired/used tokens                    |
| Brute-force token guessing | 32-byte (256-bit) CSPRNG token → 2²⁵⁶ keyspace                            |
| Logging token values       | Explicitly excluded from log statements; only hash logged for diagnostics |

## Testing Strategy

| Layer       | What                                                                | Approach                                                                                           |
|-------------|---------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Unit        | `RegisterUserHandler` emits `UserRegistered`, does NOT issue tokens | Existing `FakeIdentityRegistrationGateway` pattern; verify event emission via `FakeEventPublisher` |
| Unit        | `VerifyEmailHandler` validates tokens, sets VERIFIED, issues tokens | Pure handler tests with fakes                                                                      |
| Unit        | `LoginUserHandler` checks email status and rejects UNVERIFIED       | Add `FakePrincipalIdentityLookup` with `emailStatus`; test both paths                              |
| Unit        | `RefreshUserSessionHandler` checks email status                     | Same pattern                                                                                       |
| Unit        | `EmailVerificationToken` value object `isValid()`                   | Unit test: expired/non-expired, used/unused token combos                                           |
| Integration | Full register → verify → login → refresh flow                       | Extend `LocalAuthEndpointIntegrationTest` with H2; test key AC-1 through AC-9                      |
| Integration | Resend verification invalidates old token                           | Integration test: token A → resend → verify with token A fails                                     |
| Integration | Migration backfills existing users to VERIFIED                      | Liquibase test: insert pre-migration row, run migration, assert VERIFIED                           |
| BDD         | User registration and verification scenarios                        | Cucumber scenarios mapping to spec acceptance criteria                                             |
| Isolation   | MockEmailSender for dev/test profiles                               | `@Profile("dev","test")` conditional bean; verify captured emails                                  |

### Test files to create/modify

| File                                                                     | Action                                                                       |
|--------------------------------------------------------------------------|------------------------------------------------------------------------------|
| `.../identity/application/LocalAuthHandlersTest.kt`                      | Extend — add tests for VerifyEmail, ResendVerification, login/refresh guards |
| `.../identity/infrastructure/http/LocalAuthControllerTest.kt`            | Extend — add verify-email and resend-verification endpoint tests             |
| `.../integration/LocalAuthEndpointIntegrationTest.kt`                    | Extend — full verification lifecycle with H2 + Liquibase                     |
| `.../identity/domain/EmailVerificationTokenTest.kt`                      | Create — unit test for VO logic                                              |
| `.../identity/infrastructure/email/SendVerificationEmailConsumerTest.kt` | Create — consumer receives event and sends via mock                          |
| New BDD files under `.../bdd/`                                           | Create — Cucumber steps for verification scenarios                           |

## Open Questions

- [ ] SMTP server host/port — will be configured via env vars; should we provide dev defaults like
  `localhost:1025` (Mailpit)?
- [ ] Template engine — plain text only for now; use Kotlin string templates or a lightweight
  library?
- [ ] Token cleanup strategy — scheduled job (Spring `@Scheduled`) or rely on TTL queries with
  periodic cleanup?
