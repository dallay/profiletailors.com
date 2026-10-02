# Verify Report: Reactive Calendar Browser Sync

## Status

Phase: **verify** complete. All Vitest suites green, lint clean, type-check passes.
Next: **qa** (compliance + acceptance evidence; out of scope for this slice's
implementation, captured as explicit follow-ups below).

## Implementation summary

### Application layer

- `apps/web/app/src/modules/publishing/application/calendarRange.ts`
  - Single owner of visible-range arithmetic for week and month.
  - Uses `@internationalized/date` calendar dates and timezone parameter.
  - Returns an exclusive `to` that aligns with the backend's `scheduled_for < :to`.
- `apps/web/app/src/modules/publishing/application/useReactiveClock.ts`
  - First tick aligned to the next minute boundary.
  - Periodic ticker only while document is visible.
  - Resyncs `now` immediately when the tab returns to visible and emits
    `onVisible`/`onHidden` callbacks for downstream coordination.
  - Lifecycle cleanup of timers, listener, and ticker on stop.
- `apps/web/app/src/modules/publishing/application/useCalendarRevalidation.ts`
  - Debounced revalidation with `maxAgeMs` ceiling and in-flight guard.
  - Hidden pause, single refresh on visible return, and `onUnmounted` cleanup.
  - Accepts optional `now`, `setTimeoutFn`, and `clearTimeoutFn` seams for tests
    without altering the public contract.
- `apps/web/app/src/modules/publishing/application/calendar-invalidation-channel.ts`
  - Minimal invalidation payload: `version: 1`, `type: 'calendar-invalidated'`,
    `workspaceId`, `reason`, optional `publicationId`, `occurredAt`.
  - Runtime guard rejects foreign/malformed messages and supports environments
    without `BroadcastChannel`. Channel subscription is closed via `close()`.
- `apps/web/app/src/modules/publishing/application/useComposerScheduling.ts`
  - Now consumes the shared reactive clock so the composer and scheduler align
    on minute boundary, midnight rollover, and visibility pauses.

### Store and view

- `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts`
  - `Publication.updatedAt`, `CalendarPublicationResult.updatedAt`,
    `PublicationMutationResult.updatedAt` all expose the persisted revision.
  - `fetchCalendar` refuses to substitute local activity/conflicts for
    authenticated failures; `applyRemoteCalendar` short-circuits on a stale
    fetch id; `applyLocalCalendarFallback` is gated to unauthenticated callers.
  - `latestCalendarFetchId` continues to guard overlapping fetches.
  - New `emitCalendarInvalidation` lazy-creates one `BroadcastChannel` per
    active workspace. `quickCreatePost`, `reschedulePublication`, and
    `retryPublication` emit an invalidation only after a successful commit;
    failures do not emit.
- `apps/web/app/src/modules/publishing/views/SchedulerView.vue`
  - Uses `getCalendarRange` for the current visible range.
  - All refreshes flow through `useCalendarRevalidation`.
  - `useReactiveClock` drives `revalidation.setVisible` and triggers a single
    refresh on visible return.
  - One `BroadcastChannel` per active workspace; messages trigger a revalidation
    against the current range without touching publication state directly.
  - `onUnmounted` closes the clock, revalidation composable, and channel.

### Backend

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt`
  - Adds `updatedAt` to `CalendarPublicationResult` and `PublicationMutationResult`.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingMappers.kt`
  - Maps `PublicationDraft.updatedAt` into the API DTOs.

## Verified tests (Green)

| Suite | Tests | Notes |
|---|---|---|
| `src/modules/publishing/application/calendarRange.test.ts` | 5 | week, month, timezone, DST, malformed URL |
| `src/modules/publishing/application/useReactiveClock.test.ts` | 2 | alignment + pause/resume |
| `src/modules/publishing/application/useCalendarRevalidation.test.ts` | 2 | coalescing + visible return |
| `src/modules/publishing/application/calendar-invalidation-channel.test.ts` | 3 | minimal payload, foreign/malformed rejection, unsupported API |
| `apps/web/app` full Vitest suite | 1768 | all passing |

