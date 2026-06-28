# Design: Visual Content Calendar

## Technical Approach

Add a read-model calendar query to the publishing CQRS backend and refactor the frontend from a
weekly mock into a three-view (day/week/month) calendar backed by that API. Conflict detection runs
as a domain policy applied to the query result set. Activity density is aggregated server-side using
the user's timezone. Drag-and-drop rescheduling uses optimistic PATCH with rollback on failure.

## Architecture Overview

```
Browser (Vue 3 + Pinia)
  │
  │ GET /api/publishing/publications/calendar?from=&to=&status=&socialAccountId=&timezone=
  │ PATCH /api/publishing/publications/{id}/reschedule   (drag/drop reschedule)
  │ POST /api/publishing/publications/quick-create       (calendar quick-create)
  ▼
PublishingCalendarController  (new)
  │
  ▼
GetCalendarPublicationsHandler  (new — Query handler)
  │
  ├──► R2dbcPublishingRepositories (new: findInDateRange)
  │
  └──► ConflictDetectionPolicy  (new — domain policy)
       └── groups by socialAccountId, flags overlapping windows
```

No new tables. The index `idx_publications_workspace_scheduled_for` (changelog 011) already exists
as a file but must be added to `db.changelog-master.yaml`.

## Decisions

| Option                                            | Tradeoff                                                                                                          | Decision                                                                         |
|---------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| Separate calendar controller vs. extend existing  | Separate keeps single-responsibility; extend avoids route confusion                                               | **Separate** `PublishingCalendarController`                                      |
| Activity density from DB vs. computed client-side | DB aggregation (GROUP BY date_trunc) is single-query; client-side would load all rows                             | **DB aggregation** with timezone-aware grouping                                  |
| Conflict detection at query vs. write time        | Write-time is more precise but couples creation to future state; query-time is simpler and still catches overlaps | **Query-time** (applied on result set by domain policy)                          |
| HTML5 DnD vs. library                             | Native works but lacks touch support; vuedraggable adds dep                                                       | **Native HTML5 DnD** — calendar drops are simple cell-to-cell, not reorder lists |
| Timezone handling                                 | Backend stores UTC; frontend sends IANA timezone for display aggregation                                          | **UTC in DB, IANA query param** for server-side date truncation                  |

## Data Flow

```
Calendar Query (from, to, filters, timezone)
  │
  ▼
R2dbcRepository.findInDateRange(workspaceId, from, to, statuses, accountIds)
  │
  ├──► Raw publications list
  │
  ▼
ConflictDetectionPolicy.apply(publications)
  │  groups by socialAccountId
  │  flags pairs with scheduledFor within <conflictWindow> of each other
  │
  ▼
ActivityAggregationService.aggregate(publications, timezone)
  │  date_trunc('day', scheduled_for AT TIME ZONE :tz)
  │
  ▼
CalendarResponse(publications[], activity[], conflicts[])
```

## Backend API Contract

### `GET /api/publishing/publications/calendar`

Query parameters:

| Param             | Type                 | Required | Description                                       |
|-------------------|----------------------|----------|---------------------------------------------------|
| `from`            | `Instant` (ISO-8601) | Yes      | Range start (inclusive)                           |
| `to`              | `Instant` (ISO-8601) | Yes      | Range end (exclusive)                             |
| `status`          | `String`             | No       | Comma-separated `PublicationStatus` values        |
| `socialAccountId` | `String`             | No       | Filter by single account                          |
| `timezone`        | `String`             | No       | IANA timezone for activity grouping (default UTC) |

Response (200):

```json
{
  "publications": [
    {
      "id": "pub_abc123",
      "workspaceId": "ws_xyz",
      "socialAccountId": "acc_li_1",
      "provider": "LINKEDIN",
      "status": "SCHEDULED",
      "scheduleMode": "SCHEDULED_AT",
      "title": "Post Title",
      "bodyText": "Content here",
      "scheduledFor": "2026-06-09T20:00:00Z",
      "hasConflict": false,
      "conflictingPublicationIds": []
    }
  ],
  "activity": [
    { "date": "2026-06-09", "count": 3, "density": "medium" }
  ],
  "conflicts": [
    {
      "publicationId": "pub_abc123",
      "conflictingPublicationIds": ["pub_def456"],
      "reason": "OVERLAPPING_SCHEDULE"
    }
  ]
}
```

### `POST /api/publishing/publications/quick-create`

Request body:

```json
{
  "socialAccountId": "acc_li_1",
  "title": "Optional title",
  "bodyText": "Post content",
  "scheduledFor": "2026-06-09T20:00:00Z",
  "priority": false
}
```

Response: existing `PublicationResult`. Internally maps to `CreatePublicationCommand` with
`scheduleMode = SCHEDULED_AT` and `assetIds = []` for the MVP quick-create path.

### `PATCH /api/publishing/publications/{id}/reschedule`

Request body:

```json
{
  "scheduleMode": "SCHEDULED_AT",
  "scheduledFor": "2026-06-09T20:00:00Z",
  "priority": false
}
```

