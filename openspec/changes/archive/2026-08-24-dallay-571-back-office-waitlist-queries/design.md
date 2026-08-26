# Design: Back Office Waitlist Queries (DALLAY-571)

## Technical Approach

Expose operational read access over waitlist entries through dedicated admin query endpoints
under `/api/admin/waitlist-entries`. The change keeps the waitlist aggregate and invitation
concept independent — list/detail endpoints return read-only projections; mutations (invite,
cancel) remain on the same controller and flow through their existing CQRS handlers.

Query semantics:

- Zero-based `page`, bounded `size` (rejected with `400` when above `ADMIN_PAGE_MAX_SIZE`).
- Filters: `status` (pass-through), `email` (case-insensitive against the normalized column),
  `waitlistId`, `waitlistKey`, `joinedFrom`/`joinedTo`, `invitedFrom`/`invitedTo`.
- Sort allow-list: `joinedAt`, `invitedAt`, `email`, `status`. Default `joinedAt desc`.
- Detail returns entry state plus a separate invitation history list; never collapses
  invitation status into waitlist entry status.

Authorization: `PlatformPermission.WAITLIST_READ` via an active platform role assignment;
unauthenticated → `401`, missing permission → `403` (`PlatformAccessDeniedException`).

Observability: aggregate `platform.admin.waitlist.queries` Micrometer counter with
low-cardinality boolean tags `status.filter` and `email.search`. No individual audit emission
for reads — the rule is "read queries are not individually audited as mutations".

## Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Endpoint surface | `/api/admin/waitlist-entries` GET list/detail + invite/cancel mutations | Keeps query and mutation paths under a single resource so authorization, telemetry wiring, and DTOs stay cohesive |
| Query port | `AdminWaitlistQuery` (application port) + `R2dbcAdminWaitlistQuery` (infrastructure) | Preserves hexagonal layer direction `domain ← application ← infrastructure` |
| Telemetry port | `WaitlistQueryTelemetryPort` (framework-free) + `WaitlistQueryObservabilityAdapter` (Micrometer) | Application layer stays free of Micrometer/Spring stereotypes; the same pattern as other infrastructure adapters |
| Telemetry tagging | Low-cardinality booleans | Avoids metric cardinality explosion while still enabling aggregate-level filter-usage analysis |
| Authorization granularity | Single `WAITLIST_READ` permission on the role assignment | Matches the existing `PlatformPermission` model and the proposal's "Read authorization requires explicit administrative authorization" rule |
| BDD coverage | `@platform-admin @fast @postgres` feature; new scenarios for email search and status filter | Satisfies the SDD rule "every new user-visible backend feature must include BDD scenarios" |
| Spec strategy | Delta-only; no canonical `openspec/specs/platform-admin/spec.md` | The platform-admin query surface is an internal capability of the `platformadmin` bounded context; waitlist invariants remain in `openspec/specs/lead-capture-waitlist/spec.md` and need no merge |

## Data Flow

```text
GET /api/admin/waitlist-entries?status=&email=&page=&size=&sort=
    │
    ▼
AdminWaitlistController.listEntries
    │
    ├── requestContextStore.currentPrincipalContext() → resolveOperator()
    ├── roleAssignmentRepository.findActiveByPrincipalId(operatorId)
    ├── WAITLIST_READ ∈ effectivePermissions()?  ── no ──▶ 403 PlatformAccessDeniedException
    ├── size > ADMIN_PAGE_MAX_SIZE?             ── yes ─▶ 400
    │
    ▼
ListAdminWaitlistEntriesQuery
    │
    ▼
AdminWaitlistQuery.list(query)  ──▶  R2dbcAdminWaitlistQuery.list
    │                              (joins waitlist_entries to waitlists, selects only
    │                               operationally necessary fields, applies status/email/
    │                               date filters, paginates with bounded sort)
    ▼
WaitlistQueryTelemetryPort.recordListQuery(statusFilterApplied, emailSearch)
    │
    ▼
200 OK PagedResult<AdminWaitlistEntrySummary>
```

## Affected Areas

| Area | Impact |
|---|---|
| `server/smp/.../platformadmin/infrastructure/http/AdminWaitlistController.kt` | Modified — added `WaitlistQueryTelemetryPort` dependency and listEntries telemetry call |
| `server/smp/.../platformadmin/application/ports/WaitlistQueryTelemetryPort.kt` | New — framework-free port |
| `server/smp/.../platformadmin/infrastructure/observability/WaitlistQueryObservabilityAdapter.kt` | New — Micrometer counter implementation |
| `server/smp/src/test/resources/features/platform-admin.feature` | Modified — added search-by-email and filter-by-status scenarios |
| `server/smp/src/test/kotlin/.../bdd/PlatformAdminBddSteps.kt` | Modified — added step definitions |
| `server/smp/src/test/kotlin/.../platformadmin/infrastructure/http/AdminWaitlistControllerTest.kt` | Modified — telemetry wiring assertions |
| `server/smp/src/test/kotlin/.../platformadmin/infrastructure/observability/WaitlistQueryObservabilityAdapterTest.kt` | New — adapter unit test |

## Dependencies

- DALLAY-436, DALLAY-438, DALLAY-439 (existing waitlist capture and persistence).
- Existing `platformadmin` bounded context and `WAITLIST_READ` permission.
- `openspec/specs/lead-capture-waitlist/spec.md` for waitlist aggregate invariants.
