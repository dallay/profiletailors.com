# Apply Progress — #672 Registration Mode Config (PR 3)

## Layer

PR 3 of `feature-branch-chain` — BDD coverage + admin frontend (final PR).

| Field | Value |
|-------|-------|
| Trunk | `main` |
| Parent branch (PR 2 target) | `issue-672-admin-api` |
| Base | `issue-672-admin-api` |
| Branch | `issue-672-bdd-ui` (current) |
| Position | 3 of 3 (top of stack) |
| Delivery strategy | `feature-branch-chain` |
| Scope implemented | Phase 3 (BDD) + Phase 4 (admin frontend) + Phase 5 light pass (5.1, 5.2) |

## Completed Tasks (this batch)

- [x] 3.1 Extended `platform-admin.feature` with 7 `@platform-configuration` scenarios
- [x] 3.2 New `ConfigurationBddSteps.kt` glue (writes into shared `PlatformAdminScenarioState`)
- [x] 3.3 `just backend-bdd-fast` (267 tests, 0 failed) and `just backend-bdd-postgres` (same suite,
  dedicated Postgres Testcontainer, 0 failed) — both green, zero regressions on the other 28
  pre-existing `platform-admin.feature` scenarios
- [x] 4.1 `auth.store.ts` + test: `platform.configuration.read`
  (OWNER/OPERATOR/AUDITOR) and `platform.configuration.manage` (OWNER only)
- [x] 4.2 `nav-registry.ts` + `router/index.ts` + `nav-registry.spec.ts`: `configuration` promoted
  `planned` → `live`, gated on `platform.configuration.read`, routed to `ConfigurationView.vue`
- [x] 4.3 New `ConfigurationView.spec.ts` (7 tests: loading, read-only render, write-control
  gating, confirm-before-write, Idempotency-Key, success, two error paths)
- [x] 4.4 New `ConfigurationView.vue` (mirrors `UserDetailView.vue` composition)
- [x] 4.5 `i18n/{index,types}.ts`: `configuration.{title,currentMode,changeTo,changeConfirm,
  changeSuccess}` EN+ES
- [x] 4.6 `just admin-check` (vue-tsc clean), `just admin-test` (100/100), `just admin-build` (green)
- [x] 5.1 Reconciliation check: no companion `docs/platform-admin-*.md` created (majority precedent
  — 6 of 7 platformadmin capabilities have none; `design.md` + `specs/platform-configuration/
  spec.md` are the source of truth, consistent with that precedent)
- [x] 5.2 ADR-0022 citation check: README index row, `Related` section, and `Verification` section
  all consistent with the shipped shape — no ADR content edited

## Files Created

| File | Purpose |
|------|---------|
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/ConfigurationBddSteps.kt` | Step defs for the 7 new registration-mode BDD scenarios |
| `apps/web/admin/src/views/ConfigurationView.vue` | Read/write registration-mode admin view |
| `apps/web/admin/src/views/ConfigurationView.spec.ts` | Vitest spec (7 tests) |

## Files Edited

| File | Change |
|------|--------|
| `server/smp/src/test/resources/features/platform-admin.feature` | +7 `@platform-configuration` scenarios |
| `apps/web/admin/src/stores/auth.store.ts` | Added `platform.configuration.{read,manage}` to `ROLE_PERMISSIONS` |
| `apps/web/admin/src/stores/auth.store.test.ts` | +3 tests for the new permission matrix |
| `apps/web/admin/src/router/nav-registry.ts` | `configuration` entry: `status: 'planned'` → `'live'`, permission → `platform.configuration.read` |
| `apps/web/admin/src/router/index.ts` | Real route to `ConfigurationView.vue` (removed from the generic `plannedNavEntries()` mapping) |
| `apps/web/admin/src/router/nav-registry.spec.ts` | Updated planned/live assertions; added a `configuration`-specific live-entry assertion |
| `apps/web/admin/src/i18n/index.ts` | `configuration.*` EN+ES |
| `apps/web/admin/src/i18n/types.ts` | `configuration` schema |
| `openspec/changes/672-registration-mode-config/tasks.md` | Marked Phase 3, Phase 4, 5.1, 5.2 complete |

## Verification

| Command | Result |
|---------|--------|
| `:server:smp:compileTestKotlin` | PASS |
| `just backend-bdd-fast` (`:server:smp:bddFastTest`) | PASS — 267 tests, 0 failed (35/35 in `platform-admin.feature`, including the 7 new scenarios) |
| `just backend-bdd-postgres` (`:server:smp:bddPostgresTest`) | PASS — same suite against a dedicated Postgres Testcontainer, 0 failed |
| `:server:smp:detekt` | PASS |
| `:server:smp:spotlessCheck` | PASS |
| `pnpm --filter admin test:run` | PASS — 100/100 tests |
| `just admin-check` (`vue-tsc --build`) | PASS |
| `just admin-build` | PASS |
| `pnpm --filter admin lint` (biome check) | PASS |

## Not Run / Deferred to Orchestrator

- `just backend-check` (full backend check incl. Detekt across the whole module) — orchestrator
  runs the full gate separately per the task brief
- `just ci-full` / PostgreSQL integration suite beyond what's exercised by `bddPostgresTest`
- Push / PR creation — explicitly deferred to the orchestrator

## Next Steps

- Orchestrator: run `sdd-verify` against all 3 PRs' cumulative diff, then `sdd-qa`
- Orchestrator: run the full Definition-of-Done gate (`just backend-check`, `just ci`) before
  opening PR3
