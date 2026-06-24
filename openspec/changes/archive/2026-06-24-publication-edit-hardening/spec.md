# Spec: Publication Edit Hardening

## Scenario 1: Backend publishing quality gate passes after persistence refactor

**Given** the publishing persistence adapter currently has pre-existing Detekt violations
**When** the backend quality checks are run after the refactor
**Then** the refactored persistence code MUST preserve existing publishing behavior
**And** the previous `LargeClass` and `LongMethod` findings for `R2dbcPublishingRepositories.kt` MUST no longer fail the backend quality gate

## Scenario 2: Composer edit mode has focused unit coverage

**Given** `CreatePostModal` is opened in edit mode with an existing publication
**When** the unit test suite runs
**Then** dedicated tests MUST verify edit-mode prefill behavior for content, scheduling, priority, media, and locked channel state
**And** dedicated tests MUST verify edit-mode submission calls `updatePost()` and emits `updated`
**And** dedicated tests MUST verify create-only controls are hidden in edit mode

## Scenario 3: Scheduler publication edit flow is protected end-to-end

**Given** a user opens an editable unpublished publication from the scheduler
**When** the user edits it through the composer and saves
**Then** Playwright coverage MUST verify the detail modal closes, the composer opens in edit mode, fields are pre-filled, the update succeeds, and the scheduler refreshes to show saved state
