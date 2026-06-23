# Delta for Publishing

## ADDED Requirements

### Requirement: Unpublished Publication Deletion API

The system MUST expose `DELETE /api/publishing/publications/{publicationId}` for unpublished publications. The endpoint MUST permanently remove the publication and any unclaimed scheduling/job linkage when the publication status is `DRAFT`, `QUEUED`, or `SCHEDULED`. The endpoint MUST reject deletion for any other status and MUST NOT report local-only success.

#### Scenario: Delete scheduled publication succeeds

- GIVEN a publication exists in status `SCHEDULED`
- WHEN an authorized workspace member calls `DELETE /api/publishing/publications/{publicationId}`
- THEN the system MUST remove the publication from authoritative persistence
- AND any unclaimed related job state MUST no longer be returned by scheduler queries

#### Scenario: Delete published publication is rejected

- GIVEN a publication exists in status `PUBLISHED`
- WHEN an authorized workspace member calls the delete endpoint
- THEN the system MUST reject the request as not allowed for that status
- AND the publication MUST remain unchanged

### Requirement: Publication Edit/Delete Status Matrix

The system MUST use one pre-delivery policy for scheduler actions across frontend and backend.

| Status | Edit | Delete |
|--------|------|--------|
| `DRAFT` | MUST allow | MUST allow |
| `QUEUED` | MUST allow | MUST allow |
| `SCHEDULED` | MUST allow | MUST allow |
| `PROCESSING` | MUST reject | MUST reject |
| `PUBLISHED` | MUST reject | MUST reject |
| `BLOCKED` | MUST reject | MUST reject |
| `FAILED` | MUST reject | MUST reject |
| `CANCELLED` | MUST reject | MUST reject |

The frontend MUST hide or disable edit/delete actions for disallowed statuses, and the backend MUST still enforce the same policy if a request is sent.

#### Scenario: Allowed status exposes action

- GIVEN the scheduler shows a publication in status `QUEUED`
- WHEN the user opens publication actions
- THEN edit and delete actions MUST be available
- AND invoking them MUST use backend APIs as the source of truth

#### Scenario: Disallowed status stays server-enforced

- GIVEN a publication is in status `PROCESSING`
- WHEN a client sends edit or delete anyway
- THEN the backend MUST reject the request
- AND the scheduler MUST preserve current server state after refresh or rollback

## MODIFIED Requirements

### Requirement: Editable and Cancellable Pre-Delivery Publications

The system MUST allow editing, deletion, and cancellation before a publication is claimed for delivery.

A publication in `DRAFT`, `QUEUED`, or `SCHEDULED` MAY be edited, including text, media references, schedule mode, and schedule timing, as long as the delivery job has not been claimed for processing. Such a publication MAY also be cancelled or deleted before claim. Scheduler edit flows MUST persist through the existing `PATCH /api/publishing/publications/{publicationId}` contract, and successful responses MUST reflect server truth rather than local-only optimistic state. Once processing has begun, the system MUST prevent unsafe edits or deletion that would invalidate the claimed delivery attempt.

(Previously: Pre-delivery publications could be edited or cancelled, but delete behavior and backend-backed scheduler editing were not specified.)

#### Scenario: Queued publication is edited before claim

- GIVEN a publication is queued and not yet claimed by a worker
- WHEN the user edits the text or scheduling data
- THEN the system MUST persist the new publication content and delivery metadata
- AND the previous unclaimed job representation MUST no longer be treated as authoritative

#### Scenario: Scheduled publication edit uses backend response

- GIVEN the scheduler shows a publication in status `SCHEDULED`
- WHEN the user saves changes from the edit flow
- THEN the client MUST update its state from the successful PATCH response
- AND failed PATCH requests MUST surface an error without pretending the edit succeeded

#### Scenario: Processing publication cannot be cancelled retroactively

- GIVEN a worker has already claimed a publication job for delivery
- WHEN the user attempts to cancel the publication
- THEN the system MUST reject cancellation for that in-flight attempt
- AND it MUST preserve deterministic processing semantics
