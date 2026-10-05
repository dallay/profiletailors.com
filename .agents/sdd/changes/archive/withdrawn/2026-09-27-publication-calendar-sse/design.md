# Design: Publication Calendar SSE

## Technical Approach

Reuse the proven channel-events SSE substrate for a second, independent
stream scoped to publication invalidations. The new stream is
invalidation-only: the SPA always refetches the visible range through the
existing `CalendarRevalidationCoordinator` introduced by
`reactive-calendar-browser-sync`. No publication content ever crosses the
stream.

Backend layering follows the hexagonal rule (`domain <- application <-
infrastructure`):

- `publishing/domain/PublicationEventContracts.kt` owns `PublicationEvent`,
  `PublicationEventType`, and the `PublicationEventPublisher` port
  (`fun publish(event: PublicationEvent)`).
- `publishing/infrastructure/events/ReactorPublicationEventPublisher.kt`
  implements the port with `Sinks.many().multicast().directBestEffort`,
  ignoring the `EmitResult` (best-effort, mirroring
  `ReactorChannelEventPublisher`).
- `publishing/infrastructure/http/` owns the SSE controller and the
  `PublicationEventResponse` DTO.
- Application handlers and the publishing worker call the port only after
  their transaction boundary completes successfully.

Base dependency: this change stacks on `reactive-calendar-browser-sync`
(worktree branch `reactive-calendar`, commit `d87d33f5`). Both changes are
currently untracked in this worktree and absent from `main`. This change
MUST NOT be applied against a `main` that does not contain the coordinator,
the shared clock, and the `BroadcastChannel` adapter.

## Architecture Decisions

### Decision: REST remains authoritative; SSE is best-effort invalidation

**Choice**: The SPA refetches `GET /api/publishing/publications/calendar`
for the current visible range on every relevant SSE frame.

**Alternatives considered**: Applying SSE payload fields as state; long-poll.

**Rationale**: SSE frames can be dropped (`directBestEffort` does not
buffer), reordered across replicas, or forged by same-origin code. The
server already owns workspace authorization and calendar filtering. The
visibility/focus revalidation from the previous change remains the safety
net.

### Decision: Reuse the Reactor `directBestEffort` substrate

**Choice**: `Sinks.many().multicast().directBestEffort<PublicationEvent>()`,
exactly mirroring `ReactorChannelEventPublisher`.

**Alternatives considered**: Shared bus (Kafka/RabbitMQ), persistent outbox.

**Rationale**: No shared bus is selected yet (ADR-0011). A second substrate
would double the operational surface for a best-effort signal. Multi-replica
fan-out is explicitly unsupported until a shared substrate is selected; the
durable spec records this as a known limitation.

### Decision: Post-commit emission with handler-correct semantics

**Choice**: A publication invalidation event MUST be emitted only after the
transaction boundary completes successfully and control returns. If the
boundary throws or the state transition is not applied, no event is emitted.

**Alternatives considered**: "Emit only if `runAtomically` returns `true`".

**Rationale**: `AtomicTransactionRunner.runAtomically` is generic
(`suspend fun <T : Any> runAtomically(block: suspend () -> T): T`;
`R2dbcAtomicTransactionRunner.kt:22`). Publication handlers return the
persisted `PublicationDraft` (create/edit/retry/reschedule) or `Unit`
(cancel); they do not return `Boolean`. Only some worker paths deliberately
return `Boolean` (`finalizeSuccessfulPublication`,
`handleAmbiguousOutcome`). Generalizing the worker's `applied` flag to all
handlers is incorrect. The correct invariant is success-return, not
`== true`.

### Decision: Delete boundary follows Option A (decided)

**Choice**: Option A, decided. The delete plus its related side effects
move into a single atomic boundary, and `publication.deleted` is emitted
afterwards. Option B is rejected.

**Alternatives considered**: Option B — declare the authoritative deletion
to be `deleteUnpublished()` success and emit regardless of the auxiliary
transaction.

**Rationale**: Today `DeletePublicationHandler`
(`PublishingPublicationHandlers.kt:185-200`) calls
`publicationRepository.deleteUnpublished()` outside `transactionRunner` and
then runs a second transaction for recurrence pause plus notification
state. Under the current code, "delete succeeds → transaction commits →
SSE" misdescribes reality: the row can disappear while the handler still
fails. Option A keeps "server truth after successful mutation" clean but
expands scope. Option B is minimal but bakes a two-phase delete into the
contract. The agent MUST NOT discover this during implementation; the
decision is recorded here as blocking `apply` for the delete emitter.

