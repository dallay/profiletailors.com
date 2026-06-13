# Exploration: connect-spa-channels-to-linkedin

## Current State

### Backend publishing slice

- The backend already models social publishing in a provider-neutral way:
  - `SocialProvider` currently includes `LINKEDIN`.
  - `SocialConnectionStatus` includes `ACTIVE`, `REVOKED`, `EXPIRED`, and `ERROR`.
  - `SocialAccountKind` already includes both `PERSONAL_PROFILE` and `ORGANIZATION_PAGE`, which is enough for future LinkedIn organization-page extension without changing the basic account taxonomy.
- Existing public connection completion endpoint:
  - `PublishingConnectionController` exposes `POST /api/publishing/linkedin/connections/complete`.
  - It sends `CompleteLinkedInConnectionCommand(authorizationCode, redirectUri)` through the mediator.
- Existing application flow:
  - `CompleteLinkedInConnectionHandler` requires an authenticated principal and active workspace context.
  - Workspace context is resolved from `X-Workspace-Id` by `WorkspaceContextWebFilter`.
  - The handler delegates provider completion to `SocialConnectionProvider`, persists `SocialConnection`, persists `SocialAccount`, and returns `SocialConnectionResult` with safe metadata only.
- Existing provider adapter behavior:
  - Fake provider returns a LinkedIn personal profile.
  - Real provider exchanges an authorization code against LinkedIn token endpoint and fetches `/v2/userinfo`.
  - Real provider stores LinkedIn credentials through `LinkedInCredentialGateway` and returns only a credential reference.
- Existing persistence:
  - `social_connections` has workspace/provider/provider_ref uniqueness and stores credential reference, status, connected timestamps.
  - `social_accounts` has workspace/provider/provider_account_id uniqueness and stores account type, display name, profile URN, and status.
  - R2DBC repositories support only `upsert` and `findByWorkspaceAndId` for connections/accounts.
- Existing backend tests:
  - `PublishingHandlersTest` covers completing a LinkedIn profile in an active workspace.
  - `R2dbcPublishingRepositoriesTest` covers persisting and reading a social connection/account.
  - Controller and workspace-isolation tests exist around the publishing slice.

### Frontend publishing UI

- `apps/web/app/src/stores/auth.ts` exposes `auth.apiFetch`, backed by `createApiFetch` in `apps/web/app/src/lib/auth-api.ts`.
- `createApiFetch` injects Bearer tokens and sends `credentials: 'include'`, with API versioning via `Accept: application/vnd.api.v1+json`.
- The frontend currently does not add `X-Workspace-Id` in `apiFetch`; publishing backend handlers that require workspace context will need this header from a real workspace selector/context.
- `apps/web/app/src/stores/publishing.ts` currently seeds mock `channels` for Twitter, LinkedIn, and Instagram.
- Scheduling uses the first LinkedIn channel and falls back to `account-linkedin-mock`, which will fail against the real backend unless a matching backend `social_accounts.id` exists.
- Calendar loading already calls the backend through `GET /api/publishing/publications/calendar`, then maps backend publications to local frontend publications.
- Channels are consumed by:
  - Sidebar channel list and channel filters in `App.vue`.
  - Channel/account filter in `CalendarHeader.vue`.
  - Channel selection in `CreatePostModal.vue`.
  - Publication filtering in `SchedulerView.vue`.

### OpenSpec

- OpenSpec is initialized at `openspec/config.yaml`.
- The current canonical publishing spec already includes workspace-scoped social connections and safe read models, but the executable backend lacks the public listing/read endpoint needed by the SPA.

## Exact Gaps for Real Channels

1. **Workspace context header gap**
   - Backend publishing handlers require active workspace context.
   - Frontend `apiFetch` currently has no workspace-id provider and no `X-Workspace-Id` injection.
   - Before real channel loading can work consistently, the SPA needs a real active workspace context and the API helper must send `X-Workspace-Id`.

2. **List connected channels/accounts gap**
   - Domain/repository ports only support `upsert` and `findByWorkspaceAndId`.
   - Add application query/read model to list safe connected social accounts for the active workspace.
   - Add repository methods such as listing active accounts by workspace, optionally joined with connection metadata.
   - Return no provider credentials. Include enough for UI: account id, connection id, provider, display name, account kind, status, profile URN/handle-ish display field, connected/synced metadata if useful.

3. **OAuth initiation gap**
   - The backend can complete an OAuth code, but it does not expose an initiation endpoint that builds the LinkedIn authorization URL.
   - The SPA currently shows “coming soon” connect affordances and does not redirect users to LinkedIn.
   - Recommended backend shape: an authenticated, workspace-scoped initiation command/query that returns an authorization URL and state/nonce, with state persisted or signed and later validated on complete.
   - Do not assume credentials; use existing `publishing.linkedin.*` properties and fail safely if real mode lacks client id/redirect/scopes.

4. **OAuth callback/complete UI gap**
   - The SPA has no route/view for the LinkedIn OAuth callback.
   - Add route such as `/settings/channels/linkedin/callback` or `/integrations/linkedin/callback` that reads `code` and `state`, validates expected state locally/with backend, then calls `POST /api/publishing/linkedin/connections/complete`.
   - After completion, refresh channels from backend and navigate back to scheduler/settings with success/error feedback.

5. **Mock removal and scheduler targeting gap**
   - `channels` should be initialized empty/loading, not seeded with Twitter/LinkedIn/Instagram mocks for authenticated users.
   - `schedulePost` must use selected real `Channel.accountId` values and should block/guide the user when no connected LinkedIn personal profile exists.
   - MVP should probably restrict selectable channels to backend-connected LinkedIn personal profiles, while preserving type/model space for future providers.

