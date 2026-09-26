# Calendar Publication SSE Specification

## Purpose

Define a workspace-scoped Server-Sent Event stream that notifies the SPA of
publication state changes that originate outside the active tab's
BroadcastChannel reach: worker transitions, mutations in another tab,
mutations from another client, mutations originated by future orchestrators,
and worker retry/blocked/recovery events. REST remains the canonical
source of truth; SSE is progressive enhancement only.

The stream is metadata-only: it carries invalidation kind, workspace identity,
publication identity, optional social-account identity, and the moment the
event was produced. The receiver MUST refetch the visible range; it MUST
NOT apply payload fields as canonical publication state.

## Requirements

### Requirement: Workspace-Scoped Publication Event Stream

The system MUST expose `GET /api/publishing/publications/events` returning
a `Flux<ServerSentEvent<PublicationEventResponse>>` scoped to the requesting
principal's active workspace. The endpoint MUST require a Bearer token
and the `X-Workspace-Id` header that matches the authenticated workspace.

The endpoint MUST emit one event for every committed publication state
change in the active workspace. The endpoint MUST NOT emit events from other
workspaces.

#### Scenario: Authenticated client receives a publication event

- GIVEN an authenticated principal with a valid `X-Workspace-Id` header
- AND the client subscribes via fetch-streaming with Bearer Authorization
- WHEN a publication in the active workspace is created, updated,
  rescheduled, deleted, or transitions to a new status
- THEN the system MUST emit a publication event for that workspace
- AND the event payload MUST contain only `workspaceId`, `publicationId`,
  optional `socialAccountId`, `changeType`, and `occurredAt`
- AND the SPA MUST treat the event as a trigger to refetch the visible
  calendar range

#### Scenario: Foreign workspace events are filtered

- GIVEN an authenticated principal in workspace A with a valid stream
  subscription
- WHEN a publication event is emitted for workspace B
- THEN workspace A's stream MUST NOT deliver that event

#### Scenario: Authenticated non-member cannot subscribe to another workspace

- GIVEN principal P is authenticated
- AND P is not an active member of workspace B
- WHEN P requests the publication event stream with `X-Workspace-Id` = B
- THEN the request MUST be denied
- AND no workspace B event MUST be observable

#### Scenario: Native EventSource is rejected

- GIVEN a client connects using native `EventSource` (no Bearer header)
- WHEN the server processes the request
- THEN the server MUST return 401
- AND the SPA MUST NOT attempt native `EventSource` for this endpoint

### Requirement: SSE Uses Fetch Streaming with Bearer Auth

The SSE endpoint MUST be consumed via fetch-streaming with explicit
`Authorization: Bearer <token>` header and the `X-Workspace-Id` header.
Native `EventSource` MUST NOT be required because it cannot send custom
Authorization headers.

#### Scenario: Fetch streaming with Bearer token succeeds

- GIVEN the client opens a fetch request with `Authorization: Bearer <token>`
  and `X-Workspace-Id`
- WHEN the server processes the request
- THEN the connection MUST be accepted and event streaming MUST begin

### Requirement: Closed changeType Vocabulary

The event payload MUST use a closed `changeType` vocabulary on the wire:

- `publication.created`
- `publication.updated`
- `publication.rescheduled`
- `publication.deleted`
- `publication.status-changed`

The Kotlin enum is `PublicationEventType` with `CREATED, UPDATED,
RESCHEDULED, DELETED, STATUS_CHANGED`, mapped by a single function to the
dotted wire values above. The server MUST NOT emit any other `changeType`
value. Receivers MUST ignore unknown values without logging payload
contents.

#### Scenario: Worker status transition uses publication.status-changed

- GIVEN a worker commits a terminal transition to `PUBLISHED`, `FAILED`,
  or `BLOCKED` (including recovered success and ambiguous outcome)
- WHEN each transition commits
- THEN the server MUST emit one event per committed transition with
  `changeType = publication.status-changed`

`PROCESSING` reactivity is explicitly excluded until the persisting write
site for the `QUEUED/SCHEDULED → PROCESSING` transition is identified.

#### Scenario: User edit uses publication.updated

- GIVEN an authenticated principal commits an edit through
  `PATCH /api/publishing/publications/{publicationId}`
- WHEN the edit commits
- THEN the server MUST emit one event with `changeType = publication.updated`

#### Scenario: User reschedule uses publication.rescheduled

- GIVEN an authenticated principal commits a reschedule through
  `PATCH /api/publishing/publications/{publicationId}/reschedule`
- WHEN the reschedule commits
- THEN the server MUST emit one event with `changeType = publication.rescheduled`

#### Scenario: User cancel or delete uses the matching changeType

- GIVEN an authenticated principal cancels a publication through
  `POST /api/publishing/publications/{publicationId}/cancel`
- OR deletes a publication through
  `DELETE /api/publishing/publications/{publicationId}`
- WHEN the operation commits
- THEN the server MUST emit one event with
  `changeType = publication.status-changed` (cancel) or
  `changeType = publication.deleted` (delete)

#### Scenario: Blocked recovery emits a status event

