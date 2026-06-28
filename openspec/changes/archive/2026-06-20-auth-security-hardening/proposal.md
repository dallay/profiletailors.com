# Proposal: Auth Security Hardening

## Intent

Harden authentication flows in the identity bounded context to improve security posture and user
experience. This change addresses four gaps: (1) login should work for users with
`emailStatus = PENDING`, (2) registration should return session tokens immediately, (3) problem
detail responses need structured error codes, and (4) the codebase needs extensibility points for
future email verification gating and Argon2id password hashing migration.

## Scope

### In Scope

- Remove blocking check for `PENDING` email status in `LoginUserHandler` and
  `RefreshUserSessionHandler`
- Modify `RegisterUserHandler` to issue auth session tokens on successful registration
- Enhance `IdentityProblemDetailsHandler` with machine-readable `code` property
- Create `EmailVerificationPolicy` interface in domain/application layer (designed, not implemented)
- Design `Argon2id` password hashing with `algorithm` property on `PasswordHasher` interface

### Out of Scope

- Feature gating implementation (policy is defined, not wired into auth handlers)
- Argon2id implementation and database migration
- OAuth provider support
- Frontend changes (token handling, UI updates)
- Rate limiting on resend verification

## Capabilities

### New Capabilities

- `identity-email-pending-login`: Allow users with `emailStatus = PENDING` to authenticate and
  receive tokens
- `identity-registration-tokens`: Registration endpoint returns JWT access token and refresh cookie
  immediately

### Modified Capabilities

- `identity-auth`: Update login and refresh scenarios to permit `PENDING` status (previously only
  `VERIFIED`)

## Approach

### Login for PENDING Users

Remove the email status gate from `LoginUserHandler` and `RefreshUserSessionHandler`. The
`EmailVerificationPolicy` interface defines the contract for future feature gating but is not wired
in yet — auth handlers accept all non-expired users regardless of email status.

### Registration Returns Tokens

Add `AuthSessionPort` as a dependency to `RegisterUserHandler`. After persisting the user, call
`issueAuthSession()` to return JWT and set refresh cookie. Response changes from `201 Created` with
verification instructions to `201 Created` with auth tokens and user profile.

### Error Codes in Problem Details

Extend `IdentityProblemDetailsHandler` to include a `code` field in the RFC 9457 problem detail
response. New code `EMAIL_VERIFICATION_REQUIRED` maps to the existing `403 Forbidden` response for
unverified email scenarios.

### EmailVerificationPolicy Interface

```kotlin
interface EmailVerificationPolicy {
    enum class VerificationRequirement { REQUIRED, NOT_REQUIRED, PENDING_ALLOWED }
    fun getRequirement(emailStatus: EmailStatus): VerificationRequirement
}
```

Located in `identity/domain/` or `identity/application/` following hexagonal layering.

### Argon2id Migration Design

Add `algorithm: PasswordAlgorithm` property to `PasswordHasher` interface:

```kotlin
interface PasswordHasher {
    val algorithm: PasswordAlgorithm
    suspend fun hash(plaintext: String): PasswordHash
    suspend fun verify(plaintext: String, hash: PasswordHash): Boolean
}
enum class PasswordAlgorithm { BCRYPT, ARGON2ID }
```

Migration support deferred to future change; interface designed for multi-algorithm lookup.

## Affected Areas

| Area                                                                  | Impact   | Description                                                    |
|-----------------------------------------------------------------------|----------|----------------------------------------------------------------|
| `server/smp/identity/application/LocalAuthHandlers.kt`                | Modified | Login/refresh handlers accept PENDING; register issues session |
| `server/smp/identity/infrastructure/LocalAuthApi.kt`                  | Modified | Registration response DTO includes tokens                      |
| `server/smp/identity/infrastructure/LocalAuthController.kt`           | Modified | Controller passes auth context to handlers                     |
| `server/smp/identity/infrastructure/IdentityProblemDetailsHandler.kt` | Modified | Adds `code` field to problem detail responses                  |
| `server/smp/identity/domain/`                                         | New      | `EmailVerificationPolicy.kt` interface                         |
| `server/smp/identity/domain/`                                         | Modified | `PasswordHasher.kt` interface gets `algorithm` property        |
| `server/smp/identity/application/LocalAuthHandlersTest.kt`            | Modified | Update test cases for PENDING login                            |
| `server/smp/identity/infrastructure/LocalAuthControllerTest.kt`       | Modified | Test registration returns tokens                               |

## Risks

| Risk                                                           | Likelihood | Mitigation                                                              |
|----------------------------------------------------------------|------------|-------------------------------------------------------------------------|
| Breaking API change: registration response now includes tokens | Medium     | Document in spec delta; client migration guide                          |
| Users bypass email verification entirely                       | Low        | Feature gate deferred; policy interface exists for future wiring        |
| Error code migration breaks existing clients                   | Low        | `code` field is additive; existing responses unchanged except new field |

## Rollback Plan

- `git revert <commit>` restores original behavior for all files
- No database migration required (no schema changes in this proposal)
- Existing user sessions unaffected (refresh token semantics unchanged)
- Clients expecting registration without tokens will receive extra fields (backward-compatible
  addition)

## Dependencies

- `identity/domain/Email` entity with `emailStatus` property already exists
- `identity/application/AuthSessionPort` interface already exists for session issuance
- `LocalAuthExceptions.kt` already defines `EmailVerificationRequiredException`

## Success Criteria

- [ ] Login returns tokens for users with `emailStatus = PENDING`
- [ ] Registration returns `201` with JWT access token and refresh cookie
- [ ] Problem detail responses include `code: "EMAIL_VERIFICATION_REQUIRED"` for unverified
  scenarios
- [ ] `EmailVerificationPolicy` interface compiles and is importable
- [ ] `PasswordHasher` interface includes `algorithm` property
- [ ] `LocalAuthHandlersTest` passes for PENDING login scenarios
- [ ] `LocalAuthControllerTest` passes for registration token scenarios
