# Delta Spec: Scheduler URL Addressability

## Scope

This change makes scheduler calendar state shareable and restorable via URL. Route is the single
source of truth; Pinia store becomes derived/mirrored state only. Refetches calendar data when
route-driven date/view/query changes affect the visible range.

**Affected domain specs:** `visual-calendar`, `app-shell`
**Artifact store mode:** `openspec`

---

## Glossary: URL Param Model

| Param        | Type                                          | Default       | Description                     |
|--------------|-----------------------------------------------|---------------|---------------------------------|
| `date`       | `YYYY-MM-DD`                                  | today (local) | Center date for week/month view |
| `channels[]` | `accountId[]`                                 | all (absent)  | Filter by social account ID     |
| `timezone`   | `IANA string`                                 | browser TZ    | User's preferred timezone       |
| `status`     | `PUBLISHED\|SCHEDULED\|QUEUED\|DRAFT\|FAILED` | all           | Filter by publication status    |
| `q`          | string                                        | empty         | Free-text search filter         |
| `mode`       | `calendar\|list`                              | `calendar`    | Top-level surface mode          |

### Canonical Routes

| Path                        | Behavior                                                       |
|-----------------------------|----------------------------------------------------------------|
| `/scheduler`                | Redirect → `/scheduler/calendar/week` (preserves query params) |
| `/scheduler/calendar/week`  | Week calendar view                                             |
| `/scheduler/calendar/month` | Month calendar view                                            |
| `/scheduler/list`           | List view                                                      |

---

## ADDED Requirements

### Requirement: Canonical Scheduler Route Family

The router MUST define `/scheduler/calendar/week`, `/scheduler/calendar/month`, and
`/scheduler/list` as named routes. Navigating to `/scheduler` MUST redirect to
`/scheduler/calendar/week` preserving any existing query params.

#### Scenario: `/scheduler` redirects to canonical week route

- GIVEN a user is on `/scheduler`
- WHEN the route resolves
- THEN the browser URL becomes `/scheduler/calendar/week`
- AND any query params present on `/scheduler` are preserved (e.g., `/scheduler?q=post` →
  `/scheduler/calendar/week?q=post`)

#### Scenario: Canonical routes are directly accessible

- GIVEN a user opens `/scheduler/calendar/month`
- WHEN the page loads
- THEN the month view renders with the current month
- AND no redirect occurs

---

### Requirement: Route Query Param Contract

The system MUST parse and serialize `date`, `channels[]`, `timezone`, `status`, `q`, and `mode` as
URL query params. Absent params MUST default to: `date` = today (local), `mode` = `calendar`, all
others = show-all/unfiltered.

#### Scenario: `channels[]` uses account IDs in URL

- GIVEN the user has selected a LinkedIn account with `accountId = "acc-123"`
- WHEN the sidebar channel is clicked
- THEN the URL contains `?channels[]=acc-123`
- AND `filterChannel` (provider name) is NOT written to the URL

#### Scenario: All Channels navigates without filter

- GIVEN the user is on `/scheduler/calendar/week?channels[]=acc-123`
- WHEN the user clicks "All channels" in the sidebar
- THEN the URL becomes `/scheduler/calendar/week` (no `channels[]` param)
- AND no `channels[]` key appears in the query string

#### Scenario: Missing params default correctly

- GIVEN the user opens `/scheduler/calendar/week?date=2026-06-15&timezone=America/New_York`
- WHEN the view renders
- THEN the calendar centers on 2026-06-15
- AND timezone is `America/New_York`
- AND status filter is cleared (show all)
- AND no `channels[]` filter is applied

---

### Requirement: Route State Derivation

`SchedulerView.vue` MUST derive `calendarView` (week/month), `currentBaseDate`, `timezone`, and
filter values from `useRoute()`. The Pinia `publishingStore` MUST be updated with explicit fetch
args, NOT read route state internally.

#### Scenario: Refresh preserves view and filters

