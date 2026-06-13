# Tasks: Connect SPA Channels to LinkedIn

Implementation order (TDD-first). Each task should be completed and committed with conventional commit messages. Where possible, write tests before implementation and ensure CI passes.

## Review Workload Forecast

- Forecast: High
- Reason: This change spans backend persistence, application handlers, OAuth state security, HTTP controllers, event streaming, frontend API/workspace context, Pinia state, routes/views, UI changes, configuration, and rollout verification.
- Approved delivery strategy: implement in small reviewable slices and verify each slice independently.
  1. Backend channel persistence/read model and canonical `GET /api/publishing/channels` endpoint.
  2. Backend LinkedIn OAuth initiation and signed state validation.
  3. Frontend workspace header injection and channel store migration away from mocks.
  4. Callback route/view and LinkedIn connect UX.
  5. Fetch-streaming SSE notification client/server.
  6. Integration/security/rollout hardening.
- Current approved slice: Slice 5 — Fetch-streaming SSE notification client/server.

## 0. Prep
- [ ] Create branch: feature/connect-spa-channels-to-linkedin

## 1. Backend — Repository & Read Models (tests first)
- [x] Add R2DBC repository unit tests for upsert behavior (insert then update on conflict) and read query:
  - Path: server/smp/publishing/infrastructure/r2dbc/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/r2dbc/R2dbcPublishingRepositoriesTest.kt (add cases)
  - Tests:
    - social_connection upsert preserves id on conflict (workspace_id, provider, provider_connection_ref)
    - social_account upsert preserves id on conflict (workspace_id, provider, provider_account_id)
    - connected channels read query returns joined rows and excludes credential_reference
- [x] Implement upsert SQL with ON CONFLICT ... DO UPDATE in:
  - Path: server/smp/publishing/infrastructure/r2dbc/src/main/kotlin/.../R2dbcSocialConnectionRepository.kt
  - Path: server/smp/publishing/infrastructure/r2dbc/src/main/kotlin/.../R2dbcSocialAccountRepository.kt
- [x] Add ConnectedSocialChannel read repository interface and R2DBC implementation and tests:
  - Domain interface: server/smp/publishing/domain/src/main/kotlin/com/profiletailors/smp/publishing/domain/ConnectedSocialChannelReadRepository.kt
  - R2DBC impl: server/smp/publishing/infrastructure/r2dbc/src/main/kotlin/.../R2dbcConnectedSocialChannelReadRepository.kt
  - Tests: include SQL fixture and assertions (same R2dbcPublishingRepositoriesTest.kt)

## 2. Backend — Application Handlers & Ports (TDD)
- [x] Add domain ports and DTOs (no Spring annotations):
  - Path: server/smp/publishing/domain/src/main/kotlin/.../OAuthStateSigner.kt (interface)
  - Path: server/smp/publishing/domain/src/main/kotlin/.../LinkedInAuthorizationUrlBuilder.kt
  - Path: server/smp/publishing/application/src/main/kotlin/.../PublishingApi.kt (add Initiate/List/Complete DTOs)
  - Add unit tests for command/handler behavior in: server/smp/publishing/application/src/test/kotlin/.../PublishingHandlersTest.kt
- [x] Implement InitiateLinkedInConnectionHandler tests (mock OAuthStateSigner + LinkedInAuthorizationUrlBuilder) that assert expected result and errors when provider not configured.
- [x] Implement ListConnectedChannelsHandler tests (mock ConnectedSocialChannelReadRepository) verifying workspace resolution and default filtering to ACTIVE.
- [x] Update CompleteLinkedInConnectionHandler tests to include state validation path (invalid signature, expired, mismatched workspace/principal/redirectUri) and success path that calls upsert repositories and publishes event.

## 3. Backend — Infrastructure: LinkedIn OAuth & State Signer (tests then impl)
- [x] Add HmacOAuthStateSigner unit tests that sign/verify payload, reject tampered/expired payloads.
  - Path: server/smp/publishing/infrastructure/linkedin/src/test/kotlin/.../HmacOAuthStateSignerTest.kt
- [x] Implement HmacOAuthStateSigner:
  - Path: server/smp/publishing/infrastructure/linkedin/src/main/kotlin/.../HmacOAuthStateSigner.kt
- [x] Add LinkedInAuthorizationUrlBuilder adapter tests and implementation (uses LinkedInPublishingProperties):
  - Path: server/smp/publishing/infrastructure/linkedin/src/main/kotlin/.../LinkedInAuthorizationUrlBuilderAdapter.kt
- [x] Add configuration class `LinkedInPublishingProperties` and entries in application-test.yml for fake mode.

## 4. Backend — Controllers & HTTP Layer (tests then impl)
- [x] Add controller tests for new endpoints (use existing controller test harness):
  - Path: server/smp/publishing/infrastructure/http/src/test/kotlin/.../PublishingConnectionControllerTest.kt
  - Cases:
    - POST /api/publishing/linkedin/connections/initiate returns authorizationUrl + state
    - POST /api/publishing/linkedin/connections/complete forwards state + code
    - GET /api/publishing/channels dispatches ListConnectedChannelsQuery and requires X-Workspace-Id
    - GET /api/publishing/channels/events can be constructed with fake publisher stream
- [x] Implement PublishingConnectionController handlers and DTOs:
  - Path: server/smp/publishing/infrastructure/http/src/main/kotlin/.../PublishingConnectionController.kt
  - Path: server/smp/publishing/infrastructure/http/src/main/kotlin/.../PublishingChannelController.kt
- [x] Wire mediator/handler registrations and map exceptions to HTTP codes (ProviderNotConfiguredException -> 503, InvalidOAuthStateException -> 400, ExpiredOAuthStateException -> 400)