- GIVEN a `BLOCKED` publication is requeued by the blocked-recovery scan
- WHEN the requeue transaction commits
- THEN the server MUST emit one event with
  `changeType = publication.status-changed`

#### Scenario: Retryable attempt without publication-visible change emits nothing

- GIVEN a worker attempt fails retryably but no publication-visible state
  transition is persisted
- WHEN the attempt handling completes
- THEN the server MUST emit zero publication events

#### Scenario: User create uses publication.created

- GIVEN an authenticated principal creates a publication through
  `POST /api/publishing/publications` or
  `POST /api/publishing/publications/quick-create`
- WHEN the creation commits
- THEN the server MUST emit one event with `changeType = publication.created`

### Requirement: Events Are Emitted Only After Commit

The server MUST emit the publication event only after the publishing
transaction commits. If the transaction rolls back, the server MUST NOT
emit any event for that operation.

#### Scenario: Rolled-back transaction emits no event

- GIVEN a publication write or worker transition that throws inside the
  transaction runner
- WHEN the transaction runner reports a rollback
- THEN the server MUST NOT call `PublicationEventPublisher.publish(...)`
  for that operation

### Requirement: Publisher Failure Never Fails the Business Operation

SSE publication is best effort. Failure to emit an invalidation after a
committed publication MUST NOT roll back the publication, MUST NOT
transform a successful mutation into a failed mutation, and MUST NOT make
SSE authoritative.

#### Scenario: Publish failure after commit keeps the committed result

- GIVEN a publication transaction commits successfully
- WHEN the subsequent `publish` call throws or reports a dropped emission
- THEN the committed publication MUST stand
- AND the originating REST call MUST keep its success status
- AND no retry of the business operation MUST be triggered by the
  emission failure

### Requirement: Heartbeat and Reconnection

The server MUST emit a `heartbeat` event at a fixed cadence (20 seconds)
so HTTP intermediaries and load balancers keep the connection alive. The
heartbeat payload MUST be empty. The SPA MUST tolerate heartbeats without
triggering a refetch.

#### Scenario: Heartbeat keeps the connection alive

- GIVEN the client holds an open SSE connection
- WHEN no publication event is emitted for 20 seconds
- THEN the server MUST send a `heartbeat` event with an empty payload

#### Scenario: SPA ignores heartbeats

- GIVEN the client receives a `heartbeat` SSE frame
- WHEN the SPA parses the frame
- THEN the SPA MUST NOT trigger a `fetchCalendar` call

### Requirement: SSE Failure Is Non-Critical for Calendar Correctness

Calendar correctness MUST depend on REST and visibility/focus
revalidation. SSE event loss, latency, disconnect, or endpoint failure MUST
NOT cause calendar state inconsistency. The SPA MUST continue to operate
when the SSE endpoint is unavailable.

#### Scenario: SSE endpoint failure does not break calendar listing

- GIVEN the SSE endpoint is unavailable or returns an error
- WHEN the SPA fetches the calendar via REST
- THEN the calendar MUST still load correctly
- AND the SPA MUST continue to handle the existing same-browser
  BroadcastChannel and visibility revalidation

#### Scenario: SPA reconnects with bounded backoff after disconnect

- GIVEN the SSE connection drops unexpectedly while the scheduler is
  mounted and visible
- WHEN the SPA observes the disconnect
- THEN the SPA MUST reconnect with backoff `1s → 2s → 5s → 10s → max 30s
  + jitter`
- AND it MUST schedule at most one coalesced `fetchCalendar` call for the
  current visible range on reconnect

#### Scenario: Hidden scheduler does not reconnect aggressively

- GIVEN the SSE connection drops while the scheduler is hidden
- WHEN the tab stays hidden
- THEN the SPA MUST NOT reconnect aggressively
- AND on `visibility → visible` it MUST perform an immediate canonical
  revalidation and restart the subscription

### Requirement: Cross-Tab Duplication Is Idempotent

When the same mutation is observed via both `BroadcastChannel` and SSE,
the SPA MUST NOT trigger more than one calendar fetch for the same visible
range. The existing `CalendarRevalidationCoordinator` coalescing window
and max-age ceiling MUST absorb duplicates.

#### Scenario: Same mutation via both paths produces at most one fetch

- GIVEN the SPA receives a `publication.updated` SSE event for the visible
  range
- AND a same-workspace `BroadcastChannel` message arrives within the
  coordinator's coalescing window
- WHEN both triggers fire
- THEN the SPA MUST perform at most one `fetchCalendar` call
- AND if the cached range is stale while the scheduler stays active, at
  least one canonical REST revalidation MUST eventually occur

### Requirement: Heartbeat and reconnection in dev tools

For development convenience the SSE endpoint MUST be discoverable through
the same Swagger metadata surface as the channel-events endpoint. The
endpoint MUST NOT be exposed in production gateway metadata when
`management.endpoints` security requires authentication (default-deny
already enforced by `IdentitySecurityConfiguration`).

#### Scenario: Production gateway rejects anonymous SSE

- GIVEN an unauthenticated client opens `GET /api/publishing/publications/events`
- WHEN the server processes the request
- THEN the server MUST return 401 (default-deny)
