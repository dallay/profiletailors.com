# Design: Back Office Operational Configuration — Registration Mode

## Technical Approach

New `identity`-owned port `RegistrationModeGateway` backs both reads (`RegistrationPolicy.evaluate()`,
now `suspend`) and the admin write path — same cross-context shape `platformadmin` already uses for
`AccountStateGateway` via `UserControlHandlers`. New `RegistrationModeHandlers` mirrors
`UserControlHandlers` 1:1 (permission → `transactionRunner.runAtomically` → audit). Concurrency is a
single-statement `UPDATE...FROM...RETURNING`, not the `Invitation`/`WaitlistEntry` CAS pattern.
`PropertyBackedRegistrationPolicy.kt` is deleted; its one behavior (`properties.mode.evaluate()`)
becomes the new gateway's no-row fallback branch.

Per ADR-0022, persistence uses one shared `platform_operational_config` key/value table (not a
`registration_configuration` singleton table) so a second operational value never requires a second
migration/table. `RegistrationModeGateway` reads/writes only the `registration.mode` key through this
shared table; it does not expose the table itself outside `identity`.

## Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Concurrency | Single `UPDATE...FROM...RETURNING`, not `updateIfVersionMatches` CAS | CAS exists to reject a client acting on a stale **client-fetched-earlier** read; our POST carries no "expected previous mode." Spec wants last-write-wins with atomic previous/new capture, not rejection. One statement = one row lock = no retry loop. |
| Table shape | Shared `platform_operational_config` key/value table, not one table per value | ADR-0022: RFC frames "operational configuration" as a category (registration mode is the first value, not the only one). A per-value table forces a second migration/port/review for every future value; a shared key/value table with a CHECK-constrained key/value pair extends additively. |
| `PropertyBackedRegistrationPolicy.kt` | Delete, inline fallback in new gateway | Two `@Service` `RegistrationPolicy` beans is an ambiguous Spring DI error; keeping it as dead pass-through adds no value. |
| Port location | New port in `identity`, consumed by `platformadmin` | `identity` owns `RegistrationMode`; `platformadmin` already imports identity ports directly (`AccountStateGateway`) — no new dependency direction. |
| Idempotency store | New store mirroring `UserControlIdempotencyStore`, minus `targetPrincipalId` | Config change has no target principal; reusing the user-control table would force a meaningless placeholder value. |
| SpringDoc | None | Verified 0 of 8 `platformadmin/infrastructure/http/*Controller.kt` use `@Operation`/`@Tag`. Follow the established convention. |
| ADR | ADR-0022, written and accepted | Covers "DB-persisted, admin-mutable, read-through, no-cache config" as a durable pattern, not a one-off; see `docs/architecture/adr/0022-durable-admin-mutable-operational-configuration.md`. |

## `suspend` Migration — Every Call Site

`RegistrationPolicy.evaluate()`: `fun` → `suspend fun`.

| Call site | Fix |
|---|---|
| `RegisterUserHandler.handle` (`LocalAuthHandlers.kt:96,98`) | None — already `suspend fun handle`. |
| `GetPublicCapabilitiesHandler.handle` (`PublicCapabilities.kt:36`) | None — already `suspend fun handle`. |
| `PropertyBackedRegistrationPolicy` | Deleted, not patched in place. |
| `bddRegistrationPolicy` SAM lambda (`CommonBddTestConfiguration.kt:169-170`) | None — `fun interface` SAM conversion accepts a plain lambda for a `suspend` method; body only calls non-suspend `RegistrationMode.evaluate()`. |
| Inline `RegistrationPolicy{...}` SAM lambda (`PublicCapabilitiesHandlerTest.kt:19`) | None — same SAM rule. |
| `FakeRegistrationPolicy` explicit `override fun` (`LocalAuthHandlersTest.kt:1407-1409`) | **Required**: → `override suspend fun evaluate` — the only real compile break (explicit override, not SAM-converted). |

## Liquibase Migration

New `db/changelog/identity/010-create-platform-operational-config.yaml`, included in
`db.changelog-master.yaml` after `009-add-account-state.yaml`. Mirrors `009-add-account-state.yaml`'s
CHECK-constraint style and `platform-admin/008-...`'s zero-comment `createTable` style. Table is
shared/extensible per ADR-0022: `config_key` is the primary key (not a numeric singleton id), so a
future second operational value is a new seed row, not a new table.

