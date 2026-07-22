# Proposal: Friendly Publishing Errors

## Overview

### Intent

Stop exposing internal publishing failure details to users and replace them with localized,
actionable failure guidance for DALLAY-466 MVP readiness.

Users seeing failed posts should understand:

- what likely went wrong,
- why publishing failed in product terms,
- what recovery action to take,
  without seeing exception class names, raw backend codes, or generic `Request failed` text.

## Changes

### Scope

#### In Scope

- Friendly failed-publication copy in the post detail dialog: problem, reason, and recovery action.
- Frontend allowlist mapping plus localized fallback for unknown `errorCode` values.
- Friendly mapping for retry/delete/reschedule action failures shown from the modal.
- Backend stable async publishing failure-code taxonomy for `FAILED` and `BLOCKED` outcomes.
- Typed backend failure signals that preserve retryability without parsing exception messages.
- Safe handling for media-resolution failures that occur before provider dispatch.
- Sanitized server-side logging, delivery-attempt diagnostics, and existing notification events.
- Regression tests written first for frontend and backend behavior.
- Compatibility for historical rows storing exception class names.

#### Out of Scope

- Full database cleanup/migration for old failed rows.
- New notification/email delivery flows. Sanitizing existing notification-event payloads remains in
  scope.
- Provider-specific support dashboards or analytics.
- Changing retry budgets or queue execution semantics.

### Capabilities

#### New Capabilities

None

#### Modified Capabilities

- `publishing`: Failed publication diagnostics must use stable product failure categories and
  user-safe recovery copy.

### Approach

Use the hybrid approach from exploration.

Frontend becomes the safety boundary: `PostDetailModal.vue` must never render raw unknown codes,
`blockedReason`, or raw `Error.message`; all `FAILED`, `BLOCKED`, and action errors use i18n keys in
`postDetail` EN/ES modules. Known failure categories get specific copy; missing, unknown, or
historical raw values get a generic localized fallback.

Backend must map typed async failure signals to the canonical product taxonomy before persisting
publication state. Media resolution, provider validation, and provider dispatch must run inside the
worker failure boundary. Raw exception messages must not be stored on publications or notification
events. Delivery attempts and logs retain only sanitized diagnostics suitable for server-side
support.

Tests follow TDD: first update/add failing frontend regression tests proving unknown codes and
action failures do not leak internals; then backend tests proving worker persistence uses stable
codes while diagnostics remain available.

### Affected Areas

| Area                                                                              | Impact   | Description                                               |
|-----------------------------------------------------------------------------------|----------|-----------------------------------------------------------|
| `apps/web/app/src/modules/publishing/presentation/components/PostDetailModal.vue` | Modified | Friendly failure presentation and fallback                |
| `apps/web/app/src/shared/i18n/locales/*/postDetail.ts`                            | Modified | EN/ES failure title/body/action keys                      |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts`          | Modified | Preserve/store stable failure codes safely                |
| `server/smp/.../PublishingWorker.kt`                                              | Modified | Persist stable async failure codes                        |
| `server/smp/.../LinkedInPublishingAdapters.kt`                                    | Modified | Classify media/provider failures where needed             |
| `server/smp/.../PublishingProviderPorts.kt` or publishing failure model           | Modified | Define typed failure categories and retryability contract |
| existing publishing notification-event creation                                   | Modified | Store safe category/copy instead of exception messages    |
| frontend/backend tests                                                            | Modified | Regression coverage first                                 |

## Usage

### Success Criteria

- [ ] No failed-publication UI renders exception class names, raw unknown codes, or
  `Request failed`.
- [ ] No blocked-publication UI renders raw `blockedReason` or reconnect exception messages.
- [ ] Known failures show localized problem/reason/recovery copy in EN and ES.
- [ ] Async worker persists canonical stable codes for every new `FAILED` or `BLOCKED` outcome.
- [ ] Media-resolution, validation, and provider-dispatch failures share the same guarded
  retry/failure path.
- [ ] Technical diagnostics remain available only as sanitized logs or delivery-attempt data.
- [ ] Existing notification events and publication rows never persist raw exception/provider
  messages.
- [ ] Frontend and backend regression tests are written before implementation and pass.

## Troubleshooting

### Risks

| Risk                                                       | Likelihood | Mitigation                                                                              |
|------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------|
| Historical exception codes remain in DB                    | High       | Frontend fallback treats all unknown codes as safe generic failures                     |
| Over-broad taxonomy hides useful support detail            | Medium     | Keep redacted exception type, provider status, and correlation data in attempts/logs    |
| Frontend/backend code lists drift                          | Medium     | Use the canonical table in the design and add exhaustive contract tests                 |
| `BLOCKED` continues exposing technical reasons             | High       | Treat blocked reasons as untrusted codes and apply the same allowlist/fallback boundary |
| Pre-dispatch media failures bypass worker failure handling | High       | Move media resolution inside the guarded execution boundary and test it explicitly      |

### Rollback Plan

Deploy and retain the frontend safety boundary before enabling backend taxonomy writes. If backend
changes must be rolled back, roll them back first and keep the safe frontend fallback deployed
because rows written with new codes remain in the database. The frontend guardrail may be rolled
back only after no incompatible rows can be served or a compatible data migration has completed.

## References

### Dependencies

- Existing publishing calendar/detail APIs.
- Existing i18n parity tests.
- DALLAY-466 MVP release readiness timeline.
