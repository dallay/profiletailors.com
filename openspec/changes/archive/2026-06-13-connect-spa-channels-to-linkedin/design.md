# Design: Connect SPA Channels to LinkedIn

## Overview

This change connects the Vue SPA publishing channel UI to real workspace-scoped LinkedIn
personal-profile connections in the Spring Boot backend. The backend remains the canonical source of
channel truth through REST. A fetch-streaming SSE endpoint is added as a non-critical notification
channel that tells the SPA when to re-fetch the canonical channel list.

The implementation follows the existing mandatory hexagonal architecture:

- **Domain**: pure Kotlin models, ports, and policies. No Spring annotations or infrastructure
  dependencies.
- **Application**: command/query DTOs and handlers annotated with
  `com.profiletailors.common.domain.Service`; handlers resolve principal/workspace context and
  orchestrate ports.
- **Infrastructure**: Spring WebFlux controllers, R2DBC adapters, LinkedIn provider adapters, and
  SSE publisher/subscriber plumbing.
- **Frontend**: Vue 3 + TypeScript Pinia store replaces mock channels with backend-loaded channel
  read models, adds OAuth initiation/callback actions, and subscribes to fetch-streamed SSE with
  Bearer auth.

MVP supports **LinkedIn personal profiles only**, while keeping provider/account DTOs
provider-neutral so LinkedIn organization pages and other providers can be added later without
breaking contract shape.

## Key Design Decisions

1. **REST is canonical**: `GET /api/publishing/channels` is the source of truth. SSE only triggers
   refreshes.
2. **Use fetch-streaming SSE, not native `EventSource`**: the SPA stores access tokens in memory and
   authenticates API calls with `Authorization: Bearer`. Native `EventSource` cannot send arbitrary
   Authorization headers, so the client will use `fetch()` with `Accept: text/event-stream`,
   `Authorization`, and `X-Workspace-Id` headers, then parse the event stream.
3. **Signed stateless OAuth state for MVP**: initiation returns a compact HMAC-signed state
   containing workspace, principal, nonce, redirect path, issued/expiry timestamps, and provider.
   Completion validates signature, expiry, provider, principal, and workspace before exchanging the
   code. This avoids a new state table for MVP while remaining tamper-evident.
4. **Provider-neutral channel read model**: API fields include `provider` and `accountKind`;
   LinkedIn organization pages remain future-compatible even though initiation/completion only
   create `PERSONAL_PROFILE` accounts now.
5. **Idempotent reconnect**: R2DBC `upsert` methods must use PostgreSQL
   `ON CONFLICT ... DO UPDATE ... RETURNING ...` semantics against existing unique constraints.

## Backend Architecture

### Package and Layer Changes

#### Domain: `com.profiletailors.smp.publishing.domain`

Keep existing models:

- `SocialConnection`
- `SocialAccount`
- `SocialProvider`
- `SocialAccountKind`
- `SocialConnectionStatus`

Add pure Kotlin domain/read ports and value types:

```kotlin
interface ConnectedSocialChannelReadRepository {
    suspend fun listByWorkspace(
        workspaceId: String,
        statuses: Set<SocialConnectionStatus> = setOf(SocialConnectionStatus.ACTIVE),
    ): List<ConnectedSocialChannel>
}

data class ConnectedSocialChannel(
    val socialAccountId: String,
    val connectionId: String,
    val provider: SocialProvider,
    val accountKind: SocialAccountKind,
    val displayName: String,
    val status: SocialConnectionStatus,
    val profileUrn: String?,
    val connectedAt: Instant?,
    val lastSyncedAt: Instant?,
)
```

Add OAuth initiation/validation ports without Spring dependencies:

