# Tasks: Social Media Operating System Dashboard

## Review Workload Forecast

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

| Unit | Goal | PR | Notes |
|------|------|----|-------|
| 1 | Foundation: types, stores, mock data, formatters, i18n, layout, shared primitives | PR 1 → main | No sections yet |
| 2 | MVP: 5 dashboard sections + component tests | PR 2 → PR 1 | Depends on Unit 1 |
| 3 | Schedule & Pipeline: 4 sections + uPlot | PR 3 → PR 2 | Depends on Unit 2 |
| 4 | Engagement + HomeView wiring + verification | PR 4 → PR 3 | Final integration |

## Phase 1: Foundation

- [x] 1.1 `src/lib/types/dashboard.ts` — all data model interfaces
- [x] 1.2 `src/lib/formatters.ts` — formatNumber, formatPercent, formatDelta, formatRelativeTime
- [x] 1.3–1.8 `src/lib/mockData/*.ts` — typed fixtures (analytics, insights, contentPipeline, scheduling, engagement, growthScore)
- [x] 1.9 `src/lib/mockData/index.ts` — barrel export
- [x] 1.10–1.13 `src/stores/{analytics,insights,contentPipeline,dashboard}.ts` — stores per domain + orchestrator
- [x] 1.14 `src/assets/main.css` — chart tokens, sparkline/heatmap utilities
- [x] 1.15 `src/i18n/index.ts` — ~120 EN/ES keys under dashboard.*
- [x] 1.16 `src/components/dashboard/DashboardLayout.vue` — responsive grid, welcome, skeleton gate
- [x] 1.17–1.21 `src/components/dashboard/shared/{KpiCard,SparklineChart,PlatformBar,ScoreGauge,HeatmapGrid}.vue`
- [x] 1.22 `src/lib/formatters.test.ts`
- [x] 1.23 Store unit tests (analytics, insights, contentPipeline)

## Phase 2: MVP Sections

- [x] 2.1 `ExecutiveOverview.vue` — 4 KPI cards, period selector, last updated
- [x] 2.2 `AiInsightsHero.vue` — hero + grid, confidence badges, type styling
- [x] 2.3 `GrowthScore.vue` — gauge, factor bars, top opportunity
- [x] 2.4 `TopPerformingPosts.vue` — ranked list, platform filter, empty state
- [x] 2.5 `CrossChannelAnalytics.vue` — platform bars, follower context
- [x] 2.6 Wire Phase 1 into DashboardLayout grid
- [x] 2.7 Component tests (mount each with fake props)

## Phase 3: Schedule & Pipeline

- [x] 3.1 ~~Install uPlot~~ → Custom SVG (design revision: lighter bundle)
- [x] 3.2 `AudienceGrowthChart.vue` — 30-day line chart
- [x] 3.3 `UpcomingSchedule.vue` — next 5 posts, calendar CTA
- [x] 3.4 `BestPostingTimes.vue` — 7×24 heatmap, best-time badge
- [x] 3.5 `ContentPipeline.vue` — 4-column kanban, priority indicators
- [x] 3.6 Add Phase 2 sections to DashboardLayout
- [x] 3.7 Component tests for Phase 2 sections

## Phase 4: Engagement & Team

- [x] 4.1 `InboxSummary.vue` — per-platform inbox cards, activity dot
- [x] 4.2 `TeamActivity.vue` — action feed, online indicator
- [x] 4.3 Add Phase 3 sections to DashboardLayout
- [x] 4.4 Component tests for engagement sections

## Phase 5: Integration

- [x] 5.1 Modify `HomeView.vue` — replace grid with `<DashboardLayout />`
- [x] 5.2 Feature flag toggle via localStorage
- [x] 5.3 Responsive verification (desktop/tablet/mobile)
- [x] 5.4 `vue-tsc --noEmit` zero errors
- [x] 5.5 i18n completeness audit
- [x] 5.6 Design system token compliance audit

## Critical Path

`1.1 → 1.3–1.8 → 1.10–1.13 → 1.16 → 1.17–1.21 → 2.1–2.5 → 5.1`

## Parallelizable

- Mock data (1.3–1.8), CSS (1.14) + i18n (1.15), shared components (1.17–1.21), sections within each phase
