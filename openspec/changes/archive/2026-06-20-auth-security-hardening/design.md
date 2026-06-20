# Design: Auth Security Hardening

## Technical Approach

Enable unverified users to authenticate and receive tokens immediately after registration, while adding structured error codes for future feature gating. The change removes email verification gates from login/refresh flows, adds session token issuance to registration, and establishes interfaces for future extensibility (feature-gating policy, Argon2id migration).

## Architecture Decisions

### Decision: Allow PENDING Users to Authenticate

**Choice**: Remove `emailStatus` checks from `LoginUserHandler` and `RefreshUserSessionHandler`

**Alternatives considered**:
- Keep checks and add a feature flag — adds complexity without immediate benefit
- Defer to future feature gating — blocks current UX requirement

**Rationale**: The `EmailVerificationPolicy` interface provides the extensibility point for future feature gating. The current authentication flow should be inclusive (all authenticated users get tokens regardless of email status).

### Decision: Registration Issues Tokens Immediately

**Choice**: `RegisterUserHandler` calls `issueAuthSession()` after persisting user

**Alternatives considered**:
- Separate handler for post-registration token issuance — adds indirection
- Return `RegistrationResult` from handler and let controller issue tokens — breaks the handler abstraction

**Rationale**: `RegisterUserHandler` already has all dependencies needed (`LocalJwtIssuer`, `RefreshSessionLifecycleService`, `Clock`). Adding these makes the handler self-sufficient and matches the pattern already used in `LoginUserHandler`.

### Decision: RFC 9457 Problem Detail with `code` Property

**Choice**: Add `code` string and `type` URI to `UnverifiedEmailException` problem detail

**Alternatives considered**:
- Use existing `title` field for code — not machine-readable
- Create custom error response DTO — breaks RFC 9457 standard

**Rationale**: RFC 9457 (`Problem Detail for HTTP APIs`) supports custom properties via `setProperty()`. Adding `code` and `type` provides stable, structured error codes for frontend logic without breaking standards compliance.

### Decision: `EmailVerificationPolicy` in Application Layer

**Choice**: Define `EmailVerificationPolicy` interface and `AuthFeature` enum in `identity/application/`

**Alternatives considered**:
- Define in domain layer — domain should not know about features
- Define in infrastructure layer — policy is application-level concern

**Rationale**: Feature gating is an application-level concern (which features require verification). The domain layer remains pure; the application layer composes domain services with business rules.

### Decision: `algorithm` Property on `PasswordHasher` Interface

**Choice**: Add `val algorithm: String` property to `PasswordHasher` interface

**Alternatives considered**:
- Separate `PasswordHasherV2` interface — interface proliferation
- Store algorithm prefix in hash string — fragile, requires parsing

**Rationale**: Simple string property enables multi-algorithm support with minimal interface change. BCrypt implementation returns `"bcrypt"`; future Argon2id returns `"argon2id"`.

## Data Flow

### Registration with Session Issuance

```
Client ──POST /api/auth/register──→ LocalAuthController
                                          │
                                          ▼
                               RegisterUserHandler.handle()
                                          │
            ┌──────────────────────────────┼──────────────────────────────┐
            ▼                              ▼                              ▼
   Persist User Identity        Persist Credential           Provision Workspace
            │                              │                              │
            └──────────────────────────────┼──────────────────────────────┘
                                          │
                                          ▼
                            Create EmailVerificationToken
                                          │
                                          ▼
                                 EventPublisher.publish()
                                          │
                                          ▼
                            issueAuthSession() ◄──────────┐
                                          │               │
                    ┌─────────────────────┼───────────────┘
                    ▼                   ▼
          localJwtIssuer.issue()  refreshSessionLifecycleService.issue()
                    │                   │
                    └────────┬──────────┘
                             ▼
                   LocalAuthSessionResult
                             │
                             ▼
                  ResponseEntity<AuthTokens> + Set-Cookie
```

### Login Flow (PENDING Users)