```kotlin
interface OAuthStateSigner {
    fun sign(payload: LinkedInOAuthStatePayload): String
    fun verify(state: String): LinkedInOAuthStatePayload
}

data class LinkedInOAuthStatePayload(
    val provider: SocialProvider,
    val workspaceId: String,
    val principalId: String,
    val redirectUri: String,
    val nonce: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

interface LinkedInAuthorizationUrlBuilder {
    fun buildAuthorizationUrl(state: String, redirectUri: String): String
    fun isConfigured(): Boolean
}
```

Add event notification port:

```kotlin
interface ChannelEventPublisher {
    fun publish(event: ChannelEvent)
}

data class ChannelEvent(
    val type: ChannelEventType,
    val workspaceId: String,
    val socialAccountId: String?,
    val occurredAt: Instant,
)

enum class ChannelEventType {
    CONNECTED_CHANNEL_UPDATED,
    CONNECTED_CHANNEL_REMOVED,
}
```

Domain remains free of Reactor/Spring; `ChannelEventPublisher` is a simple outbound port.
Infrastructure can implement it with a Reactor sink.

#### Application: `com.profiletailors.smp.publishing.application`

Extend `PublishingApi.kt` with:

```kotlin
data class InitiateLinkedInConnectionCommand(
    val redirectUri: String? = null,
) : CommandWithResult<LinkedInConnectionInitiationResult>

data class LinkedInConnectionInitiationResult(
    val authorizationUrl: String,
    val state: String,
    val expiresAt: Instant,
)

data class ListConnectedChannelsQuery(
    val status: SocialConnectionStatus? = SocialConnectionStatus.ACTIVE,
) : Query<ConnectedChannelsResponse>

data class ConnectedChannelsResponse(
    val channels: List<ConnectedSocialChannelSummary>,
)

data class ConnectedSocialChannelSummary(
    val socialAccountId: String,
    val connectionId: String,
    val provider: SocialProvider,
    val accountKind: SocialAccountKind,
    val displayName: String,
    val status: SocialConnectionStatus,
    val profileUrn: String?,
    val connectedAt: Instant?,
    val lastSyncedAt: Instant?,
)
```

Modify existing completion command:

```kotlin
data class CompleteLinkedInConnectionCommand(
    val authorizationCode: String,
    val redirectUri: String,
    val state: String,
) : CommandWithResult<SocialConnectionResult>
```

Add handlers in `PublishingHandlers.kt`:

- `InitiateLinkedInConnectionHandler`
    - Requires authenticated principal.
    - Requires active workspace via `resourceContextProvider.requireWorkspaceContext()`.
    - Resolves redirect URI from command or LinkedIn properties default.
    - Verifies LinkedIn provider is configured; otherwise throws a mapped provider-not-configured
      exception.
    - Builds signed state payload with short TTL, e.g. 10 minutes.
    - Builds authorization URL via outbound port.
    - Returns `authorizationUrl`, `state`, `expiresAt`.

- `ListConnectedChannelsHandler`
    - Requires active workspace.
    - Calls `ConnectedSocialChannelReadRepository.listByWorkspace`.
    - Maps domain read rows to `ConnectedSocialChannelSummary`.
    - Defaults to `ACTIVE` only.

- Modify `CompleteLinkedInConnectionHandler`
    - Validates `state` before provider code exchange.
    - State validation must happen before calling `socialConnectionProvider.completeConnection`.
    - Requires state provider `LINKEDIN`, same principal, same workspace, same redirect URI, and
      non-expired timestamp.
    - Persists connection/account using idempotent repository `upsert` methods.
    - Publishes `CONNECTED_CHANNEL_UPDATED` after successful account persistence.

Application exceptions to introduce:

- `ProviderNotConfiguredException` -> HTTP 503.
- `InvalidOAuthStateException` -> HTTP 400.
- `ExpiredOAuthStateException` -> HTTP 400.

Use existing global exception mapping if present; otherwise map in controller with
`ResponseStatusException` or a local exception handler consistent with current conventions.

#### Infrastructure HTTP: `com.profiletailors.smp.publishing.infrastructure.http`

