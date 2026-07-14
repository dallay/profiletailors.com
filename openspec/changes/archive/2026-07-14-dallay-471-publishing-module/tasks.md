# Tasks: DALLAY-471 Publishing Module

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 300-450 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single relocation PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Guard publishing/media ownership, relocate files, rewrite imports/mocks, run focused checks | PR 1 | Single behavior-preserving relocation; no broad CI |

## Phase 1: TDD Relocation Guards

- [x] 1.1 Update `apps/web/app/src/modules/module-relocation.spec.ts` with failing checks for `@modules/publishing/views/SchedulerView.vue`, publishing component/application/store paths, and `@modules/media/infrastructure/media.store`.
- [x] 1.2 Add/adjust legacy-root guard assertions for removed publishing paths and media store ownership; verify failure with `pnpm --filter app exec vitest run src/modules/module-relocation.spec.ts`.

## Phase 2: File Relocation

- [x] 2.1 Move `src/views/SchedulerView.vue` and `.test.ts` to `src/modules/publishing/views/`.
- [x] 2.2 Move publishing components/tests from `src/components/{CalendarCell,CalendarHeader,ConflictBadge,CreatePostModal,PostDetailModal}*` to `src/modules/publishing/presentation/components/`; do not move `src/components/ui/calendar/*`.
- [x] 2.3 Move `src/components/composer/**` to `src/modules/publishing/presentation/components/composer/` preserving internal relative imports where valid.
- [x] 2.4 Move `src/composables/useComposerMediaPicker.ts` and test to `src/modules/publishing/application/`.
- [x] 2.5 Move `src/stores/publishing.ts` and test to `src/modules/publishing/infrastructure/publishing.store.ts`; preserve store ID and exports.
- [x] 2.6 Move `src/stores/media.ts` and test to `src/modules/media/infrastructure/media.store.ts`; preserve DALLAY-469 media ownership.

## Phase 3: Imports, Routes, and Mocks

- [x] 3.1 Update `src/router/index.ts` scheduler lazy imports to `@modules/publishing/views/SchedulerView.vue`.
- [x] 3.2 Rewrite app, layout, sidebar, dashboard, settings, auth, media, toast, queued-counts, and tests to new `@modules/publishing/...` and `@modules/media/...` paths.
- [x] 3.3 Update all `vi.mock()` specifiers for publishing store/view/components/composable and media store to match relocated import strings.

## Phase 4: Focused Verification

- [x] 4.1 Run `pnpm --filter app exec vitest run src/modules/module-relocation.spec.ts` and make guards pass.
- [x] 4.2 Run `pnpm --filter app exec vitest run src/modules/publishing src/modules/media src/router/index.ts src/App.test.ts`.
- [x] 4.3 Run `pnpm --filter app exec biome check src/modules src/router/index.ts src/components src/composables src/stores`.
- [x] 4.4 Run `pnpm --filter app type-check`; document unrelated failures only. Do not run broad `just ci` in this phase.
