# Design: Friendly Publishing Errors

## Technical Approach

Use a defense-in-depth contract: backend persists stable publishing failure categories for new async `FAILED` and `BLOCKED` outcomes, while the Vue modal remains the final UI safety boundary. The UI will render only localized copy selected from an allowlist; raw backend `errorCode`, `blockedReason`, `ProblemDetail.detail`, exception messages, and exception class names are diagnostics, never visible content.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| UI disclosure boundary | `PostDetailModal.vue` maps failure codes to known safe i18n keys and always falls back to a generic localized unknown failure | Render unknown codes, or trust backend messages | Historical rows and future backend defects can still contain internals; UI must fail closed. |
| Backend failure taxonomy | Use the closed canonical taxonomy below for all new `FAILED` and `BLOCKED` outcomes | Continue storing `exception::class.simpleName` | Stable codes are safe API/data contracts; class names are implementation details. |
| Backend failure signal | Adapters and media-resolution boundaries return or throw a typed failure carrying category and retryability; classification MUST NOT parse `Throwable.message` | Infer categories from exception names/messages | A single retryable exception currently conflates storage, rate limits, and provider outages. |
| Blocked-state contract | Persist a canonical category as the blocked reason for new rows and treat all historical `blockedReason` values as untrusted input | Render `blockedReason` directly | Reconnect failures currently use `BLOCKED`, not terminal `FAILED`, and can contain exception text. |
| Diagnostics boundary | Publications and notification events store only stable categories/safe copy; delivery attempts and logs retain sanitized diagnostics | Preserve raw messages on publication state or notifications | Support needs debugging data, but provider bodies, credentials, storage paths, and exception messages must not become user-facing or broadly persisted content. |
| i18n placement | Add post-modularization keys under `apps/web/app/src/shared/i18n/locales/{en,es}/postDetail.ts` | Hardcode modal copy | Existing locale modules and parity tests already enforce EN/ES coverage. |

## Data Flow

```text
Worker exception/provider failure
  ├─ typed failure(category, retryable)
  │   ├─ retry allowed → sanitized delivery attempt → reschedule job
  │   ├─ reconnect required → stable category → BLOCKED reason code
  │   └─ terminal/exhausted → stable category → publications.last_error_code
  ├─ safe category/copy → existing notification event
  └─ sanitize → delivery_attempts.provider_* + server logs only

Calendar API → publishing.store.ts stores failure/block reason as opaque data
  → PostDetailModal.vue allowlist
      ├─ known code → postDetail.failure.<code>.{label,explanation,action}
      └─ unknown/historical code → postDetail.failure.unknown.*
```

Media metadata resolution, capability validation, credential resolution, asset upload/download, and provider dispatch MUST all execute inside this guarded failure boundary. A failure before provider dispatch still records an attempt and follows the same retry/terminal rules.

## Canonical Failure Taxonomy

| Code | Meaning | Default retryability | Final state | Recovery guidance |
|---|---|---:|---|---|
| `MEDIA_NOT_FOUND` | Persisted asset metadata or required binary no longer exists | No | `FAILED` | Reattach or replace the missing media, then publish again |
| `MEDIA_UNAVAILABLE` | Media or storage service cannot currently resolve/read the asset | Yes | `FAILED` after exhaustion | Retry later; replace the asset if the problem persists |
| `PROVIDER_VALIDATION_FAILED` | Provider/capability validation rejected safe publication input | No | `FAILED` | Edit the post to meet channel requirements |
| `PROVIDER_RATE_LIMITED` | Provider returned a rate-limit response | Yes | `FAILED` after exhaustion | Retry later or reschedule |
| `PROVIDER_UNAVAILABLE` | Provider network/service failure, including retryable 5xx responses | Yes | `FAILED` after exhaustion | Retry later or reschedule |
| `ACCOUNT_RECONNECT_REQUIRED` | Credentials, grant, or scopes require reconnection | No automatic publish retry | `BLOCKED` | Reconnect the social account |
| `ACCOUNT_UNAVAILABLE` | Account is disabled, deleted, or otherwise terminally unavailable | No | `FAILED` | Choose or restore an available account |
| `PUBLISHING_FAILED` | Unexpected failure with no safe specific classification | No | `FAILED` | Retry once; contact support if it persists |

These names are authoritative. `ACCOUNT_AUTH_EXPIRED`, `SERVICE_TEMPORARILY_UNAVAILABLE`, `TERMINAL_ACCOUNT_STATUS`, `BLOCKED_MAX_RETRIES_EXCEEDED`, exception class names, and other historical values are legacy/untrusted inputs, not new producer values. Historical reconnect signals MAY map to `ACCOUNT_RECONNECT_REQUIRED` only when the mapping is exact and allowlisted; otherwise they MUST use `unknown`.

Retryability is part of the typed failure signal. A retryable failure retains the same canonical category across every attempt and when retries are exhausted. Unknown exceptions map to `PUBLISHING_FAILED`; their messages MUST NOT influence category selection.

