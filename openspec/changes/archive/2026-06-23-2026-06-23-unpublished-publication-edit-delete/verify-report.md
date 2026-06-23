# Verification Report: Unpublished Publication Edit/Delete

## Change
- **Change**: `2026-06-23-unpublished-publication-edit-delete`
- **Mode**: openspec
- **Strict TDD**: inactive (`openspec/config.yaml` sets `apply.tdd: false`; no strict TDD signal found)
- **Final Verdict**: **PASS** (delta-scope verification only; full backend and frontend suites were intentionally not re-executed)

## Completeness

| Artifact | Status | Notes |
|---|---|---|
| Proposal | ✅ | Reviewed |
| Spec | ✅ | Reviewed |
| Design | ✅ | Reviewed |
| Tasks | ✅ | All tasks 1.1–4.4 are complete |
| Verify report | ✅ | This artifact |

| Task Area | Complete | Incomplete | Notes |
|---|---:|---:|---|
| Backend foundation | 3 | 0 | All complete |
| Backend implementation | 3 | 0 | All complete |
| Frontend integration | 3 | 0 | All complete |
| Testing and verification | 4 | 0 | Focused unit/integration and scheduler E2E all executed |

## Build / Tests / Coverage Evidence

| Command | Result | Evidence |
|---|---|---|
| `pnpm --dir "apps/web/app" type-check` | ✅ PASS | `vue-tsc --build` completed successfully after tightening touched tests and focus-trap typing |
| `pnpm --dir "apps/web/app" test:run src/components/PostDetailModal.test.ts src/stores/publishing.test.ts` | ✅ PASS | 2 files passed, 73 tests passed |
| `./gradlew :server:smp:test --tests "com.profiletailors.smp.publishing.infrastructure.http.PublishingControllersTest" --tests "com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublishingRepositoriesUnitTest" --tests "com.profiletailors.smp.publishing.application.PublishingHandlersTest"` | ✅ PASS | Build successful; targeted backend verification suite passed |
| `pnpm playwright test -c e2e/playwright.scheduler.config.ts e2e/specs/scheduler-post-interaction.spec.ts` | ✅ PASS | 6 scheduler interaction tests passed, including TC-16 past-slot accessibility gating |
| Coverage | ℹ️ Observed during Playwright run | Scheduler suite reported statements 73.07%, branches 65.33%, functions 34.75%, lines 73.98% |

## Spec Compliance Matrix

| Requirement / Scenario | Implementation Evidence | Runtime Test Evidence | Judge |
|---|---|---|---|
| Requirement: Unpublished Publication Deletion API | `DeletePublicationCommand`, `DeletePublicationHandler`, `DELETE /api/publishing/publications/{publicationId}`, `PublicationRepository.deleteUnpublished(...)` added | Backend focused tests passed: controller dispatch, handler allow/reject/auth, repository cleanup | ✅ Compliant |
| Scenario: Delete scheduled publication succeeds | Handler enforces deletable states; repository removes publication, asset links, and unclaimed jobs | `PublishingHandlersTest` delete success test passed; `R2dbcPublishingRepositoriesUnitTest` cleanup test passed | ✅ Compliant |
| Scenario: Delete published publication is rejected | `PublicationLifecyclePolicy.requireDeletable(...)` rejects non pre-delivery states | `PublishingHandlersTest` delete reject test passed | ✅ Compliant |
| Requirement: Publication Edit/Delete Status Matrix | Shared mutable status set in frontend store (`DRAFT`,`QUEUED`,`SCHEDULED`); backend policy uses same matrix | Frontend modal/store tests passed for gating and mutations; backend tests passed for delete rejection and edit flow protection | ✅ Compliant |
| Scenario: Allowed status exposes action | `PostDetailModal.vue` shows save/delete only when editable/deletable; `SchedulerView.vue` card delete buttons gated by store helpers | `PostDetailModal.test.ts` editable rendering and delete success tests passed | ✅ Compliant |
| Scenario: Disallowed status stays server-enforced | Frontend hides destructive actions for protected states while backend still rejects forbidden delete/edit statuses | Backend delete rejection test passed; frontend modal gating tests passed | ✅ Compliant |
| Modified requirement: queued publication is edited before claim | `updatePost()` PATCHes backend and updates local state from server response; backend edit handler replaces unclaimed job | Frontend store tests passed for PATCH success/failure; backend focused suite passed | ✅ Compliant |
| Scenario: Scheduled publication edit uses backend response | Store maps PATCH result with `publicationMutationResultToPublication(...)`; modal save closes only on success and shows errors on failure | `publishing.test.ts` passed update success/rollback tests; `PostDetailModal.test.ts` passed save success/error tests | ✅ Compliant |
| Scenario: Past slots remain read-only/disabled in scheduler UX | Month/day/week slot helpers keep past dates read-only and now expose `aria-disabled` in month cells | Scheduler Playwright TC-15 and TC-16 passed | ✅ Compliant |

## Correctness Table

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| Backend delete flow exists and is wired end-to-end through controller → handler → repository | ✅ | ✅ | INFO | Confirmed |
| Frontend edit/delete now use backend APIs instead of local-only success | ✅ | ✅ | INFO | Confirmed |
| Shared status gating for edit/delete is implemented on both frontend and backend | ✅ | ✅ | INFO | Confirmed |
| Frontend type-check now passes after aligning touched tests and focus trap typing | ✅ | ✅ | INFO | Confirmed |
| Scheduler E2E now proves refresh-safe interaction coverage for the targeted file | ✅ | ✅ | INFO | Confirmed |
| Month calendar past cells now expose `aria-disabled`, matching accessibility expectations from TC-16 | ✅ | ✅ | INFO | Confirmed |

## Design Coherence Table

| Design Decision | Evidence | Judge |
|---|---|---|
| Dedicated delete CQRS path | `DeletePublicationCommand` + `DeletePublicationHandler` + DELETE controller endpoint present | ✅ Matches design |
| Reuse shared lifecycle policy for status gating | `PublicationLifecyclePolicy.requireEditable/requireDeletable` used | ✅ Matches design |
| Frontend edit UX stays inside `PostDetailModal.vue` | Modal owns editable title/body/schedule controls and closes on save success | ✅ Matches design |
| Store names remain `deletePost()` and `updatePost()` but become API-backed | `publishing.ts` implements async backend-backed actions | ✅ Matches design |
| E2E spec updated for scheduler flows | `scheduler-post-interaction.spec.ts` updated and executed successfully | ✅ Matches design |
| SQL hardening left out of scope | No SQL-level state guard added in repository path | ✅ Matches documented out-of-scope decision |

## Issues

### WARNING
- Focused verification is strong, but broader repo-wide frontend and backend suites were intentionally not run because this change was validated with targeted commands per repo guidance.

### SUGGESTION
- If this change is about to ship with other scheduler work, running the broader scheduler E2E pack or `just frontend-test-e2e` would provide extra confidence. This is optional but recommended per the delta scope — team policy does not require it for scoped changes.

## Summary
The implementation now fully matches the proposal, spec, design, and task list. Backend delete support exists, frontend edit/delete flows are backend-backed, focused unit/integration/type-check verification passed, and the scheduler Playwright interaction suite passed end-to-end after restoring `aria-disabled` semantics for past month cells. The change satisfies the OpenSpec verification gate.
