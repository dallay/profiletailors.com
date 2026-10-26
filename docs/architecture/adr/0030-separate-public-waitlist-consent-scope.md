# ADR-0030: Separate Public WAITLIST Consent from Tenant Workspaces

- Status: Accepted
- Date: 2026-10-26
- Decision owners: Project owner
- Scope: `server/smp` governance/leadcapture and `shared/lead-capture/waitlist`
- Supersedes: None
- Superseded by: None
- Related:
  - [ADR-0029](./0029-use-raw-uuid-identifiers.md)
  - [UUID design](../../../.agents/sdd/changes/archive/2026-10-26-uuid-native-identifiers/design.md)
  - [Consent scope contract](../../../.agents/sdd/specs/consent-scopes/spec.md)
  - [C4](../c4/04-code.md)
- Legal review status: legal-review-required

## Context

Public waitlist capture recorded consent with `workspaceId = "waitlist:<key>"`. This synthetic
scope was not a workspace identity. Native PostgreSQL UUID references exposed the distinction:
persistence and the migration guard rejected that value. The user explicitly selected a separate
`WAITLIST` scope rather than assigning each public list to a tenant workspace.

This is an engineering/product decision, not legal advice or evidence of legal compliance. It
does not change processing purposes, consent copy or policy versions.

## Decision

1. Model `WORKSPACE` and `WAITLIST` as mutually exclusive consent scopes.
2. Identify each scope through its actual entity UUID. Do not fabricate a workspace or treat a
   semantic waitlist key as a tenant UUID.
3. Keep governance independent of leadcapture's internal entities. The framework-free capture
   contract carries the already resolved waitlist identity.
4. Persist separate native UUID references to workspace and waitlist, with exactly one present.
5. Include scope in active uniqueness, queries, history, events and withdrawal.
6. Resolve historical `waitlist:<key>` scopes against existing lists. Abort with rollback when
   resolution is absent or ambiguous; do not discard legal evidence or introduce a synthetic fallback.
7. Keep tenant HTTP endpoints restricted to `WORKSPACE`. A null workspace must not grant access
   to public records or weaken authorization.
8. Verify scope isolation, idempotency, concurrency, events and historical migration through
   PostgreSQL and BDD tests. Production execution and deployment require separate operational approval.

## Consequences

- Public capture no longer depends on a fictitious tenant authorization context.
- Consent remains traceable to its actual owner without changing the subject or processing purpose.
- Projections, events, indexes and consumers must migrate together, increasing migration cost.
- Ambiguous historical references require a reviewed mapping before cutover, not automatic guessing.
- Legal review and operational validation remain separate obligations outside local verification.

## Alternatives Considered

### Assign every public list to an actual workspace

Not adopted. This requires tenant ownership for a public capture and changes its access scope.
The user selected explicit separation instead.

### Retain `waitlist:<key>` or fabricate a workspace UUID

Rejected. The string does not meet the UUID contract, while a UUID without an actual workspace
hides the same identity problem. Disabling consent recording to obtain passing tests is also rejected.

## Verification

The implementation is locally verified. Native PostgreSQL and both BDD lanes pass, including
same-UUID WORKSPACE/WAITLIST isolation. The SQL harness passes 34 rejection/rollback cases.
Actual browser capture and tenant query/withdrawal checks pass against the packaged backend and
a disposable database. Exact commands and evidence are maintained in the
[change record](../../../.agents/sdd/changes/archive/2026-10-26-uuid-native-identifiers/apply-progress.md).
This does not establish legal review, a production restore rehearsal, remote CI or cutover approval.
