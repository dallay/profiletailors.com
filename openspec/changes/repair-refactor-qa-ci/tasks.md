# Tasks: Repair Refactor QA CI

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 450–650 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 frontend repair → PR 2 backend repair → PR 3 CI evidence |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Frontend contracts and regression tests | PR 1 | Base: main; build/typecheck/lint and focused Vitest. |
| 2 | Kotlin port migration and tests | PR 2 | Base: PR 1 branch; compile, unit, BDD/Postgres. |
| 3 | E2E and hosted CI evidence | PR 3 | Base: PR 2 branch; no product changes. |

## Phase 1: Frontend Build and Runtime Contracts

- [ ] 1.1 RED: add focused failing Vitest coverage in `apps/web/app/src/modules/**/` for `Ref`/computed consumption, localized unknown-action fallback, and scheduler filter/drag rollback; run `pnpm --filter app test:run -- <affected test files>`.
- [x] 1.2 In `apps/web/app/src/modules/publishing/views/SchedulerView.vue`, delete duplicate legacy grid/drag declarations and preserve `useCalendarGrid`/`useDragAndDrop` ownership; run `just app-build`.
- [x] 1.3 Normalize declared reactive inputs/outputs and explicit barrels in `apps/web/app/src/modules/{dashboard,governance,media,publishing,settings}/application/{*.ts,index.ts}` and `apps/web/app/src/shared/composables/{index.ts,useApiError.ts,useDeleteConfirmation.ts,useFormValidation.ts,useModalState.ts,usePagination.ts}`; run `pnpm --filter app type-check`.
- [x] 1.4 Apply only required a11y/Biome corrections in affected `apps/web/app/src/**/*.vue`; run `cd apps/web/app && pnpm lint`.

## Phase 2: Focused Frontend Regression Verification

- [x] 2.1 GREEN: align only affected mocks with the proven composable contract in `apps/web/app/src/**/*.test.ts`; retain assertions for navigation, account-filter clear, drag failure rollback, and localized fallback; run `pnpm --filter app test:run -- <affected test files>`.
- [ ] 2.2 REFACTOR: remove duplication without changing public contracts; rerun `just app-build && pnpm --filter app type-check && cd apps/web/app && pnpm lint && pnpm test:coverage`.

## Phase 3: Backend Kotlin Port Repair

- [ ] 3.1 RED: add failing regression tests for current media resolver references and string-only audit metadata in `server/smp/src/test/kotlin/com/profiletailors/smp/{media,governance,publishing}/`; run `just backend-test-fast`.
- [ ] 3.2 Replace stale references in `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`; make `PublishingMediaPorts.kt`'s sole functional method valid without semantic change; run `just backend-build`.
- [ ] 3.3 Adapt metadata at `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/AuditHookGovernanceMutationAuditPort.kt` to `Map<String, String>` without widening `MutationAuditFact`; run `just backend-test-fast`.

## Phase 4: Backend Lanes and CI Recheck

- [ ] 4.1 Run `just backend-check`, `just backend-bdd-fast`, `just infra-up && just backend-test-postgres && just backend-bdd-postgres`; stop on and report the first failing gate.
- [ ] 4.2 Run scheduler and media E2E: `pnpm --filter app test:e2e:scheduler`, `just app-test-e2e-media-mocked`, and `just app-test-e2e-media-real` when credentials/CAS are available.
- [ ] 4.3 Push the repair branch, then verify CI, Security PR, and external Pages checks with `gh pr checks 477 --watch`; report Pages as unavailable if no check is attached to PR #477.
