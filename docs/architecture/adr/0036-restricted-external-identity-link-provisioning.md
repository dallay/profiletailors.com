# ADR-0036: Provision External Identity Links Through Platform Administration

- Status: Accepted
- Date: 2026-11-10
- Decision owners: Project owner
- Scope: `server/smp` identity and platform administration
- Supersedes: None
- Superseded by: None
- Related:
  - [ADR-0035](./0035-keycloak-human-authentication-bff.md)
  - [Keycloak human authentication](../../../.agents/sdd/changes/keycloak-human-authentication/proposal.md)
  - [Human authentication contract](../../../.agents/sdd/changes/keycloak-human-authentication/specs/human-authentication/spec.md)
  - [Registration contract](../../../.agents/sdd/changes/keycloak-human-authentication/specs/registration/spec.md)
  - [Technical design](../../../.agents/sdd/changes/keycloak-human-authentication/design.md)
  - Issues/PRs: None

## Context

ADR-0035 requires an explicitly approved association from a validated Keycloak `(issuer, subject)`
pair to an existing Profile Tailors `principal_id`. The identity context can resolve links and
synchronize verified email, but no application operation provisions a link. Direct database writes
would bypass platform authorization and the existing administrative audit boundary.

## Decision drivers

- Keep local principal identity and authorization under Profile Tailors control.
- Require explicit operator intent for identity association.
- Prevent email-based account selection, principal creation, link transfer, or overwrite.
- Reuse platform-admin authorization and audit contracts.
- Preserve the identity context's ownership of external-link persistence.

## Decision

1. Expose `POST /api/admin/users/{principalId}/identity-link` with explicit `issuer` and `subject`
   request fields and a required `Idempotency-Key` header. Return `201 Created` when a new link is
   persisted.
2. Require the local `USERS_MANAGE` permission at the application boundary and validated `pt-admin`
   ACR 2 plus fresh MFA at the security boundary.
3. Validate the supplied issuer against the configured Keycloak issuer. Resolve only the explicitly
   supplied existing local principal. Do not create principals or infer association values from email,
   invitation state, or self-registration.
4. Enforce uniqueness only for `(issuer, subject)`. Permit multiple external identity links to point
   to one local principal. Do not transfer or replace an existing link.
5. Reject malformed requests with `400`, missing operators with `401`, insufficient authorization
   with `403`, missing principals with `404`, and duplicate or conflicting links with `409`, using the
   established problem-details contract. Do not disclose which principal owns an existing subject.
6. Record authenticated success and rejection outcomes through existing administrative audit
   contracts. Include operator and target principal IDs, issuer, subject, action, outcome, and
   occurrence time. Do not record credentials, bearer tokens, session identifiers, or raw
   authentication artifacts. Requests rejected before an operator identity is established do not
   require operator-attributed audit events.
7. Follow the existing idempotency mechanism only to the extent its established contract supports the
   operation. Do not add a new persistence or replay guarantee as part of this decision.
8. Keep the HTTP adapter in `platformadmin` and link validation/persistence in `identity`; do not add
   a cross-context dependency on identity infrastructure from platformadmin.

## Scope and boundaries

`platformadmin` owns the operator-facing use case, local permission checks, and audit publication.
`identity` owns configured-issuer validation, external-link constraints, and persistence through an
application-facing port. The HTTP adapter maps requests and responses and does not access persistence
directly. The change does not require a new admin SPA screen; operators may use the documented API
workflow. Keycloak remains responsible for authentication, while Profile Tailors remains responsible
for authorization.

## Alternatives considered

### Controlled DBA procedure

- Description: Create identity links through a restricted database procedure or manual SQL.
- Advantages: Adds no application endpoint or operator-facing API.
- Disadvantages: Bypasses application permission checks and established administrative audit events.
- Reason rejected: The user selected an application-level restricted and auditable workflow.

### Link by email or invitation token

- Description: Select a local principal by matching email or invitation data during login or
  acceptance.
- Advantages: Reduces operator setup steps.
- Disadvantages: Can merge identities or grant access to the wrong local principal without explicit
  proof of identity association.
- Reason rejected: Email and invitation tokens are not identity keys or authorization to link accounts.

### Create the principal as part of link provisioning

- Description: Let the endpoint create a local principal from the external identity payload.
- Advantages: Combines onboarding into one operation.
- Disadvantages: Changes the approved invite-only, manually provisioned principal lifecycle and can
  create unapproved accounts.
- Reason rejected: The workflow must link only an existing principal.

## Consequences

### Positive

- Operators can provision identity links without direct database writes.
- Local permission, admin step-up, and audit behavior remain in existing platform-admin boundaries.
- Duplicate external identities cannot transfer or overwrite existing links.

### Negative

- Platform administration gains a new mutation and its application, persistence, audit, API, and BDD
  tests.
- Operators must provide the validated Keycloak subject explicitly and must verify the target
  principal before submitting the request.

### Risks

- Incorrectly verified subject values can still associate the wrong human with a local principal.
- The existing audit contract must retain enough detail for investigation without logging
  authentication artifacts.
- Existing pre-cutover legacy authentication behavior remains subject to ADR-0035's hard-cutover
  gates and is not changed by this endpoint.

### Accepted trade-offs

- Explicit operator provisioning adds operational effort but avoids implicit or email-based identity
  linking.
- The API requires an issuer even though the deployment has one configured Keycloak issuer, so the
  server can reject a request whose intended identity provider does not match its configuration.

## Compliance and enforcement

Application tests MUST verify local `USERS_MANAGE` enforcement, existing-principal-only behavior,
duplicate rejection, and audit outcomes. Infrastructure tests MUST verify configured-issuer validation,
unique `(issuer, subject)` enforcement, and that multiple external links may reference one principal.
Security and WebFlux tests MUST verify `pt-admin` ACR 2 and fresh-MFA enforcement. Cucumber scenarios
MUST verify success, authorization denial, invalid/missing input, missing principal, duplicate
conflict, and audit evidence. Existing architecture checks MUST confirm layer and module boundaries.

## Verification

- Verify `400`, `401`, `403`, `404`, `409`, and `201` responses with the established problem-details
  contract.
- Verify audit evidence contains only the approved identifiers and outcome fields.
- Verify no principal or existing link changes on rejection or conflict.
- Verify MCP authentication and local resource authorization remain unchanged.

## Migration or remediation

Add the application-facing identity-link operation and persistence support. Add the restricted
platform-admin mutation, security matcher, audit action, and operator API guidance. Reconcile the
active SDD change and product/operations contracts before implementation and verification closure.

## Follow-up actions

- [ ] Implement the API and audit behavior from the active SDD tasks.
- [ ] Verify the operational procedure for validating a Keycloak subject before linking it.
- [ ] Keep real-realm MFA/OTP, DNS/proxy, session-cookie, and external-consumer acceptance as separate
  hard-cutover gates.

## Revisit conditions

Revisit this decision through a new ADR if identity linking requires principal creation, subject
transfer, a different principal-cardinality rule, or an administrative workflow outside the existing
platform-admin boundary.
