# Tasks: Back Office Operational Configuration — Registration Mode (#672)

## Review Workload Forecast

| Field                   | Value                                                                                        |
|-------------------------|----------------------------------------------------------------------------------------------|
| Estimated changed lines | ~550–800 (prod ~250, tests/BDD ~250–400, frontend ~100)                                      |
| 400-line budget risk    | High                                                                                         |
| Chained PRs recommended | Yes                                                                                          |
| Suggested split         | PR 1 identity/DB core → PR 2 platformadmin API/audit/idempotency → PR 3 BDD + admin frontend |
| Delivery strategy       | ask-on-risk                                                                                  |
| Chain strategy          | feature-branch-chain (confirmed by user 2026-09-19)                                          |

Decision needed before apply: Resolved — feature-branch-chain, 3 sequential PRs
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                                                    | Likely PR | Notes                                                                                                                                                                 |
|------|-------------------------------------------------------------------------|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | `identity` DB-backed registration mode gateway/policy + migration       | PR 1      | `trunk=main`; `parent_branch=main`; `base=main`; `branch=issue-672-identity-gateway`; `position=1`; #672; no external contract, safe standalone                       |
| 2    | `platformadmin` permissions/audit/handlers/idempotency/HTTP API         | PR 2      | `trunk=main`; `parent_branch=issue-672-identity-gateway`; `base=issue-672-identity-gateway`; `branch=issue-672-admin-api`; `position=2`; #672; depends on PR 1's port |
| 3    | BDD scenarios + admin frontend (`ConfigurationView`, nav, i18n) + gates | PR 3      | `trunk=main`; `parent_branch=issue-672-admin-api`; `base=issue-672-admin-api`; `branch=issue-672-bdd-ui`; `position=3`; #672; depends on PR 2's endpoints             |

## Phase 1: Identity — DB-Persisted Registration Mode (TDD RED first)

- [x] 1.1 RED: `identity/application/RegistrationPolicy.kt` `evaluate()` → `suspend fun`; confirm
  `LocalAuthHandlersTest.kt`'s `FakeRegistrationPolicy` (`override fun`) is the only real compile
  break.
- [x] 1.2 GREEN: `LocalAuthHandlersTest.kt` `FakeRegistrationPolicy` →
  `override suspend fun evaluate`; module compiles, existing tests pass.
- [x] 1.3 RED/GREEN new `identity/application/RegistrationModeGateway.kt`: port (`currentMode()`,
  `changeMode(newMode)`) + `RegistrationModeChange(previousMode, newMode)`.
- [x] 1.4 RED/GREEN `db/changelog/identity/010-create-platform-operational-config.yaml` + master
  include (after `009-add-account-state.yaml`):
  `platform_operational_config(config_key PK, config_value, version, updated_at)`, per-key CHECK
  scoped to `registration.mode`, seed `registration.mode = CLOSED`; Postgres migration test proves
  table/constraint/seed.
- [x] 1.5 RED integration test (Testcontainers Postgres): no-row fallback to `properties.mode`;
  single-statement `UPDATE...FROM...RETURNING`; two concurrent writers → exactly one winning mode,
  correct audit-ready pair each.
- [x] 1.6 GREEN `identity/infrastructure/R2dbcRegistrationModeGateway.kt`: implement
  `RegistrationModeGateway` + `DatabaseBackedRegistrationPolicy` (suspend adapter, read-through
  every call, no cache).
- [x] 1.7 Delete `identity/infrastructure/PropertyBackedRegistrationPolicy.kt` and its tests;
  confirm Spring context loads with a single `RegistrationPolicy` bean (no ambiguous-bean error).

## Phase 2: Platformadmin — Permissions, Audit, Handlers, Idempotency (TDD RED first)

