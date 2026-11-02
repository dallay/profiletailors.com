# ADR-0033: Complete Bootstrap Role Grant Within Email Verification

- Status: Accepted
- Date: 2026-11-02
- Decision owners: Project owner
- Scope: `server/smp` identity, platformadmin, and governance consent application contracts
- Supersedes: None
- Superseded by: None
- Related:
  - [ADR-0002](./0002-adhere-to-hexagonal-architecture.md)
  - [ADR-0023](./0023-platformadmin-governance-application-takedown.md)
  - [ADR-0031](./0031-explicit-platform-consent-scope.md)
  - [ADR-0032](./0032-bootstrap-system-actor-provenance.md)
  - [Email verification specification](../../../.agents/sdd/changes/initial-platform-admin-bootstrap/specs/email-verification/spec.md)
  - [Bootstrap specification](../../../.agents/sdd/changes/initial-platform-admin-bootstrap/specs/platform-admin-bootstrap/spec.md)
  - [Bootstrap design](../../../.agents/sdd/changes/initial-platform-admin-bootstrap/design.md)
- Legal review status: legal-review-required

## Context

The identity module owns password registration and email verification. The platformadmin module owns
`PLATFORM_OWNER` role assignment and administrative audit. The existing email verification handler
consumes a hashed verification token and changes email status inside a transaction, then issues a
session. The existing registration path also provisions a workspace and membership, so bootstrap
cannot reuse it unchanged.

The bootstrap flow creates a new identity, password, current `PLATFORM` Terms consent, and email
verification token without tenant data. The owner role must be granted only after verification. A
separate post-commit grant could leave a verified identity without owner access if the grant or audit
write fails. A grant must not trigger a second verification email.

This is an engineering and product decision. It does not establish legal compliance or exactly-once
email delivery. Legal review remains required.

## Decision Drivers

- Grant `PLATFORM_OWNER` only after successful verification of the bootstrap recipient.
- Preserve the identity and platformadmin module boundary.
- Keep verification-token consumption, email status, bootstrap completion, role grant, and audit atomic.
- Avoid workspace or membership provisioning and preserve the existing verification email route.
- Keep normal customer registration and verification behavior unchanged.

## Decision

1. Define a public application-level verification-completion port in `identity` that accepts the verified principal identity and verification instant; do not import platformadmin into identity domain or application code.
2. Implement the port in `platformadmin` infrastructure using platformadmin application handlers and governance's consent contract where needed.
3. Invoke the port from `VerifyEmailHandler` within the existing R2DBC transaction after conditional, unexpired, single-use token consumption and email-status update, but before transaction completion.
4. Make the platformadmin adapter a no-op for principals without a claimed bootstrap invitation. For a claimed bootstrap, it MUST verify matching principal/email, current Terms consent, invitation state, and owner preconditions before completing the invitation, assigning `PLATFORM_OWNER` with actor `SYSTEM_BOOTSTRAP`, and recording audit.
5. Treat invitation expiry as an issuance-claim boundary: the recipient MUST claim the invitation before its fixed 30-minute expiry. Once claimed, bind the invitation to the new principal in a non-reassignable pending-verification state; the existing email-verification token governs the remaining verification period.
6. Roll back verification-token consumption, email status change, bootstrap completion, role assignment, and audit together if the completion port fails. The account and Terms consent created during password setup remain pending so the recipient can retry using an unconsumed verification token.
7. Keep the existing `UserRegistered` event and verification email destination after password setup. Do not publish a second verification event during role grant. Retain the existing resend path. Do not claim exactly-once provider delivery without an outbox/idempotency design.
8. Keep workspace invitations, ordinary registration, and ordinary email verification behavior unchanged.

## Scope and Boundaries

`identity` owns verification token validation, consumption, and email status. Its application contract
defines the verification-completion port. `platformadmin` implements the port and owns bootstrap
invitation, owner assignment, actor provenance, and administrative audit. `governance` owns
`PLATFORM` Terms consent under ADR-0031. Cross-context calls use public application contracts; no
controller or handler may call another context's persistence adapter directly.

