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
