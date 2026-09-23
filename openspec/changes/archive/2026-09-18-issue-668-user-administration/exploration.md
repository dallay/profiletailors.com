## Exploration: GitHub issue #668 — Back Office user administration

### Current State

Issue #668 is still **OPEN** (GitHub URL: `https://github.com/dallay/profiletailors.com/issues/668`;
linked Linear comment: `DALLAY-572`). No branch, commit, or pull request in the checked repository
history is attributable to #668/DALLAY-572. The current worktree `backoffice` is clean, has no local
commits ahead of `origin/main`, and is four commits behind `origin/main`; the checked-out code is
the issue baseline, not an implementation branch.

*Inspection date: 2026-09-17. This section is the pre-implementation baseline snapshot: it describes
the repository state before any #668 implementation work began.*

The issue has no dedicated active OpenSpec change directory. Existing active changes are
invitation-related (`dallay-565`, `dallay-567`, `dallay-568`, private-beta readiness, and a hotfix).
Existing authorization and audit changes for the platform-admin surface are archived, not #668 work:

- `openspec/changes/archive/2026-09-07-dallay-563-administrative-authorization-boundary/`
  established the permission-based admin boundary and default deny.
- `openspec/changes/archive/2026-09-09-dallay-562-administrative-audit-event-infrastructure/` and
  the live `openspec/specs/platform-admin-audit/spec.md` establish the `platformadmin` audit
  persistence/redaction seam.
- `openspec/changes/dallay-568-direct-invitation-admin-commands/` is a separate active invitation
  change, currently at apply/next qa, and must not be reused as the #668 change.

Existing admin user **queries and read UI are partially present**:

- Backend model/port/query:
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/model/AdminUserModels.kt`,
  `application/query/AdminQueries.kt`, `application/contracts/AdminUserQuery.kt`,
  `infrastructure/persistence/R2dbcAdminUserQuery.kt`.
- HTTP read endpoints:
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminUserController.kt`
  exposes `GET /api/admin/users`, `GET /api/admin/users/{principalId}`, and
  `GET /api/admin/users/{principalId}/workspaces`.
- The list supports pagination, sort, exact normalized email filtering, `status` filtering, and
  creation-time bounds. The current `status` query is explicitly a workaround: it maps to
  `principals.principal_type` because principals have no lifecycle status today
  (`R2dbcAdminUserQuery.kt`, current line 26 onward). It is **not** an administrative account-state
  implementation.
- Detail currently contains principal identity, created time, authentication methods, platform
  roles, and workspace memberships. The persisted identity schema has `principals.created_at`,
  `user_identities.email`, `user_identities.username`, and `user_identities.email_status`
  (`server/smp/src/main/resources/db/changelog/identity/001-create-principals.yaml`,
  `002-create-user-identities.yaml`, `004-add-email-verification.yaml`). It has no
  account-status/disabled field.
- Admin UI routes/views exist at `apps/web/admin/src/router/index.ts`, `src/views/UsersView.vue`,
  and `src/views/UserDetailView.vue`. The UI can browse/search by email and display
  identity/workspace data, but has no disable/enable/revoke-session controls or account-state
  display. Its client-side permissions currently include only `platform.users.read` and
  `platform.users.workspaces.read` for user operations.

Existing authorization is a foundation, not the #668 command contract:

- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformPermission.kt`
  contains `USERS_READ` and `USERS_WORKSPACES_READ`; there are no explicit user-control permissions
  for disable, enable, or session revocation.
- `OperatorAccessResolver` resolves active platform roles; controllers enforce permissions
  server-side through `effectivePermissions()` and `PlatformAccessDeniedException`
  (`platformadmin/application/OperatorAccessResolver.kt`,
  `platformadmin/domain/PlatformAdminExceptions.kt`,
  `platformadmin/infrastructure/http/AdminProblemDetailsHandler.kt`). Archived admin-authorization
  specs define the default-deny model.
- The frontend mirrors role permissions in `apps/web/admin/src/stores/auth.store.ts`, but frontend
  guards cannot replace server-side enforcement.

Existing sessions/authentication provide useful seams but do not enforce disabled accounts:

- `RefreshSessionGateway` already declares `revokeAllForPrincipal(principalId, now)` and
  `R2dbcRefreshSessionGateway` implements it with
  `UPDATE refresh_sessions SET status = 'REVOKED' ... WHERE principal_id = ... AND status = 'ACTIVE'`.
- `RefreshSessionLifecycleService.revokeAllForPrincipal()` delegates to that gateway; this is
  currently used by password security flows, not platform-admin user controls.
- `LoginUserHandler` validates the password and then issues a new JWT/refresh session without
  checking an administrative account state (`identity/application/LocalAuthHandlers.kt`, lines
  316–350). `RefreshUserSessionHandler` rotates a refresh session, loads identity facts, and issues
  a new JWT without checking account state (`LocalAuthHandlers.kt`, lines 352 onward).
  `R2dbcRefreshSessionGateway.requireActive()` checks only refresh-session status, expiry, and token
  verifier; no principal account status is consulted.
- The refresh-session table is `refresh_sessions` with principal FK, status, expiry, and revocation
  timestamps
  (`server/smp/src/main/resources/db/changelog/credentials/003-create-refresh-sessions.yaml`). A
  disable operation can reuse the existing revoke-all mechanism, but the atomicity/order with
  account-state mutation is an unresolved design point that must be specified and tested.

Existing audit infrastructure is reusable but missing #668 event types and handlers:

- `AdminAuditAction` currently includes role changes, waitlist/invitation operations, and read-view
  events only; it does not include `USER_DISABLED`, `USER_ENABLED`, or `USER_SESSIONS_REVOKED`
  (`platformadmin/domain/AdminAuditEvent.kt`).
- `AdministrativeAuditPublisher` and `R2dbcAdminAuditRepository` are the live audit seam;
  `platform-admin-audit` currently governs metadata redaction, not the user-control contract.
- `AdminProblemDetailsHandler` logs access-denied events, but the repository has no concrete metric
  implementation for the documented `platformadmin_operator_access_total`; the default `MetricsHook`
  is `NoOpMetricsHook` (`observability/domain/ObservabilityHooks.kt`,
  `observability/infrastructure/ObservabilityBootstrapConfiguration.kt`). The only current
  observability documentation entry is `docs/observability-contracts.md` line 25. Issue #668's
  disable/enable/revoke counters and failed-authorization metric therefore require an explicit
  metric owner/name/labels decision; do not infer them from the documentation placeholder.

Existing tests cover the read baseline and generic admin authorization, but not #668 behavior:

-

`server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminUserControllerTest.kt`
covers 401, 403, list filters, pagination limit, detail 404/403, and workspace permission behavior.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/R2dbcAdminUserQueryPostgresIntegrationTest.kt`
covers listing, status/type filter, email filter, pagination/sorting, invalid pagination, detail,
and workspace membership retrieval.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcRefreshSessionGatewayTest.kt`
covers create/resolve, rotate, and revoke of an individual session, but not `revokeAllForPrincipal`
(codegraph found no covering test).

- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`
  covers local auth flows, but the current login/refresh behavior has no disabled-account scenarios.
- `server/smp/src/test/resources/features/platform-admin.feature` covers admin access control and
  waitlist/invitation behavior; it contains no user-administration scenarios. No dedicated #668
  feature, BDD glue, UI tests, or OpenSpec delta spec exists.

Product and architecture constraints checked:

- `apps/web/PRODUCT.md` and `apps/web/admin/PRODUCT.md` identify the platform operator/admin SPA as
  an internal operations surface for user administration and audit review; the admin surface
  requires `platform.*` permissions and separate auth store.
- `openspec/config.yaml` confirms `openspec-only` persistence, strict TDD, backend
  JUnit/WebFlux/Security, Cucumber/Testcontainers, Vitest, Playwright, and the repository's relevant
  `just` recipes. No production code was changed.
- `openspec/specs/iam/spec.md` keeps Identity, Credentials, Authorization, Tenancy, Governance, and
  Platform as separate bounded contexts and defines refresh-session invalidation semantics. It does
  not define administrative account disable/enable state.
- Backend dependency direction remains `domain <- application <- infrastructure`; account state
  belongs to identity/domain/application contracts, refresh-session persistence belongs to
  credentials infrastructure, and admin endpoints/queries/commands belong to `platformadmin`
  adapters/application while depending inward on ports.

### Affected Areas

-

