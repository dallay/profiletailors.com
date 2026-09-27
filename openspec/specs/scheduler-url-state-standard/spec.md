# scheduler-url-state-standard Specification

## Purpose

Define the scheduler URL contract so scheduler state is canonical, shareable, restorable, and
documented for future SPA work.

## Requirements

### Requirement: Canonical scheduler surfaces and route family

The system MUST treat `/scheduler/calendar/week`, `/scheduler/calendar/month`, and `/scheduler/list`
as the only canonical scheduler surfaces. Navigating to `/scheduler` MUST redirect to
`/scheduler/calendar/week` while preserving query params. `/scheduler/calendar/day` MUST NOT remain
a canonical surface; if requested, the system SHALL canonicalize to `/scheduler/calendar/week` with
the same scheduler query state.

#### Scenario: Base scheduler route canonicalizes to week

- GIVEN a user opens `/scheduler?date=2026-07-10&q=launch`
- WHEN the router resolves the location
- THEN the URL MUST become `/scheduler/calendar/week?date=2026-07-10&q=launch`
- AND the scheduler MUST render week surface state

#### Scenario: Legacy day route is canonicalized

- GIVEN a user opens `/scheduler/calendar/day?date=2026-07-10&timezone=Europe/Madrid`
- WHEN the router resolves the location
- THEN the URL MUST become `/scheduler/calendar/week?date=2026-07-10&timezone=Europe/Madrid`
- AND no day-specific canonical route MUST remain

### Requirement: Scheduler query parameter contract

The system MUST parse and serialize scheduler state with these query params only: `date`,
`timezone`, `status`, `q`, repeated `channels[]`, `postId`, and `view`. `date` MUST use `YYYY-MM-DD`.
`timezone` MUST use an IANA zone. `channels[]` MUST contain social account IDs and MAY repeat.
Absent params SHALL mean unfiltered state except `date`, which SHALL resolve to the current local
date, and `view`, which SHALL resolve to the surface default.

#### Scenario: Shareable filtered URL round-trips

- GIVEN a user is on month view with date, timezone, status, q, and two channels selected
- WHEN the user refreshes or shares the URL
- THEN the same surface and query state MUST restore
- AND the same filtered scheduler context MUST render

#### Scenario: Clearing filters removes query keys

- GIVEN the URL includes `status`, `q`, and `channels[]`
- WHEN the user clears those filters
- THEN the canonical URL MUST remove those query keys
- AND `date` and `timezone` MAY remain if still active context

### Requirement: Scheduler view query parameter

The scheduler URL contract MUST accept a `view` query parameter whose value is one of the
discriminated-union values `day`, `3-days`, `week`, `month`, or `agenda`. The default value of
`view` for each canonical surface MUST be:

- `/scheduler/calendar/week` defaults to `view=week`
- `/scheduler/calendar/month` defaults to `view=month`
- `/scheduler/list` renders the fixed view `agenda` and MUST NOT permit any other `view` value

The scheduler MUST treat an absent `view` query parameter as the surface default. The scheduler
MUST canonicalize any `view` value outside the allowed set to the surface default by replacing
the URL rather than rejecting the navigation. The `view` key MUST be omitted from the URL whenever
its effective value equals the surface default, matching the existing rule that absent keys
denote default state. `view` is orthogonal to `date`, `timezone`, `status`, `q`, `channels[]`, and
`postId`; those keys MUST round-trip alongside `view` regardless of the rendered surface.

#### Scenario: Mobile agenda view shares URL state

- GIVEN a user opens `/scheduler/calendar/week?view=agenda&date=2026-07-10&timezone=Europe/Madrid`
  with `status=draft` and one channel `channels[]=acc-123`
- WHEN the user navigates to `/scheduler/list`
- THEN the URL MUST become `/scheduler/list?date=2026-07-10&timezone=Europe/Madrid&status=draft&channels[]=acc-123`
- AND the agenda surface MUST render the same filter context
- AND `view` MUST be omitted from the URL because the surface default for `/scheduler/list` is
  `agenda`

#### Scenario: Invalid view value is canonicalized

- GIVEN a user opens `/scheduler/calendar/week?view=hourly`
- WHEN the router resolves the location
- THEN the URL MUST be canonicalized to `/scheduler/calendar/week`
- AND the week timeline MUST render
- AND the canonicalization MUST use replace semantics so browser history does not gain an extra
  entry

#### Scenario: View key omitted when it matches the surface default

- GIVEN a user is on `/scheduler/calendar/week` with no filters and `date=2026-07-10`
- WHEN the scheduler canonicalizes the URL after a normal navigation
- THEN the URL MUST NOT contain `view=week`
- AND `date=2026-07-10` MAY remain
- AND the canonical URL MUST round-trip to the same rendered week timeline

#### Scenario: Legacy day route is canonicalized

- GIVEN a user opens `/scheduler/calendar/day?date=2026-07-10&timezone=Europe/Madrid`
- WHEN the router resolves the location
- THEN the URL MUST become `/scheduler/calendar/week?date=2026-07-10&timezone=Europe/Madrid`
- AND no day-specific canonical route MUST remain
- AND `view` MUST be omitted from the URL because `week` is the surface default
  for `/scheduler/calendar/week`

### Requirement: URL is the source of truth for scheduler state

The scheduler MUST derive visible surface, date, timezone, filters, and selected post from the
current URL rather than local-only UI state. Refresh, deep links, and browser back/forward SHALL
restore the same scheduler context. Deliberate surface/date/channel changes MUST create history
entries with push semantics; canonicalization and transient query cleanup MUST use replace
semantics.

#### Scenario: Browser history restores route-owned scheduler state

- GIVEN a user navigates from week to month and then changes channels
- WHEN the user presses browser Back
- THEN the prior scheduler URL state MUST be restored
- AND the scheduler UI MUST match that restored URL state

#### Scenario: Transient cleanup does not pollute history

- GIVEN the scheduler needs to normalize or remove stale query state
- WHEN canonicalization runs
- THEN the URL update MUST use replace semantics
- AND the browser history stack MUST NOT gain an extra entry
