# Delta for Publishing

## ADDED Requirements

### Requirement: Safe Friendly Publishing Failure Presentation

Failed publication UI MUST show user-friendly problem labels, explanations, and recovery actions from an allowlisted product taxonomy. The UI MUST NOT render exception names, stack traces, package/class names, raw provider/storage responses, URLs, tokens, tenant/workspace/internal IDs, bucket/object paths, HTTP/client debug strings, or raw unknown codes/messages.

#### Scenario: Missing media shows replacement guidance

- GIVEN a failed publication has category `MEDIA_NOT_FOUND`
- WHEN the user opens publication details
- THEN the UI MUST show localized copy explaining the media could not be found
- AND it MUST suggest reattaching media or editing the post

#### Scenario: Temporarily unavailable media suggests retry

- GIVEN a failed publication has category `MEDIA_UNAVAILABLE`
- WHEN the user opens publication details
- THEN the UI MUST explain that media could not be accessed temporarily
- AND it MUST suggest retrying later or replacing the asset if the problem persists

#### Scenario: Account authorization expired asks reconnect

- GIVEN a blocked publication has category `ACCOUNT_RECONNECT_REQUIRED`
- WHEN the user opens publication details
- THEN the UI MUST show localized reconnect guidance
- AND it MUST NOT expose provider token, auth URL, or OAuth debug details

#### Scenario: Terminal account unavailability offers a safe alternative

- GIVEN a failed publication has category `ACCOUNT_UNAVAILABLE`
- WHEN the user opens publication details
- THEN the UI MUST explain that the selected account cannot publish
- AND it MUST suggest restoring or selecting an available account

#### Scenario: Transient service outage suggests retry later

- GIVEN a failed publication has category `PROVIDER_UNAVAILABLE` or `PROVIDER_RATE_LIMITED`
- WHEN the user opens publication details
- THEN the UI MUST explain the service is temporarily unavailable
- AND it MUST suggest retrying later or rescheduling

#### Scenario: Validation failure explains safe product reason

- GIVEN a failed publication has category `PROVIDER_VALIDATION_FAILED`
- WHEN the user opens publication details
- THEN the UI MUST show localized validation guidance
- AND it MUST avoid raw provider response text

#### Scenario: Sensitive diagnostics never leak

- GIVEN a failure value contains `com.example.StorageObjectNotFoundException`, stack frames, `bucket/key`, URL, token, internal ID, or `Request failed`
- WHEN any publishing failure, retry, delete, or reschedule error is rendered
- THEN none of those raw values MUST appear in visible UI
- AND only safe localized copy MAY be shown

#### Scenario: Blocked reason is treated as untrusted input

- GIVEN a blocked publication contains a missing, unknown, historical, or raw `blockedReason`
- WHEN the user opens publication details
- THEN the UI MUST map that value through the same safe allowlist/fallback boundary
- AND it MUST NOT render the raw blocked reason

### Requirement: Localized Failure Copy and Actions

All user-facing publishing failure messages, labels, explanations, and recovery actions MUST be internationalized in English and Spanish. Visible strings for failed-publication diagnostics and modal action failures MUST NOT be hardcoded in components, stores, or tests except as locale fixtures/assertions.

#### Scenario: English and Spanish copy parity

- GIVEN the app supports English and Spanish locales
- WHEN known publishing failure categories are rendered
- THEN each locale MUST provide equivalent title, explanation, and action copy
- AND locale key parity tests MUST cover the added keys

#### Scenario: Action failure uses localized safe copy

- GIVEN retry, delete, or reschedule fails from the post detail modal
- WHEN the error is shown to the user
- THEN the UI MUST render a localized safe action-failure message
- AND it MUST NOT render raw `Error.message` or backend `ProblemDetail.detail`

#### Scenario: Structured action failure provides safe recovery guidance

- GIVEN retry, delete, or reschedule fails with a structured API code or HTTP status for unauthorized, not found, state conflict, validation, or temporary unavailability
- WHEN the error is shown to the user
- THEN the UI MUST map HTTP 401/403 to unauthorized, 404 to not found, 409 to state conflict, 400/422 to validation, and 429/network/5xx to temporarily unavailable
- AND an explicitly allowlisted backend error code MAY refine the matching safe status category without introducing backend text
- AND it MUST combine the reason with operation-specific recovery guidance
- AND an unrecognized structured value MUST use the localized unknown fallback

### Requirement: Unknown and Historical Failure Compatibility

Unknown, unmapped, missing, or historical failed and blocked reason codes MUST resolve to safe localized generic or category messages. The system MUST NOT pass raw codes or messages through to visible UI. New backend `FAILED` and `BLOCKED` outcomes MUST persist a category from the canonical taxonomy: `MEDIA_NOT_FOUND`, `MEDIA_UNAVAILABLE`, `PROVIDER_VALIDATION_FAILED`, `PROVIDER_RATE_LIMITED`, `PROVIDER_UNAVAILABLE`, `ACCOUNT_RECONNECT_REQUIRED`, `ACCOUNT_UNAVAILABLE`, or `PUBLISHING_FAILED`.

#### Scenario: Unknown error uses generic fallback

- GIVEN a failed publication has an unknown code such as `UnexpectedProviderClientException`
- WHEN the user opens publication details
- THEN the UI MUST show a localized generic publishing failure message and action
- AND the raw code MUST NOT appear

#### Scenario: Historical exception-class codes remain safe

- GIVEN an old persisted failed publication stores an exception class as `last_error_code`
- WHEN the calendar result is displayed
- THEN the UI MUST map it to a safe localized generic or category message
- AND it MUST NOT require a database migration to avoid leakage

