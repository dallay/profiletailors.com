# Delta for Visual Calendar

## ADDED Requirements

### Requirement: Scheduler Extraction Preserves Calendar Interaction

Extracting scheduler logic into composables MUST preserve existing calendar navigation, account
filtering, and drag-reschedule behavior.

#### Scenario: Calendar navigation remains functional

- GIVEN a user is on an existing scheduler calendar route
- WHEN the user switches view, navigates dates, or uses Today
- THEN the same calendar surface and URL state MUST be rendered

#### Scenario: Filter contract remains functional

- GIVEN publications for multiple social accounts are visible
- WHEN the user selects and then clears an account filter
- THEN the selected account identifier MUST constrain results
- AND clearing it MUST restore all accounts

#### Scenario: Drag reschedule failure remains safe

- GIVEN a user drags a publication to another slot
- WHEN the reschedule request fails
- THEN the publication MUST revert to its original slot
- AND a safe localized error MUST be displayed
