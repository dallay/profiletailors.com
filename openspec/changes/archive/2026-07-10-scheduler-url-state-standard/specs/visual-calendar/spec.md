# Delta for visual-calendar

## MODIFIED Requirements

### Requirement: Multi-View Calendar

The system MUST provide day, week, and month views. Week and month views are addressable via `/scheduler/calendar/week` and `/scheduler/calendar/month`. Day view is NOT a top-level route; clicking a day in month/week focuses `date=YYYY-MM-DD` within the current week/month context without a separate route. The scheduler MUST honor route-owned `date`, `timezone`, `status`, `q`, repeated `channels[]`, and `postId` when restoring calendar or list state.
(Previously: Week and month were addressable and day was not a top-level route, but the requirement did not define the full route-owned query contract or modal deep-link restoration.)

#### Scenario: User switches to week view

- GIVEN a user is viewing the monthly calendar
- WHEN the user clicks "Week"
- THEN the calendar MUST display the current week with hour-slot columns

#### Scenario: Daily view items show title, time, and status

- GIVEN a day has scheduled publications
- WHEN the user selects that day
- THEN each publication MUST display its title, scheduled time, and status
- AND clicking a publication MUST open its details

#### Scenario: Clicking day in month view focuses date

- GIVEN the user is on `/scheduler/calendar/month?date=2026-06-15`
- WHEN the user clicks a day cell (e.g., June 20)
- THEN the URL becomes `/scheduler/calendar/month?date=2026-06-20`
- AND the calendar centers on June 20 while remaining in month view

#### Scenario: Week view is accessible and shareable

- GIVEN a user shares the URL `/scheduler/calendar/week?date=2026-06-20&channels[]=acc-123`
- WHEN the recipient opens the link
- THEN the week containing June 20 renders
- AND only publications for `acc-123` are shown

#### Scenario: Post detail opens from route-owned postId

- GIVEN the URL is `/scheduler/calendar/week?date=2026-06-20&postId=post-42`
- WHEN scheduler data resolves a visible publication with ID `post-42`
- THEN the post detail modal MUST open for that publication
- AND refresh MUST restore the same open modal state

#### Scenario: Clicking a post card pushes detail state into the URL

- GIVEN the scheduler is rendered without `postId`
- WHEN the user clicks a post card for `post-42`
- THEN the URL MUST gain `postId=post-42` with push semantics
- AND the post detail modal MUST open for `post-42`

#### Scenario: Stale selected post is auto-closed and canonicalized

- GIVEN the URL contains `postId=post-42`
- WHEN date, timezone, status, q, channels, or surface changes and `post-42` no longer resolves in the current scheduler context
- THEN the detail modal MUST close
- AND the URL MUST remove `postId` with replace semantics

## ADDED Requirements

### Requirement: Durable scheduler URL-state guidance

The project MUST maintain durable documentation at `docs/architecture/scheduler-url-state-standard.md` describing the canonical scheduler surfaces, query params, route-owned state model, and push-vs-replace rules. `docs/README.md` MUST index that document for future development guidance.

#### Scenario: Scheduler URL guidance is discoverable

- GIVEN a developer opens `docs/README.md`
- WHEN they review architecture links
- THEN the scheduler URL-state standard MUST be listed
- AND the link MUST point to the durable guidance document
