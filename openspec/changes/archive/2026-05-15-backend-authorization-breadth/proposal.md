# Proposal: Backend Authorization Breadth

## Intent

Expand backend authorization breadth by turning workspace-scoped direct grants from a
modeled/test-only concept into persisted, executable behavior on the existing
`/api/authorization/workspace-access/current` proving slice. This keeps the next authorization
change small but high-value: it proves real PostgreSQL/Liquibase-backed direct allow, direct deny,
and expired-grant behavior without broadening into phase-2 policy or governance work.

## Scope

### In Scope

- Add Liquibase and PostgreSQL persistence for workspace-scoped direct grants in `server/smp`,
  including principal binding, workspace context, permission key, effect (`ALLOW` / `DENY`), and
  optional expiration.
- Add an R2DBC-backed direct grant resolution path that returns active direct grants for the current
  principal and active workspace through the existing `DirectGrantResolver` seam.
- Make the existing `/api/authorization/workspace-access/current` proving slice execute persisted
  direct-grant behavior end to end for:
    - direct allow granting access when roles do not
    - direct deny overriding role-based allow
    - expired direct grants being ignored
    - existing role-only allow/deny behavior remaining intact
- Add or update focused H2/PostgreSQL integration verification for this same slice so the new
  breadth is proven against authoritative persistence paths.

### Out of Scope

- Scope evaluation or scope-reduction runtime enforcement.
- Feature entitlements or entitlement-gated execution.
- A separate deny-rule subsystem beyond `DirectGrant(effect = DENY)`.
- Grant administration endpoints, UI, CRUD workflows, back-office tools, or seed-management APIs.
- Broader policy APIs, generalized ABAC condition execution, or new protected authorization surfaces
  beyond `/api/authorization/workspace-access/current`.
- Governance expansion such as durable audit storage, policy administration, or compliance
  workflows.

## Approach

Use the existing workspace-access proving slice as the only execution surface and convert the
current direct-grant seam into a real persisted capability. Add one narrow authorization schema
increment for workspace-scoped direct grants, implement an R2DBC adapter following the existing
membership/role persistence pattern, and wire it into `WorkspaceAuthorizationService` through
current contracts so evaluation order remains deterministic: membership -> role permissions ->
direct grants -> explicit deny/allow outcome. Verification should stay focused on the existing
endpoint and prove that persisted grants affect runtime allow/deny behavior without introducing
management APIs or unrelated policy concepts.

## Affected Areas

