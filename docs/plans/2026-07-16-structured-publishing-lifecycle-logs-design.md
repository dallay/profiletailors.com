# Structured Publishing Lifecycle Logs Design

## Overview

This design closes the remaining automated observability gap in DALLAY-466 by adding a small, stable set of semantic lifecycle logs to the publishing worker.

The change is intentionally limited to publishing attempt reconstruction. It does not introduce a logging dependency, MDC, a JSON encoder, OpenTelemetry configuration, metrics, dashboards, or centralized ingestion. Real LinkedIn connection and publication verification remains a manual release step performed with user-managed credentials.

## Changes

### Lifecycle events

`PublishingWorker` and `PublishingJobExecutor` will emit these events:

| Event | Emission point | Level |
| --- | --- | --- |
| `publishing_attempt_claimed` | After a due job is successfully claimed and before execution starts | `INFO` |
| `publishing_attempt_succeeded` | After the success transaction commits | `INFO` |
| `publishing_retry_scheduled` | After the retry transaction commits | `WARN` |
| `publishing_blocked` | After the blocked transaction commits | `WARN` |
| `publishing_terminal_failure` | After the terminal-failure transaction commits | `ERROR` |

Outcome events MUST be emitted only after the corresponding transaction completes successfully. A transaction rollback MUST NOT produce a misleading success, retry, blocked, or terminal-failure lifecycle event.

### Stable safe fields

Each event will use explicit SLF4J placeholders with a stable `key={}` format and the following required schema:

| Event | Required fields |
| --- | --- |
| `publishing_attempt_claimed` | `event`, `publicationId`, `jobId`, `workspaceId`, `attemptNumber`, `provider` |
| `publishing_attempt_succeeded` | `event`, `publicationId`, `jobId`, `workspaceId`, `attemptNumber`, `provider`, `outcome=SUCCEEDED`, `durationMs` |
| `publishing_retry_scheduled` | `event`, `publicationId`, `jobId`, `workspaceId`, `attemptNumber`, `provider`, `outcome=FAILED`, `failureCategory`, `retryable=true`, `durationMs` |
| `publishing_blocked` | `event`, `publicationId`, `jobId`, `workspaceId`, `attemptNumber`, `provider`, `outcome=BLOCKED`, `failureCategory`, `retryable=false`, `durationMs` |
| `publishing_terminal_failure` | `event`, `publicationId`, `jobId`, `workspaceId`, `attemptNumber`, `provider`, `outcome=FAILED`, `failureCategory`, `retryable=false`, `durationMs` |

`jobId` plus `attemptNumber` identifies a specific publishing attempt. `publicationId` correlates the complete publication lifecycle across retries, rescheduling, and replacement jobs.

A small `PublishingLifecycleLogger` class local to the scheduling infrastructure will own this contract through five explicit methods: `claimed`, `succeeded`, `retryScheduled`, `blocked`, and `terminalFailure`. It remains a bounded-context helper backed directly by SLF4J, not a platform abstraction. The physical output remains the current text logging format while preserving machine-extractable semantic fields.

### Data safety

Lifecycle events use an allowlist. They MUST NOT include:

- access or refresh tokens;
- authorization headers;
- post title or body content;
- email addresses;
- signed URLs or storage paths;
- provider payloads or response bodies;
- raw provider messages;
- exception messages;
- stack traces as structured field values.

Existing technical exception logging may retain a throwable only where already appropriate and safe. Throwable data is not part of the lifecycle event schema.

### Duration measurement

`PublishingJobExecutor` owns attempt duration measurement completely. It uses its injected `Clock`, measuring from the beginning of `executeClaim` until the persisted outcome completes. Duration is clamped to a non-negative millisecond value so deterministic or adjusted test clocks cannot produce negative telemetry.

No MDC, shared timing state, coroutine-local context, or executor signature change will be introduced.

### Boundaries

The change stays inside the publishing scheduling infrastructure:

- `PublishingWorker` owns claim logging only.
- `PublishingJobExecutor` owns attempt timing plus success, retry, blocked, and terminal-failure logging because it knows the canonical outcome and transaction result.
- Domain models and repository ports remain unchanged unless a narrow internal value object is required for timing context.
- Global logging configuration remains unchanged.

## Usage

Operators can reconstruct an attempt by filtering on `publicationId` or `jobId` and sorting events chronologically. A healthy immediate publication should produce:

```text
event=publishing_attempt_claimed
...
event=publishing_attempt_succeeded outcome=SUCCEEDED
```

A retryable provider outage should produce:

```text
event=publishing_attempt_claimed
...
event=publishing_retry_scheduled outcome=FAILED failureCategory=PROVIDER_UNAVAILABLE retryable=true
```

A reconnect requirement should produce:

```text
event=publishing_attempt_claimed
...
event=publishing_blocked outcome=BLOCKED failureCategory=ACCOUNT_RECONNECT_REQUIRED retryable=false
```

A non-retryable or exhausted failure should end with `publishing_terminal_failure`.

## Testing

Implementation follows TDD.

Focused tests will capture Logback events and verify:

1. Claim emits identifiers and attempt number.
2. Success emits provider, `SUCCEEDED`, and non-negative duration.
3. Retry scheduling emits the canonical failure category and `retryable=true`.
4. Blocked handling emits the canonical blocked category.
5. Terminal failure emits the canonical category and retryability.
6. Lifecycle events do not contain post content, credentials, raw diagnostics, storage paths, or provider payloads.
7. Transaction rollback does not emit an outcome event that claims persistence succeeded.
8. Every lifecycle event contains its exact required field set without relying on a brittle full-message equality assertion.

During development, verification will run the focused `PublishingWorkerTest` and transaction tests. Before merge, regression verification will run `just backend-test-fast` and `just backend-lint`. The full infrastructure-heavy CI suite remains delegated to normal PR CI unless focused verification exposes cross-cutting impact.

## Troubleshooting

### An attempt has a claim but no outcome

Check for an unexpected process termination or a failure before the executor reached a persisted outcome. Existing repository and transaction diagnostics remain authoritative; the lifecycle logs intentionally do not duplicate raw exception data.

### Structured fields are not parsed automatically

The MVP implementation emits semantic `key=value` text fields. Collector-specific parsing or a global JSON encoder is a separate platform decision.

### A sensitive value appears in a lifecycle event

Treat it as a security defect. Remove the field or replace it with a safe canonical category. Do not expand the allowlist with raw provider or content data.

## References

- [Publishing Failure Modes](../publishing-failure-modes.md)
- [Release Verification](../release-verification.md)
- [Publishing Specification](../../openspec/specs/publishing/spec.md)
- [DALLAY-466](https://linear.app/dallay/issue/DALLAY-466/launch-readiness-mvp-release-checklist)
