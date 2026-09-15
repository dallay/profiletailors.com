# Waitlist Specification

## Purpose

Admin waitlist back-office slice (`apps/web/admin`, route `/waitlist`): paged list with search/filters, entry detail with consent/metadata display, and lifecycle actions (invite/cancel/resend). Mirrors the invitations slice pattern with optimistic lock on mutations and status-count summary card.

## Requirements

### Requirement: Cancel Action Uses Optimistic Lock

The cancel action MUST send `expectedVersion` with the request so the backend rejects concurrent last-write-wins with HTTP 409.

#### Scenario: Successful cancel with matching version

- GIVEN an operator viewing a waitlist entry with status `PENDING`
- WHEN the operator enters a cancel reason and confirms cancel
- AND the current `version` matches the server state
- THEN the backend MUST accept the cancel and return updated entry
- AND the UI MUST reflect the new status without reload

#### Scenario: Concurrent edit returns 409

- GIVEN an operator viewing a waitlist entry with version `N`
- WHEN another operator cancels the same entry first
- AND the first operator then confirms cancel with stale version `N`
- THEN the backend MUST return HTTP 409 Conflict
- AND the UI MUST show a conflict error with current data

#### Scenario: Cancel reason is required

- GIVEN an operator on the cancel dialog
- WHEN the operator attempts to confirm without entering a reason
- THEN the confirm button MUST be disabled

### Requirement: Resend Invitation Action

The entry detail view MUST provide a resend action that re-triggers invitation email delivery without revoking the existing invitation.

#### Scenario: Resend succeeds

- GIVEN an operator viewing a waitlist entry with status `INVITED`
- WHEN the operator clicks resend
- THEN the system MUST call `POST .../resend`
- AND the backend MUST return 200 OK
- AND the UI MUST show a success confirmation

#### Scenario: Resend unavailable for non-invited entries

- GIVEN an operator viewing a waitlist entry with status `PENDING`
- WHEN the operator looks at the action buttons
- THEN the resend button MUST NOT be visible

#### Scenario: Resend handles server error

- GIVEN an operator viewing a waitlist entry with status `INVITED`
- WHEN the operator clicks resend and the backend returns 500
- THEN the UI MUST show an error message
- AND the entry state MUST remain unchanged

### Requirement: Consent Fields Displayed in Entry Detail

The entry detail view MUST render `earlyAccessConsent`, `marketingConsent`, and `consentVersion` fields when present.

#### Scenario: Consent fields render when present

- GIVEN an operator viewing a waitlist entry
- WHEN the entry has consent data
- THEN `earlyAccessConsent`, `marketingConsent`, and `consentVersion` MUST render in a dedicated consent section

#### Scenario: Consent fields hidden when absent

- GIVEN an operator viewing a waitlist entry
- WHEN the entry has no consent data
- THEN the consent section MUST be hidden

### Requirement: Status Count Summary Card

The list view MUST display a summary card showing entry counts grouped by status (`PENDING`, `INVITED`, `CONVERTED`, `CANCELLED`).

#### Scenario: Summary card shows accurate counts

- GIVEN a workspace with waitlist entries across statuses
- WHEN the operator opens the waitlist list view
- THEN a card MUST display each status with its count from `countByStatus`

#### Scenario: Summary card updates after actions

- GIVEN an operator viewing the waitlist list with summary card
- WHEN the operator cancels an entry from detail view
- AND returns to the list
- THEN the summary card counts MUST reflect the new state

### Requirement: Date-Range Filters

The list view MUST support `joinedFrom`, `joinedTo`, `invitedFrom`, and `invitedTo` query parameters.

#### Scenario: Filter by join date range

- GIVEN an operator on the waitlist list view
- WHEN the operator sets `joinedFrom` and `joinedTo` dates
- THEN only entries with `joinedAt` within the range MUST appear

#### Scenario: Filter by invite date range

- GIVEN an operator on the waitlist list view
- WHEN the operator sets `invitedFrom` and `invitedTo` dates
- THEN only entries with invitation dates within the range MUST appear

#### Scenario: Combined date and status filters

- GIVEN an operator on the waitlist list view
- WHEN the operator applies both date range and status filter
- THEN only entries matching both criteria MUST appear

### Requirement: WaitlistKey Filter

