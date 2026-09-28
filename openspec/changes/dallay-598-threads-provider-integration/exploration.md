# Exploration: DALLAY-598 — Threads Provider Integration

## Status

Exploration complete. No product code was changed. The next SDD phase is proposal, but proposal/spec/design should not begin until the decisions and blockers in this document are resolved or explicitly accepted.

## Issue and intended outcome

Linear DALLAY-598 is **[Threads] Integrate Threads as the second social publishing provider**, a High priority, 5-point issue in milestone **0.4 — Multi-provider proof**. Its stated outcome is to add Meta Threads as a second first-class provider while proving that the existing publication, scheduling, calendar, queue, and delivery-attempt workflow remains provider-neutral.

The issue requires:

- provider adapters and routing rather than a second publishing workflow;
- signed-state OAuth with only `threads_basic` and `threads_content_publish` requested for the MVP;
- short-lived-to-long-lived token exchange and refresh using returned expiry metadata;
- encrypted credential persistence without token or signed-media-URL leakage;
- one Threads user mapped to one `SocialAccount` with `THREADS` and `PERSONAL_PROFILE`;
- text, single image, single video, and 2–20 item image/video carousel publishing;
- bounded Threads media-container polling and final publish;
- temporary provider-fetchable HTTPS media URLs without a public bucket;
- existing scheduler/worker reuse, provider-specific validation, reconnect UX, and regression coverage;
- at least one deployed Meta test/app-role smoke flow and current Meta App Review/app-publishing evidence before production launch.

Related Linear issues are DALLAY-528 (provider-neutral social-content synchronization domain) and DALLAY-529 (fake social provider and provider contract-test harness). Neither dependency was changed in this exploration.

## Repository and SDD validation

- The repository is `dallay/profiletailors.com`, branch/worktree `threads`.
- `openspec/config.yaml` already exists and is configured for `schema: spec-driven`, OpenSpec-only persistence, `openspec/specs` as main specs, and `openspec/changes` as active changes.
- Existing change artifacts use `explore.md` or `exploration.md`, `proposal.md`, `design.md`, `tasks.md`, optional apply/QA/verification reports, and `state.yaml` with a phase list. Archived examples inspected: `2026-09-09-dallay-413-bulk-scheduling` and `2026-09-16-dallay-665-bulk-waitlist-invitation`.
- `openspec/specs/publishing/spec.md` is the primary durable publishing contract. Related current specs are `channel-list-api`, `workspace-scoped-oauth`, `oauth-initiation-api`, `oauth-callback-ui`, `channel-events-sse`, and `media-library`.
- `openspec/config.yaml` declares strict TDD and repository runners, including `just backend-test-fast`, `just backend-bdd-fast`, `just backend-test-postgres`, `just frontend-test`, and `just frontend-test-e2e`.
- `just -l` was inspected. No product implementation or tests were run because this phase is initialization/exploration only.

## Worktree and preservation

Before this exploration the only working-tree change was the untracked user/parent artifact:

- `plan/tasks/dallay-598-threads-provider-integration.md`

It was preserved unchanged. This exploration added only:

- `openspec/changes/dallay-598-threads-provider-integration/state.yaml`
- `openspec/changes/dallay-598-threads-provider-integration/exploration.md`

No reset, clean, checkout, commit, or product-code edit was performed.

## Product and design context

Read `apps/web/PRODUCT.md`, `apps/web/app/PRODUCT.md`, `apps/web/admin/PRODUCT.md`, and `.agents/DESIGN.md`.

