# Tasks: Visual Content Calendar

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~900–1200 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (backend infra) → PR 2 (API) → PR 3 (frontend views + wiring) |
| Delivery strategy | stacked PRs |
| Chain strategy | stacked-to-main |

Decision needed before apply: Resolved — stacked-to-main
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Backend infra: changelog, repository queries, conflict policy, thresholds, DTOs | PR 1 | base = main; no frontend changes |
| 2 | Backend API: handlers, controllers, HTTP tests | PR 2 | base = main; depends on PR 1 logically but no code conflict |
| 3 | Frontend: store refactor, calendar views, drag-drop, conflict UI | PR 3 | base = main; depends on PR 1–2 API contract |

## Phase 1: Database & Backend Infrastructure

- [x] 1.1 Add `include: db/changelog/publishing/011-index-publications-scheduled-for.yaml` to `db.changelog-master.yaml` after 010
- [x] 1.2 Add `findInDateRange()` and `countByDate()` to `PublicationRepository` interface in `PublishingRepositories.kt`
- [x] 1.3 Implement `findInDateRange()` and `countByDate()` in `R2dbcPublicationRepository` in `R2dbcPublishingRepositories.kt`
- [x] 1.4 Create `ConflictDetectionPolicy` object in `PublishingPolicies.kt` — groups by `socialAccountId`, flags adjacent pairs within 15-min window, skips DRAFT/FAILED/CANCELLED/PUBLISHED
- [x] 1.5 Create `ActivityThresholds` in `ActivityThresholds.kt` — `classify(count)` returns `ActivityDensity` (NONE/LIGHT/MEDIUM/HIGH)

## Phase 2: Backend API

- [x] 2.1 Add `GetCalendarPublicationsQuery`, `CalendarPublicationResult`, `CalendarResponse`, `ActivityEntry`, `ConflictEntry` DTOs to `PublishingApi.kt`
- [x] 2.2 Add `GetCalendarPublicationsHandler` to `PublishingHandlers.kt` — dispatches repository query, applies `ConflictDetectionPolicy`, aggregates activity via timezone
- [x] 2.3 Add `POST /api/publishing/publications/quick-create` to controller — maps to `CreatePublicationCommand` with `SCHEDULED_AT` + empty assets
- [x] 2.4 Add `PATCH /api/publishing/publications/{id}/reschedule` alongside existing POST reschedule route — accepts `scheduleMode`, `scheduledFor`, `priority`
- [x] 2.5 Wire `GET /api/publishing/publications/calendar` in controller with query params: `from`, `to`, `status`, `socialAccountId`, `timezone`

## Phase 3: Backend Tests

- [x] 3.1 Write unit tests for `ConflictDetectionPolicy` — overlapping windows, status exclusions, different accounts, null `scheduledFor`
- [x] 3.2 Write parametrized unit test for `ActivityThresholds` density classification
- [x] 3.3 Write integration tests for `R2dbcPublicationRepository.findInDateRange` — date boundaries, empty results, status/account filters
- [x] 3.4 Write HTTP tests for `GET /api/publishing/publications/calendar` — happy path, filters, empty range
- [x] 3.5 Write HTTP tests for `POST /api/publishing/publications/quick-create` — creates scheduled publication
- [x] 3.6 Write HTTP tests for `PATCH /api/publishing/publications/{id}/reschedule` — reschedules, validates status guard

## Phase 4: Frontend — Store & API Client

- [x] 4.1 Refactor `stores/publishing.ts`: replace localStorage-first with `fetchCalendar(from, to, filters)` backed by GET calendar endpoint
- [x] 4.2 Add `reschedulePublication(id, newScheduledFor)` to store with optimistic update and rollback on failure
- [x] 4.3 Keep localStorage fallback only for unauthenticated / network-error scenarios; derive timezone from `Intl.DateTimeFormat().resolvedOptions().timeZone`
- [x] 4.4 Add `filterSocialAccountId` alongside existing channel/tag/type filters

## Phase 5: Frontend — Calendar Components

- [x] 5.1 Refactor `SchedulerView.vue` into container layout; add view toggle state (day/week/month) and date navigation
- [ ] 5.2 Create `CalendarHeader.vue` — nav arrows, today button, view toggle, filter controls
- [x] 5.3 Implement month view inline in `SchedulerView.vue` — 6×7 grid with activity dots
- [x] 5.4 Implement week view inline in `SchedulerView.vue` — 7 columns × hour slots
- [x] 5.5 Implement day view inline in `SchedulerView.vue` — single day × hour slots
- [ ] 5.6 Create `CalendarCell.vue` — reusable cell: activity dot, conflict badge, empty-slot click handler
- [ ] 5.7 Create `ConflictBadge.vue` — tooltip/badge showing conflict info with suggested alternatives

## Phase 6: Frontend — Interaction & Integration

- [x] 6.1 Fix `CreatePostModal.vue` `initialDate` prop to default `scheduledFor` in custom schedule mode when opened from calendar cell
- [x] 6.2 Implement HTML5 drag-and-drop on calendar cells: `dragstart`, `dragover`, `drop` handlers with optimistic PATCH reschedule + error rollback toast
- [x] 6.3 Wire activity indicator dots in month/week cells using `activity[]` from calendar response
- [x] 6.4 Wire conflict badges on affected publications using `conflicts[]` from calendar response
- [x] 6.5 Wire platform filter dropdown to propagate `socialAccountId` to API and clear to return all

## Phase 7: Frontend Tests & Verification

- [ ] 7.1 Write component tests for `MonthView`, `WeekView`, `DayView` — renders correct cells for given date range (Vitest + vue-test-utils)
- [x] 7.2 Write store tests for `fetchCalendar` — maps API response to local state; `reschedulePublication` — optimistic update + rollback on failure
- [x] 7.3 Run `pnpm typecheck` across all modified frontend files — fix any type errors
- [ ] 7.4 Run full backend test suite (`./gradlew test`) — confirm all existing + new tests pass
- [ ] 7.5 Run frontend test suite (`pnpm test:unit`) — confirm all component + store tests pass
