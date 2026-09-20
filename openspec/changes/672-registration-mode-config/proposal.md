# Proposal: Back Office Operational Configuration — Registration Mode (#672)

## Intent

Registration mode (`OPEN`/`INVITE_ONLY`/`CLOSED`) binds once at startup from `SMP_REGISTRATION_MODE`
(`RegistrationConfigurationProperties`). Changing it requires redeploying the single-replica,
`stop-first` `backend` service — causing downtime and silently reverting any manual runtime patch on
the next deploy. Expose it as a DB-persisted, Back Office-mutable value so `PLATFORM_OWNER` can
change it live, promoting the already-`planned` `configuration` nav entry.

## Scope

### In Scope
- Single-row DB-persisted mode, read-through on every `RegistrationPolicy.evaluate()` call (no
  cache, matching current behavior), seeded/falling back to
  `RegistrationConfigurationProperties.mode` to preserve fail-closed-to-`CLOSED`
- GET (read) + POST (change) Back Office endpoints mirroring `AdminUserController` /
  `UserControlHandlers`: `Idempotency-Key`, `OperatorAccessResolver`,
  `PlatformAccessDeniedException`, `transactionRunner.runAtomically`, `AdministrativeAuditPublisher`
- Atomic single-statement `UPDATE ... RETURNING` (or optimistic-lock version column) to avoid a
  read-modify-write race and capture previous+new value for the audit record atomically
- New permissions `platform.configuration.read` (OWNER+OPERATOR) and
  `platform.configuration.manage` (OWNER-only WRITE — mirrors `operators.manage`)
- New `AdminAuditAction.CONFIGURATION_CHANGED`
- Promote admin nav `configuration` entry `planned`→`live`; new `ConfigurationView.vue`
- BDD: read, owner-write success, operator-write denied, redaction of `previousMode`/`newMode`
  verified (not assumed)

### Out of Scope
- Config beyond registration mode; no generic multi-value config framework
- Removing `SMP_REGISTRATION_MODE` (stays as seed/fallback)
- Changing `OPEN`/`INVITE_ONLY`/`CLOSED` evaluation semantics
- A caching layer (rejected — matches existing zero-cache precedent)

## Capabilities

### New Capabilities
- `platform-configuration`: Back Office read/write surface for operational config; registration
  mode is the first value

### Modified Capabilities
- `registration`: mode source of truth becomes DB-persisted, admin-mutable at runtime; properties
  become seed/fallback only
- `admin-authorization`: two new permission keys, OWNER-only write gate
- `platform-admin-audit`: new `CONFIGURATION_CHANGED` action

## Approach

Add a single-row `registration_configuration` table (mode + version/updated-at for CAS). New
DB-backed `RegistrationPolicy` adapter reads through per `evaluate()` call, falling back to
`RegistrationConfigurationProperties.mode` when no row exists — chosen over an in-memory holder
(reverts on every `stop-first` redeploy) or a cached read (staleness cost for one rarely-changed
value). `RegistrationPolicy.evaluate()` is currently a synchronous `fun interface` SAM; the adapter
requires it to become `suspend` — a deliberate, reviewed port change.

New `RegistrationModeHandlers` in `platformadmin/application` mirrors `UserControlHandlers`:
resolve operator via `OperatorAccessResolver`, deny non-OWNER writes with
`PlatformAccessDeniedException`, run the atomic `UPDATE ... RETURNING previous_mode` inside
`transactionRunner.runAtomically`, publish `AdminAuditEvent(CONFIGURATION_CHANGED, metadata =
{previousMode, newMode})`. Controller stays translate-only, reusing existing `Idempotency-Key`
conventions.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `identity/infrastructure/RegistrationConfigurationProperties.kt`, `PropertyBackedRegistrationPolicy.kt` | Modified | Becomes seed/fallback only |
| `identity/application/RegistrationPolicy.kt` | Modified | `evaluate()` becomes `suspend` |
| `identity/infrastructure` (new) | New | DB-backed adapter + Liquibase migration (table + seed) |
| `platformadmin/domain/PlatformPermission.kt` | Modified | Add `CONFIGURATION_READ`/`CONFIGURATION_MANAGE`; wire role map (manage=OWNER only) |
| `platformadmin/domain/AdminAuditEvent.kt` | Modified | Add `CONFIGURATION_CHANGED` |
| `platformadmin/application/handler` (new) | New | `RegistrationModeHandlers` |
| `platformadmin/infrastructure/http` (new/modified) | New/Modified | GET/POST endpoints |
| `apps/web/admin/src/router/nav-registry.ts` | Modified | `configuration`: planned→live, permission→`platform.configuration.read` |
| `apps/web/admin/src/views/ConfigurationView.vue` | New | Read/mutate UI |
| `.../features/platform-admin.feature` + glue | Modified/New | BDD coverage |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Read-modify-write race on concurrent writes | Med | Atomic `UPDATE ... RETURNING` capturing previous+new in one statement |
| Redaction false match/miss on mode keys/values | Low | Test asserting keys/OPEN/INVITE_ONLY/CLOSED stay unredacted |
| No DB row on first deploy | Low | Migration seeds from `SMP_REGISTRATION_MODE`; adapter falls back if absent |
| OWNER-only write narrows OPERATOR vs. other surfaces | Low | Deliberate, documented (mirrors `operators.manage`) |
| Sync→suspend port change ripples to callers/tests | Med | Confirm call sites/test doubles updated in design/tasks |
| Durable runtime-mutable config may need an ADR | Med | Decide in design phase; track as a design task |

## Rollback Plan

Revert migration (drop table) and code in one PR. `RegistrationPolicy` reverts to
`PropertyBackedRegistrationPolicy`; `SMP_REGISTRATION_MODE` stays authoritative — no data loss.

## Dependencies

- Builds on already-live `admin-authorization`, `platform-admin-audit`, `registration` capabilities

## Success Criteria

- [ ] `PLATFORM_OWNER` changes mode via Back Office with no redeploy; effective on next `evaluate()`
- [ ] `PLATFORM_OPERATOR` and below cannot write mode (denied + audited `REJECTED`)
- [ ] Concurrent writes never lose an update (test-proven atomic capture)
- [ ] A `backend` redeploy does not revert an admin-set mode
- [ ] Audit records `previousMode`/`newMode` unredacted; BDD scenarios pass
- [ ] `configuration` nav entry is `live`; `ConfigurationView` renders the current mode
