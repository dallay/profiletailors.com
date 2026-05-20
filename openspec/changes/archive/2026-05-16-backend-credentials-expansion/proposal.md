# Proposal: Backend Credentials Expansion

## Intent

Add the smallest next executable credential capability to `server/smp` by turning deferred
service-account authentication into a real persisted and enforceable backend path.

This change exists to prove that non-user machine principals can authenticate through the current
bearer flow, remain governed by the same workspace authorization slice, and be denied immediately
when authoritative credential state revokes them.

## Scope

### In Scope

- Persist `SERVICE_ACCOUNT` principals in the existing identity model with only the minimal
  companion metadata needed to identify and operate that actor type.
- Add a service-account credential validation and principal materialization path that reuses the
  current bearer-based protected request flow.
- Add authoritative revocation enforcement for the service-account credential path so a technically
  valid presented credential is denied when backend credential state says it is revoked.
- Prove the behavior end to end on the existing `/api/authorization/workspace-access/current` slice,
  including allow, authorization-controlled deny, and revoked-credential deny outcomes.
- Keep audit-ready runtime proof aligned with the existing governance slice only as needed to make
  service-account use and revocation denial observable on that protected path.

### Out of Scope

- API key authentication, storage, hashing, lookup, request parsing, or transport handling.
- Full credential rotation workflows, credential families, dual-active rollover, or broad lifecycle
  automation.
- End-user JWT revocation or generalized blacklist/session invalidation for externally validated
  user tokens.
- Credential-management/admin APIs, issuance consoles, broad operator surfaces, or UI work.
- Broad governance expansion such as durable audit storage, audit query APIs, compliance reporting,
  or new governed endpoints.
- General-purpose machine identity platform abstractions beyond what this service-account path
  needs.

## Approach

Use the existing bearer-protected backend path as the transport entrypoint, then introduce a narrow
branch for service-account credentials that resolves to a persisted `SERVICE_ACCOUNT` principal and
`CredentialType.SERVICE_ACCOUNT`.

The implementation should stay close to the current architecture:

- extend persisted identity support using the current `principals` model plus minimal
  service-account metadata,
- introduce credential-side authoritative state sufficient to decide whether a presented
  service-account credential is active or revoked,
- materialize a repo-local authenticated principal through the existing security/application seams,
- keep workspace membership and authorization as the source of access truth,
- verify the new actor on the current workspace-access proving slice instead of broadening into new
  endpoints.

## Affected Areas

| Area                                                                 | Impact           | Description                                                                                                                    |
|----------------------------------------------------------------------|------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `openspec/changes/backend-credentials-expansion/proposal.md`         | New              | Proposal artifact for this change.                                                                                             |
| `openspec/specs/credentials/spec.md`                                 | Modified later   | Delta spec will narrow credentials expansion to executable service-account bearer auth plus revocation.                        |
| `openspec/specs/identity/spec.md`                                    | Modified later   | Delta spec will activate runtime `SERVICE_ACCOUNT` materialization while preserving principal/credential separation.           |
| `openspec/specs/platform/spec.md`                                    | Modified later   | Delta spec will clarify authoritative credential validity enforcement on the protected slice.                                  |
| `openspec/specs/governance/spec.md`                                  | Modified later   | Delta spec will keep observability limited to runtime proof on the existing workspace-access slice.                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/**`   | Modified         | Credential validation and authoritative revocation enforcement for service-account bearer credentials.                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/**`      | Modified         | Principal lookup/materialization support for persisted `SERVICE_ACCOUNT` principals and minimal metadata.                      |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/**` | Minimal/Indirect | Existing authorization slice remains the proving surface; behavior should stay driven by workspace state, not credential type. |
| `server/smp/src/main/resources/db/changelog/**`                      | Modified         | Schema additions for minimal service-account metadata and authoritative credential revocation state.                           |
| `server/smp/src/test/kotlin/com/profiletailors/smp/**`               | Modified         | End-to-end and focused tests for service-account allow/deny/revoked flows on the existing protected slice.                     |

## Scope Boundaries

- The only new executable actor capability in this change is `SERVICE_ACCOUNT` through the current
  bearer path.
- The only new credential invalidation behavior in this change is authoritative revocation for that
  service-account credential path.
- The only required end-to-end proving surface is the existing
  `/api/authorization/workspace-access/current` slice.
- Any design pressure toward API keys, credential issuance UX, multi-surface admin controls, or
  generic credential platform breadth must be deferred.

## Non-Goals

- Build a full credential inventory and lifecycle platform for every credential type.
- Re-architect the Spring Security entrypoint into multiple parallel authentication transports.
- Introduce universal revocation semantics across users, service accounts, API keys, and future
  credential families.
- Expand tenancy or authorization semantics beyond proving that existing workspace access rules
  still govern the new principal type.

## Risks

| Risk                                                                                     | Likelihood | Mitigation                                                                                                                                     |
|------------------------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| Service-account support expands into full machine identity lifecycle management          | Medium     | Keep scope limited to persisted principal support, bearer validation/materialization, revocation enforcement, and proof on one existing slice. |
| Revocation design becomes a generic credential ledger too early                          | Medium     | Model only the authoritative state needed for the service-account bearer path and defer universal abstractions.                                |
| User JWT and service-account bearer semantics blur in the shared transport path          | Medium     | Keep credential classification and principal materialization explicit, with separate repo-local mapping rules and tests.                       |
| Service accounts bypass workspace discipline if not tied to existing authorization facts | Low/Medium | Require the same workspace membership/authorization evaluation as the current USER path for the proving slice.                                 |
| “Credentials expansion” is misread as API key and rotation delivery                      | Medium     | State boundaries and non-goals explicitly in proposal, specs, and design.                                                                      |

## Rollback Plan

If the change introduces instability or incorrect access behavior:

1. Disable or remove the new service-account credential branch from the bearer authentication path.
2. Revert schema changes for service-account metadata and revocation state if the rollout has not
   been promoted, or ship a follow-up migration that leaves data inert while restoring current JWT
   USER-only behavior.
3. Restore the previous principal materialization path so only JWT-backed `USER` principals can
   reach the protected slice.
4. Re-run the existing workspace-access proofs to confirm the baseline hardening behavior remains
   intact.

The rollback target is the currently working USER JWT proving slice with no executable
service-account authentication behavior.

## Dependencies

- Existing exploration artifact: `openspec/changes/backend-credentials-expansion/exploration.md`
- Existing protected slice: `/api/authorization/workspace-access/current`
- Current backend foundation/hardening seams already established in `server/smp`
- Existing persisted workspace membership and authorization model used by the proving slice

## Success Criteria

- [ ] The proposal stays intentionally narrow: executable service-account bearer auth plus
  authoritative revocation on the existing protected slice.
- [ ] Specs and design can be written without introducing API key transport or full credential
  rotation workflows.
- [ ] The change clearly identifies affected backend modules/packages and concrete scope boundaries.
- [ ] The change defines a rollback path back to the current JWT USER-only behavior.
- [ ] End-to-end proof target is explicit: existing workspace-access slice with allow,
  authorization-controlled deny, and revoked-credential deny coverage.
