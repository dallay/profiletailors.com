# Tasks: Batch 2 — Remove Dead ThrowsCount / TooGenericExceptionCaught Suppressions in Tenancy Handlers

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 5 deletions + 1 narrowing (≤6 lines) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

TDD does not apply: annotation-only diff, no behavior change. Existing `TenancyOwnershipHandlersInternalTest` + `UpdateWorkspaceMembershipStatusHandlerTest` cover handlers. Evidence is `just backend-lint` + `just backend-check` PASS.

## Phase 1: Deletion + Narrowing

- [x] 1.1 Delete `@Suppress("TooGenericExceptionCaught")` at `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/application/TenancyOwnershipHandlersInternal.kt:35`. Outcome: line gone, surroundings byte-identical. Verify: `grep TooGenericExceptionCaught` on file returns only `:62`-equivalent remaining pre-narrow state.
- [x] 1.2 Delete `@Suppress("ThrowsCount")` at `TenancyOwnershipHandlersInternal.kt:66` (`AddWorkspaceOwnerHandler.handle`, 1 throw). Outcome: 1 line gone. Verify: `git diff` shows deletion-only, no signature change.
- [x] 1.3 Narrow `@Suppress("ThrowsCount", "LongMethod")` → `@Suppress("LongMethod")` at `TenancyOwnershipHandlersInternal.kt:140` (`TransferWorkspaceOwnershipHandler.handle`, 3 throws ≤ max 3). Outcome: line reads `@Suppress("LongMethod")`. Verify: no `ThrowsCount` token on that line.
- [x] 1.4 Delete `@Suppress("ThrowsCount")` at `TenancyOwnershipHandlersInternal.kt:223` (`RemoveWorkspaceOwnerHandler.handle`, 2 throws). Outcome: 1 line gone. Verify: `git diff` deletion-only.
- [ ] 1.5 Delete `@Suppress("ThrowsCount")` at `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/application/UpdateWorkspaceMembershipStatusHandler.kt:25` (2 throws at `:34,:37`). REVERTED — suppression is LIVE: Detekt counts 4 throw statements (2 `?: throw` + 2 `throw exception` rethrows in catch) > max 3. Line restored byte-identical, recorded as deferred debt. No refactor.
- [x] 1.6 Delete `@Suppress("TooGenericExceptionCaught")` at `UpdateWorkspaceMembershipStatusHandler.kt:62` (dead via `detekt.yml:214` `**/application/**` exclusion). Outcome: 1 line gone. Verify: `grep TooGenericExceptionCaught` over `tenancy/application` returns zero matches.
- [x] 1.7 Verify deletion-only diff: actual outcome 4 deleted lines + 1 narrowed line + 1 retained (`Update...:25`, proven live); zero logic/signature/test changes; `shared/`, `detekt.yml`, `detekt-baseline.xml` untouched.

## Phase 2: Verification

- [x] 2.1 Run `just backend-lint` — must PASS with zero new findings; baseline only shrinks or stays equal. PASS after restoring live `Update...:25` suppression (first run tripped `ThrowsCount` there, proving it live).
- [x] 2.2 Run `just backend-check` — must PASS with `HexagonalArchTest` + `ComponentScanArchTest` green. PARTIAL: 2591 tests, 1 unrelated failure (`ComplianceControllerWebTest` release-gate, blocking-read timeout); arch tests 13/13 + 5/5 green, tenancy handler tests 12/12 + 3/3 green.
- [x] 2.3 Verify guards: diff adds zero `@Suppress` lines; `git status --porcelain` shows only the 2 `.kt` files plus OpenSpec artifacts.

## Phase 3: LongMethod Fallback (conditional)

- [x] 3.1 If 2.1 trips on `LongMethod` at `TransferWorkspaceOwnershipHandler.handle` (~66 vs 60): restore minimal `@Suppress("LongMethod")` at `:140` byte-identical, re-run `just backend-lint`, record as deferred debt. No refactor in this batch. NOT TRIGGERED — Transfer narrowing passed lint. Analogous fallback WAS applied at `UpdateWorkspaceMembershipStatusHandler.kt:25` (`ThrowsCount`, proven live at 4 throws > max 3): restored byte-identical, lint re-run PASS, recorded as deferred debt. No refactor.
