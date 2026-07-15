# Tasks: Friendly Publishing Errors

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 500-800 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1: frontend safety boundary; PR 2: typed backend taxonomy and guarded worker |
| Delivery strategy | Ordered rollout: deploy PR 1 before enabling PR 2 |
| Chain strategy | Two independently releasable sequential PRs |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: frontend-safety-then-backend-taxonomy
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Safe localized modal failures | PR 1 | Component/store tests, mapper, EN/ES copy, modal labels |
| 2 | Stable backend failure codes | PR 2 | Worker taxonomy, adapter classification, diagnostics and HTTP contract tests |

## Phase 1: RED Frontend Regression Tests

- [x] 1.1 Add failing `PostDetailModal.test.ts` cases for every canonical code: media not found/unavailable, reconnect required, account unavailable, provider validation, provider unavailable/rate limit, and generic publishing failure.
- [x] 1.2 Add failing modal fallback cases for missing failed codes plus unknown/historical failed codes.
- [x] 1.3 Add failing `BLOCKED` cases proving canonical reconnect guidance renders and raw/missing/historical `blockedReason` values use the safe fallback.
- [x] 1.4 Add failing modal no-leak cases for exception class names, stack text, URLs, tokens, internal IDs, bucket/object paths, `Request failed`, and raw unknown values.
- [x] 1.5 Add failing retry/delete/reschedule action-error tests for unauthorized, not found, state conflict, validation, temporarily unavailable, and unknown responses; prove raw `Error.message` and `ProblemDetail.detail` never render.
- [x] 1.6 Add failing store/DTO mapping tests proving failed and blocked codes remain opaque and diagnostic messages are never converted to visible copy.
- [x] 1.7 Extend EN/ES i18n parity tests for every failure label, explanation, recovery action, modal title, structured action-error reason, operation-specific action, and fallback key.

## Phase 2: GREEN Frontend Safety Boundary

- [x] 2.1 Add one allowlist failure-code mapper near `PostDetailModal.vue` that handles both failed `errorCode` and blocked reason values, and maps missing/unknown/historical values to `unknown`.
- [x] 2.2 Update `PostDetailModal.vue` to render localized failed/blocked title, label, explanation, and action only; remove raw detail/code/message rendering.
- [x] 2.3 Add a separate structured action-error mapper based only on safe API `errorCode`/HTTP status and update retry/delete/reschedule handling to combine localized reason with operation-specific recovery guidance.
- [x] 2.4 Add EN/ES keys in `apps/web/app/src/shared/i18n/locales/{en,es}/postDetail.ts` with equivalent friendly actionable copy.
- [x] 2.5 Ensure `publishing.store.ts` treats failed/blocked categories as opaque data, preserves structured action error metadata, and never transforms backend messages into visible copy.
- [x] 2.6 Replace the hardcoded invalid-reschedule date text with localized post-detail copy.

## Phase 3: RED Backend Taxonomy Tests

- [x] 3.1 Add exhaustive failure-model tests for every canonical category, default retryability, and final state.
- [x] 3.2 Add failing `PublishingWorkerTest.kt` cases for missing/unavailable media, validation, rate limit, provider outage, reconnect, unavailable account, and unknown failures.
- [x] 3.3 Prove retryable failures retain their canonical category through every attempt and terminal retry exhaustion.
- [x] 3.4 Prove media metadata resolution failures before provider dispatch record an attempt, never call the provider, and explicitly reschedule or terminate the claimed job.
- [x] 3.5 Prove reconnect writes `ACCOUNT_RECONNECT_REQUIRED` as a blocked category and never persists `exception.message` as `blockedReason`.
- [x] 3.6 Add diagnostic-redaction tests covering provider bodies, tokens, auth URLs/headers, stack text, internal IDs, and bucket/object paths in publication, attempt, and notification-event persistence.
- [x] 3.7 Add calendar/detail serialization tests proving only canonical opaque codes cross the HTTP boundary and technical messages remain absent.

## Phase 4: GREEN Backend Taxonomy

- [x] 4.1 Add the canonical publishing failure model with typed category and explicit retryability; unknown exceptions map to `PUBLISHING_FAILED` without message parsing.
- [x] 4.2 Update media-resolution ports/adapters to distinguish typed missing-media from temporarily unavailable media signals.
- [x] 4.3 Update `LinkedInPublishingAdapters.kt` to distinguish typed auth reconnect, validation, rate limit, provider unavailable, media missing/unavailable, and unknown failure paths without embedding provider bodies in exception messages.
- [x] 4.4 Move media resolution and every pre-dispatch stage inside the `PublishingWorker.kt` guarded failure boundary.
- [x] 4.5 Preserve the typed category across retry attempts and use it before `markFailed` after exhaustion.
- [x] 4.6 Persist canonical blocked and failed codes, set publication technical messages to null/safe copy, and sanitize delivery-attempt diagnostics before persistence.
- [x] 4.7 Sanitize existing notification-event messages/actions so they never contain raw exceptions or provider responses; do not add new notification channels.
- [x] 4.8 Keep `PublishingPolicies.kt` domain state generic: accept canonical nullable code and safe/null message without UI semantics.

## Phase 5: Refactor and Focused Verification

- [x] 5.1 Refactor duplicate frontend/backend taxonomy fixtures while keeping the design taxonomy authoritative and mappings exhaustive.
- [x] 5.2 Run focused frontend component, store, and i18n parity tests through the repo `just`/pnpm commands.
- [x] 5.3 Run focused backend worker, transaction, notification, and HTTP serialization tests through the repo `just`/Gradle commands.
- [x] 5.4 Run `just frontend-lint`, `just frontend-test`, and `just backend-test-fast` — latest verify executed all three successfully; apply follow-up reran focused checks and the configured Gradle build.
- [x] 5.5 Verify deployment notes require frontend safety first and backend-first rollback while the frontend guardrail remains deployed.
