# Delta for scheduler-url-state-standard

## ADDED Requirements

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
