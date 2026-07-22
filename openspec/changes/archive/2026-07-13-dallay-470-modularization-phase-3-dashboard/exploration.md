## Exploration: DALLAY-470 Modularization Phase 3 — Dashboard module

### Current State

Dashboard functionality currently lives in root-level `views/`, `components/dashboard/`, `stores/`,
and `lib/` instead of `modules/dashboard/`. Phase 1 modules use a feature-module shape under
`apps/web/app/src/modules/{auth,workspace,settings}/` with `presentation/` for Vue views/components
and `infrastructure/` for stores/API adapters, referenced through `@modules/...` aliases.

The dashboard route (`/`) imports `HomeView` eagerly from `src/views/HomeView.vue`; the analytics
route (`/analytics`) lazy-loads `src/views/AnalyticsView.vue`. `HomeView` imports auth (
`@modules/auth/infrastructure/auth.store`), root UI button, root `CreatePostModal`, dashboard layout
from `@/components/dashboard/DashboardLayout.vue`, and `vue-sonner`.

### Affected Areas

- `apps/web/app/src/views/HomeView.vue` — Dashboard landing view; should move to
  `modules/dashboard/views/` or `modules/dashboard/presentation/views/` and router import must
  change.
- `apps/web/app/src/views/AnalyticsView.vue` — Analytics route view; in scope because it is
  dashboard/analytics-related and currently outside modules.
- `apps/web/app/src/components/dashboard/` — Main dashboard component tree with 11 dashboard
  sections plus shared atoms and component tests.
- `apps/web/app/src/components/dashboard/shared/` — Dashboard atoms (`KpiCard`, `SparklineChart`,
  `ScoreGauge`, `HeatmapGrid`, `PlatformBar`) used only by dashboard components today.
- `apps/web/app/src/stores/dashboard.ts` — Dashboard orchestrator store; depends on analytics,
  insights, and contentPipeline stores.
- `apps/web/app/src/stores/analytics.ts` — Analytics Pinia store; depends on dashboard types and
  analytics/growth/scheduling mock data.
- `apps/web/app/src/stores/insights.ts` — Insights Pinia store; depends on dashboard types and
  insights mock data.
- `apps/web/app/src/stores/contentPipeline.ts` — Content-pipeline store used directly by dashboard
  layout; not listed explicitly in issue title but functionally part of dashboard module.
- `apps/web/app/src/lib/types/dashboard.ts` — Dashboard domain types used by all dashboard
  components, stores, mock data, and tests.
- `apps/web/app/src/lib/mockData/analytics.ts` — Analytics mock data for `useAnalyticsStore`.
- `apps/web/app/src/lib/mockData/insights.ts` — Insights mock data for `useInsightsStore`.
- `apps/web/app/src/lib/mockData/contentPipeline.ts` — Pipeline mock data for
  `useContentPipelineStore`.
- `apps/web/app/src/lib/mockData/scheduling.ts` — Scheduling mock data/types used by dashboard
  layout and analytics store.
- `apps/web/app/src/lib/mockData/engagement.ts` — Inbox/team mock data used by dashboard layout.
- `apps/web/app/src/lib/mockData/growthScore.ts` — Growth score mock data used by analytics store.
- `apps/web/app/src/lib/mockData/index.ts` — Barrel exports dashboard mock data; must update or move
  with care.
- `apps/web/app/src/router/index.ts` — Route imports for `HomeView` and `AnalyticsView` need
  `@modules/dashboard/...` paths.
- `apps/web/app/src/modules/module-relocation.spec.ts` — Phase 1 relocation guard should be extended
  for dashboard module resolution.
- `apps/web/app/e2e/pages/dashboard-page.ts` — E2E page object documents HomeView and may need no
  import update, but is dashboard-related verification context.

### Approaches

1. **Move all dashboard-owned code into `modules/dashboard`** — Create
   `modules/dashboard/presentation/{views,components}`, `modules/dashboard/infrastructure`, and
   likely `modules/dashboard/domain` or `modules/dashboard/lib` for types/mock data.
    - Pros: Matches Phase 1 feature-module direction; keeps dashboard stores/types/mock
      data/components together; easiest to enforce with module relocation guard.
    - Cons: Requires many import updates across colocated tests and root router; `contentPipeline`
      must be included to avoid leaving a dashboard-only dependency in root stores.
    - Effort: Medium

