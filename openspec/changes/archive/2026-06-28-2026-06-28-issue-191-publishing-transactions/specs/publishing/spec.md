# Delta for Publishing

## ADDED Requirements

### Requirement: Atomic Publication and Job Mutations

Create, Edit, Cancel, Retry, and Reschedule MUST execute their publication mutation (including asset-link changes) and corresponding job mutation through one `AtomicTransactionRunner` boundary. Both sides MUST commit together or both MUST roll back.

#### Scenario: Paired mutation commits

- GIVEN any of the five workflows has valid inputs
- WHEN both publication and job mutations succeed
- THEN the persisted publication and asset links MUST reflect the result
- AND the corresponding job mutation MUST be committed

#### Scenario: Create job mutation fails

- GIVEN no publication, asset links, or job exists for a Create request
- WHEN publication persistence succeeds but job persistence fails
- THEN the publication, its asset links, and its job MUST NOT exist

#### Scenario: Existing workflow job mutation fails

- GIVEN Edit, Cancel, Retry, or Reschedule targets a persisted publication with its current asset links and job
- WHEN the publication mutation succeeds but the job mutation fails
- THEN the prior publication state and asset links MUST remain unchanged
- AND any pre-existing job MUST remain unchanged

### Requirement: Framework-Neutral Transaction Orchestration

The five handlers MUST depend on `AtomicTransactionRunner` and MUST NOT depend on Spring, Reactor, or coroutine-Reactor transaction APIs. Authorization, lifecycle and capability validation, external reads, and media resolution MUST complete before the transaction starts; only paired persistence mutations SHALL run inside it.

#### Scenario: Validation fails before transaction

- GIVEN a mutation request fails authorization, lifecycle, or capability validation
- WHEN the handler processes the request
- THEN `AtomicTransactionRunner` MUST NOT be invoked
- AND no publication or job write MUST occur

#### Scenario: External read fails before transaction

- GIVEN a required account, publication, asset, or media-resolution read fails
- WHEN the handler prepares the mutation
- THEN no transaction MUST begin
- AND no durable mutation MUST occur

### Requirement: Jobs Use Persisted Publication Result

Edit, Retry, and Reschedule MUST derive replacement jobs from the publication returned by persistence, not from the pre-persistence draft.

#### Scenario: Persistence normalizes publication data

- GIVEN persistence returns publication identity, workspace, status, or schedule data different from the prepared draft
- WHEN Edit, Retry, or Reschedule creates its replacement job
- THEN the job MUST use the persisted publication result
- AND no stale pre-persistence value MAY determine the job

### Requirement: Delete Behavior Is Unchanged

Delete MUST retain its existing behavior and MUST NOT acquire `AtomicTransactionRunner` wiring as part of this change.

#### Scenario: Delete executes existing path

- GIVEN a publication is eligible for deletion
- WHEN Delete is invoked
- THEN its existing persistence behavior MUST remain unchanged
- AND it MUST NOT invoke `AtomicTransactionRunner`
