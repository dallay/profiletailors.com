# Tasks: PR #624 Social Content Foundation Remediation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 500–700 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 contracts; PR 2 handlers; PR 3 migration |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: No — user approved single-pr with size exception
Chained PRs recommended: No — approved unified PR
Chain strategy: size-exception
400-line budget risk: Accepted by user for this remediation

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Contracts, invariants, limits, month validation | PR 1 | Tests and focused verification. |
| 2 | Handlers, bounded sync, replies, fakes | PR 2 | Depends on Unit 1. |
| 3 | Workspace-safe migration | PR 3 | Depends on migration proof. |

**No-touch guard:** Never modify `shared/shield/ratelimit/**`, PR #625, or unrelated rate-limiting behavior. Do not add Spring/mediator wiring or HTTP surface.

## Phase 1: Domain and Configuration

- [x] 1.1 RED: Add failing cases in `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModelsTest.kt` and `SocialContentPortsTest.kt` for typed failures, limits, invariants, expiry, byte equality, reply transitions, and ports.
- [x] 1.2 GREEN: Modify `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModels.kt` and `SocialContentPorts.kt` with minimum contracts/invariants.
- [x] 1.3 VERIFY: Run focused domain tests; confirm no provider calls or writes.
- [x] 1.4 RED: Add `202600`/`202613` failures in `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/socialcontent/SocialContentPropertiesTest.kt`.
- [x] 1.5 GREEN: Update `SocialContentProperties.kt` for calendar-month validation and sync limits.
- [x] 1.6 VERIFY: Run focused properties tests and publishing test compilation.

## Phase 2: CQRS and State

- [x] 2.1 RED: Extend `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/SocialContentFoundationHandlersTest.kt` for boundaries, denial/no-call, pagination, repeated cursor, max pages, resume/HWM, checkpoints, and no-write behavior.
- [x] 2.2 GREEN: Create `DiscoverSocialActorsQueryHandler.kt`, `SyncSocialPostsCommandHandler.kt`, and `SyncSocialCommentsCommandHandler.kt`; replace `SocialContentFoundationHandlers.kt` without Spring wiring.
- [x] 2.3 RED: Add reply-state/conflict tests in `SocialContentFoundationHandlersTest.kt` and `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/fake/FakeReplyCommandRepositoryTest.kt`.
- [x] 2.4 GREEN: Create `ReplyToSocialCommentCommandHandler.kt`; update `FakeReplyCommandRepository.kt`, `FakeSocialContentProvider.kt`, and ports for atomic claim/one terminal save.
- [x] 2.5 VERIFY: Run focused application/fake tests, workspace isolation, and page-size/cursor assertions. High-water marks now incorporate both provider metadata and received item timestamps.

## Phase 3: Liquibase

- [x] 3.1 RED: Extend `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/SocialContentLiquibaseChangelogTest.kt` for `017`, inclusion, composite constraints, indexes, and rollback.
- [x] 3.2 GREEN: Create `server/smp/src/main/resources/db/changelog/publishing/017-social-content-workspace-fks.yaml`; include after `016` in `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`, preserving `016` history.
- [x] 3.3 VERIFY: Run static Liquibase tests; live Postgres proof remains a verify-phase concern because the available harness requires infrastructure startup.

## Phase 4: Review Closure

- [ ] 4.1 Reply to missing `architecture-docs-sync.md`: absent here, no command invented, status bounded to PR #624.
- [ ] 4.2 Reply to ratelimit/PR #625: out of scope; confirm both guarded areas remain unchanged.
- [ ] 4.3 Reply to `mutationAllowed`: cite reconciliation behavior and `SocialContentModelsTest.kt`; do not duplicate code.
- [ ] 4.4 Reply to Cucumber/Liquibase applicability: no Cucumber without HTTP surface; static migration tests plus targeted Postgres proof when needed.
- [ ] 4.5 Verify final diff has no guarded changes and all four replies contain evidence and a decision.

## Phase 5: Final Verification

- [ ] 5.1 Run `just backend-test-fast` and selected Liquibase/Postgres checks without touching guarded areas. Apply ran the full fast backend suite successfully; verify still owns final gate and live Postgres proof.

## Apply Quality Continuation

- [x] Q.1 Resolve all 14 local Detekt findings from `server/smp/build/reports/detekt/detekt.md` without suppressions, including braces, named constants, and max-line formatting in changed files.
- [x] Q.2 Preserve and verify deterministic reply idempotency for persisted `PROCESSING`, `SUCCEEDED`, and `FAILED` results; focused publishing tests pass without a second provider call.
- [x] Q.3 Run Spotless formatting/checks, focused publishing tests, `just backend-check`, and `git diff --check`; all local quality checks pass.
- [ ] Q.4 Run live PostgreSQL/Liquibase proof when Docker/infrastructure is available; do not mark complete from this continuation.
- [ ] Q.5 Post external GitHub review replies with evidence; do not mark complete from this continuation.