`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/model/AdminUserModels.kt` —
add only the operational fields that the approved contract defines; current model lacks account
status and verification/registration naming needed by the issue.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/query/AdminQueries.kt`
and `application/contracts/AdminUserQuery.kt` — existing read query seams; list/search semantics and
detail shape need a precise contract, including whether email search is exact or partial and how
account status filters.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/command/AdminCommands.kt`
plus new cohesive command handlers/ports under `platformadmin/application` — likely home for
disable/enable/revoke orchestration, permission checks at HTTP boundary or existing handler
convention, audit publication, and refresh-session port calls.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminUserController.kt` —
existing read controller; issue-preferred mutation routes are `POST /api/admin/users/{id}/disable`,
`/enable`, and `/sessions/revoke`.

- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformPermission.kt` —
  add explicit control permissions only after deciding whether the contract uses one
  `platform.users.manage` permission or separate disable/enable/revoke permissions; update role
  mappings and the admin SPA mirror.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/AdminAuditEvent.kt`,
  `application/contracts/AdministrativeAuditPublisher.kt`, and existing audit persistence/listener
  tests — add and verify `USER_DISABLED`, `USER_ENABLED`, and `USER_SESSIONS_REVOKED`, including
  safe metadata and success/failure semantics.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity` — introduce administrative account
  status as a first-class state and enforce it in login and refresh. Current
  `PrincipalIdentityFacts`/`R2dbcPrincipalIdentityLookup` seam can carry status, but the persistence
  model/migration and domain ownership are not present.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/RefreshSessionGateway.kt`,
`RefreshSessionLifecycleService.kt`, and
`credentials/infrastructure/R2dbcRefreshSessionGateway.kt` — reuse/verify revoke-all behavior and
expose it through an inward-facing port; add focused unit/integration coverage.

- `server/smp/src/main/resources/db/changelog/identity/` — add the account-state persistence
  migration only after the state name/default/index/transition rules are approved.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminProblemDetailsHandler.kt`
and observability hooks/metrics configuration — define how failed authorization attempts are counted
without duplicating or weakening the existing 403 handler.

- `apps/web/admin/src/views/UsersView.vue`, `UserDetailView.vue`, `src/stores/auth.store.ts`,
  `src/i18n/index.ts`, and route/view tests — display status and expose safe, permission-gated
  action controls; preserve server enforcement and accessible confirmation/error states.
-

`server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminUserControllerTest.kt`,
new command-handler tests, identity/auth tests, `R2dbcRefreshSessionGatewayTest.kt`, Postgres
integration tests, `server/smp/src/test/resources/features/platform-admin.feature` plus BDD glue —
mandatory failing-first tests for each externally observable contract and regression.

- `openspec/specs/iam/spec.md`, `openspec/specs/admin-authorization/spec.md`,
  `openspec/specs/platform-admin-audit/spec.md`, and likely a new issue-specific delta spec under
  the change — reconcile durable account-state, authorization, audit, and session semantics rather
  than treating existing specs as sufficient.
- `docs/observability-contracts.md` and potentially an ADR — update only if metric names/labels or a
  cross-context atomicity decision becomes durable; do not document placeholders as implemented.
- `Justfile`/existing recipes — no new recipe is currently required; use the authoritative existing
  recipes from `just -l` and the package/Gradle commands specified in `openspec/config.yaml` after
  implementation.

### Approaches

1. **Minimal platform-admin command slice over existing identity/credentials seams** — Add
   first-class account status to identity persistence/lookup, expose narrow admin command
   ports/handlers, call the existing refresh-session revoke-all operation, add explicit admin
   routes/permissions/audit events, and extend the existing admin UI.
    - Pros: preserves hexagonal bounded contexts; reuses
      `RefreshSessionLifecycleService.revokeAllForPrincipal()` and current admin query/UI seams;
      supports TDD and incremental tests; avoids broad mutable-user editing.
    - Cons: requires a new identity migration and cross-context application port composition; must
      settle atomic disable+revoke consistency; current login/refresh seams need changes;
      observability ownership is not currently concrete.
    - Effort: High

2. **Centralized account-control service in `platformadmin` that directly updates identity and
   refresh-session tables** — Put status mutation and session revocation in one admin
   adapter/service with SQL across tables.
    - Pros: can make the disable operation appear atomic in one DB transaction; fewer public ports
      initially.
    - Cons: violates the repository's hexagonal and bounded-context ownership rules; couples
      platformadmin to identity/credentials persistence details; difficult to reuse for non-admin
      account policy; likely conflicts with architecture tests and IAM spec.
    - Effort: High

3. **Global authentication filter/account-status check plus admin mutation endpoints** — Check
   disabled status in JWT/refresh authentication infrastructure, while the admin command updates
   status and revokes refresh sessions.
    - Pros: centralizes enforcement for all credential paths, including already-issued JWT requests
      if policy requires it; reduces chance of a login/refresh path omission.
    - Cons: scope is larger and may change existing bearer/JWT behavior; account-status lookup on
      every request can add latency and caching/invalidation concerns; does not eliminate the need
      for login/refresh checks; requires a clear distinction between access-token acceptance and
      refresh/login denial.
    - Effort: High

