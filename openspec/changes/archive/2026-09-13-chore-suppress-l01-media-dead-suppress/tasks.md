# Tasks: Batch 1 — Remove Dead TooGenericExceptionCaught Suppression

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 1 deletion + 1 deferred (see 1.2) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

TDD does not apply: chore delete-only, no behavior change. Evidence is `just backend-lint` + `just backend-check` PASS.

## Phase 1: Deletion

- [x] 1.1 Delete `@Suppress("TooGenericExceptionCaught")` at `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt:243`, keep surrounding code byte-identical — DONE, 1 line deleted
- [ ] 1.2 ~~Delete `@Suppress("TooGenericExceptionCaught")` + trailing comment at `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/StaleAssetReconciler.kt:96`~~ — DEFERRED to a future epic #1019 batch with tool-run baseline regeneration. Cause: `detekt-baseline.xml:88` embeds the annotation text in the `LongMethod:processBlob` ID; deleting the line resurfaced LongMethod as a new finding (`backend-lint` FAILED). Line restored byte-identical per proposal mitigation; baseline never hand-edited (hand-edit prohibited).
- [x] 1.3 Verify deletion-only diff: `git diff` shows exactly 1 deleted line (MediaHandlers.kt only), zero added logic lines; `grep TooGenericExceptionCaught` over `media/application` returns 1 match (deferred `StaleAssetReconciler.kt:96`, see 1.2) — DONE per amended 1-deletion contract.

## Phase 2: Verification

- [x] 2.1 Run `just backend-lint` — must PASS with zero new findings; baseline only shrinks or stays equal — PASS (BUILD SUCCESSFUL, 40s, detekt task green; baseline untouched)
- [x] 2.2 Run `just backend-check` — must PASS with `HexagonalArchTest` + `ComponentScanArchTest` green — PASS (BUILD SUCCESSFUL in 44m15s, EXIT_CODE=0; HexagonalArchTest 13/13, ComponentScanArchTest 5/5, 0 failures/errors)
- [x] 2.3 Verify guards: diff adds zero `@Suppress` lines; `git status --porcelain` shows only the two `.kt` files plus OpenSpec artifacts; `shared/`, `config/detekt/detekt.yml`, `detekt-baseline.xml`, `package.json`, `pnpm-lock.yaml` untouched — PASS with note: `package.json` + `pnpm-lock.yaml` show as modified but are pre-existing worktree dirt (untouched by this change); only `MediaHandlers.kt` carries the 1-line deletion. `shared/`, `detekt.yml`, `detekt-baseline.xml` untouched.
