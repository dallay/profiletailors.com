# Design: Password Recovery

Technical design for the `password-recovery` change. Mirrors the existing
`identity` hexagonal layout (domain ← application ← infrastructure) and
reuses the `EmailVerificationTokenHasher` pattern end-to-end without
sharing the table, the symbol, or the lifecycle.

## Layered Architecture

```
domain/            Pure Kotlin — no Spring, no R2DBC
  PasswordResetToken
  PasswordResetRequested          (DomainEvent)

application/       Orchestration, no framework annotations on pure logic
  PasswordResetTokenHasher        (object)
  RequestPasswordResetCommand
  RequestPasswordResetResult
  ResetPasswordCommand
  ResetPasswordResult
  PasswordResetTokenRepository   (port)
  RequestPasswordResetHandler
  ResetPasswordHandler
  PasswordResetTokenExceptions   (sealed)

infrastructure/
  R2dbcPasswordResetTokenRepository    (adapter)
  SendPasswordResetEmailConsumer       (event-driven notification)
  EmailTemplates.passwordResetEmail()  (template)
  LocalAuthController                  (modified — adds 2 endpoints)
  IdentityProblemDetailsHandler        (modified — adds 3 token mappings)
  IdentitySecurityConfiguration        (modified — permitAll for 2 paths)
  Liquibase: identity/005-create-password-reset-tokens.yaml
```

## Domain Layer

### `domain/PasswordResetToken.kt`

```kotlin
package com.profiletailors.smp.identity.domain

import java.time.Instant
import java.util.UUID

data class PasswordResetToken(
    val id: UUID,
    val principalId: String,
    val tokenHash: String,
    val requestedAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant? = null,
) {
    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
    fun isUsed(): Boolean = usedAt != null
    fun isValid(now: Instant): Boolean = !isExpired(now) && !isUsed()
}
```

### `domain/PasswordResetRequested.kt`

```kotlin
package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.bus.event.DomainEvent

data class PasswordResetRequested(
    val principalId: String,
    val email: String,
    val rawResetToken: String,  // for dispatch only — never persisted
) : DomainEvent
```

## Application Layer

### `application/PasswordResetTokenHasher.kt`

Mirrors `EmailVerificationTokenHasher` exactly. Reuses the same SHA-256 +
URL-safe Base64 pattern. The only difference is the TTL constant.

```kotlin
package com.profiletailors.smp.identity.application

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

object PasswordResetTokenHasher {
    private const val TOKEN_BYTE_SIZE = 32
    private const val TOKEN_TTL_MINUTES = 30L
    private val secureRandom = SecureRandom()

    fun generate(now: Instant = Instant.now()): GeneratedPasswordResetToken {
        val rawBytes = ByteArray(TOKEN_BYTE_SIZE)
        secureRandom.nextBytes(rawBytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        val tokenHash = hash(rawToken)
        val expiresAt = now.plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES)
        return GeneratedPasswordResetToken(rawToken, tokenHash, expiresAt)
    }

    fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

data class GeneratedPasswordResetToken(
    val rawToken: String,
    val tokenHash: String,
    val expiresAt: Instant,
)
```

### `application/PasswordResetTokenExceptions.kt`

```kotlin
package com.profiletailors.smp.identity.application

open class InvalidPasswordResetTokenException :
    RuntimeException("This password reset link is invalid or has expired. Request a new one.")

class ExpiredPasswordResetTokenException : InvalidPasswordResetTokenException()
class UsedPasswordResetTokenException : InvalidPasswordResetTokenException()
```

### `application/PasswordResetCommands.kt`

```kotlin
package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.bus.command.CommandWithResult

data class RequestPasswordResetCommand(val email: String) :
    CommandWithResult<RequestPasswordResetResult>

data class RequestPasswordResetResult(val accepted: Boolean = true)

data class ResetPasswordCommand(val token: String, val newPassword: String) :
    CommandWithResult<ResetPasswordResult>

data class ResetPasswordResult(val passwordChanged: Boolean = true)
```

### `application/PasswordResetTokenRepository.kt` (port)

