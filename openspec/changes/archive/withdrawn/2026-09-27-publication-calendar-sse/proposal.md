# Proposal: Publication Calendar SSE

## Intent

Make the authenticated scheduler converge on the current publication state
without manual F5 and without browser-wide BroadcastChannel limits. The
previous change (`reactive-calendar-browser-sync`) added a same-browser
cross-tab invalidation; this change adds the cross-process / cross-client
invalidation path that the previous proposal explicitly deferred: the
publishing worker, future orchestrators, and other clients (background tabs,
sessions without BroadcastChannel, sleep-restored tabs, second backend
replicas) must be able to tell the SPA that the visible range may be stale.

REST remains the canonical source. The new stream is invalidation-only: the
receiver MUST refetch the visible range; it MUST NOT apply payload fields as
state.

## Scope

### In Scope

- A workspace-scoped Server-Sent Event stream
  `GET /api/publishing/publications/events` returning
  `Flux<ServerSentEvent<PublicationEventResponse>>`.
- A domain event type `PublicationEvent` with a closed `changeType` enum
  (wire names):
  - `publication.created`
  - `publication.updated`
  - `publication.rescheduled`
  - `publication.deleted`
  - `publication.status-changed`
- A port `PublicationEventPublisher.publish(event)` in `publishing/domain`.
  The Reactor `tryEmitNext` detail stays inside the infrastructure adapter.
- A Reactor-backed adapter `ReactorPublicationEventPublisher` implementing the
  port with `Sinks.many.multicast().directBestEffort`, mirroring the existing
  `ReactorChannelEventPublisher`.
- A registry interface `PublicationEventStreamRegistry` exposing a
  `stream(): Flux<PublicationEvent>` so controllers can filter and stream.
- Workspace-scoped filter inside the controller plus active-membership
  enforcement via `WorkspaceMembershipGate.requireActiveMember` plus a
  20-second heartbeat to match the channel stream.
- Post-commit emission from `PublishingJobExecutor` for the worker transitions
  that already persist: `markPublished`, `markFailed`, `markBlocked`,
  `blockPublication`, and `AMBIGUOUS_OUTCOME`. Backend-side `EditPublication`,
  `CancelPublication`, `DeletePublication`, `ReschedulePublication`, and
  `RetryPublication` handlers are also emitters, but they only need to fan
  out after the transaction commits; they reuse the same port.
- SPA `subscribePublicationEvents()` action that opens a fetch-streaming SSE
  with Bearer and `X-Workspace-Id`, parses frames with `consumeSseStream`,
  and translates them into `revalidation.request(currentRange)` calls. The
  SchedulerView owns the lifecycle.
- Strict-TDD coverage:
  - Backend (Kotlin): domain event contract, adapter behaviour, controller
    workspace filtering, membership denial, and heartbeat, handler/worker
    post-commit emission, rollback suppression.
  - Vitest: store subscriber, coordinator coalescing under duplicate
    signals, SchedulerView lifecycle, workspace switch, disconnect.
  - Playwright: cross-tab SSE scenario driven from the route-backed SSE
    fixture only (no `BroadcastChannel` equivalent), so tab B proves the
    SSE path refetches `/calendar`.

### Out of Scope

- Cross-replica fan-out via a shared bus (Kafka/RabbitMQ). The repository
  does not select one yet (ADR-0011). The new stream is in-process best-effort
  per replica; REST remains authoritative.
- Native `EventSource` support. The SSE endpoint requires a Bearer header,
  which `EventSource` cannot send; the existing channel-events convention
  rejects it.
- Replacing REST with SSE payloads. The SPA always refetches; the SSE
  payload is metadata only.
- Optimistic concurrency on the worker write path. That work is a separate
  follow-up already documented as out of scope in the previous change.
- Persistent event store or replay. Late subscribers do not see past events
  by design; the visibility/focus revalidation covers the gap.
- New dependencies. We use the existing Reactor + Spring WebFlux SSE
  substrate and the existing `consumeSseStream` parser.

## Capabilities

### Modified Capabilities

- `visual-calendar`: same revalidation coordinator absorbs SSE events; no
  new behaviour beyond another trigger.
- `publishing`: workers and backend handlers emit a typed publication
  invalidation event after every successful commit.

### New Capabilities

- None. The change composes with the existing `visual-calendar` and
  `publishing` capabilities. A new durable spec
  `openspec/specs/calendar-publication-sse/spec.md` is added to fix the
  stream contract.

## Approach

1. Add `PublicationEvent` and `PublicationEventType` in `publishing/domain`
   next to the existing `ChannelEvent` types.
