# Tasks: Auth Security Hardening

## Review Workload Forecast

| Field                   | Value                                                   |
|-------------------------|---------------------------------------------------------|
| Estimated changed lines | ~280-350                                                |
| 400-line budget risk    | Medium                                                  |
| Chained PRs recommended | No                                                      |
| Suggested split         | Single PR — all changes are cohesive and interdependent |
| Delivery strategy       | single-pr                                               |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: N/A (single PR)
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal                                  | Likely PR | Notes                                               |
|------|---------------------------------------|-----------|-----------------------------------------------------|
| 1    | Auth security hardening — all changes | PR 1      | Modify handlers, add policy interface, update tests |

## Phase 1: Domain/Application Interfaces (Foundation)

- [x] 1.1 Create `identity/application/EmailVerificationPolicy.kt` with `AuthFeature` enum and
  `EmailVerificationPolicy` interface
- [x] 1.2 Create `DefaultEmailVerificationPolicy` implementation in same file
- [x] 1.3 Add `algorithm: String` property to `PasswordHasher.kt` interface
- [x] 1.4 Update `BCryptPasswordHasher.kt` to implement `algorithm = "bcrypt"`

## Phase 2: Core Implementation

- [x] 2.1 In `LocalAuthHandlers.kt`: Add `LocalJwtIssuer`, `RefreshSessionLifecycleService`, `Clock`
  dependencies to `RegisterUserHandler`
- [x] 2.2 In `LocalAuthHandlers.kt`: Modify `RegisterUserHandler.handle()` to call
  `issueAuthSession()` after creating user, return `LocalAuthSessionResult`
- [x] 2.3 In `LocalAuthHandlers.kt`: Remove `UnverifiedEmailException` check from
  `LoginUserHandler` (around lines 160-162)
- [x] 2.4 In `LocalAuthHandlers.kt`: Remove `UnverifiedEmailException` check from
  `RefreshUserSessionHandler` (around lines 196-198)
- [x] 2.5 In `LocalAuthApi.kt`: Add optional `requiresVerification: Boolean` property to
  `ResendVerificationResult`

## Phase 3: Infrastructure / HTTP Layer

- [x] 3.1 In `LocalAuthController.kt`: Change `register()` return type from
  `ResponseEntity<RegistrationResult>` to `ResponseEntity<AuthTokens>`
- [x] 3.2 In `LocalAuthController.kt`: Update `register()` to call `sessionResponse()` instead of
  returning verification instructions
- [x] 3.3 In `IdentityProblemDetailsHandler.kt`: Add `type` URI property to
  `UnverifiedEmailException` handler (
  `https://api.profiletailors.com/errors/email-verification-required`)
- [x] 3.4 In `IdentityProblemDetailsHandler.kt`: Add `code` property `"EMAIL_VERIFICATION_REQUIRED"`
  to `UnverifiedEmailException` handler
- [x] 3.5 In `IdentityProblemDetailsHandler.kt`: Update detail message to
  `"Please verify your email before using this feature."`

## Phase 4: Testing

- [x] 4.1 In `LocalAuthHandlersTest.kt`: Change "rejects login with unverified email" test to expect
  success instead
- [x] 4.2 In `LocalAuthHandlersTest.kt`: Change "rejects refresh with unverified email" test to
  expect success instead
- [x] 4.3 In `LocalAuthHandlersTest.kt`: Add new test for `RegisterUserHandler` returns
  `LocalAuthSessionResult` with tokens
- [x] 4.4 In `LocalAuthHandlersTest.kt`: Add test for
  `EmailVerificationPolicy.requiresVerification()` returns correct values per feature
- [x] 4.5 In `LocalAuthHandlersTest.kt`: Add test for `PasswordHasher.algorithm` returns `"bcrypt"`
- [x] 4.6 In `LocalAuthControllerTest.kt`: Update `register` test to assert `201` with `AuthTokens`
  payload and refresh cookie
- [x] 4.7 In `LocalAuthControllerTest.kt`: Update `login` test for PENDING user to assert `200` with
  tokens

## Phase 5: Integration Verification

- [x] 5.1 Run `./gradlew :server:smp:test --tests "*LocalAuthHandlersTest*"` and confirm all pass
- [x] 5.2 Run `./gradlew :server:smp:test --tests "*LocalAuthControllerTest*"` and confirm all pass
- [x] 5.3 Run `./gradlew :server:smp:check` to verify full build

## Dependencies

- `AuthSessionPort` interface already exists
- `LocalJwtIssuer`, `RefreshSessionLifecycleService`, `Clock` already available
- `Email` entity with `emailStatus` property exists

## Implementation Order

1. **Phase 1 first** — new interfaces are dependencies for nothing, but establish the design
   contract early
2. **Phase 2 second** — core handler changes are the main logic
3. **Phase 3 third** — controller and problem details handler wire the changes to HTTP
4. **Phase 4 fourth** — tests verify the new behavior
5. **Phase 5 last** — integration verification confirms everything works together
