# Delta for Lead Capture Waitlist

## Overview

This delta defines the waitlist bounded context: aggregate, entity, consent value object, CQRS
commands/handlers, and repository ports. All types MUST be pure Kotlin with no framework
dependencies.

## Changes

### ADDED Requirements

#### Requirement: Waitlist Aggregate

`Waitlist` MUST be an aggregate root with a `WaitlistStatus` (`draft`, `active`, `paused`, `closed`,
`archived`). A waitlist with status `active` MUST accept new entries. A waitlist with status
`paused`, `closed`, or `archived` MUST NOT accept new entries. Status transitions MUST follow valid
lifecycle paths.

#### Scenario: Active waitlist accepts entries

- GIVEN a waitlist with status `active`
- WHEN a new entry is attempted
- THEN the entry MUST be accepted

#### Scenario: Paused waitlist rejects entries

- GIVEN a waitlist with status `paused`
- WHEN a new entry is attempted
- THEN the entry MUST be rejected with a `waitlist_closed` error

#### Scenario: Unknown waitlist key returns not found

- GIVEN a waitlist key that does not exist
- WHEN a new entry is attempted
- THEN the handler MUST signal `waitlist_not_found` (distinct from `waitlist_closed`)

#### Requirement: WaitlistEntry Entity

`WaitlistEntry` MUST have a `WaitlistEntryStatus` (`pending`, `invited`, `converted`, `cancelled`).
It MUST track lifecycle timestamps: `joined_at`, `invited_at` (nullable), `converted_at` (nullable),
`cancelled_at` (nullable). A new entry starts as `pending`.

#### Scenario: New entry starts pending

- GIVEN a valid email and consent for an active waitlist
- WHEN a `WaitlistEntry` is created
- THEN its status MUST be `pending`
- AND `joined_at` MUST be set to the current time

#### Requirement: WaitlistConsent Value Object

`WaitlistConsent` MUST live inside the waitlist domain (NOT in `common`). It MUST have explicit
`earlyAccess` (boolean, required true) and `marketing` (boolean, default false) flags and a
`version` tag. Marketing consent MUST NEVER be implicit from joining the waitlist.

#### Scenario: Early access consent required

- GIVEN a join attempt where `consent.earlyAccess` is `false` or missing
- WHEN the `JoinWaitlistHandler` processes the command
- THEN the command MUST be rejected with a `consent_required` error

#### Scenario: Marketing consent defaults to false

- GIVEN a join attempt where `consent.marketing` is not provided
- WHEN the `WaitlistConsent` is created
- THEN `marketing` MUST default to `false`
- AND it MUST NOT be inferred from `earlyAccess`

#### Requirement: Idempotent Join

`JoinWaitlistHandler` MUST be idempotent: the same email joining the same waitlist returns the same
`accepted` result whether the entry is new or already exists. The handler MUST NOT leak the
distinction between `joined_new`, `already_joined`, and `reactivated` to the public API.

#### Scenario: New join returns accepted

- GIVEN an active waitlist and an email not previously joined
- WHEN the `JoinWaitlistHandler` processes the command
- THEN the result MUST be `accepted`

#### Scenario: Duplicate join returns accepted

- GIVEN an active waitlist and an email that already joined
- WHEN the `JoinWaitlistHandler` processes the command
- THEN the result MUST be `accepted` (same as new join)

#### Scenario: Internal distinction is not public

- GIVEN the handler processes a duplicate join
- WHEN the result is returned
- THEN the public result MUST NOT distinguish `joined_new` from `already_joined`

#### Requirement: Email Deduplication Per Waitlist

Email deduplication MUST be per waitlist, not global. The same email MAY join different waitlists.
The constraint is `UNIQUE(waitlist_id, normalized_email)`.

#### Scenario: Same email different waitlists

- GIVEN email `"user@example.com"` joins waitlist A
- WHEN the same email joins waitlist B
- THEN both joins MUST succeed

#### Scenario: Same email same waitlist is idempotent

