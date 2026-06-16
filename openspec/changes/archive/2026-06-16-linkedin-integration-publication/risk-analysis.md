# Risk Analysis: LinkedIn Integration Publication

## Scope

This artifact analyzes the open questions raised for `linkedin-integration-publication` against the
current codebase, PRD, LinkedIn integration notes, and existing OpenSpec artifacts. It records
decisions/spec implications only; it does not define implementation tasks.

## 1. LinkedIn product/scopes approval

### Current evidence/files

- PRD requires personal profiles, organization pages, documents/PDF, videos, comments/replies,
  organization lookup/mentions, retries, and notifications (
  `tmp/prd-specs/linkedin/linkedin-integration-publication-prd.md`).
- LinkedIn docs summarize a capability-specific scope matrix: `w_member_social` for member posts,
  `w_organization_social` for organization posts, `*_social_feed` scopes for comments/reactions/feed
  actions, `rw_organization_admin` for organization admin/analytics-style endpoints,
  `r_member_social` as restricted/closed, and Community Management product review as a production
  dependency (`tmp/prd-specs/linkedin/docs/linkedin-integration.md`).
- Current proposal/spec already names Developer Portal readiness and basic publish scopes, but does
  not fully spell out document/video/comment/org-lookup permissions as a capability matrix (
  `proposal.md`, `specs/publishing/spec.md`).
- Current code has one configurable `publishing.linkedin.scopes` string and no per-capability scope
  bundle model in `LinkedInPublishingProperties` (`LinkedInPublishingAdapters.kt`).

### Recommended decision

Treat LinkedIn access as explicit capability bundles rather than one generic connection state:

- **Personal publishing**: require `w_member_social`.
- **Organization publishing**: require `w_organization_social` plus page-role verification.
- **Comments/replies/social actions**: require a separate feed/social-actions bundle using the
  relevant `*_social_feed` scopes, subject to product approval.
- **Documents/videos/media**: gate by Community Management/product access and endpoint-specific
  capability verification; do not assume image upload approval implies document/video publishing.
- **Organization lookup/mentions**: gate separately because lookup/mention APIs may require
  additional organization/community-management permissions and Rest.li formatting.

### Tradeoffs

- Capability bundles increase UX and persistence complexity, but avoid over-asking scopes and make
  partial LinkedIn approval survivable.
- A single broad scope request is simpler, but LinkedIn may invalidate previous tokens when scope
  sets change and product approval may not cover all requested scopes.
- Feature flags per capability allow the MVP to launch with approved member publishing while page
  publishing/comments/documents remain blocked until reviewed.

### Spec implications

- Expand the OAuth scope requirement from member/page publishing only into a scope/capability
  matrix.
- Specify that unsupported/unapproved capability bundles must not be shown as publishable.
- Specify granted scope validation at activation and before provider operations.

## 2. Status mapping/migration

### Current evidence/files

- PRD integration states: `active`, `disabled`, `requires_reconnect`, `deleted`, `pending` (
  `linkedin-integration-publication-prd.md`).
- Current domain status enum only has `ACTIVE`, `REVOKED`, `EXPIRED`, `ERROR` (
  `PublishingModels.kt`).
- `SocialConnection` and `SocialAccount` both persist a `status` string but have no reason/cause
  fields (`001-create-social-connections.yaml`, `002-create-social-accounts.yaml`).
- Existing spec says existing statuses may be reused if API/UI distinguishes active, disabled,
  deleted/revoked, expired, error, and reconnect-required semantics (`specs/publishing/spec.md`).

### Recommended decision

Use the existing enum as the storage baseline for now, but require an explicit semantic/API mapping:

- `ACTIVE` -> PRD `active`.
- `EXPIRED` -> `requires_reconnect` when caused by token expiry/unrefreshable credentials.
- `ERROR` -> `requires_reconnect` only when cause is auth/scope/role; otherwise provider/account
  error.
- `REVOKED` -> `deleted`/disconnected when user-initiated, or revoked by provider when
  provider-initiated.
- Add a distinct `disabled` semantic before launch because manual disable is not equivalent to
  revoked/deleted.

A durable reason/cause is needed to avoid overloading enum values.