The existing customer verification email continues to use its configured application URL and
`POST /api/auth/verify-email`. The admin `/bootstrap` route is for invitation claim and password
setup only. It does not replace the verification UI or expose issuance/status.

## Alternatives Considered

### Grant after the verification transaction commits

- Description: Publish a verification event and grant the role in a later handler.
- Advantages: Looser runtime coupling between identity and platformadmin.
- Disadvantages: Verification can commit while role assignment or audit fails; event delivery is not currently proven durable.
- Reason rejected: It creates a partial success state without a durable retry/outbox contract.

### Add a bootstrap-specific verification endpoint

- Description: Route bootstrap recipients to a separate verification controller and token flow.
- Advantages: Separates bootstrap HTTP behavior.
- Disadvantages: Duplicates token handling and can drift from the existing verification contract.
- Reason rejected: Use the existing verification endpoint and an application port for the post-verification role grant.

### Reuse customer registration

- Description: Create the bootstrap account through `RegisterUserHandler` and workspace invitation acceptance.
- Advantages: Reuses the current registration form and email event.
- Disadvantages: Creates workspace/membership and age-consent records, which bootstrap forbids.
- Reason rejected: Violates the approved no-tenant and no-age-check bootstrap contract.

### Publish a second verification event after grant

- Description: Reuse `UserRegistered` after the role grant to trigger additional verification email delivery.
- Advantages: Reuses the existing email consumer.
- Disadvantages: Can duplicate verification messages and does not improve delivery guarantees.
- Reason rejected: Verification email is sent after password setup; role grant emits no email event.

## Consequences

### Positive

- Verification and owner grant have one atomic database outcome.
- Identity remains unaware of platformadmin implementation details.
- Normal email verification and customer registration remain unchanged.
- The bootstrap invitation is consumed before verification and is bound to the identity that claimed it.

### Negative

- Identity exposes a new public application contract and invokes a cross-context completion port in its transaction.
- All modules must use the same reactive transaction context for the atomic completion.
- PostgreSQL integration tests must prove rollback, concurrency, and replay behavior.

### Risks

- A blocking or external side effect in the completion port could hold the identity transaction open; the port MUST perform only database work.
- A future asynchronous refactor must add durable event/outbox semantics before moving role grant after commit.
- The existing email publisher is best-effort and does not guarantee exactly-once delivery; the resend path remains necessary.

### Accepted Trade-offs

- Synchronous application-port coupling is accepted to protect the verification/role-grant invariant.
- Bootstrap's 30-minute invitation expiry applies to claim; the existing verification token expiry applies after claim.

## Compliance and Enforcement

Identity MUST depend only on its own application contract. Platformadmin MAY implement that contract
through its infrastructure layer and consume public governance application contracts. Existing
Spring Modulith module tests and architecture tests MUST pass without exemptions. PostgreSQL tests
MUST cover transaction rollback, concurrent verification, expired/unclaimed invitation, used-token
replay, and one role grant. BDD MUST prove no workspace, membership, or customer access is created.

## Verification

- Verify bootstrap account and `PLATFORM` Terms consent creation without tenant rows.
- Verify invalid, expired, used, or email-mismatched verification tokens do not grant the role.
- Verify successful verification and role assignment commit together.
- Force a grant/audit failure and verify token consumption and email status roll back.
- Verify a normal user verification does not create a platform role or send another email.
- Verify the existing verification email destination and resend behavior remain unchanged.

## Migration or Remediation

Add bootstrap and actor migrations additively. Existing identity and workspace invitation rows remain
unchanged. A claimed invitation stores its principal identity; no raw token is retained. The current
verification-token hash remains the sole email-verification credential. Test clean initialization
and upgrade from the supported current schema.

## Follow-up Actions

- [ ] Add the identity-owned verification-completion port and platformadmin implementation.
- [ ] Add conditional one-use verification-token consumption and transaction rollback tests.
- [ ] Add BDD and architecture/module-boundary checks for verified owner grant.
- [ ] Verify container image TTY invocation and existing verification-email behavior.

## Revisit Conditions

Revisit the synchronous completion contract if verification and platformadmin move to separate
databases, if a durable outbox is adopted, or if the first-owner workflow gains an authenticated
human operator identity.