6. **Event streaming gap**
   - No SSE or WebSocket implementation currently exists in backend or frontend.
   - Real-time need is one-way: after OAuth complete or background sync, the server may notify the SPA that connected channels changed.
   - Event stream should be a projection/notification path, not the source of truth; the SPA should still fetch the canonical list endpoint on connect/reconnect or event receipt.

7. **Idempotent upsert risk**
   - Tables have unique constraints on workspace/provider/provider refs/account ids.
   - Repository `upsert` methods currently execute plain `INSERT`, so reconnecting the same LinkedIn account will likely violate uniqueness.
   - MVP should either implement actual upsert semantics or detect existing connection/account and update status/credential metadata.

8. **Publication/account relation risk**
   - `RealLinkedInPublisher.resolveAccessToken` currently derives credential owner from provider account id and comments that production should link `SocialAccount -> SocialConnection` and use `credentialReference`.
   - Listing channels can still work, but robust publish-after-connect should close this gap before relying on organization pages or credential rotation.

## Recommendation: SSE vs WebSocket

Recommend **Server-Sent Events (SSE)** for the MVP.

Evidence and rationale:

- Current product need is one-way server-to-client notification: “connected channels changed” or “connection completed/synced”. There is no demonstrated bidirectional collaboration or client-to-server streaming requirement.
- Backend is Spring Boot WebFlux, where returning `Flux<ServerSentEvent<...>>` is a natural fit and avoids adding a WebSocket protocol layer.
- Frontend can use `EventSource` for simple reconnecting event streams, but native `EventSource` cannot send arbitrary `Authorization` headers. Because access tokens are held only in memory, there are two safe implementation options:
  1. Prefer same-origin/session-cookie based SSE if the endpoint can authenticate via existing cookie/session semantics; or
  2. Use `fetch` streaming with Bearer headers instead of native `EventSource`.
- Given current auth uses in-memory Bearer with refresh cookies, the simplest robust first step is not to make channel correctness depend on streaming. Implement canonical REST list + OAuth complete first, then add SSE/fetch-stream as progressive enhancement.
- WebSocket would add bidirectional connection lifecycle, message protocol, auth handshake complexity, and testing surface without a current bidirectional need.

Suggested event model:

- Endpoint: `GET /api/publishing/channels/events` or `/api/publishing/connections/events`, workspace-scoped.
- Event types: `connected-channel.updated`, `connected-channel.removed`, maybe heartbeat.
- Payload: safe account summary only, or just a version/timestamp instructing the SPA to refetch.
- Source of truth remains `GET /api/publishing/channels`.

## Suggested Proposal Shape

- Add `ListConnectedSocialAccountsQuery` and `ConnectedSocialChannelSummary` read model.
- Add repository list method(s) in `SocialAccountRepository` / `SocialConnectionRepository` or a dedicated read-model port.
- Add `GET /api/publishing/channels` returning active/safe connected channels for the current workspace.
- Add LinkedIn OAuth initiation endpoint returning authorization URL with state.
- Keep completion endpoint but add state validation if not already covered by initiation design.
- Add SPA publishing store actions:
  - `fetchChannels()`
  - `connectLinkedInPersonalProfile()`
  - `completeLinkedInConnectionFromCallback()`
  - optional `subscribeChannelEvents()` after REST flow exists.
- Replace authenticated mock channel seeding with backend-loaded channels; keep unauthenticated/dev fallback only if explicitly desired.
- Update UI to show an empty connected-channel state and “Connect LinkedIn profile” CTA.
- Add tests at application, controller, R2DBC, and Pinia store levels.

## Files/Symbols to Inspect During Proposal/Implementation

- Backend:
  - `PublishingModels.kt`
  - `PublishingRepositories.kt`
  - `PublishingApi.kt`
  - `PublishingHandlers.kt`
  - `PublishingControllers.kt`
  - `LinkedInPublishingAdapters.kt`
  - `R2dbcPublishingConnectionRepositories.kt`
  - `server/smp/src/main/resources/db/changelog/publishing/001-create-social-connections.yaml`
  - `server/smp/src/main/resources/db/changelog/publishing/002-create-social-accounts.yaml`
  - `WorkspaceContextWebFilter.kt`
  - `TenancyWebConfiguration.kt`
- Frontend:
  - `apps/web/app/src/lib/auth-api.ts`
  - `apps/web/app/src/stores/auth.ts`
  - `apps/web/app/src/stores/publishing.ts`
  - `apps/web/app/src/router/index.ts`
  - `apps/web/app/src/App.vue`
  - `apps/web/app/src/components/CreatePostModal.vue`
  - `apps/web/app/src/components/CalendarHeader.vue`
  - `apps/web/app/src/views/SettingsView.vue`

## Test Evidence / Conventions

- Backend unit pattern: `PublishingHandlersTest` with fixed principal/resource context providers and in-memory repositories.
- Backend R2DBC pattern: `R2dbcPublishingRepositoriesTest : DatabaseUnitTestBase()`.
- Backend controller pattern: `PublishingControllersTest` with mediator capture/fakes.
- Frontend store tests: `apps/web/app/src/stores/publishing.test.ts` uses Pinia + Vitest and spies `auth.apiFetch`.

## Risks

- Native `EventSource` with in-memory Bearer auth is awkward; avoid making SSE mandatory for correctness unless auth transport is explicitly designed.
- Current frontend lacks active workspace selection/header injection, which can block all workspace-scoped backend publishing calls.
- Plain INSERT repository methods may break reconnect/idempotency under existing unique constraints.
- OAuth `state`/CSRF handling must be specified before exposing initiation/callback to real users.
- Organization-page support should remain out of MVP except for preserving `SocialAccountKind.ORGANIZATION_PAGE` and provider-neutral read models.