- GIVEN email `"user@example.com"` joins waitlist A
- WHEN the same email joins waitlist A again
- THEN the result MUST be `accepted` (idempotent, no error)

#### Requirement: Repository Ports

`WaitlistRepository` and `WaitlistEntryRepository` MUST be defined as ports in
`shared/lead-capture/waitlist/application/ports`. They MUST be interfaces with no framework
dependencies. The infrastructure layer provides R2DBC implementations.

#### Scenario: Port is framework-free

- GIVEN the `WaitlistRepository` interface
- WHEN inspected for imports
- THEN it MUST NOT import `io.r2dbc.*` or `org.springframework.*`

#### Requirement: Framework Isolation

All types in `shared/lead-capture/waitlist` MUST NOT import or depend on `org.springframework.*`,
`io.r2dbc.*`, `com.profiletailors.smp.*`, or any server-side framework. This MUST be verified by
ArchUnit or equivalent module-boundary tests.

#### Scenario: No Spring imports in waitlist

- GIVEN the compiled classes of `shared/lead-capture/waitlist`
- WHEN inspected for imports
- THEN no class MUST import any `org.springframework.*` package

#### Scenario: No R2DBC imports in waitlist

- GIVEN the compiled classes of `shared/lead-capture/waitlist`
- WHEN inspected for imports
- THEN no class MUST import any `io.r2dbc.*` package

#### Scenario: No server package dependency in waitlist

- GIVEN the compiled classes of `shared/lead-capture/waitlist`
- WHEN inspected for imports
- THEN no class MUST import any `com.profiletailors.smp.*` package

---

### ADDED Requirements (DSAR Integration — DALLAY-493)

#### Requirement: Waitlist Entry DSAR Lookup and Anonymization

`WaitlistEntryRepository` MUST support lookup by normalized email (case-insensitive, trimmed). An
`anonymizeEmail(entryId)` operation MUST replace the `email` field with `[REDACTED on {timestamp}]`
and clear PII in `metadata` (→ `{}`).

##### Scenario: Lookup matches normalized email

- GIVEN entries with emails `"User@Example.com"` and `"other@x.com"`
- WHEN `findByNormalizedEmail("user@example.com")` is called
- THEN the entry with email `"User@Example.com"` MUST be returned

##### Scenario: Anonymization clears PII

- GIVEN an entry with `email = "user@x.com"` and `metadata = {"name":"John"}`
- WHEN `anonymizeEmail(entryId)` is called
- THEN `email` MUST be `[REDACTED on 2026-07-19T12:00:00Z]`
- AND `metadata` MUST be `{}`

#### Requirement: Correction Propagation

When `email` is corrected on a `user_identities` row, the correction MUST propagate to waitlist
entries matching BOTH old and new email (entries with the old email are updated to the new email).
Entries matching only the old or only the new email MUST NOT be affected.

##### Scenario: Propagation updates matching entries

- GIVEN an entry with `email = "old@x.com"`
- AND a correction from `old@x.com` to `new@x.com`
- WHEN propagation runs
- THEN the entry's email MUST become `"new@x.com"`

---

## MVP Rate-Limiting Decision

The MVP MUST NOT introduce Redis, another distributed bucket store, or any other distributed
rate-limit implementation. The accepted MVP behavior is:

- The shared rate-limit adapter uses a bounded, per-JVM Caffeine cache for Bucket4j buckets.
- SMP's waitlist limiter defaults to disabled through
  `SMP_WAITLIST_RATE_LIMIT_ENABLED:false` in `server/smp/src/main/resources/application.yaml`.
- An operator MAY explicitly enable the waitlist limiter, but that opt-in is per instance and MUST
  NOT be treated as safe distributed enforcement.
- Multi-replica waitlist rate-limit enablement remains deferred until DALLAY-512 (distributed
  bucket backend) and DALLAY-513 (trusted proxy / client-identity handling) are resolved.

The two-replica burst and shared-window-reset scenarios are follow-up scenarios only. They are not
current MVP acceptance criteria and MUST NOT be used to claim that the per-JVM bucket is globally
enforced across replicas.
