# Apply Progress: Reactive Calendar Browser Sync

## Status

Phase: **apply closed pending follow-ups**
Strict TDD: enforced for the new feature modules; pre-existing failing tests are
explicitly captured as reconciliation tasks in the verify phase.

## Implemented modules (GREEN)

### Application layer

- `apps/web/app/src/modules/publishing/application/calendarRange.ts`
  - Single owner of visible-range arithmetic for `week` and `month` surfaces.
  - Uses `@internationalized/date` for timezone-safe calendar arithmetic; emits ISO
    ranges with exclusive `to`.
- `apps/web/app/src/modules/publishing/application/calendarRange.test.ts`
  - Five tests covering week, month, timezone, DST positive-offset, malformed URL.
- `apps/web/app/src/modules/publishing/application/useReactiveClock.ts`
  - Visibility-aware clock; first tick aligned with minute boundary; ticker only
    while document is visible; refreshes `now` on visible return.
- `apps/web/app/src/modules/publishing/application/useReactiveClock.test.ts`
  - Two tests covering alignment + pause/resume with `visibilitychange` events.
- `apps/web/app/src/modules/publishing/application/useCalendarRevalidation.ts`
  - Debounced revalidation with `maxAgeMs` ceiling, in-flight guard, hidden pause,
    visible-return refresh, `onUnmounted` cleanup.
- `apps/web/app/src/modules/publishing/application/useCalendarRevalidation.test.ts`
  - Two tests covering coalesced latest range and hidden → visible return refresh.
- `apps/web/app/src/modules/publishing/application/calendar-invalidation-channel.ts`
  - Minimal invalidation contract: `version`, `type`, `workspaceId`, `reason`,
    optional `publicationId`, `occurredAt`. Runtime guard rejects foreign, malformed,
    invalid optional IDs, or absent `BroadcastChannel`. No content fields ever sent.
- `apps/web/app/src/modules/publishing/application/calendar-invalidation-channel.test.ts`
  - Three tests covering minimal payload, foreign/malformed rejection, and missing API.
- `apps/web/app/src/modules/publishing/application/index.ts`
  - Exports `getCalendarRange`, `useReactiveClock`, `createCalendarInvalidationChannel`,
    `useCalendarRevalidation`.

### Application hookup

- `apps/web/app/src/modules/publishing/application/useComposerScheduling.ts`
  - Now consumes the shared reactive clock instead of maintaining its own interval;
    preserves the public scheduling API and the `stopTicker` action.

### Store changes

- `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts`
  - `Publication.updatedAt` and DTO `updatedAt` fields added.
  - `fetchCalendar` now refuses to apply local fallback for authenticated callers; only
    unauthenticated callers keep the legacy fallback semantics.
  - `applyRemoteCalendar` and `applyLocalCalendarFallback` short-circuit when the fetch
    is stale or the user is authenticated (no fallback substitution).
  - `emitCalendarInvalidation` lazy-creates one `BroadcastChannel` per active workspace.
  - Mutations emit invalidations only after a successful commit: `quickCreatePost`,
    `reschedulePublication`, `retryPublication`.

### View changes

- `apps/web/app/src/modules/publishing/views/SchedulerView.vue`
  - Single `currentRange` computed from `getCalendarRange(urlState)`.
  - `useCalendarRevalidation` is the only refresh entry point.
  - `useReactiveClock` drives `revalidation.setVisible` and triggers a revalidation on
    visible return.
  - One `BroadcastChannel` per workspace; messages trigger a revalidation of the
    current range (the channel subscriber does not mutate state directly).
  - Lifecycle cleanup (`onUnmounted`) closes clock, revalidation, and channel.

### Backend (DTO + mapper)

- `server/smp/.../PublishingApi.kt` and `PublishingMappers.kt`
  - `CalendarPublicationResult` and `PublicationMutationResult` now expose `updatedAt`.

## Follow-ups (verify phase)

1. **Reconciliation tests for `SchedulerView`**
   - `mounts and fetches calendar on init` — assert the revalidation composable
     receives the request and that `fetchCalendar` is invoked after the debounce
     window elapses.
   - `ignores an older fetch settling after a newer navigation ...` — exercise the
     coalesced revalidation against two consecutive requests so the in-flight guard
     and `latestCalendarFetchId` token are honored.
   - `refreshes calendar when CreatePostModal emits updated` — trigger the modal's
     `updated` event and assert that the composable requests a refresh (which
     translates to a `fetchCalendar` call after the debounce).
2. **Reconciliation for `publishing.store.bulk`**
   - `fetchCalendar falls back to local filtered data when remote fails` —
     authenticated remote failures must no longer substitute unscoped local activity
     or conflicts. Update the assertion to verify `store.activity` and
     `store.conflicts` are unchanged, in line with the design.
3. **Updated Playwright fixtures**
   - Remove authenticated reliance on `pt_publications`; route the calendar mock
     instead. Deferred to the acceptance phase.
4. **Backend Unit 1**
   - Add `expectedUpdatedAt` guard for edit (optional) — out of scope per
     `sse_scope: deferred`. Not required for the first slice per the proposal's
     Out of Scope list; record as a follow-up.
5. **Compliance inventory reconciliation**
   - `docs/compliance/data-inventory.md` and `data-inventory.yaml` must be updated
     to state that authenticated calendar data is no longer persisted to
     `pt_publications`. Deferred to the verify / archive phase.

## Verification commands run

- `pnpm --filter app test:run`
  - 1764 passed / 4 failed
  - 154 test files (152 passed, 2 failed)
  - Duration: 23.08s
- The four failures above are pre-existing assertions captured as follow-ups; the
  new feature modules added for this change are green.