- GIVEN the user is on `/scheduler/calendar/week?date=2026-06-15&status=SCHEDULED`
- WHEN the browser refreshes
- THEN the week view renders centered on 2026-06-15
- AND only SCHEDULED publications are shown

#### Scenario: `fetchCalendar` refetches on route change

- GIVEN `SchedulerView` has fetched publications for week A
- WHEN the user navigates to week B (via date picker or arrow)
- THEN `fetchCalendar(from, to, { status, socialAccountId, timezone })` is called with the new range
- AND the calendar updates without a full page reload

---

### Requirement: CalendarHeader Navigates with Route Updates

`CalendarHeader.vue` MUST emit navigation intent (`change:view`, `change:date`, `change:filter`)
instead of mutating Pinia refs directly. The shell MUST update the route using `router.push()` for
deliberate view/date changes and `router.replace()` for transient filter keystrokes.

#### Scenario: Changing view updates URL path

- GIVEN the user is on `/scheduler/calendar/week`
- WHEN the user clicks "Month"
- THEN the URL becomes `/scheduler/calendar/month`
- AND query params (`date`, `channels[]`, `status`) are preserved

#### Scenario: Changing filter updates URL query string

- GIVEN the user is on `/scheduler/calendar/week`
- WHEN the user selects "SCHEDULED" status
- THEN `router.replace()` is called adding `?status=SCHEDULED` to the URL
- AND browser history is NOT polluted with filter-only entries

#### Scenario: `date` param updates on navigation

- GIVEN the user is on `/scheduler/calendar/week?date=2026-06-15`
- WHEN the user clicks the next-week arrow
- THEN the URL updates to `/scheduler/calendar/week?date=2026-06-22`
- AND the calendar re-renders for the new week

---

### Requirement: Browser History Integration

Browser back/forward buttons MUST restore scheduler state from the URL without manual store resets
or extra hydration logic.

#### Scenario: Back button restores previous state

- GIVEN the user visits `/scheduler/calendar/week?status=SCHEDULED`
- AND navigates to `/scheduler/calendar/month?status=SCHEDULED`
- WHEN the user clicks the browser back button
- THEN the URL returns to `/scheduler/calendar/week?status=SCHEDULED`
- AND the week view renders with SCHEDULED filter active

#### Scenario: Forward button restores forward state

- GIVEN the user navigates back using browser back
- WHEN the user clicks browser forward
- THEN the URL returns to `/scheduler/calendar/month?status=SCHEDULED`
- AND the month view renders correctly

---

## MODIFIED Requirements

### Requirement: Multi-View Calendar (visual-calendar)

The system MUST provide day, week, and month views. Week and month views are addressable via
`/scheduler/calendar/week` and `/scheduler/calendar/month`. Day view is NOT a top-level route;
clicking a day in month/week focuses `date=YYYY-MM-DD` within the current week/month context without
a separate route.

(Previously: all three views were non-addressable with no URL contract)

#### Scenario: Clicking day in month view focuses date

- GIVEN the user is on `/scheduler/calendar/month?date=2026-06-15`
- WHEN the user clicks a day cell (e.g., June 20)
- THEN the URL becomes `/scheduler/calendar/month?date=2026-06-20`
- AND the calendar centers on June 20 while remaining in month view

#### Scenario: Week view is accessible and shareable

- GIVEN a user shares the URL `/scheduler/calendar/week?date=2026-06-20&channels[]=acc-123`
- WHEN the recipient opens the link
- THEN the week containing June 20 renders
- AND only publications for `acc-123` are shown

---

### Requirement: SidebarChannelsSection Navigation (app-shell)

`SidebarChannelsSection.vue` MUST emit `selectAll` and `selectChannel` with the channel's
`accountId`. The shell MUST navigate to the current scheduler route with `channels[]=<accountId>` as
a query param, NOT mutate `filterChannel` directly.