### Decision: Tenant boundary is principal plus active membership

**Choice**: The SSE controller requires the authenticated principal AND
active workspace membership via `WorkspaceMembershipGate.requireActiveMember`
(`authorization/application/WorkspaceMembershipGate.kt:15`), in addition to
the `X-Workspace-Id` header and the in-controller workspace filter.

**Alternatives considered**: Header plus in-controller filter only (the
channel-events precedent).

**Rationale**: `X-Workspace-Id` is a requested workspace placed into
`ResourceContext`; the primary tenant defence is application/repository
scoping plus the membership gate. Copying the weaker channel-events
boundary because precedent exists would violate security-first. The durable
spec carries a mandatory non-member-denied scenario.

### Decision: Minimal payload including `workspaceId`

**Choice**:

```ts
PublicationEventResponse {
  workspaceId: string
  publicationId: string
  socialAccountId?: string
  changeType: PublicationEventType
  occurredAt: Instant
}
```

**Alternatives considered**: Omitting `workspaceId` to keep the frame smaller.

**Rationale**: The receiver must ignore foreign-workspace frames, which
requires `workspaceId` on the wire. It is identity metadata, not
publication content, and adds a useful receiver-side defence. Never sent:
body, title, schedule, media URLs, tokens, provider payloads.

### Decision: Closed wire vocabulary with `publication.*` names

**Choice**: Kotlin enum `PublicationEventType` with
`CREATED, UPDATED, RESCHEDULED, DELETED, STATUS_CHANGED`, mapped by a
single function to wire names `publication.created`, `publication.updated`,
`publication.rescheduled`, `publication.deleted`,
`publication.status-changed`.

**Alternatives considered**: `publication-created` style names.

**Rationale**: The existing stream uses dotted names
(`connected-channel.updated`). One convention across both streams reduces
receiver branching. The enum stays closed; receivers ignore unknown values
without logging payload contents.

### Decision: Domain port is `publish`, Reactor detail stays in infrastructure

**Choice**:

```kotlin
fun interface PublicationEventPublisher {
    fun publish(event: PublicationEvent)
}
```

**Alternatives considered**: `tryEmitNext(event)` on the port.

**Rationale**: `tryEmitNext` leaks a Reactor `Sinks` concept into the
domain port. The project pattern is `ChannelEventPublisher.publish` in
domain with `sink.tryEmitNext` hidden inside the adapter. Keeping `publish`
preserves hexagonal direction and lets a future outbox/bus replace Reactor
without changing the application/domain API. Blank `workspaceId` /
`publicationId` are rejected (non-null Kotlin types make a `null` contract
meaningless).

### Decision: Publisher failure never fails the business operation

**Choice**: SSE publication is best effort. Failure to emit after a
committed publication MUST NOT roll back the publication, MUST NOT turn a
successful mutation into a failed mutation (no HTTP 500 for an already
committed REST write), and MUST NOT make SSE authoritative.

**Alternatives considered**: Propagating publish failures to the caller.

**Rationale**: The commit already happened; rollback is impossible. Turning
a committed write into an apparent failure invites client retries and
duplicate publications. `ReactorChannelEventPublisher` already ignores the
`EmitResult`, which matches this model.

### Decision: Coalescing is at-most-once per window

**Choice**: One or more relevant invalidation signals arriving within the
coordinator coalescing window MUST cause at most one visible-range calendar
revalidation. If the cached range is stale and the scheduler stays active,
at least one canonical REST revalidation MUST eventually occur.

**Alternatives considered**: "Exactly one fetch per SSE frame".

**Rationale**: `BroadcastChannel` plus SSE routinely describe the same
mutation. One fetch per frame would double traffic by design. The
coordinator's debounce plus max-age ceiling absorbs `BC + SSE + SSE` into a
single `fetchCalendar`.

### Decision: Bounded SSE reconnect with visibility as safety net

**Choice**: While the scheduler is mounted and visible, reconnect with
`1s → 2s → 5s → 10s → max 30s + jitter`. While hidden, do not reconnect
aggressively. On `visibility → visible`, perform an immediate canonical
revalidation and restart the subscription.

