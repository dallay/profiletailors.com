# Exploration: Social Media Operating System Dashboard

## Current State

The Profile Tailors monorepo has **two separate frontend applications**:

1. **Marketing Site** (`apps/web/marketing/`) - Astro 6, static-first landing page
2. **Dashboard App** (`apps/web/app/`) - Vue 3 SPA with existing dashboard functionality

**Critical Discovery:** The dashboard already exists in the Vue app, NOT the marketing site. The marketing site is a static landing page for waitlist/signups. The Vue app is the actual product application with auth, routing, and state management.

### Existing Dashboard (Vue App)

The Vue app already has:
- **HomeView** with basic KPI cards (Scheduled Posts, Connected Platforms, Audience Reach, Avg Engagement)
- **SchedulerView** with calendar-based content management
- **AnalyticsView** with mock chart visualizations
- **Sidebar navigation** with workspace switching
- **Auth system** (JWT-based)
- **Publishing store** with publications, channels, and calendar API integration
- **i18n** with EN/ES translations
- **shadcn-vue** component library (Card, Button, Sidebar, Tooltip)

### Existing Design System

Defined in `.agents/DESIGN.md` and implemented in:
- `apps/web/app/src/assets/main.css` (Vue app tokens)
- `apps/web/marketing/src/styles/global.css` (Marketing site tokens)

**Key Design Tokens:**
- Colors: OLED black (#0a0a0a) primary, warm off-white (#F5F5F0) light mode
- Fonts: Doto (hero only), Space Grotesk (body/UI), Space Mono (labels/data)
- Status: Success (#4A9E5C), Warning (#D4A843), Error (#D71921), Info (#a3a3a3)
- Spacing: 8px base, jumps at 32/64/96px
- Radii: 0px (technical), 4px (compact), 8px (general), 12px (cards), 16px (large), 999px (pills)

## Affected Areas

### Frontend (Vue App - `apps/web/app/`)

| Path | Why Affected |
|------|--------------|
| `src/views/HomeView.vue` | Main dashboard view - needs expansion for 11 sections |
| `src/components/` | New components for each dashboard section |
| `src/stores/` | New stores for analytics, insights, content pipeline |
| `src/i18n/index.ts` | New translation keys for dashboard sections |
| `src/assets/main.css` | May need new tokens for charts/visualizations |
| `src/lib/` | Utility functions for data processing |

### Backend (Spring Boot - `server/smp/`)

| Path | Why Affected |
|------|--------------|
| `publishing/` | Existing publication APIs - may need analytics extensions |
| `identity/` | User/workspace context for dashboard data |
| New module? | Analytics/insights domain may need new bounded context |

### Shared (`shared/`)

| Path | Why Affected |
|------|--------------|
| `common/` | Shared domain types for analytics |
| `bus/` | CQRS commands/queries for dashboard data |

## Approaches

### Approach 1: Extend Existing Vue App (Recommended)

Build the dashboard as new views/components within the existing Vue SPA.

**Pros:**
- Auth, routing, state management already in place
- shadcn-vue components available
- Publishing store has channel/publication data
- i18n infrastructure ready
- Backend API proxy already configured

**Cons:**
- HomeView may become too large - needs decomposition
- Analytics data may require new backend endpoints
- Chart libraries need to be added (e.g., Chart.js, ApexCharts)

**Effort:** Medium

### Approach 2: New Dedicated Dashboard App

Create a separate app in `apps/web/dashboard/` specifically for the OS dashboard.

**Pros:**
- Clean separation of concerns
- Can optimize specifically for dashboard use case
- Independent deployment possible

**Cons:**
- Duplicates auth, routing, component infrastructure
- Loses existing publishing store integration
- More maintenance overhead
- User asked for ONE dashboard, not separate apps

**Effort:** High

### Approach 3: Marketing Site + Dashboard Hybrid

Add dashboard functionality to the Astro marketing site.

**Pros:**
- Single codebase for all frontend

**Cons:**
- Astro is static-first - dashboard needs dynamic data, auth, real-time updates
- Would require Astro SSR or client-side hydration for all dashboard features
- Conflicts with "static-first" constraint in AGENTS.md
- Auth system doesn't exist in marketing site

**Effort:** High + Architectural Mismatch

## Recommendation

**Approach 1: Extend Existing Vue App**

The dashboard should be built within the existing Vue app at `apps/web/app/`. This is where:
- Auth is already implemented
- Routing with protected routes exists
- Publishing data (channels, publications) is already available
- The sidebar navigation and workspace switching are in place
- i18n with EN/ES is ready

The 11 sections should be implemented as:
1. **New components** in `src/components/dashboard/`
2. **New views** or expanded HomeView
3. **New stores** for analytics, insights, content pipeline
4. **New API endpoints** in the backend for analytics data

## Risks

1. **Backend Analytics Gap**
   - Current backend has publishing APIs but no analytics/insights endpoints
   - Growth Score, AI Insights, Best Posting Times need new backend domain
   - Mitigation: Start with mock data, implement backend in parallel

2. **Chart Library Selection**
   - Need to choose a charting library that fits the monochrome design
   - Must support sparklines, bar charts, heatmaps
   - Mitigation: Evaluate Chart.js, ApexCharts, or lightweight SVG charts

3. **Real-time Data**
   - Some sections (Inbox Summary, Team Activity) may need WebSocket/SSE
   - Backend has SSE for channel events - can extend pattern
   - Mitigation: Design for polling initially, add real-time later

4. **Content Pipeline (Kanban)**
   - Kanban requires drag-and-drop - needs library (vuedraggable, dnd-kit)
   - Current publishing store has publication statuses but no pipeline view
   - Mitigation: Evaluate drag-and-drop libraries, design API for pipeline state

5. **Cross-Channel Analytics**
   - Only LinkedIn integration exists in backend
   - X, Bluesky, Threads need new integrations
   - Mitigation: Design抽象层 for multi-platform, implement LinkedIn first

## Key Decisions Needed

1. **Chart Library:** Which library for data visualization?
2. **Kanban Library:** Which drag-and-drop library for Content Pipeline?
3. **Backend Scope:** Build analytics backend now or mock first?
4. **Section Priority:** Which of the 11 sections to implement first?
5. **Data Source:** Real API data or mock data for initial implementation?

## Ready for Proposal

**Yes** - The exploration is complete. The recommendation is clear: extend the existing Vue app. The orchestrator should now proceed to proposal phase with:

- Scope: 11 dashboard sections in Vue app
- Approach: Component-based architecture with new stores
- Dependencies: Chart library, Kanlib library decisions
- Backend: Analytics API endpoints needed