Update `PublishingConnectionController`:

- `POST /api/publishing/linkedin/connections/initiate`
- `POST /api/publishing/linkedin/connections/complete`

Request/response DTOs:

```json
POST /api/publishing/linkedin/connections/initiate
{
  "redirectUri": "http://localhost:5173/integrations/linkedin/callback"
}
```

Response:

```json
{
  "authorizationUrl": "https://www.linkedin.com/oauth/v2/authorization?...",
  "state": "base64url.payload.signature",
  "expiresAt": "2026-06-12T12:10:00Z"
}
```

Completion request:

```json
POST /api/publishing/linkedin/connections/complete
{
  "authorizationCode": "AQ...",
  "redirectUri": "http://localhost:5173/integrations/linkedin/callback",
  "state": "base64url.payload.signature"
}
```

Response remains `SocialConnectionResult` for compatibility, with safe account metadata only.

Add `PublishingChannelController`:

```kotlin
@RestController
@RequestMapping(value = ["/api/publishing/channels"])
class PublishingChannelController(private val mediator: Mediator) {
    @GetMapping(version = "1")
    suspend fun listChannels(@RequestParam(required = false) status: SocialConnectionStatus?): ConnectedChannelsResponse

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE], version = "1")
    fun streamEvents(): Flux<ServerSentEvent<ChannelEventResponse>>
}
```

The events endpoint authenticates through existing Spring Security and workspace resolution filters.
It must reject requests without Bearer auth and without `X-Workspace-Id`.

Event response shape:

```json
{
  "type": "connected-channel.updated",
  "workspaceId": "workspace-1",
  "socialAccountId": "soacc-...",
  "occurredAt": "2026-06-12T12:00:00Z"
}
```

Use SSE event names:

- `connected-channel.updated`
- `connected-channel.removed`
- `heartbeat`

#### Infrastructure LinkedIn

Extend `LinkedInPublishingProperties` or add a dedicated OAuth properties class to include:

- `mode`
- `clientId`
- `clientSecret`
- `redirectUri`
- `scopes`
- `authorizationBaseUrl`
- `stateSigningSecret` or shared application secret
- `stateTtl`, default `PT10M`

Add infrastructure implementations:

- `HmacOAuthStateSigner`
    - JSON serialize payload.
    - Base64url encode payload.
    - Sign with HMAC-SHA256.
    - Verify using constant-time comparison.
    - Reject malformed, unsigned, tampered, or expired payloads.

- `LinkedInAuthorizationUrlBuilderAdapter`
    - Builds authorization URL with:
        - `response_type=code`
        - `client_id`
        - `redirect_uri`
        - `scope`
        - `state`
    - URL-encodes all query values.
    - `isConfigured()` returns false if client ID, redirect URI, or scopes are missing in real mode.

Fake mode should still return deterministic fake provider completion, but initiation should generate
a structurally valid authorization URL against configured/fake authorization base URL so frontend
flow can be exercised locally.

#### Infrastructure Persistence

Existing migrations already provide required unique constraints:

- `social_connections`: `workspace_id, provider, provider_connection_ref`
- `social_accounts`: `workspace_id, provider, provider_account_id`

No schema migration is required for the MVP channel list and upsert fix.

Update `R2dbcSocialConnectionRepository.upsert`:

- Use `ON CONFLICT (workspace_id, provider, provider_connection_ref) DO UPDATE`.
- Update:
    - `status`
    - `credential_reference`
    - `connected_at`
    - `last_synced_at`
- Return the persisted row using `RETURNING ...` to preserve the original `id` on reconnect.

Update `R2dbcSocialAccountRepository.upsert`:

- Use `ON CONFLICT (workspace_id, provider, provider_account_id) DO UPDATE`.
- Update:
    - `social_connection_id`
    - `account_type`
    - `display_name`
    - `profile_urn`
    - `status`
