# Publishing Failure Modes

**Last Updated:** 2026-08-29
**Status:** Active

## Overview

This document catalogs the canonical publishing failure taxonomy used across the
Profile Tailors publishing pipeline. It defines each failure category, its root
cause, retryability, user-facing impact, and recommended developer response.

The taxonomy is the single source of truth for how async publishing failures are
classified, persisted, and surfaced. The frontend maps these categories to
localized user-facing messages via an allowlist boundary (see
[Frontend Failure Mapping](#frontend-failure-mapping)).

The full specification lives in
[`openspec/specs/publishing/spec.md`](../openspec/specs/publishing/spec.md) under
the requirements: *Typed Failure Classification and Retry Semantics*,
*Unknown and Historical Failure Compatibility*, *Server-Side Diagnostic
Redaction*, and *Localized Failure Copy and Actions*.

## Changes

This taxonomy was introduced as part of DALLAY-484 (PR #335) to eliminate raw
exception leakage and establish a stable, provider-neutral failure
classification system. Prior to this change, the UI displayed internal exception
names such as `StorageObjectNotFoundException` and generic transport errors like
`Request failed`.

## Canonical Failure Categories

All new backend `FAILED` and `BLOCKED` outcomes MUST persist one of these
categories. Unknown exceptions map to `PUBLISHING_FAILED`.

| Category                     | Auto-Retryable | State     | Root Cause                                          |
|------------------------------|----------------|-----------|-----------------------------------------------------|
| `MEDIA_NOT_FOUND`            | No             | `FAILED`  | Asset metadata or binary no longer exists           |
| `MEDIA_UNAVAILABLE`          | Yes            | `FAILED`  | Temporary media/storage access failure              |
| `PROVIDER_VALIDATION_FAILED` | No             | `FAILED`  | Provider or capability rejected the content         |
| `PROVIDER_RATE_LIMITED`      | Yes            | `FAILED`  | Provider returned HTTP 429                          |
| `PROVIDER_UNAVAILABLE`       | Yes            | `FAILED`  | Provider network or HTTP 5xx failure                |
| `ACCOUNT_RECONNECT_REQUIRED` | N/A            | `BLOCKED` | Expired, revoked credentials or insufficient scopes |
| `ACCOUNT_UNAVAILABLE`        | No             | `FAILED`  | Disabled or deleted terminal account                |
| `PUBLISHING_FAILED`          | No             | `FAILED`  | Unexpected exception (catch-all)                    |

The **Auto-Retryable** column governs whether the publishing worker
automatically reschedules another delivery attempt. It does NOT govern
user-initiated manual retries. Any `FAILED` publication — including
non-auto-retryable categories like `PROVIDER_VALIDATION_FAILED` — remains
eligible for manual retry or rescheduling by an authorized workspace member
(see [Delivery Attempts spec, line 478-479](../openspec/specs/publishing/spec.md)).
When a user manually retries a failed publication, the system resets the
delivery attempt counter and transitions the publication back to `QUEUED`,
regardless of the original failure category. Blocked publications
(`ACCOUNT_RECONNECT_REQUIRED`) require reconnection before a manual retry
can proceed.

### Classification Rules

Classification MUST NOT inspect or parse exception messages, provider response
bodies, or exception simple names. The boundary that understands the failure
type assigns the canonical category. Unknown exceptions always map to
`PUBLISHING_FAILED`.

### Default Provider Mappings

| Condition                                          | Category                     | Auto-Retryable |
|----------------------------------------------------|------------------------------|----------------|
| Missing asset metadata or binary                   | `MEDIA_NOT_FOUND`            | No             |
| Temporary media/storage access failure             | `MEDIA_UNAVAILABLE`          | Yes            |
| Provider/capability content rejection              | `PROVIDER_VALIDATION_FAILED` | No             |
| Provider HTTP 429                                  | `PROVIDER_RATE_LIMITED`      | Yes            |
| Provider network or HTTP 5xx                       | `PROVIDER_UNAVAILABLE`       | Yes            |
| Expired/revoked credentials or insufficient scopes | `ACCOUNT_RECONNECT_REQUIRED` | Blocked        |
| Disabled/deleted terminal account                  | `ACCOUNT_UNAVAILABLE`        | No             |
| Unexpected exception                               | `PUBLISHING_FAILED`          | No             |

## Frontend Failure Mapping

The frontend uses an **allowlist boundary** — it never passes raw error codes,
exception names, or provider messages through to the user. Each canonical
category maps to localized (EN/ES) title, explanation, and recommended action.

Unknown, unmapped, or historical codes (including old exception-class names)
resolve to a safe localized generic fallback. No database migration is required
for historical records.

### Action Failure Mapping

When retry, delete, or reschedule actions fail from the post detail modal, the
UI maps HTTP status to a safe category:

| HTTP Status       | Safe Category           |
|-------------------|-------------------------|
| 401, 403          | Unauthorized            |
| 404               | Not found               |
| 409               | State conflict          |
| 400, 422          | Validation              |
| 429, network, 5xx | Temporarily unavailable |

An explicitly allowlisted backend error code MAY refine the safe category
without introducing backend text. Unrecognized values use the localized unknown
fallback.

### Localization

All user-facing failure strings live in locale files, never hardcoded in
components or stores:

- English: `shared/i18n/locales/en/postDetail.ts`
- Spanish: `shared/i18n/locales/es/postDetail.ts`

Both locales MUST maintain key parity. Locale key tests validate coverage.

## Server-Side Diagnostic Redaction

Publication state and notification events contain only canonical categories and
safe non-technical copy. The worker MUST NOT copy `Throwable.message`,
provider response bodies, or storage paths into publications or notification
events.

Delivery attempt diagnostics MAY retain sanitized server-side information:

- Exception type
- Provider HTTP status
- Non-secret provider correlation IDs

Sanitization removes before persistence:

- Access/refresh tokens
- Authorization headers or URLs
- Provider response bodies
- Stack traces
- Identifiers embedded in messages
- Raw bucket/object paths

## Retry Semantics

### Automatic Worker Retries

Auto-retryable failures (`MEDIA_UNAVAILABLE`, `PROVIDER_RATE_LIMITED`,
`PROVIDER_UNAVAILABLE`) retain the same category across all delivery attempts
and terminal persistence after retry exhaustion.

The publishing worker uses a bounded retry budget configured via:

- `SMP_PUBLISHING_MAX_RETRIES` (default: 3)
- `SMP_PUBLISHING_RETRY_BACKOFF` (default: PT5M)

When the budget is exhausted, the publication transitions to `FAILED` with the
same category it carried throughout retries. Unauthorized or blocked failures
(`ACCOUNT_RECONNECT_REQUIRED`) do not consume retry budget — the publication
transitions to `BLOCKED` immediately.

### Manual User Retries

Any `FAILED` publication remains eligible for manual retry or rescheduling by an
authorized workspace member, regardless of the original failure category. This
includes non-auto-retryable categories such as `PROVIDER_VALIDATION_FAILED`,
`MEDIA_NOT_FOUND`, `ACCOUNT_UNAVAILABLE`, and `PUBLISHING_FAILED`. A manual retry
resets the delivery attempt counter and transitions the publication back to
`QUEUED`. Blocked publications (`ACCOUNT_RECONNECT_REQUIRED`) require the user
to reconnect the social account before a manual retry can proceed.

## Historical Compatibility

Old persisted failed publications may store exception class names (e.g.,
`StorageObjectNotFoundException`) as `last_error_code`. These remain safe
because the frontend allowlist boundary resolves any unrecognized code to the
generic localized fallback. No database migration is needed.

New failed outcomes MUST use the canonical categories listed above.

## Usage

### For Backend Developers

When implementing a new provider adapter or publishing failure path:

1. Identify the failure type at the boundary that understands it.
2. Map it to the canonical category using the table above.
3. Never classify based on exception message parsing.
4. Ensure `DeliveryAttempt.providerMessage` is sanitized (no tokens, paths,
   bodies).
5. Leave `Publication.lastErrorMessage` null or safe.

### For Frontend Developers

When adding new failure UI or error handling:

1. Use the `usePublishingErrors` composable for consistent mapping.
2. Never render raw `errorCode`, `blockedReason`, or `Error.message`.
3. Add new categories to both EN and ES locale files.
4. Use the allowlist boundary — never pass raw codes through.
5. Add locale key parity tests for any new keys.

### Lifecycle Observability

Publishing attempts emit five semantic lifecycle events:

- `publishing_attempt_claimed`
- `publishing_attempt_succeeded`
- `publishing_retry_scheduled`
- `publishing_blocked`
- `publishing_terminal_failure`

Use `jobId` plus `attemptNumber` to identify one execution attempt. Use
`publicationId` to correlate the complete lifecycle across retries,
rescheduling, and replacement jobs. Events contain only allowlisted identifiers,
canonical categories, outcome, retryability, provider, and duration fields.

The structured lifecycle events are documented here because the temporary design plan was removed
after implementation. Treat this section as the durable source for event names, correlation fields,
and data-safety rules.

## Troubleshooting

### A new exception type appears in the UI

The frontend allowlist is missing a mapping. Check if the backend is persisting
a non-canonical category. If it is a new canonical category, add it to the
allowlist and both locale files. If it is an exception class name, fix the
backend to use the canonical taxonomy.

### Historical records show exception names

This is expected and safe. The frontend resolves unrecognized codes to the
generic fallback. No migration is needed unless you want to backfill canonical
categories for cleaner reporting.

### Provider message leaks into persisted data

Check the `DeliveryAttempt.providerMessage` sanitization path. The worker
must sanitize before persistence. If a new provider response pattern bypasses
sanitization, extend the sanitizer to cover it.

## References

- [Publishing Specification](../openspec/specs/publishing/spec.md) — full
  requirements including Typed Failure Classification, Unknown and Historical
  Failure Compatibility, Server-Side Diagnostic Redaction, Localized Failure
  Copy and Actions
- [DALLAY-484 / PR #335](https://github.com/dallay/profiletailors.com/pull/335)
  — implementation that introduced the taxonomy
- [Production Secrets](production-secrets.md) — `PUBLISHING_CREDENTIALS_KEY`
  and related credential encryption
- [Getting Started](getting-started.md) — publishing worker configuration
  variables
