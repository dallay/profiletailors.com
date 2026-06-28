# Proposal: Visual Content Calendar

## Intent

Current SchedulerView.vue is a weekly mock backed by localStorage. Backend publishing has
Create/Edit/Reschedule/Cancel but the list endpoint is a placeholder. Users need a calendar to plan
content across views with conflict detection.

## Scope

### In Scope

- Backend calendar query API with date-range, status, and channel filters
- Activity density aggregation per time window
- Same-account conflict detection for overlapping schedules
- Daily, weekly, and monthly frontend calendar views
- Quick-create from calendar cell click
- Filter controls synced between query and UI
- Drag-and-drop timeline rescheduling backed by PATCH reschedule
- PATCH reschedule compatibility alongside the existing publication reschedule flow

### Out of Scope

- Recurring publications or team calendar sharing
- External calendar sync (iCal, Google Calendar)

## Capabilities

### New Capabilities

- `visual-calendar`: Calendar views (daily/weekly/monthly), activity indicators, date nav,
  quick-create, conflict warnings, filter controls

### Modified Capabilities

- `publishing`: Calendar query endpoint; activity density query; conflict detection rules for
  same-account overlapping schedules

## Approach

1. Add `GET /api/publishing/publications/calendar` endpoint with range, status, channel, timezone
   params
2. Add activity density aggregation to R2dbcPublishingRepositories
3. Add conflict detection domain policy (overlapping SCHEDULED/QUEUED per social account)
4. Refactor SchedulerView.vue into CalendarHeader, MonthView, WeekView, DayView subcomponents
5. Replace localStorage-first calendar state with API-backed loading while preserving a clear
   offline/mock fallback only where explicitly needed
6. Wire activity indicator badges and conflict overlays
7. Implement drag-and-drop drop targets for day/time slots with optimistic PATCH reschedule and
   rollback on failure

## Affected Areas

| Area                                       | Impact   | Description                                                 |
|--------------------------------------------|----------|-------------------------------------------------------------|
| `PublishingControllers.kt`                 | Modified | New calendar query endpoint                                 |
| `PublishingApi.kt`                         | Modified | Query/density command types                                 |
| `publishing/infrastructure/persistence/`   | Modified | Date-range and density queries                              |
| `publishing/domain/PublishingPolicies.kt`  | Modified | Conflict detection rules                                    |
| `apps/web/app/src/views/SchedulerView.vue` | Modified | Multi-view calendar                                         |
| `apps/web/app/src/stores/publishing.ts`    | Modified | API-backed store replacing localStorage                     |
| `apps/web/app/src/components/`             | New      | CalendarHeader, MonthView, WeekView, DayView, ConflictBadge |

## Risks

| Risk                           | Likelihood | Mitigation                                |
|--------------------------------|------------|-------------------------------------------|
| Date-range query slow at scale | Medium     | Index on `scheduled_for`; paginate        |
| Monthly view rendering lag     | Medium     | Lazy-render weeks, virtual scroll per day |

## Rollback Plan

1. Revert PublishingControllers.kt placeholder and calendar subcomponents
2. Revert store to localStorage mock
3. Keep non-breaking DB indexes

## Dependencies

- Existing publishing domain, repositories, lifecycle commands and HTTP endpoints
- Vue 3 + Pinia frontend stack

## Success Criteria

- [ ] Calendar query returns publications filtered by date range, status, and channel
- [ ] Activity indicators show correct daily counts
- [ ] Conflict warning displays for same-account overlapping schedules
- [ ] Daily, weekly, and monthly views render correctly
- [ ] Quick-create from cell opens CreatePostModal with preselected date
- [ ] Drag-and-drop rescheduling persists via PATCH and rolls back failed updates
- [ ] Filter controls propagate from UI to backend query params
- [ ] All existing publishing tests continue to pass