```kotlin
package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.PasswordResetToken
import java.time.Instant

interface PasswordResetTokenRepository {
    suspend fun invalidateActiveTokens(principalId: String, invalidatedAt: Instant)

    suspend fun create(
        principalId: String,
        tokenHash: String,
        requestedAt: Instant,
        expiresAt: Instant,
    )

    suspend fun findByTokenHash(tokenHash: String): PasswordResetToken?

    /**
     * Atomic consume-and-update. Returns true iff exactly one row was
     * consumed AND the password credential was updated in the same
     * transaction. On any failure the entire transaction rolls back.
     */
    suspend fun consumeAndUpdatePassword(
        tokenHash: String,
        now: Instant,
        newPasswordHash: String,
    ): Boolean
}
```

### `application/RequestPasswordResetHandler.kt`

```kotlin
@Service
internal class RequestPasswordResetHandler(
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val localPasswordCredentialGateway: LocalPasswordCredentialGateway,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val transactionRunner: AtomicTransactionRunner,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val clock: Clock,
    private val passwordRecoveryEnabled: () -> Boolean,
) : CommandWithResultHandler<RequestPasswordResetCommand, RequestPasswordResetResult> {

    override suspend fun handle(command: RequestPasswordResetCommand): RequestPasswordResetResult {
        if (!passwordRecoveryEnabled()) {
            throw PasswordRecoveryDisabledException()  // -> 503
        }
        val normalizedEmail = normalizeEmail(command.email)
        val principalIdentity = principalIdentityLookup.findByEmail(normalizedEmail)
        val credential = localPasswordCredentialGateway.findByEmail(normalizedEmail)

        // Only proceed when local credential exists; OAuth-only or unknown => silent
        if (principalIdentity == null || credential == null) {
            return RequestPasswordResetResult()
        }

        val now = clock.instant()
        val generated = PasswordResetTokenHasher.generate(now)
        val principalId = principalIdentity.principalId

        // Transactional: invalidate + insert
        transactionRunner.runAtomically {
            passwordResetTokenRepository.invalidateActiveTokens(principalId, now)
            passwordResetTokenRepository.create(
                principalId = principalId,
                tokenHash = generated.tokenHash,
                requestedAt = now,
                expiresAt = generated.expiresAt,
            )
        }

        // Event dispatched AFTER commit so a rolled-back tx never sends email
        eventPublisher.publish(
            PasswordResetRequested(
                principalId = principalId,
                email = normalizedEmail,
                rawResetToken = generated.rawToken,
            ),
        )

        return RequestPasswordResetResult()
    }
}
```

### `application/ResetPasswordHandler.kt`

```kotlin
@Service
internal class ResetPasswordHandler(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val passwordRecoveryEnabled: () -> Boolean,
) : CommandWithResultHandler<ResetPasswordCommand, ResetPasswordResult> {

    override suspend fun handle(command: ResetPasswordCommand): ResetPasswordResult {
        if (!passwordRecoveryEnabled()) {
            throw PasswordRecoveryDisabledException()
        }
        validatePassword(command.newPassword)

        val tokenHash = PasswordResetTokenHasher.hash(command.token)
        val now = clock.instant()
        val newPasswordHash = passwordHasher.hash(command.newPassword)

        // Look up to distinguish invalid/expired/used for Problem Details
        val stored = passwordResetTokenRepository.findByTokenHash(tokenHash)
            ?: throw InvalidPasswordResetTokenException()

        when {
            stored.isUsed() -> throw UsedPasswordResetTokenException()
            stored.isExpired(now) -> throw ExpiredPasswordResetTokenException()
        }

        // Atomic: consume + password update + revoke sessions in one tx
        val principalId = stored.principalId
        val consumed = transactionRunner.runAtomically {
            val ok = passwordResetTokenRepository.consumeAndUpdatePassword(
                tokenHash = tokenHash,
                now = now,
                newPasswordHash = newPasswordHash,
            )
            if (!ok) {
                // Will roll back the transaction (no rows updated)
                throw InvalidPasswordResetTokenException()
            }
            refreshSessionLifecycleService.revokeAllForPrincipal(principalId)
        }

        if (!consumed) throw InvalidPasswordResetTokenException()
        return ResetPasswordResult()
    }

    private fun validatePassword(password: String) {
        if (password.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            throw InvalidPasswordException(password)
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
    }
}
```