(Previously: sidebar emitted `selectChannel(channel)` and shell set `filterChannel` and pushed
`/scheduler`)

#### Scenario: Channel click writes `channels[]` to URL

- GIVEN the user is on `/scheduler/calendar/week`
- WHEN the user clicks the "LinkedIn" channel row
- THEN `router.push({ query: { channels[]: 'acc-linkedin' } })` is called
- AND the URL becomes `/scheduler/calendar/week?channels[]=acc-linkedin`

#### Scenario: Multiple channels accumulate in URL

- GIVEN the user is on `/scheduler/calendar/week?channels[]=acc-linkedin`
- WHEN the user additionally selects the "Bluesky" channel
- THEN the URL becomes `/scheduler/calendar/week?channels[]=acc-linkedin&channels[]=acc-bluesky`

#### Scenario: Active channel state derives from URL

- GIVEN the URL is `/scheduler/calendar/week?channels[]=acc-123`
- WHEN `SidebarChannelsSection` renders
- THEN the channel row for `acc-123` is visually marked as active

---

## REMOVED Requirements

None.

---

## Exact Files to Change

| File                                                             | Change                                                                                 |
|------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `apps/web/app/src/router/index.ts`                               | Add scheduler route family, `/scheduler` redirect, canonical route names               |
| `apps/web/app/src/views/SchedulerView.vue`                       | Derive state from route, add `useRoute()` watchers for refetch triggers                |
| `apps/web/app/src/stores/publishing.ts`                          | Accept explicit args in `fetchCalendar()`, stop reading route-relevant refs internally |
| `apps/web/app/src/components/CalendarHeader.vue`                 | Emit navigation intent, remove direct Pinia mutations for route-relevant fields        |
| `apps/web/app/src/components/layout/AppShell.vue`                | Handle sidebar channel events → route navigation with `channels[]` query param         |
| `apps/web/app/src/components/sidebar/SidebarChannelsSection.vue` | Pass `accountId` in emit, derive active state from route                               |
| `apps/web/app/src/router/index.spec.ts`                          | Add route definitions, redirect, and param round-trip tests                            |
| `apps/web/app/src/router/index.guard.test.ts`                    | Update auth redirect assertions for deep scheduler URLs                                |
| `apps/web/app/src/views/SchedulerView.test.ts`                   | Add route-driven init, refetch, and back/forward scenarios                             |
| `apps/web/app/src/components/CalendarHeader.test.ts`             | Update from store mutation expectations to navigation event expectations               |
| `apps/web/app/src/stores/publishing.test.ts`                     | Update if `fetchCalendar` signature changes                                            |
| `apps/web/app/e2e/**`                                            | Add deep-link, refresh, and navigation history coverage for scheduler routes           |

---

## Acceptance Criteria Checklist

| # | Criterion                                                                           | Scenarios                                                            |
|---|-------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| 1 | `/scheduler` redirects to `/scheduler/calendar/week` preserving query               | `Redirect` scenario                                                  |
| 2 | `/scheduler/calendar/week` and `/scheduler/calendar/month` restore state on refresh | `Refresh preserves view and filters` scenario                        |
| 3 | `date`, `channels[]`, `timezone`, `status`, `q`, `mode` round-trip through URL      | `Missing params default correctly`, `channels[] uses account IDs`    |
| 4 | Browser back/forward restores scheduler state                                       | `Back button restores`, `Forward button restores`                    |
| 5 | Sidebar channel clicks navigate with `channels[]=<accountId>`                       | `Channel click writes channels[]`, `Active channel derives from URL` |
| 6 | `fetchCalendar` refetches on route-driven changes                                   | `fetchCalendar refetches on route change`                            |
| 7 | Unit tests cover route param reactivity and URL sync                                | `index.spec.ts`, `SchedulerView.test.ts`, `CalendarHeader.test.ts`   |
| 8 | E2E covers filter-URL-refresh cycle                                                 | `e2e/**`                                                             |
