# Exploration: Publication Calendar SSE

## Problem

The previous change (`reactive-calendar-browser-sync`) added a BroadcastChannel
optimization for same-browser cross-tab invalidation. It also deferred an
additional capability: cross-process / cross-client invalidations driven by
the publishing worker and any future orchestrator that updates a publication
out of band (worker transitions, retries, blocked → recovery, etc.). Without
that capability, the SPA only refreshes the calendar when a tab is visible and
has a BroadcastChannel — which excludes background tabs, sleep-restored tabs,
sessions without broadcast support, and the moment a worker transitions a
publication from `QUEUED` to `PROCESSING` to `PUBLISHED`/`FAILED`/`BLOCKED` from
a different process or even a different backend replica.

The user explicit request: workers, retries, cancellations, blocks, recoveries
and out-of-band status changes should refresh the calendar across browser
sessions without manual F5.

## Evidence inspected

### Frontend (SPA)

| Area | Evidence | Finding |
|---|---|---|
| Scheduler store SSE | `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts:743` `subscribeChannelEvents` | Already opens fetch-streaming against `/api/publishing/channels/events`, filters out non-channel events, and refreshes the connected channel list. This is the same wiring pattern publication SSE must reuse. |
| Shared SSE parser | `apps/web/app/src/shared/lib/sse.ts:40` `consumeSseStream` | Bearer-friendly fetch-streaming parser with structured error swallowing; used by the channel-event subscriber. |
| Calendar revalidation | `apps/web/app/src/modules/publishing/application/useCalendarRevalidation.ts` and `useCalendarRevalidation.test.ts` | The previous change introduced a feature-local coalescing coordinator fed by URL changes, visibility, and BroadcastChannel. SSE must plug in as another trigger for `request(range)`, not a parallel fetch path. |
| BroadcastChannel adapter | `apps/web/app/src/modules/publishing/application/calendar-invalidation-channel.ts` | Minimal invalidation contract with workspace filter; same-browser only. |
| Auth fetch wrapper | `apps/web/app/src/modules/auth/infrastructure/auth-api.ts:402` `withWorkspace` | Sets `X-Workspace-Id` from the active workspace when `workspaceScoped: true`; SSE calls must opt in. |
| Auth `apiFetchRaw` | `apps/web/app/src/modules/auth/infrastructure/auth-api.ts:427` | Returns a `Response` so SSE streams can be consumed byte-by-byte. |

### Backend (Kotlin, Spring WebFlux, Modulith)

| Area | Evidence | Finding |
|---|---|---|
| Existing SSE substrate | `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/events/ReactorChannelEventPublisher.kt` | Uses `Sinks.many().multicast().directBestEffort<ChannelEvent>()`. Same pattern fits publication invalidations. |
| Channel SSE controller | `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt:177` `streamEvents` | `Flux<ServerSentEvent<ChannelEventResponse>>`, 20s heartbeat, workspace filter inside the controller, no native `EventSource` requirement, and `ResourceContextProvider.requireWorkspaceContext()` for workspace scoping. |
| Channel SSE DTO | `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt:197` `ChannelEventResponse` | `{ type, workspaceId, socialAccountId, occurredAt }`. |
| Channel event domain | `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/OAuthConnectionContracts.kt:34` `ChannelEvent` | Carries workspace id, channel id, occurredAt; published by `CompleteLinkedInConnectionHandler` after the transaction commits (line 138). |
| Publishing worker transitions | `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt` | `PublishingJobExecutor.executeClaim` covers `QUEUED/SCHEDULED → PROCESSING → PUBLISHED`, terminal `FAILED`, `BLOCKED` recovery, and `AMBIGUOUS_OUTCOME` (line 596). These are exactly the moments publication invalidations should fire. |
| Persistence | `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt:271,295,342` | `markPublished`, `markFailed`, `markBlocked` are called inside `transactionRunner.runAtomically`. Publication events MUST fire only after the transaction commits; emitting before would violate the "server-truth" contract. |
| Security wiring | `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt:140` `authorizeExchange` | Default-deny via `.anyExchange().authenticated()`; SSE inherits the same JWT bearer + `X-Workspace-Id` requirement. Native `EventSource` is rejected because it cannot send the bearer header (the existing channel-events endpoint already documents this). |
| API media-type | `MediaType.TEXT_EVENT_STREAM_VALUE` is already used by `channels/events`. The publishing controller uses `@Version` + `ApiVersionStrategy` and the versioned media type. The new SSE endpoint MUST follow the same conventions. |
| Modulith governance | `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/ModuleMetadata.kt` exposes the `infrastructure` named interface; `server/smp/src/test/kotlin/com/profiletailors/smp/ModularityVerificationTest.kt` enforces module boundaries. The SSE publisher, registry, controller and domain event types must respect that split. |