## Diagnostic Sanitization Contract

- Publication `lastErrorCode` and new blocked-reason values contain only canonical codes; `lastErrorMessage` MUST be null or safe non-technical copy for new worker failures.
- Existing notification events receive a canonical category and safe message/action; they MUST NOT receive `exception.message` or provider response bodies.
- Delivery attempts MAY retain exception type, provider HTTP status, and non-secret provider correlation IDs after sanitization.
- Delivery attempts and logs MUST NOT persist access/refresh tokens, authorization headers or URLs, provider response bodies, stack traces, tenant/workspace identifiers embedded in messages, or raw bucket/object paths.
- Sanitization MUST happen before persistence. UI filtering is an additional boundary, not the diagnostic redaction mechanism.

## File Changes

| File | Action | Description |
|---|---|---|
| `apps/web/app/src/modules/publishing/presentation/components/PostDetailModal.vue` | Modify | Replace raw failed/blocked detail and action errors with localized allowlist/fallback presentation; invalid date message also i18n. |
| `apps/web/app/src/shared/i18n/locales/en/postDetail.ts` | Modify | Add failure label/explanation/action keys and safe action-error keys. |
| `apps/web/app/src/shared/i18n/locales/es/postDetail.ts` | Modify | Spanish parity for all new keys. |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | Modify | Preserve backend code as opaque data; do not convert it to UI text. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingPolicies.kt` | Modify | Persist canonical code and safe/null message without UI semantics. |
| publishing failure model/ports | Add or modify | Define typed canonical category plus retryability; no message parsing. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt` | Modify | Guard pre-dispatch resolution through dispatch, preserve category across retries, and sanitize persistence/notifications. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt` | Modify | Emit typed media, auth, validation, rate-limit, and provider-unavailable failures. |
| `server/smp/src/test/.../PublishingWorkerTest.kt` | Modify | Assert canonical failed/blocked codes, retry behavior, pre-dispatch handling, and diagnostic redaction. |
| calendar HTTP/store contract tests | Modify | Assert only opaque safe codes cross the backend/frontend boundary. |
| `apps/web/app/src/modules/publishing/presentation/components/PostDetailModal.test.ts` | Modify | Assert no raw failed/blocked/action value leaks and EN/ES copy renders. |

## Interfaces / Contracts

Backend `CalendarPublicationResult.errorCode` remains nullable string but its produced values become the closed product taxonomy above for new `FAILED` results. The existing blocked-reason field also becomes an opaque canonical-code carrier for new `BLOCKED` results. Frontend treats both as untrusted input.

```ts
type PublishingFailureCopyKey = 'mediaUnavailable' | 'mediaNotFound' | 'providerValidationFailed' | 'providerRateLimited' | 'providerUnavailable' | 'accountReconnectRequired' | 'accountUnavailable' | 'publishingFailed' | 'unknown'
```

Unknown values, including old `StorageObjectNotFoundException` and `RetryablePublishingException`, resolve to `unknown`.

Synchronous retry/delete/reschedule failures use a separate safe allowlist derived only from structured `ApiError.errorCode` or HTTP status: HTTP 401/403 → `unauthorized`, 404 → `notFound`, 409 → `stateConflict`, 400/422 → `validation`, 429/network/5xx → `temporarilyUnavailable`, and everything else → `unknown`. An explicitly allowlisted backend error code MAY refine the matching status category but MUST NOT introduce backend text. The modal combines the safe reason with the operation-specific localized action. It MUST NOT derive visible copy from `Error.message` or `ProblemDetail.detail`.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Modal never renders raw failed/blocked values, exception names, `Request failed`, or API error messages, including missing-code fallback | Add failing Vitest tests first against `PostDetailModal.test.ts`. |
| Unit | EN/ES keys exist for all failure labels/explanations/actions | Extend existing i18n parity/localization tests. |
| Backend unit/integration | Typed failures map exhaustively, pre-dispatch media failures retry/terminate correctly, blocked reasons are safe, and diagnostics/notifications are sanitized | Add tests in worker and transaction suites before implementation. |
| HTTP/store contract | Calendar results expose only canonical/opaque codes and never publication diagnostic messages | Add focused backend serialization and frontend store mapping tests. |
| E2E | Not required for this MVP fix | Component, worker, and HTTP/store contract coverage protects every boundary changed here. |

## Migration / Rollout

No database migration is required. Historical rows with exception class names or raw blocked reasons remain compatible because frontend fallback treats every unrecognized value as generic localized copy. Deploy the frontend safety boundary first, then enable backend canonical writes. On rollback, revert backend writers first and retain the frontend fallback while any historical or new non-legacy value can still be served.

## Open Questions

None. The canonical taxonomy, blocked-state representation, typed failure contract, sanitization rules, and deployment order are normative decisions in this design.
