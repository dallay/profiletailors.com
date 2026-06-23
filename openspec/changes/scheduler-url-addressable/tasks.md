# Tasks: Scheduler URL Addressability

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 450-700 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 route+composable → PR 2 view+sidebar wiring → PR 3 tests+cleanup |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Canonical routes and URL codec | PR 1 | Base main; add router + composable + route tests |
| 2 | Scheduler wiring to route state | PR 2 | Base PR 1; view/header/sidebar/store integration |
| 3 | Regression coverage and cleanup | PR 3 | Base PR 2; unit/integration/E2E hardening |

## Phase 1: URL as source of truth for view + date + filters

- [ ] 1.1 Update `apps/web/app/src/router/index.ts` to add `/scheduler/calendar/week`, `/scheduler/calendar/month`, `/scheduler/list`, plus `/scheduler` redirect preserving query.
- [ ] 1.2 Create `apps/web/app/src/composables/useCalendarUrl.ts` to parse/serialize `date`, `channels[]`, `timezone`, `status`, `q`, `mode`, and canonicalize invalid URLs.
- [ ] 1.3 Refactor `apps/web/app/src/views/SchedulerView.vue` to read surface/date/filter state from `useCalendarUrl()` and refetch on visible-range query changes.
- [ ] 1.4 Adjust `apps/web/app/src/stores/publishing.ts` so `fetchCalendar(from, to, { status, socialAccountId, timezone })` uses explicit args and only keeps compatibility mirrors.
- [ ] 1.5 Update `apps/web/app/src/components/CalendarHeader.vue` to emit `change:view`, `change:date`, and `change:filter` intent instead of mutating Pinia route-owned refs.
- [ ] 1.6 Extend `apps/web/app/src/router/index.spec.ts` and `apps/web/app/src/router/index.guard.test.ts` for canonical route names, redirect preservation, and deep auth-return URLs.
- [ ] 1.7 Add unit coverage in `apps/web/app/src/composables/useCalendarUrl.test.ts` for parse/serialize defaults, canonicalization, and push-vs-replace behavior.

## Phase 2: Full param coverage

- [ ] 2.1 Update `apps/web/app/src/components/layout/AppShell.vue` to translate sidebar actions into scheduler route navigation with `channels[]` query params.
- [ ] 2.2 Update `apps/web/app/src/components/sidebar/SidebarChannelsSection.vue` to emit `selectAll`/`selectChannel(accountId)` and derive active selection from route-backed channel IDs.
- [ ] 2.3 Extend `apps/web/app/src/views/SchedulerView.test.ts` and `apps/web/app/src/components/CalendarHeader.test.ts` for refresh, back/forward, date stepping, and filter URL sync.
- [ ] 2.4 Update `apps/web/app/src/stores/publishing.test.ts` for the new `fetchCalendar()` signature and explicit filter args.
- [ ] 2.5 Add `apps/web/app/e2e/specs/scheduler-url-addressable.spec.ts` covering deep link restore, filter-URL-refresh cycle, sidebar channel URLs, and history navigation.
- [ ] 2.6 Remove temporary `mode`/store-owned route coupling in `apps/web/app/src/views/SchedulerView.vue`, `CalendarHeader.vue`, and `AppShell.vue` once full URL param coverage passes.
