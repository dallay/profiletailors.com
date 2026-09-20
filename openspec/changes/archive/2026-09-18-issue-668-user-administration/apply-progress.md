# Apply Progress: Back Office User Administration (#668)

## Delivery

- Strategy: `single-pr`
- Size exception: explicitly accepted by the user for one coherent branch/unit
- Branch/base: `backoffice` / `backoffice`
- Scope: verification correction batch after CRITICAL findings; no commit or push

## Completed

- [x] 1.1 Account state model defaults to `ACTIVE` and supports `DISABLED`; login/refresh regression tests cover disabled paths.
- [x] 1.2 Login and refresh reject disabled accounts before access-token/session issuance; refresh regression confirms zero rotation calls.
- [x] 1.3 Liquibase preserves `principals.status` and adds `principals.account_state` via a separate ordered migration; existing principal-status lifecycle remains intact.
- [x] 1.4 Credentials bulk revocation returns the count of active sessions revoked; PostgreSQL refresh/session integration remains covered.
- [x] 2.1 User control commands, result models, failure/idempotent-state behavior tests added.
- [x] 2.2 Disable/enable/session-revoke handlers compose Identity and Credentials under `AtomicTransactionRunner` and publish redacted audit outcomes.
- [x] 2.3 Durable idempotency store/service claims, replays, rejects operation/target scope conflicts, returns explicit `IDEMPOTENCY_KEY_IN_PROGRESS` for missing concurrent records or incomplete claims, and removes failed claims.
- [x] 2.4 `platform.users.manage` and `USER_DISABLED`, `USER_ENABLED`, `USER_SESSIONS_REVOKED` registered; owner/operator mapping preserved and read-only roles remain denied.
- [x] 3.1 Admin list/detail models and SQL expose user-only account/verification/principal status/version; list workspace counts and detail memberships/workspace roles/platform roles use existing read ports.
- [x] 3.2 Admin mutation routes enforce server permissions, require `Idempotency-Key`, and the workspace route requires both `platform.users.read` and `platform.users.workspaces.read`; missing users return 404.
- [x] 3.3 Audit outcomes and bounded user-control metrics are implemented and covered.
- [x] 4.1 Fresh BDD scenarios and glue cover detail memberships, workspace authorization, controls, replay, and permission denial.
- [x] 4.2 Admin UI uses `accountState`, `disable`/`enable`/`sessions/revoke`, current i18n labels, manage guards, and idempotency headers.
- [x] 4.3 Admin Playwright remains not applicable: no admin Playwright project, configuration, or fixtures exist.
- [x] 5.1 API versioning, operations, migration, and rollback documentation updated.
- [x] 5.2 Original focused backend, PostgreSQL, migration, BDD, and frontend verification evidence retained.
- [x] 4.1 Verify follow-up: refresh checks account state before rotation.
- [x] 4.2 Verify follow-up: admin query maps user-only workspace and role data through existing ports.
- [x] 4.3 Verify follow-up: workspace endpoint requires both read permissions.
- [x] 4.4 Verify follow-up: idempotency claim races return explicit in-progress conflict.
- [x] 4.5 Verify follow-up: user-control metrics have bounded operation/outcome and authorization-failure labels.
- [x] 4.6 Verify follow-up: unauthenticated mutations emit redacted rejected audit events.
- [x] 4.7 Verify follow-up: fresh BDD runtime passes.
- [x] Apply correction: admin auth-store tests now assert `platform.users.manage`, remove obsolete publishing-stale expectations, and cover read-only denial.
- [x] Apply correction: UserDetailView fixtures/assertions now use `accountState`, `disable`/`enable`/`revoke sessions`, current i18n labels, and manage-permission visibility.
- [x] Apply correction: removed the extra EOF blank line from `AdminCommands.kt`.
- [x] Apply correction: fixed all changed Spotless/Detekt findings, including auth line wrapping, Modulith credentials application dependency, workspace repository/test line lengths, class ordering, and observability test decomposition.
- [x] Apply correction: aligned legacy deactivate/reactivate handler test expectations with retained `USER_DISABLED`/`USER_ENABLED` audit actions.

## Verification Evidence

- Admin full Vitest: PASS — 9 files, 82 tests.
- Admin type-check: PASS — `pnpm --filter @profiletailors/admin type-check`.
- Admin build: PASS — `pnpm --filter @profiletailors/admin build`.
- Backend quality: PASS — `just backend-check` completed successfully in 7m18s; Spotless and Detekt pass.
- Focused backend tests: PASS — selected Identity, user-control, idempotency, controller, and refresh-session tests.
- Fresh BDD: PASS — `./gradlew :server:smp:bddFastTest --no-daemon --console=plain --rerun-tasks`.
- PostgreSQL admin-query integration: PASS.
- PostgreSQL refresh-session integration: PASS.
- Liquibase migration test: PASS.
- Backend compile: PASS — `./gradlew :server:smp:compileKotlin`.
- Diff hygiene: PASS — `git diff HEAD --check`.

## Status

All assigned verification-correction tasks are complete. Current phase is `apply`; next phase is `verify`.
