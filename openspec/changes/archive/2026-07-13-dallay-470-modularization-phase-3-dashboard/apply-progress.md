# Apply Progress: DALLAY-470 Modularization Phase 3 — Dashboard

## Status

Completed dashboard module relocation in strict TDD flow.

## TDD Evidence

- RED: Updated `apps/web/app/src/modules/module-relocation.spec.ts` to require dashboard module paths before moving production files.
  - Command: `pnpm --filter app test:run -- module-relocation`
  - Result: Failed as expected because `@modules/dashboard/presentation/views/HomeView.vue` did not exist yet.
- GREEN/REFACTOR: Moved dashboard-owned views, components, stores, domain types, mock data, and tests into `apps/web/app/src/modules/dashboard/`; rewrote imports and router entries to module paths.

## Completed Tasks

- [x] 1.1 Confirm branch and state artifact are ready for apply.
- [x] 1.2 Read proposal, spec, design, and inspect current dashboard layout.
- [x] 1.3 Add failing dashboard relocation guard before production moves.
- [x] 2.1 Move `HomeView.vue` and `AnalyticsView.vue` to dashboard presentation views.
- [x] 2.2 Move `components/dashboard/**`, including shared atoms and colocated tests, to dashboard presentation components.
- [x] 2.3 Preserve intentional imports to auth, root UI primitives, root `CreatePostModal`, root formatters, and `vue-sonner`.
- [x] 3.1 Move dashboard, analytics, and insights stores to dashboard infrastructure `*.store.ts` files while preserving Pinia IDs.
- [x] 3.2 Move content pipeline store to `content-pipeline.store.ts` and update tests/mocks.
- [x] 3.3 Move dashboard store tests to dashboard infrastructure `*.store.test.ts` files.
- [x] 3.4 Move dashboard domain types to `domain/dashboard.types.ts`.
- [x] 3.5 Move dashboard mock data to `infrastructure/mock-data/`; replace root mock data barrel with empty export.
- [x] 4.1 Rewrite imports and tests to `@modules/dashboard/...` or colocated relative imports; legacy dashboard paths removed.
- [x] 4.2 Update router dashboard view imports for `/` and `/analytics`.
- [x] 4.3 Run focused relocation checks and fix resolution failures.
- [x] 4.4 Run focused moved store/component tests after import rewrites.
- [x] 5.1 Run app unit tests.
- [x] 5.2 Run app lint.
- [x] 5.3 Run app type-check.
- [x] 5.4 Prepare handoff notes.

## Files Changed Categories

- Dashboard module created/populated under `apps/web/app/src/modules/dashboard/`:
  - `presentation/views/`
  - `presentation/components/`
  - `domain/dashboard.types.ts`
  - `infrastructure/*.store.ts`
  - `infrastructure/*.store.test.ts`
  - `infrastructure/mock-data/`
- Router/imports updated:
  - `apps/web/app/src/router/index.ts`
  - Dashboard component, store, mock-data, and test imports.
- Relocation guard updated:
  - `apps/web/app/src/modules/module-relocation.spec.ts`
- Legacy root dashboard locations removed:
  - `apps/web/app/src/views/HomeView.vue`
  - `apps/web/app/src/views/AnalyticsView.vue`
  - `apps/web/app/src/components/dashboard/**`
  - `apps/web/app/src/stores/{dashboard,analytics,insights,contentPipeline}.ts`
  - corresponding dashboard store tests
  - dashboard-owned `apps/web/app/src/lib/mockData/*`
  - `apps/web/app/src/lib/types/dashboard.ts`
- Root mock-data barrel retained as `export {}` to avoid an empty TypeScript module edge case.

## Verification Output

- `pnpm --filter app test:run -- module-relocation`
  - RED before moves: failed as expected on missing `@modules/dashboard/presentation/views/HomeView.vue`.
  - GREEN after moves: passed, 82 files / 845 tests due Vitest filter behavior collecting the app suite.
- `pnpm --filter app test:run -- modules/dashboard stores/dashboard stores/analytics stores/insights stores/contentPipeline components/dashboard`
  - Passed, 82 files / 845 tests due Vitest filter behavior collecting the app suite.
- `pnpm --filter app test:run`
  - Passed, 82 files / 845 tests.
- `pnpm --filter app lint`
  - First run failed on Biome formatting in `module-relocation.spec.ts`; fixed.
  - Second combined run failed on Biome formatting in `mock-data/engagement.ts`; fixed.
  - Final run passed: `Checked 631 files`.
- `pnpm --filter app type-check`
  - First run failed on moved mock-data imports still pointing to `../types/dashboard`; fixed to `@modules/dashboard/domain/dashboard.types`.
  - Final run passed.

## Warnings / Notes

- Vitest emitted existing console/CSS warnings during passing tests, including `Could not parse CSS stylesheet`, RouterView resolution warnings in `App.test.ts`, and expected error logs from negative-path tests. They did not fail the suite.
- No publishing/media/composer files were moved.
- `CreatePostModal` remains a root dependency by design.
- No behavior changes were introduced intentionally; this is a physical relocation plus import rewrite.