- Return the persisted row using `RETURNING ...` to preserve the original `id` on reconnect.

Add `R2dbcConnectedSocialChannelReadRepository`:

```sql
SELECT
  a.id AS social_account_id,
  a.social_connection_id AS connection_id,
  a.provider,
  a.account_type,
  a.display_name,
  a.status,
  a.profile_urn,
  c.connected_at,
  c.last_synced_at
FROM social_accounts a
JOIN social_connections c
  ON c.id = a.social_connection_id
 AND c.workspace_id = a.workspace_id
WHERE a.workspace_id = :workspaceId
  AND a.status IN (:statuses)
  AND c.status IN (:statuses)
ORDER BY c.connected_at DESC NULLS LAST, a.created_at DESC
```

Do not select or map `credential_reference` into public read models.

#### Infrastructure Events

Implement an in-memory Reactor sink as the MVP event bus:

- `ReactorChannelEventPublisher : ChannelEventPublisher`
- `ChannelEventStreamRegistry` or equivalent component exposing `Flux<ChannelEvent>`.
- Use `Sinks.many().multicast().directBestEffort<ChannelEvent>()` or similar.
- The SSE controller filters events by the active workspace ID from `ResourceContextProvider`.
- Add a heartbeat event every 15–30 seconds using `Flux.interval` to keep proxies from closing idle
  streams.
- Events are best-effort and not persisted. Missing events are acceptable because the REST channel
  list is canonical.

For multi-instance deployments, replace this in-memory sink later with Postgres LISTEN/NOTIFY, Redis
pub/sub, or a durable outbox. Do not over-engineer this for MVP.

## API Contracts

### `GET /api/publishing/channels`

Headers:

- `Authorization: Bearer <access-token>`
- `X-Workspace-Id: <workspace-id>`
- `Accept: application/vnd.api.v1+json`

Query parameters:

- `status` optional. Defaults to `ACTIVE`. MVP supports `ACTIVE`; future values may be added.

Response:

```json
{
  "channels": [
    {
      "socialAccountId": "soacc-123",
      "connectionId": "soconn-456",
      "provider": "LINKEDIN",
      "accountKind": "PERSONAL_PROFILE",
      "displayName": "Yuniel Acosta Pérez",
      "status": "ACTIVE",
      "profileUrn": "urn:li:person:abc",
      "connectedAt": "2026-06-12T12:00:00Z",
      "lastSyncedAt": null
    }
  ]
}
```

Errors:

- `401` unauthenticated.
- `400` missing workspace context.

### `POST /api/publishing/linkedin/connections/initiate`

Headers:

- `Authorization: Bearer <access-token>`
- `X-Workspace-Id: <workspace-id>`
- `Content-Type: application/json`
- `Accept: application/vnd.api.v1+json`

Request:

```json
{
  "redirectUri": "http://localhost:5173/integrations/linkedin/callback"
}
```

Response:

```json
{
  "authorizationUrl": "https://www.linkedin.com/oauth/v2/authorization?response_type=code&client_id=...&redirect_uri=...&scope=openid%20profile&state=...",
  "state": "eyJ...signature",
  "expiresAt": "2026-06-12T12:10:00Z"
}
```

Errors:

- `401` unauthenticated.
- `400` missing workspace context or invalid redirect URI.
- `503` LinkedIn provider not configured.

### `POST /api/publishing/linkedin/connections/complete`

Headers:

- `Authorization: Bearer <access-token>`
- `X-Workspace-Id: <workspace-id>`
- `Content-Type: application/json`
- `Accept: application/vnd.api.v1+json`

Request:

```json
{
  "authorizationCode": "AQ...",
  "redirectUri": "http://localhost:5173/integrations/linkedin/callback",
  "state": "eyJ...signature"
}
```

Response: existing `SocialConnectionResult`.

Errors:

- `400` invalid/missing/expired state, redirect mismatch, provider mismatch.
- `401` unauthenticated.
- `400` missing workspace context.
- Provider exchange errors should be mapped to a safe client error or gateway error without leaking
  token endpoint details.

### `GET /api/publishing/channels/events`

Headers:

- `Authorization: Bearer <access-token>`
- `X-Workspace-Id: <workspace-id>`
- `Accept: text/event-stream`

Stream format:

```text
event: connected-channel.updated
data: {"type":"connected-channel.updated","workspaceId":"workspace-1","socialAccountId":"soacc-123","occurredAt":"2026-06-12T12:00:00Z"}

```

Client behavior:

- On `connected-channel.updated` or `connected-channel.removed`, call `fetchChannels()`.
- On stream failure, log or surface non-blocking status and continue using REST.
- Do not use native `EventSource` because it cannot set Bearer Authorization headers.

## OAuth State Strategy

State payload fields:

| Field         | Purpose                                                  |
|---------------|----------------------------------------------------------|
| `provider`    | Must be `LINKEDIN`; prevents cross-provider replay.      |
| `workspaceId` | Ensures callback completes in the same active workspace. |
| `principalId` | Binds the flow to the initiating user.                   |
| `redirectUri` | Prevents code/state reuse with another callback URI.     |
| `nonce`       | Adds uniqueness and replay resistance within TTL.        |
| `issuedAt`    | Audit/debug field.                                       |
| `expiresAt`   | Short-lived CSRF window, default 10 minutes.             |

Validation order in `CompleteLinkedInConnectionHandler`:

1. Require principal and workspace context.
2. Verify HMAC signature and parse payload.
3. Check `expiresAt > clock.instant()`.
4. Check `provider == LINKEDIN`.
5. Check payload `workspaceId` equals active workspace.
6. Check payload `principalId` equals current principal.
7. Check payload `redirectUri` equals request `redirectUri`.
8. Only then call LinkedIn provider to exchange code.

Replay note: signed stateless state does not fully prevent replay inside the TTL if the
authorization code has not already been consumed. LinkedIn authorization codes are single-use and
short-lived; this is acceptable for MVP. If stricter replay guarantees are needed, add an
`oauth_connection_states` table with nonce consumption.

## Frontend Design

### Workspace Header Injection

Update `apps/web/app/src/lib/auth-api.ts` and `apps/web/app/src/stores/auth.ts` so `createApiFetch`
can receive an optional workspace ID provider and a per-request workspace requirement option.

Recommended shape:

```ts
type ApiFetchOptions = RequestInit & {
  workspaceScoped?: boolean
}

createApiFetch({
  getToken,
  getWorkspaceId,
  onRefresh,
  onUnauthenticated,
})
```

Behavior:

- For workspace-scoped API calls, require `getWorkspaceId()`.
- If missing, throw an `ApiError` with a clear `Workspace context is required` detail before sending
  the request.
- Inject `X-Workspace-Id` into headers when present.
- Keep `Authorization: Bearer` and `credentials: 'include'` behavior unchanged.

Because no dedicated workspace store exists in the inspected frontend, implementation should either:

1. Introduce a small `workspace` store with `activeWorkspaceId`; or
2. Add an interim active workspace resolver in auth/session state if the backend currently returns
   only one workspace.

Do not hard-code workspace IDs in production code. Tests may use fake workspace IDs.

### Publishing Store Changes

Update `apps/web/app/src/stores/publishing.ts`:

- Replace initial mock `channels` array with `ref<Channel[]>([])`.
- Add backend DTO types:

```ts
interface ConnectedSocialChannelSummary {
  socialAccountId: string
  connectionId: string
  provider: 'LINKEDIN' | string
  accountKind: 'PERSONAL_PROFILE' | 'ORGANIZATION_PAGE' | string
  displayName: string
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED' | 'ERROR' | string
  profileUrn: string | null
  connectedAt: string | null
  lastSyncedAt: string | null
}

interface ConnectedChannelsResponse {
  channels: ConnectedSocialChannelSummary[]
}
```