### Tradeoffs

- Reusing enum values is migration-light but ambiguous for user messaging and worker behavior.
- Adding first-class `DISABLED`, `DELETED`, `REQUIRES_RECONNECT`, and `PENDING` is clearer but
  requires enum/db/API/frontend migration.
- A reason field plus current enum minimizes enum churn but still needs API contract clarity.

### Spec implications

- Strengthen status mapping language from “MAY be reused” to “MUST expose distinct semantics.”
- Define whether `disabled` and `deleted` are first-class statuses or API-facing derived states.
- Require worker preflight to treat disabled/deleted/reconnect-required accounts as non-publishable
  without calling LinkedIn.

## 3. Token refresh flow missing in worker/publisher path

### Current evidence/files

- PRD requires refresh on expired tokens and reconnect when refresh fails (`UC-12`, `UC-13`).
- LinkedIn docs require absolute access/refresh expiries, non-sliding refresh TTL, refresh-ahead,
  and reconnect fallback (`linkedin-integration.md`).
- Current credential model stores `accessToken`, `refreshToken`, `expiresAtEpochSeconds`, and
  `scope`, but not refresh-token expiry, last refresh attempt, or refresh status (
  `LinkedInCredentialGateway.kt`).
- `RealLinkedInPublisher.resolveAccessToken` reads and returns the stored access token directly; it
  does not refresh ahead or react to expiry (`LinkedInPublishingAdapters.kt`).
- `PublishingJobExecutor` treats provider exceptions generically; no auth-refresh/retry-once path is
  present (`PublishingWorker.kt`).

### Recommended decision

Make token access a provider credential service responsibility that resolves a fresh-enough access
token for the worker/publisher. It should support:

- refresh-ahead when access token is expired/near expiry and a valid programmatic refresh token
  exists;
- retry-once after an auth failure if refresh succeeds;
- reconnect-required state when refresh is absent, expired, revoked, invalid, or scope-deficient;
- atomic credential replacement when LinkedIn rotates tokens.

### Tradeoffs

- Refresh inside `resolveAccessToken` hides complexity from publisher code but can blur side
  effects.
- Refresh in `PublishingJobExecutor` keeps workflow phases explicit but couples the generic worker
  to credential semantics.
- A dedicated provider credential port keeps the generic worker clean and testable, but is an
  additional abstraction.

### Spec implications

- Current token lifecycle requirement is directionally correct; add a stronger requirement that the
  worker/publisher path MUST use the refresh-aware resolver and not raw stored access tokens.
- Specify one-flight/locking behavior per credential to prevent concurrent refresh races.

## 4. Public URL persistence

### Current evidence/files

- PRD requires saving both remote identifier and public URL on success (
  `linkedin-integration-publication-prd.md`).
- Current `PublicationDraft` contains `externalPublicationId` but no public URL (
  `PublishingModels.kt`).
- Current `ProviderPublishResult` contains `externalPublicationId` and `providerMessage`, but no
  URL (`PublishingProviderPorts.kt`).
- `publications` and `delivery_attempts` tables contain `external_publication_id` only; no public
  URL column was found (`004-create-publications.yaml`, `007-create-delivery-attempts.yaml`).
- `RealLinkedInPublisher` returns `x-restli-id` as the external publication id and does not
  derive/store a URL (`LinkedInPublishingAdapters.kt`).

### Recommended decision

Treat public URL as a first-class publication result field, nullable when LinkedIn does not return
or safely derivable URL cannot be established. Persist the remote URN/ID separately from a
user-facing URL.

### Tradeoffs

- Deriving URLs from URNs is useful for immediate user feedback but can be brittle if LinkedIn URL
  patterns vary by author/post type.
- Storing only remote ID is robust but does not satisfy PRD user-notification requirements.
- Nullable URL with clear “unavailable/not derivable” semantics avoids fabricating broken links.

### Spec implications

- Specify that URL persistence is required when returned or safely derivable, and otherwise the
  absence must be explicit.
- Require API/UI to handle a published post with remote ID but no URL.

## 5. Organization pages modeled but validator rejects non-personal accounts

### Current evidence/files

