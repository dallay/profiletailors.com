# Visual Calendar Specification

## Purpose

Define visual content calendar UI for planning and managing publications across daily/weekly/monthly
views with activity indicators, quick-create, drag-drop reschedule, conflict warnings, and filter
controls.

## Requirements

### Requirement: Multi-View Calendar

The system MUST provide day, week, and month views. Week and month views are addressable via
`/scheduler/calendar/week` and `/scheduler/calendar/month`. Day view is NOT a top-level route;
clicking a day in month/week focuses `date=YYYY-MM-DD` within the current week/month context without
a separate route. Date arrows and "Today" button MUST navigate the calendar.

The daily view MUST show publications for the selected day with title, time, and status — each
clickable for details.

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

### Requirement: Activity Indicators

The month view MUST show per-day activity density using these thresholds: 0 = none (no dot), 1–2 =
low (yellow/small), 3–5 = medium (orange/medium), 6+ = high (green/large with "+").

#### Scenario: Cells show correct density levels

- GIVEN a month has days with 0, 2, 4, and 7 publications
- WHEN the month view renders
- THEN cells with 0 show no dot, 2 a yellow dot, 4 an orange dot, and 7 a green dot with "+"

### Requirement: Quick-Create from Cell

Clicking an empty calendar cell MUST open CreatePostModal with the clicked date-time prefilled.
Submitting while authenticated MUST call the quick-create endpoint. On success, the calendar store
MUST replace any optimistic record with the returned backend publication, including its real
`publicationId`, normalized `status`, `scheduleMode`, `scheduledFor`, `nextSlotAfter`, and
`socialAccountId`. The calendar MUST refresh without a full reload and the created publication MUST
be immediately editable by its backend ID.

(Previously: Quick-create refreshed the calendar but did not require reconciliation of identity and
normalized server fields.)

#### Scenario: Click empty slot creates scheduled post

- GIVEN the weekly calendar shows Wednesday
- WHEN the user clicks an empty slot at 14:00
- THEN CreatePostModal MUST open with `scheduledFor` set to Wednesday 14:00
- AND submitting MUST create a SCHEDULED publication visible in the calendar

#### Scenario: Authenticated quick-create adopts backend identity

- GIVEN authenticated quick-create has an optimistic local record
- WHEN the endpoint returns a successful `PublicationResult`
- THEN the calendar store MUST use the returned publication ID and normalized fields
- AND MUST NOT retain a synthetic local ID or stale schedule fields

#### Scenario: Quick-created publication can be reopened and edited

- GIVEN quick-create returned and reconciled a publication
- WHEN the user reopens it and saves an edit
- THEN PATCH MUST target its real backend publication ID
- AND the calendar MUST display the successful PATCH response

### Requirement: Drag-and-Drop Reschedule

Dragging a publication to a new slot MUST optimistically update the UI and fire PATCH reschedule. On
success the position is kept. On failure the publication MUST revert and an error toast MUST show.

#### Scenario: Drag reschedule persists immediately

- GIVEN a publication at Monday 10:00
- WHEN the user drags it to Monday 14:00 and drops
- THEN the publication immediately shows at 14:00
- AND a confirmation toast appears

#### Scenario: Failed reschedule reverts

- GIVEN a drag-drop operation
- WHEN the PATCH request fails
- THEN the publication reverts to its original time slot
- AND an error toast displays

### Requirement: Conflict Warnings

The system MUST warn when two SCHEDULED/QUEUED publications for the same social account overlap
within the conflict window. The conflict badge MUST show on affected publications. The conflict view
SHOULD suggest the next available slot (tracked as follow-up). The user MAY confirm and keep the
overlap.

#### Scenario: Overlapping publications show conflict

- GIVEN two publications for the same LinkedIn account at 10:00 and 10:10
- WHEN the calendar loads
- THEN both show a conflict badge
- AND the user can confirm despite the conflict

### Requirement: Platform Filter

A filter dropdown MUST let users select a social account. The selection MUST propagate as
`socialAccountId` to the API. Clearing the filter MUST return all accounts.

#### Scenario: Filter by LinkedIn clears back

- GIVEN the calendar shows publications for multiple accounts
- WHEN the user selects "LinkedIn"
- THEN only LinkedIn publications appear
- WHEN the user clears the filter
- THEN all publications reappear

### Requirement: Imported Page Posts Are Calendar Read Models

The visual calendar backend MAY surface imported Company Page posts through `GET /api/publishing/social-content/calendar`, using the existing workspace, version, range, lifecycle, actor, cursor, and limit contract. Imported items MUST remain distinguishable from Profile Tailors publications by provider, actor, origin, and lifecycle. Calendar responses MUST preserve `nextCursor` and MUST NOT expose credentials.

#### Scenario: Calendar displays an imported Page post

- GIVEN an active workspace contains an imported LinkedIn Company Page post
- WHEN the member requests a valid calendar range with required authentication and workspace headers
- THEN the response MUST include the post when it falls in range
- AND the item MUST identify its Page actor and imported origin

### Requirement: Imported Page Posts Cannot Enter Publication Writes

Imported Company Page posts MUST be informational calendar/detail records only. They MUST NOT be draggable, rescheduled, edited, cancelled, deleted, quick-created from, or submitted to personal-profile publishing endpoints. Existing personal-profile calendar and publication behavior MUST remain unchanged.

#### Scenario: Read-only Page item is not reschedulable

- GIVEN the calendar contains an imported Page post with `mutationAllowed = false`
- WHEN a client attempts to reschedule or edit it using a publication write contract
- THEN the system MUST reject the operation
- AND the imported post MUST remain unchanged

#### Scenario: Personal publication remains separate

- GIVEN the same calendar range contains a personal-profile publication and an imported Page post
- WHEN the calendar is requested
- THEN both records MUST retain distinct account/actor identity and origin
- AND only the personal publication MAY participate in publication lifecycle operations