2. Add a `PublicationEventPublisher` port in the same package. Hexagonal
   layering: domain owns the contract, infrastructure owns the Reactor
   adapter.
3. Implement `ReactorPublicationEventPublisher` using
   `Sinks.many.multicast().directBestEffort`. Reuse the exact pattern from
   `ReactorChannelEventPublisher`; no new infrastructure.
4. Add `PublicationEventStreamRegistry` as a separate functional interface
   so the controller can be unit-tested with a fake registry
   (`FakePublicationEventStreamRegistry`).
5. Emit post-commit from `PublishingJobExecutor` and from the publication
   mutation handlers. The emit happens only after the transaction boundary
   completes successfully and control returns; if the boundary throws or
   the state transition is not applied, no event is emitted. (The worker's
   internal `applied: Boolean` is a worker-local detail, not the general
   contract: handlers return the persisted draft or `Unit`.) The delete
   path follows the decided Option A: delete plus related side effects in
   a single atomic boundary, then emit.
6. Add `PublishingPublicationSseController.streamEvents()` returning
   `Flux<ServerSentEvent<PublicationEventResponse>>` with:
   - Bearer + `X-Workspace-Id` plus `WorkspaceMembershipGate.requireActiveMember`
     (header alone is not authorization; the channel-events filter-only
     precedent is deliberately not copied).
   - Workspace filter by `resourceContextProvider.requireWorkspaceContext().workspaceId`.
   - 20-second `heartbeat` event to keep proxies alive.
   - `event` field set to the dotted `changeType` wire value.
   - Best-effort publish: a publish failure after commit never rolls back
     the publication and never turns a committed REST write into an error.
7. Add a backend (Kotlin, not Vitest) suite for the controller proving
   workspace filter, membership denial, and heartbeat behaviour using a
   fake registry (parallel to the channel SSE test).
8. SPA side: expose `subscribePublicationEvents(onInvalidation)` /
   `unsubscribePublicationEvents()` from the publishing store as a pure
   input adapter (no coordinator or URL-range knowledge inside the store).
   `SchedulerView` owns the lifecycle and translates every parsed frame
   into `coordinator.request(currentRange.value)`.
9. SPA tests: extend `publishing.store.test.ts` with a subscriber test
   that simulates SSE frames and asserts:
   - workspace match triggers one coordinator request;
   - foreign-workspace frames are ignored;
   - heartbeats trigger no fetch;
   - `BroadcastChannel` + `SSE` + `SSE` within the coordinator window
     produce at most one `fetchCalendar` call (never one-per-frame).
10. Playwright: extend `apps/web/app/e2e/specs/scheduler-browser-sync.spec.ts`
    with a worker-driven scenario driven ONLY by the route-backed SSE
    fixture (no `BroadcastChannel` equivalent is posted). Tab B must
    refetch `/calendar` and render the new state, proving the SSE path
    rather than the local broadcast path.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublicationEventContracts.kt` | Create | Domain event types and `publish` port. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/events/ReactorPublicationEventPublisher.kt` | Create | Reactor-backed adapter using `Sinks.many.multicast().directBestEffort`; `EmitResult` ignored. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` | Modify | New `PublishingPublicationSseController.streamEvents()` endpoint with membership gate. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingPublicationHandlers.kt` | Modify | Post-commit `publish` from edit/delete/cancel/reschedule/retry handlers (delete pending Option A/B). |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/CreatePublicationHandler.kt` | Modify | Post-commit `publish` for the create path. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt` | Modify | Post-commit `publish` from success/failure/block/ambiguous/recovered/requeue paths. |