- The shared product truth says the early-access app currently lets authenticated users create, schedule, and review LinkedIn content; LinkedIn is explicitly the first integrated platform and other integrations follow after validation.
- The dashboard is the affected surface. Its operating context says users connect one or more LinkedIn accounts via OAuth, content creation/scheduling happens in-browser, and publishing executes server-side from stored OAuth tokens. Threads changes this current shipped truth and will require a product/OpenSpec update during proposal/spec work.
- Admin is an internal operator surface for waitlist, users, and audit; no direct Threads UI seam was found or required by the issue. Admin should remain out of scope unless the approved design discovers an operational control requirement.
- `.agents/DESIGN.md` defines the Nothing-inspired dark-first system. Threads UI additions belong in existing dashboard channel/connect/composer patterns and must not create a parallel visual language.

## Architecture constraints

The applicable architecture is the modular Spring Boot 4/Kotlin/WebFlux backend with `domain <- application <- infrastructure`, CQRS/mediator application entry points, Spring Modulith context boundaries, encrypted credentials, R2DBC/PostgreSQL, and Vue 3/Pinia dashboard modules. Relevant accepted ADRs inspected include ADR-0002 (hexagonal architecture) and ADR-0004 (CQRS via mediator). The repository constitution also requires provider behavior to remain in adapters, controller thinness, no raw-token exposure, Cucumber coverage for new externally observable backend behavior, and no linter/test/configuration bypasses.

## Existing backend seams and findings

