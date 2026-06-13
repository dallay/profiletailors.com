# Proposal: Connect SPA Channels to LinkedIn

## Intent

The Vue SPA currently seeds mock channels (Twitter, LinkedIn, Instagram) in the Pinia publishing store and falls back to `account-linkedin-mock` when scheduling. No real channel data reaches the UI. This change replaces mocks with real backend-connected LinkedIn personal profiles, wires the OAuth flow end-to-end, and introduces SSE as a progressive notification mechanism — making channel state reflect actual workspace-scoped social connections.

## Problem

- **No list endpoint**: Backend persistence exists for `social_connections` and `social_accounts`, but no query endpoint returns connected channels to the SPA.
- **No OAuth initiation**: The backend completes an OAuth code but cannot generate the authorization URL + state for the SPA to redirect the user.
- **No workspace header**: `apiFetch` omits `X-Workspace-Id`; all workspace-scoped publishing handlers reject without it.
- **No callback route**: The SPA has no route/view to handle the LinkedIn OAuth redirect.
- **Mock seeding blocks reality**: `publishing.ts` seeds fake channels; scheduling targets `account-linkedin-mock`.
- **No real-time signal**: After OAuth completion, the SPA has no way to learn that channels changed without a manual refresh.
- **INSERT-only upserts**: Repository methods use plain `INSERT`, which violates unique constraints on reconnect.

## Scope

### In Scope

- `GET /api/publishing/channels` — workspace-scoped list of safe connected social account summaries
- `POST /api/publishing/linkedin/connections/initiate` — returns authorization URL + signed `state`
- State validation on the existing completion endpoint
- Frontend `apiFetch` injection of `X-Workspace-Id` from active workspace context
- SPA LinkedIn OAuth callback route + view (`/integrations/linkedin/callback`)
- Replaced mock channel seeding with backend-loaded channels; empty state + "Connect LinkedIn" CTA
- `fetch`-streaming SSE endpoint (`GET /api/publishing/channels/events`) with Bearer auth
- Idempotent upsert semantics in R2DBC repositories (ON CONFLICT UPDATE)
- Application-layer tests, controller tests, R2DBC tests, and Pinia store tests

### Out of Scope

- LinkedIn organization page support (model space preserved; no UI/flow)
- Twitter, Instagram, or other provider connections
- Bidirectional WebSocket protocol
- Workspace selector UI (assumes single active workspace for MVP)
- Token refresh/rotation for provider credentials
- Channel disconnection/revoke flow
- Native `EventSource` cookie-auth SSE variant (deferred to post-MVP if needed)

## Capabilities

### New Capabilities

- `channel-list-api`: Query endpoint returning safe connected social account summaries for the active workspace
- `channel-events-sse`: One-way SSE notification stream for connected-channel state changes, using fetch-streaming with Bearer auth
- `oauth-initiation-api`: Authenticated endpoint that builds the LinkedIn authorization URL with state/nonce and returns it for client redirect
- `oauth-callback-ui`: SPA route and view handling the LinkedIn OAuth redirect, validating state, calling completion, and refreshing channels

### Modified Capabilities

- `publishing`: Existing publishing spec gains channel-list and initiation requirements; existing completion scenario gains state-validation precondition
- `tenancy`: Workspace context resolution now requires `X-Workspace-Id` injection from the SPA side

## Approach

1. **Backend first — list + initiation endpoints**: Add `ListConnectedSocialAccountsQuery` + read model, `InitiateLinkedInConnectionCommand` + handler, and corresponding controller routes. Repository methods for listing active accounts by workspace and upsert with ON CONFLICT UPDATE.
2. **Frontend integration**: Add workspace-id interceptor to `apiFetch`. Replace mock seeding with `fetchChannels()` action. Add `connectLinkedInPersonalProfile()` and `completeLinkedInConnectionFromCallback()` store actions. Add callback route.
3. **SSE as progressive enhancement**: After REST flow works end-to-end, add `GET /api/publishing/channels/events` returning `Flux<ServerSentEvent<ChannelEvent>>`. SPA subscribes via `fetch` streaming with Bearer headers. On event receipt, SPA re-fetches canonical channel list. Channel correctness never depends on SSE.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../publishing/application/` | Modified | New query, command, handler, read model |
| `server/smp/.../publishing/infrastructure/` | Modified | New controller routes, R2DBC list methods, upsert fix, SSE endpoint |
| `server/smp/.../publishing/domain/` | Modified | State/nonce model for OAuth initiation |
| `apps/web/app/src/lib/auth-api.ts` | Modified | Inject `X-Workspace-Id` header |
| `apps/web/app/src/stores/publishing.ts` | Modified | Replace mocks; add fetchChannels, connect, complete, subscribe actions |
| `apps/web/app/src/router/index.ts` | Modified | Add `/integrations/linkedin/callback` route |
| `apps/web/app/src/views/` | New | LinkedIn callback view component |
| `apps/web/app/src/components/` | Modified | Empty-channel state + CTA in sidebar/settings |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| EventSource cannot send Bearer headers | High | Use `fetch` streaming with explicit Authorization header; SSE is non-critical for correctness |
| Missing active-workspace context blocks all publishing calls | High | Add workspace-id injection to apiFetch first; hard-fail if no active workspace |
| Plain INSERT breaks on LinkedIn reconnect | Med | Implement ON CONFLICT UPDATE in R2DBC upsert methods |
| OAuth state/CSRF not validated on completion | Med | Add state signing and validation in initiation + completion handler |
| LinkedIn OAuth scope/credential config missing in real mode | Low | Fail safely with clear error; do not assume credentials exist |

## Rollback Plan

1. Revert `apiFetch` workspace header injection — publishing calls fall back to cookie/session if already supported, or return 400 gracefully.
2. Re-enable mock channel seeding via feature flag or environment check (`VITE_USE_MOCK_CHANNELS`).
3. Remove new backend routes — existing endpoints remain unaffected.
4. Remove callback route — LinkedIn connect UI becomes "coming soon" again.
5. SSE endpoint is additive — removing it breaks nothing; SPA simply re-fetches on manual refresh.

## Dependencies

- Active workspace context must be resolvable on the SPA side (assumes single-workspace MVP).
- LinkedIn OAuth client ID, redirect URI, and scopes configured in backend properties for real mode.

## Success Criteria

- [ ] SPA loads connected LinkedIn personal profiles from `GET /api/publishing/channels` instead of mocks
- [ ] User can initiate LinkedIn OAuth, authorize, and see the connected profile appear in the channel list
- [ ] Scheduling a publication targets a real `socialAccountId` from the backend
- [ ] `apiFetch` includes `X-Workspace-Id` on all workspace-scoped publishing requests
- [ ] Reconnecting the same LinkedIn profile does not violate unique constraints (upsert)
- [ ] SSE endpoint delivers channel-change events; SPA re-fetches canonical list on receipt
- [ ] All new backend endpoints have application, controller, and R2DBC test coverage
- [ ] Pinia store tests cover fetchChannels, connect, complete, and subscribe actions