| `apps/web/app/src/modules/publishing/infrastructure/scheduling/PublishingSchedulingConfiguration.kt` | Modify | Wire the publisher port into the executor and worker beans. |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | Modify | Expose `subscribePublicationEvents(onInvalidation)` / `unsubscribePublicationEvents` as an input adapter; no coordinator knowledge. |
| `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | Modify | Own the SSE lifecycle; translate invalidations into `coordinator.request(currentRange)`. |
| `apps/web/app/src/modules/publishing/application/calendarRevalidation.ts` | Modify | Allow SchedulerView to call `request(range)` with the current range. |
| `apps/web/app/src/modules/publishing/application/index.ts` | Modify | Export new types if needed. |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.test.ts` | Create/Modify | Subscribe/unsubscribe and coalescing tests. |
| `apps/web/app/e2e/specs/scheduler-browser-sync.spec.ts` | Modify | Cross-tab SSE scenario. |
| `apps/web/app/e2e/fixtures/scheduler-mocks.ts` | Modify | Route-backed SSE handler. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/**` | Modify | Controller, handler, worker tests. |
| `openspec/specs/calendar-publication-sse/spec.md` | Create | Durable stream contract. |
| `docs/compliance/data-inventory.{md,yaml}` | Modify | No new event-derived personal data, but update if a new SSE-derived personal data category appears. |
| `openspec/changes/publication-calendar-sse/` | Create | This change's audit trail. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Event fires before transaction commit, so a rolled-back transition is observed | Medium | All emit sites call `PublicationEventPublisher.publish(...)` only after the transaction boundary completes successfully. Add a unit test that asserts no event fires when the boundary throws. Publisher failure after commit never fails the business operation. |
| Foreign workspace data leak via SSE | Medium | Controller requires active membership via `WorkspaceMembershipGate.requireActiveMember` plus the in-controller workspace filter; receiver ignores foreign frames. Mandatory non-member-denied test. |
| Cross-tab duplication (BroadcastChannel + SSE) causes duplicate fetches | Medium | Both paths funnel into the same `useCalendarRevalidation` coordinator; coalescing + max-age ceiling absorb duplicates. Add Vitest proof. |
| Multi-replica deployment loses events | High (known) | Documented as a known limitation; REST + visibility/focus revalidation remain authoritative. Record an ADR-less follow-up for a shared event bus. |
| Heartbeat cost under load | Low | Heartbeat cadence matches the channel stream (20 s); no payload payload size impact. |
| Native `EventSource` misuse | Low | Bearer header is required for the endpoint; native `EventSource` cannot send it and will be rejected by `IdentitySecurityConfiguration`. Document the rejection in the durable spec. |
| Strict TDD violation | Medium | Enforce RED → GREEN → REFACTOR for each contract; no suppressions, no broad mocks, no unsafe casts. Hexagonal boundaries preserved. |

## Rollback Plan

Roll back the SSE endpoint, the publisher port, the adapter, the controller,
and the SPA subscriber in one change. The previous change's
BroadcastChannel + visibility revalidation remains intact and continues to
cover same-browser invalidation. No database changes; the `updatedAt`
column from the previous slice is reused. No migration rollback is required.

If a partial rollout is desired, deploy the SSE endpoint first behind a
feature flag that disables emission. The SPA subscriber can be deployed
independently because the SPA tolerates the absence of the stream (no fetch
storm, BroadcastChannel + visibility revalidation still work).

## Dependencies

- Existing `Sinks.many.multicast().directBestEffort` Reactor substrate.
- Existing `MediaType.TEXT_EVENT_STREAM_VALUE` Spring WebFlux SSE
  encoding.
- Existing `consumeSseStream` parser in the shared SPA library.
- Existing `IdentitySecurityConfiguration` default-deny Bearer + workspace
  header enforcement.
- Existing `useCalendarRevalidation` coordinator from
  `reactive-calendar-browser-sync` (base: worktree branch `reactive-calendar`,
  commit `d87d33f5`; both changes currently untracked and absent from `main`).
- No new dependencies, no migration, no third-party infrastructure.

## Success Criteria

- [ ] A worker transition (e.g. `markPublished`, `markFailed`,
      `markBlocked`) emits exactly one SSE event after the transaction
      commits; a rolled-back transition emits zero.
- [ ] The new SSE endpoint filters by the active workspace and rejects
      foreign-workspace events; a tab in workspace B never receives a
      workspace A event.
- [ ] The SPA folds one or more invalidation signals within the coordinator
      window into at most one visible-range revalidation (never one fetch
      per frame), even when both BroadcastChannel and SSE deliver the same
      invalidation.
- [ ] Closing the SSE connection (or simulating a backend failure) does
      not break the SPA: visibility revalidation and BroadcastChannel
      continue to provide same-browser invalidation.
- [ ] Backend focused tests prove the controller workspace filter and
      heartbeat. Handler tests prove the post-commit emission pattern.
- [ ] Playwright two-tab spec proves that a tab observing a worker-driven
      mutation on tab B refreshes without manual F5.
- [ ] `publication-calendar-sse` files modified only as needed; the
      channel-events SSE remains untouched.
- [ ] No new source comments, suppressions, unsafe casts, or
      `Any`-broadened mocks; `biome`, `vue-tsc`, and `just backend-check`
      stay green.

## Cross-references

- `reactive-calendar-browser-sync` for the SPA revalidation coordinator,
  BroadcastChannel contract, and store wiring.
- `channel-events-sse` for the existing SSE pattern and auth conventions.
- ADR-0011 for the multi-replica fan-out caveat.
