# Delta for Visual Calendar

## MODIFIED Requirements

### Requirement: Quick-Create from Cell

Clicking an empty calendar cell MUST open CreatePostModal with the clicked date-time prefilled. Submitting while authenticated MUST call the quick-create endpoint. On success, the calendar store MUST replace any optimistic record with the returned backend publication, including its real `publicationId`, normalized `status`, `scheduleMode`, `scheduledFor`, `nextSlotAfter`, and `socialAccountId`. The calendar MUST refresh without a full reload and the created publication MUST be immediately editable by its backend ID.

(Previously: Quick-create refreshed the calendar but did not require reconciliation of identity and normalized server fields.)

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