The list view MUST support filtering by `waitlistKey` (or `waitlistId`) to narrow results by waitlist source.

#### Scenario: Filter by waitlistKey

- GIVEN a workspace with entries from multiple waitlist sources
- WHEN the operator enters a `waitlistKey` value
- THEN only entries with matching `waitlistKey` MUST appear

#### Scenario: WaitlistKey input is a text field

- GIVEN an operator on the waitlist list view
- WHEN the operator looks at the filter controls
- THEN `waitlistKey` MUST be a text input field

### Requirement: Metadata Summary Displayed in Entry Detail

The entry detail view MUST display `metadataSummary` when the backend surfaces it.

#### Scenario: Metadata renders when present

- GIVEN an operator viewing a waitlist entry
- WHEN the entry has `metadataSummary`
- THEN it MUST render in a dedicated metadata section

#### Scenario: Metadata hidden when absent

- GIVEN an operator viewing a waitlist entry
- WHEN the entry has no `metadataSummary`
- THEN the metadata section MUST be hidden

### Requirement: Email Search and Status Filter

The list view MUST support email substring search and status dropdown filter.

#### Scenario: Email search narrows results

- GIVEN a workspace with many waitlist entries
- WHEN the operator types an email fragment in search
- THEN only entries with matching email substrings MUST appear

#### Scenario: Status filter shows subset

- GIVEN a workspace with mixed-status entries
- WHEN the operator selects a status from the dropdown
- THEN only entries with that status MUST appear

#### Scenario: Empty search returns full page

- GIVEN an operator on the waitlist list view
- WHEN the operator clears all filters
- THEN a full page of entries MUST appear sorted by `joinedAt` desc

### Requirement: Entry Detail Navigation

The list view row click MUST navigate to the entry detail view with full entry data.

#### Scenario: Click row opens detail

- GIVEN an operator on the waitlist list view
- WHEN the operator clicks a row
- THEN the system MUST navigate to entry detail view
- AND display all entry fields including consent and metadata

### Requirement: Vitest Spec Coverage

The `WaitlistView.vue` and `WaitlistEntryView.vue` components MUST have Vitest specs covering core user interactions.

#### Scenario: WaitlistView renders correctly

- GIVEN the WaitlistView component
- WHEN rendered with mock API returning entries
- THEN the component MUST render the summary card, list, and filters
- AND respond to filter changes without errors

#### Scenario: WaitlistView handles loading and error states

- GIVEN the WaitlistView component
- WHEN the API returns loading state
- THEN a loading indicator MUST display
- WHEN the API returns error state
- THEN an error message MUST display

#### Scenario: WaitlistEntryView renders entry details

- GIVEN the WaitlistEntryView component
- WHEN rendered with a mock entry object
- THEN consent fields MUST appear when present
- AND action buttons MUST reflect entry status

#### Scenario: WaitlistEntryView cancel action flow

- GIVEN the WaitlistEntryView component
- WHEN the operator initiates cancel
- THEN the reason dialog MUST appear
- AND on confirm with reason, the API MUST be called with expectedVersion

#### Scenario: WaitlistEntryView resend action

- GIVEN the WaitlistEntryView component with an `INVITED` entry
- WHEN the operator clicks resend
- THEN the resend API MUST be called
- AND success/error feedback MUST display

## Acceptance Mapping

| # | Criterion | Requirement | Scenario |
|---|-----------|-------------|----------|
| G1 | Cancel sends `expectedVersion` | Cancel Action Uses Optimistic Lock | Successful cancel, 409 on mismatch, reason required |
| G2 | Resend action available | Resend Invitation Action | Success, unavailable for non-invited, error handling |
| G3 | Consent fields in detail | Consent Fields Displayed in Entry Detail | Present, absent |
| G4 | Summary card shows counts | Status Count Summary Card | Accurate counts, updates after actions |
| G5 | Vitest specs exist | Vitest Spec Coverage | Render, loading/error, cancel flow, resend |
| G6 | `waitlistKey` filter | WaitlistKey Filter | Text filter narrows results |
| G7 | Date-range filters | Date-Range Filters | Join range, invite range, combined |
| G8 | Metadata summary display | Metadata Summary Displayed in Entry Detail | Present, absent |
