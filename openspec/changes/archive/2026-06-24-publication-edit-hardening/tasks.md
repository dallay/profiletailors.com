# Tasks: Publication Edit Hardening

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 250-450 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Backend refactor → unit tests → E2E → verification |
| Delivery strategy | single-pr |
| Chain strategy | not-needed |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: not-needed
400-line budget risk: Medium

## Phase 1: Backend quality gate hardening

- [x] 1.1 Refactor `R2dbcPublishingRepositories.kt` to remove the `insertOrUpdate` long-method Detekt violation without changing repository behavior.
- [x] 1.2 Reduce the responsibility surface in `R2dbcPublishingRepositories.kt` enough to clear the `LargeClass` Detekt violation.
- [x] 1.3 Run targeted backend publishing tests covering the refactored persistence behavior.
- [x] 1.4 Run `just backend-check` and confirm the previous Detekt blockers are resolved or identify any remaining unrelated failures.

## Phase 2: CreatePostModal edit-mode unit coverage

- [x] 2.1 Add dedicated unit tests for edit-mode prefill of content, scheduling, schedule mode, priority, and media.
- [x] 2.2 Add dedicated unit tests that verify the channel selector is locked in edit mode and create-only controls are hidden.
- [x] 2.3 Add dedicated unit tests that verify edit-mode submit calls `updatePost()` instead of `schedulePost()`.
- [x] 2.4 Add dedicated unit tests that verify `updated` is emitted and edit-mode errors are surfaced.

## Phase 3: Scheduler edit-flow E2E coverage

- [x] 3.1 Add a Playwright spec for opening an unpublished publication from the scheduler and entering edit mode.
- [x] 3.2 Verify content, scheduling, channel lock, and media prefill in the browser flow.
- [x] 3.3 Verify saving edits closes the composer and refreshes the scheduler with updated state.

## Phase 4: Verification

- [x] 4.1 Run `cd apps/web/app && pnpm test`.
- [x] 4.2 Run `cd apps/web/app && pnpm lint`.
- [x] 4.3 Run the targeted Playwright spec for publication editing.
- [x] 4.4 Record final results for verify.
