# Delta for Publishing

## ADDED Requirements

### Requirement: Calendar Query Endpoint

The system MUST expose a `GET /api/publishing/publications/calendar` endpoint returning publications
filtered by date range, status, channel, and timezone.

The endpoint MUST accept `from` and `to` (ISO-8601 Instant, required), `status` (comma-separated,
optional), `socialAccountId` (optional), and `timezone` (IANA, optional, defaults to UTC). The
response MUST include `publications[]` with conflict flags, `activity[]` with per-day counts and
density levels, and `conflicts[]` with overlapping publication pairs.

#### Scenario: Calendar query returns filtered publications

- GIVEN a workspace has publications across multiple dates and statuses
- WHEN a GET request is made with
  `from=2026-06-01T00:00:00Z&to=2026-06-30T00:00:00Z&status=SCHEDULED,QUEUED&socialAccountId=acc_li_1`
- THEN the response MUST include only SCHEDULED and QUEUED publications for the LinkedIn account
  within June 2026
- AND the response MUST include `activity` entries grouped by date

#### Scenario: Empty range returns empty result set

- GIVEN a workspace has no publications in the requested range
- WHEN a GET request is made with a date range that has no publications
- THEN the response MUST return 200 with empty `publications[]`, `activity[]`, and `conflicts[]`

### Requirement: Activity Density Aggregation

The system MUST aggregate publication counts per day using the user's timezone for activity
indicators.

The aggregation MUST group publications by calendar date in the requested IANA timezone and classify
each day into density levels: 0 = `none`, 1–2 = `light`, 3–5 = `medium`, 6+ = `high`. Thresholds
MUST be defined as constants in `ActivityThresholds`.

#### Scenario: Activity aggregation respects timezone boundary

- GIVEN publications scheduled at 2026-06-09T23:00:00Z and 2026-06-10T01:00:00Z
- WHEN `timezone=America/New_York` (UTC-4)
- THEN both publications MUST be counted on 2026-06-09 in the New York timezone

### Requirement: Conflict Detection Policy

The system MUST detect conflicting publications when two SCHEDULED or QUEUED publications for the
same social account fall within a configurable conflict window (default 15 minutes).

The `ConflictDetectionPolicy` MUST group publications by `socialAccountId`, sort by `scheduledFor`,
and flag adjacent pairs where the gap is less than the conflict window. DRAFT, FAILED, CANCELLED,
and PUBLISHED statuses MUST be excluded from detection.

#### Scenario: Adjacent same-account publications within window are flagged

- GIVEN two SCHEDULED publications for account `acc_li_1` at 10:00 and 10:10
- WHEN the conflict detection policy runs with a 15-minute window
- THEN both publications MUST be flagged with `hasConflict: true`
- AND the conflict entry MUST list both publication IDs with reason `OVERLAPPING_SCHEDULE`

#### Scenario: Publications across different accounts do not conflict

- GIVEN two SCHEDULED publications at the same time for different social accounts
- WHEN the conflict detection policy runs
- THEN neither publication MUST be flagged as conflicting

### Requirement: Quick-Create Endpoint

The system MUST expose `POST /api/publishing/publications/quick-create` that maps to
`CreatePublicationCommand` with `scheduleMode = SCHEDULED_AT` and empty assets.

The endpoint MUST accept `socialAccountId`, `title`, `bodyText`, `scheduledFor`, and `priority`. The
response MUST return the existing `PublicationResult`.

#### Scenario: Quick-create creates a scheduled publication

- GIVEN a valid workspace and social account
- WHEN a POST request submits `socialAccountId`, `bodyText`, and `scheduledFor`
- THEN a publication MUST be created with `scheduleMode = SCHEDULED_AT` and `status = SCHEDULED`
- AND the response MUST contain the new publication ID and created publication data

### Requirement: PATCH Reschedule Endpoint

The system MUST expose `PATCH /api/publishing/publications/{id}/reschedule` alongside the existing
`POST` reschedule route for drag-and-drop updates.

The endpoint MUST accept `scheduleMode`, `scheduledFor`, and `priority`. Only SCHEDULED and QUEUED
publications MUST be reschedulable. The response MUST return the existing `PublicationResult`.

#### Scenario: Drag-drop reschedule updates publication time

- GIVEN a SCHEDULED publication with `scheduledFor` at Monday 10:00
- WHEN a PATCH request submits
  `{"scheduleMode": "SCHEDULED_AT", "scheduledFor": "2026-06-09T14:00:00Z"}`
- THEN the publication's `scheduledFor` MUST be updated to 14:00
- AND the response MUST reflect the new schedule
