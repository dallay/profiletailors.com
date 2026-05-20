# Proposal: Backend API Key Support

## Intent

Add the smallest executable API-key authentication capability to `server/smp` so the existing
proving slice can authenticate a persisted machine credential without broadening into
credential-management platform work.

This change exists to prove that an API key can be securely looked up and verified against
authoritative backend state, materialized into an `API_KEY` principal, and then governed by the same
workspace authorization rules already exercised on `/api/authorization/workspace-access/current`.

## Scope

### In Scope

- Persist API-key credential state with secure lookup and verifier semantics, including principal
  binding and active/revoked status.
- Materialize authenticated `API_KEY` principals for the existing
  `/api/authorization/workspace-access/current` proving slice.
- Enforce active/revoked API-key state before authorization executes.
- Prove end-to-end API-key allow, authorization-controlled deny, and revoked-credential deny
  behavior on `/api/authorization/workspace-access/current`.
- Reuse current runtime governance seams only as needed to make API-key allow/deny outcomes
  audit-ready on the existing slice.

### Out of Scope

- API-key issuance endpoints, admin CRUD APIs, operator consoles, or secret reveal flows.
- Rotation workflows, key families, dual-active rollover, expiration policy breadth, or last-used
  tracking.
- Credential inventory surfaces, broad metadata expansion, ownership-management consoles, or
  organization-wide reporting.
- New protected endpoints created only to prove API-key behavior.
- Broad credential-platform redesign, including forced unification of every credential form into a
  generic ledger unless directly required by the narrow slice.
- End-user JWT revocation expansion or unrelated service-account lifecycle work.

## Approach

Introduce a narrow API-key authentication adapter that coexists with the current JWT-based security
flow and is used only to authenticate the existing proving slice.

The implementation should stay tightly scoped:

- add persisted API-key credential state with a stable lookup identifier/prefix, non-plaintext
  secret verifier representation, principal binding, and active/revoked timestamps,
- authenticate presented API keys through lookup plus secret verification rather than JWT claim
  parsing,
- materialize a repo-local authenticated principal with `PrincipalType.API_KEY` and
  `CredentialType.API_KEY`,
- reuse the existing identity, active workspace, authorization, and governance seams after
  authentication succeeds,
- keep access truth in workspace membership, roles, and direct grants rather than in the API key
  itself.

## Affected Areas

| Area                                                                                                                     | Impact                   | Description                                                                                                                                  |
|--------------------------------------------------------------------------------------------------------------------------|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `openspec/changes/backend-api-key-support/proposal.md`                                                                   | New                      | Proposal artifact for this change.                                                                                                           |
| `openspec/specs/credentials/spec.md`                                                                                     | Modified later           | Delta spec will activate executable API-key persistence, secure lookup/verifier semantics, and revocation enforcement for the proving slice. |
| `openspec/specs/identity/spec.md`                                                                                        | Modified later           | Delta spec will activate runtime `API_KEY` principal materialization while preserving identity/credential separation.                        |
| `openspec/specs/platform/spec.md`                                                                                        | Modified later           | Delta spec will clarify deterministic protection rules for API-key authentication on the existing slice without broadening to new surfaces.  |
| `openspec/specs/governance/spec.md`                                                                                      | Modified later           | Delta spec will keep API-key observability limited to runtime audit-ready proof on the existing slice.                                       |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`    | Modified                 | Security chain will need a narrow API-key authentication path that coexists with the current JWT flow.                                       |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/domain/ValidatedToken.kt`                                 | Modified or complemented | Current normalized credential model may need careful extension or a parallel shape for API-key verification results.                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/**`                                                       | Modified                 | API-key lookup, verifier comparison, and authoritative active/revoked enforcement.                                                           |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/**`                                                          | Modified                 | Principal lookup/materialization support for persisted `API_KEY` principals.                                                                 |
| `server/smp/src/main/resources/db/changelog/**`                                                                          | Modified                 | Schema additions for persisted API-key credential state with secure lookup/verifier semantics.                                               |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`         | Modified                 | H2-backed proving-slice scenarios for API-key allow/deny/revoked flows.                                                                      |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` | Modified                 | PostgreSQL-backed proving-slice scenarios for API-key allow/deny/revoked flows.                                                              |

## Scope Boundaries

- The only new executable actor capability in this change is `API_KEY` authentication for the
  existing `/api/authorization/workspace-access/current` slice.
- The only required persisted credential breadth is what is necessary to securely look up, verify,
  and revoke an API key at runtime.
- The only required end-to-end proof surface is `/api/authorization/workspace-access/current`.
- Any pressure toward issuance, inventory, broad metadata, rotation, or generic credential-platform
  redesign must be deferred.

## Non-Goals

- Build a full API-key lifecycle management product surface.
- Design a universal credential inventory or administration platform.
- Re-architect the entire authentication boundary around multiple generalized transports.
- Expand authorization or tenancy semantics beyond proving that the current workspace-access rules
  still govern an authenticated `API_KEY` principal.

## Risks

| Risk                                                                                          | Likelihood | Mitigation                                                                                                                              |
|-----------------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| API keys are treated too much like JWTs, producing insecure or brittle verification semantics | Medium     | Make lookup + secret verification an explicit adapter path, separate from JWT claim validation, and prove behavior with focused tests.  |
| Scope expands into issuance, rotation, or operator tooling                                    | Medium     | Keep proposal/spec boundaries explicit and limit success criteria to runtime authentication on one existing endpoint.                   |
| Secret storage is implemented with plaintext or reversible persistence                        | Low/Medium | Require verifier-based storage semantics and keep the proposal explicit that secure lookup/verifier behavior is mandatory.              |
| API-key support triggers an unnecessary generic credential-platform redesign                  | Medium     | Model only the persisted state and seams needed for this proving slice; defer broad unification work unless proven necessary in design. |
| API-key principals bypass existing workspace authorization discipline                         | Low/Medium | Reuse current membership, role, and grant evaluation unchanged after principal materialization.                                         |

## Rollback Plan

If the change introduces instability or incorrect access behavior:

1. Disable or remove the API-key authentication adapter from the security chain so requests fall
   back to the pre-change authentication boundary.
2. Revert or inert the new API-key credential-state schema and repositories if rollout has not been
   promoted, or ship a follow-up migration that leaves the records unused while restoring current
   runtime behavior.
3. Restore principal materialization so executable access on the proving slice returns to the
   current non-API-key baseline.
4. Re-run the existing workspace-access proofs to confirm the baseline slice still behaves correctly
   without API-key authentication.

The rollback target is the currently working proving slice with no executable API-key authentication
behavior.

## Dependencies

- Existing exploration artifact: `openspec/changes/backend-api-key-support/exploration.md`
- Existing protected slice: `/api/authorization/workspace-access/current`
- Current identity, authorization, and governance seams already established in `server/smp`
- Existing persisted principal, workspace membership, and authorization model used by the proving
  slice

## Success Criteria

- [ ] The proposal stays intentionally narrow: persisted API-key runtime authentication plus
  active/revoked enforcement on the existing protected slice.
- [ ] The change clearly identifies affected backend modules/packages, scope boundaries, and
  non-goals.
- [ ] Specs and design can proceed without introducing issuance/admin APIs, rotation workflows,
  inventory surfaces, broad metadata, or broader credential-platform redesign.
- [ ] The rollback path restores the current proving slice without executable API-key
  authentication.
- [ ] End-to-end proof target is explicit: `/api/authorization/workspace-access/current` with
  API-key allow, authorization-controlled deny, and revoked-credential deny coverage.