### Provider-neutral publishing seam

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingProviderContracts.kt` already defines:

- `CompleteProviderConnectionCommand`;
- `ProviderConnectionResult` and `ProviderAccountProfile`;
- `ProviderPublishCommand` and `ProviderPublishResult`;
- `RefreshAwareCredentialResolver` and reconnect reasons;
- `ProviderCapabilityValidationInput` / `ProviderCapabilityValidator`;
- `SocialConnectionProvider` and `SocialPublisher` ports.

`PublishingWorker` receives one `SocialPublisher` and `PublishingSchedulingConfiguration` wires that port. The worker owns provider-neutral claim, delivery-attempt, retry, failure, and lifecycle processing. Existing models include `PublicationDraft`, `PublicationJob`, `DeliveryAttempt`, `DeliveryAttemptPhase.PROVIDER_CREATE/FINALIZATION/AMBIGUOUS`, and `DeliveryAttemptOutcome.AMBIGUOUS`, which are relevant to Threads container creation and transport uncertainty.

### Current LinkedIn-only wiring

The implementation is not yet provider-neutral at the composition boundary:

- `SocialProvider` in `domain/PublishingModels.kt` currently contains only `LINKEDIN`.
- OAuth contracts are named `LinkedInOAuthStatePayload` and `LinkedInAuthorizationUrlBuilder`.
- Application commands and handlers are `InitiateLinkedInConnectionCommand` / `CompleteLinkedInConnectionCommand` and corresponding handlers.
- `PublishingConnectionController` is mounted at `/api/publishing/linkedin/connections`.
- `PublishingApplicationConfiguration` checks only `SocialProvider.LINKEDIN`, `LinkedInPublishingProperties`, and `ConfigurableLinkedInAuthorizationUrlBuilder` when resolving the provider catalog.
- `PublishingSchedulingConfiguration` injects one `SocialPublisher`.
- `LinkedInPublishingWiring.kt` exposes `RealLinkedInConnectionProvider`, `RealLinkedInPublisher`, LinkedIn HTTP transport, authorization URL generation, capability validation, and asset upload behavior.
- `application.yaml` has only `publishing.linkedin.*` properties and LinkedIn environment variables. Production compose mounts LinkedIn client/state secrets and the shared `PUBLISHING_CREDENTIALS_ENCRYPTION_KEY`.

This is the principal architecture seam to generalize. The issue's candidate registry/router shape is compatible with the current ports, but the exact design is intentionally deferred to proposal/design.

### OAuth and connection flow

`PublishingConnectionHandlers.kt` resolves workspace/principal context, signs and validates state, checks provider availability, completes the provider connection, persists `SocialConnection` and `SocialAccount`, publishes channel events, and returns a safe `SocialConnectionResult`. `OAuthConnectionContracts.kt` binds provider, workspace, principal, redirect URI, nonce, issued/expiry timestamps in state, but the payload and builder are LinkedIn-named.

`PublishingControllers.kt` exposes:

- `POST /api/publishing/linkedin/connections/initiate`;
- `POST /api/publishing/linkedin/connections/complete`;
- `GET /api/publishing/channels`;
- `GET /api/publishing/channels/providers`;
- `GET /api/publishing/channels/events`.

`openspec/specs/oauth-initiation-api/spec.md` and `oauth-callback-ui/spec.md` are LinkedIn-specific and must be modified or generalized if the approved Threads flow shares these endpoints/contracts. `workspace-scoped-oauth` already requires signed workspace binding, PKCE/browser authorization, audience separation, and membership validation; the existing publishing state signer additionally binds provider and redirect details.

### Credentials and token lifecycle

`secure_credentials` and `CredentialEncryptionService` are reusable storage mechanisms. However, `LinkedInCredentialGateway.kt` currently owns `LinkedInCredentials`, `LinkedInCredentialGateway`, and `R2dbcLinkedInCredentialGateway`; `RefreshAwareCredentialResolverImpl` injects that gateway plus `LinkedInPublishingProperties` and `LinkedInHttpTransport`, so token refresh is currently LinkedIn-coupled. The issue explicitly asks to extract provider-neutral storage mechanics rather than clone this adapter.

The current domain stores only a `credentialReference` on `SocialConnection`. APIs return safe channel summaries and do not expose credential payloads. The approved design must preserve these properties and define a provider-neutral credential payload/refresh gateway without logging raw tokens.

### Capabilities and media

`PublishingProviderContracts.kt` has provider capability validation input, while `PublishingModels.kt` contains LinkedIn-specific `LinkedinCapabilityBundle` and `GrantedScopeBundle`. The issue requires a provider-neutral capability representation that can express text, image, video, and carousel, with Threads rejecting unsupported combinations before an external call.

`MediaAssetResolverImpl` resolves ready media assets from the media bounded context. Existing `PublicationAsset` supports uploaded and external URL sources, provider asset references, and workspace scoping. The issue requires a temporary HTTPS URL that Meta can fetch while a Threads container is processing; it explicitly forbids making the media bucket permanently public. The exact URL-signing/TTL seam is not yet confirmed by this exploration and is a proposal/design blocker.

### Failure and idempotency seam

The scheduler has typed failure categories for provider rate limiting, provider unavailable, account reconnect, account unavailable, and publishing failure. The domain already models ambiguous delivery attempts. Threads container creation followed by status polling and final publish needs an adapter-local reconciliation/idempotency policy so an uncertain request is not blindly retried into duplicate content. The existing operation key is carried by `ProviderPublishCommand`, but the repository does not yet prove that Meta Threads accepts it as an idempotency key or that container IDs can be recovered after a network timeout. This needs an explicit design decision and tests.

## Existing frontend seams and findings

### Channel and provider catalog

`apps/web/app/src/modules/publishing/domain/channel.ts` defines the channel model and statuses including `REQUIRES_RECONNECT`. `publishing.store.ts` fetches `/api/publishing/channels` and `/api/publishing/channels/providers`, maps provider strings to channel models, and exposes `connectLinkedInPersonalProfile`. Provider attachment limits are currently a frontend map keyed by provider.

`apps/web/app/src/shared/lib/provider-presentation.ts` already includes `threads` in its known-provider union/presentation data, but `PROVIDER_ACTIONS` currently only has `CONNECT_LINKEDIN_PERSONAL_PROFILE`; Threads is not currently a connectable action. `SidebarConnectSection.vue` renders available non-hidden catalog entries, and `AppShell.vue` hard-codes the LinkedIn connect action. This is the main frontend provider catalog/connect seam.

### OAuth callback

The router registers only `/integrations/linkedin/callback` and loads `LinkedInCallbackView.vue`. `useLinkedInCallback.ts` builds the LinkedIn redirect URI, calls the LinkedIn initiation/completion store actions, refreshes channels, and reports success/error. Threads needs either a generalized callback route/view/composable or a provider-aware equivalent; do not create a separate composer or scheduler.

### Composer and scheduler

`ComposerChannelSelector.vue` selects from active channels and is provider-neutral in structure. `useComposerValidation.ts` currently models a single character limit and attachment limit with LinkedIn-oriented assumptions; provider-specific text/media/capability rules will need an explicit contract. Publication creation and calendar types in `publishing.store.ts` already carry provider and social-account identifiers, so the scheduler/calendar seam appears reusable. `ComposerSchedulePanel.vue` is provider-neutral and should not be duplicated.

The existing frontend tests include publishing store, channel selector, media picker, composer validation/scheduling, sidebar channels/connect, router, callback, and app shell coverage. They should be extended after approved contracts exist.

## Test and configuration inventory

Relevant backend tests and BDD resources include:

- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingProviderCatalogHandlersTest.kt`;
- `PublishingApiTest.kt`, `PublishingHandlersTest.kt`, `CreatePublicationHandlerTest.kt`;
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingWiringTest.kt`;
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/RefreshAwareCredentialResolverTest.kt`;
- publishing worker/scheduling tests and `PublishingWorkerTransactionPostgresIntegrationTest.kt`;
- `server/smp/src/test/resources/features/publishing-channels.feature`, `publishing-publications.feature`, `media-assets.feature`, and stale/recurring/bulk publishing features;
- `PublishingBddSteps.kt`, `MediaBddSteps.kt`, and related glue.