```
Client ──POST /api/auth/login──→ LocalAuthController
                                       │
                                       ▼
                            LoginUserHandler.handle()
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                      ▼
         Validate Password                       Lookup PrincipalIdentity
                    │                                      │
                    └──────────────────┬──────────────────┘
                                       ▼
                              issueAuthSession()
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                      ▼
         JWT Access Token                          Refresh Cookie
                    │                                      │
                    └──────────────────┬──────────────────┘
                                       ▼
                            ResponseEntity<AuthTokens>
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` | Modify | Remove email status guard from `LoginUserHandler` (L160-162) and `RefreshUserSessionHandler` (L196-198); add session-issuance deps to `RegisterUserHandler` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt` | Modify | Change `register()` return type from `RegistrationResult` to `AuthTokens`; call `sessionResponse()` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/IdentityProblemDetailsHandler.kt` | Modify | Add `code` and `type` properties to `UnverifiedEmailException` handler |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/EmailVerificationPolicy.kt` | Create | `EmailVerificationPolicy` interface and `AuthFeature` enum |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/PasswordHasher.kt` | Modify | Add `val algorithm: String` property |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/BCryptPasswordHasher.kt` | Modify | Implement `algorithm` property returning `"bcrypt"` |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` | Modify | Update PENDING login tests; add registration-with-tokens test |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthControllerTest.kt` | Modify | Assert registration returns `AuthTokens` + cookie |

## Interfaces / Contracts

### New: `EmailVerificationPolicy`

```kotlin
package com.profiletailors.smp.identity.application

enum class AuthFeature {
    PUBLISH_CONTENT,
    SCHEDULE_POST,
    INVITE_TEAM,
    CONNECT_SOCIAL,
    ACCESS_BILLING,
    ENABLE_AUTOMATIONS,
}

interface EmailVerificationPolicy {
    fun requiresVerification(feature: AuthFeature): Boolean
}

class DefaultEmailVerificationPolicy : EmailVerificationPolicy {
    private val restrictedFeatures = setOf(
        AuthFeature.PUBLISH_CONTENT,
        AuthFeature.SCHEDULE_POST,
        AuthFeature.INVITE_TEAM,
        AuthFeature.CONNECT_SOCIAL,
        AuthFeature.ACCESS_BILLING,
        AuthFeature.ENABLE_AUTOMATIONS,
    )
    
    override fun requiresVerification(feature: AuthFeature): Boolean =
        feature in restrictedFeatures
}
```

### Modified: `PasswordHasher`

```kotlin
package com.profiletailors.smp.identity.application

interface PasswordHasher {
    val algorithm: String  // "bcrypt" or "argon2id"
    fun hash(rawPassword: String): String
    fun matches(rawPassword: String, passwordHash: String): Boolean
}
```

### New: `UnverifiedEmailException` Problem Detail Enhancement

```kotlin
@ExceptionHandler(UnverifiedEmailException::class)
fun handle(exception: UnverifiedEmailException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN,
        "Please verify your email before using this feature."
    ).apply {
        title = "Email verification required"
        type = URI("https://api.profiletailors.com/errors/email-verification-required")
        setProperty("code", "EMAIL_VERIFICATION_REQUIRED")
    }
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `LoginUserHandler` accepts PENDING email | Add test with `EmailStatus.PENDING` asserting no exception |
| Unit | `RefreshUserSessionHandler` accepts PENDING email | Add test with `EmailStatus.PENDING` asserting no exception |
| Unit | `RegisterUserHandler` returns `LocalAuthSessionResult` | Add test asserting tokens present, event published |
| Unit | `EmailVerificationPolicy` returns correct values | Test enum coverage, default policy behavior |
| Unit | `PasswordHasher.algorithm` property | Test BCrypt returns `"bcrypt"` |
| Controller | Registration returns `201` with `AuthTokens` + cookie | Extend `LocalAuthControllerTest` |
| Controller | Login with PENDING returns `200` | Extend `LocalAuthControllerTest` |
| Integration | Full registration flow with token extraction | BDD test with real DB (optional) |

### Test Fixtures Required

- `FakeEmailVerificationPolicy`: returns configurable boolean per `AuthFeature`
- Update `FakePasswordHasher` with `algorithm = "fake"`

## Migration / Rollout

**No database migration required.** The change:
- Removes a conditional check (email status guard)
- Adds new behavior (session issuance on register)
- Adds new interface (policy, algorithm property)

All existing data remains valid. No schema changes.

**Rollback**: `git revert` restores:
- Email status guards in login/refresh handlers
- `RegistrationResult` return type (no tokens)
- Clean `UnverifiedEmailException` handler

**Feature Flags**: None required for this phase. Future feature gating will use `EmailVerificationPolicy`.

## Open Questions

- [ ] Should `EmailVerificationPolicy` be injected into handlers immediately, or remain a design-only artifact?
- [ ] Do we want to keep the `UnverifiedEmailException` path alive for future feature-gated endpoints, or remove the exception entirely?
- [ ] Is `LocalAuthSessionResult` the correct return type for registration, or should we create a dedicated `RegistrationResultWithTokens` type?
