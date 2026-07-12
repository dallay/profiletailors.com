# scheduler-url-state-standard Specification

## Purpose

Define the scheduler URL contract so scheduler state is canonical, shareable, restorable, and documented for future SPA work.

## Requirements

### Requirement: Canonical scheduler surfaces and route family

The system MUST treat `/scheduler/calendar/week`, `/scheduler/calendar/month`, and `/scheduler/list` as the only canonical scheduler surfaces. Navigating to `/scheduler` MUST redirect to `/scheduler/calendar/week` while preserving query params. `/scheduler/calendar/day` MUST NOT remain a canonical surface; if requested, the system SHALL canonicalize to `/scheduler/calendar/week` with the same scheduler query state.

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

The system MUST parse and serialize scheduler state with these query params only: `date`, `timezone`, `status`, `q`, repeated `channels[]`, and `postId`. `date` MUST use `YYYY-MM-DD`. `timezone` MUST use an IANA zone. `channels[]` MUST contain social account IDs and MAY repeat. Absent params SHALL mean unfiltered state except `date`, which SHALL resolve to the current local date.

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

### Requirement: URL is the source of truth for scheduler state

The scheduler MUST derive visible surface, date, timezone, filters, and selected post from the current URL rather than local-only UI state. Refresh, deep links, and browser back/forward SHALL restore the same scheduler context. Deliberate surface/date/channel changes MUST create history entries with push semantics; canonicalization and transient query cleanup MUST use replace semantics.

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