| Area                                                                                                                     | Impact            | Description                                                                                                                           |
|--------------------------------------------------------------------------------------------------------------------------|-------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/resources/db/changelog/authorization/`                                                              | Modified          | Add Liquibase changelog(s) for persisted workspace-scoped direct grants and any required indexes/constraints.                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/AuthorizationModels.kt`                          | Possibly Modified | Align persisted direct-grant shape with existing domain model if persistence-specific identifiers or timestamps need mapping support. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt`           | Modified          | Consume resolved persisted direct grants while preserving existing deterministic allow/deny semantics.                                |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt`  | Modified          | Replace current no-op direct-grant wiring with the R2DBC-backed resolver for runtime execution.                                       |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/`                                        | New/Modified      | Add repository/adapter classes for resolving active direct grants for the current principal and workspace.                            |
| `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt`       | Modified          | Keep service-level semantics explicit for direct allow, direct deny, and expiration behavior.                                         |
| `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/DirectGrantPrecedenceTest.kt`               | Modified          | Preserve precedence regression coverage while shifting assertions toward persisted behavior assumptions where appropriate.            |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`         | Modified          | Verify executable persisted-grant behavior on the existing proving slice in the default integration path.                             |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` | Modified          | Prove the same direct-grant allow/deny/expired behavior against real PostgreSQL-backed execution.                                     |
| `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt`                  | Modified          | Assert the authorization changelog remains valid after adding direct-grant persistence.                                               |
| `openspec/specs/authorization/spec.md`                                                                                   | Referenced        | Source-of-truth semantics already define direct grants, deny precedence, and expiration behavior that this change operationalizes.    |
| `openspec/specs/governance/spec.md`                                                                                      | Referenced        | Confirms governance remains runtime-proof-only and should not broaden in this change.                                                 |

## Scope Boundaries

This change is intentionally a narrow breadth expansion, not a general authorization-platform phase.
It must stay anchored to persisted workspace-scoped direct grants and their executable effect on the
already-existing `/api/authorization/workspace-access/current` proving slice. If a proposed addition
does not directly support persisted direct-grant resolution or its allow/deny/expired runtime proof
on that slice, it belongs in a later change.

## Non-Goals

- Do not introduce scope-aware partial-access semantics just because `ScopeResolver` already exists.
- Do not introduce entitlement checks just because `EntitlementResolver` already exists.
- Do not design or implement a grant management subsystem.
- Do not create a separate deny-rule store when current domain semantics already support deny
  through direct grants.
- Do not broaden from one proving slice into generalized policy APIs or more protected endpoints.

## Risks

| Risk                                                                            | Likelihood | Mitigation                                                                                                                               |
|---------------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Direct-grant persistence scope expands into admin/API workflow work             | Medium     | Keep all write-path and management capabilities explicitly out of scope and verify only read/evaluation behavior on the existing slice.  |
| Duplicate or ambiguous persisted grants create unclear runtime behavior         | Medium     | Define narrow uniqueness/indexing rules in the schema and keep evaluation semantics aligned with current deterministic service behavior. |
| PostgreSQL and H2 behavior diverge around timestamps, enums, or SQL assumptions | Medium     | Cover direct allow, direct deny, and expired-grant scenarios in PostgreSQL-backed integration tests for the same slice.                  |
| Scope or entitlement seams tempt premature execution breadth                    | Medium     | Treat both as explicit deferrals and reject implementation steps that require new slice semantics to justify them.                       |
| Liquibase/R2DBC integration lands as another seam-only implementation           | Medium     | Require end-to-end verification through `/api/authorization/workspace-access/current` rather than relying only on unit tests.            |

## Rollback Plan

If the change proves unstable or over-broad, revert the direct-grant Liquibase changelog, R2DBC
resolver wiring, and related proving-slice tests together as one unit, restoring the previous no-op
direct-grant resolution path and role-only executable behavior for
`/api/authorization/workspace-access/current`. Because this change adds a narrow schema increment
and does not introduce management APIs or cross-module product features, rollback remains contained
to the `server/smp` authorization slice and its new persistence artifacts.

## Dependencies

- Existing exploration artifact at `openspec/changes/backend-authorization-breadth/exploration.md`.
- Current authorization source of truth at `openspec/specs/authorization/spec.md`.
- Existing proving slice at `/api/authorization/workspace-access/current`.
- Existing `server/smp` Liquibase + R2DBC patterns, especially current authorization changelogs and
  membership/role resolvers.
- PostgreSQL-backed integration test capability already established for the current slice.

## Success Criteria

- [ ] The proposal stays limited to persisted workspace-scoped direct grants and executable behavior
  on the current workspace-access proving slice.
- [ ] Liquibase/PostgreSQL persistence for direct grants is planned without introducing
  grant-management workflows.
- [ ] R2DBC resolution of active direct grants for the current principal and workspace is planned
  through existing authorization seams.
- [ ] The current `/api/authorization/workspace-access/current` slice is planned to prove persisted
  direct allow, direct deny, and expired-grant behavior end to end.
- [ ] Scopes, entitlements, separate deny-rule infrastructure, grant admin workflows, and broader
  policy APIs remain explicitly deferred.
- [ ] Rollback remains low-risk and contained to the authorization persistence increment plus
  proving-slice wiring/tests.