**Alternatives considered**: Unbounded immediate reconnect; no reconnect
(the channel-events subscriber simply ends when the stream drops).

**Rationale**: SSE is not durable messaging; the visibility/max-age path is
the safety net. A bounded backoff keeps proxies happy without turning a
dead stream into a hot loop. The policy must exist in the spec before any
reconnect test is demanded.

### Decision: SSE is an input adapter, not a store responsibility

**Choice**:

```text
publication SSE infrastructure
       │
       │ PublicationInvalidation
       ▼
SchedulerView / publishing application composition
       │
       ▼
CalendarRevalidationCoordinator.request(currentRange)
       │
       ▼
publishingStore.fetchCalendar()
```

The store exposes `subscribePublicationEvents(onInvalidation)` /
`unsubscribePublicationEvents()`; it does not know the coordinator or the
URL-derived range.

**Alternatives considered**: Store receives SSE and calls the coordinator
directly.

**Rationale**: Keeps dependency direction clean and makes SSE one more
input adapter next to `BroadcastChannel` and visibility, not an extra
responsibility of publication state.

## Data Flow

```text
worker transition / handler mutation
        │
        │ transaction boundary completes successfully
        ▼
PublicationEventPublisher.publish(event)   [best effort, ignored on failure]
        │
        ▼
Sinks.many().multicast().directBestEffort
        │
        ▼
PublishingPublicationSseController.streamEvents()
  workspace = requireWorkspaceContext + requireActiveMember
  filter { it.workspaceId == workspace }
  merge(heartbeats every 20s)
        │
        ▼
SPA subscribePublicationEvents (fetch-streaming, Bearer + X-Workspace-Id)
        │
        ▼
SchedulerView composition → coordinator.request(currentRange)
        │
        ▼
publishingStore.fetchCalendar(from, to, filters)
  latestCalendarFetchId remains the sole stale-response guard
```

## Message Contract

Wire frame (SSE `data`):

```json
{
  "workspaceId": "workspace-aaa",
  "publicationId": "pub-123",
  "socialAccountId": "sa-linkedin-001",
  "changeType": "publication.status-changed",
  "occurredAt": "2026-09-25T12:00:00Z"
}
```

Kotlin domain:

```kotlin
enum class PublicationEventType {
    CREATED, UPDATED, RESCHEDULED, DELETED, STATUS_CHANGED
}

data class PublicationEvent(
    val type: PublicationEventType,
    val workspaceId: String,
    val publicationId: String,
    val socialAccountId: String?,
    val occurredAt: Instant,
)
```

Single mapping function `PublicationEventType.wireName()` produces the
dotted wire values. Blank `workspaceId` / `publicationId` are rejected at
construction or at the port boundary.

## Emitter Matrix (frozen)

| Origin | Successful state change | `changeType` (wire) |
|---|---|---|
| CreatePublicationHandler | publication created | `publication.created` |
| EditPublicationHandler | publication changed | `publication.updated` |
| ReschedulePublicationHandler | schedule changed | `publication.rescheduled` |
| CancelPublicationHandler | → CANCELLED | `publication.status-changed` |
| DeletePublicationHandler | removed, single atomic boundary per Option A | `publication.deleted` |
| RetryPublicationHandler | FAILED/BLOCKED → schedulable state | `publication.status-changed` |
| worker normal success | → PUBLISHED | `publication.status-changed` |
| worker recovered success (`finalizeRecoveredSuccess` → `markPublished`) | → PUBLISHED | `publication.status-changed` |
| worker terminal failure | → FAILED | `publication.status-changed` |
| worker reconnect block | → BLOCKED | `publication.status-changed` |
| worker ambiguous outcome | → BLOCKED | `publication.status-changed` |
| blocked recovery (`scanBlockedForRecovery` → requeue) | BLOCKED → queued/scheduled state | `publication.status-changed` |
| retryable provider attempt with no publication-visible state change | no event | — |