Relevant frontend tests include publishing store tests, `ComposerChannelSelector.test.ts`, `useComposerValidation.test.ts`, `useComposerMediaPicker.test.ts`, `SidebarConnectSection.test.ts`, `AppShell.test.ts`, router specs, and `useLinkedInCallback`/callback view tests.

Configuration currently includes:

- `server/smp/src/main/resources/application.yaml` `publishing.worker`, `publishing.linkedin`, and `publishing.credentials.encryption` sections;
- `infra/apps/smp/production/compose.yaml` LinkedIn client ID/redirect variables and mounted LinkedIn state/client-secret secrets;
- `compose.yaml` and WireMock fixtures for LinkedIn local simulation;
- no Threads app ID/secret, redirect, graph base URL, token refresh-ahead, container timeout/backoff, or media URL TTL configuration was found.

No external Meta API calls, Meta app configuration inspection, deployed smoke test, App Review status check, or production environment verification was run. Those are explicit evidence gaps, not claims of absence in Meta.

## Current OpenSpec relationship

`openspec/specs/publishing/spec.md` currently describes a provider-neutral contract but still says LinkedIn personal-profile publishing is the first implemented provider. It contains provider catalog, connected channels, publication lifecycle, scheduler, retries, media, and provider-seam requirements. The Threads change should modify this capability rather than create a duplicate publishing spec.

The related OAuth specs are LinkedIn-specific. `channel-list-api` and `channel-events-sse` are already provider-neutral at the summary/event level. `media-library` owns media lifecycle separately from publishing and should remain the source of asset readiness; Threads fetchability/URL TTL belongs in the publishing/provider adapter contract or an explicitly approved media integration contract.

## Scope blockers and decisions required before proposal/spec/design

