# Proposal: LinkedIn Integration Publication

## Summary

Extend the existing Profile Tailors publishing bounded context from the current LinkedIn
personal-profile MVP toward a production-ready LinkedIn integration and publication capability. The
change keeps the provider-neutral publishing core, but hardens the LinkedIn adapter and connection
lifecycle around modern LinkedIn REST APIs, Spring Boot 3-compatible OAuth2/token practices,
organization/page publishing, richer media workflows, quota-aware retries, privacy constraints, and
proactive reconnect UX.

This proposal intentionally does **not** introduce a separate LinkedIn-only subsystem. Current
exploration found that `server/smp` already has the correct bounded context shape: workspace-scoped
social connections/accounts, HMAC-signed OAuth state, encrypted credentials in `secure_credentials`,
R2DBC repositories, LinkedIn provider adapters, asset upload adapters, database-backed publication
jobs, delivery attempts, and frontend channel/publication flows. The proposal reconciles those
existing seams with LinkedIn's current production requirements and fills the missing
policy/specification gaps.

## Scope

### In Scope

- Document production LinkedIn Developer Portal prerequisites before the feature can be treated as
  launch-ready:
    - LinkedIn app registration.
    - Active company page association.
    - Verification URL approval by a company page admin.
    - Community Management API product review, with expected review lead time of approximately 1-4
      weeks.
    - HTTPS absolute redirect URI registered exactly in the Developer Portal.
    - Client ID and Client Secret provisioned through secure configuration/secrets management.
- Use modern Spring/Spring Boot-compatible integration patterns:
    - Do **not** use obsolete `spring-social-linkedin`.
    - Prefer direct LinkedIn REST communication through the existing provider adapter boundary.
    - Use Spring Security OAuth2 where it adds value, while preserving compatibility with the
      existing custom HMAC `state` and direct token-exchange flow.
- Support LinkedIn 3-legged OAuth2 scope bundles for:
    - Personal profile publishing with `w_member_social`.
    - Organization/page publishing with `w_organization_social` and resource-level page-role
      validation.
- Persist and act on LinkedIn token lifecycle metadata:
    - Access token expiry, typically around 60 days.
    - Refresh token absolute expiry, typically around 365 days when programmatic refresh tokens are
      available.
    - Refresh-token absolute expiry MUST NOT be treated as sliding when used.
    - Store encrypted tokens plus absolute expiry timestamps and refresh/reconnect status metadata.
    - Schedule proactive renewal/reconnect UX before hard expiry.
- Harden LinkedIn API requests:
    - Use `/rest/posts` as the preferred modern post-creation endpoint for this change.
    - Include mandatory `Linkedin-Version: YYYYMM` and `X-Restli-Protocol-Version: 2.0.0` headers
      for `/rest/*` requests.
    - Use LinkedIn URNs consistently: `urn:li:person:{id}`, `urn:li:organization:{id}`, and provider
      media asset URNs.
    - Create public posts with `author`, `commentary`, `visibility: PUBLIC`,
      `distribution.feedDistribution: MAIN_FEED`, and `lifecycleState: PUBLISHED`.
- Extend publication support beyond the current MVP where the PRD requires it:
    - Text posts.
    - Image posts.
    - Video posts.
    - Document/PDF and carousel/document strategy.
    - Organization/page authors.
    - Comments/threads where LinkedIn permissions and product decisions allow.
    - Scheduled and immediate publication.
    - Publication result persistence, including remote identifier and public URL when
      available/derivable.
- Define media upload flow at proposal/spec level:
    - Initialize image/video uploads through `/rest/images` or `/rest/videos` as applicable.
    - Capture upload URL and asset URN.
    - PUT binary content to the upload URL.
    - Poll or wait until the media resource is `AVAILABLE` before creating the post.
    - Include the asset URN in the post content payload.
- Enforce privacy and compliance constraints:
    - Do not persist member profile data longer than 24 hours unless it is minimal connection
      metadata required for the user's explicit integration.
    - Do not persist social activity data longer than 48 hours unless future analytics/compliance
      requirements explicitly redefine retention.
- Handle quotas and resiliency:
    - Account for LinkedIn app-level and member-level quotas, including approximately 100k calls/day
      per app and approximately 100 posts/day per member where applicable.
    - Rate-limit outbound provider calls client-side.
    - Treat 429 and transient 5xx responses as retryable with bounded backoff.
    - Detect 401/invalid credentials, refresh tokens automatically when possible, and mark the
      connection for reconnect when refresh is impossible, expired, revoked, or missing required
      scopes.

### Out of Scope / Non-Goals

- Using `spring-social-linkedin` or any obsolete LinkedIn SDK.
- Scraping LinkedIn or automating engagement in ways that violate LinkedIn policies.
- Advanced analytics/insights dashboards beyond short-lived activity/error data required for
  publication feedback.
