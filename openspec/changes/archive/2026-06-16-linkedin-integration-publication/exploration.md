# Exploration: linkedin-integration-publication

## PRD Goals

- Connect LinkedIn personal profiles via OAuth and persist the integration securely.
- Connect LinkedIn organization/page channels when the user has sufficient permissions.
- Publish immediately or schedule future LinkedIn posts.
- Support text, images, video, PDF/document, image-carousel/document strategies, comments/threads
  where supported, provider retry limits, token refresh/reconnect, user notifications, and
  publication result persistence.
- Keep the design provider-neutral so future social providers can reuse the publishing core.

## Current Architecture Findings

### Backend publishing bounded context

- `server/smp` already has a `com.profiletailors.smp.publishing` bounded context with
  domain/application/infrastructure layering.
- Core models exist in `PublishingModels.kt`:
    - `SocialProvider.LINKEDIN`
    - `SocialConnectionStatus.ACTIVE/REVOKED/EXPIRED/ERROR`
    - `SocialAccountKind.PERSONAL_PROFILE/ORGANIZATION_PAGE`
    - `PublicationStatus.DRAFT/QUEUED/SCHEDULED/PROCESSING/PUBLISHED/FAILED/CANCELLED`
    - `PublicationAsset`, `PublicationJob`, `DeliveryAttempt`
- OAuth connection endpoints already exist in `PublishingControllers.kt`:
    - `POST /api/publishing/linkedin/connections/initiate`
    - `POST /api/publishing/linkedin/connections/complete`
- Channel endpoints already exist:
    - `GET /api/publishing/channels`
    - `GET /api/publishing/channels/providers`
    - `GET /api/publishing/channels/events` SSE
- Publication endpoints already exist:
    - `POST /api/publishing/publications`
    - `PATCH /api/publishing/publications/{publicationId}`
    - `POST /api/publishing/publications/{publicationId}/cancel`
    - `POST /api/publishing/publications/{publicationId}/retry`
    - `PATCH /api/publishing/publications/{publicationId}/reschedule`
    - `POST /api/publishing/publications/quick-create`
    - `GET /api/publishing/publications/calendar`
    - `GET /api/publishing/publications` is still a placeholder.

### OAuth/auth/workspace patterns

- Publishing handlers require authenticated principal and active workspace context from
  `PrincipalContextProvider` and `ResourceContextProvider`.
- Workspace scoping is carried by `X-Workspace-Id`; frontend `auth-api.ts` now supports
  `workspaceScoped: true` and injects the header.
- OAuth state is HMAC-signed by `HmacOAuthStateSigner` and includes provider, workspaceId,
  principalId, redirectUri, nonce, issuedAt, expiresAt.
- `CompleteLinkedInConnectionHandler` verifies state provider/workspace/principal/redirect URI
  before exchanging the authorization code.
- LinkedIn credentials are stored via `R2dbcLinkedInCredentialGateway` into `secure_credentials`,
  encrypted by `CredentialEncryptionService` with AES-GCM.

### LinkedIn provider adapters

- `LinkedInPublishingAdapters.kt` contains:
    - `LinkedInPublishingProperties` from `publishing.linkedin.*` env/config.
    - `RealLinkedInConnectionProvider` for token exchange and `/v2/userinfo` profile lookup.
    - `LinkedInCapabilityValidator`, currently MVP-oriented: personal profiles only, max 10 assets,
      supported media types include images and `VIDEO/MP4`, max asset size 10MB.
    - `RealLinkedInPublisher`, which builds `/rest/posts` requests and embeds uploaded asset URNs as
      `contentEntities`.
- `LinkedInAssetUploaderAdapters.kt` implements real LinkedIn asset registration/upload/status flow
  and stores provider asset refs back on `PublicationAsset`.
- Real publisher resolves access tokens through
  `SocialAccount.socialConnectionId -> SocialConnection.credentialReference -> LinkedInCredentialGateway`.

### Persistence and jobs

- Liquibase publishing changelogs already exist for social connections/accounts, publications,
  assets, asset links, jobs, attempts, secure credentials, provider asset refs, file sizes,
  scheduled indexes, and social account avatars.
- R2DBC repositories implement upsert/list/find patterns. Social connection/account upserts now use
  `ON CONFLICT` semantics.
- Background publishing uses a database-backed worker:
    - `PublishingWorkerLifecycle` starts only when `publishing.worker.enabled=true`.
    - `PublicationJobRepository.claimNextDue` drives due work.
    - `PublishingJobExecutor` loads publication/account/assets, validates capabilities, publishes,
      records `DeliveryAttempt`, then marks publication/job as published, retry-waiting, or failed.
- Retry behavior currently treats `RetryablePublishingException` as retryable; LinkedIn publisher
  maps HTTP 429 and 5xx to retryable failures.

### Frontend publishing UI

- `apps/web/app/src/stores/publishing.ts` already has actions for channel loading, LinkedIn connect
  initiation, callback completion, SSE subscription, calendar fetching, quick-create, reschedule,
  and scheduling posts.
