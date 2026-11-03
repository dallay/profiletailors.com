# ADR-0034: Record Bootstrap Terms Through the Governance Application Port

- Status: Accepted
- Date: 2026-11-02
- Decision owners: Project owner
- Scope: `server/smp` modules `platformadmin` and `governance`
- Supersedes: None
- Superseded by: None
- Related:
  - [ADR-0023](./0023-platformadmin-governance-application-takedown.md)
  - [ADR-0031](./0031-explicit-platform-consent-scope.md)
  - [ADR-0033](./0033-atomic-bootstrap-owner-grant.md)
  - [Consent scope contract](../../../.agents/sdd/changes/initial-platform-admin-bootstrap/specs/consent-scopes/spec.md)
  - [Bootstrap design](../../../.agents/sdd/changes/initial-platform-admin-bootstrap/design.md)
- Legal review status: legal-review-required

## Context

The initial-owner bootstrap must record the recipient's current Terms acceptance in the ID-free
`PLATFORM` consent scope. `governance` owns consent scope and persistence. `platformadmin` owns the
bootstrap invitation and owner-grant flow.

The current implementation of `GovernanceBootstrapTermsConsentVerifier` imports governance domain
types from `platformadmin`. Spring Modulith rejects that dependency. ADR-0023 and `platformadmin`'s
`ModuleMetadata` already permit a dependency on `governance :: application`, but platformadmin must
not consume governance domain or infrastructure.

This is an engineering and product decision. It does not establish legal compliance. Legal review of
the Terms and consent evidence remains required.

## Decision Drivers

- Keep governance as the owner of consent scopes, invariants, events, and persistence.
- Keep the `platformadmin` module inside its approved `governance :: application` dependency.
- Prevent governance domain types from crossing into platformadmin.
- Record bootstrap Terms acceptance without a workspace or membership.
- Preserve existing tenant consent endpoints and their authorization boundary.

## Decision

1. Implement bootstrap Terms verification and recording through a narrow public contract in
   `governance :: application`.
2. The contract MUST accept only application-level values, including the accepting principal UUID,
   current Terms version, and occurrence time. It MUST NOT expose `ConsentScope`, `ConsentRecord`,
   `ConsentRepository`, or another governance domain type to platformadmin.
3. Governance application MUST map the request to `ConsentScope.Platform`, purpose
   `terms.acceptance`, and the approved consent type and MUST own persistence and event creation.
4. `platformadmin` MAY consume this consent contract for the bootstrap flow. It MUST NOT import
   `governance :: domain` or `governance :: infrastructure` for bootstrap consent.
5. Keep `platformadmin :: governance :: application` as the existing narrow named-interface edge.
   Do not add a wildcard dependency or weaken `ModularStructureTest` or `ModularityVerificationTest`.
6. Keep workspace consent commands and endpoints restricted to `WORKSPACE`; the new port does not
   grant platform consent access through tenant APIs.
7. Reconsider the exposed contract only through a new ADR if another use case requires a materially
   different governance operation.

## Scope and Boundaries

`governance` owns the meaning and persistence of `PLATFORM` consent. `platformadmin` requests the
bootstrap acceptance operation through a public application contract and owns the decision to grant
`PLATFORM_OWNER`. The contract may participate in ADR-0033's existing verification transaction, but
it does not own the platform role or identity verification token.

## Alternatives Considered

### Import governance domain types in platformadmin

- Description: Let the bootstrap verifier use `ConsentScope`, `ConsentRepository`, and
  `SubjectReference` directly.
- Advantages: Minimal adapter code.
- Disadvantages: Crosses the bounded-context domain boundary and fails Spring Modulith verification.
- Reason rejected: Governance owns the consent model and exposes application behavior instead.

### Add a wildcard governance dependency

- Description: Permit platformadmin to depend on all governance layers.
- Advantages: Removes immediate import errors.
- Disadvantages: Allows platformadmin to bypass governance application behavior and persistence
  ownership.
- Reason rejected: Weakens the module boundary and conflicts with ADR-0001/ADR-0023.

### Duplicate platform Terms persistence in platformadmin

- Description: Store Terms acceptance in a bootstrap-specific table owned by platformadmin.
- Advantages: Avoids a governance module call.
- Disadvantages: Duplicates consent scope, idempotency, history, and event behavior.
- Reason rejected: ADR-0031 and the governance contract assign consent ownership to governance.

## Consequences

### Positive

- Consent ownership remains in governance while platformadmin coordinates the bootstrap use case.
- The existing module dependency remains layer-specific and can be verified without exceptions.
- Workspace consent APIs remain isolated from platform consent.

### Negative

- Governance application gains a narrow bootstrap Terms operation and corresponding tests.
- The application port must remain stable and must not become a general repository facade.

### Risks

- A broad or domain-shaped DTO could recreate the forbidden coupling without an import violation.
- Transactional correctness requires the port implementation to use the active R2DBC transaction.

### Accepted Trade-offs

- A focused application contract is preferred over a small amount of duplicated mapping logic in
  platformadmin.

## Compliance and Enforcement

`ModularStructureTest` and `ModularityVerificationTest` MUST pass. `platformadmin` production code
MUST contain no imports from `governance :: domain` or `governance :: infrastructure` for bootstrap
consent. Governance application tests MUST verify the resulting scope, purpose, policy version,
projection, and event.

## Verification

- Verify platformadmin consumes only the governance application contract.
- Verify governance persists ID-free `PLATFORM` consent and matching events.
- Verify workspace consent endpoints cannot read or withdraw platform consent.
- Verify the consent operation participates in ADR-0033's transaction rollback tests.

## Migration or Remediation

Move bootstrap Terms persistence and verification out of the platformadmin adapter and into the
governance application-owned operation. Remove the direct governance domain imports. Keep the
existing `governance :: application` allowlist narrow and unchanged.

## Follow-up Actions

- [ ] Add the governance application contract and implementation for platform Terms consent.
- [ ] Replace `GovernanceBootstrapTermsConsentVerifier` with the approved application operation.
- [ ] Add Modulith and transaction tests for the new call path.

## Revisit Conditions

Revisit this contract if platformadmin must consume another governance operation whose input or
consistency requirements cannot be represented by the existing narrow application interface.
