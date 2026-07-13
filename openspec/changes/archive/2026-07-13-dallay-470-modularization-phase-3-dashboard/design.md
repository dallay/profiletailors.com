# Design: DALLAY-470 Modularization Phase 3 — Dashboard

## Technical Approach

Relocate dashboard-owned Vue SPA files into `apps/web/app/src/modules/dashboard/` using the Phase 1 module pattern already used by auth/settings/workspace. This is a physical move plus import rewrite only: route behavior, Pinia store IDs, component APIs, mock data values, and tests stay unchanged.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Module layout | `presentation/views`, `presentation/components`, `infrastructure`, `domain`, `infrastructure/mock-data` | Keep `views/components/stores/lib` at root; promote atoms globally | Matches existing modules while keeping dashboard domain, stores, mocks, and UI together. Atoms are dashboard-only today. |
| Store filenames | Normalize moved stores to `*.store.ts` | Keep `dashboard.ts`, `analytics.ts`, etc. | Existing modules use `auth.store.ts`, `settings.store.ts`, `workspace.store.ts`; normalizing during move is low-risk because all imports must change anyway. Keep Pinia IDs unchanged. |
| Mock data | Move dashboard mock data to `infrastructure/mock-data/*`, with local `index.ts` barrel only if useful | Leave under root `lib/mockData`; move to `domain` | Mocks feed stores/layout infrastructure and should travel with the dashboard module without becoming domain contracts. |
| Cross dependencies | Preserve explicit imports to auth, root UI, root formatters, and root `CreatePostModal` | Move auth/composer/shared UI too | Constraints forbid publishing/media/composer moves. Root UI/utilities remain shared infrastructure. |

## Data Flow

```text
router ──→ dashboard presentation views ──→ dashboard presentation components
              │                                  │
              ├──→ auth store                    ├──→ dashboard stores
              ├──→ root CreatePostModal          └──→ root UI/formatters
              └──→ root UI primitives                  │
                                                       └──→ dashboard mock-data/domain types
```

## File Changes

| File | Action | Description |
|---|---|---|
| `src/modules/dashboard/presentation/views/HomeView.vue` | Create/move | From `src/views/HomeView.vue`; update `DashboardLayout` import. |
| `src/modules/dashboard/presentation/views/AnalyticsView.vue` | Create/move | From `src/views/AnalyticsView.vue`. |
| `src/modules/dashboard/presentation/components/**` | Create/move | From `src/components/dashboard/**`, including colocated tests and `shared/` atoms. |
| `src/modules/dashboard/infrastructure/dashboard.store.ts` | Create/move | From `src/stores/dashboard.ts`; preserve `defineStore('dashboard')`. |
| `src/modules/dashboard/infrastructure/analytics.store.ts` | Create/move | From `src/stores/analytics.ts`; preserve `defineStore('analytics')`. |
| `src/modules/dashboard/infrastructure/insights.store.ts` | Create/move | From `src/stores/insights.ts`; preserve `defineStore('insights')`. |
| `src/modules/dashboard/infrastructure/content-pipeline.store.ts` | Create/move | From `src/stores/contentPipeline.ts`; preserve `defineStore('contentPipeline')`. |
| `src/modules/dashboard/infrastructure/*.store.test.ts` | Create/move | From dashboard-owned root store tests. |
| `src/modules/dashboard/domain/dashboard.types.ts` | Create/move | From `src/lib/types/dashboard.ts`. |
| `src/modules/dashboard/infrastructure/mock-data/{analytics,insights,content-pipeline,scheduling,engagement,growth-score}.ts` | Create/move | From dashboard-owned root mock data. |
| `src/lib/mockData/index.ts` | Modify/delete exports | Remove moved dashboard exports unless non-dashboard consumers still require them. |
| `src/router/index.ts` | Modify | Import `HomeView` and lazy `AnalyticsView` from `@modules/dashboard/...`. |
| `src/modules/module-relocation.spec.ts` | Modify | Add dashboard view/store/type resolution checks. |

## Interfaces / Contracts

Imports inside moved code should prefer module aliases for cross-directory module references:

```ts
import { useDashboardStore } from '@modules/dashboard/infrastructure/dashboard.store'
import type { KpiMetric } from '@modules/dashboard/domain/dashboard.types'
import { upcomingSchedule } from '@modules/dashboard/infrastructure/mock-data/scheduling'
```

Relative imports stay for colocated component-to-component/test-to-component references.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Moved component/store tests | Move tests with files; update relative and mocked import paths. |
| Integration | Module resolution and router imports | Extend `src/modules/module-relocation.spec.ts`. |
| E2E | Existing dashboard route behavior | No new E2E required; run only if unit/lint reveals route concerns. |

## Migration / Rollout

No migration required. Roll out as one relocation PR. Mitigate risk by using git moves, small mechanical import rewrites, and preserving public names/store IDs. Rollback is a git revert of moves/import rewrites.

## Verification Plan

Use Vue SPA-specific commands, not `just frontend-test`/`just frontend-lint` because those target marketing:

1. `pnpm --filter app test:run`
2. `pnpm --filter app lint`
3. `pnpm --filter app type-check`
4. Optional route smoke: `pnpm --filter app test:e2e:scheduler` only if dashboard-adjacent routing regresses.

## Open Questions

None.