```yaml
databaseChangeLog:
  - changeSet:
      id: identity-010-create-platform-operational-config
      author: identity
      changes:
        - createTable:
            tableName: platform_operational_config
            columns:
              - column: {name: config_key, type: varchar(64), constraints: {primaryKey: true, nullable: false}}
              - column: {name: config_value, type: varchar(64), constraints: {nullable: false}}
              - column: {name: version, type: bigint, defaultValueNumeric: 0, constraints: {nullable: false}}
              - column: {name: updated_at, type: timestamp with time zone, defaultValueComputed: CURRENT_TIMESTAMP, constraints: {nullable: false}}
        - sql:
            sql: >
              ALTER TABLE platform_operational_config
              ADD CONSTRAINT ck_platform_operational_config_registration_mode
              CHECK (config_key <> 'registration.mode' OR config_value IN ('OPEN','INVITE_ONLY','CLOSED'));
        - insert:
            tableName: platform_operational_config
            columns:
              - {column: {name: config_key, value: registration.mode}}
              - {column: {name: config_value, value: CLOSED}}
      rollback:
        - dropTable: {tableName: platform_operational_config}
```

Seed `CLOSED` matches both the code default and the actual production default
(`infra/apps/smp/production/.env.example: SMP_REGISTRATION_MODE=CLOSED`) — no behavior change on
migrate. `currentMode()`'s fallback to `properties.mode` is defense-in-depth only, not the primary
seed mechanism (no Liquibase-parameter-injection precedent exists in this repo to read the live
Spring property at migration time). The CHECK constraint is scoped to the `registration.mode` key
specifically (`config_key <> 'registration.mode' OR ...`) so a future key with a different allowed-value
set adds its own `OR`-guarded clause via a new changeSet, without touching this one.

`RegistrationModeGateway`'s write always targets `WHERE config_key = 'registration.mode'` — it never
reads or writes any other key, keeping the shared-table blast radius scoped per port.

## Permission Wiring

`PlatformPermission.kt`: add `CONFIGURATION_READ("platform.configuration.read")`,
`CONFIGURATION_MANAGE("platform.configuration.manage")`. `PLATFORM_ROLE_PERMISSIONS`:
`PLATFORM_OWNER` gets both (already `entries.toSet()`); add `CONFIGURATION_READ` to
`PLATFORM_OPERATOR` and `AUDITOR`; `SUPPORT_AGENT` unchanged (neither).

## Audit Wiring

`AdminAuditAction.CONFIGURATION_CHANGED` added. Audit target uses the existing
`targetType`/`targetId` fields (not a new metadata key) to identify which configuration key
changed: `targetType = "CONFIGURATION"`, `targetId = "registration.mode"` — this generalizes to
future operational-configuration keys without adding a per-key metadata field. `metadata` carries
only the value transition: `mapOf("previousMode" to ..., "newMode" to ...)`.

**Redaction pitfall avoided, not just checked**: a metadata key literally named `configKey` would
match the `key` substring in the redaction denylist (`password|secret|token|key|credential|auth|bearer`,
case-insensitive) and silently redact the very value the audit trail needs to show. Using
`targetId` for the key name sidesteps this — `targetId` is never passed through `redact()` (only
`metadata` values are). `previousmode`/`newmode` themselves contain none of the denylist substrings
and stay unredacted correctly.

`RegistrationModeHandlers.changeMode`: permission denial publishes `REJECTED` (mirrors
`UserControlHandlers.requirePermission`); invalid mode string publishes `FAILED` with `reason` — new
`InvalidRegistrationModeException(value): IllegalArgumentException` reuses the existing
`@ExceptionHandler(IllegalArgumentException::class)` → 400 `VALIDATION_ERROR`, **no new handler**;
success publishes `SUCCEEDED` with the `previousMode`/`newMode` metadata above, captured atomically
by `RETURNING`. Add a case to `RedactSensitiveMetadataTest.kt` asserting `previousMode`/`newMode`
pass through unredacted, and a case confirming `targetId` values are never subject to redaction
(only `metadata` is).

## HTTP Contract

New `AdminConfigurationController.kt`, `@RequestMapping("/api/admin/configuration")`, no SpringDoc.

