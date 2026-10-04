# Design: Back Office User Administration

## Technical Approach

Extend the existing `platformadmin` read surface with CQRS command handlers and explicit routes.
Identity owns `UserAccountState`; Credentials owns refresh-session revocation; `platformadmin`
authorizes, orchestrates through ports, audits, and records bounded metrics. No cross-context SQL or
new admin model is introduced. Existing 401/403/404 conventions and `application/vnd.api.v1+json`
remain unchanged.

## Architecture Decisions

| Option                                         | Tradeoff                                                                                                                            | Decision                                                                                                                          |
|------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Store state on `principals`                    | One authoritative state for user authentication and admin queries; applies only to user principals through a constrained value/enum | **Chosen**: `account_state` on `principals`, default `ACTIVE`; non-user principals remain active-compatible.                      |
| Store state in `platformadmin`                 | Duplicates Identity ownership and makes login checks cross-context                                                                  | Rejected.                                                                                                                         |
| Let platformadmin update refresh tables        | Simple SQL, but violates bounded-context ownership and makes disable/revoke atomicity fragile                                       | Rejected. Use `RefreshSessionLifecycleService` port.                                                                              |
| One command transaction for state + revocation | Prevents reporting a successful partial disable; requires transaction-aware adapters                                                | **Chosen**: use `AtomicTransactionRunner`/R2DBC transaction boundary around Identity state update and Credentials bulk revoke.    |
| Revoke only refresh sessions                   | Preserves ADR-0009 stateless access-token behavior; bearer tokens expire normally                                                   | **Chosen**. No per-request Identity lookup or access-token blacklist.                                                             |
| Client-supplied idempotency key                | Explicit retry contract and duplicate-request protection, but requires storage                                                      | **Chosen**: require `Idempotency-Key` on mutation routes and persist request outcome keyed by operator, command, target, and key. |

## Data Flow

```text
Admin HTTP → permission resolver → command handler
                         │              ├─ Identity state port
                         │              ├─ Credentials revoke-all port
                         │              ├─ audit publisher
                         │              └─ metrics port
Vue admin ← ProblemDetail/command response ← committed transaction
Login/refresh → Identity state lookup → reject DISABLED before issuing/rotating tokens
```

## File Changes

| File                                                                                                            | Action        | Description                                                                                                                       |
|-----------------------------------------------------------------------------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/.../identity/domain/*`, `application/*`                                                             | Modify        | Add `UserAccountState`, state lookup/update ports, and enforce state in login and refresh before token/session issuance.          |
| `server/smp/.../credentials/application/RefreshSession*`, `infrastructure/R2dbcRefreshSessionGateway.kt`        | Modify        | Expose transactional bulk revoke returning affected count; retain active/rotated/revoked semantics.                               |
| `server/smp/.../platformadmin/application/{command,handler,contracts,model}`                                    | Modify/Create | Add disable, enable, and revoke-sessions commands/handlers, ports, result DTOs, and idempotency coordination.                     |
| `server/smp/.../platformadmin/infrastructure/http/{AdminUserController,AdminProblemDetailsHandler}.kt`          | Modify        | Add explicit POST routes and stable ProblemDetail mappings.                                                                       |
| `server/smp/.../platformadmin/domain/{PlatformPermission,AdminAuditEvent}.kt`                                   | Modify        | Add `USERS_MANAGE`, three audit actions, and role mapping.                                                                        |
| `server/smp/.../platformadmin/infrastructure/persistence/R2dbcAdminUserQuery.kt`                                | Modify        | Return account state/verification state and make `status` filter state-based; preserve exact normalized email and sort allowlist. |
| `server/smp/src/main/resources/db/changelog/{identity,platformadmin}/*.yaml`, `db.changelog-master.yaml`        | Create/Modify | Add account-state migration and idempotency record table/indexes; include in Liquibase master.                                    |
| `apps/web/admin/src/{stores/auth.store.ts,views/UsersView.vue,views/UserDetailView.vue,router/index.ts,i18n/*}` | Modify        | Add manage permission, state display, guarded disable/enable/revoke controls, confirmation/error handling, and idempotency keys.  |
| `server/smp/src/test/**`, `server/smp/src/test/resources/features/**`, `apps/web/admin/src/**/*.spec.ts`        | Create/Modify | Unit, WebFlux, PostgreSQL, security, BDD, and UI coverage.                                                                        |

## Interfaces / Contracts

- `POST /api/admin/users/{principalId}/disable`, `/enable`, and `/sessions/revoke`: authenticated
  platform operator with `platform.users.manage`; require `Idempotency-Key`; accept no sensitive
  body; return `200` with `{ principalId, accountState, revokedSessionCount }` for disable/enable
  and `{ principalId, revokedSessionCount }` for revoke. Repeated same-key requests replay the
  original response; same key with a different command/target returns `409 IDEMPOTENCY_KEY_REUSED`.
- Missing authentication is `401`; missing permission is `403`; unknown user is
  `404 USER_NOT_FOUND`; invalid principal/state transition is `409 USER_STATE_CONFLICT`; malformed
  key/target is `400 VALIDATION_ERROR`; unconfirmed transactional completion is
  `500 USER_CONTROL_FAILED`. Existing admin `ProblemDetail` shape and non-sensitive codes are used.
  Successful mutation is committed before `SUCCEEDED` audit; rejected/failed attempts emit exactly
  one `REJECTED`/`FAILED` event where operator context exists, with redacted metadata.
- Identity port: `findAccountState(principalId)` and
  `changeAccountState(principalId, expected, replacement)`. Credentials port:
  `revokeAllForPrincipal(principalId, now): Int`. Commands are user-principal-only; `status` is
  `ACTIVE|DISABLED`.
- Metrics: Micrometer counters `profiletailors.admin.user_control.requests` with fixed tags
  `operation={disable|enable|sessions_revoke}` and `outcome={success|rejected|failure|idempotent}`,
  plus `profiletailors.admin.user_control.authorization_failures` with `operation`. No principal,
  email, token, UA, IP, or correlation values are labels.

## Testing Strategy

| Layer       | What to Test                                                                                                      | Approach                                                                                                                            |
|-------------|-------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Unit        | State transitions, default state, idempotency, fail-closed disable, permission mapping, audit/metric outcomes     | JUnit/Kotlin fakes for Identity/Credentials/audit/metrics; include repeated commands and failures.                                  |
| Integration | Liquibase schema, atomic state+revoke, query DTO mapping, login/refresh rejection, ProblemDetail/status contracts | Existing PostgreSQL/Testcontainers and WebFlux tests; verify no token/session on disabled paths and revoked sessions cannot rotate. |
| BDD         | Authorized list/detail and all three commands; 401/403/404, retry replay, disabled login/refresh, audit evidence  | Add tagged `@platform-admin @user-administration @fast` feature and existing `PlatformAdminBddSteps`/`BddDatabaseSupport`.          |
| UI          | Permission visibility, state labels, confirmation, mutation success/error, stale refresh, retry key reuse         | Vitest view/store tests; Playwright critical admin control flow if the admin E2E lane is available.                                 |

## Migration / Rollout

Liquibase adds `account_state` with default `ACTIVE`, backfills principals, and enforces non-null;
existing users remain usable. Add permission mapping and deploy backend before enabling controls in
the UI. Roll back UI/routes first; retain the state column if rollback would lose state. No
workspace-404 behavior is changed.

## Open Questions

None blocking. Exact DB changelog filenames and concrete metrics adapter wiring are implementation
details for tasks, not product decisions.