- Editing or deleting already-published LinkedIn posts remotely.
- Private messaging/InMail.
- Additional social providers beyond preserving provider-neutral seams.
- A full implementation plan or task breakdown in this phase; this is proposal-only per SDD Kerrigan
  workflow.

## Current Implementation Reconciliation

Exploration found meaningful existing implementation that should be retained and refined rather than
replaced:

- The existing `com.profiletailors.smp.publishing` bounded context already models
  `SocialProvider.LINKEDIN`, social connection status, personal/profile vs organization account
  kinds, publication/job/attempt states, and publication assets.
- OAuth initiation/completion endpoints already exist, and the completion handler already verifies
  HMAC-signed provider/workspace/principal/redirect state before code exchange. This is compatible
  with LinkedIn 3-legged OAuth2 and should remain the primary CSRF/state protection.
- Credentials are already stored through `R2dbcLinkedInCredentialGateway` into `secure_credentials`
  using AES-GCM via `CredentialEncryptionService`. This proposal requires extending the stored
  metadata and behavior around absolute expiry, refresh attempts, and reconnect state rather than
  introducing a new token store.
- `RealLinkedInConnectionProvider` currently exchanges tokens and calls `/v2/userinfo`; that can
  remain for basic member identity/avatar discovery, subject to LinkedIn's OIDC nonce nuance
  described below.
- `RealLinkedInPublisher` already targets `/rest/posts`, and `LinkedInAssetUploaderAdapters.kt`
  already contains a real provider asset upload/status flow. This proposal aligns those adapters
  with the current Posts API shape, required headers, author URNs, image/video initialization
  endpoints, status polling, error taxonomy, and organization support.
- The current worker already records attempts and treats 429/5xx as retryable. This proposal
  strengthens the classification by adding 401 refresh/reconnect behavior, quota-aware rate
  limiting, media-processing phases, and terminal non-retryable cases for validation/scope/role
  failures.
- Current organization account kinds exist, but the validator is MVP-oriented and rejects
  non-personal accounts. This proposal promotes organization/page publishing to an explicit target
  capability with role/scope verification instead of treating it as a future model placeholder.
- Current code appears to use WebFlux/R2DBC and existing adapters are likely asynchronous/reactive.
  Although Spring Boot 3.2 `RestClient` is the modern choice for Web MVC synchronous stacks, the
  repo context and existing WebFlux/R2DBC architecture favor continuing with `WebClient` or a
  consistent reactive HTTP abstraction rather than mixing blocking `RestClient` into reactive
  workers. If a future module is Web MVC/blocking, it SHOULD use `RestClient` behind the same
  provider port.

### Spring Security OIDC Nonce Nuance

LinkedIn's OIDC response does not include/support a nonce in the way Spring Security's OIDC
validator expects. If this change elects to move any part of the connection flow to Spring Security
OAuth2/OIDC client login mechanics, the implementation MUST configure a custom
`AuthorizationRequestResolver` that removes the `nonce` parameter and corresponding validation
expectation to avoid nonce validation exceptions.

Because the current implementation already uses custom HMAC state plus direct token exchange, the
compatible direction is to preserve that flow for LinkedIn publishing connections unless a clear
benefit justifies migration. Spring Security OAuth2 can still be used for reusable
client/token-management primitives where it does not conflict with LinkedIn's OIDC behavior.

## Implementation Strategy

1. **Keep provider-neutral publishing core stable.** Extend existing domain/application ports for
   capabilities, token refresh/reconnect, media phase status, organization authors, public URL
   persistence, and comments without embedding LinkedIn payload details in the domain.
2. **Harden LinkedIn infrastructure adapters.** Centralize LinkedIn REST client behavior in adapter
   code that always sets version/protocol headers, maps URNs, uses DTO serialization rather than
   hand-built JSON, classifies provider errors, and owns `/rest/posts`, `/rest/images`,
   `/rest/videos`, and any document/comment endpoints chosen later.
3. **Make token lifecycle explicit.** Persist access-token expiry, refresh-token absolute expiry,
   last refresh attempt/status, granted scopes, and reconnect-required causes. Refresh access tokens
   ahead of expiry; block automatic refresh when the refresh token is near absolute expiry and
   surface reconnect UX.
4. **Promote organization/page publishing deliberately.** Store organization accounts as separate
   social accounts, validate `w_organization_social`, verify the authenticated member's page role
   before marking the page publishable, and publish with `urn:li:organization:{id}` authors.
5. **Use phased media orchestration.** Worker execution should become explicit enough to audit
   validation, upload initialization, binary PUT, media availability wait, post creation, and
   optional comments. Provider asset refs should be persisted for idempotency and retry.