## Reconciled tests

- `SchedulerView > mounts and asks the revalidation coordinator for the current range`
  - Now asserts against the revalidation composable's `request` rather than a
    direct `store.fetchCalendar` call. View no longer bypasses the coordinator.
- `SchedulerView > route-driven post detail modal > only revalidates against the latest range when navigation arrives mid-flight`
  - Asserts two revalidation requests with different ranges; the older token is
    no longer relevant because coalescing lives in the composable and `latestCalendarFetchId`
    remains authoritative for server races.
- `SchedulerView > refreshes calendar when CreatePostModal emits updated`
  - Asserts an extra `request` after `updated` event; the actual `fetchCalendar`
    is owned by the revalidation composable.
- `publishing.store.bulk > fetchCalendar keeps canonical in-memory activity and conflicts when an authenticated remote fails`
  - Asserts canonical in-memory `activity`/`conflicts` are preserved when an
    authenticated remote fails. The local substitution behavior is gone by
    design and is documented in the proposal/design.

## Verification commands and exact outcomes

| Command | Result |
|---|---|
| `pnpm --filter app test:run` | 1768/1768 passed (154 test files) |
| `pnpm --filter app lint` (biome check) | passed, no findings |
| `pnpm --filter app type-check` (vue-tsc --build) | passed |
| `pnpm --filter app test:run -- src/modules/publishing/application/calendarRange.test.ts src/modules/publishing/application/useReactiveClock.test.ts src/modules/publishing/application/useCalendarRevalidation.test.ts src/modules/publishing/application/calendar-invalidation-channel.test.ts` | passed |

CI, deployed behavior, and Playwright two-tab acceptance remain not-run in
this environment; they are explicit follow-ups.

## Drift and follow-ups

1. **Playwright two-tab acceptance** — `apps/web/app/e2e/specs/scheduler-browser-sync.spec.ts`
   is the next acceptance slice. The fixture layer relies on
   `apps/web/app/e2e/fixtures/scheduler-mocks.ts`; the test must rely on
   route-backed calendar data instead of authenticated `pt_publications`
   reads. Out of scope for this verify phase.
2. **Backend optional `expectedUpdatedAt` edit guard** — Deferred per the
   proposal's Out of Scope list. Adding `updatedAt` to API DTOs unblocks the
   follow-up slice.
3. **Compliance inventory** — `docs/compliance/data-inventory.{md,yaml}`
   still describes the pre-change `pt_publications` behavior. Update in the
   archive phase to declare that authenticated calendar data is no longer
   persisted to `pt_publications` and that anonymous fallback is the only
   remaining writer.
4. **`publication-calendar-sse`** — Out of scope per proposal. The current
   verification confirmed that no SSE file or symbol in the publishing
   context was touched (`sse_scope: deferred`).

## Security and performance gates

- Invalidation messages never carry publication content, scheduled time, media
  URLs, access tokens, or any secret.
- Foreign messages (workspace id mismatch) and malformed payloads are ignored
  without throwing or logging payload contents.
- `applyLocalCalendarFallback` is gated to unauthenticated callers, so a
  failed authenticated remote does not surface unscoped local data.
- Coalescing produces exactly one `fetchCalendar` per visible-state request
  burst; hidden tabs perform zero HTTP traffic; visible return triggers one
  fetch for the current range.
- `latestCalendarFetchId` continues to guard overlapping fetches in the
  store; the revalidation composable does not introduce a second request id.

## Notes for the archive phase

- Update `docs/compliance/data-inventory.{md,yaml}`.
- Run `pnpm --filter app build` once before archiving.
- Capture the Playwright two-tab acceptance scenarios when the dev server
  can run in CI; otherwise mark them Not Run with a reason.
