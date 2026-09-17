# Tasks: Back Office User Administration (#668)

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~700–1,000 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 state; PR2 controls/API; PR3 UI/BDD/docs |
| Delivery strategy | single-pr |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: single-pr
400-line budget risk: High
Size exception: User explicitly selected one delivery branch/unit for the complete issue; implementation remains one coherent delivery unit.

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Identity/Credentials foundation | PR1 | `trunk=main`; `parent_branch=main`; `base=main`; `branch=issue-668-foundation`; `position=1`; #668 / DALLAY-572; rollback with migration |
| 2 | Controls/API/security/telemetry | PR2 | `trunk=main`; `parent_branch=issue-668-foundation`; `base=issue-668-foundation`; `branch=issue-668-controls`; `position=2`; #668 / DALLAY-572; depends PR1 |
| 3 | UI/BDD/docs/verification | PR3 | `trunk=main`; `parent_branch=issue-668-controls`; `base=issue-668-controls`; `branch=issue-668-ui`; `position=3`; #668 / DALLAY-572; depends PR2 |

## Phase 1: Foundation (TDD RED first)

- [x] 1.1 RED `identity/domain/PrincipalIdentityFacts.kt`, `LocalAuthHandlersTest`: default `ACTIVE` and `DISABLED`; done when missing-state tests fail.
- [x] 1.2 GREEN `identity/application/*`, `LocalAuthHandlers.kt`: lookup state before login/refresh; done when disabled paths issue neither token nor session; unit tests pass.
- [x] 1.3 RED/GREEN `identity/*.yaml`, `db.changelog-master.yaml`: constrained `principals.account_state`, default `ACTIVE`, safe backfill; done when migration preserves verification; PostgreSQL test passes.
- [x] 1.4 RED/GREEN `RefreshSessionGateway`, `RefreshSessionLifecycleService`, `R2dbcRefreshSessionGateway`: active-only revoke-all count; done with rotation compatibility; unit/integration tests pass.

## Phase 2: Domain/Application

- [x] 2.1 RED `platformadmin/application/{command,handler,model}` tests: disable/enable/revoke unknown target, zero sessions, idempotency, failure; done when explicit ports/results are required.
- [x] 2.2 GREEN handlers plus `AtomicTransactionRunner`: compose Identity and Credentials; done when disable cannot report partial success; handler/transaction tests pass.
- [x] 2.3 RED/GREEN idempotency port/adapter and `platform-admin/*.yaml`: key operator/command/target/header; done when replay avoids side effects/success audit; service and persistence implementation added. Durable replay scope is enforced by the operator/key unique index; in-progress replays return a conflict without repeating side effects.
- [x] 2.4 RED/GREEN `PlatformPermission.kt`, role mapping, `AdminAuditEvent.kt`: `USERS_MANAGE` and three actions/results; done owner/operator-only; domain tests pass.

## Phase 3: Infrastructure/API/Authorization/Audit/Metrics

- [x] 3.1 RED/GREEN `AdminUserModels.kt`, `R2dbcAdminUserQuery.kt`: state/verification/registration/workspaces and state-based `status`; done with exact email, sort, pagination, 404 tests.
- [x] 3.2 RED/GREEN `AdminUserController.kt`, `AdminProblemDetailsHandler.kt`: three POST routes, server permission, required `Idempotency-Key`, stable 401/403/404/5xx and v1 media type; WebFlux tests pass.
- [x] 3.3 RED/GREEN audit publisher/repository tests and handlers: control handlers now emit `REJECTED` audit events for unauthorized attempts and `FAILED` for operation errors; existing redaction seam preserved. Rejected and failed reasons are generic and do not expose target/error details.
- [x] 3.4 RED/GREEN metrics port/adapter: operation×outcome and auth-rejection counters; done with bounded non-sensitive labels; metrics tests pass.

## Phase 4: BDD/UI

- [x] 4.1 RED/GREEN `platform-admin.feature` and glue: added permission/control/idempotency scenarios and glue; fresh `bddFastTest --rerun-tasks` executes 256 scenarios successfully after aligning the workspace-permission scenario with the AUDITOR role, which has no workspace-read permission.
- [x] 4.2 RED/GREEN admin `UsersView.vue`, `UserDetailView.vue`, `auth.store.ts`, `lib/api.ts`, `i18n/*`: state, guarded accessible confirmation, pending/errors, keys; Vitest/type/lint/build pass.
- [x] 4.3 NOT APPLICABLE in the current repository: admin Playwright flow/denial coverage was not run because `apps/web/admin` has no Playwright project, configuration, or fixtures. Existing admin Vitest coverage exercises the implemented controls and permission denial; adding a runner or dependency is outside the approved scope.

## Phase 5: Documentation/Compatibility/Verification

- [x] 5.1 Added platform-admin user administration API/operations/migration documentation and linked it from API versioning docs.
- [x] 5.2 Focused backend/frontend/lint/build gates pass; focused PostgreSQL query test passes; fresh BDD runtime passes; no configured admin Playwright project exists to run.


## Phase 4: VERIFY follow-up corrections

- [x] 4.1 Refresh rejects disabled accounts before rotation; regression asserts zero rotation calls.
- [x] 4.2 Admin user list/detail query maps user-only workspace counts, memberships, workspace roles, and platform roles through existing ports.
- [x] 4.3 Workspace membership endpoint requires both user-read and workspace-read permissions.
- [x] 4.4 Idempotency claim races return the contractual in-progress conflict without nullable assertions.
- [x] 4.5 User-control authorization rejection and idempotency replay metrics use bounded labels; metric names include the required authorization-failure counter and idempotent outcome.
- [x] 4.6 Unauthenticated mutation requests emit redacted rejected audit events through the audit hook seam.
- [x] 4.7 Platform-admin BDD scenarios and step bindings cover detail memberships and workspace authorization; fresh BDD runtime passes with the unauthorized workspace-read scenario using AUDITOR.

## Phase 6: VERIFY correction batch

- [x] 6.1 Update admin auth-store tests to the current `platform.users.manage` contract and cover read-only denial.
- [x] 6.2 Update UserDetailView tests to the `accountState`, `disable`/`enable`/`sessions/revoke`, Idempotency-Key, and current i18n contracts while preserving permission visibility coverage.
- [x] 6.3 Remove the extra EOF blank line from `AdminCommands.kt`.
- [x] 6.4 Resolve changed Spotless/Detekt findings without suppressions, config changes, baselines, comments, or unsafe types; preserve legacy PrincipalStatus lifecycle and #668 UserAccountState controls.
- [x] 6.5 Re-run admin type-check, tests, build, focused backend tests, fresh BDD, PostgreSQL integration checks, `just backend-check`, and `git diff HEAD --check`.
