# Tasks: Argon2id Password Hash Migration

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 260-380 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Deliver schema, dual-hasher auth flow, and focused tests | PR 1 | Single branch; TDD sequence across unit and integration tests |

## Phase 1: Foundation

- [ ] 1.1 Modify `server/smp/src/main/resources/db/changelog/identity/003-create-local-password-credentials.yaml` to add nullable `password_algorithm`.
- [ ] 1.2 Update `server/smp/src/main/resources/db/changelog/data/dev/local_password_credentials_dev.csv` with `password_algorithm=bcrypt` seed data.
- [ ] 1.3 Extend `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalPasswordCredentialGateway.kt` with algorithm-aware create/find/update contracts.
- [ ] 1.4 Update `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcLocalPasswordCredentialGateway.kt` to persist, read, and update `password_algorithm`.

## Phase 2: Core Implementation

- [ ] 2.1 RED: add `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/Argon2idPasswordHasherTest.kt` for hash format, match, mismatch, malformed hash, and `algorithm`.
- [ ] 2.2 GREEN: create `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/Argon2idPasswordHasher.kt` with fail-closed Spring Security Argon2id behavior.
- [ ] 2.3 Modify `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityBootstrapConfiguration.kt` to make Argon2id the default `PasswordHasher` and expose `BCryptPasswordHasher`.
- [ ] 2.4 RED: extend `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` for registration algorithm persistence, BCrypt login compatibility, null-metadata fallback, rehash, and malformed-hash rejection.
- [ ] 2.5 GREEN: update `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` so registration writes `passwordHasher.algorithm` and login verifies/upgrades by resolved algorithm.

## Phase 3: Integration / Verification

- [ ] 3.1 Update `LocalAuthHandlersTest.kt` fakes to capture `passwordAlgorithm` and `updatePassword()` side effects for the new TDD scenarios.
- [ ] 3.2 Add integration coverage in `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt` for Argon2id registration persistence and successful login.
- [ ] 3.3 Add integration coverage in `LocalAuthEndpointIntegrationTest.kt` for BCrypt login upgrade, null-metadata inference fallback, and malformed-hash 401 behavior.
- [ ] 3.4 Run focused backend verification for the auth area covering the spec scenarios and atomic registration rollback expectations.

## Phase 4: Cleanup / Documentation

- [ ] 4.1 Confirm archived `design.md` and specs stay aligned with final task ordering and no stale YAML references remain.
- [ ] 4.2 Remove `tasks.yaml` after `tasks.md` becomes the authoritative artifact (if still present).
