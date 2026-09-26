# Delta for visual-calendar

## MODIFIED Requirements

### Requirement: Calendar data stays current across browser lifecycle events

The scheduler MUST keep its visible calendar data current without requiring manual route navigation. While the document is visible, the scheduler SHOULD use one shared reactive clock/ticker for time-bound refresh decisions. When the document becomes visible after being hidden, the scheduler MUST revalidate the current visible range once. The scheduler MUST NOT run a continuous calendar ticker while the document is hidden.

A publication or relevant channel mutation MAY emit a browser invalidation signal. The signal is advisory only: the receiving tab MUST fetch canonical REST calendar data for its own active workspace and visible range rather than applying publication data from the message.

(Previously: the calendar was refreshed by route/view changes and explicit local update handlers only.)

#### Scenario: Visible scheduler revalidates after a same-workspace invalidation

- GIVEN an authenticated user is viewing the scheduler for workspace `workspace-a`
- AND the scheduler document is visible
- AND another authenticated calendar surface emits a valid invalidation for `workspace-a`
- WHEN the scheduler receives the invalidation
- THEN it MUST request the current visible calendar range from the canonical calendar API
- AND it MUST NOT apply publication data carried in the browser message

#### Scenario: Invalidation burst is coalesced

- GIVEN a visible scheduler receives several valid invalidations for its active workspace before revalidation starts
- WHEN the invalidations are processed
- THEN the scheduler MUST perform at most one calendar revalidation for that burst
- AND overlapping response handling MUST retain the newest `fetchCalendar()` result

#### Scenario: Foreign workspace invalidation is ignored

- GIVEN a scheduler is active for `workspace-a`
- WHEN it receives a well-formed invalidation for `workspace-b`
- THEN it MUST ignore the message
- AND it MUST NOT request or display workspace `b` calendar data

#### Scenario: Malformed browser message is ignored

- GIVEN a scheduler is active
- WHEN it receives an unsupported message type, missing workspace identity, or invalid field shape
- THEN it MUST ignore the message without throwing
- AND the normal URL-driven calendar behavior MUST remain available

#### Scenario: BroadcastChannel is unavailable

- GIVEN the browser does not provide `BroadcastChannel`
- WHEN the scheduler mounts
- THEN it MUST continue to load and refresh through the canonical REST path
- AND no browser-channel error MUST block the scheduler

#### Scenario: Hidden scheduler refreshes once on return

- GIVEN the scheduler is hidden while a publication changes in the active workspace
- WHEN the document becomes visible again
- THEN the scheduler MUST request the current visible range once
- AND it MUST not have run a continuous calendar ticker while hidden

### Requirement: Visible calendar range is derived consistently

The scheduler MUST use one range calculation for initial loading, URL changes, visibility revalidation, invalidation revalidation, and post-mutation refreshes. The calculation MUST interpret the URL date as a calendar date in the selected timezone, use the existing `@internationalized/date` primitives, and produce an ISO instant range whose upper bound is exclusive for the backend query.

#### Scenario: Week range includes the final visible day

- GIVEN the selected date is a Sunday in the configured timezone
- WHEN the scheduler calculates a week range
- THEN the range MUST start at the Sunday calendar day
- AND the exclusive end MUST be the following Sunday
- AND a publication on the Saturday calendar day MUST be included

#### Scenario: Month range includes the final month day

- GIVEN the selected date is in a month with 30 or 31 days
- WHEN the scheduler calculates a month range
- THEN the range MUST start at the first calendar day of that month
- AND the exclusive end MUST be the first calendar day of the following month
- AND a publication on the last day of the selected month MUST be included

#### Scenario: Timezone and DST do not shift the calendar date

- GIVEN a scheduler timezone with a daylight-saving transition or a positive UTC offset
- WHEN it calculates the visible range and maps returned publications to calendar dates
- THEN the local calendar day boundaries MUST remain aligned with the selected timezone
- AND no publication MUST move to an adjacent day solely because of `toISOString()` conversion

### Requirement: Calendar mutation invalidation is minimal

Browser invalidation messages MUST contain only a versioned event kind, the active workspace identity, an invalidation category, and safe identifiers needed to decide whether to revalidate. They MUST NOT contain publication body text, title, media URLs, access tokens, or a complete publication object.

#### Scenario: Local authenticated mutation emits a minimal invalidation

- GIVEN an authenticated user creates, edits, reschedules, cancels, or deletes a publication in `workspace-a`
- WHEN the mutation succeeds
- THEN the app MAY emit one calendar invalidation for `workspace-a`
- AND the message MUST contain no publication content or credentials

#### Scenario: Cross-tab message causes canonical refresh only

- GIVEN two tabs are authenticated in the same workspace
- WHEN tab A completes a calendar mutation and tab B receives the invalidation
- THEN tab B MUST re-fetch its own visible range
- AND tab B MUST render the REST response rather than trust message payload data
