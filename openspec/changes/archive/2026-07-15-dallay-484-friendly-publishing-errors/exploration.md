# Exploration: DALLAY-484 Improve User-Facing Error Messages for Failed Post Publishing

## Current State

Publishing diagnostics are shown in `PostDetailModal.vue` for `FAILED` and `BLOCKED` publications.
The frontend receives `CalendarPublicationResult.errorCode`, stores it as `Publication.errorCode`,
and maps a small allowlist of backend codes to localized `postDetail.errorMessages.*` strings.
Unknown failed codes currently render raw values, so class names such as
`StorageObjectNotFoundException` can leak directly to users. If no failed code exists, the failure
block is hidden entirely. Blocked publications render `blockedReason` directly; reconnect handling
currently stores `ReconnectRequiredException.message` in that field. Retry/delete/reschedule action
failures also render `Error.message`, which can surface backend `ProblemDetail.detail` or generic
`Request failed` from the shared auth API client.

Backend publishing workers persist terminal publication failure state with
`exception::class.simpleName` as `last_error_code` and `exception.message` as `last_error_message`.
The calendar API exposes `lastErrorCode` as `errorCode`, but does not expose `lastErrorMessage` in
calendar results. Storage download failures are converted to `RetryablePublishingException` with
technical details in the message; the same exception type also represents provider rate limits and
5xx responses, so the worker cannot classify these cases without parsing text. Once retries are
exhausted, the stored code becomes `RetryablePublishingException`, not a stable product taxonomy.
Media metadata resolution occurs before the worker's guarded publish block, so those failures can
bypass attempt recording and explicit job transition. Publishing `ProblemDetail` mappings exist for
synchronous API errors, but many use exception messages as `detail` and are not enough for
asynchronous failed-publication UX.

## Affected Areas

- `apps/web/app/src/modules/publishing/presentation/components/PostDetailModal.vue` — displays
  failed and blocked publication diagnostics, raw unknown error/reason values,
  retry/delete/reschedule action errors, and owns the current frontend error-code mapping.
- `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` — maps backend calendar
  `errorCode` into the frontend `Publication` model; API mutation failures pass through
  `auth.apiFetch` errors unchanged.
- `apps/web/app/src/modules/auth/infrastructure/auth-api.ts` — shared fetch wrapper builds
  `ApiError` from `ProblemDetail.title/detail/errorCode`; default title is `Request failed`, and
  consumers often render `Error.message`.
- `apps/web/app/src/shared/i18n/locales/en/postDetail.ts` and
  `apps/web/app/src/shared/i18n/locales/es/postDetail.ts` — current post-detail messages live here
  after i18n modularization; new user-facing failure title/body/action/fallback keys should live
  here.
- `apps/web/app/src/shared/i18n/i18n-keys.test.ts` — verifies locale key parity and should catch any
  new EN/ES keys.
- `apps/web/app/src/modules/publishing/presentation/components/PostDetailModal.test.ts` — already
  covers failure diagnostics and explicitly asserts raw unknown error-code leakage; this is the
  primary regression-test target.
- `apps/web/app/src/modules/publishing/infrastructure/publishing.store.test.ts` — appropriate for
  verifying API error propagation only if a store-level friendly mapper is introduced.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt` —
currently resolves media before its failure guard, stores exception simple names/messages for async
failures, stores reconnect messages as blocked reasons, records technical attempt details, and
copies failure messages into notification events.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt` —
converts storage download problems into retryable technical exceptions; logs storage/bucket/key
details server-side.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` —
maps synchronous publishing exceptions to `ProblemDetail`; can add/normalize `errorCode` values for
client mapping.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorkerTest.kt`
and `PublishingWorkerTransactionPostgresIntegrationTest.kt` — likely backend regression locations
for stable stored failure codes.

## Approaches

1. **Frontend-safe presentation mapping only** — Replace raw fallback in `PostDetailModal.vue` with
   localized generic unknown failure copy, expand known code mappings, rename label from "Error
   Code" to a user-facing failure label, and map retry/delete/reschedule API failures to friendly
   messages.
    - Pros: Fastest MVP release-readiness fix; directly stops leaking
      `StorageObjectNotFoundException`, `RetryablePublishingException`, and unknown raw codes in
      dialogs; localized in the existing post-detail i18n module; low backend risk.
    - Cons: Backend still stores exception class names as product error codes; observability
      taxonomy remains weak; new backend codes require frontend updates to become specific.
    - Effort: Low

2. **Backend failure taxonomy only** — Introduce stable async publishing failure categories/codes in
   the worker/adapters (for example `MEDIA_UNAVAILABLE`, `MEDIA_NOT_FOUND`, `PROVIDER_RATE_LIMITED`,
   `PROVIDER_VALIDATION_FAILED`, `PROVIDER_UNAVAILABLE`) while keeping sanitized technical
   diagnostics only in delivery attempts/logs.
    - Pros: Creates durable product contract; avoids storing exception class names in publication
      state; helps future UI, notifications, analytics, and support.
    - Cons: Does not by itself protect against unknown future codes unless frontend fallback
      changes; requires careful backend tests and migration thinking for existing failed rows.
    - Effort: Medium

3. **Hybrid safe UI plus backend taxonomy** — Implement frontend allowlist/fallback immediately and
   add stable backend failure-code mapping where async publishing failures are persisted.
    - Pros: Best release-quality outcome: no user leakage now, stable API/data contract going
      forward, technical details remain in logs/delivery attempts; supports actionable copy for
      retry/reconnect/media recovery.
    - Cons: More scope than a frontend-only patch; needs coordinated frontend and backend tests;
      existing persisted raw codes may still appear until mapped by frontend fallback or remediated.
    - Effort: Medium

## Recommendation

Use the hybrid approach, with frontend fallback as the mandatory first guardrail. The frontend must
never render unknown backend codes, raw blocked reasons, or raw `Error.message` values in the
publishing failure dialog; it should display localized, actionable copy for known categories and a
localized unknown fallback for everything else. Backend should then emit typed failure signals,
guard media resolution through provider dispatch, stop persisting exception messages on
publications/notifications, and retain only sanitized diagnostics in delivery attempts and
logs/observability.

## Risks

- Existing tests currently assert raw unknown code rendering, so the required behavior intentionally
  breaks that expectation.
- Backend has historical failed rows with raw `last_error_code`; frontend fallback must handle them
  even if backend taxonomy is added.
- Backend has historical blocked rows with raw reasons; the same fallback must protect them.
- Media resolution currently occurs before the worker failure boundary and must be moved or
  explicitly guarded.
- Existing retryable exception types conflate media, rate-limit, and provider-outage failures;
  classification must be typed rather than message-based.
- Synchronous API errors from `auth.apiFetch` can still leak `ProblemDetail.detail` through
  action-level errors unless those UI paths map errors to localized messages.
- A backend-only taxonomy without frontend fallback would still leak any new unmapped code.

## Ready for Proposal

Yes — propose DALLAY-484 as a release-readiness change using hybrid scope: frontend
failed/blocked/action safety first, backend typed taxonomy and guarded worker execution second, with
boundary tests proving no internal exception, provider payload, raw blocked reason, or generic
`Request failed` text reaches publishing dialogs.
