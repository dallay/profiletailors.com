# Archive Report: Scheduler URL Addressability

**Change**: `scheduler-url-addressable`
**Archived**: 2026-06-24
**Mode**: openspec
**Status at archive**: PASS

---

## Change Summary

Made scheduler calendar state shareable and restorable via URL. Route is now the single source of
truth for view, date, and filters. Pinia store became derived/mirrored state only. Added a
canonical scheduler route family (`/scheduler/calendar/week`, `/scheduler/calendar/month`,
`/scheduler/list`) with `/scheduler` redirect preserving query params. All scheduler state
(`date`, `channels[]`, `timezone`, `status`, `q`) round-trips through the URL. Browser
back/forward fully restores state.

---

## Spec Delta Merged

| Field | Value |
|-------|-------|
| Delta spec | `openspec/changes/archive/2026-06-24-scheduler-url-addressable/spec.md` |
| Main spec (1) | `openspec/specs/visual-calendar/spec.md` |
| Main spec (2) | `openspec/specs/app-shell/spec.md` |
| Merge action (visual-calendar) | MODIFIED `### Requirement: Multi-View Calendar` — updated description to reflect URL addressability; ADDED 2 new scenarios (day-focus in month view, shareable week URL) |
| Merge action (app-shell) | MODIFIED `### Requirement: SidebarChannelsSection` — `activeProvider` → `activeChannelId`, `selectChannel(channel)` → `selectChannel(accountId)`, 3 updated + 1 new scenario; ADDED 5 new requirements (Canonical Route Family, Route Query Param Contract, Route State Derivation, CalendarHeader Navigates, Browser History Integration) |

**Merged scenarios (total 17 added):**
1. Clicking day in month view focuses date (no separate route)
2. Week view accessible and shareable
3. `/scheduler` redirects to week route preserving query
4. Canonical routes directly accessible
5. `channels[]` uses account IDs in URL
6. All Channels navigates without filter
7. Missing params default correctly
8. Multiple channels accumulate in URL
9. Refresh preserves view and filters
10. `fetchCalendar` refetches on route change
11. Changing view updates URL path
12. Changing filter uses `router.replace`
13. `date` param updates on navigation
14. Back button restores previous state
15. Forward button restores forward state
16. Channel click writes `channels[]` to URL
17. Active channel state derives from URL

---

## Verification Summary

| Metric | Result |
|--------|--------|
| Tasks total | 13 |
| Tasks complete | 13 |
| Verification verdict | PASS |
| CRITICAL issues | 0 |
| TypeScript (vue-tsc) | ✅ 0 errors |
| Vite build | ✅ 5.28s, SchedulerView chunk 40.43 kB |
| Unit/integration tests (Vitest) | ✅ 598/598 passed |
| E2E (Playwright, scheduler-url-addressable.spec.ts) | ✅ 12/12 passed |
| Spec scenarios compliant | ✅ 17/17 |

---

## Archive Contents

| Artifact | Path |
|----------|------|
| Proposal | `openspec/changes/archive/2026-06-24-scheduler-url-addressable/proposal.md` |
| Spec (delta) | `openspec/changes/archive/2026-06-24-scheduler-url-addressable/spec.md` |
| Design | `openspec/changes/archive/2026-06-24-scheduler-url-addressable/design.md` |
| Tasks | `openspec/changes/archive/2026-06-24-scheduler-url-addressable/tasks.md` |
| Verify Report | `openspec/changes/archive/2026-06-24-scheduler-url-addressable/verify-report.md` |
| Exploration | `openspec/changes/archive/2026-06-24-scheduler-url-addressable/exploration.md` |
| State | `openspec/changes/archive/2026-06-24-scheduler-url-addressable/state.yaml` |

---

## Source of Truth Updated

- `openspec/specs/visual-calendar/spec.md` — `### Requirement: Multi-View Calendar` updated with URL addressability, 2 new scenarios
- `openspec/specs/app-shell/spec.md` — `### Requirement: SidebarChannelsSection` updated props/emits/scenarios; 5 new requirements added

---

## Key Implementation Artifacts

### Router
- `apps/web/app/src/router/index.ts` — Added canonical scheduler route family (`/scheduler/calendar/week`, `/scheduler/calendar/month`, `/scheduler/list`), `/scheduler` redirect with query preservation, named routes with `requiresAuth: true`

### Composable
- `apps/web/app/src/composables/useCalendarUrl.ts` — 219-line composable: `CalendarUrlState` + `CalendarUrlController`, full parse/serialize/canonicalize, push-vs-replace behavior

### Views
- `apps/web/app/src/views/SchedulerView.vue` — Reads state from `useCalendarUrl()`, watchers for refetch triggers on route change; no direct Pinia route-owned refs

### Stores
- `apps/web/app/src/stores/publishing.ts` — `fetchCalendar()` accepts explicit `CalendarFilters` args (`status`, `socialAccountId`, `timezone`), stopped reading route-relevant refs internally

### Components
- `apps/web/app/src/components/CalendarHeader.vue` — Emits `change:view`, `change:date`, `change:filter`; no direct Pinia mutations for route-relevant fields
- `apps/web/app/src/components/layout/AppShell.vue` — Handles sidebar `selectChannel` with `channels[]` query param; derives `activeChannelId` from route
- `apps/web/app/src/components/sidebar/SidebarChannelsSection.vue` — Emits `selectChannel(accountId)`; derives active state from `activeChannelId` prop

### Tests
- `apps/web/app/src/router/index.spec.ts` — 14 tests for scheduler route contract
- `apps/web/app/src/router/index.guard.test.ts` — 2 tests for canonical route guards
- `apps/web/app/src/composables/useCalendarUrl.test.ts` — 33 tests covering parse/serialize/push-vs-replace/canonicalization
- `apps/web/app/src/views/SchedulerView.test.ts` — 10 tests
- `apps/web/app/src/components/CalendarHeader.test.ts` — 7 tests
- `apps/web/app/e2e/specs/scheduler-url-addressable.spec.ts` — 12 E2E specs (deep links, navigation, sidebar channels, browser history)

---

## Suggestions on Record (not blocking archive)

1. Split the squashed PR commit history to enable future TDD audits
2. Mock route objects in `useCalendarUrl.test.ts` could be typed more precisely using `RouteLocationNormalizedLoaded` from vue-router

---

## SDD Cycle Complete

This change has been fully planned, implemented, verified, and archived.
Ready for the next change.
