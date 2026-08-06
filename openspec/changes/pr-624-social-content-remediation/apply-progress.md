# Apply Progress: PR #624 Social Content Foundation Remediation

## Overview

This document tracks the implementation progress for PR #624 social content foundation remediation. The work applies valid CodeRabbit findings to make the LinkedIn page social-content foundation type-safe, bounded, workspace-safe, and testable without changing PR #625 or shared/shield/ratelimit.

Delivery strategy: single-pr with explicitly approved size exception. Guarded paths preserved: shared/shield/ratelimit/** and PR #625 scope remain unchanged.

## Changes

### Phase 1: Domain and Configuration

- [x] 1.1 RED: Added focused tests for actor/comment invariants, sync limits, defensive payload equality, typed capability preservation, and reply/port contracts. Initial tests described behavior absent from the baseline.
- [x] 1.2 GREEN: Added SocialContentSyncLimits, required actor/body/publication invariants, defensive PayloadCache, and provider page-size parameters.
- [x] 1.3 VERIFY: Focused domain tests passed; fake provider call recording and repository write assertions remain green.
- [x] 1.4 RED: Added invalid 202600 and 202613 configuration cases.
- [x] 1.5 GREEN: API version validation now accepts only YYYYMM with calendar months 01..12; polling page/max-page limits are validated.
- [x] 1.6 VERIFY: Focused properties tests and publishing compilation passed.

### Phase 2: CQRS and Bounded State

- [x] 2.1 RED: Added denial/no-provider-call, dedicated-handler presence, repeated-cursor, max-page, checkpoint-preservation, resume-cursor, and high-water-mark tests.
- [x] 2.2 GREEN: Added dedicated discovery, post-sync, and comment-sync handlers. The existing façade remains only as a compatibility delegate and no Spring/mediator wiring was added.
- [x] 2.3 RED: Existing reply repository tests cover first atomic claim and subsequent PROCESSING replay; application tests cover reply validation and idempotent execution paths.
- [x] 2.4 GREEN: Added dedicated ReplyToSocialCommentCommandHandler.kt; moved reply execution out of the compatibility façade, persisted typed provider failures, and retained atomic fake-repository claims.
- [x] 2.5 VERIFY: Focused application, provider-fake, reply-repository, domain, properties, and Liquibase tests pass. Added regression coverage for high-water marks derived from newer received post/comment timestamps; fake comments honor cursor offsets and page-size bounds.

### Phase 3: Liquibase

- [x] 3.1 RED: Added static tests for migration inclusion, workspace/account composite constraints, workspace/post composite constraints, foreign keys, and rollback metadata.
- [x] 3.2 GREEN: Added additive 017-social-content-workspace-fks.yaml after unchanged migration 016; no existing migration history was rewritten.
- [x] 3.3 VERIFY: Static changelog tests pass. Live PostgreSQL proof is deferred to verification because no targeted database harness was run during apply.

### Phase 4: Review Closure

- [x] 4.1 Evidence: architecture-docs-sync.md is absent on this branch; no command or file was invented.
- [x] 4.2 Evidence: shared/shield/ratelimit/** and PR #625 paths remain unchanged.
- [x] 4.3 Evidence: mutationAllowed is already addressed by reconciliation behavior and existing model tests; no duplicate implementation was added.
- [x] 4.4 Evidence: No public HTTP endpoint exists for social content in this slice, so Cucumber coverage is not applicable. Static Liquibase tests cover the migration contract; live Postgres proof belongs to verify.
- [ ] 4.5 Post GitHub replies: Final external review replies to be posted as GitHub communication; evidence and decisions recorded in design.md and apply-progress.md.

### Phase 5: Final Verification

- [x] 5.1 Local tests: Full just backend-test-fast and focused publishing suite executed successfully during apply.
- [ ] 5.2 Live PostgreSQL/Liquibase verification: Deferred because Docker daemon is unavailable; static migration tests pass.

### Quality Continuation

- [x] Q.1 Resolved all 14 findings from server/smp/build/reports/detekt/detekt.md without suppressions: BracesOnWhenStatements in ReplyToSocialCommentCommandHandler.kt; three MagicNumber findings through named sync-limit constants and named defaults; ten MaxLineLength findings across changed production, test, and migration-test files through imports, multiline construction, and formatting.
- [x] Q.2 Preserved deterministic reply idempotency behavior for existing PROCESSING, SUCCEEDED, and FAILED results; the focused handler test covers all three states and verifies no provider call.
- [x] Q.3 Applied repository Spotless formatting; spotlessKotlinCheck, Detekt, compilation, and tests pass through just backend-check.
- [x] Q.4 Confirmed git diff --check passes and no files under shared/shield/ratelimit/** are modified.
- [ ] Q.5 Live PostgreSQL migration proof remains deferred because Docker is unavailable in this environment.
- [ ] Q.6 External GitHub review replies remain pending and were not represented as completed.

### Test Evidence

- ./gradlew :server:smp:compileKotlin --no-daemon: PASS
- Focused publishing suite covering SocialContentFoundationHandlersTest, SocialContentModelsTest, SocialContentPortsTest, FakeSocialContentProviderTest, FakeReplyCommandRepositoryTest, SocialContentPropertiesTest, SocialContentLiquibaseChangelogTest: PASS, BUILD SUCCESSFUL in 3s
- git diff --check: PASS
- just backend-test-fast: PASS, BUILD SUCCESSFUL in 3s
- ./gradlew :server:smp:test --tests SocialContentFoundationHandlersTest.should include newer post timestamps when provider high water mark is older --no-daemon: passed after HWM regression fix
- ./gradlew :server:smp:test --tests SocialContentFoundationHandlersTest.should include newer comment timestamps when provider high water mark is older --no-daemon: passed after comment HWM regression fix

## Usage

The implementation follows strict TDD: RED tests first, GREEN minimal implementation, VERIFY focused test execution. Dedicated CQRS handlers are directly constructible in tests without Spring wiring. The compatibility façade delegates to the new handlers.

Test construction pattern:
```kotlin
val handler = SyncSocialPostsCommandHandler(
    provider, postRepository, checkpointRepository,
    capabilityResolver, retention, syncLimits, retryPolicy
)
```

## Troubleshooting

- Cucumber: No public HTTP surface was added or found for this foundation-only remediation; no Cucumber scenario was invented. The direct handler/fake/static migration tests are the applicable proof.
- architecture-docs-sync.md: Not present on this branch; no command or file was invented.
- shared/shield/ratelimit/** and PR #625: Out of scope and unchanged.
- mutationAllowed: Prior issue is already addressed; no duplicate change was added.
- Live PostgreSQL proof: Docker daemon unavailable; static Liquibase tests pass, live proof deferred to environment with Docker.

## References

- Design document: design.md
- Task breakdown: tasks.md
- Verification report: verify-report.md
- Specification delta: specs/publishing/spec.md
- Migration: server/smp/src/main/resources/db/changelog/publishing/017-social-content-workspace-fks.yaml