| Route | Auth | Status | Notes |
|---|---|---|---|
| `GET /registration-mode` | `CONFIGURATION_READ` | 200,401,403 | `RegistrationModeResult(mode: String)` |
| `POST /registration-mode` | `CONFIGURATION_MANAGE` | 200,400,401,403 | Body `ChangeRegistrationModeRequest(mode: String)`; requires `Idempotency-Key` (same `requireIdempotencyKey` as `AdminUserController`); invalid value validated **inside the handler after the permission check** (not `AdminOperatorController.assignRole`'s pre-permission `runCatching{}` shortcut) so the 400 path is audited `FAILED` per spec |

New `ConfigurationIdempotencyStore`/`Service` (`platformadmin/application/ConfigurationIdempotency.kt`)
+ table `db/changelog/platform-admin/010-create-configuration-idempotency.yaml`, mirroring
`platform-admin/008-create-user-control-idempotency.yaml`.

## File Changes

| File | Action |
|---|---|
| `identity/application/RegistrationPolicy.kt` | Modify: `suspend` |
| `identity/application/RegistrationModeGateway.kt` | New: port + `RegistrationModeChange` |
| `identity/infrastructure/R2dbcRegistrationModeGateway.kt` | New: adapter + `DatabaseBackedRegistrationPolicy` |
| `identity/infrastructure/PropertyBackedRegistrationPolicy.kt` | Delete |
| `db/changelog/identity/010-...yaml` + master include | New |
| `platformadmin/domain/{PlatformPermission,AdminAuditEvent,PlatformAdminExceptions}.kt` | Modify |
| `platformadmin/application/command/AdminCommands.kt` | Modify: `ChangeRegistrationModeCommand` |
| `platformadmin/application/handler/RegistrationModeHandlers.kt` | New |
| `platformadmin/application/ConfigurationIdempotency.kt` | New |
| `platformadmin/infrastructure/http/AdminConfigurationController.kt` | New |
| `db/changelog/platform-admin/010-...yaml` + master include | New |
| `.../persistence/RedactSensitiveMetadataTest.kt` | Modify: add case |
| `.../features/platform-admin.feature` + new glue | Modify/New |
| `apps/web/admin/src/router/{nav-registry,index}.ts` | Modify: planned→live, real route |
| `apps/web/admin/src/router/nav-registry.spec.ts` | Modify: drop from planned assertions |
| `apps/web/admin/src/views/ConfigurationView.vue` | New |
| `apps/web/admin/src/stores/auth.store.ts` | Modify: mirror both keys |
| `apps/web/admin/src/i18n/{index,types}.ts` | Modify: `configuration.*` EN+ES |

## Frontend

`ConfigurationView.vue` mirrors `UserDetailView.vue`'s composition (not `WaitlistEntryView.vue`'s
custom modal): `ref` state, `authStore.request(...)`, `crypto.randomUUID()` `Idempotency-Key`,
`window.confirm(t('configuration.changeConfirm', {mode}))` before POST — same blast-radius
confirmation UX as disable-user/revoke-sessions. Read gated on `platform.configuration.read`; write
control only if `hasPermission('platform.configuration.manage')`. New i18n namespace
`configuration`: `title`, `currentMode`, `changeTo`, `changeConfirm`, `changeSuccess` (EN+ES),
reusing `common.error`/`common.loading`. `auth.store.ts` `ROLE_PERMISSIONS`: add
`platform.configuration.read` to `PLATFORM_OWNER`/`PLATFORM_OPERATOR`/`AUDITOR`; add
`platform.configuration.manage` to `PLATFORM_OWNER` only.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | Handler permission/validation/audit branches; `FakeRegistrationPolicy` suspend fix | Mirror `LocalAuthHandlersTest` |
| Unit | Redaction non-match for `previousMode`/`newMode` | Add case to `RedactSensitiveMetadataTest.kt` |
| Integration | Gateway no-row fallback, atomic previous/new capture, concurrent writes | New test, Testcontainers Postgres |
| BDD | Read/write/deny/audit/redeploy-durability | Extend `platform-admin.feature`, `@smoke @platform-admin @fast @postgres` |
| Frontend | Permission gating, confirm-before-write, nav promotion | Vitest + `nav-registry.spec.ts` update |

## BDD Scenarios (→ `platform-admin.feature` + new `ConfigurationBddSteps.kt`)

1. Read permission holder reads current mode → 200
2. No read permission → 401/403, no value disclosed
3. Owner changes mode → 200, persisted, next `evaluate()` observes it
4. Non-owner write → 403, audited `REJECTED`, no state change
5. Invalid value from owner → 400, audited `FAILED`, no state change
6. Concurrent writes: no lost update, audit pair matches actual transition
7. Redeploy/restart does not revert an admin-set mode

## Open Questions

Both prior open questions are resolved:

- ~~New ADR~~ — **Resolved**: ADR-0022 written and accepted
  (`docs/architecture/adr/0022-durable-admin-mutable-operational-configuration.md`), README index
  updated. Covers the shared-table pattern for any future operational configuration value, not just
  registration mode.
- ~~`smallint` singleton vs. extensible key~~ — **Resolved**: table is
  `platform_operational_config(config_key PK, config_value, version, updated_at)`, extensible from
  the start per ADR-0022. `registration.mode` is the first seeded key; a future operational value is
  a new row plus its own owning-port adapter, not a new table.

No open questions remain for `sdd-tasks`.