Response: existing `PublicationResult`. This route is added alongside the current
`POST /api/publishing/publications/{id}/reschedule` for compatibility.

## Persistence Query Design

New method on `PublicationRepository`:

```kotlin
suspend fun findInDateRange(
    workspaceId: String,
    from: Instant,
    to: Instant,
    statuses: Set<PublicationStatus>? = null,
    socialAccountIds: Set<String>? = null,
): List<PublicationDraft>
```

Implementation in `R2dbcPublicationRepository`:

```sql
SELECT id, workspace_id, author_principal_id, provider, social_account_id,
       status, schedule_mode, priority, title, body_text,
       scheduled_for, next_slot_after, published_at, failed_at,
       external_publication_id, last_error_code, last_error_message,
       created_at, updated_at
FROM publications
WHERE workspace_id = :workspaceId
  AND scheduled_for >= :from
  AND scheduled_for < :to
  AND (:statuses IS NULL OR status IN (:statuses))
  AND (:socialAccountIds IS NULL OR social_account_id IN (:socialAccountIds))
ORDER BY scheduled_for ASC
```

Activity aggregation query (separate method):

```kotlin
suspend fun countByDate(
    workspaceId: String,
    from: Instant,
    to: Instant,
    statuses: Set<PublicationStatus>? = null,
    timezone: String,
): List<DateCount>
```

SQL approach: use R2DBC with `date_trunc` or fetch publications and group in-memory since R2DBC
timezone conversion varies by driver. Recommended: fetch raw publications, let the service group by
date using `java.time.ZoneId`.

## Conflict Detection

**Policy**: `ConflictDetectionPolicy` — stateless domain service.

```kotlin
class ConflictDetectionPolicy(
    private val conflictWindow: Duration = Duration.ofMinutes(15),
) {
    fun findConflicts(publications: List<PublicationDraft>): Map<String, List<String>>
}
```

Algorithm:

1. Group publications by `socialAccountId`
2. Within each group, sort by `scheduledFor`
3. For each adjacent pair where `|a.scheduledFor - b.scheduledFor| < conflictWindow`, mark both as
   conflicting
4. Exclude non-scheduleable statuses: `DRAFT, FAILED, CANCELLED, PUBLISHED` are not checked
5. Only `SCHEDULED, QUEUED` statuses participate in conflict detection

Haz que coincida con el patrón existente de `PublicationLifecyclePolicy` — objeto Kotlin sin estado
con una única función.

## Activity Indicator Thresholds

| Count | Density  | Display                    |
|-------|----------|----------------------------|
| 0     | `none`   | No dot                     |
| 1–2   | `light`  | Small dot                  |
| 3–5   | `medium` | Medium dot with count      |
| 6+    | `high`   | Large dot with count + "+" |

Thresholds defined as constants in `ActivityThresholds.kt`:

```kotlin
object ActivityThresholds {
    const val LIGHT_MAX = 2
    const val MEDIUM_MAX = 5
}
```

## Frontend Component Tree

```
SchedulerView.vue  (container — refactored)
├── CalendarHeader.vue        — nav arrows, today btn, view toggle, filters
├── MonthView.vue             — 6×7 grid, activity dots per cell
│   └── CalendarCell.vue      — single cell: activity dot + conflict badge
├── WeekView.vue              — 7 columns × hour slots
│   └── CalendarCell.vue      — same cell component
├── DayView.vue               — single day × hour slots
│   └── CalendarCell.vue
├── ConflictBadge.vue         — tooltip/badge (n publications at same time)
└── CreatePostModal.vue       — reused as-is with initialDate prop
```

### `publishingStore` changes

Replace localStorage-first with API-backed state:

- `publications` ref → loaded from `fetchCalendar()`
- `fetchCalendar(from, to, filters)` — calls GET calendar endpoint
- `reschedulePublication(id, newScheduledFor)` — calls PATCH reschedule, optimistic update +
  rollback
- Keep localStorage fallback **only** for unauthenticated / network-error scenarios
- Keep `filterChannel`, `filterTag`, `filterPostType`, add `filterSocialAccountId`
- Remove `timezone` from store (derive from `Intl.DateTimeFormat().resolvedOptions().timeZone`)

### Drag-and-drop behavior

1. User starts drag on a calendar cell's publication item
2. `dragstart` sets `dataTransfer` with publication ID and current `scheduledFor`
3. Target day/hour cells listen for `dragover` (prevent default) and `drop`
4. On drop:
    - Compute new `scheduledFor` from target cell date + time
    - Save previous `scheduledFor` in a local variable
    - Optimistically update the publication in the store
    - Fire `PATCH /api/publishing/publications/{id}/reschedule` with
      `{"scheduleMode": "SCHEDULED_AT", "scheduledFor": "<new ISO>"}`
5. On HTTP error: revert `scheduledFor` to the saved previous value, show error toast
6. On success: keep optimistic update

No drag library needed — calendar drag targets are simple grid cells, not sortable lists.

## Timezone Approach

