# Proposal: Scheduler calendar URL addressability

## Intent

Make scheduler state shareable and restorable via URL so refresh, deep links, login redirect return,
and browser back/forward preserve the selected scheduler surface, date, filters, and timezone.

## Scope

- Add canonical routes: `/scheduler` → redirect, `/scheduler/calendar/week`,
  `/scheduler/calendar/month`, `/scheduler/list`
- Add route query contract: `date`, `channels[]`, `timezone`, `status`, `q`, `mode`
- Make the URL the single source of truth; scheduler store becomes derived/mirrored state only
- Refetch calendar data when route-driven date/view/query changes affect the visible range
- Update sidebar channel navigation to write `channels[]=<accountId>` consistently
- Spec impact: modify `visual-calendar` and `app-shell`
- Out of scope: new scheduler backend filters beyond existing supported calendar fetch inputs

## Approach

Create a typed scheduler route-state codec/composable that parses and serializes route params,
normalizes defaults, and distinguishes `router.push` for deliberate navigation from `router.replace`
for transient query edits.

Resolve day view by dropping it from URL-addressable navigation in this change. The canonical
calendar surfaces are week and month only; clicking a month cell focuses the selected `date` inside
week/month behavior instead of entering a separate day route.

Resolve `channels[]` to mean social account IDs only. Provider names remain presentation data;
fetches and active sidebar selection use `accountId` consistently.

## Affected files

- `apps/web/app/src/router/index.ts` — scheduler route family and redirect rules
- `apps/web/app/src/views/SchedulerView.vue` — route-derived state, range calculation, refetch
  triggers
- `apps/web/app/src/stores/publishing.ts` — explicit fetch args, compatibility mirrors, filter
  cleanup
- `apps/web/app/src/components/CalendarHeader.vue` — emit navigation/filter intent instead of direct
  store mutation
- `apps/web/app/src/components/layout/AppShell.vue` — sidebar route navigation with `channels[]`
- `apps/web/app/src/components/sidebar/SidebarChannelsSection.vue` — active state from route-backed
  account selection
- `apps/web/app/src/router/index.spec.ts`, `index.guard.test.ts`, `SchedulerView.test.ts`,
  `CalendarHeader.test.ts`, `apps/web/app/e2e/**`

## Migration strategy

Land route parsing and canonical redirects first, keep temporary store mirrors for compatibility,
then move header/sidebar interactions to route updates and remove direct route-relevant store
ownership.

## Rollback plan

Revert scheduler route additions and restore store-owned scheduler state in `SchedulerView`,
`CalendarHeader`, and `AppShell`; preserve `/scheduler` as the sole entry route and remove
query-driven refetch watchers.

## Open questions

None. This proposal resolves day-view removal from URL scope and standardizes `channels[]` as
account IDs.

## Risks

- Timezone/date normalization may cause off-by-one visible periods near UTC boundaries
- Frequent query updates could create noisy history or duplicate refetches
- Sidebar active state may drift during compatibility transition

## Acceptance criteria

- `/scheduler` redirects to canonical scheduler URL without losing query params
- `/scheduler/calendar/week`, `/scheduler/calendar/month`, and `/scheduler/list` restore state on
  refresh and auth redirect return
- `date`, `channels[]`, `timezone`, `status`, `q`, and `mode` round-trip through the URL
- Browser back/forward restores scheduler state without manual store resets
- Sidebar channel clicks navigate with `channels[]=<accountId>`
- `fetchCalendar` refetches when route-driven date/view/query changes require a new range or filter
  set