#### Scenario: New failed outcomes use stable categories

- GIVEN async publishing exhausts retries for media, auth, provider outage, validation, or unknown failures
- WHEN terminal failure state is persisted
- THEN the user-facing failure code MUST be a canonical stable product category
- AND exception type/message MUST remain server-side diagnostics only

#### Scenario: New reconnect outcomes use a stable blocked category

- GIVEN publishing cannot continue until the social account is reconnected
- WHEN the publication transitions to `BLOCKED`
- THEN the persisted blocked reason MUST be `ACCOUNT_RECONNECT_REQUIRED`
- AND the raw reconnect exception message MUST NOT be persisted as the blocked reason

### Requirement: Typed Failure Classification and Retry Semantics

Async publishing failures MUST carry a typed canonical category and explicit retryability from the boundary that understands the failure. Classification MUST NOT inspect or parse exception messages, provider response bodies, or exception simple names. Unknown exceptions MUST map to `PUBLISHING_FAILED`.

Retryable failures MUST retain the same category across delivery attempts and terminal persistence after retry exhaustion. The required default mappings are:

- missing asset metadata or binary → `MEDIA_NOT_FOUND`, non-retryable;
- temporary media/storage access failure → `MEDIA_UNAVAILABLE`, retryable;
- provider/capability rejection → `PROVIDER_VALIDATION_FAILED`, non-retryable;
- provider HTTP 429 → `PROVIDER_RATE_LIMITED`, retryable;
- provider network or HTTP 5xx failure → `PROVIDER_UNAVAILABLE`, retryable;
- expired/revoked credentials or insufficient scopes → `ACCOUNT_RECONNECT_REQUIRED`, blocked;
- disabled/deleted terminal account → `ACCOUNT_UNAVAILABLE`, non-retryable;
- unexpected exception → `PUBLISHING_FAILED`, non-retryable.

#### Scenario: Retryable category survives retry exhaustion

- GIVEN provider dispatch returns a typed `PROVIDER_RATE_LIMITED` failure
- WHEN the worker retries and eventually exhausts the configured budget
- THEN every failed delivery attempt MUST retain `PROVIDER_RATE_LIMITED`
- AND terminal publication state MUST persist `PROVIDER_RATE_LIMITED`

#### Scenario: Unknown exception never uses its message as classification

- GIVEN the worker receives an unexpected exception whose type or message contains technical details
- WHEN it classifies the failure
- THEN the publication MUST use `PUBLISHING_FAILED`
- AND no category decision MAY depend on exception name or message text

### Requirement: Guarded Pre-Dispatch and Provider Execution

Media metadata resolution, capability validation, credential resolution, asset download/upload, and provider dispatch MUST execute inside the worker failure boundary. A failure at any of these stages MUST record a failed delivery attempt and follow the typed retry, blocked, or terminal transition contract. No claimed job MAY remain without an explicit reschedule, blocked completion, successful completion, or terminal failure solely because a pre-dispatch dependency threw.

#### Scenario: Media resolution fails before provider dispatch

- GIVEN a claimed publication references media and media resolution is temporarily unavailable
- WHEN resolution fails before provider dispatch
- THEN the worker MUST record a `MEDIA_UNAVAILABLE` failed attempt
- AND it MUST reschedule or fail the job according to the existing retry budget
- AND it MUST NOT call the provider

#### Scenario: Missing media fails safely before provider dispatch

- GIVEN media resolution proves that a required asset or binary no longer exists
- WHEN the worker prepares the publication
- THEN it MUST record a non-retryable `MEDIA_NOT_FOUND` attempt
- AND it MUST mark the publication and job failed atomically
- AND it MUST NOT call the provider

### Requirement: Server-Side Diagnostic Redaction

Publication state and existing notification events MUST contain only canonical categories and safe non-technical copy. New async worker failures MUST leave publication `lastErrorMessage` null or safe and MUST NOT copy `Throwable.message`, provider bodies, or storage paths into publications or notification events.

Delivery attempts and logs MAY retain sanitized server-side diagnostics such as exception type, provider HTTP status, and non-secret provider correlation IDs. Sanitization MUST occur before persistence and MUST remove access/refresh tokens, authorization headers or URLs, provider response bodies, stack traces, identifiers embedded in messages, and raw bucket/object paths.

#### Scenario: Provider response is redacted before persistence

- GIVEN a provider failure contains a response body, URL, token-like value, internal identifier, or stack text
- WHEN the worker records the failure and its notification event
- THEN publication and notification data MUST contain only canonical/safe values
- AND persisted attempt diagnostics MUST exclude every prohibited raw value

#### Scenario: Calendar API exposes no technical message

- GIVEN a publication has a canonical failed or blocked category and server-side attempt diagnostics
- WHEN the calendar/detail API serializes the publication
- THEN the client-visible result MUST expose only the opaque category required for safe mapping
- AND it MUST NOT expose publication or attempt diagnostic messages

### Requirement: Safe Deployment Compatibility

The frontend unknown/missing/historical fallback MUST be deployed before the backend begins persisting canonical categories. A backend rollback MUST occur before any rollback of the frontend guardrail. The frontend guardrail MUST remain deployed while rows containing canonical or historical untrusted values can still be served.

#### Scenario: Backend taxonomy is rolled back

- GIVEN the backend has already persisted one or more canonical categories
- WHEN backend taxonomy writers are rolled back
- THEN the frontend safe fallback MUST remain active
- AND those persisted values MUST NOT become raw visible content
