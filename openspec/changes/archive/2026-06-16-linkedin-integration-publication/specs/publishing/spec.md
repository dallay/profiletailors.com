# Delta for Publishing — LinkedIn Integration Publication

## Preamble

This delta applies ON TOP OF the following archived specs:

- `archive/2026-05-27-linkedin-publishing-mvp/specs/publishing/spec.md`
- `archive/linkedin-media-upload/specs/publishing-media-upload.md`
- `archive/2026-06-13-connect-spa-channels-to-linkedin/specs/publishing/spec.md`

It REPLACES requirements that conflict with the delta. It ADDS new requirements not covered by the
archived specs.

This change does not require a separate design phase. The existing bounded context, adapter pattern,
and provider-neutral architecture provide sufficient design foundation. Technical decisions are
captured in the `risk-analysis.md` and embedded in these requirements. The `risk-analysis.md` file
is a supporting artifact of this change.

Additional delta specs for `oauth-initiation-api`, `oauth-callback-ui`, and other affected
capabilities may be created in subsequent iterations or as part of the design phase notes.

## MVP Definition

For this change, MVP is defined as: LinkedIn personal-profile text and image publishing with
immediate and scheduled publication, encrypted token storage with refresh-aware resolution,
publication result persistence with remote ID, public URL nullable for MVP, durable notification
events, and provider-neutral publishing architecture preserved. Organization/page publishing, video,
PDF/document, carousel, comments/threads, and analytics are explicitly gated/non-MVP.

## ADDED Requirements

### Requirement: LinkedIn Capability-Bundled Integration Model

The system MUST model LinkedIn publishing as a set of explicit capability bundles rather than as one
monolithic "LinkedIn connected" integration.

Each LinkedIn social account MUST declare the capabilities it is eligible to use, the OAuth scopes
granted for those capabilities, the resource kind the capability applies to, and whether the
capability is available, gated, unsupported, or disabled. Capability evaluation MUST be performed
before OAuth activation, before publication validation, and again immediately before worker
execution.

The initial capability matrix MUST include at least:

| Capability bundle                  | MVP status                                      | Required grant / verification                                                   | Notes                                                                                                        |
|------------------------------------|-------------------------------------------------|---------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Personal profile text publishing   | Supported                                       | `w_member_social`; author `urn:li:person:{id}`                                  | Launch MVP.                                                                                                  |
| Personal profile image publishing  | Supported                                       | `w_member_social`; validated image upload workflow                              | Launch MVP after media endpoint workflow validation.                                                         |
| Organization/page text publishing  | Supported but gated                             | `w_organization_social`; organization URN; sufficient page role                 | Only enabled when LinkedIn app/product access and page role checks pass.                                     |
| Organization/page image publishing | Supported but gated                             | `w_organization_social`; sufficient page role; validated image workflow         | Same org gate as text plus media gate.                                                                       |
| Organization mentions              | Gated / non-MVP                                 | Organization search/resolution API, mention syntax validation                   | Requires organization search/resolution API, mention syntax validation, and organization-not-found handling. |
| Video publishing                   | Gated / non-MVP                                 | Endpoint-specific video upload, finalize, processing, and permission validation | MUST NOT be required for launch MVP.                                                                         |
| PDF/document publishing            | Gated / non-MVP                                 | Endpoint-specific document workflow and permission validation                   | MUST NOT be required for launch MVP.                                                                         |
| Carousel publishing                | Gated / non-MVP                                 | Explicit product strategy and endpoint-specific workflow validation             | Strategy not defined for MVP. MUST NOT be required for launch MVP.                                           |
| Comments / threads                 | Gated / unsupported for first implementation    | Explicit future product enablement and feed/comment scopes                      | MUST NOT be required for launch MVP.                                                                         |
| Analytics / insights               | Out of scope for this change unless later added | Analytics-specific scopes                                                       | This change may preserve extension points but MUST NOT require analytics delivery.                           |

#### Scenario: Unsupported capability is rejected before provider calls

- GIVEN a workspace has an active LinkedIn personal-profile account with text and image publishing
  capabilities only
- WHEN a publication requests LinkedIn video, PDF/document, carousel, comments, or another gated
  capability that is not enabled
- THEN the system MUST reject or block the publication before calling LinkedIn
- AND the user-facing result MUST identify the capability as gated or unsupported rather than
  reporting a generic provider failure

#### Scenario: Organization page capability is evaluated independently

- GIVEN a user has connected a LinkedIn personal profile
- WHEN the user attempts to publish as an organization page
- THEN the system MUST require a separate organization/page account or capability grant
- AND MUST NOT infer organization publishing eligibility from personal publishing eligibility alone

