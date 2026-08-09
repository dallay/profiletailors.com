# Delta for Publishing

## ADDED Requirements

### Requirement: Publishing Beta Operations Are Observable and Reversible (DALLAY-555)

Publishing MUST expose sufficient durable state for an operator to distinguish queued, processing,
successful, blocked, failed, retryable, and stale work without reading raw exceptions or provider
secrets. The worker MUST honor existing claim, idempotency, bounded-retry, typed-failure, and diagnostic
redaction contracts. Publishing acceptance MUST be classified `USER_REPORTED_OPERATIONAL`; it MUST NOT
be labeled provider-verified, production-verified, or `MULTI_USER_VERIFIED`.

#### Scenario: Failure is visible and safe

- GIVEN a claimed job encounters a typed retryable or terminal failure
- WHEN the worker records the outcome
- THEN the publication/job MUST show a safe canonical state and recovery action
- AND no raw exception, token, provider response, stack trace, or storage path may be client-visible

#### Scenario: Stale work is actionable

- GIVEN a job remains claimed or processing beyond the configured stale threshold
- WHEN an operator or diagnostic check reviews the queue
- THEN the job MUST be identifiable as stale with its publication, workspace, age, and safe next action
- AND it MUST NOT be silently treated as published

#### Scenario: Worker safe-off prevents new delivery

- GIVEN the publishing worker is disabled through the supported operational control
- WHEN due jobs exist
- THEN no new provider delivery call may be initiated
- AND persisted jobs MUST remain recoverable for later review or controlled re-enable

### Requirement: Live Publishing Evidence Is Separated From Code Evidence

Focused tests, BDD, WireMock, and E2E results MAY establish code and contract behavior only. A managed
VPS run MAY establish that the deployed instance produced the recorded user-visible result at a stated
time. Neither class establishes provider-side delivery unless the evidence explicitly comes from the
provider; this change MUST NOT make that claim. Missing timestamps, deployment identity, or scope MUST
block the publishing acceptance record.

#### Scenario: User-reported publish result is classified correctly

- GIVEN an operator submits a live beta publish or schedule report without provider-side evidence
- WHEN the evidence ledger is updated
- THEN the result MUST be stored as `USER_REPORTED_OPERATIONAL`
- AND the DALLAY-559 gate MUST retain the provider-verification limitation
