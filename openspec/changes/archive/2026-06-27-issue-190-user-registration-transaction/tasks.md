# Tasks: Atomic User Registration Reactive Transaction (Issue #190)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 70-120 lines |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Extract runner & wrap registration in transaction | PR 1 | Complete atomic transaction protection with unit and integration rollback tests |

## Phase 1: Foundation & Shared Extraction

- [ ] 1.1 Create `shared/common/src/main/kotlin/com/profiletailors/common/domain/persistence/AtomicTransactionRunner.kt` with `runAtomically` interface.
- [ ] 1.2 Update imports in `R2dbcAtomicTransactionRunner.kt`, `MediaHandlers.kt`, `StaleAssetReconciler.kt`, and `MediaCasHandlersTest.kt` to point to the new domain package.
- [ ] 1.3 Remove redundant interface at `com.profiletailors.smp.media.application.AtomicTransactionRunner`.
- [ ] 1.4 Run `./gradlew test` to verify media module builds cleanly after extraction.

## Phase 2: Core Handler Implementation (TDD)

- [ ] 2.1 Add failing unit test in `LocalAuthHandlersTest.kt` asserting `RegisterUserHandler` wraps DB inserts in `runAtomically` and defers event/session logic.
- [ ] 2.2 Inject `AtomicTransactionRunner` into `RegisterUserHandler` in `LocalAuthHandlers.kt` and wrap registration insertions inside `runAtomically`.
- [ ] 2.3 Verify `LocalAuthHandlersTest.kt` unit tests pass using `NoopAtomicTransactionRunner`.

## Phase 3: Integration & Rollback Verification

- [ ] 3.1 Add failing integration test in `LocalAuthEndpointIntegrationTest.kt` demonstrating mid-registration failure causes complete DB rollback and suppresses side effects.
- [ ] 3.2 Execute `./gradlew test --tests LocalAuthEndpointIntegrationTest` to verify atomic rollback passes against R2DBC stack.

## Phase 4: Verification & Quality Check

- [ ] 4.1 Run `./gradlew check` to verify detekt compliance and all backend unit/integration tests pass.
- [ ] 4.2 Validate implementation against scenarios in `openspec/changes/2026-06-27-issue-190-user-registration-transaction/specs/registration/spec.md`.
