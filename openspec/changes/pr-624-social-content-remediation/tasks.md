# Tasks: PR #624 Social Content Foundation Remediation

## Overview

This document breaks down the implementation tasks for PR #624 social content foundation remediation. The work was delivered as a single PR with an explicitly approved size exception.

Estimated changed lines: 500-700
400-line budget risk: High (approved by user)
Delivery strategy: single-pr
Chain strategy: size-exception

No-touch guard: Never modify shared/shield/ratelimit/**, PR #625, or unrelated rate-limiting behavior. Do not add Spring/mediator wiring or HTTP surface.

## Changes

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Contracts, invariants, limits, month validation | PR 1 | Tests and focused verification. |
| 2 | Handlers, bounded sync, replies, fakes | PR 2 | Depends on Unit 1. |
| 3 | Workspace-safe migration | PR 3 | Depends on migration proof. |

Decision: User approved single-pr with size exception; all units delivered together.

### Phase 1: Domain and Configuration

- [x] 1.1 RED: Add failing cases in server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModelsTest.kt and SocialContentPortsTest.kt for typed failures, limits, invariants, expiry, byte equality, reply transitions, and ports.
- [x] 1.2 GREEN: Modify server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModels.kt and SocialContentPorts.kt with minimum contracts/invariants.
- [x] 1.3 VERIFY: Run focused domain tests; confirm no provider calls or writes.
- [x] 1.4 RED: Add 202600/202613 failures in server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/socialcontent/SocialContentPropertiesTest.kt.
- [x] 1.5 GREEN: Update SocialContentProperties.kt for calendar-month validation and sync limits.
- [x] 1.6 VERIFY: Run focused properties tests and publishing test compilation.

### Phase 2: CQRS and State

- [x] 2.1 RED: Extend server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/SocialContentFoundationHandlersTest.kt for boundaries, denial/no-call, pagination, repeated cursor, max pages, resume/HWM, checkpoints, and no-write behavior.
- [x] 2.2 GREEN: Create DiscoverSocialActorsQueryHandler.kt, SyncSocialPostsCommandHandler.kt, and SyncSocialCommentsCommandHandler.kt; replace SocialContentFoundationHandlers.kt without Spring wiring.
- [x] 2.3 RED: Add reply-state/conflict tests in SocialContentFoundationHandlersTest.kt and server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/fake/FakeReplyCommandRepositoryTest.kt.
- [x] 2.4 GREEN: Create ReplyToSocialCommentCommandHandler.kt; update FakeReplyCommandRepository.kt, FakeSocialContentProvider.kt, and ports for atomic claim/one terminal save.
- [x] 2.5 VERIFY: Run focused application/fake tests, workspace isolation, and page-size/cursor assertions. High-water marks now incorporate both provider metadata and received item timestamps.

### Phase 3: Liquibase

- [x] 3.1 RED: Extend server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/SocialContentLiquibaseChangelogTest.kt for 017, inclusion, composite constraints, indexes, and rollback.
- [x] 3.2 GREEN: Create server/smp/src/main/resources/db/changelog/publishing/017-social-content-workspace-fks.yaml; include after 016 in server/smp/src/main/resources/db/changelog/db.changelog-master.yaml, preserving 016 history.
- [x] 3.3 VERIFY: Run static Liquibase tests; live Postgres proof remains a verify-phase concern because the available harness requires infrastructure startup.

### Phase 4: Review Closure Evidence and Reply Planning

- [x] 4.1 Evidence recorded: architecture-docs-sync.md is absent on this branch; no command or file was invented. Scope: PR #624 only.
- [x] 4.2 Evidence recorded: shared/shield/ratelimit/** and PR #625 paths remain unchanged. Scope: out of scope for this remediation.
- [x] 4.3 Evidence recorded: mutationAllowed is already addressed by reconciliation behavior and existing model tests; no duplicate implementation was added.
- [x] 4.4 Evidence recorded: No public HTTP endpoint exists for social content in this slice, so Cucumber coverage is not applicable. Static Liquibase tests cover the migration contract; live Postgres proof belongs to verify.
- [ ] 4.5 Post GitHub replies: Final external review replies remain an external GitHub action; evidence and decisions documented in design.md, apply-progress.md, and verify-report.md.

### Phase 5: Final Verification

- [x] 5.1 Local test execution: Full just backend-test-fast and focused publishing suite executed successfully during apply; all local quality checks pass.
- [ ] 5.2 Live PostgreSQL/Liquibase verification: Deferred because Docker daemon is unavailable; static migration tests pass; live proof required before production deployment.

### Apply Quality Continuation

- [x] Q.1 Resolve all 14 local Detekt findings from server/smp/build/reports/detekt/detekt.md without suppressions, including braces, named constants, and max-line formatting in changed files.
- [x] Q.2 Preserve and verify deterministic reply idempotency for persisted PROCESSING, SUCCEEDED, and FAILED results; focused publishing tests pass without a second provider call.
- [x] Q.3 Run Spotless formatting/checks, focused publishing tests, just backend-check, and git diff --check; all local quality checks pass.
- [ ] Q.4 Run live PostgreSQL/Liquibase proof when Docker/infrastructure is available; do not mark complete from this continuation.
- [ ] Q.5 Post external GitHub review replies with evidence; do not mark complete from this continuation.

## Usage

Execute phases in order: domain/config, CQRS/state, Liquibase, review-closure, verification. Each phase follows RED-GREEN-VERIFY cycle. Tests construct handlers directly without Spring wiring.

Phase progression:
1. Domain and configuration: types, invariants, limits
2. CQRS and state: handlers, pagination, checkpoints, replies
3. Liquibase: migration, static tests
4. Review closure: evidence collection, reply planning
5. Final verification: local tests, live database proof

## Troubleshooting

- Incomplete tasks: Phase 4.5 (GitHub replies) and 5.2 (live PostgreSQL) are external dependencies, not implementation failures.
- Docker unavailable: Static Liquibase tests pass; live proof deferred to environment with operational Docker daemon.
- Evidence vs. replies: Items 4.1-4.4 represent evidence collected and decisions made; actual GitHub communication is item 4.5.
- Test failures: If focused tests fail, verify handler construction matches documented patterns in design.md.

## References

- Design: design.md
- Implementation progress: apply-progress.md
- Verification report: verify-report.md
- Specification delta: specs/publishing/spec.md
- Handlers: server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/
- Migration: server/smp/src/main/resources/db/changelog/publishing/017-social-content-workspace-fks.yaml
