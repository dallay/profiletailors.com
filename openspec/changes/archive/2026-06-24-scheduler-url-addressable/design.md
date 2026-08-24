# Design: Scheduler calendar URL addressability

## Technical Approach

Make the scheduler route the single source of truth for surface, date, and filters.
`SchedulerView.vue` will consume a new `useCalendarUrl()` composable that parses route params/query
into typed state, exposes setters for user intent, and serializes canonical URLs back through Vue
Router. The publishing store keeps fetched data and async state, while route-owned controls become
derived values or short-lived compatibility mirrors.

## Architecture Decisions

| Decision                | Options                                            | Choice                                                                                | Rationale                                                                                                        |
|-------------------------|----------------------------------------------------|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Route ownership         | Store-first sync vs route-first                    | Route-first                                                                           | Avoids watcher loops and makes refresh, auth redirect, and back/forward work naturally.                          |
| Calendar surface model  | Keep `/scheduler` only vs canonical family         | `/scheduler` redirect + `/scheduler/calendar/:range(week\|month)` + `/scheduler/list` | Matches proposal, keeps URLs shareable, and removes ambiguous implicit state.                                    |
| Day behavior            | Keep routeable day view vs fold into selected date | No canonical day route in phase 1                                                     | Proposal resolves canonical surfaces to week/month/list; month cell click updates `date` and lands on week view. |
| Channel filter contract | Provider names vs account IDs                      | `channels[]` = social account IDs                                                     | Aligns with backend `socialAccountId` filter and sidebar data model.                                             |

## Data Flow

Route tree:

- `/scheduler` → redirect to named route `scheduler-calendar-week`, preserving query
-

`/scheduler/calendar/week?date=YYYY-MM-DD&channels[]=acc-1&timezone=Europe/Madrid&status=QUEUED&q=text`

- `/scheduler/calendar/month?...same query schema...`
- `/scheduler/list?...same query schema...&mode=list` (mode accepted temporarily, path is canonical)

Query schema:

- `date`: canonical local date string, default today in resolved timezone
- `channels[]`: repeated account IDs, default empty
- `timezone`: IANA zone, default browser/store timezone
- `status`: `QUEUED|PUBLISHED|CANCELLED` only
- `q`: trimmed free text
- `mode`: temporary compatibility query; normalized to path

```text
$route.params/$route.query
        ↓ parse + normalize
   useCalendarUrl()
        ↓ typed state + setters
 SchedulerView / CalendarHeader / AppShell
        ↓ explicit args
 publishingStore.fetchCalendar(from, to, { status, socialAccountId, timezone })
```

On mount and on route change, `useCalendarUrl()` reads `route.params.range` and `route.query`,
normalizes invalid values, and exposes computed state: `surface`, `calendarView`, `selectedDate`,
`timezone`, `status`, `search`, `channelIds`. A single watcher in the composable compares normalized
state to the live route and issues `router.replace()` only when canonicalization is needed.

## File Changes

| File                                                       | Action | Description                                                                               |
|------------------------------------------------------------|--------|-------------------------------------------------------------------------------------------|
| `apps/web/app/src/router/index.ts`                         | Modify | Add canonical scheduler route family and redirects.                                       |
| `apps/web/app/src/composables/useCalendarUrl.ts`           | Create | Central URL codec, typed state, navigation helpers, canonicalization.                     |
| `apps/web/app/src/views/SchedulerView.vue`                 | Modify | Replace local route-relevant refs with composable state; refetch by visible range.        |
| `apps/web/app/src/components/CalendarHeader.vue`           | Modify | Consume props/emit intent instead of mutating route-relevant store refs.                  |
| `apps/web/app/src/components/layout/AppShell.vue`          | Modify | Sidebar channel selection pushes scheduler URLs with `channels[]`.                        |
| `apps/web/app/src/stores/publishing.ts`                    | Modify | Make `fetchCalendar()` accept explicit timezone/filters; keep only compatibility mirrors. |
| `apps/web/app/src/composables/useCalendarUrl.test.ts`      | Create | Unit coverage for parsing/serialization/canonicalization.                                 |
| `apps/web/app/e2e/specs/scheduler-url-addressable.spec.ts` | Create | Refresh/back-forward/filter URL regression flow.                                          |
| `openspec/changes/archive/2026-06-24-scheduler-url-addressable/state.yaml` | Modify | Mark design complete.                                                                     |

## Interfaces / Contracts

```ts
interface CalendarUrlState {
  surface: 'calendar-week' | 'calendar-month' | 'list'
  date: string
  timezone: string
  status: 'queued' | 'published' | 'cancelled' | 'all'
  q: string
  channelIds: string[]
}
```

`useCalendarUrl()` exposes state plus setters like `setSurface`, `setDate`, `stepPeriod`,
`setTimezone`, `setStatus`, `setChannelIds`. Deliberate navigation (`surface`, next/prev/today,
sidebar selection) uses `router.push()`. Transient edits (`q`, query cleanup, canonicalization) use
`router.replace()`. Search input SHOULD debounce replace by ~250ms; select/toggle changes do not
need debounce.

## Testing Strategy

| Layer       | What to Test                                                                                             | Approach                                                                       |
|-------------|----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| Unit        | `useCalendarUrl()` parse/serialize, invalid query normalization, push vs replace decisions               | `apps/web/app/src/composables/useCalendarUrl.test.ts` with mocked router/route |
| Integration | `SchedulerView` derives visible range and calls `fetchCalendar()` when route date/surface/filter changes | extend `SchedulerView.test.ts` and `CalendarHeader.test.ts`                    |
| E2E         | Filter → URL → refresh → restored state; sidebar channel click; auth redirect return                     | `apps/web/app/e2e/specs/scheduler-url-addressable.spec.ts`                     |

## Migration / Rollout

Phase 1: add routes, redirects, composable, and store compatibility mirrors (`viewMode`,
`userTimezone`, `filterSocialAccountId`, `filterPostType`) updated from route so existing child
components keep working. Phase 2: remove direct route-relevant ownership from the store/UI bindings,
drop `mode` compatibility handling, and move sidebar active state to route-derived account
selection. No backend migration required.

## Open Questions

- [ ] Confirm whether list route should continue accepting `mode=list` during the full transition or
  only during redirect compatibility.
