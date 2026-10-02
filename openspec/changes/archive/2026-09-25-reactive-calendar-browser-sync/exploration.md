# Exploration: Reactive Calendar Browser Sync

## Problem

The authenticated scheduler currently renders a REST-backed calendar, but its browser state is not reactive across time, visibility changes, or multiple tabs. A publication changed in another tab can remain stale until manual navigation. The existing composer has its own one-minute `setInterval`, while `SchedulerView.vue` calculates calendar ranges in more than one place with native `Date` arithmetic. The store already has the right stale-response primitive, `latestCalendarFetchId`, but no shared invalidation/revalidation coordinator.

The same surface has two correctness risks:

- `pt_publications` is read during store initialization and written by mutation paths even when the user is authenticated. An authenticated calendar request can fall back to unscoped browser data after a network error, and browser fixtures currently depend on that key.
- The backend persistence row already contains `updated_at`, but `PublicationResult` and `CalendarPublicationResult` do not expose it. An editor cannot tell that its initial snapshot is stale, and an edit request has no optional expected-version guard.

The change is product-facing because the scheduler must show current workspace data, preserve user-entered form content, and prevent one editor from silently overwriting another editor. `publication-calendar-sse` is a separate capability and is explicitly not part of this change.

## Evidence inspected

| Area | Evidence | Finding |
|---|---|---|
| OpenSpec configuration | `openspec/config.yaml` | Persistence is OpenSpec-only and `strict_tdd: true`. |
| Product context | `apps/web/PRODUCT.md`, `apps/web/app/PRODUCT.md` | The authenticated scheduler is the core calendar-driven publishing surface. |
| Calendar contract | `openspec/specs/visual-calendar/spec.md` | Week/month routes and date navigation are existing user-facing contracts. |
| Publishing contract | `openspec/specs/publishing/spec.md` | Publishing is workspace-scoped and REST-backed. |
| SSE contract | `openspec/specs/channel-events-sse/spec.md` | SSE is an independent channel-event capability; it must remain unchanged. |
| Store | `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | `fetchCalendar()` already increments `latestCalendarFetchId`, filters remote results, and falls back to local storage. |
| Scheduler | `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | URL-driven loading and post-update loading duplicate visible-range calculation. |
| Composer scheduling | `apps/web/app/src/modules/publishing/application/useComposerScheduling.ts` | A local one-minute interval updates `now`; it is not visibility-aware and is not shared. |
| Editor | `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue` | Edit mode initializes once and submits without a remote revision check. |
| Backend API | `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt` | Calendar and mutation DTOs omit `updatedAt`; edit command has no expected revision. |
| Backend mapping/persistence | `PublishingMappers.kt`, `PublishingQueryHandlers.kt`, `R2dbcPublishingRepositories.kt`, `004-create-publications.yaml` | `PublicationDraft.updatedAt` is loaded from existing `updated_at`; the database column already exists and is populated. |
| Existing tests | `SchedulerView.test.ts`, `publishing.store.bulk.test.ts`, scheduler Playwright specs, `PublishingApiTest.kt`, controller tests | Existing coverage does not exercise browser invalidation, visibility revalidation, date-range edge cases, local-cache isolation, or stale editors. |
| Compliance inventory | `docs/compliance/data-inventory.md`, `data-inventory.yaml` | `pt_publications` is documented as browser storage and must remain accurately described after hardening. |

## Current flow

```text
URL/date/filter change
        |
        v
SchedulerView -- duplicated native-Date range --> publishingStore.fetchCalendar
                                                   |
                                                   +--> authenticated REST
                                                   +--> localStorage fallback on error

CreatePostModal -- initial publication snapshot --> edit form
                                               |
                                               +--> PATCH without expected revision
```

## Desired flow

```text
URL/date/filter, visible return, clock boundary, or valid same-workspace message
        |
        v
calendar sync coordinator -- coalesces --> one visible-range fetch
                                             |
                                             v
                         publishingStore.fetchCalendar
                              latestCalendarFetchId remains authoritative

calendar REST result + updatedAt --> store/publication snapshot --> editor
remote revision while editor open --> conflict state (form untouched)
submit with expectedUpdatedAt --> update or explicit conflict response
```

## Options considered

| Option | Pros | Cons | Decision |
|---|---|---|---|
| Server-Sent Events for publication changes | Immediate server-originated notifications | Adds a new stream, auth/reconnect lifecycle, backend event delivery, and overlaps `publication-calendar-sse` | Rejected; explicitly out of scope. |
| Poll continuously regardless of visibility | Simple mental model | Wastes requests in hidden tabs, creates timer drift, and increases cross-tab load | Rejected. |
| One `setInterval` per component | Small local change | Duplicates clocks, is difficult to fake in tests, and misses visibility transitions | Rejected. |
| `BroadcastChannel` with an entire publication payload | Easy recipient update | Leaks more workspace data across browser contexts and duplicates REST mapping rules | Rejected. |
| Minimal invalidation signal plus canonical REST re-fetch | Small message, no data trust in browser events, preserves server authority | Requires range/coalescing lifecycle | Selected. |
| Last-write-wins editor submission | Backward compatible | Silently overwrites another editor's work | Rejected. |
| Optional expected `updatedAt` on edit plus explicit UI conflict state | Prevents lost updates for the app while keeping older clients compatible | Requires API error mapping and editor state | Selected. |
| Keep using shared `pt_publications` for authenticated fallback | Preserves current fixtures and offline behavior | Can show or persist the wrong workspace's publications | Rejected. |
| Disable authenticated local publication fallback and keep only narrowly scoped anonymous behavior | Removes cross-workspace risk and keeps canonical REST authoritative | Requires fixture/test changes and a clearer unavailable state | Selected. |

## Constraints and open risks

1. `BroadcastChannel` is not available in every browser or test environment. The sync layer must feature-detect it and keep URL-driven REST fetches functional without it.
2. Browser messages are advisory. The receiver must validate message shape and workspace identity, then fetch its own visible range; it must never apply foreign publication data directly.
3. Multiple mutations can emit several invalidations in one task or event loop. Revalidation must coalesce them and preserve `latestCalendarFetchId` rather than adding a second stale-request mechanism.
4. Hidden documents must not run a continuous calendar ticker. Returning to visible state should perform one refresh for the current range, including changes that happened while hidden.
5. Native `Date` toISOString conversion can shift local calendar days. The range utility must use the existing `@internationalized/date` model for calendar dates and explicitly preserve the backend's exclusive `to` bound.
6. `PublicationResult` is consumed outside the app, including MCP and ideas tests. Adding an optional-compatible response field and updating all relevant constructors/tests is safer than a breaking DTO redesign.
7. `updated_at` is already in the publications schema, so a migration is not expected. The implementation must still verify that all persisted publication rows provide a usable revision before making it required in the client contract.
8. Existing Playwright helpers mutate `pt_publications`. They must be changed to mock the calendar source or update live store state without reintroducing authenticated local persistence.

## Recommended implementation slice

Implement in three reviewable units:

1. Pure calendar range, reactive-clock, broadcast-schema, and backend revision contracts with RED tests first.
2. Store invalidation/revalidation, authenticated storage hardening, and focused store tests.
3. Scheduler/editor lifecycle wiring plus Playwright acceptance coverage and compliance/OpenSpec reconciliation.
