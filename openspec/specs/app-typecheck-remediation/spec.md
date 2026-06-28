# Delta for App Type-Check Remediation

## ADDED Requirements

### Requirement: Deterministic Zero-Error Type-Check

The app MUST pass the existing complete type-check without exclusions, weakened compiler settings,
or suppressive broad casts.

#### Scenario: Repeatable clean gate

- GIVEN the remediation changes are applied
- WHEN `pnpm --filter app type-check` runs twice consecutively
- THEN both runs MUST complete with zero TypeScript errors

### Requirement: Behavior Preservation

The remediation MUST preserve existing product behavior, API contracts, routes, lifecycle values,
and compiler coverage.

#### Scenario: Existing behavior remains valid

- GIVEN the type corrections are complete
- WHEN focused tests and the full app unit suite run
- THEN existing composer, scheduler, media, settings, and hashing behavior MUST remain unchanged

### Requirement: Scheduler Day Canonicalization

The scheduler day route MUST canonicalize to the existing week surface and MUST NOT introduce a new
day surface behavior.

#### Scenario: Day route uses week fallback

- GIVEN a scheduler URL identifies the day route
- WHEN the scheduler surface is resolved
- THEN the result MUST be the week surface

### Requirement: Typed Media Presentation

The media UI MUST map both `PENDING_UPLOAD` and `UPLOADING` CAS statuses to its existing in-progress
presentation without adding `PROCESSING` to the API status union. Assets without an active workspace
MUST NOT be submitted to the media API.

#### Scenario: CAS statuses share presentation

- GIVEN media assets respectively have `PENDING_UPLOAD` and `UPLOADING` statuses
- WHEN filters, counts, and selection rules are evaluated
- THEN both MUST receive the existing in-progress presentation and selection exclusions

#### Scenario: Workspace is absent

- GIVEN no active workspace exists
- WHEN an asset upload is requested
- THEN the app MUST return or fail deterministically without calling the media API

### Requirement: Contract-Correct Tests

Test doubles, imports, fixtures, and indexed values MUST satisfy the production contracts and strict
TypeScript settings.

#### Scenario: Browser file stream double

- GIVEN file hashing tests exercise native stream paths and threshold boundaries
- WHEN a `File.stream()` double is supplied
- THEN it MUST use the DOM-compatible stream contract and preserve digest results

#### Scenario: Strict test contracts

- GIVEN tests use Vitest hooks, router mocks, regex captures, or scheduler activity fixtures
- WHEN they are type-checked
- THEN hooks MUST be explicitly imported, mocks MUST use the established typed boundary, captures
  MUST be checked, and activity MUST use `{date,density,count}`

### Requirement: Vitest Configuration Typing

The Vite configuration MUST type Vitest fields through Vitest-aware configuration types without
suppressing validation.

#### Scenario: Configuration loads

- GIVEN the app configuration includes a `test` section
- WHEN Vitest loads it and the app is type-checked
- THEN both operations MUST succeed without configuration type errors

### Requirement: Timer and Date Typing

Timers MUST use environment-neutral timeout typing, and composer date absence and schedule modes
MUST remain aligned with publishing contracts.

#### Scenario: Timer lifecycle

- GIVEN settings feedback schedules dismissal
- WHEN the timer fires or the view unmounts
- THEN dismissal and cleanup MUST remain type-safe and behaviorally unchanged

#### Scenario: Composer scheduling values

- GIVEN immediate, next-slot, custom, or edit scheduling is submitted
- WHEN date and mode values are resolved
- THEN absence MUST use the contract’s canonical representation and mode MUST remain within its
  literal union

### Requirement: CAS E2E PR 1 Isolation

Unrelated CAS E2E PR 1 files MUST remain untouched and MUST NOT be modified or staged by this
remediation.

#### Scenario: Final ownership check

- GIVEN CAS E2E PR 1 changes already exist in the working tree
- WHEN the remediation diff and staging area are inspected
- THEN no remediation-owned edit or staging change MUST affect `Justfile`,
  `apps/web/app/package.json`, or `apps/web/app/e2e/**`
