# Verification Report — 672-registration-mode-config

- change: `672-registration-mode-config` (GitHub issue dallay/profiletailors.com#672, epic #656)
- mode: openspec
- verified at: 2026-09-20, on `main` @ `72d474e8` (squash-merge of #1105, which contains the full 3-PR chain content)
- delivery note: #1102 (identity gateway) merged 2026-09-20T10:19Z; #1106 (BDD+UI) merged into `issue-672-admin-api`
  2026-09-20T10:20Z; #1105 squash-merged the whole stack into `main` 2026-09-20T11:48Z. Commit `dca2cb07` (#1106)
  is not a direct ancestor of `main`, but `git show 72d474e8 --stat` proves all PR3 files (BDD glue, 7 scenarios,
  `ConfigurationView.vue`, nav promotion, i18n) landed on `main` via #1105. Verified artifact is `main` worktree.
- verdict: **PASS WITH WARNINGS**

## Completeness (tasks.md)

| Phase | Tasks | Status | Evidence |
|---|---|---|---|
| 1 identity gateway + migration | 1.1–1.7 | Done | `R2dbcRegistrationModeGateway.kt`, `RegistrationModeGateway.kt`, `RegistrationPolicy.kt` (`suspend`), migration `identity/010-create-platform-operational-config.yaml`, `PropertyBackedRegistrationPolicy.kt` deleted |
| 2 platformadmin API/audit/idempotency | 2.1–2.10 | Done | `PlatformPermission.kt`, `AdminAuditEvent.kt`, `PlatformAdminExceptions.kt`, `AdminCommands.kt`, `RegistrationModeHandlers(.kt/Test)`, `ConfigurationIdempotency(.kt/ServiceTest)`, `AdminConfigurationController(.kt/Test)`, `RedactSensitiveMetadataTest` case |
| 3 BDD | 3.1–3.3 | Done | 7 `@platform-configuration` scenarios + `ConfigurationBddSteps.kt`; re-run locally, see below |
| 4 admin frontend | 4.1–4.6 | Done | `auth.store.ts`, `nav-registry.ts`/`index.ts`, `ConfigurationView.vue` + spec, i18n EN+ES; re-run locally, see below |
| 5 reconciliation/gates | 5.1, 5.2 | Done | no companion doc per 6-of-7 precedent; ADR-0022 citations consistent |
| 5 gates | 5.3–5.5 | Partial | full `backend-check`/`backend-build`/`admin-build`/`bdd-postgres` NOT re-run locally on `main`; covered by remote CI on the exact squash commit (see W1) |

## Build / Tests / Coverage evidence (local = this session on `main`, remote = GitHub Actions on #1105)

| Gate | Result | Scope |
|---|---|---|
| `:server:smp:bddFastTest --rerun-tasks` (local, 14m12s) | PASS — 267 tests, 0 failures, 0 errors, 0 skipped | full BDD suite on `main` |
| `platform-admin.feature` (local, from same run) | PASS — 35/35 (28 pre-existing + 7 new) | regression + new scenarios |
| `:server:smp:detekt :server:smp:spotlessCheck --rerun-tasks` (local, 55s) | PASS, no findings, no suppressions/baseline growth | static analysis + format |
| `just admin-test` (local, 20.9s) | PASS — 104/104 across 12 files | admin Vitest (100 at branch time + 4 from later main PRs) |
| `just admin-check` / `vue-tsc --build` (local) | PASS, clean | admin type-check |
| Remote CI on #1105 squash (`✅ CI Gate`, `🔨 Backend BDD`, `🐘 Backend Postgres`, `🧪 Backend Unit Tests`, `🔨 Production Builds`, `🧪 Frontend Unit Tests`, `Quality Gate`, semgrep/codeql/trivy/gitleaks/SonarCloud) | all SUCCESS | full gate incl. postgres + builds on exact merged content |
| Coverage gate | Not-run | no coverage command executed; quality-gate remote check passed at merge time |
| `just backend-check` (full), `just backend-build`, `just admin-build`, `just backend-bdd-postgres` on `main` | Not-run locally | env/time cost; equivalent lanes green remotely (see W1) |

## Spec compliance matrix (requirement → evidence, all runtime-proven unless noted)

| Spec requirement / scenario | Implementation | Covering test (passed) |
|---|---|---|
| platform-configuration: authorized read → 200 with OPEN/INVITE_ONLY/CLOSED | `AdminConfigurationController.getRegistrationMode` + `RegistrationModeHandlers.currentMode` (CONFIGURATION_READ gate) | BDD "Authorized read returns the current registration mode" (bddFastTest 35/35) + `AdminConfigurationControllerTest` (remote Backend Unit Tests SUCCESS) |
| platform-configuration: unauthorized read → 401/403, no disclosure | `resolveOperator() ?: 401`; handler throws `PlatformAccessDeniedException` → 403 | BDD "Unauthorized read … denied without disclosure" |
| platform-configuration: owner write → 200, persisted, next `evaluate()` observes | `changeRegistrationMode` (Idempotency-Key) → `changeMode` → atomic `UPDATE…FROM…RETURNING` | BDD "Owner changes the registration mode and the change is observable on the next read" |
| platform-configuration: denied/invalid writes → 403/400, no change, no SUCCEEDED audit | permission check before `parseMode`; invalid → `InvalidRegistrationModeException : IllegalArgumentException` → existing handler → 400 `VALIDATION_ERROR`; both audited (REJECTED/FAILED) | BDD non-owner-write + invalid-value scenarios; `RegistrationModeHandlersTest` |
| platform-configuration: atomic concurrent writes, audit pair matches transition | single-statement `WITH previous … FOR UPDATE / UPDATE … RETURNING previous/new` in `R2dbcRegistrationModeGateway.changeMode` | BDD "Concurrent writes never lose an update"; Testcontainers gateway integration test (remote Postgres lane SUCCESS) |
| platform-configuration: durability across restart/redeploy | DB-persisted `platform_operational_config.registration.mode`, seed CLOSED = production default | BDD "Admin-set mode survives a restart-equivalent fresh request" |
| platform-configuration: audit SUCCEEDED unredacted previousMode/newMode; REJECTED audited | `targetType="CONFIGURATION"`, `targetId="registration.mode"`, `metadata={previousMode,newMode}`; REJECTED published before throw | BDD audit assertions; `RedactSensitiveMetadataTest` new case (unredacted) |
| admin-authorization: permission matrix (OWNER both, OPERATOR+AUDITOR read, SUPPORT_AGENT neither, manage OWNER-only) | `PlatformPermission.kt`: OWNER=`entries.toSet()`, OPERATOR+READ, AUDITOR+READ, SUPPORT_AGENT neither | `PlatformPermissionTest` (remote SUCCESS); frontend `auth.store.test.ts` +3 |
| admin-authorization: frontend mirror equals server; configuration nav live gated on own permission; forced nav still server-denied | `ROLE_PERMISSIONS` mirror verified line-equal; `nav-registry.ts` `configuration` status `live`, permission `platform.configuration.read`; server 401/403 independent of client | `nav-registry.spec.ts` live-entry assertion; `ConfigurationView.spec.ts` (7 tests); BDD deny scenarios |
| platform-admin-audit: CONFIGURATION_CHANGED SUCCEEDED/REJECTED/FAILED, success only after commit, atomic pair | `AdminAuditAction.CONFIGURATION_CHANGED`; success published after `runAtomically`; pair from `RETURNING` | handler unit tests + BDD audit assertions |
| registration: DB source of truth, read-through no cache, property seed/fallback, default CLOSED | `currentMode()`: SELECT → row else `properties.mode`; migration seeds CLOSED; no cache | gateway integration tests (fallback/override); BDD durability scenario |
| registration: `evaluate()` suspend, runtime-mutable, redeploy-safe | `suspend fun evaluate`; `DatabaseBackedRegistrationPolicy` read-through; single `RegistrationPolicy` bean | `LocalAuthHandlersTest` suspend fix; full unit lane remote SUCCESS; BDD owner-write-observed scenario |

## Correctness table

| Check | Result |
|---|---|
| Every spec scenario has a covering test that passed at runtime | Yes — 7/7 BDD scenarios green locally; unit/integration lanes green remotely on merged content |
| `Accept: application/vnd.api.v1+json` contract | BDD glue sends it (`CONFIGURATION_API_V1` const); controller under `/api/admin/configuration` versioned stack |
| Hexagonal dependency rule (`domain <- application <- infrastructure`) | New port `RegistrationModeGateway` in identity application, consumed by platformadmin handlers; R2DBC adapter in infrastructure; detekt green |
| No weakened tests, no new suppressions/`any`/baseline entries, no config downgrades | Confirmed by inspection of merged diff stats + clean detekt/spotless/vue-tsc runs |
| Idempotency-Key enforced on POST, invalid mode audited FAILED (validation after permission check) | Confirmed in controller + handler source; covered by controller/handler tests |
| ADR-0022 exists and matches shipped shape | `docs/architecture/adr/0022-durable-admin-mutable-operational-configuration.md` present; citation check 5.2 done |

## Design coherence table

| Design decision | Shipped as designed |
|---|---|
| Single-statement `UPDATE…FROM…RETURNING`, no CAS | Yes (`CHANGE_MODE_SQL`) |
| Shared `platform_operational_config` key/value table, per-key CHECK | Yes (migration 010 + scoped constraint) |
| Delete `PropertyBackedRegistrationPolicy`, fallback inside gateway | Yes (file absent, fallback branch present) |
| Suspend migration with exactly one test-code fix | Yes (`suspend fun evaluate`; SAM lambdas unchanged) |
| Dedicated `ConfigurationIdempotency` store (no targetPrincipalId) + migration 010 | Yes |
| Audit via `targetType/targetId`, redaction-safe metadata | Yes; `configKey` metadata key avoided per design |
| No SpringDoc on new controller | Yes (matches 8 siblings) |
| Frontend mirrors `UserDetailView` composition (confirm + randomUUID key) | Yes (`window.confirm`, `crypto.randomUUID()`) |

## Issues

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| Full `just backend-check` / `backend-build` / `admin-build` / `bdd-postgres` not re-run locally on `main` post-merge | ✅ | ✅ | WARNING | Accepted — equivalent lanes SUCCESS in remote CI on the exact squash commit; local bdd-fast + detekt + spotless + admin-test/check re-run green |
| Coverage not measured locally | ✅ | ✅ | WARNING (informational) | INFO — remote Quality Gate passed at merge; no coverage regression signal |
| 2 untracked files in worktree (`openspec/changes/archive/.../post-archive-amendments-2026-09-16.md`, `openspec/specs/e2e/bulk-waitlist-invitation-test-plan.md`) | ✅ | ✅ | SUGGESTION | Pre-existing, unrelated to #672; left untouched |
| #1106 merged into chain branch rather than `main` directly (history nuance) | ✅ | ❌ | SUGGESTION | INFO — content fully present on `main` via #1105 squash; no action |

No CRITICAL issues. No spec scenario is UNTESTED or FAILING.

## Gaps and risks

- Residual risk is limited to post-merge `main` drift (#1109/#1110 touched web shared layers): mitigated by this session's
  fresh local runs (admin-test 104/104, vue-tsc clean, bdd-fast 267/267) on current HEAD.
- `SMP_REGISTRATION_MODE` remains seed/fallback by design; operators must not expect env edits to override an admin-set value.
- Handoff: technical conformance only. Operator acceptance (live Back Office walkthrough) belongs to `sdd-qa` (`qa-report.md`).

## Verdict

**PASS WITH WARNINGS** — implementation matches specs, design, and all tasks except the explicitly deferred full-gate
re-runs (5.3–5.5), which are covered by remote CI SUCCESS on the merged content plus fresh focused local evidence.
Recommended next: `sdd-qa` acceptance, then archive.