- Domain models include `SocialAccountKind.ORGANIZATION_PAGE` (`PublishingModels.kt`).
- PRD requires connecting and publishing as LinkedIn pages/organizations (
  `linkedin-integration-publication-prd.md`).
- Current `LinkedInCapabilityValidator` has `require(input.socialAccount.kind == PERSONAL_PROFILE)`
  and message “LinkedIn MVP supports personal profiles only” (`LinkedInPublishingAdapters.kt`).
- `RealLinkedInPublisher.buildPostBody` uses `socialAccount.profileUrn` as `author`, so it can
  theoretically support `urn:li:organization:{id}` if validation and connection creation allow it.
- Current connection provider always creates a personal profile account from `/v2/userinfo` with
  `urn:li:person:{sub}` (`LinkedInPublishingAdapters.kt`).

### Recommended decision

Keep organization pages as a separate capability, not a small validator tweak. Organization accounts
should be created only after OAuth scope validation and page-role verification, and should be stored
as separate `ORGANIZATION_PAGE` social accounts with organization URNs.

### Tradeoffs

- Removing the validator check without page-role verification risks failed/unauthorized posts and
  confusing channel state.
- Full organization connection flow adds complexity but matches LinkedIn's scope+role model and PRD
  expectations.
- Phasing page publishing behind a capability flag lets personal publishing remain stable.

### Spec implications

- Current organization text-post scenario is valid but should explicitly require connection-time
  page discovery/verification before account activation.
- Specify non-personal accounts remain non-publishable until org product approval,
  `w_organization_social`, and role checks are satisfied.

## 6. Comments/threads absent

### Current evidence/files

- PRD includes comments/hilos with ordered publication, optional delays, and partial failure
  semantics (`linkedin-integration-publication-prd.md`).
- No comment/thread model, table, repository, or API endpoint was found in the publishing
  domain/persistence search.
- Current publication model is a single `PublicationDraft` with text/assets and a single
  job/result (`PublishingModels.kt`).
- Current delivery attempts have only success/failure and one external publication id; no
  phase/comment child id (`007-create-delivery-attempts.yaml`).
- LinkedIn docs indicate comments/social actions likely require separate feed scopes (
  `linkedin-integration.md`).

### Recommended decision

Do not fold comments into `bodyText`. Model comments as child publication phases or child entities
with their own remote IDs, order, delays, and failure state. Decide product semantics before
implementation: reject unsupported comments at validation time for first release, or allow main-post
success with per-comment partial failures.

### Tradeoffs

- Child entities provide accurate partial success and retry handling, but require more workflow
  state.
- Treating comments as extra body text is simple but violates PRD behavior and LinkedIn comment
  semantics.
- Modeling comments as separate publications may reuse job machinery but can obscure parent/child
  ordering unless explicitly linked.

### Spec implications

- Add a comments/thread requirement only if in target release; otherwise explicitly gate comments as
  not publishable until approved.
- If included, require ordered child state, remote IDs per comment, partial-success behavior, and
  feed-scope validation.

## 7. Video/documents chunked upload/polling and current validator gap

### Current evidence/files

- PRD requires video, PDF/document, and carousel/document workflows with processing wait/polling (
  `linkedin-integration-publication-prd.md`).
- Current validator allows JPEG/PNG/GIF/WEBP and `VIDEO/MP4`, max 10 assets, max 10MB, and no PDF
  media type (`LinkedInPublishingAdapters.kt`).
- Current asset uploader uses legacy-ish `/rest/assets`, reads the entire asset into memory with
  `collectToByteArray`, single PUTs binary content, and calls a one-shot `checkStatus`; it does not
  branch by image/video/document endpoint, chunk uploads, or poll availability (
  `LinkedInAssetUploaderAdapters.kt`).
- `PublicationAssetStatus` has only `READY`, `PROCESSING`, `FAILED` and provider asset ref stores
  only asset id/media/access URL (`PublishingModels.kt`).

### Recommended decision

Differentiate media classes explicitly:

- images can be first supported with simple upload/status semantics;
- videos require single-video validation, chunking/finalization where LinkedIn requires it, and
  processing polling/timeouts;
- PDFs/documents/carrusels require a document-specific endpoint/permission/capability path and title
  metadata.