#### Scenario: Organization mention is rejected when mention capability is gated

- GIVEN a LinkedIn publication includes an organization mention syntax
- AND the organization mentions capability is not enabled for the account and environment
- WHEN the publication is validated or executed
- THEN the system MUST reject or block the publication before calling LinkedIn
- AND the result MUST identify organization mentions as gated/non-MVP

### Requirement: LinkedIn Developer Portal Readiness

The system and operating runbook MUST define the external LinkedIn Developer Portal prerequisites
required before LinkedIn integration publication can be enabled for production users.

The readiness criteria MUST include LinkedIn app registration, Client ID/Client Secret provisioning
through secure configuration, an HTTPS absolute redirect URI registered exactly in LinkedIn
Developer Portal, an active company page association when organization/page publishing is enabled,
verification URL approval by a company page administrator, and required LinkedIn product access
review such as Community Management API access for organization/page management. Any product review
lead time MUST be tracked as a launch dependency, and organization/page publishing MUST remain gated
until the required access is approved.

#### Scenario: Production enablement is blocked until prerequisites are satisfied

- GIVEN LinkedIn publishing is configured for a production environment
- WHEN required LinkedIn app credentials, HTTPS redirect URI, page association, verification
  approval, or product access are missing
- THEN the system or deployment readiness process MUST treat real LinkedIn publishing as not
  launch-ready
- AND organization/page publishing MUST remain disabled until the required product access and page
  verification are complete

### Requirement: Modern Provider-Adapter Integration Approach

The LinkedIn integration MUST use modern Spring/Spring Boot-compatible OAuth2 and REST integration
patterns and MUST NOT use obsolete LinkedIn SDKs such as `spring-social-linkedin`.

LinkedIn provider communication MUST be isolated behind the existing publishing provider-adapter
boundary. In a WebFlux/R2DBC path, the implementation SHOULD use the existing reactive HTTP-client
approach such as `WebClient`. In a blocking Spring MVC path, the implementation SHOULD use Spring
Boot `RestClient`. The domain/application layer MUST NOT depend on the concrete HTTP client or
LinkedIn DTOs.

#### Scenario: Provider adapter owns LinkedIn HTTP details

- GIVEN the publishing worker dispatches a LinkedIn publication
- WHEN the system sends OAuth, media, or post requests to LinkedIn
- THEN the LinkedIn infrastructure adapter MUST perform the HTTP communication
- AND the provider-neutral publishing domain MUST remain independent of `WebClient`, `RestClient`,
  or LinkedIn payload details

### Requirement: LinkedIn Connection Status Semantics

LinkedIn connections MUST expose production-facing status semantics using first-class persisted
states `PENDING`, `ACTIVE`, `DISABLED`, `REQUIRES_RECONNECT`, `DELETED`, and `ERROR`.

The system MUST persist `DISABLED` and `REQUIRES_RECONNECT` as first-class states in the database.
`PENDING` and `DELETED` MAY be exposed through API mapping from existing internal states if full
migration is deferred.

The status semantics MUST be:

| State                |                       Publishable | Semantics                                                                                            |
|----------------------|----------------------------------:|------------------------------------------------------------------------------------------------------|
| `PENDING`            |                                No | OAuth or setup flow started but not fully validated/activated.                                       |
| `ACTIVE`             | Yes, subject to capability checks | Required credentials, scopes, and resource-role checks are currently valid.                          |
| `DISABLED`           |                                No | User or system has intentionally paused the account without deleting credentials/history.            |
| `REQUIRES_RECONNECT` |                                No | OAuth grant, token refresh, scope, or role state requires user reauthorization or permission repair. |
| `DELETED`            |                                No | Account was disconnected/soft-deleted and MUST NOT be used for new publications.                     |
| `ERROR`              |                     No by default | Non-reconnect operational error exists; recovery requires explicit classification before publishing. |

When a scheduled post targets a non-publishable LinkedIn account, the worker MUST NOT call LinkedIn
and MUST block the publication according to product rules with clear user guidance.

#### Scenario: Scheduled publication for reconnect-required account does not call LinkedIn

- GIVEN a scheduled publication targets a LinkedIn account whose status is `REQUIRES_RECONNECT`
- WHEN the publication becomes due
- THEN the worker MUST NOT call LinkedIn for that publication
- AND the publication MUST be marked `BLOCKED` with a reconnect-required reason
- AND a durable notification event MUST be recorded for the user-actionable reconnect requirement

#### Scenario: Disabled account is distinct from deleted account

- GIVEN a user disables a LinkedIn account without disconnecting it
- WHEN future publication jobs evaluate that account
- THEN the account MUST be treated as non-publishable with status `DISABLED`
- AND the system MUST preserve the distinction from `DELETED` for audit/history and possible
  re-enable behavior