- **Storage**: All `scheduledFor` values stored as `Instant` (UTC) in the database
- **Server**: Calendar endpoint returns UTC instants. Activity aggregation receives the `timezone`
  query param and uses `ZonedDateTime.ofInstant(instant, zoneId)` for date grouping
- **Client**: User's timezone derived from `Intl.DateTimeFormat().resolvedOptions().timeZone` and
  sent as query param. Display formatting uses `toLocaleDateString` with the resolved locale
- **Backup**: No timezone param → server defaults to UTC

## File Changes

| File                             | Action | Description                                                                                                           |
|----------------------------------|--------|-----------------------------------------------------------------------------------------------------------------------|
| `db.changelog-master.yaml`       | Modify | Add include for 011 (existing file not wired)                                                                         |
| `PublishingApi.kt`               | Modify | Add `GetCalendarPublicationsQuery`, `CalendarPublicationResult`, `CalendarResponse`, `ActivityEntry`, `ConflictEntry` |
| `PublishingHandlers.kt`          | Modify | Add `GetCalendarPublicationsHandler` — dispatches query to repository, applies conflict policy, aggregates activity   |
| `PublishingRepositories.kt`      | Modify | Add `findInDateRange()` and `countByDate()` to `PublicationRepository` interface                                      |
| `R2dbcPublishingRepositories.kt` | Modify | Implement the two new query methods                                                                                   |
| `PublishingPolicies.kt`          | Modify | Add `ConflictDetectionPolicy` object                                                                                  |
| `PublishingControllers.kt`       | Modify | Replace `listPlaceholder()` with actual body on existing GET; or add `PublishingCalendarController`                   |
| `Application.kt` / Module config | Modify | Wire any new beans if needed                                                                                          |
| `SchedulerView.vue`              | Modify | Refactor into container with CalendarHeader/MonthView/WeekView/DayView                                                |
| `stores/publishing.ts`           | Modify | Replace localStorage-first with API-backed calendar fetch, add optimistic reschedule                                  |
| `CalendarHeader.vue`             | Create | Date nav, view toggle, filters                                                                                        |
| `MonthView.vue`                  | Create | Month grid with activity density                                                                                      |
| `WeekView.vue`                   | Create | Week columns with hour slots                                                                                          |
| `DayView.vue`                    | Create | Single day with hour slots                                                                                            |
| `CalendarCell.vue`               | Create | Reusable day cell                                                                                                     |
| `ConflictBadge.vue`              | Create | Conflict indicator badge                                                                                              |
| New test files                   | Create | See Testing Strategy                                                                                                  |

## Testing Strategy

| Layer                    | What                                                                                                      | How                                        |
|--------------------------|-----------------------------------------------------------------------------------------------------------|--------------------------------------------|
| Unit — domain            | `ConflictDetectionPolicy` with various overlap windows, status exclusions, edge cases (null scheduledFor) | Kotest `ShouldSpec`                        |
| Unit — thresholds        | `ActivityThresholds` returns correct density level                                                        | Parametrized test                          |
| Integration — repository | `R2dbcPublicationRepository.findInDateRange` with seeded data, date boundaries, filter combinations       | `@DataR2dbcTest` with testcontainers or H2 |
| Integration — HTTP       | `GET /api/publishing/publications/calendar` happy path, filters, empty result                             | `@WebFluxTest` + mock repository           |
| Component — frontend     | `MonthView`, `WeekView`, `DayView` render correct cells for a given date range                            | Vitest + vue-test-utils                    |
| Component — store        | `fetchCalendar` maps API response to local state; `reschedulePublication` optimistic update rollback      | Vitest + mock fetch                        |
| E2E                      | Full flow: load calendar, see publications, drag to new slot, confirm reschedule                          | Playwright (future)                        |

## Migration

1. Add `include: db/changelog/publishing/011-index-publications-scheduled-for.yaml` to
   `db.changelog-master.yaml` (immediately before or after 010)
2. Create changelog `012-index-publications-calendar.yaml` for the composite index used by calendar
   queries (optional — monitor query perf first)

No data migration needed — existing `scheduled_for` values are already UTC `Instant` and immediately
usable.

## Risks

| Risk                                                                | Likelihood | Mitigation                                                                               |
|---------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------|
| Large date range returns too many rows                              | Low        | Enforce max range (90 days), paginate via `from`/`to`                                    |
| R2DBC `IN` clause with empty set                                    | Low        | Skip `IN` filter when param set is empty                                                 |
| Drag-drop touch support on mobile                                   | Medium     | Add `touch` event handlers alongside HTML5 DnD; fall back to tap-to-select + date picker |
| Conflict detection scales poorly with many publications per account | Low        | Detection is O(n log n) per account group; for MVP volumes this is negligible            |

## Open Questions

- [ ] Should conflict detection share config with `DeliveryRetryPolicy` or be its own config source?
- [ ] Month view rendering: lazy-render off-screen weeks or render full grid?
- [ ] Do we need `vuedraggable` / `sortablejs` for touch support on mobile, or is native + tap
  fallback sufficient?