## 5. Backend — Event Bus & SSE Endpoint (tests then impl)
- [x] Add ChannelEventPublisher in-memory Reactor sink and tests for publish/filter by workspace:
  - Path: server/smp/publishing/infrastructure/events/src/main/kotlin/.../ReactorChannelEventPublisher.kt
  - Expose Flux<ChannelEvent> via ChannelEventStreamRegistry
- [x] Implement SSE controller endpoint that subscribes to Flux and filters by workspace context and emits SSE named events with heartbeat every 20s.
  - Path: server/smp/publishing/infrastructure/http/src/main/kotlin/.../PublishingChannelController.kt (streamEvents method)
- [ ] Add integration test that authenticates, sets X-Workspace-Id, and asserts SSE event stream produces correct event frames when publisher publishes.

## 6. Backend — Integration & Security Tests
- [ ] Add integration tests ensuring GET /api/publishing/channels requires Authorization and X-Workspace-Id (401/400 cases)
- [ ] Add test where completion in fake LinkedIn mode results in persisted channel and published event
- [ ] Run R2DBC suite locally and fix flakiness/migrations if any

## 7. Frontend — API Fetch & Workspace header (TDD/unit)
- [x] Add unit tests for apiFetch workspace injection and error-on-missing-workspace behavior
  - Path: apps/web/app/src/lib/__tests__/auth-api.spec.ts
- [x] Modify or add createApiFetch to accept getWorkspaceId and workspaceScoped option:
  - Path: apps/web/app/src/lib/auth-api.ts
- [x] Add or expose active workspace ID provider (temporary store or from existing auth/session store):
  - Path suggestion: apps/web/app/src/stores/workspace.ts (new) or apps/web/app/src/stores/auth.ts (modify)
- [x] Ensure all publishing calls use workspaceScoped: true when invoking apiFetch

## 8. Frontend — Pinia Publishing Store & Tests
- [x] Add unit tests for publishing store actions before implementation:
  - Path: apps/web/app/src/stores/__tests__/publishing.spec.ts
  - Cases: fetchChannels maps API to store, connectLinkedInPersonalProfile calls initiate endpoint and redirects, completeLinkedInConnectionFromCallback posts code/state and refreshes channels
- [x] Implement store changes:
  - Path: apps/web/app/src/stores/publishing.ts
  - Add state: channels, channelsLoading, channelsError, channelEventsConnected, channelEventsAbortController
  - Add actions: fetchChannels, connectLinkedInPersonalProfile, completeLinkedInConnectionFromCallback; SSE subscribe/unsubscribe stubs remain for Slice 5
- [x] Add helper apiFetchRaw / apiFetchStream to read streaming responses (if needed) in auth-api.ts

## 9. Frontend — Router and Callback View (TDD)
- [x] Add unit test for callback view behavior (parsing query params, calling store action)
  - Path: apps/web/app/src/views/LinkedInCallbackView.spec.ts
- [x] Add route and view component:
  - Path: apps/web/app/src/router/index.ts (add route)
  - Path: apps/web/app/src/views/LinkedInCallbackView.vue (new)
- [x] Ensure view computes redirectUri from window.location and passes code/state to publishing.completeLinkedInConnectionFromCallback

## 10. Frontend — UI Changes (integration/manual)
- [x] Replace mock channel seeding in publishing store and any components referencing `account-linkedin-mock`:
  - Files to update: apps/web/app/src/stores/publishing.ts, apps/web/app/src/components/SidebarChannels.vue, apps/web/app/src/views/SettingsChannels.vue
- [x] Add empty state CTA: `Connect LinkedIn profile` in settings/sidebar
- [x] Update scheduling UI to require selected channel and error if none (remove mock fallback)

## 11. Frontend — SSE Client Implementation & Tests
- [x] Implement SSE fetch-stream parser utility and unit tests:
  - Path: apps/web/app/src/lib/sse.ts and __tests__/sse.spec.ts
- [x] Implement subscribeChannelEvents action to open fetch stream with Authorization + X-Workspace-Id and call fetchChannels on received events

## 12. End-to-end / Manual Verification (checklist)
- [ ] Start backend in fake LinkedIn mode (dev profile)
- [ ] Start frontend, sign in, verify GET /api/publishing/channels => []
- [ ] Click Connect LinkedIn; complete fake OAuth; ensure channel appears
- [ ] Schedule a post; verify backend receives socialAccountId (inspect request/DB)
- [ ] Reconnect same profile; assert upsert preserved id and no unique constraint errors
- [ ] Open events SSE and verify connect event triggers a fetch

## 13. Rollout / Configuration
- [ ] Add LinkedIn properties to prod config with clientId, clientSecret, redirectUri, scopes, stateSigningSecret; document in ops README
- [ ] Add environment flag VITE_USE_MOCK_CHANNELS for local/demo only and ensure it is disabled in prod
- [ ] Monitor logs for provider-not-configured errors and 503s during initial rollout
- [ ] Plan replacement of in-memory event sink for multi-instance (Postgres NOTIFY or Redis pub/sub)

## 14. Housekeeping
- [x] Update openspec/changes/connect-spa-channels-to-linkedin/state.yaml to mark phase=tasks (automated)
- [ ] Create follow-up issue: replace in-memory event bus for multi-instance deployments
- [ ] Add CI job to run R2DBC tests in a reproducible test DB (Postgres testcontainer)


Notes on commit style and testing:
- Use conventional commits: feat(publishing): add initiate linkedin connection
- Write tests first per TDD; where very costly add integration tests after unit tests
- Keep changes small and reviewable; prefer multiple PRs if size grows large: e.g., (1) upsert + read repo, (2) handlers + controllers, (3) frontend apiFetch + store changes, (4) SSE
