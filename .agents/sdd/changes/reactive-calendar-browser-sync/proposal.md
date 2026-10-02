# Proposal: Reactive Calendar Browser Sync

## Intent

Make the authenticated scheduler converge on current workspace data without forcing users to navigate manually or risk losing edits. The current surface has no cross-tab invalidation, performs calendar-range arithmetic in more than one component path, updates its scheduling clock with a component-local interval, and can use the shared `pt_publications` browser key as a fallback after an authenticated request fails. The editor also submits an edit without knowing whether the publication changed after it was opened.

This change keeps REST as the canonical calendar source while adding a small browser synchronization layer. It also adds revision metadata and an optimistic concurrency guard so a remote edit is surfaced instead of silently overwritten.

## Scope

### In Scope

- A testable reactive clock and visibility-aware calendar ticker that updates while the scheduler is visible and refreshes when the document becomes visible again.
- A single visible-range utility for week/month scheduler requests, including timezone-aware calendar-date handling and the backend's exclusive upper-bound semantics.
- A minimal, runtime-validated `BroadcastChannel` invalidation contract containing only event kind, workspace identity, and safe publication/channel invalidation metadata; receivers must ignore foreign or malformed messages.
- Coalesced calendar revalidation for local mutations and valid same-workspace browser messages, reusing `publishingStore.fetchCalendar()` and its existing `latestCalendarFetchId` stale-response protection.
- Authenticated `pt_publications` hardening so unscoped local data cannot become an authenticated workspace calendar fallback or be persisted as canonical authenticated publication data.
- `updatedAt` revision metadata in authenticated calendar and publication mutation responses.
- An optional expected-revision guard on publication edits, with a conflict response that the editor can surface without replacing the user's form values.
- Vitest coverage for pure range/clock/message behavior, store coalescing and storage boundaries, backend DTO/mapping/concurrency behavior, and editor conflict state.
- Playwright coverage for visible scheduler refresh, same-workspace cross-tab invalidation, malformed/foreign message rejection, and stale-editor protection.
- Reconciliation of affected compliance/OpenSpec documentation, including the `pt_publications` inventory entry.

### Out of Scope

- `publication-calendar-sse`, `openspec/specs/channel-events-sse/spec.md`, or any server-sent event endpoint/event stream.
- Replacing REST with a browser message payload or trusting browser messages as publication data.
- Continuous polling while a document is hidden.
- New dependencies, a broad scheduler redesign, provider changes, recurring-schedule synchronization, or a database migration for `updated_at`.
- Silent last-write-wins behavior for an editor whose initial publication revision is stale.
- Cross-workspace synchronization, shared browser storage of authenticated publication data, or exposing raw publication content in browser invalidation messages.

## Capabilities

### New Capabilities

- None. The change composes and hardens the existing visual-calendar and publishing capabilities.

### Modified Capabilities

- `visual-calendar`: visible-range correctness, visibility-aware revalidation, reactive clock boundaries, and safe cross-tab invalidation.
- `publishing`: revision metadata and optimistic edit conflict handling.
- `privacy-compliance`: authenticated publication browser storage no longer acts as an unscoped canonical fallback.

## Approach

Create small feature-local utilities rather than a global event bus:

1. `calendarRange` derives the request range from the URL surface/date/timezone and is the only owner of visible-range arithmetic.
2. `useReactiveClock` owns a fakeable clock ref and a visibility-aware one-minute boundary signal. The scheduler uses it for refresh decisions; composer scheduling reuses the same behavior instead of maintaining an unconditional interval.
3. `calendarBroadcast` owns feature-detected `BroadcastChannel` creation, message parsing, and minimal invalidation serialization. It does not carry publication data.
4. The publishing store exposes a coalesced calendar revalidation entry point. `SchedulerView` supplies the current range and active workspace identity; the store continues to call `fetchCalendar()` so monotonic response protection remains centralized.
5. Authenticated store initialization and mutation persistence no longer read or write the anonymous `pt_publications` key. Failed authenticated fetches retain the last canonical in-memory result and expose the existing error path rather than substituting unscoped local data.
6. Backend calendar/mutation DTOs expose `updatedAt`. The app sends `expectedUpdatedAt` for edits, and the backend rejects a mismatch with the existing API error convention. The editor records a conflict state and preserves all local form values.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `apps/web/app/src/modules/publishing/application/calendarRange.ts` | Create | Shared visible-range extraction and timezone-safe calendar arithmetic. |
| `apps/web/app/src/modules/publishing/application/useReactiveClock.ts` | Create/Modify | Testable clock and visibility-aware ticker used by scheduler/composer. |
| `apps/web/app/src/modules/publishing/infrastructure/calendarBroadcast.ts` | Create | Validated minimal BroadcastChannel messages with feature detection. |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | Modify | Revalidation coalescing, workspace/authentication storage boundary, revision mapping, invalidation emission. |
| `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | Modify | One range owner, lifecycle listeners, visibility/ticker wiring, editor conflict coordination. |
| `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue` | Modify | Dirty-form and stale-revision detection; explicit conflict UI without form overwrite. |
| `apps/web/app/src/modules/publishing/application/useComposerScheduling.ts` | Modify | Reuse reactive clock behavior. |
| `apps/web/app/src/modules/publishing/**.test.ts` | Create/Modify | Strict-TDD unit and component coverage. |
| `apps/web/app/e2e/specs/scheduler-*.spec.ts`, fixtures | Modify | Browser acceptance coverage without authenticated local-storage canonical data. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt` | Modify | `updatedAt` and optional expected revision contract. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingMappers.kt` | Modify | Map persisted revision to API responses. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingPublicationHandlers.kt` | Modify | Enforce expected revision on app edit path. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` | Modify | Bind expected revision and preserve existing error/media-type conventions. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/**` | Modify | API, handler, controller, and concurrency tests. |
| `docs/compliance/data-inventory.md`, `docs/compliance/data-inventory.yaml` | Modify | Reconcile implemented `pt_publications` browser-storage controls. |
| `openspec/changes/reactive-calendar-browser-sync/` | Create | This change's audit trail and verification inputs. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A malformed or foreign browser message triggers a fetch or data leak | Medium | Validate a narrow schema, compare active workspace identity, never apply message data, and test malformed/foreign cases. |
| Several updates create a request storm | Medium | Debounce/coalesce invalidations per event loop/short window and reuse `fetchCalendar()`'s monotonic fetch id. |
| Hidden tabs consume unnecessary requests | Medium | Stop ticker work while hidden; perform one current-range refresh on visibility return. |
| DST or timezone conversion shifts a calendar day | Medium | Use `@internationalized/date` calendar dates and test DST/positive-offset boundaries with exclusive `to`. |
| Authenticated users see stale or foreign local data | High | Remove authenticated local fallback/persistence, retain canonical in-memory state on failure, and change E2E fixtures. |
| A remote edit overwrites a dirty editor | High | Carry `updatedAt`, send `expectedUpdatedAt`, reject server mismatch, and preserve form state in a conflict UI. |
| API response change breaks other consumers | Medium | Add compatible fields, update constructor tests and all mapping consumers, and run backend checks. |
| Browser support/test environment lacks BroadcastChannel | Low | Feature-detect and keep REST/visibility paths fully functional; test an unavailable channel. |

## Rollback Plan

Revert the frontend synchronization utilities and wiring, the optional expected-revision request/handler changes, and the DTO fields as one change. Because the existing `updated_at` column is reused and no migration is planned, rollback does not require database reversal. If a staged delivery is used, deploy the DTO field/optional guard before the editor sends `expectedUpdatedAt`; older clients remain compatible because the expected field is optional. Restore E2E fixtures to API-backed mocks rather than restoring an authenticated local-storage fallback.

## Dependencies

- Existing `@internationalized/date` dependency (`3.12.4`) and current composer usage.
- Existing Vue 3/Pinia lifecycle, `document.visibilityState`, `visibilitychange`, and browser `BroadcastChannel` APIs.
- Existing backend `publications.updated_at` column and workspace membership/query scoping.
- Existing Vitest and scheduler Playwright configurations.
- No new package or database migration.

## Success Criteria

- [ ] A visible scheduler revalidates its current range after a valid same-workspace mutation/invalidation and coalesces bursts into one fetch.
- [ ] A hidden scheduler does not continuously poll and refreshes once when it becomes visible again.
- [ ] Week/month ranges are derived in one utility, are timezone-correct, and include the final visible day under the backend's exclusive upper bound.
- [ ] Malformed, unsupported, unavailable, and foreign-workspace browser messages never update the store or trigger a cross-workspace fetch.
- [ ] Authenticated calendar failures never substitute unscoped `pt_publications` data, and authenticated canonical data is not written to that key.
- [ ] Calendar and mutation responses expose a usable publication revision, and stale edit submissions are rejected without replacing dirty form values.
- [ ] Vitest, backend focused tests, app type/lint/build, and scheduler Playwright evidence are recorded with exact outcomes.
- [ ] `publication-calendar-sse` remains unchanged and explicitly documented as deferred.
