# Design: Social Media Operating System Dashboard

## Technical Approach

Mock-first, component-per-section architecture. Each of the 11 dashboard sections is a standalone component receiving typed props from Pinia stores. Stores hold typed mock fixtures matching future API contracts, enabling a mock → real swap without component rewrites. The layout uses a CSS Grid system managed by `DashboardLayout.vue`, replacing the current monolithic `HomeView.vue` (242 lines).

Phased delivery: Phase 1 delivers 5 high-value sections; Phase 2 adds pipeline/schedule/heatmap; Phase 3 adds inbox/team. Each phase is independently deployable.

## Architecture Decisions

### Decision: Chart Library — uPlot

| Option | Tradeoff | Decision |
|--------|----------|----------|
| uPlot (~33KB min) | Tiny bundle, fast rendering, limited chart types. Needs custom sparkline/bar wrappers. | ✅ Recommended |
| Chart.js (~70KB) | Broad chart types, large community. Heavier bundle for simple sparklines. | ❌ Too heavy for sparklines |
| ApexCharts (~130KB) | Rich features, interactive. Way too heavy for dashboard cards. | ❌ Bundle bloat |
| Raw SVG + D3 | Full control, zero deps. High implementation cost for heatmaps and interaction. | ❌ Maintenance burden |
| lightweight-charts (TradingView) | Great for line charts. No bar/heatmap support. | ❌ Limited scope |

**Rationale:** uPlot gives us sparklines, bar charts, and line charts at ~33KB. For the heatmap (Best Posting Times), we build a simple HTML/CSS grid component — no library needed. This keeps total chart overhead under 40KB gzipped.

### Decision: Drag-and-Drop — Defer to Phase 2

**Choice:** No DnD library in Phase 1. Kanban uses a simple list layout with move buttons.
**Alternatives considered:** vue-draggable-plus, @dnd-kit, SortableJS
**Rationale:** Proposal explicitly defers DnD evaluation to Phase 2. Phase 1 validates the kanban data model and UI with simple controls first.

### Decision: Store Granularity — One Store Per Domain

**Choice:** Three new stores (`analytics.ts`, `insights.ts`, `contentPipeline.ts`) plus one `dashboard.ts` orchestrator.
**Alternatives considered:** Single `dashboard.ts` store for all state; per-section stores.
**Rationale:** Follows existing pattern (`publishing.ts`, `auth.ts` — one store per bounded context). The `dashboard.ts` orchestrator coordinates refresh cycles without coupling section stores to each other.

### Decision: Component Props vs. Store Injection

**Choice:** Section components receive data via props from a parent `DashboardLayout.vue` that reads stores. Sections are presentation-only.
**Alternatives considered:** Each section injects its own store via `useStore()`.
**Rationale:** Props-first makes sections independently testable (mount with fake data, no store mock needed). The layout orchestrator owns the store wiring. This matches the proposal's "each section is a standalone component with props."

### Decision: i18n — Extend Existing Flat Structure

**Choice:** Add `dashboard.*` keys to the existing `i18n/index.ts` messages object.
**Alternatives considered:** Split into per-domain JSON files; use `vue-i18n` lazy loading.
**Rationale:** Current i18n is a single file with ~150 keys. Adding ~120 dashboard keys keeps it under 300 — still manageable without a split. Follows the existing pattern exactly.

## Data Flow

```
DashboardLayout.vue (orchestrator)
  │
  ├── reads useAnalyticsStore()     → KPI cards, growth score, analytics
  ├── reads useInsightsStore()      → AI recommendations
  ├── reads useContentPipelineStore() → Kanban pipeline
  │
  └── passes props to sections
        │
        ├── <ExecutiveOverview :kpis="..." />
        ├── <AiInsightsHero :recommendations="..." />
        ├── <GrowthScore :score="..." />
        ├── <TopPerformingPosts :posts="..." />
        ├── <CrossChannelAnalytics :channels="..." />
        ├── <AudienceGrowthChart :data="..." />
        ├── <UpcomingSchedule :items="..." />
        ├── <BestPostingTimes :heatmap="..." />
        ├── <ContentPipeline :columns="..." />
        ├── <InboxSummary :threads="..." />
        └── <TeamActivity :events="..." />
```

