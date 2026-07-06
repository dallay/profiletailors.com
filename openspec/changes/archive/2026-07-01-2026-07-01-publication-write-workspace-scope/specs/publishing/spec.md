# Delta for Publishing

## MODIFIED Requirements

### Requirement: Editable and Cancellable Pre-Delivery Publications

The system MUST allow editing, deletion, and cancellation before a publication is claimed for
delivery.

A publication in `DRAFT`, `QUEUED`, or `SCHEDULED` MAY be edited, including text, media references,
schedule mode, and schedule timing, as long as the delivery job has not been claimed for processing.
Such a publication MAY also be cancelled or deleted before claim. Scheduler edit flows MUST persist
through the existing `PATCH /api/publishing/publications/{publicationId}` contract, and successful
responses MUST reflect server truth rather than local-only optimistic state. Publication writes MUST
target exactly one row in the caller's current workspace. A write scoped by `publicationId` MUST
update an existing publication only when both the publication identifier and workspace match the
current workspace context. If no publication row in the current workspace matches the requested
write target, the system MUST either create the draft in the current workspace when the operation is
a create/save flow, or reject the operation as not found for the current workspace when the
operation requires updating an existing publication. The system MUST NOT mutate a publication row
that belongs to another workspace. Once processing has begun, the system MUST prevent unsafe edits
or deletion that would invalidate the claimed delivery attempt.

(Previously: Pre-delivery publications were editable before claim, but the spec did not require
workspace-scoped write targeting or define behavior when an update target is missing in the current
workspace.)

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

#### Scenario: Same-workspace write updates the intended publication row

- GIVEN workspace A already owns publication `P1` in an editable pre-delivery state
- WHEN workspace A saves edits for publication `P1`
- THEN the system MUST update the existing row for workspace A
- AND it MUST NOT create a duplicate row for workspace A

#### Scenario: Save flow creates a draft when no current-workspace row exists

- GIVEN workspace A has no publication row with identifier `P1`
- WHEN workspace A performs a draft save that is allowed to create
- THEN the system MUST persist a new draft in workspace A
- AND the write MUST NOT depend on rows from other workspaces

#### Scenario: Cross-workspace publication rows remain isolated during writes

- GIVEN workspace A owns publication `P1`
- AND workspace B also has a row that is the only existing match for publication `P1` outside
  workspace A's scope
- WHEN workspace A performs a write for publication `P1`
- THEN the system MUST NOT update workspace B's row
- AND any persisted change MUST apply only within workspace A's scope

#### Scenario: Update fails when the current workspace cannot target a row

- GIVEN workspace A requests an update-only write for publication `P1`
- AND workspace A has no matching publication row for `P1`
- WHEN the system evaluates the write target
- THEN the system MUST reject the operation as not found for the current workspace
- AND it MUST leave rows in other workspaces unchanged

#### Scenario: Processing publication cannot be cancelled retroactively

- GIVEN a worker has already claimed a publication job for delivery
- WHEN the user attempts to cancel the publication
- THEN the system MUST reject cancellation for that in-flight attempt
- AND it MUST preserve deterministic processing semantics
