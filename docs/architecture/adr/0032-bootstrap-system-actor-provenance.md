# ADR-0032: Represent Initial Bootstrap as a System Actor

- Status: Accepted
- Date: 2026-11-02
- Decision owners: Project owner
- Scope: `server/smp` platform-role assignments, platform-admin audit, and initial-owner bootstrap
- Supersedes: None
- Superseded by: None
- Related:
  - [ADR-0031](./0031-explicit-platform-consent-scope.md)
  - [Bootstrap design](../../../.agents/sdd/changes/initial-platform-admin-bootstrap/design.md)

## Context

The first platform owner is assigned before an authenticated platform operator exists. The private
CLI runs inside the backend container and does not receive a verified human principal identity. The
new owner is the recipient of the grant, not the operator who initiated bootstrap. The current
`PlatformRoleAssignment` and `AdminAuditEvent` models require a human principal UUID and the audit
event also records the operator's platform roles.

Using the recipient as the actor would misattribute self-provisioning. A made-up UUID or a synthetic
user account would create an identity that does not represent an authenticated person.

## Decision Drivers

- Preserve truthful attribution for the first-owner assignment.
- Keep human operator identity and authorization required for ordinary role changes.
- Represent the absence of an authenticated human principal without a fake user.
- Make system-originated bootstrap events visible in audit queries and review surfaces.

## Decision

1. Represent the bootstrap actor with the explicit actor kind `SYSTEM_BOOTSTRAP` and no human principal UUID.
2. Do not use the recipient principal as the actor for the initial role assignment or its audit event.
3. Keep human role assignments and audit events attributed to their authenticated operator principal; the system actor is valid only for dedicated bootstrap invitation issuance/replacement and the initial-owner grant with their related audit events.
4. Persist the actor kind for both role assignment provenance and audit events. Require a human principal UUID for human actors and no principal UUID for `SYSTEM_BOOTSTRAP` through domain validation and database constraints.
5. Represent the system actor's platform-role snapshot as an empty set. Do not claim that the system actor has human platform permissions.
6. Expose actor kind and the nullable principal identity in internal administrative audit read models so the event is not hidden or presented as a human action.
7. Reconsider the actor model only through a new ADR if the CLI gains a verified operator identity.

## Scope and Boundaries

`platformadmin` owns role assignment and administrative audit semantics. The bootstrap use case may
create a system-attributed initial assignment without invoking the normal operator-authorized role
assignment handler. Normal role assignment continues to require `OPERATORS_MANAGE` and a human
principal. No shared or customer-facing authentication contract changes.

## Alternatives Considered

### Attribute the assignment to the recipient

- Description: Use the newly created owner principal as `assignedBy` and audit operator.
- Advantages: Satisfies existing non-null UUID fields without a schema change.
- Disadvantages: Records the target as the actor and falsely describes the operation.
- Reason rejected: The recipient is not the person or system that initiated bootstrap.

### Create a synthetic service user

- Description: Insert a special user identity and use its UUID as the actor.
- Advantages: Preserves the existing UUID columns.
- Disadvantages: Creates a non-human identity in user tables and risks confusion with normal accounts and permissions.
- Reason rejected: A fake user identity is not a truthful actor model.

### Store only an unauthenticated CLI label

- Description: Persist an operator-supplied string or container username.
- Advantages: Can display an attribution label without an identity UUID.
- Disadvantages: The string is not authenticated, and the container user does not identify the human host operator.
- Reason rejected: It would imply an attribution level that the system cannot verify.

## Consequences

### Positive

- Initial-owner creation is auditable without falsely identifying the recipient or a human operator.
- Ordinary platform administration retains its current authenticated actor requirements.

### Negative

- Role assignment and audit schemas, domain models, repositories, and internal read models must support a typed non-human actor.
- Audit consumers must display `SYSTEM_BOOTSTRAP` as a system action with no human principal.

### Risks

- If a system actor is permitted beyond the dedicated bootstrap path, it could become a bypass for operator authorization.
- Consumers that assume all audit actors are UUID-backed humans could fail until updated.

### Accepted Trade-offs

- The system records that bootstrap performed the action, not which person ran the container command.
- Host-level Docker audit remains separate evidence and is not copied into application audit without a verified identity contract.

## Compliance and Enforcement

Domain tests MUST reject a human actor without a principal UUID and MUST reject system actor use outside
the bootstrap grant. Persistence constraints MUST enforce actor-kind/principal consistency. Tests MUST
verify the initial owner is the target, `SYSTEM_BOOTSTRAP` is the actor, and ordinary role changes
still require an authenticated platform operator.

## Verification

- Verify role assignment actor-kind constraints on a clean PostgreSQL schema and upgrade.
- Verify bootstrap audit reads as `SYSTEM_BOOTSTRAP` with no principal UUID.
- Verify the admin audit view does not label the recipient as the operator.
- Verify normal `AssignPlatformRoleHandler` authorization is unchanged.

## Migration or Remediation

Additive migrations MUST preserve existing human actor UUIDs and role snapshots. Backfill existing
role assignments and audit events as human actors. Use `SYSTEM_BOOTSTRAP` only for new bootstrap
records. Do not rewrite or reattribute historical events.

## Follow-up Actions

- [ ] Add actor-kind fields and constraints to role assignment and admin audit records.
- [ ] Update internal audit response types and UI to render system actors explicitly.
- [ ] Add domain, PostgreSQL, and admin audit tests.

## Revisit Conditions

Revisit this decision if bootstrap gains an authenticated and verified operator identity or if the
platform adopts a service-principal model for privileged operations.