- [x] 2.1 RED/GREEN `platformadmin/domain/PlatformPermission.kt`: add `CONFIGURATION_READ`/
  `CONFIGURATION_MANAGE`; wire `PLATFORM_ROLE_PERMISSIONS` (`OPERATOR`+`AUDITOR` get READ only;
  `OWNER` gets both via `entries.toSet()`; `SUPPORT_AGENT` neither); test asserts the exact matrix
  from `specs/admin-authorization/spec.md`.
- [x] 2.2 RED/GREEN `platformadmin/domain/AdminAuditEvent.kt`: add `CONFIGURATION_CHANGED` to
  `AdminAuditAction`.
- [x] 2.3 RED/GREEN `platformadmin/domain/PlatformAdminExceptions.kt`: add
  `InvalidRegistrationModeException(value): IllegalArgumentException`; test confirms existing
  `@ExceptionHandler(IllegalArgumentException::class)` → 400 `VALIDATION_ERROR` (no new handler).
- [x] 2.4 RED/GREEN `platformadmin/application/command/AdminCommands.kt`: add
  `ChangeRegistrationModeCommand(operatorId, newMode)`.
- [x] 2.5 RED `platformadmin/application/handler/RegistrationModeHandlersTest.kt` (new): read for
  `CONFIGURATION_READ`; write denied for non-`CONFIGURATION_MANAGE` →
  `PlatformAccessDeniedException` + `REJECTED`; invalid mode → `FAILED`, no state change; success
  runs in `transactionRunner.runAtomically`, publishes `SUCCEEDED` with
  `metadata={previousMode,newMode}`, `targetType="CONFIGURATION"`, `targetId="registration.mode"`.
- [x] 2.6 GREEN `platformadmin/application/handler/RegistrationModeHandlers.kt` (new): mirror
  `UserControlHandlers` 1:1 (permission → `runAtomically` → audit).
- [x] 2.7 RED/GREEN `platformadmin/application/ConfigurationIdempotency.kt` (new store+service, no
  `targetPrincipalId`) + `db/changelog/platform-admin/010-...yaml`, mirroring
  `UserControlIdempotencyStore`/`008-...yaml` minus target-principal; unit tests for
  claim/replay/in-progress-conflict + Postgres migration test.
- [x] 2.8 RED `platformadmin/infrastructure/http/AdminConfigurationControllerTest.kt` (new,
  WebFlux): GET requires `CONFIGURATION_READ` (401/403/200); POST requires `CONFIGURATION_MANAGE` +
  `Idempotency-Key` (mirrors `AdminUserController`), validates mode value inside the handler after
  the permission check (so invalid-from-owner is audited `FAILED`); 200/400/401/403.
- [x] 2.9 GREEN `platformadmin/infrastructure/http/AdminConfigurationController.kt` (new): thin
  controller, `RegistrationModeResult`/`ChangeRegistrationModeRequest` DTOs, no SpringDoc (matches
  the other 8 controllers).
- [x] 2.10 RED/GREEN `platformadmin/infrastructure/persistence/RedactSensitiveMetadataTest.kt`: add
  case — `previousMode`/`newMode` pass `redact()` unredacted; add case — `targetId` values are never
  redacted (only `metadata` is).

## Phase 3: BDD Coverage

- [x] 3.1 RED extend `server/smp/src/test/resources/features/platform-admin.feature` with 7
  scenarios (read-ok→200; read-denied→401/403 no disclosure; owner-write→200 persisted+observed;
  non-owner-write→403 `REJECTED`; invalid-value→400 `FAILED`; concurrent-writes no lost update,
  correct audit pair; restart durability). Feature-level tags
  `@smoke @platform-admin @fast @postgres` apply (same file); scenario-level
  `@platform-configuration` added for organization.
- [x] 3.2 GREEN new
  `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/ConfigurationBddSteps.kt`: shared
  `PlatformAdminScenarioState`, `WebTestClient`, `Idempotency-Key` +
  `Accept: application/vnd.api.v1+json` headers, `BDD_ADMIN_TOKEN` fixture — drives all 7 scenarios
  green.
