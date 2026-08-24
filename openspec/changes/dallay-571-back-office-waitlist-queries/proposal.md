# Proposal: Back Office Waitlist Queries, Filtering, and Search (DALLAY-571)

## Overview

Provide Back Office waitlist query capabilities for operational use. Administrators need list,
search by email, filter by status, sort by creation date, and pagination over waitlist entries
without database access, exposing enough invitation/conversion state to support invite-only
onboarding workflows.

## Changes

### Intent

Waitlist remains independent from identity and invitation. Query results reflect
invitation/conversion linkage without collapsing concepts. Read access requires explicit
administrative authorization; only operationally necessary fields are exposed. Read queries are
not individually audited as mutations.

### Scope

#### In Scope

- List waitlist entries with pagination.
- Search waitlist entries by email.
- Filter by status.
- Sort by creation date or equivalent operational default.
- Expose invitation/conversion references needed for admin workflows.
- Aggregate-level observability of query performance and filter usage.

#### Out of Scope

- Bulk invitation commands (DALLAY-569).
- Full admin UI.
- Tags, scoring, notes, or campaign attribution.

### Capabilities

#### New Capabilities

- `platform-admin-waitlist-queries`: operational read access over waitlist entries.

#### Modified Capabilities

- `lead-capture-waitlist`: admin query projections read existing waitlist entry and invitation
  state without mutating the aggregate.

### Approach

- Explicit admin query endpoints under `/api/admin/waitlist-entries` for list and detail access.
- Query semantics support pagination (page/size bounded), safe filtering (status, email,
  date ranges), and deterministic sorting via an allow-list.
- The list query joins waitlist entries to waitlists and selects operationally necessary fields
  only; the detail query exposes invitation history as a separate concept.
- Read authorization requires `WAITLIST_READ` permission via an active platform role assignment.
- Observability records query count with low-cardinality filter tags (status filter present,
  email search used); HTTP-level performance is covered by existing Actuator meters.

### Affected Areas

| Area                                                                                                 | Impact   | Description                             |
|------------------------------------------------------------------------------------------------------|----------|-----------------------------------------|
| `server/smp/src/main/kotlin/.../platformadmin/infrastructure/http/AdminWaitlistController.kt`        | Modified | List/detail endpoints, telemetry wiring |
| `server/smp/src/main/kotlin/.../platformadmin/application/ports/`                                    | Modified | Query and telemetry ports               |
| `server/smp/src/main/kotlin/.../platformadmin/infrastructure/persistence/R2dbcAdminWaitlistQuery.kt` | Modified | R2DBC list/detail/count projection      |
| `server/smp/src/main/kotlin/.../platformadmin/infrastructure/observability/`                         | New      | Micrometer telemetry adapter            |
| `server/smp/src/test/`                                                                               | Modified | Unit, integration, and BDD coverage     |

## Usage

### Risks

| Risk                                                   | Likelihood | Mitigation                                                                      |
|--------------------------------------------------------|------------|---------------------------------------------------------------------------------|
| Over-exposing PII in list responses                    | Low        | List returns operationally necessary fields only; detail is a separate endpoint |
| Filter cardinality explosion in metrics                | Low        | Telemetry uses low-cardinality boolean/enum tags, not raw values                |
| Confusing waitlist entry status with invitation status | Medium     | Detail returns invitation history as a separate concept                         |

### Dependencies

- DALLAY-436, DALLAY-438, DALLAY-439 (existing waitlist capture and persistence).
- Existing platform-admin bounded context and `WAITLIST_READ` permission.

### Success Criteria

- [ ] Administrators can list waitlist entries with pagination.
- [ ] Administrators can search waitlist entries by email.
- [ ] Administrators can filter entries by waitlist status.
- [ ] Query results expose enough state to support invitation workflows.
- [ ] BDD scenarios for list, search, and filter pass.
- [ ] Query performance and filter usage are measurable at aggregate level.

## Troubleshooting

### Rollback Plan

Revert the controller, query, and telemetry changes. The waitlist aggregate and join endpoint are
unaffected; admin operators fall back to prior read paths.

## References

- DALLAY-571, DALLAY-560, DALLAY-569.
- RFC sections 17, 18, 19, 20, 34, 45.
