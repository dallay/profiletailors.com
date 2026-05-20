# Tasks: Backend Auth Foundation

## Review Workload Forecast

| Field                      | Value                                                                                                               |
|----------------------------|---------------------------------------------------------------------------------------------------------------------|
| Review budget              | 400 changed lines                                                                                                   |
| Estimated workload         | High                                                                                                                |
| Chained PRs recommended    | Yes                                                                                                                 |
| Proposed delivery strategy | stacked-prs                                                                                                         |
| Work-unit balance          | Slice by vertical deliverables: platform bootstrap, persistence/auth wiring, protected query, then tests/hardening. |

## Phase 1: Executable platform baseline in `server/smp`

- [x] 1.1 Update `server/smp/build.gradle.kts` and `server/smp/src/main/resources/application.yaml`
  with JWT resource-server, Liquibase, R2DBC/Postgres, test, and platform hook configuration
  placeholders.
- [x] 1.2 Create bounded-context roots under
  `server/smp/src/main/kotlin/com/profiletailors/smp/{platform,identity,tenancy,authorization,credentials,governance}/**`
  and align `SmpApplication.kt` package scanning to those seams.
- [x] 1.3 Implement `platform/application` CQRS contracts in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/**` for `Command`,
  `Query`, `Mediator`, handler interfaces, and principal/resource-context provider ports.
- [x] 1.4 Implement `platform/infrastructure` Spring wiring in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/**` for mediator
  dispatch, request context storage, Problem Details mapping, and audit/metrics/rate-limit hook
  interfaces.
- [x] 1.5 Implement identity and credentials seams in
  `server/smp/src/main/kotlin/com/profiletailors/smp/{identity,credentials}/**` for `PrincipalType`,
  `PrincipalContext`, JWT-backed principal materialization, and provider-neutral token validation
  boundaries.
- [x] 1.6 Implement tenancy core in `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/**`
  for `Workspace`, `WorkspaceOwnership`, `WorkspaceMembership`, active-workspace resolution, and
  explicit `ResourceContext(WORKSPACE)` handling.
- [x] 1.7 Implement authorization core in
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/**` for `PermissionKey`,
  roles-as-permission-compositions, membership-role resolution, default-deny decisions, and seams
  for direct grants/deny/scopes/entitlements.
- [x] 1.8 Create Liquibase baseline in `server/smp/src/main/resources/db/changelog/**` for
  principals, user identities, workspaces, workspace_ownerships, workspace_memberships, permissions,
  roles, role_permissions, and membership_roles.
- [x] 1.9 Add R2DBC repositories/adapters in
  `server/smp/src/main/kotlin/com/profiletailors/smp/{identity,tenancy,authorization}/infrastructure/**`
  to load principal, membership, ownership, and effective role-permission facts for phase one.
- [x] 1.10 Implement the proving slice in
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/**` and
  `.../infrastructure/http/**`: `GetCurrentWorkspaceAccessSummaryQuery`, handler, protected
  endpoint, explicit workspace-id input, and permission check for `workspace:access:read`.

## Phase 2: Phase-one hardening and proof

- [ ] 2.1 Add architecture/modulith tests in
  `server/smp/src/test/kotlin/com/profiletailors/smp/architecture/**` to enforce bounded-context
  dependency rules from the design.
- [ ] 2.2 Add unit tests in `server/smp/src/test/kotlin/com/profiletailors/smp/platform/**` for
  mediator dispatch, context-provider behavior, and request hook ordering.
- [ ] 2.3 Add unit tests in `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/**` for
  explicit permission matching, role composition, default deny, and ownership-versus-membership
  semantics.
- [ ] 2.4 Add WebFlux/Spring Security integration tests in
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/**` for valid JWT + workspace +
  permission success, missing workspace rejection, non-member denial, and member-without-permission
  denial.
- [ ] 2.5 Add minimal governance diagnostics in
  `server/smp/src/main/kotlin/com/profiletailors/smp/governance/**` and tests proving protected
  authorization outcomes can emit audit-ready facts without full audit persistence.

## Phase 3: Deferred authorization breadth

- [ ] 3.1 Extend `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/**` and
  `db/changelog/authorization/**` with executable direct grants and explicit deny rules.
- [ ] 3.2 Add scope-reduction and ABAC-ready policy seams in
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/**` without breaking explicit
  permission contracts.
- [ ] 3.3 Add entitlement contracts and feature-gate evaluation in
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/**` separate from principal
  permission checks.

## Phase 4: Deferred credential and platform expansion

- [ ] 4.1 Add service-account and API-key runtime flows in
  `server/smp/src/main/kotlin/com/profiletailors/smp/{identity,credentials}/**` plus matching
  migrations/tests.
- [ ] 4.2 Add audit persistence/retrieval in
  `server/smp/src/main/resources/db/changelog/governance/**` and
  `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/**`.
- [ ] 4.3 Add safe caching, invalidation, and rate-limit adapters in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/**` for permission and
  membership lookups.

## Phase 5: Deferred federation and maturity

- [ ] 5.1 Add richer OIDC/provider federation mapping in
  `server/smp/src/main/kotlin/com/profiletailors/smp/{credentials,identity}/infrastructure/**` while
  preserving repo-local principal semantics.
- [ ] 5.2 Add revocation/rotation workflows, compliance reporting seams, and broader operational
  observability across `server/smp/src/main/kotlin/com/profiletailors/smp/**`.