- [x] 3.3 Ran `just backend-bdd-fast` (267 tests, 0 failed) and `just backend-bdd-postgres` (same
  suite via a dedicated Postgres Testcontainer, 0 failed); all 7 new scenarios pass in both lanes,
  zero regressions on the other 28 `platform-admin.feature` scenarios.

## Phase 4: Admin Frontend

- [x] 4.1 RED/GREEN `apps/web/admin/src/stores/auth.store.ts` (+ its test): mirror
  `platform.configuration.read` (OWNER/OPERATOR/AUDITOR) and `platform.configuration.manage` (OWNER
  only) in `ROLE_PERMISSIONS`, matching the server matrix exactly.
- [x] 4.2 RED/GREEN `apps/web/admin/src/router/{nav-registry,index}.ts` + `nav-registry.spec.ts`:
  drop `configuration` from "planned" assertions; promote to `live`, gated on
  `platform.configuration.read`, real route to `ConfigurationView.vue`.
- [x] 4.3 RED new `apps/web/admin/src/views/ConfigurationView.spec.ts`: read-only render for
  read-only sessions; write control + `window.confirm` gated on `platform.configuration.manage`;
  `Idempotency-Key` via `crypto.randomUUID()`; success/error/loading states.
- [x] 4.4 GREEN new `apps/web/admin/src/views/ConfigurationView.vue`: mirror `UserDetailView.vue`
  composition (`ref` state, `authStore.request`, `crypto.randomUUID()` Idempotency-Key,
  `window.confirm(t('configuration.changeConfirm', {mode}))` before POST).
- [x] 4.5 GREEN `apps/web/admin/src/i18n/{index,types}.ts`: add
  `configuration.{title,currentMode,changeTo,changeConfirm,changeSuccess}` EN+ES, reusing
  `common.error`/`common.loading`.
- [x] 4.6 Ran `just admin-check`, `just admin-test` (100/100 passed) and `just admin-build`; green,
  no new suppressions, no new `any`/type errors.

## Phase 5: Reconciliation, Docs, Gates

- [x] 5.1 Checked `docs/api-versioning*.md` and `docs/README.md` admin doc index. Confirmed: only
  `docs/platform-admin-user-administration.md` (#668) has a companion route-enumeration doc + a
  matching "Platform admin user controls" cross-reference section in `docs/api-versioning.md`; the
  other 6 platformadmin HTTP controllers (waitlist, invitations, operators, audit, dashboard,
  publishing-stale-jobs) have zero such docs and rely on `design.md`/`specs/*/spec.md` as the source
  of truth. Following that majority precedent, no new `docs/platform-admin-configuration.md` or
  `api-versioning.md` subsection was added for `AdminConfigurationController`; the two new routes
  are fully documented in this change's `design.md` (HTTP Contract table) and
  `specs/platform-configuration/spec.md`.
- [x] 5.2 Citation-only check: `docs/architecture/adr/README.md` row 0022 → status Accepted, correct
  filename. ADR-0022's own `Related` section correctly cites
  `openspec/changes/672-registration-mode-config/` and issue #672. ADR-0022's `Verification` section
  (seed/fallback, concurrent-writes, BDD durability scenario) matches what Phase 1/3 actually
  implemented. No ADR content edited, per instructions.
- [ ] 5.3 Run `just backend-check` (Detekt included); fix any new finding without suppression,
  baseline growth, or config downgrade.
- [ ] 5.4 Run `just backend-build` and `just admin-build`; confirm `pnpm-lock.yaml`/Gradle catalog
  unchanged (no new dependency).
- [ ] 5.5 Full confirmation: `just backend-test-postgres`, `just backend-bdd-postgres`,
  `just admin-check`, `just admin-test`, `just admin-build`; record exact Passed/Failed/Not run per
  repo Definition of Done.