## Infrastructure Layer

### `infrastructure/R2dbcPasswordResetTokenRepository.kt`

```kotlin
@Repository
internal class R2dbcPasswordResetTokenRepository(
    private val template: R2dbcEntityTemplate,
    private val passwordCredentialTable: LocalPasswordCredentialTable,
) : PasswordResetTokenRepository {

    // invalidateActiveTokens: UPDATE used_at = now WHERE principal_id = ? AND used_at IS NULL
    // create: INSERT INTO password_reset_tokens ... RETURNING *
    // findByTokenHash: SELECT * FROM password_reset_tokens WHERE token_hash = ?
    // consumeAndUpdatePassword: see "Atomic Consume" below
}
```

**Atomic consume SQL.** The handler invokes `consumeAndUpdatePassword`
inside `transactionRunner.runAtomically`. The implementation MUST execute
this sequence as a single R2DBC transaction:

```sql
-- Step 1: try to consume. Affects at most one row.
UPDATE password_reset_tokens
   SET used_at = :now
 WHERE token_hash = :tokenHash
   AND used_at IS NULL
   AND expires_at > :now;

-- Step 2 (only if step 1's UPDATE returned 1 row):
UPDATE local_password_credentials
   SET password_hash = :newPasswordHash
 WHERE principal_id = (
     SELECT principal_id
       FROM password_reset_tokens
      WHERE token_hash = :tokenHash
 );

-- Step 3 (only if step 1's UPDATE returned 1 row):
-- Delegates to refreshSessionLifecycleService.revokeAllForPrincipal(principalId),
-- which performs its own DELETE/UPDATE inside the same tx.
```

If the first UPDATE returns 0 rows the method returns `false` and the
transaction is rolled back. The handler then throws
`InvalidPasswordResetTokenException`.

### `infrastructure/email/SendPasswordResetEmailConsumer.kt`

Mirrors `SendVerificationEmailConsumer`:

```kotlin
@Component
@Subscribe(filterBy = PasswordResetRequested::class)
class SendPasswordResetEmailConsumer(
    private val emailSender: EmailSender,
    private val emailProperties: EmailProperties,
) : EventConsumer<PasswordResetRequested> {

    private val log = LoggerFactory.getLogger(SendPasswordResetEmailConsumer::class.java)

    override suspend fun consume(event: PasswordResetRequested) {
        val message = EmailTemplates.passwordResetEmail(
            username = event.principalId,  // or displayName from lookup if available
            token = event.rawResetToken,
            publicAppUrl = emailProperties.publicAppUrl,
        )
        val subject = "Reset your password"
        val result = emailSender.send(
            to = event.email,
            subject = subject,
            message = message,
        )
        if (!result.success) {
            log.error(
                "Failed to send password reset email to '${event.email}' " +
                    "for principal '${event.principalId}': ${result.error}",
            )
        } else {
            log.info("Password reset email sent to '${event.email}' for principal '${event.principalId}'")
        }
    }
}
```

### `infrastructure/email/EmailTemplates.kt` (additions)

```kotlin
fun passwordResetEmail(username: String, token: String, publicAppUrl: String): String {
    val resetUrl = "$publicAppUrl/reset-password?token=$token"
    return """
        <p>Hello ${escapeHtml(username)},</p>
        <p>We received a request to reset the password for your account.</p>
        <p><a href="$resetUrl" style="display:inline-block;padding:12px 24px;background:#0d6efd;color:#fff;border-radius:6px;text-decoration:none">Reset password</a></p>
        <p>Or copy and paste this link into your browser:</p>
        <p>$resetUrl</p>
        <p>This link expires in 30 minutes. If you didn't request a reset, you can safely ignore this email.</p>
    """.trimIndent()
}
```

The token is rendered in the URL inside the email body. Email transport
is TLS-only via the existing `EmailSender` infrastructure. No password or
temp password is included anywhere.

### `infrastructure/http/LocalAuthController.kt` (additions)