- `LinkedInCallbackView.vue` handles `code/state/error` and calls
  `completeLinkedInConnectionFromCallback`, then returns to settings.
- `SettingsView.vue` and `AppShell.vue` expose LinkedIn connect actions and connected channel
  display.
- Some local/mock publication fallback remains in the store for unauthenticated/offline fallback.

### Tests and conventions

- Backend tests exist for publishing handlers, controllers, R2DBC repositories, worker, queue
  integration, workspace isolation, LinkedIn adapters, OAuth state signer, authorization URL
  builder, events, credentials.
- Frontend tests exist for publishing store, auth API workspace header behavior, callback view,
  settings view, create post modal, router, app shell.

## Impacted Areas

Likely affected files/components for the next phases:

- Backend domain/application:
    - `PublishingModels.kt` — state taxonomy may need `requires_reconnect` equivalent or status
      mapping.
    - `PublishingProviderPorts.kt` — provider contracts may need organization pages, comments,
      refresh, media-processing status, public URL.
    - `PublishingRepositories.kt` — extra update methods for integration status, remote URL,
      attempts/audit if missing.
    - `PublishingApi.kt` / `PublishingHandlers.kt` — create/edit/retry/asset/comment flows and
      validation responses.
- Backend infrastructure:
    - `PublishingControllers.kt` — missing list publications, asset upload endpoint exposure,
      org/page connection endpoints, comment endpoints if in scope.
    - `LinkedInPublishingAdapters.kt` — organization author URNs, refresh-token handling, richer
      LinkedIn error classification, public URL extraction, comments.
    - `LinkedInAssetUploaderAdapters.kt` — video/document/PDF status handling and chunking strategy.
    - `R2dbcPublishingRepositories.kt` and Liquibase changelogs — publication remote URL,
      comments/thread records, integration scopes/expiry/status if not already represented.
    - `application.yaml` — timeouts, media limits, LinkedIn organization scopes, refresh/media
      polling configs.
- Frontend:
    - `publishing.ts` store — real asset upload flow, comments/thread model, retry/reconnect UX,
      remove/limit local fallback where product expects backend truth.
    - Create/schedule modal components — media/document/carousel/comment UI and validation feedback.
    - Settings/channels UI — organization page connection, reconnect/disabled states.
- Tests:
    - Application handler tests, controller tests, LinkedIn adapter tests, worker retry tests, R2DBC
      tests, frontend store/view tests.

## Recommended Design Direction

1. Treat the existing publishing bounded context as the core; avoid creating a separate
   LinkedIn-only subsystem.
2. Phase implementation by capability:
    - Phase 1: close current MVP gaps: robust text publish result URL, publication list endpoint,
      reconnection/status mapping, token refresh decision, notifications/audit shape.
    - Phase 2: media hardening: images first, then video/PDF/document with explicit processing
      timeout and size strategy.
    - Phase 3: organization pages: separate connection/account records with `ORGANIZATION_PAGE`,
      admin permission verification, organization URNs, page-specific scopes.
    - Phase 4: comments/threads and delays, since partial success semantics add workflow complexity.
3. Keep provider-neutral domain models but isolate LinkedIn-specific payloads, scope names, URN
   construction, upload protocols, and error mapping in infrastructure adapters.
4. Extend worker execution into explicit phases for validation → upload → processing wait → post →
   comments; persist enough attempt/failure details for audit and idempotency.
5. Prefer backend-owned validations and publishability checks, with frontend mirroring limits for UX
   only.

## Risks / Open Questions

- LinkedIn product/scopes approval: exact permissions for personal publishing, organization
  publishing, documents, videos, comments, and organization lookup must be confirmed.
- PRD uses statuses `requires_reconnect`, `disabled`, `deleted`; current code has `REVOKED`,
  `EXPIRED`, `ERROR`. Need a mapping or enum migration decision.
- Token refresh is stored conceptually (`refreshToken`, expiry) but no refresh flow was found in the
  worker/publisher path.
- Public URL persistence is required by PRD, but current `ProviderPublishResult` appears focused on
  external id/message; confirm whether URL column/model exists.
- Organization pages are modeled but current validator rejects non-personal accounts.
- Comments/threads are not apparent in current models; implementing them likely needs new
  tables/entities/jobs or child publication semantics.
- Video and documents may require chunked upload and polling; current capability validator limits
  assets to 10MB and supported types do not include PDF.
- Idempotency after remote success but before local save remains hard; delivery attempts help, but
  remote duplicate prevention strategy needs explicit design.
- Notifications are required by PRD; no user notification subsystem was identified in this
  exploration.

## Existing OpenSpec Context

Relevant archived changes already exist and should be used as source material in proposal/design
phases:

- `openspec/changes/archive/2026-05-27-linkedin-publishing-mvp/`
- `openspec/changes/archive/linkedin-media-upload/`
- `openspec/changes/archive/2026-06-13-connect-spa-channels-to-linkedin/`
- `openspec/changes/archive/2026-06-14-linkedin-channel-avatars/`

## Artifact Notes

- Created this exploration artifact only.
- No proposal/spec/tasks were created during exploration phase.