### Requirement: LinkedIn Publication Lifecycle States

The system MUST define publication lifecycle states that include `BLOCKED` as a distinct state
from `FAILED`.

The publication lifecycle states MUST be:

| State        | Reversible | Semantics                                                                                                                                                                                                                                                                                                                                        |
|--------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DRAFT`      | Yes        | Publication is being composed, not yet submitted for scheduling.                                                                                                                                                                                                                                                                                 |
| `QUEUED`     | Yes        | Publication has been submitted and is awaiting scheduling.                                                                                                                                                                                                                                                                                       |
| `SCHEDULED`  | Yes        | Publication is scheduled for a future time.                                                                                                                                                                                                                                                                                                      |
| `PROCESSING` | No         | Publication execution is underway (validation, media upload, post creation).                                                                                                                                                                                                                                                                     |
| `PUBLISHED`  | No         | Publication succeeded with a remote LinkedIn post identifier.                                                                                                                                                                                                                                                                                    |
| `BLOCKED`    | Yes        | Publication cannot proceed due to non-publishable account status (DISABLED, REQUIRES_RECONNECT). When the account status is restored to ACTIVE, blocked publications MUST be automatically retried with exponential backoff (initial delay 1 minute, max delay 1 hour, max 5 retries). After max retries, the publication transitions to FAILED. |
| `FAILED`     | No         | Publication failed terminally due to an irrecoverable error, DELETED account, or max retries exhausted.                                                                                                                                                                                                                                          |
| `CANCELLED`  | No         | Publication was cancelled by the user or system before completion.                                                                                                                                                                                                                                                                               |

#### Scenario: Publication for DISABLED account is blocked and may retry on re-enable

- GIVEN a LinkedIn account is in `DISABLED` status
- AND there are scheduled or queued publications targeting that account
- WHEN the worker evaluates the publication for execution
- THEN the publication MUST be marked `BLOCKED` with a disabled-account reason
- AND a durable notification event MUST be recorded
- AND when the account status is restored to `ACTIVE`, blocked publications MUST be automatically
  retried using exponential backoff (initial delay 1 minute, max delay 1 hour, max 5 retries)
- AND after max retries the publication MUST transition to `FAILED`

#### Scenario: Publication for DELETED account fails terminally

- GIVEN a LinkedIn account is in `DELETED` status
- AND there are scheduled or queued publications targeting that account
- WHEN the worker evaluates the publication for execution
- THEN the publication MUST be marked `FAILED` with a deleted-account reason
- AND the system MUST NOT automatically retry the publication
- AND a durable notification event MUST be recorded

#### Scenario: BLOCKED publications auto-retry with exponential backoff

- GIVEN a publication is marked `BLOCKED` due to a DISABLED or REQUIRES_RECONNECT account
- WHEN the account status is restored to `ACTIVE`
- THEN the system MUST automatically retry blocked publications using exponential backoff
- AND the initial delay MUST be 1 minute, with max delay of 1 hour
- AND a maximum of 5 retries is allowed
- AND after max retries the publication MUST transition to `FAILED`

### Requirement: LinkedIn OAuth Scope Bundles and State Validation

The system MUST use LinkedIn 3-legged OAuth2 for delegated LinkedIn publishing connections and MUST
request scopes according to the selected capability bundle.

Personal-profile publishing MUST request and validate `w_member_social`. Organization/page
publishing MUST request and validate `w_organization_social` and MUST additionally verify that the
authenticated LinkedIn member has a page role sufficient to publish for the target organization.
OAuth callbacks MUST validate signed state, workspace, principal, provider, redirect URI, expiry,
and granted scopes before activating the connection.

If Spring Security OAuth2/OIDC client machinery is used for LinkedIn authorization, the
implementation MUST account for LinkedIn's OIDC nonce behavior by configuring a custom authorization
request resolver or equivalent customization that removes `nonce` and avoids nonce validation
failures. If the existing custom HMAC state plus direct token-exchange flow is retained, that flow
MUST remain the CSRF/state authority.

#### Scenario: Personal profile connection validates member publishing scope

- GIVEN an authenticated workspace member completes LinkedIn OAuth for a personal profile
- WHEN LinkedIn returns tokens and granted scopes
- THEN the system MUST activate the personal LinkedIn connection only if `w_member_social` was
  granted
- AND the system MUST reject activation when the signed OAuth state is invalid, expired, or
  mismatched

#### Scenario: Organization page connection validates scope and page role

- GIVEN an authenticated workspace member attempts to connect a LinkedIn organization page
- WHEN the OAuth grant includes `w_organization_social`
- THEN the system MUST verify that the member has a LinkedIn page role sufficient for posting to
  that organization
- AND the system MUST persist the page as a separate organization-page social account only after
  both scope and role checks succeed

#### Scenario: PENDING transitions to ACTIVE after successful OAuth completion

- GIVEN a LinkedIn account is in `PENDING` status after OAuth initiation
- WHEN the OAuth callback succeeds, signed state is valid, required scopes are granted, and scope
  validation passes
- THEN the system MUST transition the account status to `ACTIVE`
- AND the account MUST become eligible for publishing subject to capability checks

#### Scenario: PENDING transitions to ERROR on OAuth failure

- GIVEN a LinkedIn account is in `PENDING` status after OAuth initiation
- WHEN the OAuth callback fails, scopes are insufficient, or signed state validation fails
- THEN the system MUST transition the account status to `ERROR`
- AND the system MUST NOT activate the connection
- AND a durable notification event MUST be recorded identifying the failure reason

### Requirement: LinkedIn Token Lifecycle and Refresh-Aware Credential Resolution

The system MUST persist and use LinkedIn token lifecycle metadata for refresh, reconnect, and worker
credential decisions.

LinkedIn access-token expiry timestamps MUST be persisted, with the expected nominal lifetime around
60 days. Refresh-token absolute expiry timestamps MUST be persisted when LinkedIn provides refresh
tokens, with the expected nominal lifetime around 365 days. The system MUST NOT treat refresh-token
expiry as sliding; using a refresh token does not extend the original absolute refresh expiry unless
LinkedIn returns a replacement refresh token with new expiry metadata. Tokens MUST be encrypted at
rest, and public APIs MUST NOT expose token values.

The worker/publisher path MUST obtain provider credentials through a refresh-aware credential
resolver or equivalent port. The worker/publisher path MUST NOT read and use raw stored access
tokens directly. The resolver MUST check connection status, capability eligibility, access-token
expiry, refresh-token absolute expiry, refresh availability, and single-flight/concurrency
protection before returning an access token usable for a LinkedIn call.

The refresh-aware credential resolver MUST prevent concurrent refresh attempts for the same
credential using optimistic locking via a database version column. The credential record MUST
include a version column that is checked and incremented atomically during refresh. If a concurrent
refresh detects a version mismatch (OptimisticLockException), it MUST retry by re-reading the
credential record. If the re-read shows the token was already refreshed by another process, the
resolver MUST use the newly refreshed token instead of issuing a duplicate refresh.

The system SHOULD attempt access-token refresh ahead of access-token expiry when programmatic
refresh is available. The system MUST surface proactive reconnect UX before refresh-token absolute
expiry or when refresh is unavailable, revoked, or fails with a terminal provider error.

#### Scenario: Expiring access token refreshes automatically through resolver

- GIVEN a LinkedIn connection has an encrypted refresh token that has not reached absolute expiry
- AND the access token is expired or within the configured refresh-ahead window
- WHEN a scheduled or immediate publication needs provider access
- THEN the worker MUST ask the refresh-aware credential resolver for credentials
- AND the resolver MUST attempt token refresh according to policy before returning credentials or
  after a refreshable 401 classification
- AND on refresh success it MUST atomically persist the new encrypted access token, updated access
  expiry, and any replacement refresh token LinkedIn returns

#### Scenario: Expired refresh token requires reconnect

- GIVEN a LinkedIn connection's refresh-token absolute expiry has passed or refresh fails with a
  terminal `invalid_grant`/revocation equivalent
- WHEN the system attempts to publish or proactively refresh
- THEN the system MUST mark the connection as `REQUIRES_RECONNECT`
- AND the affected publication MUST be marked `BLOCKED` with a user-actionable reconnect reason
  rather than retrying indefinitely
- AND a durable notification event MUST be recorded

### Requirement: LinkedIn REST Posts API Contract

The LinkedIn publisher MUST use LinkedIn REST API semantics consistently for post creation.

The preferred post creation endpoint for this capability is `POST /rest/posts`. Requests to
`/rest/*` endpoints MUST include `Linkedin-Version` in `YYYYMM` format and
`X-Restli-Protocol-Version: 2.0.0`. The post author MUST be a LinkedIn URN: `urn:li:person:{id}` for
personal profiles or `urn:li:organization:{id}` for organization pages. Organic publication payloads
MUST include commentary, `visibility: PUBLIC`, `distribution.feedDistribution: MAIN_FEED`, and
`lifecycleState: PUBLISHED` unless a future capability explicitly defines another
visibility/distribution model.

The adapter MUST capture LinkedIn's created-post identifier from the authoritative response location
for the endpoint, such as `x-restli-id` for `/rest/posts`, and map provider errors into safe
internal error classes without exposing tokens or authorization codes.

#### Scenario: Personal text post uses person URN and required headers

- GIVEN an active LinkedIn personal-profile social account with valid `w_member_social` credentials
- WHEN the worker publishes a valid text-only post
- THEN the LinkedIn adapter MUST call `POST /rest/posts`
- AND the request MUST include `Linkedin-Version` and `X-Restli-Protocol-Version: 2.0.0`
- AND the request author MUST be `urn:li:person:{id}`
- AND the request MUST set public visibility, main-feed distribution, and published lifecycle state

#### Scenario: Organization text post uses organization URN

- GIVEN an active LinkedIn organization-page social account with valid `w_organization_social`
  credentials and verified publish role
- WHEN the worker publishes a valid text-only post
- THEN the LinkedIn adapter MUST call `POST /rest/posts`
- AND the request author MUST be `urn:li:organization:{id}`
- AND the local publication result MUST identify the remote LinkedIn post URN or ID returned by
  LinkedIn

### Requirement: LinkedIn Text and Commentary Validation

The system MUST validate LinkedIn text content before calling LinkedIn.

Text publication MUST enforce the configured LinkedIn commentary length limit, SHOULD use 3,000
characters as the default product limit unless a stricter configured limit applies, and MUST
sanitize editor HTML/Markdown into provider-safe plain text or LinkedIn-supported commentary syntax.
JSON serialization MUST be delegated to a JSON serializer rather than manual string concatenation.
LinkedIn commentary escaping, mention syntax, hashtag syntax, Unicode, newlines, apostrophes, and
backslashes MUST be handled as separate provider-text concerns from JSON escaping.

Organization mentions MAY be supported only when the system can resolve and preserve valid LinkedIn
mention syntax. Organization mentions are gated — the mention capability must be explicitly enabled.
Personal-profile mentions MUST remain gated unless a future approved capability defines the required
permissions and behavior.

#### Scenario: Invalid text is rejected locally

- GIVEN a LinkedIn publication contains commentary longer than the configured limit
- WHEN the user submits or schedules the publication
- THEN the system MUST reject the publication before calling LinkedIn
- AND the user-facing validation result MUST identify the length violation

#### Scenario: Commentary serialization preserves Unicode and apostrophes

- GIVEN a LinkedIn publication contains Unicode, emoji, apostrophes, newlines, backslashes, or
  supported mention syntax
- WHEN the adapter builds the provider payload
- THEN JSON serialization MUST produce a valid JSON request body
- AND LinkedIn commentary escaping MUST NOT double-escape content or corrupt supported LinkedIn
  syntax

### Requirement: LinkedIn Media Upload and Availability Flow

The system MUST model LinkedIn media publication as a multi-phase provider workflow before creating
the final post.

For MVP launch, media publishing MUST prioritize text plus image posts. Image posts MUST initialize
provider media through the relevant LinkedIn REST image endpoint such as `/rest/images`, capture the
returned upload URL and asset URN, PUT binary content to the upload URL, and wait until the provider
reports the resource as `AVAILABLE` when LinkedIn exposes status for that media type or apply a
documented conservative wait strategy when status is unavailable.

For MVP launch, image publishing MUST enforce a maximum of 10MB per asset and a maximum of 10 assets
per publication. These limits MUST be configurable.

The final `/rest/posts` request MUST reference the provider asset URN in the post content according
to the LinkedIn content type being published. Provider asset references, upload phase state,
availability status, retryability, and timeout/error classification MUST be durable enough to
support idempotent retries and audit.

Video, PDF/document, and carousel media workflows MUST remain gated/non-MVP until endpoint-specific
initialization, upload/finalize, availability polling, scope/product permission, timeout, and retry
semantics are validated and explicitly enabled.

#### Scenario: Image post waits for available asset before post creation

- GIVEN a publication contains a valid image asset within the 10MB size limit
- WHEN the worker prepares the LinkedIn publication
- THEN the adapter MUST initialize the image upload and persist the provider asset URN
- AND it MUST PUT the binary image to LinkedIn's upload URL
- AND it MUST wait for the image resource to become `AVAILABLE` when LinkedIn exposes status for
  that media type or use the configured conservative wait strategy
- AND only then create the LinkedIn post referencing the asset URN

#### Scenario: Gated video is not published in launch MVP

- GIVEN a publication contains a video asset
- AND the video capability is not explicitly enabled for the account and environment
- WHEN the publication is validated or executed
- THEN the system MUST reject or block the publication before video upload or post creation
- AND the result MUST identify video publishing as gated/non-MVP

#### Scenario: Document or carousel request is gated

- GIVEN a publication requests PDF/document or carousel behavior
- AND the corresponding capability is not explicitly enabled
- WHEN the publication is validated or executed
- THEN the system MUST reject or block the publication before calling LinkedIn
- AND carousel publishing strategy is not defined for MVP

### Requirement: Organization Page Gating and Role Verification

Organization/page LinkedIn publishing MUST be gated by app capability, OAuth scope, target
organization identity, and resource-level role verification.

The system MUST represent organization pages as separate social accounts or equivalent publish
targets from personal profiles. The organization account MUST store the LinkedIn organization
id/URN, display metadata allowed by privacy policy, granted scope bundle, validating member
principal, role verification status, and capability status. Page publishing MUST require
`w_organization_social` plus a LinkedIn page role sufficient for posting, such as the roles
documented by LinkedIn for the selected endpoint/product. If role verification fails, becomes stale,
or LinkedIn reports insufficient permission, the page account MUST become non-publishable and SHOULD
be marked `REQUIRES_RECONNECT` or `ERROR` according to the error cause.

#### Scenario: Page role loss blocks future jobs

- GIVEN an organization page account was previously active
- AND LinkedIn later reports that the validating member no longer has sufficient page role
- WHEN the worker classifies the provider error
- THEN the page account MUST become non-publishable
- AND future queued jobs for that page MUST NOT call LinkedIn until role/scope eligibility is
  restored
- AND a durable notification event MUST be recorded

#### Scenario: Invalid organization identifier is terminal

- GIVEN a user attempts to connect or publish to an invalid LinkedIn organization id or URN
- WHEN validation or LinkedIn classification identifies the organization as invalid
- THEN the operation MUST fail without an automatic retry loop
- AND the user-facing result MUST identify the organization target problem

### Requirement: LinkedIn Publication Result Persistence

Successful LinkedIn publication attempts MUST persist provider result metadata in first-class
fields.

When LinkedIn creates a post, the system MUST persist the remote LinkedIn post identifier or URN and
MUST persist `publicUrl` as a first-class nullable publication result field separate from external
id/URN. For MVP, `publicUrl` MUST remain null. LinkedIn does not return a public URL in the
`/rest/posts` response. The system MUST persist null and MUST NOT attempt URL derivation until a
documented, validated derivation strategy is defined in a future capability.

Delivery attempts MUST record phase, status, mapped provider error, retryability, correlation
metadata, LinkedIn endpoint/version, and remote identifiers when known, without storing secrets.

#### Scenario: Successful post stores remote id and nullable public URL

- GIVEN LinkedIn returns a successful post creation response with a remote post identifier
- WHEN the worker completes the attempt
- THEN the publication MUST be marked published
- AND the system MUST persist the remote identifier or URN
- AND the system MUST persist `publicUrl` as null for MVP
- AND `publicUrl` MUST remain null rather than being derived or fabricated until a validated
  derivation strategy is defined

#### Scenario: Result metadata excludes secrets

- GIVEN a LinkedIn attempt completes with success or failure
- WHEN audit/result metadata is persisted
- THEN the metadata MUST include safe correlation details and mapped outcome fields
- AND MUST NOT include access tokens, refresh tokens, authorization codes, client secrets, or raw
  provider payloads containing secrets

### Requirement: LinkedIn Publication List API

The system MUST provide a paginated list-publications endpoint that returns publication state,
remote identifier, public URL when available, failure reason, scheduled time, and account/provider
context.

The endpoint MUST support filtering by publication state, LinkedIn account, and date range. The
endpoint MUST return results in reverse chronological order by default.

#### Scenario: User reviews publication history with states and results

- GIVEN a workspace has LinkedIn publications in various states (PUBLISHED, BLOCKED, FAILED,
  DRAFT, QUEUED, SCHEDULED, PROCESSING, CANCELLED)
- WHEN the user requests the publication list
- THEN the system MUST return a paginated list with publication state, remote identifier, public
  URL (nullable), failure reason when applicable, scheduled time, and LinkedIn account context
- AND results MUST be ordered reverse chronologically by default

### Requirement: Durable Idempotency and Ambiguous Outcome Handling

LinkedIn publication idempotency MUST include durable operation and phase state. Blind retry after
an uncertain remote create is prohibited.

The system MUST persist an operation key or equivalent idempotency identity before making provider
create calls. The publication workflow MUST record durable phases such as validation, credential
resolution, media initialization, binary upload, media availability wait, post creation requested,
post creation succeeded, ambiguous/unknown outcome, failed terminally, and retry scheduled. At most
one worker execution MAY be in-flight for a given publication operation and target account.

The worker MUST NOT execute more than N concurrent publications for the same LinkedIn account,
where N is a configurable limit (default: 1).

If a LinkedIn post creation request times out, loses its response, or otherwise has an ambiguous
outcome after the request may have reached LinkedIn, the attempt MUST be marked ambiguous/unknown.
The next action MUST use durable attempt/idempotency state and a defined reconciliation strategy
when possible. If reconciliation is impossible because required read permissions or identifiers are
unavailable, the system MUST require operator-safe or user-safe resolution according to product
policy rather than issuing an unbounded duplicate create.

#### Scenario: Ambiguous timeout is not blindly replayed

- GIVEN a LinkedIn post creation request times out after the request may have reached LinkedIn
- WHEN the worker records the attempt outcome
- THEN the attempt MUST be marked ambiguous or unknown with the phase `post creation requested`
- AND the next action MUST use durable attempt/idempotency state and reconciliation policy
- AND the system MUST NOT blindly issue a duplicate create without resolving the ambiguous outcome
  according to policy

#### Scenario: Worker restart resumes from durable phase

- GIVEN a worker crashes after initializing image upload and before creating the post
- WHEN another worker resumes the publication
- THEN it MUST load the durable provider asset and phase state
- AND it MUST continue or retry from the safe phase according to retry classification rather than
  restarting the whole workflow blindly

#### Scenario: Concurrent publications for same account are serialized

- GIVEN multiple publications are queued for the same LinkedIn account
- AND the configurable concurrency limit N is set to 1 (default)
- WHEN the worker picks up publications for execution
- THEN it MUST NOT execute more than N publications concurrently for the same account
- AND additional publications for that account MUST wait until the in-progress publication completes
  or fails

### Requirement: Quota-Aware Error Handling and Retry

The LinkedIn provider integration MUST apply bounded, quota-aware error handling.

The system MUST rate-limit LinkedIn calls by provider endpoint and actor/account to reduce 429
responses and protect app/member quotas. Approximate quota assumptions MAY be configured, but the
system MUST allow operational tuning based on LinkedIn Developer Portal analytics and observed
response behavior. The system MUST treat 429, transient 5xx responses, and retryable write conflicts
as retryable with bounded backoff and jitter. The system MUST detect 401/credential failures,
attempt token refresh through the refresh-aware credential resolver when possible, and trigger
reconnect when refresh is impossible or expired.

The system MUST NOT retry indefinitely for validation errors, insufficient scopes, insufficient page
roles, invalid organization identifiers, unsupported/gated media, duplicate-content classifications
treated as terminal, or unrefreshable credentials.

#### Scenario: Rate limit response is retried with backoff

- GIVEN LinkedIn returns HTTP 429 for a publication or media request
- WHEN the retry budget has not been exhausted
- THEN the system MUST record the attempt as retryable
- AND schedule a later retry using bounded backoff and jitter
- AND preserve the same durable operation identity

#### Scenario: Insufficient page permission fails without retry loop

- GIVEN a publication targets a LinkedIn organization page
- AND LinkedIn indicates the member no longer has a role sufficient to publish
- WHEN the worker classifies the error
- THEN the system MUST mark the publication failed with a permission reason
- AND the social account MUST be marked non-publishable according to the chosen status mapping
- AND the system MUST NOT retry the same request indefinitely

### Requirement: Durable Notification Events

The system MUST record durable notification events for user-actionable LinkedIn publication and
connection outcomes.

Notification events MUST be persisted to a dedicated `notification_events` table with columns for
id, workspace_id, provider, social_account_id, publication_id, category, message, suggested_action,
public_url, and occurred_at. The table MUST be queryable by workspace, account, publication,
category, and date range.

A durable notification event MUST be recorded when a LinkedIn publication succeeds, fails
terminally, is blocked because the integration is disabled/non-publishable, requires reconnect,
lacks scopes or page roles, encounters media-processing failure/timeout, or enters an ambiguous
outcome requiring action. Delivery channel implementation MAY be separate or future-facing if no
notification subsystem exists, but the event record MUST be durable and sufficient for later
delivery.

Notification events MUST include provider, workspace/account context, publication identity when
applicable, result category, suggested user action when applicable, and `publicUrl` when a
successful publication has one. Notification events MUST NOT contain tokens or secrets.

#### Scenario: Reconnect-required event is recorded

- GIVEN a LinkedIn publication cannot proceed because the connection is `REQUIRES_RECONNECT`
- WHEN the worker blocks or fails the publication
- THEN the system MUST record a durable notification event
- AND the event MUST identify LinkedIn, the affected account, the publication when applicable, and
  the reconnect action

#### Scenario: Delivery channel can be future

- GIVEN the repository has no implemented notification delivery channel for this event type
- WHEN a LinkedIn notification event is recorded
- THEN the specification MUST still require durable event creation
- AND MUST NOT require a specific email, push, or in-app delivery implementation in this change
  phase

### Requirement: LinkedIn Privacy Retention

The system MUST minimize and expire LinkedIn-derived personal/profile and social activity data
according to product privacy constraints.

The system MUST NOT persist LinkedIn member profile data longer than 24 hours unless the field is
minimal durable connection metadata necessary to provide the user-requested integration, such as
provider account id, display name, account kind, avatar URL subject to existing sanitization rules,
granted scopes, capability status, and connection status. The system MUST NOT persist LinkedIn
social activity data longer than 48 hours unless a future approved analytics capability explicitly
changes retention. Retention cleanup MUST avoid deleting publication audit records required for
local publication history, but those records MUST contain only safe provider result/error metadata
and no unnecessary profile/social activity payloads.

Comments/threads and analytics are gated/non-MVP for this change; therefore any social activity data
collected only for comments, reactions, or analytics MUST either remain out of scope or comply with
the 48-hour retention limit until a future approved policy supersedes it.

#### Scenario: Short-lived profile payload is removed after retention window

- GIVEN the system temporarily stores LinkedIn profile payload data for connection completion or
  debugging
- WHEN the data is older than 24 hours
- THEN the retention process MUST delete or anonymize that temporary profile payload
- AND durable connection metadata required for the active integration MAY remain

#### Scenario: Social activity data is removed after retention window

- GIVEN the system stores LinkedIn social activity data for publication feedback, comments/status
  processing, or future analytics experiments
- WHEN the data is older than 48 hours and no future approved retention policy applies
- THEN the retention process MUST delete or anonymize the social activity data
- AND publication audit records MUST retain only safe minimal outcome metadata

### Requirement: LinkedIn Scheduler Frontend Changes

The scheduler UI at `/scheduler` MUST be updated to support LinkedIn-specific publishing flows as
part of this change.

The following frontend changes are in scope:

#### Calendar Time Reference

The weekly calendar view MUST display a single time-axis column on the left side of the grid, NOT
duplicate time labels in every day column. Time labels MUST appear once per row (e.g., "6 AM",
"8 AM", etc.) on the left edge.

#### Monthly Calendar View

The scheduler MUST provide a monthly calendar view accessible from the existing view toggle
(Calendar/Week/Day). The monthly view MUST show publication posts as items on their scheduled
dates, with channel indicator and status color coding.

#### Channel Filtering for LinkedIn

When a user selects only the LinkedIn channel from the channel filter, the scheduler MUST display
only LinkedIn publications. The channel filter MUST support single-channel selection for LinkedIn,
and the "NEW POST" form MUST pre-select the LinkedIn channel when reached from a LinkedIn-filtered
view.

#### Publication Status Indicators

Post items in the calendar/list views MUST display status indicators using the publication
lifecycle states: DRAFT, QUEUED, SCHEDULED, PROCESSING, PUBLISHED, BLOCKED, FAILED, CANCELLED.
BLOCKED publications MUST be visually distinct from FAILED publications to indicate they may
auto-retry.

#### Reconnect UX

When a LinkedIn account is in `REQUIRES_RECONNECT` status, the scheduler MUST display a visible
reconnect prompt near the channel selector and on any BLOCKED publications targeting that account.
The reconnect prompt MUST link to the LinkedIn OAuth initiation flow.

#### Scenario: Monthly view shows scheduled LinkedIn posts

- GIVEN a workspace has LinkedIn publications scheduled for various dates in the current month
- WHEN the user switches to the monthly calendar view
- THEN the calendar MUST display publication items on their scheduled dates
- AND each item MUST show the channel indicator and status color

#### Scenario: Time axis is displayed once per row in weekly view

- GIVEN the user is in the weekly calendar view
- WHEN the calendar renders time slots
- THEN time labels MUST appear once on the left side of the grid
- AND MUST NOT be duplicated in each day column

#### Scenario: LinkedIn-only filter shows only LinkedIn publications

- GIVEN the user selects only the LinkedIn channel in the filter
- WHEN the scheduler renders the calendar or list view
- THEN only LinkedIn publications MUST be displayed
- AND the NEW POST form MUST pre-select LinkedIn as the channel

#### Scenario: BLOCKED publication shows reconnect prompt

- GIVEN a LinkedIn account is in `REQUIRES_RECONNECT` status
- AND there are BLOCKED publications targeting that account
- WHEN the user views the scheduler
- THEN a reconnect prompt MUST be visible near the channel selector
- AND each BLOCKED publication MUST show a reconnect action