- Add state:
    - `channelsLoading`
    - `channelsError`
    - `channelEventsConnected`
    - `channelEventsAbortController`

- Add actions:
    - `fetchChannels()`
    - `connectLinkedInPersonalProfile()`
    - `completeLinkedInConnectionFromCallback({ code, state, redirectUri })`
    - `subscribeChannelEvents()`
    - `unsubscribeChannelEvents()`

Channel mapping:

```ts
function apiChannelToChannel(api: ConnectedSocialChannelSummary): Channel {
  return {
    id: api.socialAccountId,
    accountId: api.socialAccountId,
    name: api.displayName,
    provider: api.provider.toLowerCase() as Channel['provider'],
    avatar: '',
    handle: api.profileUrn ?? api.displayName,
    status: api.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE',
  }
}
```

Scheduling behavior:

- If a user schedules for LinkedIn and no active LinkedIn channel exists, block submission and
  surface a connect CTA/error.
- Never fall back to `account-linkedin-mock` for authenticated backend calls.
- Use the selected channel's `accountId` as `socialAccountId`.
- Local unauthenticated demo behavior may keep local publications, but authenticated real backend
  paths must use real account IDs.

### OAuth Callback Route/View

Add route in `apps/web/app/src/router/index.ts`:

```ts
{
  path: '/integrations/linkedin/callback',
  name: 'linkedin-callback',
  component: () => import('../views/LinkedInCallbackView.vue'),
  meta: { requiresAuth: true },
}
```

Add `apps/web/app/src/views/LinkedInCallbackView.vue`:

- Reads query parameters:
    - `code`
    - `state`
    - `error`
    - `error_description`
- If `error` exists, show user-friendly denial message and do not call backend.
- If `code` or `state` missing, show validation error and retry button.
- Compute `redirectUri` from `window.location.origin + '/integrations/linkedin/callback'`.
- Calls `publishing.completeLinkedInConnectionFromCallback`.
- On success, calls `fetchChannels()` before navigation.
- Navigates to `/scheduler` or `/settings` with success feedback.
- On failure, shows safe error and a retry CTA invoking initiation flow.

### Settings / Sidebar UI

- Add a LinkedIn connection CTA in settings and/or the empty channel state.
- Sidebar channel list should render backend-loaded channels only.
- Empty state should say no channels are connected and offer `Connect LinkedIn profile`.
- Disable unsupported providers or label them `Coming soon` rather than displaying fake
  Twitter/Instagram channels.

### Fetch-Streaming SSE Client

Implement with `fetch`, not `EventSource`:

```ts
const response = await auth.apiFetchRaw('/api/publishing/channels/events', {
  method: 'GET',
  headers: { Accept: 'text/event-stream' },
  workspaceScoped: true,
  signal: abortController.signal,
})
```

If `apiFetch` only returns JSON today, either:

- Add `apiFetchStream` / `apiFetchRaw` for streaming responses, or
- Add an option such as `responseType: 'stream'`.

Parser responsibilities:

- Read `response.body.getReader()`.
- Decode chunks with `TextDecoder`.
- Parse SSE frames separated by blank lines.
- For event names `connected-channel.updated` and `connected-channel.removed`, call
  `fetchChannels()`.
- Ignore malformed event payloads.
- Reconnect with backoff if desired, but avoid infinite tight loops.

## Security Considerations

- Provider access/refresh tokens must remain in credential infrastructure only and never appear in
  REST or SSE responses.
- OAuth completion must validate state before exchanging authorization code to avoid CSRF and
  confused-deputy flows.
- State signature verification must use constant-time signature comparison.
- State TTL should be short, e.g. 10 minutes.
- Redirect URI should be exact-match validated against the value embedded in state and, where
  possible, against configured allowed callback origins.