6. **Add quota-aware resilience.** Introduce provider/member/endpoint rate limiting before outbound
   calls, bounded retries with jitter for 429/5xx/409 where appropriate, and terminal handling for
   400/401-unrefreshable/403/422 validation or permission failures.
7. **Minimize and expire provider-derived data.** Keep only safe durable connection metadata
   necessary for the user's integration. Treat profile details and social activity as
   short-retention data according to the 24h/48h requirements.

## Affected Specs / Capabilities

| Capability / Spec                                       |               Impact | Notes                                                                                                                                                              |
|---------------------------------------------------------|---------------------:|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `publishing`                                            |             Modified | Main capability for social connections, tokens, LinkedIn REST publishing, organization pages, media, retries, privacy, and result persistence.                     |
| `channel-list-api`                                      |             Modified | Channel summaries need reconnect/disabled/expiry warning states and organization-page channels.                                                                    |
| `channel-events-sse`                                    |             Modified | Should emit reconnect/status changes and publication result changes as progressive updates.                                                                        |
| `oauth-initiation-api`                                  |             Modified | Scope bundles, Developer Portal prerequisites, HTTPS redirect URI, HMAC state, and optional Spring Security nonce workaround.                                      |
| `oauth-callback-ui`                                     |             Modified | Reconnect UX and scope/role failure feedback.                                                                                                                      |
| `email-notifications` or future notification capability | Potentially Modified | Publication success/failure/reconnect-required notifications are required by the PRD, but exploration did not confirm a user notification subsystem in publishing. |
| `credentials`                                           |             Modified | Token storage already exists but must include absolute expiries, refresh attempt status, and proactive renewal metadata.                                           |
| `dashboard-scheduling` / `visual-calendar`              |             Modified | Existing scheduled publication UI should surface failed/reconnect/processing outcomes and avoid backend truth being masked by mock/local fallback.                 |

## Migration and Data Needs

- Add or verify durable fields for encrypted LinkedIn token metadata:
    - access token expiry timestamp,
    - refresh token expiry timestamp,
    - absolute refresh expiry warning threshold,
    - granted scopes,
    - last refresh attempt timestamp/status,
    - reconnect-required reason.
- Add or verify status mappings between PRD states and existing enums:
    - PRD `active` -> existing `ACTIVE`.
    - PRD `requires_reconnect` -> likely existing `EXPIRED` or `ERROR` plus explicit reconnect
      reason, or a new enum if product needs a first-class state.
    - PRD `disabled` -> may require a new status or mapping from `REVOKED` depending on semantics.
    - PRD `deleted` -> likely `REVOKED` plus soft-delete/disconnect metadata, or a new status if
      deletion must be distinct.
- Add or verify publication result fields for LinkedIn post URN/ID and public URL.
- Add or verify provider asset state fields for media initialization, upload URL/URN, status
  polling, `AVAILABLE`/failed/timeout outcomes, and retry eligibility.
- Add organization/page metadata needed for publishing:
    - organization URN/id,
    - display name/avatar if allowed,
    - validating member principal,
    - page role/capability status,
    - scope bundle used.
- If comments/threads are in the target release, add child publication/comment records or equivalent
  phase records capable of partial success semantics.
- Retention jobs or cleanup policies are needed for LinkedIn member profile data older than 24h and
  social activity data older than 48h.

## Security and Compliance Considerations

- Tokens, authorization codes, refresh tokens, and client secrets MUST NOT be exposed to the
  frontend or unsafe logs.
- Token values MUST remain encrypted at rest. Token hashes MAY be stored for
  diagnostics/deduplication if they do not reveal bearer secrets.
- OAuth callbacks MUST validate signed state, workspace, principal, provider, redirect URI,
  nonce/expiry, and scopes before activation.
- Redirect URIs MUST be HTTPS, absolute, and exactly registered in LinkedIn Developer Portal.
- Scope requests SHOULD be minimized and grouped by capability bundle: member publish, organization
  publish, and later analytics/feed interactions if approved.
- LinkedIn organization publishing MUST validate both OAuth scope and member role on the target page
  before treating a page as publishable.
- Provider-derived profile data and social activity data MUST honor the 24h/48h retention limits
  unless an explicit future compliance decision changes the retention policy.
- Logs and metrics SHOULD include correlation IDs, endpoint path, LinkedIn version, status, mapped
  error code, actor/account URN, and delivery attempt ID, but MUST redact tokens and secrets.

## Risks