### Recommendation

Proceed with Approach 1, but only after an upstream proposal/spec decision settles the missing
contract points. Keep the administrative mutation API explicit and narrow, model account status as a
first-class identity-owned administrative state, and use an inward-facing credentials
port/application service for `revokeAllForPrincipal`. Enforce disabled status before issuing new
sessions in both login and refresh; treat refresh-session revocation as an explicit side effect of
disable and verify it with integration tests. Keep account-state mutation, session revocation, audit
publication, and response semantics within a transaction/consistency design approved by the design
phase rather than inventing a direct SQL shortcut.

The initial implementation should not expand into account deletion, email changes, ownership
transfer, impersonation, or manual verification, which the issue explicitly excludes. Existing
`workspaceId`/workspace membership behavior is unrelated to account status; no evidence in this
repository connects the remembered workspaceId->404 decision to #668, so it is intentionally not
carried into this exploration.

### Risks

- **Contract ambiguity:** the issue names operational fields but does not specify API response
  shapes, pagination/search semantics, status enum values, idempotency behavior, not-found/conflict
  mapping, or whether mutation authorization uses one or multiple permissions.
- **Atomicity/race:** disabling and revoking refresh sessions must not leave a window where a
  disabled account can refresh or where status changes without sessions being revoked. Transaction
  boundaries and concurrent login/refresh behavior require explicit design and tests.
- **Existing data migration:** all existing principals need a safe default status; the migration
  must preserve current accounts and avoid treating email verification (`email_status`) as account
  lifecycle state.
- **Authentication policy scope:** the issue says disabled accounts cannot authenticate or refresh,
  but does not say whether already-issued access JWTs are immediately rejected, nor how OAuth/API
  keys/service accounts are affected. Do not assume local-password-only scope without a decision.
- **Authorization granularity:** separate permissions for reading, mutating account state, and
  revoking sessions are not defined. Role mapping changes can accidentally grant dangerous actions
  to support/auditor roles.
- **Audit failure semantics:** the issue requires audit events but does not state whether a mutation
  fails if audit publication fails, nor whether rejected authorization attempts are themselves audit
  records or only metrics/logs.
- **Observability design:** metric names, labels, backend registry/implementation, and cardinality
  limits are not specified. The current documented platform-admin metric is only a contract
  placeholder and the runtime default is no-op.
- **UI safety:** disable/enable/revoke actions need confirmation, pending/error/idempotency
  behavior, and accessible status feedback; no product copy or API error contract exists yet.
- **Review size:** this is a cross-context feature spanning identity, credentials, platformadmin,
  audit, observability, backend tests/BDD, and admin UI. The implementation should be forecast
  against the 400 changed-line review budget and split into reviewable slices if the task phase
  estimates high risk.

### Ready for Proposal

No — exploration is complete, but proposal/spec/design must first resolve the contract decisions
above. The orchestrator should ask the user/product owner to decide at minimum:

1. The account-status enum/default and whether it applies to USER principals only or all principal
   types.
2. Exact list/search/detail API shapes, including partial versus exact email search, status filters,
   and 404 behavior for unknown principal IDs.
3. Permission model: one `platform.users.manage` permission versus distinct
   disable/enable/session-revoke permissions, and which roles receive it.
4. Authentication scope: whether disabled users' existing access JWTs are rejected immediately, and
   whether OAuth/API-key/service-account paths are included.
5. Disable/enable/revoke idempotency and response semantics, including whether revoking zero active
   sessions still emits `USER_SESSIONS_REVOKED`.
6. Audit and metrics failure semantics/names, including whether unauthorized attempts are
   metrics-only or also persisted audit events.
7. Transaction/consistency guarantee for account-state mutation plus refresh-session revocation.

### Verification Evidence

- `gh api repos/dallay/profiletailors.com/issues/668` — issue remains OPEN; body matches the
  requested scope.
- `git status --short --branch`, `git rev-list --left-right --count HEAD...origin/main`,
  `git log --all --grep='668'/'admin.*user'` — no #668 implementation branch/commit; clean baseline
  is four commits behind origin/main.
- `just -l` — command hub inspected; no production/test command was run because this phase is
  read-only exploration.
- `openspec/config.yaml`, relevant product files, IAM/admin-authorization/platform-admin-audit
  specs, existing change states, backend/frontend source and tests — inspected as listed above.