- All workspace-scoped routes require `X-Workspace-Id` and must rely on existing workspace context
  validation.
- Fetch-streaming SSE carries Bearer tokens in headers and should be treated like any authenticated
  API request. Do not support token-in-query SSE URLs.
- SSE payloads must include safe metadata only; preferably only IDs and timestamps.
- Frontend must not store OAuth state or access tokens in localStorage. Existing in-memory
  access-token behavior remains preferred.
- Logs must not include authorization codes, access tokens, refresh tokens, or full signed state
  payloads.

## Test Strategy

### Backend Application Tests

Extend `PublishingHandlersTest` with:

- Lists active connected channels for workspace.
- Excludes revoked/expired/error channels by default.
- Initiates LinkedIn connection with signed state and authorization URL.
- Initiation fails when provider is not configured.
- Completion rejects missing/tampered/expired state before provider exchange.
- Completion rejects state for another workspace/principal/redirect URI.
- Completion with valid state persists connection/account and publishes channel event.
- Reconnecting same profile returns preserved IDs from upsert repositories where in-memory fakes
  emulate conflict behavior.

### Backend Controller Tests

Extend `PublishingControllersTest` with:

- `POST /initiate` dispatches `InitiateLinkedInConnectionCommand`.
- `POST /complete` includes `state` in `CompleteLinkedInConnectionCommand`.
- `GET /api/publishing/channels` dispatches `ListConnectedChannelsQuery` with default/filtered
  status.
- SSE controller can be unit-tested against a fake event stream if controller construction stays
  lightweight.

### Backend R2DBC Tests

Extend `R2dbcPublishingRepositoriesTest` or `R2dbcPublishingRepositoriesUnitTest`:

- Social connection upsert inserts then updates on
  `(workspace_id, provider, provider_connection_ref)` conflict.
- Social account upsert inserts then updates on `(workspace_id, provider, provider_account_id)`
  conflict.
- Upsert returns the persisted original ID on conflict.
- Channel read repository returns active joined account/connection rows.
- Channel read repository does not expose `credential_reference`.
- Workspace isolation: channels from another workspace are not returned.

### Backend Integration Tests

- Controller/security path for `GET /api/publishing/channels` with auth + workspace header returns
    200.
- Missing workspace header returns 400.
- Missing Bearer token returns 401.
- SSE endpoint rejects missing Bearer token.
- Completion end-to-end in fake LinkedIn mode results in channel list containing the new profile.

### Frontend Unit Tests

Update `apps/web/app/src/stores/publishing.test.ts`:

- Initial authenticated channel state is empty, not mock-seeded.
- `fetchChannels()` maps backend channels to store channels.
- `fetchChannels()` handles empty list and errors.
- `connectLinkedInPersonalProfile()` calls initiation endpoint and redirects to returned
  authorization URL.
- `completeLinkedInConnectionFromCallback()` forwards state unchanged and refreshes channels.
- `schedulePost()` uses selected real channel `accountId` and never uses `account-linkedin-mock`.
- Scheduling with no LinkedIn channel surfaces an error/throws.
- `subscribeChannelEvents()` uses fetch-streaming with Bearer/workspace headers and calls
  `fetchChannels()` on channel events.

Add tests for `auth-api.ts`:

- Workspace-scoped request injects `X-Workspace-Id`.
- Missing workspace prevents request before network call.
- Non-workspace auth endpoints do not require workspace.
- 401 refresh retry preserves workspace header.

### Manual Verification

1. Start backend in fake LinkedIn mode.
2. Sign in to SPA.
3. Verify `GET /api/publishing/channels` returns empty array.
4. Click `Connect LinkedIn profile`.
5. Complete fake OAuth/callback path.
6. Verify channel appears in sidebar/settings.
7. Schedule a LinkedIn post and confirm backend receives real `socialAccountId`.
8. Reconnect same profile and verify no unique-constraint failure.
9. Open events stream and confirm connect event triggers a channel refresh.

