# Delta for Publishing

## Overview

This specification defines the ADDED requirements for PR #624 social content foundation remediation. These requirements establish typed failures, CQRS boundaries, bounded pagination with checkpoints, reply idempotency states, domain invariants, API validation, and test integration scope for the social content foundation.

This backend handler-level feature has no public HTTP surface, so Cucumber/BDD scenarios are not applicable. This is an explicit, approved exception: direct handler tests, fake provider tests, and static migration tests provide the applicable coverage. If a public HTTP endpoint is added in the future, appropriate Cucumber scenarios will be required.

## Changes

### ADDED Requirements

#### Requirement: Typed Failures and CQRS Boundaries

Capability denials MUST be REAUTH_REQUIRED, ROLE_REQUIRED, MISSING_SCOPE, or UNSUPPORTED; provider failures MUST be UNAUTHORIZED, ROLE_FORBIDDEN, or RATE_LIMITED. A denial MUST make no provider call. Discovery, post sync, comment sync, and reply MUST have dedicated CQRS command/query handler boundaries. Only rate limits MAY be retried, with a finite limit.

**Scenario: Denial is safe**
- GIVEN an actor lacks a required scope or role
- WHEN its handler runs
- THEN it MUST return the matching typed denial and make no provider call

#### Requirement: Bounded Pagination and Checkpoints

Post and comment sync MUST honor pageSize and maxPages, detect repeated non-null cursors, and fail without looping. Persistence and post tombstoning MUST occur only after bounded completion. Checkpoints MUST be workspace/actor/resource scoped, resume from their cursor, preserve a high-water mark unless newer, and update lastSuccessfulAt only after success. Failure, bound exhaustion, or repetition MUST leave prior checkpoint and tombstone state unchanged.

**Scenario: Successful resume**
- GIVEN a checkpoint cursor C1
- WHEN synchronization completes within the page bound
- THEN the first call MUST use C1 and the checkpoint MUST record terminal cursor, high-water mark, and success time

**Scenario: Guard failure is safe**
- GIVEN a provider repeats a cursor or remains paged after maxPages
- WHEN synchronization runs
- THEN it MUST raise a typed failure without persistence, tombstoning, or checkpoint replacement

#### Requirement: Reply Idempotency States

Reply idempotency MUST be keyed by workspace and key and validate scope, actor, parent, thread, expiry, and capability before provider execution. Existing SUCCEEDED, FAILED, or PROCESSING results MUST be returned unchanged without provider calls; recovery MUST be explicit. A new claim MUST persist PROCESSING, then exactly one terminal success with external ID or failure.

**Scenario: Duplicate reply is deterministic**
- GIVEN an existing result in any reply state
- WHEN the same command is submitted
- THEN the stored result MUST be returned and the provider MUST not be called

#### Requirement: Domain Invariants and ByteArray Equality

SocialPost and SocialComment MUST reject blank identity/body values and preserve workspace/external identity. mutationAllowed MUST require PROFILETAILORS origin plus a non-blank local publication ID. Expiry MUST be inclusive at the boundary. PayloadCache equality and hash code MUST compare encrypted bytes by content, not array reference.

**Scenario: Invalid values and equal payloads**
- GIVEN invalid post/comment fields or caches with equal bytes in distinct arrays
- WHEN values are constructed or compared
- THEN invalid values MUST fail and equal-byte caches MUST be equal with equal hash codes

#### Requirement: API Month and Workspace FK Validation

apiVersion MUST be six digits in YYYYMM with month 01..12; impossible months MUST fail construction. Social-content tables referencing social_accounts MUST enforce a composite workspace/account relationship, preserving uniqueness and rollback.

**Scenario: Invalid month or account scope fails**
- GIVEN 202600, 202613, or a row for workspace A referencing an account in B
- WHEN configuration or persistence is validated
- THEN it MUST fail; same-workspace rows MUST remain valid

#### Requirement: Fakes, Cleanup, and Test Integration Scope

Tests MUST directly cover fake pagination, typed failures, identity isolation, upsert/tombstone scope, and reply claim/save transitions. Mutable fixtures MUST be reset or recreated. This foundation has no public HTTP surface, so Cucumber coverage is NOT required unless one is introduced. This is an explicit, approved BDD exception: backend handler-level features without HTTP endpoints use direct handler tests and static migration tests as applicable coverage. Static Liquibase tests MUST cover changelog inclusion, constraints, indexes, and rollback; Postgres/Testcontainers coverage is required only when composite-FK or migration behavior needs live proof.

**Scenario: Fake state is isolated**
- GIVEN two workspaces use a fake or tests use mutable fixtures
- WHEN one operation mutates state
- THEN the other workspace and subsequent tests MUST remain unaffected

#### Requirement: Review-Thread Responses

Remediation MUST provide classification, evidence, and scope decisions for stale/out-of-scope comments. The missing architecture-docs-sync.md comment MUST state documentation status and a bounded decision. Ratelimit feedback outside PR #624 MUST be answered out of scope without changing shared/shield/ratelimit or PR #625. The already-addressed mutationAllowed comment MUST cite current behavior and its test without duplicate code.

**Scenario: Non-code comment is closed**
- GIVEN a stale, reply-only, or unrelated review comment
- WHEN remediation is reported
- THEN its response MUST identify evidence and decision, and unrelated code MUST remain unchanged

## Usage

Handlers are directly constructible in tests without Spring wiring:
```kotlin
val syncHandler = ImportSocialPostsHandler(
    provider, postRepository, checkpointRepository,
    capabilityResolver, retention, syncLimits, retryPolicy
)
syncHandler.handle(SyncSocialPostsCommand(actor, now))
```

The BDD exception applies: no Cucumber scenarios are required because no HTTP surface exists. If HTTP endpoints are added, corresponding Cucumber scenarios will become required.

## Troubleshooting

- Cucumber/BDD coverage: This is an explicit exception. Backend handler-level features with no HTTP surface use direct handler tests. If HTTP endpoints are introduced, Cucumber scenarios will be required.
- Review closure: Evidence and decisions are documented; GitHub communication is external to the implementation.
- Live PostgreSQL proof: Static tests pass; live proof required only when composite-FK enforcement needs verification against a running database.

## References

- Design document: ../../design.md
- Implementation progress: ../../apply-progress.md
- Task breakdown: ../../tasks.md
- Verification report: ../../verify-report.md
- Handlers: /server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/
- Migration: /server/smp/src/main/resources/db/changelog/publishing/017-social-content-workspace-fks.yaml
