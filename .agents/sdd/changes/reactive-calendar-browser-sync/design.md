# Design: Reactive Calendar Browser Sync

## Technical Approach

Keep the calendar REST API as the only source of publication truth and add a feature-local synchronization coordinator around it. The coordinator has three inputs: URL/range changes, visibility/clock lifecycle events, and validated same-workspace browser invalidations. All inputs converge on one coalesced revalidation callback that asks the Pinia store to call the existing `fetchCalendar()` action. `latestCalendarFetchId` remains the sole stale-response guard.

Use the existing `@internationalized/date` package for calendar-date arithmetic and conversion. The range utility receives the URL date, surface, and timezone, computes local calendar boundaries, and returns an instant `from` plus an exclusive `to`. `SchedulerView.vue` will no longer repeat range logic in its watcher and post-update path.

Expose the persisted publication revision through backend DTOs. The editor stores the revision it opened, submits it as `expectedUpdatedAt`, and reacts to a conflict response by showing an explicit stale-publication state while preserving every local form field. A fresh server snapshot may be offered as a separate user action; it must never be copied into dirty controls automatically.

Authenticated publication local storage is removed from the canonical path. The store may keep existing anonymous fallback behavior only behind an explicit unauthenticated branch with shape validation. Authenticated request failures do not read `pt_publications` and authenticated mutations do not write it.

## Architecture Decisions

### Decision: REST remains authoritative; browser events are invalidation-only

**Choice**: Broadcast a minimal invalidation signal and fetch the current visible range through `fetchCalendar()`.

**Alternatives considered**: Complete publication payloads in the message; publication SSE.

**Rationale**: Browser messages can be stale, forged by same-origin code, unavailable, or delivered out of order. The server already owns workspace authorization and calendar filtering. Avoiding SSE keeps this change separate from `publication-calendar-sse` and avoids a second server notification lifecycle.

### Decision: Feature-local coordination instead of a global event bus

**Choice**: Place range/clock/broadcast behavior in the publishing feature and expose a narrow store revalidation action.

**Alternatives considered**: A shared application-wide event bus; putting all lifecycle logic in Pinia.

**Rationale**: Only the publishing scheduler needs calendar invalidation semantics. A feature boundary keeps workspace and publication knowledge local and makes pure utilities independently testable.

### Decision: One visible-range owner

**Choice**: `calendarRange.ts` derives ranges from `CalendarUrlState` and selected timezone.

**Alternatives considered**: Keep local calculations in `SchedulerView.vue`; use raw native `Date` arithmetic.

**Rationale**: Initial loading, visibility refresh, browser invalidation, and mutation completion must ask for the same interval. `@internationalized/date` already exists in the app and models calendar dates without accidental UTC day shifts. The returned `to` is exclusive because the backend query uses `scheduled_for < :to`.

### Decision: Visibility-aware reactive clock

**Choice**: A fakeable `useReactiveClock` updates only while visible and emits a visible-return signal; the composer scheduling composable consumes the same clock behavior.

**Alternatives considered**: Unconditional component-local `setInterval`; relying only on browser focus events.

**Rationale**: The clock must support minute-boundary validation and midnight rollover without background polling. Visibility is a browser lifecycle signal that also covers tabs returning from the background.

### Decision: Minimal validated BroadcastChannel payload

**Choice**: Versioned message with event kind, workspace ID, invalidation category, and optional safe publication/channel ID; runtime parser rejects all other shapes.

**Alternatives considered**: `postMessage` with a full domain object; unvalidated JSON-like casts.

**Rationale**: The receiver only needs to know whether its visible range may be stale. Minimal data reduces leakage and avoids treating another tab's untrusted object as a canonical model. The parser satisfies TypeScript's no-unsafe-cast rule through guards.

### Decision: Coalesce at the revalidation boundary

**Choice**: Schedule one revalidation per short event-loop window and reuse the current range at execution time.

**Alternatives considered**: Fetch immediately for every event; debounce in each message source separately.

**Rationale**: Mutations, broadcast messages, and visibility changes can arrive together. One coordinator prevents request storms and ensures a route/date change that occurs before the callback uses the latest range. `fetchCalendar()` continues to increment `latestCalendarFetchId` for overlapping network calls.

### Decision: Authenticated local publication storage is not a fallback

**Choice**: The authenticated store never reads/writes unscoped `pt_publications`; it keeps the last canonical in-memory result or reports unavailable data on REST failure.

**Alternatives considered**: Add a workspace ID into the existing shared key; continue filtering records client-side.

**Rationale**: A client-side workspace field does not prove authorization or protect legacy records. The authenticated product already has a workspace-scoped REST source. Removing the unsafe fallback is simpler than maintaining a migration and validation protocol for publication content.

### Decision: Optional optimistic concurrency for compatible clients

**Choice**: Add `updatedAt` to responses and optional `expectedUpdatedAt` to edit requests. The app sends the expected revision; older clients may omit it.

**Alternatives considered**: Required API version break; client-only comparison with no server guard; last-write-wins.

**Rationale**: Client-only detection cannot close the race between revalidation and submit. An optional request field protects the app while keeping existing consumers compatible. The server compares inside the workspace-scoped edit path before persistence.

## Data Flow

