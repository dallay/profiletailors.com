# ADR-0008: Application-Level Multi-tenancy

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Repository-wide
- Supersedes: None
- Superseded by: None

## Context
The platform is multi-tenant by design, with Workspaces being the primary isolation boundary.

## Decision drivers
- Data Isolation (users must never see data from other workspaces).
- Implementation Simplicity (easy to test and reason about in application code).
- Scalability (supports many small tenants).

## Decision
Multi-tenancy MUST be enforced at the application level.
- **Tenant ID**: The `workspace_id` MUST be present on all tenant-scoped database rows.
- **Header**: The frontend MUST supply the active workspace ID via the `X-Workspace-Id` header.
- **Resolution**: The backend MUST extract this header and store it in the `ResourceContext` for the duration of the request.
- **Filtering**: Every database query for tenant-scoped data MUST include a `WHERE workspace_id = :workspaceId` clause.

## Scope and boundaries
- All repositories under `com.profiletailors.smp`.
- Cross-tenant lookups (e.g., admin views) MUST be explicitly authorized and bypass standard filters.

## Alternatives considered
### PostgreSQL Row Level Security (RLS)
- Advantages: Stronger isolation at the database level.
- Disadvantages: More complex to manage migrations, harder to debug, and requires consistent setting of session variables.
- Reason rejected: Application-level filtering is sufficient for the current scale and easier for developers to implement with R2DBC.

## Consequences
### Positive
- Explicit code logic makes isolation clear to developers.
- Easy to perform cross-tenant operations in administrative contexts.

### Negative
- Risk of developer error (forgetting a `WHERE` clause).
- Potential performance impact on very large tables if indexes aren't correctly applied to `workspace_id`.

## Compliance and enforcement
Enforced through Repository interfaces and code reviews.

## Verification
- Repository integration tests check for `workspaceId` filtering.
- `WorkspaceContextWebFilter` is active in the backend.

## Migration or remediation
None required; the pattern is already consistently applied.

## Revisit conditions
- Regulatory requirements demand database-enforced isolation (e.g., RLS).
- Accidental data leakage occurs due to missing application filters.
