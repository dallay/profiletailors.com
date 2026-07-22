# Proposal: DALLAY-470 Modularization Phase 3 — Dashboard

## Intent

Move dashboard frontend code into `modules/dashboard` using the Phase 1 module shape, preserving
routes, UI, Pinia behavior, mock data behavior, and tests.

## Scope

### In Scope

- Move `HomeView.vue` and `AnalyticsView.vue` into dashboard presentation views.
- Move `components/dashboard/**`, including dashboard-local shared atoms and tests.
- Move dashboard-owned stores: dashboard, analytics, insights, and content pipeline.
- Move dashboard types and dashboard mock data with the module.
- Update router, source imports, tests, Vitest mocks, and relocation guard.

### Out of Scope

- New dashboard features or visual/behavior changes.
- Refactoring store logic, component internals, mock data shape, or route behavior.
- Moving root `CreatePostModal`, shadcn-vue UI primitives, root formatters, or auth module code.
- Promoting dashboard atoms to app-level shared components.

## Capabilities

### New Capabilities

None — this is behavior-preserving physical modularization only.

### Modified Capabilities

- `frontend-modularization`: extend modularization requirements to include dashboard-owned views,
  components, stores, domain types, mock data, tests, and import paths.

## Approach

Follow the DALLAY-468 Phase 1 pattern: move files directly, then rewrite imports to
`@modules/dashboard/...`. Use `presentation/views`, `presentation/components`, `infrastructure`, and
`domain`. Keep dashboard-local atoms under dashboard presentation because current consumers and
types are dashboard-specific. Preserve `HomeView` → auth store and root `CreatePostModal`
dependencies as explicit cross-module/root imports.

## Affected Areas

| Area                                                 | Impact       | Description                                                  |
|------------------------------------------------------|--------------|--------------------------------------------------------------|
| `apps/web/app/src/modules/dashboard`                 | New/Modified | Dashboard views, components, stores, types, mock data, tests |
| `apps/web/app/src/views`                             | Modified     | Remove moved dashboard views                                 |
| `apps/web/app/src/components/dashboard`              | Modified     | Remove moved dashboard component tree                        |
| `apps/web/app/src/stores`                            | Modified     | Remove moved dashboard stores                                |
| `apps/web/app/src/lib/{types,mockData}`              | Modified     | Remove or update dashboard-owned exports                     |
| `apps/web/app/src/router/index.ts`                   | Modified     | Route imports point to dashboard module                      |
| `apps/web/app/src/modules/module-relocation.spec.ts` | Modified     | Guard dashboard module resolution                            |

## Risks

| Risk                                        | Likelihood | Mitigation                                                             |
|---------------------------------------------|------------|------------------------------------------------------------------------|
| Missed imports or mocks                     | Med        | Run focused app Vitest/Biome checks and fix module-resolution failures |
| Leaving `contentPipeline.ts` in root stores | Med        | Treat it as dashboard-owned and move with stores                       |
| `CreatePostModal` remains cross-cutting     | Med        | Preserve dependency; defer composer modularization                     |
| Mock data barrel breakage                   | Low        | Update/remove exports deliberately after move                          |

## Rollback Plan

Revert the DALLAY-470 file moves and import rewrites. No schema, API, or data migrations are
involved; rollback is a git revert plus rerunning frontend app tests.

## Dependencies

- Phase 0/1 `@modules/*` alias and module structure.
- Existing dashboard behavior specs remain authoritative.

## Success Criteria

- [ ] Dashboard-owned code lives under `apps/web/app/src/modules/dashboard/`.
- [ ] No source/tests import moved dashboard files through legacy root paths.
- [ ] Routes still render `/` and `/analytics` unchanged.
- [ ] `contentPipeline` store moves with dashboard stores.
- [ ] Frontend app unit tests and lint/type checks pass.