2. **Move views/components/stores only, leave types/mock data in root `lib`** — Move visible
   dashboard files but keep `@/lib/types/dashboard` and `@/lib/mockData/*` stable.
    - Pros: Smaller diff; fewer import updates; less risk to unrelated root `lib` users.
    - Cons: Leaves dashboard domain data scattered; weak modularization; future modules still depend
      on dashboard-specific root `lib` paths.
    - Effort: Low

3. **Promote shared atoms to app-level shared components** — Move dashboard section components into
   dashboard module, but move generic atoms to `src/components/shared` or a future shared module.
    - Pros: Reuse-ready for future analytics/reporting surfaces; keeps generic chart/card primitives
      central.
    - Cons: Current atoms are all coupled to dashboard types/style/i18n or only used by dashboard;
      premature abstraction now.
    - Effort: Medium

### Recommendation

Use Approach 1, with the Phase 1 pattern:

- `@modules/dashboard/presentation/views/HomeView.vue`
- `@modules/dashboard/presentation/views/AnalyticsView.vue`
- `@modules/dashboard/presentation/components/**`
-

`@modules/dashboard/infrastructure/{dashboard.store,analytics.store,insights.store,content-pipeline.store}.ts`

- `@modules/dashboard/domain/dashboard.types.ts`
- `@modules/dashboard/infrastructure/mock-data/**` or `@modules/dashboard/domain/mock-data/**` for
  current mock data

Keep the current `components/dashboard/shared/*` atoms inside
`modules/dashboard/presentation/components/shared/` for now. They are dashboard-specific in practice
because their props use `dashboard` domain types and all current consumers are dashboard components.
Do not promote them to root shared until a second module uses them.

Dependency map:

- `HomeView.vue` imports: Vue, Vue I18n, `@modules/auth/infrastructure/auth.store`, root `Button`,
  root `CreatePostModal`, `DashboardLayout`, `vue-sonner`. Imported by `router/index.ts`.
- `AnalyticsView.vue` imports: root `Card` UI. Imported by `router/index.ts` lazy route.
- `DashboardLayout.vue` imports: Vue, Vue I18n, dashboard/analytics/insights/contentPipeline stores,
  scheduling/engagement mock data, all dashboard section components. Imported only by
  `HomeView.vue`.
- `dashboard.ts` imports analytics, insights, contentPipeline stores and orchestrates
  `refreshAll()`. No reverse imports found, so no circular store dependency.
- `analytics.ts` imports dashboard types and mock data (`analytics`, `growthScore`, `scheduling`).
  No store dependencies.
- `insights.ts` imports dashboard types and insights mock data. No store dependencies.
- `contentPipeline.ts` imports dashboard types and contentPipeline mock data. No store dependencies.
- Dashboard components import Vue/Vue I18n, root UI primitives (`Card`, `Badge`, `Button`, chart
  container), root `formatters`, dashboard types, and dashboard-local shared atoms.

Cross-module dependencies are acceptable and one-way: dashboard depends on auth for display/session
context through `HomeView`; it also depends on root composer (`CreatePostModal`) and root UI/lib
utilities. Dashboard does not currently depend on workspace store, publishing store, media store, or
scheduler views directly. The notable cross-cutting concern is `CreatePostModal`, which likely pulls
composer/publishing/media/workspace dependencies indirectly.

### Risks

- `contentPipeline.ts` is dashboard-owned but not called out in the issue headline; leaving it in
  root `stores/` would keep modularization incomplete because `DashboardLayout` imports it directly.
- `lib/mockData/index.ts` exports dashboard mock data; moving files may break barrel consumers if
  any external consumers are added later. Current grep found direct dashboard consumers only, but
  update or remove barrel exports deliberately.
- `PlatformBar.vue` appears unused by `CrossChannelAnalytics.vue`, which duplicates similar
  rendering inline. Moving it is safe, but this is a cleanup opportunity only if tests stay green.
- `HomeView` depends on root `CreatePostModal`; moving HomeView will preserve a cross-module/root
  dependency and may pull publishing/media concerns indirectly. Do not move composer files in this
  phase unless explicitly scoped.
- Tests are colocated with old paths and import via `./Component.vue` or `./store`; moving files
  requires updating relative imports and Vitest discovery should still pick them up.
- Router currently eagerly imports `HomeView`; after move, use
  `@modules/dashboard/presentation/views/HomeView.vue` consistently with Phase 1.

### Ready for Proposal

Yes — propose a full dashboard module relocation that includes dashboard components, HomeView,
AnalyticsView, dashboard/analytics/insights/contentPipeline stores, dashboard types, dashboard mock
data, tests, router imports, and the module relocation guard. The implementation should be mostly
file moves plus import rewrites, followed by `just frontend-test` and `just frontend-lint`.