```kotlin
@PostMapping("/forgot-password", consumes = ["application/json"], version = "1")
suspend fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<Unit> {
    mediator.send(RequestPasswordResetCommand(email = request.email))
    return ResponseEntity.accepted().build()
}

@PostMapping("/reset-password", consumes = ["application/json"], version = "1")
suspend fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Unit> {
    mediator.send(
        ResetPasswordCommand(
            token = request.token,
            newPassword = request.newPassword,
        ),
    )
    return ResponseEntity.noContent().build()
}

data class ForgotPasswordRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Token is required")
    val token: String,
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    val newPassword: String,
)
```

### `infrastructure/http/IdentityProblemDetailsHandler.kt` (additions)

```kotlin
@ExceptionHandler(InvalidPasswordResetTokenException::class)
fun handle(exception: InvalidPasswordResetTokenException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, PASSWORD_RESET_INVALID_DETAIL).apply {
        title = "Invalid password reset token"
        setProperty("code", "INVALID_PASSWORD_RESET_TOKEN")
    }

@ExceptionHandler(ExpiredPasswordResetTokenException::class)
fun handle(exception: ExpiredPasswordResetTokenException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, PASSWORD_RESET_INVALID_DETAIL).apply {
        title = "Expired password reset token"
        setProperty("code", "EXPIRED_PASSWORD_RESET_TOKEN")
    }

@ExceptionHandler(UsedPasswordResetTokenException::class)
fun handle(exception: UsedPasswordResetTokenException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, PASSWORD_RESET_INVALID_DETAIL).apply {
        title = "Used password reset token"
        setProperty("code", "USED_PASSWORD_RESET_TOKEN")
    }

@ExceptionHandler(PasswordRecoveryDisabledException::class)
fun handle(exception: PasswordRecoveryDisabledException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is disabled.").apply {
        title = "Password recovery disabled"
        setProperty("code", "PASSWORD_RECOVERY_DISABLED")
    }

companion object {
    // All three token-error mappings share this constant
    private const val PASSWORD_RESET_INVALID_DETAIL =
        "This password reset link is invalid or has expired. Request a new one."
}
```

### `infrastructure/security/IdentitySecurityConfiguration.kt` (modifications)

Add the two new paths under the existing `permitAll` block:

```kotlin
.pathMatchers(
    HttpMethod.POST,
    "/api/auth/login",
    "/api/auth/register",
    "/api/auth/refresh",
    "/api/auth/logout",
    "/api/auth/resend-verification",
    "/api/auth/forgot-password",   // NEW
    "/api/auth/reset-password",    // NEW
).permitAll()
```

The reset-password endpoint does not require an ambient session. The raw
token in the body carries all authorization. (`@csrf` block comment in
`securityWebFilterChain` is updated to mention the new endpoints.)

### `infrastructure/security/AuthRateLimitWebFilter.kt` (or new adapter)

Add two new buckets to the existing `RateLimit` infrastructure
(`InMemoryRateLimitAdapter`):

- `PASSWORD_RESET_REQUEST_IP` — 5 requests / 15 min
- `PASSWORD_RESET_REQUEST_EMAIL` — 3 requests / 30 min (key = normalized email)
- `PASSWORD_RESET_ATTEMPT_IP` — 10 requests / 15 min

When any bucket is exceeded the filter MUST short-circuit with `429` and
code `AUTH_RATE_LIMIT_EXCEEDED`. The email bucket MUST increment even when
the email does not resolve to an account.

### `application/RefreshSessionLifecycleService.kt` (modification)

Add a new method to the existing interface:

```kotlin
suspend fun revokeAllForPrincipal(principalId: String)
```

The R2DBC implementation MUST run inside the caller's transaction
(reactive transaction context propagation) so that the password update
and the session revocation share the same atomic boundary.

## Liquibase

