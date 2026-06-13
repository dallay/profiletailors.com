# Channel Events SSE Specification

## Purpose

Define a one-way server-sent event stream that notifies the SPA of connected-channel state changes, using fetch-streaming with Bearer auth. REST remains the canonical source of truth; SSE is progressive enhancement only.

## Requirements

### Requirement: SSE Channel Change Notification Stream

The system MUST expose `GET /api/publishing/channels/events` returning a `Flux<ServerSentEvent<ChannelEvent>>` scoped to the requesting principal's active workspace.

Each event MUST carry a typed payload: `connected-channel.updated` or `connected-channel.removed`. The event payload MUST contain only a safe summary or a signal instructing the SPA to refetch the canonical channel list. Channel correctness MUST NOT depend on SSE delivery.

#### Scenario: Authenticated client receives channel change event

- GIVEN an authenticated principal with a valid `X-Workspace-Id` header
- AND the client subscribes via fetch-streaming with Bearer Authorization
- WHEN a connected channel is updated in the active workspace
- THEN the system MUST emit a `connected-channel.updated` event
- AND the SPA MUST treat the event as a trigger to refetch `GET /api/publishing/channels`

#### Scenario: Channel removal triggers removal event

- GIVEN a connected channel is removed in the active workspace
- WHEN the removal is persisted
- THEN the system MUST emit a `connected-channel.removed` event

### Requirement: SSE Uses Fetch Streaming with Bearer Auth

The SSE endpoint MUST be consumed via fetch-streaming with explicit `Authorization: Bearer` header. Native `EventSource` MUST NOT be required because it cannot send custom Authorization headers.

#### Scenario: Fetch streaming with Bearer token succeeds

- GIVEN the client opens a fetch request with `Authorization: Bearer <token>` and `X-Workspace-Id`
- WHEN the server processes the request
- THEN the connection MUST be accepted and event streaming MUST begin

#### Scenario: Native EventSource without auth is rejected

- GIVEN a client connects using native `EventSource` (no Bearer header)
- WHEN the server processes the request
- THEN the server MUST return 401
- AND the SPA MUST NOT attempt native `EventSource` for this endpoint

### Requirement: SSE Is Non-Critical for Channel Correctness

Channel list correctness MUST depend solely on the REST endpoint `GET /api/publishing/channels`. SSE event loss, latency, or endpoint failure MUST NOT cause channel state inconsistency.

#### Scenario: SSE endpoint failure does not break channel listing

- GIVEN the SSE endpoint is unavailable or returns an error
- WHEN the SPA fetches channels via REST
- THEN the channel list MUST still load correctly
- AND the SPA MAY display channels without real-time updates

#### Scenario: SPA refetches canonical list on SSE event receipt

- GIVEN the SPA receives a `connected-channel.updated` SSE event
- WHEN the SPA processes the event
- THEN the SPA MUST call `GET /api/publishing/channels` to refresh its canonical channel list
- AND it MUST NOT apply the SSE payload directly as the source of truth
