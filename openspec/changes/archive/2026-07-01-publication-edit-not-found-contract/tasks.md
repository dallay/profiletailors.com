# Tasks: Publication Edit Not-Found Contract

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 120-220 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Add narrow 404 mapping plus focused regressions | PR 1 | Single backend slice with tests and no create-flow changes |

## Phase 1: Foundation

- [x] 1.1 In `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandlerTest.kt`, add a RED test for `PublicationNotFoundException` → `404 ProblemDetail` title/detail.
- [x] 1.2 In `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllersTest.kt`, add RED coverage for update-only endpoint misses on `PATCH /api/publishing/publications/{id}` using mediator-thrown `PublicationNotFoundException`.
- [x] 1.3 In `PublishingControllersTest.kt`, add focused shared-endpoint coverage for one sibling update-only operation (`DELETE`, `cancel`, `retry`, or `reschedule`) proving the same exception path returns 404.

## Phase 2: HTTP Contract Implementation

- [x] 2.1 Update `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` to map only `PublicationNotFoundException` to HTTP 404 with publishing-specific `ProblemDetail` title/detail.
- [x] 2.2 Confirm `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` keeps update-only handlers (`edit`, `delete`, `cancel`, `retry`, `reschedule`) throwing `PublicationNotFoundException` for active-workspace misses without broadening create/save behavior.
- [x] 2.3 Verify `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingPublicationController.kt` needs no create-flow contract change beyond inheriting advice behavior.

## Phase 3: Focused Verification

- [x] 3.1 Make handler/advice regression tests GREEN for the spec scenario: edit miss returns 404, not 500.
- [x] 3.2 Make shared endpoint regression tests GREEN for the spec scenario: sibling update-only operations using `PublicationNotFoundException` also return 404.
- [x] 3.3 Add/keep a guard assertion that create-capable publication flows do not start returning 404 for missing rows outside update-only paths.

## Phase 4: Cleanup

- [x] 4.1 Run focused backend tests for `PublishingProblemDetailsHandlerTest` and `PublishingControllersTest`; record failures fixed and final pass state.
- [x] 4.2 If test scaffolding added mediator stubs/helpers, trim them to the minimum needed for this 404 contract regression.