Do not advertise video/PDF support until the endpoint-specific workflow and limits are verified.

### Tradeoffs

- Keeping 10MB/simple upload makes current MVP safer but under-delivers PRD video/PDF.
- Raising limits without streaming/chunking risks memory pressure and failed large uploads.
- Endpoint-specific upload adapters are more complex but necessary for production media reliability.

### Spec implications

- Current media requirement should add media-type-specific limits and explicitly state
  current/launch capability flags.
- Require streaming/chunk-aware upload for media above in-memory-safe thresholds.
- Require PDF/document type and title validation if document publishing is enabled.

## 8. Idempotency after remote success before local save

### Current evidence/files

- PRD explicitly calls out duplicate prevention when the process crashes after publishing but before
  saving result (`linkedin-integration-publication-prd.md`).
- Current worker records a success delivery attempt after `socialPublisher.publish` returns, then
  marks the publication published; if the process dies between remote success and local writes, the
  local state remains ambiguous (`PublishingWorker.kt`).
- `delivery_attempts` unique constraint is `(publication_job_id, attempt_number)`, which prevents
  duplicate attempt rows for a claimed attempt but not duplicate remote posts across retries (
  `007-create-delivery-attempts.yaml`).
- `PublicationJob`/`DeliveryAttempt` lack an operation key, phase, ambiguous outcome, request hash,
  or remote correlation id beyond successful external id (`PublishingModels.kt`).
- LinkedIn docs note no generic server-side idempotency key for post creation and warn not to rely
  on duplicate-content behavior (`linkedin-integration.md`).

### Recommended decision

Use durable application-level idempotency and ambiguity management, not blind retry:

- persist a publication-level operation key before any remote create;
- record phase transition before post creation and immediately after any returned remote ID;
- represent ambiguous timeout/crash windows explicitly;
- reconcile using read APIs only when scopes allow; otherwise block for operator/user-safe retry
  rather than blindly creating again.

### Tradeoffs

- Stronger idempotency requires schema/workflow changes and may temporarily block retries after
  ambiguous failures.
- Blind retry is simpler but can create duplicate LinkedIn posts, a high-impact user-visible
  failure.
- LinkedIn duplicate detection may reduce duplicates in narrow cases but is not a correctness
  guarantee.

### Spec implications

- Strengthen the existing idempotency requirement to require an operation key/phase model or
  equivalent.
- Add an explicit ambiguous outcome/state beyond `SUCCEEDED`/`FAILED`.
- Specify when blind retry is disallowed.

## 9. Notifications requirement; no subsystem identified

### Current evidence/files

- PRD requires notifications for publish success, failure, reconnect required, disabled integration,
  missing scopes, and media processing failures (`linkedin-integration-publication-prd.md`).
- Current publishing controller has channel change SSE (`GET /api/publishing/channels/events`) but
  this is not a durable user notification subsystem (`PublishingControllers.kt`, `exploration.md`).
- Existing proposal marks `email-notifications` or a future notification capability as potentially
  modified because no notification subsystem was identified (`proposal.md`).
- Current worker only records delivery attempts and publication states; no user notification
  port/event was found in the publishing worker path (`PublishingWorker.kt`).

### Recommended decision

Separate real-time UI events from durable notifications:

- SSE/channel events can satisfy live UI refresh only.
- Product notifications should be modeled as domain/application events that can be delivered by
  whatever notification subsystem exists or is later introduced.
- Until a subsystem is confirmed, the spec should require notification events to be
  emitted/recorded, not assume email/push/in-app delivery mechanics.

### Tradeoffs

- Requiring only event emission avoids blocking LinkedIn publication on a missing notification
  platform, but may not fully satisfy UX expectations.
- Building a notification subsystem as part of this change expands scope beyond LinkedIn publishing.
- Treating SSE as notifications is expedient but fails offline/asynchronous user-feedback
  requirements.

### Spec implications

- Add a clear distinction between publication state updates/SSE and durable user notifications.
- Require a notification event contract with provider, account, publication, outcome, URL when
  available, and suggested action.
- Leave delivery channels as a separate capability decision unless an existing subsystem is
  identified.