### Existing tests / coverage

| Area | Evidence | Finding |
|---|---|---|
| Channel SSE controller test | `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllersTest.kt:138` `constructs SSE stream scoped to active workspace` | Pattern for asserting workspace filter using a fake registry. Reuse for the publication controller test. |
| SSE parser test | `apps/web/app/src/shared/lib/sse.test.ts` | Confirms heartbeat handling and malformed payload swallowing; new tests should not duplicate this. |
| Scheduler store tests | `apps/web/app/src/modules/publishing/infrastructure/publishing.store.test.ts` | Existing suite covers subscribe/unsubscribe; we will extend it for the publication SSE subscription. |

## Current flow

```text
URL/date/filter, visible return, clock boundary, or valid same-workspace message
        |
        v
calendar sync coordinator -- coalesces --> one visible-range fetch
                                          |
                                          v
                       publishingStore.fetchCalendar
                            latestCalendarFetchId remains authoritative
```

`CalendarRevalidationCoordinator` covers same-browser triggers. The worker
path is still outside:

```text
worker: markPublished/markFailed/markBlocked
   |
   +-- atomic transaction commits --> row in DB
   |
   +-- nothing publishes a publication invalidation anywhere
```

## Desired flow

```text
worker: markPublished/markFailed/markBlocked
   |
   +-- transaction commits
   |
   +-- publication event publisher.tryEmitNext (Sinks.many.multicast.directBestEffort)
                                                 |
   v
PublishingSseController.stream(): Flux<ServerSentEvent<PublicationEventResponse>>
                                                 |
                                                 v
                                    workspace filter inside controller
                                                 |
                                                 v
SPA: subscribePublicationEvents (fetch-streaming with Bearer + X-Workspace-Id)
                                                 |
                                                 v
                                  CalendarRevalidationCoordinator.request(range)
                                                 |
                                                 v
                                  publishingStore.fetchCalendar()
```

Both the BroadcastChannel adapter and the SSE subscriber call into the same
coordinator, so the visible-state, max-age, debounce, and `latestCalendarFetchId`
guarantees remain centralized.

## Options considered

| Option | Pros | Cons | Decision |
|---|---|---|---|
| Per-worker RabbitMQ/Kafka outbox | Durable cross-replica delivery; survives backend restarts | Adds infrastructure dependency the repository does not yet select; ADR-0011 says no production Kafka/RabbitMQ yet | Rejected for now; revisit when cross-replica fan-out becomes a real requirement |
| Per-replica Reactor `Sinks.many.multicast.directBestEffort` (same as channel events) | Reuses proven pattern; no new infrastructure | Best-effort across replicas; acceptable as long as REST remains authoritative and visibility revalidation is the safety net | Selected for this slice |
| Long-poll | Simple | Hard to bound, hurts server | Rejected |
| Pure visibility/focus revalidation without SSE | No backend changes | Workers, retries, blocked recoveries, and other-client edits remain invisible until the tab is focused; doesn't solve the user's problem | Rejected |
| Trust SSE payload as canonical state | Avoids extra fetch | Violates the server-truth contract from design; receiver must refetch | Rejected; SSE payload is invalidation metadata only, never publication content |

## Constraints and open risks

1. **Event-after-commit invariant.** Every SSE emission MUST happen after the
   publishing transaction commits. If the transaction rolls back the event
   must NOT fire. Pattern: call `publicationEventPublisher.tryEmitNext(...)`
   from the same handler that already calls `publicationRepository.markPublished/markFailed/markBlocked`,
   AFTER `transactionRunner.runAtomically { ... }` returns true. This mirrors
   how `channelEventPublisher.publish(...)` is invoked from
   `CompleteLinkedInConnectionHandler` after the atomic persist block.