### `resources/db/changelog/identity/005-create-password-reset-tokens.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: identity-005-create-password-reset-tokens
      author: opencode
      changes:
        - createTable:
            tableName: password_reset_tokens
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
              - column:
                  name: principal_id
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: token_hash
                  type: varchar(128)
                  constraints:
                    nullable: false
              - column:
                  name: requested_at
                  type: timestamp with time zone
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
                  name: request_ip_hash
                  type: varchar(128)
                  constraints:
                    nullable: true
              - column:
                  name: user_agent_hash
                  type: varchar(128)
                  constraints:
                    nullable: true
        - addUniqueConstraint:
            tableName: password_reset_tokens
            columnNames: token_hash
            constraintName: uq_password_reset_tokens_hash
        - addForeignKeyConstraint:
            baseTableName: password_reset_tokens
            baseColumnNames: principal_id
            constraintName: fk_password_reset_principal
            referencedTableName: principal_identities
            referencedColumnNames: principal_id
            onDelete: CASCADE
        - createIndex:
            indexName: idx_password_reset_principal_active
            tableName: password_reset_tokens
            columns:
              - column:
                  name: principal_id
              - column:
                  name: expires_at
            # PostgreSQL partial index — declared via raw SQL below for portability
        - sql: |
            CREATE INDEX idx_password_reset_principal_active_partial
            ON password_reset_tokens (principal_id, expires_at)
            WHERE used_at IS NULL;
```

## Configuration

`app.identity.password-recovery.enabled` (default `true`) — drives the
`passwordRecoveryEnabled` lambda injected into the handlers and the
`PasswordRecoveryDisabledException` -> 503 mapping.

## Frontend Design (apps/web/app)

### Module layout

```
src/modules/auth/
  views/
    ForgotPasswordView.vue       (NEW)
    ResetPasswordView.vue        (NEW)
    LoginView.vue                (modified — adds "Forgot password?" link)
  api/
    auth.ts                      (modified — adds 2 functions)
  router/
    routes.ts                    (modified — adds 2 guest-only routes)
  locales/
    en.json                      (modified — adds namespace)
    es.json                      (modified — adds namespace)