Stores initialize from mock fixtures on creation. A `refreshAll()` action on `dashboard.ts` would later trigger API calls — for now it's a no-op that logs "mock mode."

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/components/dashboard/DashboardLayout.vue` | Create | Grid orchestrator, reads stores, passes props to sections |
| `src/components/dashboard/ExecutiveOverview.vue` | Create | Phase 1 — KPI row with sparklines (Scheduled, Platforms, Audience, Engagement) |
| `src/components/dashboard/AiInsightsHero.vue` | Create | Phase 1 — AI recommendation cards with actionable CTAs |
| `src/components/dashboard/GrowthScore.vue` | Create | Phase 1 — Composite score gauge (84/100) with breakdown |
| `src/components/dashboard/TopPerformingPosts.vue` | Create | Phase 1 — Ranked post list with metrics |
| `src/components/dashboard/CrossChannelAnalytics.vue` | Create | Phase 1 — Platform performance bars |
| `src/components/dashboard/AudienceGrowthChart.vue` | Create | Phase 2 — Line chart (30-day audience trend) |
| `src/components/dashboard/UpcomingSchedule.vue` | Create | Phase 2 — Next scheduled posts widget |
| `src/components/dashboard/BestPostingTimes.vue` | Create | Phase 2 — Heatmap grid (day × hour) |
| `src/components/dashboard/ContentPipeline.vue` | Create | Phase 2 — Kanban board (Ideas → Drafts → Scheduled → Published) |
| `src/components/dashboard/InboxSummary.vue` | Create | Phase 3 — Message threads and reply status |
| `src/components/dashboard/TeamActivity.vue` | Create | Phase 3 — Team member action feed |
| `src/components/dashboard/shared/KpiCard.vue` | Create | Reusable KPI card with sparkline slot |
| `src/components/dashboard/shared/SparklineChart.vue` | Create | uPlot-based mini sparkline (60×24px) |
| `src/components/dashboard/shared/PlatformBar.vue` | Create | Horizontal bar chart for platform comparison |
| `src/components/dashboard/shared/HeatmapGrid.vue` | Create | CSS grid heatmap for posting times |
| `src/components/dashboard/shared/ScoreGauge.vue` | Create | Circular/arc gauge for growth score |
| `src/stores/analytics.ts` | Create | KPIs, growth score, channel performance, top posts |
| `src/stores/insights.ts` | Create | AI recommendations, actions, priority levels |
| `src/stores/contentPipeline.ts` | Create | Kanban columns, cards, stage transitions |
| `src/stores/dashboard.ts` | Create | Orchestrator — coordinates refresh, loading states |
| `src/lib/mockData/analytics.ts` | Create | Typed mock fixtures for analytics data |
| `src/lib/mockData/insights.ts` | Create | Typed mock fixtures for AI recommendations |
| `src/lib/mockData/contentPipeline.ts` | Create | Typed mock fixtures for kanban columns |
| `src/lib/formatters.ts` | Create | `formatNumber()`, `formatPercent()`, `formatDelta()` helpers |
| `src/views/HomeView.vue` | Modify | Replace 4-card grid + activity stream with `<DashboardLayout />` |
| `src/i18n/index.ts` | Modify | Add ~120 EN/ES keys under `dashboard.*` namespace |
| `src/assets/main.css` | Modify | Add chart color tokens, sparkline/heatmap utilities |

## Interfaces / Contracts

### Analytics Store

```ts
// src/stores/analytics.ts
interface KpiMetric {
  id: string
  label: string          // i18n key
  value: string          // formatted display value
  delta: number          // period-over-period change %
  deltaLabel: string     // "vs last 30 days"
  sparklineData: number[] // 7-point trend
  trend: 'up' | 'down' | 'flat'
}

interface TopPost {
  id: string
  content: string
  platform: 'linkedin' | 'twitter' | 'instagram' | 'facebook'
  publishedAt: string
  impressions: number
  engagementRate: number
  reactions: number
  comments: number
  shares: number
}

interface ChannelPerformance {
  platform: string
  followers: number
  growth: number
  engagementRate: number
  postsCount: number
  color: string           // brand color for bar chart
}

interface GrowthScore {
  overall: number         // 0-100
  breakdown: {
    consistency: number   // 0-100
    engagement: number    // 0-100
    growth: number        // 0-100
    reach: number         // 0-100
  }
  topOpportunity: string  // i18n key
  trend: 'improving' | 'declining' | 'stable'
}
```

### Insights Store

```ts
// src/stores/insights.ts
interface AiInsight {
  id: string
  type: 'recommendation' | 'alert' | 'opportunity'
  title: string
  description: string
  actionLabel: string     // CTA text
  actionTarget?: string   // route or external URL
  priority: 'high' | 'medium' | 'low'
  createdAt: string
  dismissed: boolean
}
```

### Content Pipeline Store

```ts
// src/stores/contentPipeline.ts
interface PipelineCard {
  id: string
  title: string
  content: string
  platform: string
  scheduledFor?: string
  author: string
  thumbnail?: string
  tags: string[]
}

interface PipelineColumn {
  id: string            // 'ideas' | 'drafts' | 'scheduled' | 'published'
  title: string         // i18n key
  cards: PipelineCard[]
}
```

### Dashboard Orchestrator

```ts
// src/stores/dashboard.ts
interface DashboardState {
  isLoading: boolean
  lastRefreshedAt: string | null
  error: string | null
  sections: Record<string, { visible: boolean; loading: boolean }>
}
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `formatters.ts` — number/percent formatting | Vitest, pure function tests |
| Unit | Store getters and computed state | Vitest with `createPinia()` + `setActivePinia()` |
| Component | Each section component renders with props | `@vue/test-utils` mount with fake data props |
| Component | `KpiCard` renders value, delta, sparkline | Mount with explicit props, assert DOM |
| Component | `DashboardLayout` wires stores to sections | Mount with populated stores, assert child rendering |
| Integration | Store actions mutate state correctly | Vitest, call action → assert state change |
| Visual | Dashboard matches design on dark/light | Playwright screenshot comparison (future) |

No E2E tests in Phase 1 — mock data layer is deterministic, visual regression is sufficient.

## Migration / Rollout

No data migration required. All changes are additive to the Vue app.

**Phased rollout:**
- Phase 1: Replace `HomeView.vue` body with `<DashboardLayout />` showing 5 sections. The QuickStart component moves to a dedicated onboarding route or remains as a dismissible banner.
- Phase 2: Add 4 more sections to `DashboardLayout` grid. No breaking changes.
- Phase 3: Add final 2 sections. Complete the 11-section dashboard.

**Feature flag:** Wrap `<DashboardLayout />` in a `v-if="showNewDashboard"` toggle (localStorage key) so the old `HomeView` can be restored during development.

## Open Questions

- [ ] Should `QuickStart.vue` be removed from HomeView or retained as a collapsible section? (Proposal implies removal — need confirmation)
- [ ] uPlot is the recommendation, but team may prefer Chart.js for broader ecosystem. Confirm before Phase 1.
- [ ] Mobile layout: tabs vs. accordion vs. scroll. Proposal mentions tabs — need UX spec.
- [ ] Future API base URL and auth pattern for dashboard endpoints — depends on backend progress.
