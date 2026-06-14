# Proposal: Social Media Operating System Dashboard

## Intent

The existing HomeView has 4 static KPI cards and a recent-activity stream. Users need an at-a-glance command center that surfaces performance, AI-driven recommendations, content pipeline status, and cross-channel analytics — turning Profile Tailors from a scheduling tool into a social media operating system.

## Scope

### In Scope

- **Phase 1 (MVP):** Executive Overview, AI Insights hero, Growth Score, Top Performing Posts, Cross-Channel Analytics — all with mock data, wired to real APIs later
- **Phase 2:** Audience Growth chart, Upcoming Schedule widget, Best Posting Times heatmap, Content Pipeline kanban
- **Phase 3:** Inbox Summary, Team Activity
- Component architecture: one component per section in `src/components/dashboard/`
- New Pinia stores: `analytics.ts` (KPIs, growth score), `insights.ts` (AI recommendations), `contentPipeline.ts` (kanban state)
- Design tokens for charts: sparkline, bar, heatmap palette extensions to `main.css`
- i18n keys for all 11 sections (EN/ES)
- Mock data layer with typed fixtures — zero backend dependency for Phase 1

### Out of Scope

- Backend analytics API (follow-up change)
- Real AI insights generation (mock recommendations for now)
- WebSocket/SSE for real-time data (polling design only)
- X, Bluesky, Threads integrations (LinkedIn-only for MVP)
- Drag-and-drop kanban library evaluation (defer to Phase 2)

## Capabilities

### New Capabilities
- `dashboard-overview`: KPI cards with sparklines, trend comparisons, and period-over-period deltas
- `dashboard-insights`: AI recommendation hero section with actionable CTAs
- `dashboard-growth-score`: Composite metric (84/100) with breakdown and top opportunity
- `dashboard-analytics`: Cross-channel performance bars and top performing posts list
- `dashboard-content-pipeline`: Kanban board (Ideas → Drafts → Scheduled → Published)
- `dashboard-scheduling`: Best posting times heatmap and upcoming schedule widget
- `dashboard-engagement`: Inbox summary and team activity feed

### Modified Capabilities
- None — this is additive; existing publishing/calendar specs remain unchanged

## Approach

**Mock-first, component-per-section.** Build all 11 sections against typed mock fixtures. Each section is a standalone component with props — no section depends on another's state. Pinia stores hold mock data with typed interfaces that match future API contracts. This lets us ship the full UI, validate the design system, and swap mock → real without component rewrites.

**Phased delivery:** Phase 1 ships the top-5 high-value sections. Phase 2 adds schedule/pipeline/heatmap. Phase 3 adds inbox/team. Each phase is independently deployable.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/components/dashboard/` | New | 11 section components + shared chart primitives |
| `apps/web/app/src/stores/` | New | `analytics.ts`, `insights.ts`, `contentPipeline.ts` |
| `apps/web/app/src/views/HomeView.vue` | Modified | Replace 4-card grid with dashboard section composition |
| `apps/web/app/src/i18n/index.ts` | Modified | ~120 new translation keys (EN/ES) |
| `apps/web/app/src/assets/main.css` | Modified | Chart color tokens, sparkline/heatmap utilities |
| `apps/web/app/src/lib/` | New | `mockData/` fixtures, `formatters.ts` (number/percent) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| HomeView becomes monolith | Med | Extract section composition to `DashboardLayout.vue` |
| Chart library bloat | Low | Use lightweight SVG primitives or tiny lib (uplot) |
| Mock data drifts from future API | Low | Type fixtures against API contract interfaces |
| 11 sections overwhelm on small screens | Med | Mobile-first responsive grid, fold sections into tabs |

## Rollback Plan

All changes are additive to the Vue app. Revert by removing `src/components/dashboard/`, new stores, and restoring original `HomeView.vue` from git. No backend changes to revert.

## Dependencies

- Existing shadcn-vue components (Card, Button, Tooltip) — already available
- Existing design tokens in `main.css` — already available
- Publishing store channels/publications — read-only, no modifications

## Success Criteria

- [ ] All 11 sections render with mock data on desktop and mobile
- [ ] Design system tokens used consistently (no hardcoded colors/fonts)
- [ ] i18n complete — every user-visible string in EN and ES
- [ ] HomeView decomposed — no single file > 200 lines
- [ ] Each section component is independently testable with props
- [ ] Zero TypeScript errors, zero console warnings
