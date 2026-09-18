# Tasks: R01 — Remove 3 UNUSED_PARAMETER Suppressions in PublishingProblemDetailsHandler

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 40–70 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Oracle + 3 rename-and-drops + test | PR 1 | Single PR; tests/docs included |

## Phase 1: Lint-Oracle Pre-Check

- [x] 1.1 Delete `@Suppress` at `PublishingProblemDetailsHandler.kt:39`, run `just backend-lint`; outcome: Detekt flags `UNUSED_PARAMETER` live. Verify: lint output cites `:40`.
- [x] 1.2 Repeat oracle for `:71` and `:158`; outcome: both flag live. Verify: lint output cites `:72`, `:159`.
- [x] 1.3 Dead-site fallback: if any site does NOT flag, convert it to deletion-only + cite oracle evidence; outcome: no rename on dead site. Verify: saved lint log quoted in PR.

## Phase 2: TDD Rename-and-Drop (per site)

- [x] 2.1 RED `PublishingProblemDetailsHandlerTest.kt`: add 3 tests calling `handleProviderNotConfigured()`, `handlePublicationNotFound()`, `handleRecurringScheduleNotFound()` asserting status/title/detail; outcome: compile-fail (unresolved). Verify: test run fails red.
- [x] 2.2 Site 1 `PublishingProblemDetailsHandler.kt:38-40`: `handle(exception: ProviderNotConfiguredException)` → `handleProviderNotConfigured()`, drop param + `:39` suppress; outcome: 503 "Provider not configured". Verify: site-1 test green.
- [x] 2.3 Site 2 `PublishingProblemDetailsHandler.kt:70-72`: → `handlePublicationNotFound()`, drop param + `:71` suppress; outcome: 404 "Publication not found". Verify: site-2 test green.
- [x] 2.4 Site 3 `PublishingProblemDetailsHandler.kt:157-159`: → `handleRecurringScheduleNotFound()`, drop param + `:158` suppress; outcome: 404 "Recurring schedule not found". Verify: site-3 test green.

## Phase 3: Gates and Guards

- [x] 3.1 Update existing call sites in `PublishingProblemDetailsHandlerTest.kt:32` (+ equivalents) to renamed methods; outcome: full test file green. Verify: run `infrastructure/http` tests.
- [x] 3.2 Run `just backend-lint`; outcome: PASS, zero new findings. Verify: exit 0, no added `@Suppress`.
- [x] 3.3 Run `just backend-check` + `publishing-publications.feature` lane; outcome: PASS, arch + BDD green. Verify: exit 0 both.
- [x] 3.4 Diff/status guard: `git status --porcelain` + `git diff --stat`; outcome: only handler + 1 test file modified, `:35`/`detekt.*`/`shared/` untouched. Verify: diff shows ≤4 hunks, zero new `@Suppress`.
