# Tasks: DALLAY-470 Modularization Phase 3 — Dashboard

## Review Workload Forecast

| Field                   | Value                  |
|-------------------------|------------------------|
| Estimated changed lines | 250-380 plus git moves |
| 400-line budget risk    | Medium                 |
| Chained PRs recommended | No                     |
| Suggested split         | Single relocation PR   |
| Delivery strategy       | ask-on-risk            |
| Chain strategy          | pending                |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal                                                       | Likely PR | Notes                                       |
|------|------------------------------------------------------------|-----------|---------------------------------------------|
| 1    | Dashboard relocation guard, moves, imports, focused checks | PR 1      | Base main; behavior-preserving, no features |

## Phase 1: Preflight / Guard

- [x] 1.1 Confirm branch and
  `openspec/changes/dallay-470-modularization-phase-3-dashboard/state.yaml` are ready for apply.
- [x] 1.2 Read proposal, spec, design, and inspect current
  `apps/web/app/src/{views,components/dashboard,stores,lib}` layout.
- [x] 1.3 RED: update `apps/web/app/src/modules/module-relocation.spec.ts` with dashboard
  view/store/type/module-path checks that fail before moves.

## Phase 2: Presentation Relocation

- [x] 2.1 Move `src/views/{HomeView,AnalyticsView}.vue` to
  `src/modules/dashboard/presentation/views/`.
- [x] 2.2 Move `src/components/dashboard/**`, including `shared/` atoms and colocated tests, to
  `src/modules/dashboard/presentation/components/`.
- [x] 2.3 Preserve intentional imports to auth, root UI primitives, root `CreatePostModal`, root
  formatters, and `vue-sonner`.

## Phase 3: State / Domain / Mock Data

- [x] 3.1 Move `src/stores/{dashboard,analytics,insights}.ts` to
  `src/modules/dashboard/infrastructure/*.store.ts`; keep Pinia IDs unchanged.
- [x] 3.2 Move `src/stores/contentPipeline.ts` to
  `src/modules/dashboard/infrastructure/content-pipeline.store.ts`; update its tests/mocks.
- [x] 3.3 Move dashboard store tests to `src/modules/dashboard/infrastructure/*.store.test.ts` with
  design-approved filenames.
- [x] 3.4 Move `src/lib/types/dashboard.ts` to `src/modules/dashboard/domain/dashboard.types.ts`.
- [x] 3.5 Move dashboard mock data to `src/modules/dashboard/infrastructure/mock-data/`; update or
  remove `src/lib/mockData/index.ts` exports deliberately.

## Phase 4: Imports / Router / Focused Tests

- [x] 4.1 Rewrite source, tests, and Vitest mocks to `@modules/dashboard/...` or valid colocated
  relatives; remove legacy dashboard paths.
- [x] 4.2 Update `apps/web/app/src/router/index.ts` for `/` and `/analytics` dashboard view imports.
- [x] 4.3 Run focused relocation checks with `pnpm --filter app test:run -- module-relocation` and
  fix resolution failures.
- [x] 4.4 Run focused moved store/component tests after import rewrites; report unrelated failures
  separately.

## Phase 5: Verification / Handoff

- [x] 5.1 Run `pnpm --filter app test:run`.
- [x] 5.2 Run `pnpm --filter app lint`.
- [x] 5.3 Run `pnpm --filter app type-check`.
- [x] 5.4 Prepare Linear/PR notes: behavior-preserving relocation, intentional cross-module/root
  dependencies, checks run, no new features.
