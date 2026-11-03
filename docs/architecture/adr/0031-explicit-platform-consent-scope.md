# ADR-0031: Represent Platform Consent as an Explicit ID-Free Scope

- Status: Accepted
- Date: 2026-11-02
- Decision owners: Project owner
- Scope: `server/smp` governance consent scope, schema, persistence, and platform-admin bootstrap
- Supersedes: ADR-0030
- Superseded by: None
- Related:
  - [ADR-0029](./0029-use-raw-uuid-identifiers.md)
  - [ADR-0030](./0030-separate-public-waitlist-consent-scope.md)
  - [ADR-0032](./0032-bootstrap-system-actor-provenance.md)
  - [Consent scope contract](../../../.agents/sdd/changes/initial-platform-admin-bootstrap/specs/consent-scopes/spec.md)
  - [Bootstrap design](../../../.agents/sdd/changes/initial-platform-admin-bootstrap/design.md)
  - [C4 code model](../c4/04-code.md)
- Legal review status: legal-review-required

## Context

ADR-0030 defines consent ownership through mutually exclusive `WORKSPACE` and `WAITLIST` scopes.
It correctly requires real UUID references for both entity scopes, prevents a public waitlist from
being assigned to a tenant, and keeps tenant consent endpoints restricted to workspace data.

The initial platform-owner bootstrap must record acceptance of the current Terms version without
creating a workspace or membership. The user approved a platform-level consent record for this
purpose. Neither a workspace nor a waitlist represents ownership of this consent. A synthetic UUID
or a null-reference inference would hide the scope's meaning.

This is an engineering and product decision. It is not legal advice or evidence that the Terms or
consent design satisfies legal requirements. Legal review remains required.

## Decision Drivers

- Record the first operator's Terms acceptance without creating tenant data.
- Preserve existing workspace and waitlist consent ownership and isolation.
- Keep scope semantics explicit in stored projections and events.
- Avoid fake entity identifiers and ambiguous null-reference inference.
- Preserve scoped uniqueness, idempotency, history, withdrawal, and tenant endpoint authorization.

## Decision

1. Model consent scopes as `WORKSPACE`, `WAITLIST`, or `PLATFORM`.
2. Require an actual UUID reference for `WORKSPACE` and `WAITLIST`; keep their existing foreign-key and ownership rules.
3. Model `PLATFORM` as one ID-free scope for the running installation. Do not assign a sentinel UUID or a workspace/list reference.
4. Persist an explicit scope discriminator on consent projections and events. Database constraints MUST require entity references only for the matching `WORKSPACE` or `WAITLIST` kind and no entity reference for `PLATFORM`.
5. Include scope kind and any entity reference in active uniqueness, lookup, history, withdrawal, and event matching. Platform consent uniqueness MUST include subject, purpose, and policy version within the platform scope.
6. Record bootstrap Terms acceptance for the recipient identity as `PLATFORM` consent. Do not create a workspace, membership, or customer access to record this consent.
7. Keep tenant consent endpoints restricted to `WORKSPACE`; they MUST NOT read or withdraw `PLATFORM` or `WAITLIST` records.
8. Apply this ADR as a scoped replacement of ADR-0030's two-scope cardinality rule. Retain its real-UUID requirements for `WORKSPACE` and `WAITLIST`, public-list separation, migration safety, scoped uniqueness, history, and tenant isolation.
9. Reconsider the platform scope representation only through a new ADR and a data-preserving migration plan.

## Scope and Boundaries

`governance` owns consent scope semantics, recording, projection/event persistence, history, and
withdrawal. `identity` supplies the accepting user identity and current Terms version through an
approved application contract. `platformadmin` coordinates bootstrap and does not own or duplicate
governance persistence. Workspace consent APIs remain workspace-scoped and authorized by existing
workspace permissions.

The scope kind is not a DDD entity identity. `PLATFORM` denotes the singleton application scope in
one installation; `WORKSPACE` and `WAITLIST` continue to reference their actual UUID-backed owners.

## Alternatives Considered

### Reuse a workspace scope

