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
- [x] 2.2 GREEN: Create the dedicated discovery, post-sync, and comment-sync handler files with explicit query/command objects; replace SocialContentFoundationHandlers.kt without Spring wiring.
- [x] 2.3 RED: Add reply-state/conflict tests in SocialContentFoundationHandlersTest.kt and server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/fake/FakeReplyCommandRepositoryTest.kt.
- [x] 2.4 GREEN: Create ReplyToSocialCommentCommandHandler.kt; update FakeReplyCommandRepository.kt, FakeSocialContentProvider.kt, and ports for atomic claim/one terminal save.
- [x] 2.5 VERIFY: Run focused application/fake tests, workspace isolation, and page-size/cursor assertions. High-water marks now incorporate both provider metadata and received item timestamps.
- [x] 2.6 REVIEW FIX: Added explicit DiscoverSocialContentActorsQuery, SyncSocialPostsCommand, and SyncSocialCommentsCommand boundaries, renamed dedicated handlers to DiscoverSocialContentActorsHandler, ImportSocialPostsHandler, and ImportSocialCommentsHandler, and retained the compatibility façade.
- [x] 2.7 REVIEW FIX: Isolated comment checkpoints by post in domain/ports/handler tests and added migration coverage for the persistence contract.

### Phase 3: Liquibase

- [x] 3.1 RED: Extend server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/SocialContentLiquibaseChangelogTest.kt for 017, inclusion, composite constraints, indexes, and rollback.
- [x] 3.2 GREEN: Create server/smp/src/main/resources/db/changelog/publishing/017-social-content-workspace-fks.yaml; include after 016 in server/smp/src/main/resources/db/changelog/db.changelog-master.yaml, preserving 016 history.
- [x] 3.3 VERIFY: Run static Liquibase tests; live Postgres proof was completed later during verification with the available Testcontainers harness.
- [x] 3.4 REVIEW FIX: Added publishing-018 to add post_id, post-scoped checkpoint indexes, workspace/post foreign-key protection, and rollback while preserving the existing 017 composite-FK approach.

### Phase 4: Review Closure Evidence and Reply Planning

- [x] 4.1 Evidence recorded: architecture-docs-sync.md is absent on this branch; no command or file was invented. Scope: PR #624 only.
- [x] 4.2 Evidence recorded: shared/shield/ratelimit/** and PR #625 paths remain unchanged. Scope: out of scope for this remediation.
- [x] 4.3 Evidence recorded: mutationAllowed is already addressed by reconciliation behavior and existing model tests; no duplicate implementation was added.
- [x] 4.4 Evidence recorded: No public HTTP endpoint exists for social content in this slice, so Cucumber coverage is not applicable. Static Liquibase tests cover the migration contract; live Postgres proof belongs to verify.
- [ ] 4.5 Post GitHub replies: Final external review replies remain an external GitHub action; evidence and decisions documented in design.md, apply-progress.md, and verify-report.md.

### Phase 5: Final Verification

- [x] 5.1 Local test execution: Full just backend-test-fast and focused publishing suite executed successfully during apply; all local quality checks pass.
- [x] 5.2 Live PostgreSQL/Liquibase verification: `just backend-test-postgres` completed successfully with Docker/OrbStack available; the Testcontainers harness applied the Liquibase baseline and passed PostgreSQL integration tests.

### Phase 6: Review Remediation (CodeRabbit findings)

- [x] 6.1 FIX 1 (P1): Bind reply commands to the executing actor — `handle()` throws `ReplyRejectedException(EXECUTOR_MISMATCH)` when `command.actorId != actor.id`; provider is never called.
- [x] 6.2 FIX 2 (P1): Deterministic idempotency — add non-mutating `ReplyCommandRepository.find()`; return stored SUCCEEDED/FAILED/PROCESSING results before current-state validation; invalid commands never create PROCESSING records. Hardens Q.2.
- [x] 6.3 FIX 3 (P1): Migration 018 stores `post_id` as `varchar(255)` and drops the impossible local FK; rollback drops the FK block too.
- [x] 6.4 FIX 4 (P1): Migration 018 rollback deletes `COMMENTS` checkpoints before restoring the old unique constraint (deterministic).
- [x] 6.5 FIX 6 (P2): Cap exponential backoff at 60s (`MAX_BACKOFF_SHIFT = 20`) to prevent Long overflow at attempt 58+.
- [x] 6.6 FIX 7 (P2): Correct the outdated `ImportSocialPostsHandler` usage example in apply-progress.md.
- [x] 6.7 FIX 5 (policy): Document the BDD exception — this slice has no HTTP surface, so Cucumber coverage is not applicable; exception recorded in apply-progress.md.

### Apply Quality Continuation

- [x] Q.1 Resolve all 14 local Detekt findings from server/smp/build/reports/detekt/detekt.md without suppressions, including braces, named constants, and max-line formatting in changed files.
- [x] Q.2 Preserve and verify deterministic reply idempotency for persisted PROCESSING, SUCCEEDED, and FAILED results; focused publishing tests pass without a second provider call.
- [x] Q.3 Run Spotless formatting/checks, focused publishing tests, just backend-check, and git diff --check; all local quality checks pass.
- [x] Q.4 Live PostgreSQL/Liquibase proof: PASS (2026-08-07) — `PublishingHandlersTransactionPostgresIntegrationTest` (13 tests) green against real Testcontainers Postgres; migration 018 applied via the full Liquibase changelog.
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

- Incomplete tasks: Phase 4.5 (GitHub replies) is an external communication dependency, not an implementation failure.
- Docker: Live PostgreSQL proof completed 2026-08-07 via the targeted integration class; running the full default `:server:smp:test` (which includes all `@Tag("postgres")` classes) can stall on Docker daemon responses — use `-PexcludeTags=postgres` plus the targeted integration class for deterministic local verification.
- Evidence vs. replies: Items 4.1-4.4 represent evidence collected and decisions made; actual GitHub communication is item 4.5.
- Test failures: If focused tests fail, verify handler construction matches documented patterns in design.md.

## References

- Design: design.md
- Implementation progress: apply-progress.md
- Verification report: verify-report.md
- Specification delta: specs/publishing/spec.md
- Handlers: server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/
- Migration: server/smp/src/main/resources/db/changelog/publishing/017-social-content-workspace-fks.yaml