| Risk                                                                                    | Likelihood | Impact | Mitigation                                                                                                                             |
|-----------------------------------------------------------------------------------------|-----------:|-------:|----------------------------------------------------------------------------------------------------------------------------------------|
| LinkedIn product review/API access blocks organization publishing                       |       High |   High | Document Developer Portal prerequisites, begin review early, feature-flag org publishing until approved.                               |
| Token refresh availability differs by app/product approval                              |       High |   High | Support both programmatic refresh when available and reconnect UX when refresh is unavailable/expired/revoked.                         |
| Refresh token expiry treated incorrectly as sliding                                     |     Medium |   High | Persist absolute refresh expiry and schedule proactive reconnect before hard expiry.                                                   |
| Spring Security OIDC nonce validation breaks LinkedIn callback                          |     Medium | Medium | Preserve current HMAC/direct exchange flow, or configure custom resolver to remove nonce if using Spring Security OIDC.                |
| Mixing blocking `RestClient` with reactive worker causes resource issues                |     Medium | Medium | Continue using WebClient/consistent reactive adapters in this WebFlux/R2DBC architecture; reserve RestClient for blocking MVC modules. |
| Media/document/video APIs require different product permissions or processing semantics |     Medium |   High | Keep media phases explicit, validate permissions/capabilities, and phase rollout by media type.                                        |
| Duplicate posts after ambiguous timeouts                                                |     Medium |   High | Use persisted jobs/attempts/provider refs, operation keys, and cautious reconciliation rather than blind replay.                       |
| Privacy retention requirements conflict with future analytics                           |     Medium | Medium | Keep analytics/social activity out of scope for this change unless retention policies are explicitly extended.                         |
| Quota/rate-limit assumptions differ from actual app limits                              |     Medium | Medium | Implement adaptive client-side rate limiting and rely on LinkedIn Developer Portal analytics for operational tuning.                   |

## Rollout Plan

1. **Configuration and readiness gate:** Add operational readiness checks for LinkedIn client
   config, version header, redirect URI, approved scopes/products, and organization-product
   availability.
2. **Token lifecycle hardening:** Deploy additive credential metadata and refresh/reconnect state
   before enabling long-lived scheduled publishing.
3. **Personal publishing hardening:** Ensure `/rest/posts` personal text/image publishing uses
   mandatory headers, URNs, retry classification, remote ID/URL persistence, and privacy controls.
4. **Media rollout by type:** Enable images first, then video/document/PDF/carrusel flows behind
   capability flags after upload/status behavior is verified.
5. **Organization/page beta:** Enable page connection and page-author publishing only for approved
   LinkedIn app/product access and verified test pages.
6. **Comments/threads:** Gate behind a product decision and capability flag because partial success
   semantics and additional scopes increase workflow complexity.
7. **Operational monitoring:** Watch token refresh failures, reconnect prompts, 429 rates, publish
   success, media timeouts, and duplicate/ambiguous outcomes.

## Rollback Plan

- Feature-flag LinkedIn organization/page publishing, media types beyond images, and
  comments/threads so each can be disabled independently without removing the core publishing
  module.
- If provider instability occurs, pause the publishing worker for LinkedIn or disable only real
  LinkedIn adapter execution while preserving queued data for later retry.
- Keep additive migrations backward-compatible; avoid destructive rollback of credential/publication
  data. If a schema issue reaches production, prefer a forward-fix migration and endpoint/worker
  disablement over dropping tables or columns.
- Existing personal-profile MVP paths should remain available unless the rollback explicitly
  disables all LinkedIn publishing.

## Acceptance Criteria

- [ ] Proposal/spec artifacts define LinkedIn publishing around modern REST APIs and explicitly
  reject obsolete `spring-social-linkedin` usage.
- [ ] Developer Portal prerequisites and expected Community Management API review timeline are
  documented.
- [ ] OAuth requirements specify 3-legged OAuth2, `w_member_social`, `w_organization_social`, HMAC
  state compatibility, and Spring Security OIDC nonce workaround if applicable.
- [ ] Token lifecycle requirements include encrypted storage, access-token expiry, absolute
  refresh-token expiry, proactive renewal/reconnect UX, and automatic refresh/reconnect handling on
    401.
- [ ] LinkedIn REST requirements specify `/rest/posts`, required headers, URN authors, public
  visibility, main-feed distribution, and published lifecycle state.
- [ ] Media requirements specify initialize/upload/status-available/post flow for
  images/videos/documents as capability allows.
- [ ] Privacy requirements specify 24h member profile retention and 48h social activity retention.
- [ ] Quota/error requirements specify rate limiting, bounded retry for 429/5xx, and terminal
  handling for unrefreshable auth/scope/role/validation failures.
- [ ] Current implementation is reconciled, including keeping the provider-neutral publishing
  bounded context, existing HMAC state, encrypted credential gateway, WebFlux-compatible HTTP client
  direction, and worker/attempt model.
