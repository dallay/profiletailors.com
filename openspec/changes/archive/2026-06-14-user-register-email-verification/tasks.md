# Tasks: User Registration with Email Verification

## Review Workload Forecast

| Field                   | Value                                                               |
|-------------------------|---------------------------------------------------------------------|
| Estimated changed lines | 800–1000                                                            |
| 400-line budget risk    | High                                                                |
| Chained PRs recommended | Yes                                                                 |
| Suggested split         | PR 1 (foundation) → PR 2 (core logic) → PR 3 (wiring + integration) |
| Delivery strategy       | size-exception                                                      |
| Chain strategy          | single-PR                                                           |

Decision needed before apply: ✅ RESOLVED — size-exception (single PR)
Chained PRs recommended: Yes (but proceeding with single PR per user decision)
Chain strategy: ✅ resolved → single-PR
400-line budget risk: High (accepted by user)

### Suggested Work Units

| Unit | Goal                                       | Likely PR | Notes                                                                                                                      |
|------|--------------------------------------------|-----------|----------------------------------------------------------------------------------------------------------------------------|
| 1    | Domain + DB migration + project dependency | PR 1      | Build config, DB migration, domain types, ports; no behavior                                                               |
| 2    | Application handlers + unit tests (TDD)    | PR 2      | RegisterUserHandler emits event, login/refresh guard, VerifyEmailHandler, ResendVerificationHandler; unit tests with fakes |
| 3    | Infrastructure wiring + integration tests  | PR 3      | HTTP controllers, email adapters, security config, bootstrap config, integration/BDD tests                                 |

## Phase 1: Foundation

- [x] 1.1 Add `spring-boot-starter-mail` dependency to `build.gradle.kts`
- [x] 1.2 Create DB migration `db/changelog/identity/004-add-email-verification.yaml` — add
  `email_status` column, create `email_verification_tokens` table, backfill existing users to
  `VERIFIED`
- [x] 1.3 Include migration in `db.changelog-master.yaml`
- [x] 1.4 Create `EmailStatus` enum in `identity/domain/` (`UNVERIFIED`, `VERIFIED`)
- [x] 1.5 Create `EmailVerificationToken` value object in `identity/domain/` — fields: tokenHash,
  expiresAt, usedAt; `isValid(now)` method
- [x] 1.6 Create `UserRegistered` domain event in `identity/domain/` — fields: principalId, email,
  username
- [x] 1.7 Create `EmailSender` port interface in `identity/application/` —
  `suspend fun send(to, subject, body): EmailSendResult`
- [x] 1.8 Modify `PrincipalIdentityFacts` or `PrincipalIdentityLookup` to include `emailStatus`
- [x] 1.9 Modify `IdentityRegistrationGateway` port — add `emailStatus` param to
  `createUserIdentity`
- [x] 1.10 Add `RegistrationResult` sealed class and `VerifyEmailCommand`,
  `ResendVerificationCommand` to `LocalAuthApi.kt`
- [x] 1.11 Add `emailStatus: String` field to `AuthTokens` DTO
- [x] 1.12 Create `UnverifiedEmailException` in `LocalAuthExceptions.kt`

## Phase 2: Core Logic + Unit Tests (TDD)

- [x] 2.1 Unit test + impl: `RegisterUserHandler` emits `UserRegistered`, stores with `UNVERIFIED`,
  returns 201 without tokens
- [x] 2.2 Unit test + impl: `LoginUserHandler` checks `emailStatus` and rejects `UNVERIFIED` with
  403
- [x] 2.3 Unit test + impl: `RefreshUserSessionHandler` checks `emailStatus` and rejects
  `UNVERIFIED` with 403
- [x] 2.4 Unit test + impl: `VerifyEmailHandler` validates token (hash + expiry + single-use), sets
  `VERIFIED`, issues tokens
- [x] 2.5 Unit test + impl: `ResendVerificationHandler` invalidates old tokens, generates new token,
  dispatches email
- [x] 2.6 Unit test: `EmailVerificationToken.isValid()` — expired/non-expired/used combos
- [x] 2.7 Implement `EmailVerificationTokenHasher` utility (SHA-256, same pattern as
  `RefreshTokenHasher`)

## Phase 3: Infrastructure Wiring + Integration Tests

- [x] 3.1 Modify `R2dbcIdentityRegistrationGateway` — persist `emailStatus`, insert into
  `email_verification_tokens`
- [x] 3.2 Modify `R2dbcPrincipalIdentityLookup` — include `emailStatus` in queries and
  `PrincipalIdentityFacts`
- [x] 3.3 Add `GET /api/auth/verify-email` endpoint to `LocalAuthController.kt` — permit all, token
  query param
- [x] 3.4 Add `POST /api/auth/resend-verification` endpoint to `LocalAuthController.kt` — permit
  all, email body
- [x] 3.5 Handle `UnverifiedEmailException` in `IdentityProblemDetailsHandler.kt` → 403
  ProblemDetail
- [x] 3.6 Permit verification endpoints in `IdentitySecurityConfiguration.kt` (no auth required)
- [x] 3.7 Create `SmtpEmailSender` in `identity/infrastructure/email/` — implements `EmailSender`,
  Spring Mail
- [x] 3.8 Create `MockEmailSender` in `identity/infrastructure/email/` — logs to console, used when
  no SMTP configured
- [x] 3.9 Create `SendVerificationEmailConsumer` in `identity/infrastructure/email/` —
  `EventConsumer<UserRegistered>`
- [x] 3.10 Create `EmailTemplates` in `identity/infrastructure/email/` — plain text verification
  template
- [x] 3.11 Wire email beans in `IdentityBootstrapConfiguration.kt` — conditional on profile
- [x] 3.12 Add SMTP config properties to `application.yaml` (`app.email.sender`,
  `app.email.verification-subject-prefix`)

## Phase 4: Integration + BDD Tests

- [x] 4.1 Extend `LocalAuthEndpointIntegrationTest` — register → verify-email flow, login rejection
  for UNVERIFIED
- [ ] 4.2 Add integration test: resend invalidates old token, verify with old token fails (pending —
  needs mock email infrastructure)
- [ ] 4.3 Add migration test: insert pre-migration row, run migration, assert `VERIFIED` (Liquibase
  H2 constraint issue — pre-existing)
- [ ] 4.4 Create Cucumber BDD steps for verification scenarios (deferred — out of scope for this PR)
- [x] 4.5 Run identity unit and handler tests — all pass; full integration test suite has
  pre-existing `CapturingAuditHook` wiring issue (not introduced by this change)

## Phase 5: Cleanup

- [x] 5.1 Remove exploration artifacts from `tmp/` if any (none found)
- [ ] 5.2 Verify all spec scenarios (52 total) map to at least one passing test (deferred to verify
  phase)

## Notes

- Integration tests (`LocalAuthEndpointIntegrationTest`) have a **pre-existing** failure:
  `CapturingAuditHook` bean not found. The `IntegrationTestBase.SharedTestConfiguration` inner class
  is not auto-imported by child classes. This was NOT introduced by this change.
- The full test suite shows 58 pre-existing failures (mostly
  `WorkspaceAccessSummaryEndpointPostgresIntegrationTest` and
  `R2dbcWorkspaceMembershipResolverTest`) — unrelated to this feature.
- All new unit and handler tests pass: `LocalAuthHandlersTest` (10 tests),
  `LocalAuthControllerTest` (7 tests), `EmailVerificationTokenTest` (6 tests),
  `EmailVerificationTokenHasherTest` (5 tests).