## Rollout and Migration Plan

1. **Backend read/write safety first**
    - Implement R2DBC upsert fixes.
    - Add channel list query/repository/controller.
    - Add tests ensuring no credential leakage.

2. **OAuth initiation/state validation**
    - Add state signer and authorization URL builder.
    - Extend completion command/request with `state`.
    - Keep existing completion route path unchanged.
    - Coordinate frontend rollout because completion will require the new `state` field after
      deployment.

3. **Frontend workspace header**
    - Add active workspace source and `X-Workspace-Id` injection.
    - Fail fast if workspace missing for publishing calls.

4. **Frontend channel migration**
    - Replace mock channel seed with backend `fetchChannels()`.
    - Add empty state and LinkedIn CTA.
    - Remove `account-linkedin-mock` fallback from authenticated publishing paths.

5. **OAuth callback UI**
    - Add route/view and store actions.
    - Verify fake mode locally before real LinkedIn credentials.

6. **SSE progressive enhancement**
    - Add backend in-memory event stream and frontend fetch-stream parser.
    - If SSE fails, do not block channel list or scheduling.

7. **Production configuration**
    - Configure LinkedIn client ID, secret, redirect URI, scopes, and state signing secret.
    - Validate redirect URI matches LinkedIn app settings.

Rollback:

- Channel list and SSE endpoints are additive.
- If OAuth initiation fails in production, hide/disable CTA while keeping channel list read-only.
- If frontend channel migration causes issues, use a temporary feature flag such as
  `VITE_USE_MOCK_CHANNELS` for local/demo only; do not reintroduce mocks into authenticated backend
  scheduling.
- If SSE causes instability, disable subscription client-side; REST remains correct.

## Tradeoffs

| Choice                                        | Benefit                                                                           | Cost / Limitation                                                                                    |
|-----------------------------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Fetch-streaming SSE over native `EventSource` | Supports Bearer auth headers with current in-memory token model.                  | Requires custom stream parsing and reconnect handling.                                               |
| SSE over WebSocket                            | Simpler one-way notification model, natural with WebFlux `Flux<ServerSentEvent>`. | Not suitable for future bidirectional collaboration without another protocol.                        |
| Stateless signed OAuth state                  | No new table, fast to implement, tamper-evident.                                  | Does not persist nonce consumption; replay prevention relies on short TTL and single-use OAuth code. |
| REST canonical + SSE refresh trigger          | Robust against dropped events and reconnects.                                     | Extra GET after each event; event payload cannot directly mutate state.                              |
| Provider-neutral channel DTO                  | Future-proofs org pages and other providers.                                      | Frontend must tolerate unknown providers/account kinds.                                              |
| In-memory Reactor event sink                  | Minimal MVP infrastructure.                                                       | Events do not cross backend instances; replace before relying on multi-instance real-time behavior.  |

## Implementation Notes and Gotchas

- `CompleteLinkedInConnectionCommand` currently lacks `state`; controller tests and all call sites
  must be updated together.
- Current `R2dbcSocialConnectionRepository.upsert` and `R2dbcSocialAccountRepository.upsert` are
  plain `INSERT`; this must be fixed before reconnect tests can pass.
- Current `publishing.ts` seeds Twitter/LinkedIn/Instagram mocks and falls back to
  `account-linkedin-mock`; authenticated backend paths must remove this fallback.
- `createApiFetch` currently always parses JSON. SSE requires a raw/streaming response path.
- Workspace source is not obvious in the current frontend; implementation must introduce or locate
  active workspace context before channel calls can work.
- Do not expose `providerAccountId` in the new channel list unless explicitly needed;
  `socialAccountId` is the backend scheduling identifier. Existing
  `SocialConnectionResult.account.providerAccountId` can remain for completion response
  compatibility, but the list endpoint should minimize fields.
