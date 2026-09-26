# Delta for publishing

## ADDED Requirements

### Requirement: Publication responses expose a revision timestamp

Authenticated calendar publication results and publication mutation results MUST expose `updatedAt` as the server's persisted ISO 8601 revision timestamp. The timestamp MUST identify the version used by the app to detect a publication changed after an editor opened it. The existing `publications.updated_at` persistence column is the source; this change MUST NOT add a second revision store.

#### Scenario: Calendar result includes a revision

- GIVEN an authenticated member requests a calendar range containing publication `pub-1`
- AND `pub-1` has a persisted `updated_at` value
- WHEN the calendar response is returned
- THEN the result for `pub-1` MUST include that value as `updatedAt`

#### Scenario: Mutation result includes the new revision

- GIVEN an authenticated member successfully updates publication `pub-1`
- WHEN the mutation response is returned
- THEN the response MUST include the persisted revision for the updated publication
- AND the app MUST be able to replace its local snapshot with the returned result

### Requirement: Publication edits support expected-revision protection

An authenticated publication edit MAY include `expectedUpdatedAt`. When supplied, the server MUST compare it with the current workspace-scoped publication revision before persisting. If the revision differs, the server MUST reject the edit using the existing versioned API error conventions and MUST NOT persist any submitted fields. Requests that omit `expectedUpdatedAt` remain compatible for clients that do not yet participate in optimistic concurrency.

#### Scenario: Edit succeeds with the current revision

- GIVEN an editable publication has revision `2026-09-25T10:00:00Z`
- AND the editor submits `expectedUpdatedAt=2026-09-25T10:00:00Z`
- WHEN the edit is processed in the publication's workspace
- THEN the edit MUST be persisted
- AND the response MUST contain a newer or equal persisted `updatedAt`

#### Scenario: Stale edit is rejected without a partial write

- GIVEN an editor opened publication `pub-1` at revision `R1`
- AND another editor changed `pub-1` so its current revision is `R2`
- WHEN the first editor submits `expectedUpdatedAt=R1`
- THEN the server MUST reject the edit as a revision conflict
- AND none of the first editor's submitted fields MUST be persisted
- AND the response MUST identify a conflict through the repository's structured error contract without exposing secrets

### Requirement: Workspace scope remains authoritative for revision checks

Revision reads and writes MUST use the authenticated principal's resolved workspace context and the existing membership guard. A client-supplied publication or workspace identifier MUST NOT allow a revision check against another workspace.

#### Scenario: Foreign-workspace publication cannot be used for revision comparison

- GIVEN the principal is not an active member of the publication's workspace
- WHEN an edit request supplies a publication ID and expected revision
- THEN the request MUST be rejected by the existing workspace authorization path
- AND no foreign publication metadata MUST be returned

## MODIFIED Requirements

### Requirement: Calendar and publication operations are workspace-scoped

The calendar and publication operation responses MUST retain existing workspace scoping while adding `updatedAt` revision metadata. Browser synchronization is only a trigger to re-fetch this canonical workspace-scoped REST data; it is not a replacement for authorization or server-side filtering.

(Previously: calendar and mutation responses were workspace-scoped but did not expose a client-usable revision.)

#### Scenario: Same workspace revalidation returns only authorized publications

- GIVEN an authenticated member requests a visible range after a browser invalidation
- WHEN the canonical calendar query executes
- THEN the response MUST contain only publications from the resolved active workspace
- AND each returned publication MUST carry its revision timestamp