2. **Single-replica caveat.** The `Sinks.many.multicast.directBestEffort`
   substrate is in-process. Multi-replica deployments will need an outbox or
   shared event bus. Document this in the design as a known limitation and
   record an ADR-less follow-up, since `ADR-0011` does not yet select a
   shared event bus.
3. **Backpressure / replay.** `directBestEffort` does not buffer. Late
   subscribers do not see past events; this is acceptable because REST is
   authoritative and the visibility/focus revalidation covers the safety net.
4. **Heartbeats.** A heartbeat on the publication stream is required to keep
   the connection alive through proxies and load balancers. Use the same
   20-second cadence as the channel stream.
5. **Workspace isolation.** The controller MUST filter events by the active
   workspace's `X-Workspace-Id` and reject messages where the event's
   `workspaceId` does not match. Authentication and `ResourceContextProvider`
   enforce the header; the filter is defence-in-depth.
6. **No content over the wire.** SSE payloads MUST carry only `event name`,
   `publicationId`, optional `socialAccountId`, `changeType`, and `occurredAt`.
   Never publish publication content, scheduled time, media URLs, or tokens.
7. **Concurrency with the existing BroadcastChannel.** Same-tab A still
   emits invalidation via the local store and triggers the same coordinator
   without a fetch. Different-tab A/B still triggers the channel before SSE
   fires. SSE must therefore remain idempotent: receiving the same event in
   two paths should produce at most one fetch thanks to the coordinator's
   debounce and max-age ceiling.
8. **Strict TDD.** No source change without a failing test. Repository
   instructions forbid suppressions, weak mocks, and unsafe casts. Hexagonal
   boundaries: domain event types live in `publishing/domain`, publisher
   port + adapter in `infrastructure/events`, controller + DTO in
   `infrastructure/http`, Modulith governance enforced.

## Recommended implementation slice

Three review units (consistent with the previous change):

1. **Pure contracts + backend domain + port**
   - `PublicationEvent` value object and `PublicationEventType` enum in
     `publishing/domain` (alongside `ChannelEvent`).
   - `PublicationEventPublisher` port in the same domain package.
2. **Backend infrastructure + controller + worker wiring + RED tests**
   - `ReactorPublicationEventPublisher` adapter implementing the port with the
     existing `Sinks.many.multicast.directBestEffort` pattern.
   - `PublicationEventStreamRegistry` interface mirroring `ChannelEventStreamRegistry`.
   - `PublishingPublicationSseController` exposing `GET /api/publishing/publications/events`
     with workspace filter, heartbeat, and Bearer-friendly SSE response.
   - Wire `markPublished/markFailed/markBlocked` happy paths in the worker
     to call `publicationEventPublisher.tryEmitNext(...)` after commit.
3. **SPA subscription + coordinator wiring + Playwright**
   - `subscribePublicationEvents` action in the publishing store, mirroring
     `subscribeChannelEvents`, that calls `consumeSseStream` and translates
     publication events into `revalidation.request(currentRange)`.
   - Vitest store test that asserts no fetch when receiving same-tab events
     that the BroadcastChannel already covers, and a single coalesced fetch
     on cross-tab SSE.
   - Playwright two-tab spec with route-backed `/publications/events` that
     asserts a worker mutation in one tab triggers a fetch in another tab.

## Open questions to resolve during propose/design

- Should `changeType` be a single string or a closed enum?
  We will use a closed enum to keep payload typing tight.
- Should we emit a single event per worker transition (recommended) or a
  heartbeat-with-status variant? The previous proposal prefers single event
  per transition to keep payload small and unambiguous.
- Should the SPA subscribe unconditionally on SchedulerView mount or only
  when the user navigates to it? The current `subscribeChannelEvents` is
  invoked from `AppShell.vue`; the same applies here, but we will use
  SchedulerView scope to keep the spec tight.

## Out of scope (explicit)

- Cross-replica fan-out via shared bus (Kafka/RabbitMQ).
- Native `EventSource` support.
- Replacing REST with SSE payloads (the SPA always refetches).
- Optimistic concurrency on the worker write path (that lives in
  `reactive-calendar-browser-sync` as a separate follow-up).