```text
URL/date/timezone/filter
        |
        v
calendarRange(state) -----------------------------+
        |                                          |
        v                                          |
Scheduler lifecycle ----+                         |
Broadcast parser -------+--> coalesced revalidate-+--> publishingStore.fetchCalendar
local mutation ----------+                                  |
                                                           v
                                             REST calendar response
                                                + updatedAt
                                                           |
                                                           v
                                      Pinia publications/activity/conflicts
                                                           |
                                                           v
                                             Scheduler and editor snapshot

Editor form + opened updatedAt
        |
        +--> remote calendar result with different updatedAt
        |          |
        |          +--> conflict state; form remains untouched
        |
        +--> PATCH(expectedUpdatedAt)
                   |
          current revision comparison
              +----+----+
              |         |
            match     mismatch
              |         |
            persist   structured conflict; no write
```

## Message Contract

The feature-local message is conceptually:

```ts
type CalendarInvalidationMessage = {
  version: 1
  type: 'calendar.invalidated'
  workspaceId: string
  reason: 'publication-created' | 'publication-updated' | 'publication-deleted' | 'channel-updated'
  publicationId?: string
  channelId?: string
}
```

The exact implementation must validate string lengths/allowed values and reject unknown keys or invalid optional identifiers as appropriate for the repository's runtime validation conventions. No body, title, scheduled time, media URL, access token, or full publication is sent.

## File Changes

| File | Action | Description |
|---|---|---|
| `apps/web/app/src/modules/publishing/application/calendarRange.ts` | Create | Pure range derivation and calendar-date/instant conversion. |
| `apps/web/app/src/modules/publishing/application/calendarRange.test.ts` | Create | Week/month, exclusive-end, timezone, DST, and invalid-date tests. |
| `apps/web/app/src/modules/publishing/application/useReactiveClock.ts` | Create | Fakeable clock, visible ticker, lifecycle cleanup, and visibility-return signal. |
| `apps/web/app/src/modules/publishing/application/useReactiveClock.test.ts` | Create | Fake-timer and visibility lifecycle tests. |
| `apps/web/app/src/modules/publishing/infrastructure/calendarBroadcast.ts` | Create | Channel feature detection, schema guard, subscribe/publish lifecycle. |
| `apps/web/app/src/modules/publishing/infrastructure/calendarBroadcast.test.ts` | Create | Minimal payload, malformed/foreign/unavailable channel tests. |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | Modify | Add `updatedAt`, stop authenticated local fallback/persistence, coalesced revalidation, and mutation invalidation. |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.sync.test.ts` | Create | Coalescing, stale fetch ordering, workspace filtering, auth storage, and mutation emission. |
| `apps/web/app/src/modules/publishing/application/useComposerScheduling.ts` | Modify | Consume shared reactive clock behavior and preserve current scheduling validation. |
| `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | Modify | Use range utility and sync lifecycle; route all refreshes through coordinator. |
| `apps/web/app/src/modules/publishing/views/SchedulerView.test.ts` | Modify | Range refresh and lifecycle assertions. |
| `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue` | Modify | Track dirty state/revision, show conflict decision state, send expected revision. |
| `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.test.ts` | Create/Modify | Dirty form preservation and stale revision interaction tests. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt` | Modify | Add response revision and optional edit expected revision. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingMappers.kt` | Modify | Map `updatedAt` into mutation/calendar results. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingPublicationHandlers.kt` | Modify | Compare expected revision before edit persistence. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` | Modify | Bind expected revision in PATCH request. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingApiTest.kt` | Modify | DTO serialization and revision mapping coverage. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingPublicationHandlersTest.kt` | Create/Modify | Match/mismatch/no-expected-revision handler tests. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllersTest.kt` | Modify | Request/409 contract coverage. |
| `apps/web/app/e2e/fixtures/scheduler-mocks.ts` | Modify | Stop using `pt_publications` for authenticated canonical state; update route-backed source. |
| `apps/web/app/e2e/specs/scheduler-views.spec.ts` | Modify | Visibility/range refresh scenario. |
| `apps/web/app/e2e/specs/scheduler-edit-post.spec.ts` | Modify | Stale editor scenario. |
| `apps/web/app/e2e/specs/scheduler-browser-sync.spec.ts` | Create | Two-page/tab invalidation and workspace isolation scenarios. |
| `docs/compliance/data-inventory.md` | Modify | State the authenticated publication-storage boundary and fallback behavior. |
| `docs/compliance/data-inventory.yaml` | Modify | Keep machine-readable `pt_publications` controls aligned. |

## Error and lifecycle handling

- If `BroadcastChannel` construction or posting fails, close any partial channel and continue with REST/visibility paths.
- If a message fails validation or workspace comparison, do nothing and avoid logging payload contents.
- If revalidation fails while authenticated, retain the last canonical in-memory result and expose the existing store error state; never use `pt_publications`.
- Clean up timers, `visibilitychange` listeners, and channel subscriptions on component/composable unmount.
- If the editor detects a changed revision, keep all controls and local attachments untouched. The UI may offer refresh/discard or close actions; only an explicit user choice may replace the snapshot.
- If the expected-revision request receives a conflict, do not retry automatically. The user must choose how to proceed.

## Testing Strategy

1. RED tests for pure range and message guards before implementation.
2. Fake timers and synthetic visibility events for clock/ticker behavior.
3. Store tests with mocked REST and mocked BroadcastChannel to prove one fetch per burst, same-workspace filtering, and `latestCalendarFetchId` behavior.
4. Backend unit/controller tests prove revision comparison occurs before writes and that the API error shape remains versioned.
5. Component tests prove dirty editor values remain after a remote snapshot changes.
6. Playwright uses two isolated pages/contexts or a deterministic channel mock, route-backed canonical calendar data, and explicit workspace identities. Tests must not depend on authenticated `pt_publications` persistence.
7. Run focused suites before broad app/backend gates, then record exact outcomes in verification and QA artifacts.