- Description: Associate Terms acceptance with a newly provisioned workspace.
- Advantages: Reuses the existing workspace consent schema and flow.
- Disadvantages: Creates tenant data solely to record platform-level acceptance.
- Reason rejected: Bootstrap explicitly creates no workspace or membership.

### Assign a synthetic platform UUID

- Description: Represent the singleton platform as an entity-like UUID in existing reference columns.
- Advantages: Avoids a scope discriminator.
- Disadvantages: Invents an owner identity and can be confused with a workspace or waitlist UUID.
- Reason rejected: Violates real-entity identity and obscures the consent owner.

### Infer `PLATFORM` when both entity references are null

- Description: Treat a row with neither `workspace_id` nor `waitlist_id` as platform consent.
- Advantages: Requires fewer stored fields.
- Disadvantages: Scope meaning is implicit and cannot be constrained or queried as an explicit contract.
- Reason rejected: Null references alone do not identify the consent owner safely.

### Create a separate Terms-acceptance table

- Description: Store platform Terms acceptance outside governance consent.
- Advantages: Avoids changing the general consent schema.
- Disadvantages: Duplicates versioned consent, history, withdrawal, and audit behavior.
- Reason rejected: The platform-level acceptance is consent and should use the existing governance owner.

### Do not record Terms acceptance during bootstrap

- Description: Grant the first owner without current Terms acceptance.
- Advantages: No new scope or migration is needed.
- Disadvantages: The bootstrap account would bypass the user's chosen current-Terms requirement.
- Reason rejected: The user selected requiring current Terms acceptance.

## Consequences

### Positive

- The first platform operator can accept current Terms without tenant provisioning.
- Scope ownership is explicit and tenant API isolation remains testable.
- Workspace and waitlist UUID ownership remains unchanged.

### Negative

- Consent projections, events, queries, indexes, constraints, and migrations gain a third scope kind.
- Fresh-schema, upgrade, idempotency, history, withdrawal, and isolation tests must cover all three kinds.
- The previous two-scope cardinality statement in ADR-0030 is superseded.

### Risks

- Incorrect migration or query filters could expose platform consent through workspace endpoints.
- Legal review may require changes to the Terms acceptance flow or retained evidence.

### Accepted Trade-offs

- An explicit discriminator and additive migration are accepted to avoid fabricated identity and ambiguous null ownership.
- The `PLATFORM` scope is installation-wide because each self-hosted installation has a separate platform database.

## Compliance and Enforcement

Kotlin domain tests MUST verify the ID-free `PLATFORM` scope and UUID-backed entity scopes.
PostgreSQL checks MUST enforce valid scope/reference combinations and event/projection agreement.
Repository tests MUST cover per-scope uniqueness, idempotency, history, withdrawal, and concurrency.
Workspace endpoint tests MUST prove that only `WORKSPACE` records are accessible through tenant
consent routes. Liquibase tests MUST cover clean initialization and migration from the supported
schema without losing historical evidence.

## Verification

- Verify fresh PostgreSQL initialization and upgrade migration.
- Verify scope isolation and `ON CONFLICT` behavior under concurrent writes.
- Verify platform consent history and withdrawal preserve events.
- Verify workspace consent endpoints cannot read or modify `PLATFORM` records.
- Complete legal review separately; local engineering tests do not establish legal compliance.

## Migration or Remediation

Add an additive Liquibase changeset for the discriminator. Backfill existing consent projections and
events from their actual workspace/list references. Preserve row counts, owners, subjects, terms
versions, statuses, and events. Add scope-kind constraints and indexes only after the backfill is
validated. Do not rewrite applied changesets or infer platform scope for historical rows.

## Follow-up Actions

- [ ] Implement the three-kind scope model and additive migration in the bootstrap change.
- [ ] Add PostgreSQL concurrency, history, withdrawal, and tenant-isolation coverage.
- [ ] Complete the required legal review before production use.

## Revisit Conditions

Revisit this decision if platform consent becomes tenant-owned, a deployment hosts multiple
independent platform identities in one database, or legal review requires a different Terms evidence
model.