```

### `api/auth.ts` additions

```ts
export async function requestPasswordReset(email: string): Promise<void> {
  await requestRaw('/api/auth/forgot-password', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export async function resetPassword(payload: {
  token: string
  newPassword: string
}): Promise<void> {
  await requestRaw('/api/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
```

### Router additions

Both new routes inherit the existing `guestOnly` guard that protects
`/login` and `/register`.

### View states (state machines)

`ForgotPasswordView`:
```
idle → submitting → (confirmed | error | rate_limited)
```

`ResetPasswordView`:
```
loading_token → (invalid_link | idle → submitting → (success | invalid_token | error))
```

Loading + form-disabled gates prevent duplicate submits.

### i18n namespaces

`en.json` / `es.json` add:
```
auth.forgotPassword.title
auth.forgotPassword.emailPlaceholder
auth.forgotPassword.submit
auth.forgotPassword.confirmation
auth.resetPassword.title
auth.resetPassword.newPasswordPlaceholder
auth.resetPassword.confirmPasswordPlaceholder
auth.resetPassword.submit
auth.resetPassword.invalidLinkMessage
auth.resetPassword.successMessage
auth.login.forgotPasswordLink
auth.errors.authRateLimited
auth.errors.invalidPassword
```

### Accessibility

- `aria-live="polite"` on the confirmation region.
- `aria-describedby` linking inputs to validation messages.
- Visible focus rings (inherited from the existing design system).
- Keyboard-only submission (Enter key) works on both views.

## Testing Strategy

### Backend unit tests (no Spring context)

- `PasswordResetTokenHasherTest`
  - 256 bits of entropy (length + charset assertion)
  - SHA-256 hash is deterministic
  - Generated tokens are unique across 1000 iterations
  - Hash of raw token never matches a different raw token
- `RequestPasswordResetHandlerTest`
  - Returns accepted result for unknown email
  - Returns accepted result for OAuth-only identity
  - Invalidate + create + event publish for local account
  - Email normalized before lookup
  - Does not dispatch event when `passwordRecoveryEnabled = false`
- `ResetPasswordHandlerTest`
  - Throws `InvalidPasswordResetTokenException` for unknown token hash
  - Throws `ExpiredPasswordResetTokenException` for expired token
  - Throws `UsedPasswordResetTokenException` for used token
  - Calls `consumeAndUpdatePassword` exactly once
  - Calls `revokeAllForPrincipal` after success
  - Does NOT issue new session
  - Throws `PasswordRecoveryDisabledException` when disabled

### Backend integration tests (R2DBC + Postgres test container)

- `R2dbcPasswordResetTokenRepositoryTest`
  - Insert + lookup by hash
  - Uniqueness violation on duplicate hash
  - `invalidateActiveTokens` marks only `used_at IS NULL` rows
  - `consumeAndUpdatePassword` returns `false` for expired token
  - `consumeAndUpdatePassword` returns `false` for used token
  - `consumeAndUpdatePassword` returns `true` and updates password hash in same tx
  - Rollback when password update fails
  - Two concurrent `consumeAndUpdatePassword` calls — exactly one returns `true`
  - FK cascade: deleting principal deletes reset tokens

### HTTP adapter tests (`@WebFluxTest`)

- `LocalAuthControllerPasswordRecoveryTest`
  - `POST /forgot-password` returns `202` with empty body
  - `POST /forgot-password` returns `400` for invalid email
  - `POST /forgot-password` returns `429` when IP rate limit exceeded
  - `POST /forgot-password` returns `429` when email rate limit exceeded
  - `POST /reset-password` returns `204` on success
  - `POST /reset-password` returns `400` with `INVALID_PASSWORD_RESET_TOKEN`
  - `POST /reset-password` returns `400` with `EXPIRED_PASSWORD_RESET_TOKEN`
  - `POST /reset-password` returns `400` with `USED_PASSWORD_RESET_TOKEN`
  - The three token-error responses share the same `detail` string
  - `POST /reset-password` returns `400` with `INVALID_PASSWORD` for short password
  - Both endpoints return `503` when password recovery is disabled

### BDD tests (Cucumber)

`server/smp/src/test/resources/features/identity-request-password-reset.feature`  
`server/smp/src/test/resources/features/identity-reset-password.feature`  
`server/smp/src/test/resources/features/identity-password-reset-persistence.feature`  
`server/smp/src/test/resources/features/identity-password-reset-notifications.feature`  
`server/smp/src/test/resources/features/identity-password-reset-security.feature`

Each scenario from the spec maps to a `@fast` Cucumber scenario. Use
existing `BddDatabaseSupport`, `BddSteps`, `WebTestClient` glue patterns.

### Frontend unit tests (Vitest)

- `auth.api.spec.ts` — `requestPasswordReset` and `resetPassword` contracts.
- `ForgotPasswordView.spec.ts` — validation, loading state, generic confirmation.
- `ResetPasswordView.spec.ts` — invalid-token state, password mismatch, success.

### Frontend E2E (Playwright)

`apps/web/app/e2e/specs/password-reset-frontend.spec.ts`:
- "Forgot password?" link on login
- Forgot password request happy path
- Generic confirmation for unknown email
- Reset password page with missing token
- Reset password page with mismatching passwords
- Successful reset and link to login
- Reset rejects an invalid token

## Delivery Sequence

| PR | Scope | Depends on |
|----|-------|-----------|
| **PR 1** Backend | Liquibase + domain + handlers + ports + adapters + endpoints + tests | — |
| **PR 2** Frontend | API client + views + router + i18n + tests | PR 1 merged |
| **PR 3** Hardening | Rate limit buckets, audit event, cleanup job, runbook | PR 1 (optional decoupled) |

PR 1 is the reviewable unit. PR 2 and PR 3 are optional and can land
later without re-opening PR 1.

## Risks

| Risk | Mitigation |
|------|-----------|
| `revokeAllForPrincipal` propagation into R2DBC transactional context | Verify against existing `RefreshSessionLifecycleService.rotate` which already runs in tx; reuse the same `TransactionalOperator` injection |
| `EventPublisher.publish` triggered before commit | Document the dispatch ordering (publish is a no-op for in-memory bus; integration with async bus is wired via `@Subscribe` listeners which are not invoked synchronously) |
| Email containing raw token is logged by SMTP provider | Use existing `EmailSender` abstraction; document `EmailProperties.publicAppUrl` requirement |
| Token leak via `application.yml` defaults | No defaults — token length is constant in code |
| Frontend storing token in analytics | Verified: token is read from `route.query.token` only; no analytics calls added |

## Rollback

1. Revert PR 1's `LocalAuthController` + Security config (endpoints
   disappear; `password_reset_tokens` table is left in place).
2. Optionally drop `password_reset_tokens` via Liquibase rollback tag.
3. Revert PR 2's route additions (login link is the only coupling).
4. No data migrations touch existing tables. Additive only.