1. **Public API compatibility:** Should new provider-aware endpoints be added (`/api/publishing/{provider}/connections/...`) while retaining the LinkedIn paths, or should the existing LinkedIn paths be generalized with a compatibility alias? This affects OpenAPI, frontend callback URLs, BDD, and backward compatibility.
2. **OAuth security semantics:** Meta Threads documentation must confirm whether the requested OAuth flow uses PKCE and whether the existing browser/state flow should carry PKCE verifier/challenge in addition to the current signed state. The approved design must not weaken existing state/redirect/workspace validation.
3. **Provider routing ownership:** Confirm whether DALLAY-528/DALLAY-529 are prerequisites or whether DALLAY-598 owns the minimal registry/router and fake-provider contract harness needed for this slice. Avoid duplicating their future work.
4. **Credential schema:** Decide the canonical provider-neutral encrypted payload and gateway boundary, including refresh-token presence/absence, returned token expiry units, scope representation, optimistic refresh update/concurrency behavior, and how `SocialConnection` identifies the provider-specific resolver.
5. **Threads token lifecycle:** Confirm the exact Meta exchange and refresh endpoints/parameters and whether refresh responses rotate tokens. Local implementation must use returned expiration values, not hard-coded 60-day assumptions.
6. **Media URL delivery:** Identify the existing temporary signed/private media URL capability and its TTL semantics. Decide whether provider fetch URLs are generated per container/asset and how revocation/expiry interacts with Meta's bounded processing window.
7. **Container polling and ambiguity:** Define terminal states, polling interval/backoff/deadline, cancellation behavior, and reconciliation after timeout or uncertain POST. Decide whether ambiguous attempts become `AMBIGUOUS`/`BLOCKED` for operator retry rather than issuing another create call.
8. **Capability contract:** Decide whether the domain evolves from LinkedIn-specific bundles to a generic media-shape capability matrix, and where provider-specific constraints (carousel 2–20, media type combinations, text requirements) are validated.
9. **Publication scope:** Confirm whether delete, replies, insights, search, mentions, location tags, polls, quote posts, reposts, and ghost posts remain out of scope for this change. The issue says they are out of scope and the exploration assumes no expansion.
10. **Disconnect semantics:** The issue acceptance mentions disconnect/reconnect smoke coverage, but the current inspected publishing controller/commands expose initiation/completion and channel listing; no Threads-specific disconnect contract was identified. Decide whether disconnect is in scope or only reconnect-required handling is required.
11. **Configuration enablement:** Confirm whether Threads is hidden when configuration is incomplete and whether an explicit `enabled` flag is required. Define safe startup validation behavior for enabled-but-misconfigured production deployments.
12. **External launch evidence:** Assign an owner and environment for Meta test/app-role OAuth and deployed smoke testing, and define the required App Review/app-publishing evidence. Local WireMock/unit tests cannot satisfy this acceptance item.
13. **Product truth update:** Approve changing app product truth from LinkedIn-only early-access wording to “LinkedIn and Threads” for the implemented slice, with any rollout/availability caveat.

## Recommended proposal boundary

Subject to the decisions above, keep the change focused on:

- provider-neutral OAuth authorization/connection lookup and routing;
- `THREADS` account connection and encrypted token lifecycle;
- Threads adapter for text/image/video/carousel container creation, bounded status polling, final publish, and typed failure mapping;
- provider-neutral capabilities and provider catalog/frontend connect metadata;
- existing channel, composer, scheduler, calendar, delivery-attempt, media-library, and SSE reuse;
- focused unit/integration/BDD/Vitest coverage plus separately tracked external Meta/deployed evidence.

Explicitly exclude unrelated engagement/synchronization features and the provider capabilities listed as out of scope by the issue. Do not implement anything during this exploration phase.

## Verification performed

- Worktree status inspected with `git status --short --branch`; unrelated `plan/tasks/dallay-598-threads-provider-integration.md` remains unmodified.
- `openspec/config.yaml`, `openspec/README.md`, current publishing/OAuth/media/channel specs, archived OpenSpec conventions, product files, design system, C4 docs, ADRs, source seams, tests, BDD resources, and environment/deployment configuration were inspected.
- Linear DALLAY-598 metadata and description were read, including acceptance criteria, external launch requirement, and related issues.
- No implementation or test command was run; runtime, build, static analysis, Meta, and deployed evidence are therefore **not run**.