`PROCESSING` is explicitly excluded until the persisting write site for the
`QUEUED/SCHEDULED → PROCESSING` transition is identified (`markProcessing`
is defined in `PublishingPolicies.kt:101` with no located call site). The
spec MUST NOT promise `PROCESSING` reactivity.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublicationEventContracts.kt` | Create | `PublicationEvent`, `PublicationEventType`, `PublicationEventPublisher` port with `publish`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/events/ReactorPublicationEventPublisher.kt` | Create | Reactor adapter; `tryEmitNext` hidden inside; `EmitResult` ignored. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` | Modify | New `PublishingPublicationSseController.streamEvents()`; membership gate + workspace filter + heartbeat. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingPublicationHandlers.kt` | Modify | Post-commit `publish` from create/edit/delete/cancel/reschedule/retry handlers; delete consolidated into a single atomic boundary per decided Option A. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/CreatePublicationHandler.kt` | Modify | Post-commit `publish` for create path. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt` | Modify | Post-commit `publish` for success/failure/block/ambiguous/recovered/requeue paths. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingSchedulingConfiguration.kt` | Modify | Wire the publisher port into executor/worker beans. |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | Modify | Expose `subscribePublicationEvents(onInvalidation)` / `unsubscribePublicationEvents`; no coordinator knowledge. |
| `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | Modify | Own the SSE lifecycle; translate invalidations into `coordinator.request(currentRange)`. |
| `apps/web/app/src/modules/publishing/application/useCalendarRevalidation.ts` | Modify | No contract change; documented as the single funnel. |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.test.ts` | Create/Modify | Subscriber, coalescing, workspace-switch, disconnect tests (Vitest). |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/**` | Modify | Domain, adapter, controller, handler, worker, Modulith tests (Kotlin). |
| `apps/web/app/e2e/specs/scheduler-browser-sync.spec.ts` | Modify | SSE scenario driven from the route-backed SSE fixture only, no `BroadcastChannel` equivalent. |
| `apps/web/app/e2e/fixtures/scheduler-mocks.ts` | Modify | Route-backed `text/event-stream` handler. |
| `docs/compliance/data-inventory.md`, `docs/compliance/data-inventory.yaml` | Modify | Note the workspace-scoped stream; no new personal-data category. |
| `openspec/changes/publication-calendar-sse/` | Create | This change's audit trail. |

## Error and lifecycle handling

- Membership denial: non-member subscription attempts are denied before any
  event is observable; no workspace-B bytes ever reach the socket.
- Publish failure after commit: swallowed; business result stands; no HTTP
  status change; no retry of the business operation.
- Malformed/unknown `changeType` frames: ignored without logging payload
  contents; REST remains canonical.
- Disconnect while visible: bounded backoff reconnect; one coalesced refresh
  on reconnect through the coordinator.
- Disconnect while hidden: no aggressive reconnect; visibility return does
  an immediate canonical revalidation and restarts the subscription.
- Workspace switch: abort the old stream before the new one can deliver
  anything; the coordinator serves the latest range only.
- Unmount: abort subscription, clear timers, remove listeners.

## Testing Strategy

1. RED domain tests for event value semantics, enum exhaustiveness, and
   blank-identifier rejection before any production type.
2. RED adapter tests for delivery to a same-workspace subscriber and
   accepted backpressure drops.
3. RED controller tests with a fake registry: workspace filter, heartbeat,
   event-name mapping, membership denial.
4. RED handler/worker tests: one event per committed transition per the
   matrix; zero events on rollback; zero events for retryable attempts with
   no publication-visible state change; explicit recovered-success and
   blocked-recovery coverage.
5. RED Vitest subscriber tests: workspace match triggers one coordinator
   request; foreign frames ignored; heartbeats ignored; `BC + SSE + SSE`
   within the window yields at most one `fetchCalendar`; workspace switch
   aborts the old stream; disconnect schedules one coalesced refresh.
6. RED Playwright: route-backed SSE fixture emits the frame with no
   `BroadcastChannel` equivalent; tab B refetches `/calendar` and renders
   the new state.
7. Focused suites before broad gates; exact outcomes recorded in verify/QA.

## Open Decision (resolved: Option A)

Delete boundary is Option A: the delete plus its related side effects move
into a single atomic boundary, and `publication.deleted` is emitted
afterwards. This keeps the "server truth after successful mutation"
contract clean at the cost of widening scope to the delete path. Option B
(emit on `deleteUnpublished()` success regardless of the auxiliary
transaction) is rejected because it would bake a two-phase delete into the
contract.
