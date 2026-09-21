# Acceptance QA Report — 672-registration-mode-config

## 1. Identity

- change: `672-registration-mode-config` (GitHub issue dallay/profiletailors.com#672, epic #656)
- mode: openspec
- phase: qa (acceptance gate, after `sdd-verify`, before `sdd-archive`)
- date: 2026-09-20
- HEAD under test: `main` @ `72d474e8` (squash-merge of #1105; contains full 3-PR chain #1102/#1105/#1106)
- verdict: **PASS WITH WARNINGS**

## 2. Source artifacts and technical verification handoff

| Artifact | Status |
|---|---|
| `proposal.md` | Read. Success criteria (6 items) used as acceptance baseline |
| `specs/platform-configuration/spec.md` | Read. 8 scenarios traced |
| `specs/admin-authorization/spec.md` | Read. 4 scenarios traced |
| `specs/platform-admin-audit/spec.md` | Read. 3 scenarios traced |
| `specs/registration/spec.md` | Read. 6 scenarios traced |
| `design.md` | Read. HTTP contract, audit wiring, frontend composition checked |
| `tasks.md` | Read. Phases 1–4 done; 5.3–5.5 deferred to remote CI |
| `apply-progress.md` | Read. PR3 file/verification breakdown confirmed |
| `verify-report.md` | Read. **PASS WITH WARNINGS** on the identical commit `72d474e8` |
| `state.yaml` | Read. `current_phase: verify`, `next: qa` at session start |
| `openspec/config.yaml` | Read. Capability catalog + `evidence_policy` + `archive_blockers` applied |

Technical handoff (verify, same HEAD, no source drift since — `git status` shows only
`state.yaml` modified plus untracked report/plan files, zero production-code drift):

- Local `bddFastTest`: PASS — 267 tests, 0 failures; `platform-admin.feature` 35/35 (28 pre-existing + 7 new)
- Local detekt + spotlessCheck: PASS, clean
- Local `admin-test`: PASS — 104/104; `admin-check` (vue-tsc): PASS, clean
- Remote CI on the #1105 squash: all SUCCESS (CI Gate, Backend BDD, Backend Postgres,
  Backend Unit Tests, Production Builds, Frontend Unit Tests, Quality Gate, semgrep/codeql/trivy/gitleaks/SonarCloud)
- Not-run anywhere: coverage measurement; full `backend-check`/`backend-build`/`admin-build`/`bdd-postgres` locally

## 3. Target, environment, permissions, and limitations

- Target: **none supplied**. No deployed Back Office URL, no staging/prod credentials, no operator
  session was provided, and none is invented (per contract).
- Environment: QA executed in the repository worktree on `main` @ `72d474e8` (Linux, Node/pnpm +
  Gradle/JDK per repo recipes). No dev servers were started; no `infra-up` was run.
- Permissions: no operator roles to impersonate live; authorization is exercised through the
  executable suites (BDD fixtures with role tokens, Vitest permission-matrix tests).
- Limitations:
  - No live target ⇒ every scenario requiring a deployed Back Office, real browser, assistive
    technology, or real restart/redeploy is `NOT TESTED` (contract: no target ⇒ `NOT TESTED`).
  - Full backend BDD lane (~14 min) was **not re-executed** in this session: identical commit as
    verify's fresh run today, zero production-code drift, cost/benefit negative. Backend scenarios
    cite the handoff runtime evidence explicitly (commit, scope, counts) — never static inspection.
  - Focused frontend lane **was re-executed** in this session (see §5, QA-EXEC-01).

## 4. Capability inventory

Source: `openspec/config.yaml` `testing.capabilities` plus browser/API/data/a11y/responsive/locale/
persistence/exploratory dimensions required by the QA contract.

| Capability | Disposition | Rationale |
|---|---|---|
| `backend_bdd_fast` (Cucumber/Testcontainers) | **selected (inherited evidence)** | Fresh PASS on identical HEAD from verify handoff (267 tests, 35/35 feature). Observable API behavior via real HTTP stack. Not re-run in QA (cost, no drift) |
| `frontend_unit` (Vitest, admin) | **selected (executed now)** | QA-EXEC-01: 3 focused files, 35/35 PASS in-session. Observable UI behavior (render, gating, confirm, Idempotency-Key, i18n) |
| `backend_unit` (JUnit/MockK, handlers/permissions/redaction) | **selected (inherited evidence)** | Remote Backend Unit Tests SUCCESS on exact squash; `RegistrationModeHandlersTest`, `PlatformPermissionTest`, `RedactSensitiveMetadataTest` cover branches BDD exercises end-to-end |
| `backend_postgres_integration` | **rejected (inherited only)** | Remote Backend Postgres lane SUCCESS on exact squash; local `bdd-postgres` not re-run (needs `infra-up`, time). Durability/concurrency acceptance rests on BDD + remote lane |
| `frontend_e2e` (Playwright, real browser) | **unavailable in-session** | No target URL supplied; admin ConfigurationView has no Playwright spec in-repo. ⇒ `NOT TESTED` |
| Browser manual walkthrough (Back Office live) | **unavailable** | No target/credentials supplied. ⇒ `NOT TESTED` |
| Accessibility walkthrough (keyboard/screen-reader on live UI) | **unavailable** | Requires live target + AT; repo has no admin a11y E2E lane. ⇒ `NOT TESTED` |
| Responsive walkthrough | **rejected** | Admin SPA change adds no layout breakpoints; no responsive acceptance criterion in specs. Non-applicable with reason |
| Locale visual check (ES render in browser) | **unavailable** | Strings proven present + unit-rendered; visual ES confirmation needs live target. ⇒ `NOT TESTED` (P3) |
| Persistence across real restart/redeploy | **unavailable** | Requires deployed backend; BDD covers restart-*equivalent* fresh-request durability. Real redeploy ⇒ `NOT TESTED` |
| Exploratory/security probing beyond specs | **rejected** | Forced-nav case covered by spec scenario + server enforcement tests; open-ended probing needs live target. Non-applicable in-session with reason |
| `backend_coverage` (Kover) | **rejected** | Not run in verify either; remote Quality Gate passed. Coverage is not an acceptance criterion of #672 |
| `full_ci` | **rejected** | Broad builds intentionally avoided per mission (cost); exact-squash remote CI already green |

Runner-envelope note: no automated QA-runner envelope exists for this change; there is no runner
`FAIL`/`UNAVAILABLE` to map. No `fallback` beyond the documented inherit-vs-execute split above.

## 5. Scenario matrix (acceptance criteria → result + evidence)

Evidence legend: **QA-EXEC-01** = executed in this QA session
(`pnpm --filter admin test:run ConfigurationView.spec.ts auth.store.test.ts nav-registry.spec.ts` →
3 files PASS, 35 tests PASS, 2.73 s).
**VERIFY-HO** = verify handoff runtime on identical commit `72d474e8` (see §2).
**REMOTE-CI** = GitHub Actions SUCCESS on the #1105 squash (exact merged content).
Static inspection is cited only as traceability support, never as PASS evidence.

### 5.1 Platform-configuration (new capability)

| # | Scenario (spec) | Result | Evidence / reason |
|---|---|---|---|
| PC-01 | Authorized read returns current mode (200, one of OPEN/INVITE_ONLY/CLOSED) | PASS | VERIFY-HO: BDD "Authorized read returns the current registration mode" green within 35/35; glue sends `Accept: application/vnd.api.v1+json` (`ConfigurationBddSteps.kt:23,156`) |
| PC-02 | Unauthorized read denied 401/403, no disclosure | PASS | VERIFY-HO: BDD "Unauthorized read … denied without disclosure" green within 35/35 |
| PC-03 | Owner write 200, persisted, next `evaluate()` observes | PASS | VERIFY-HO: BDD owner-write-observed scenario green; suspend `evaluate()` read-through proven by owner-write→register-against-new-mode path |
| PC-04 | Denied/invalid writes: 403/400, no change, no SUCCEEDED audit | PASS | VERIFY-HO: BDD non-owner-write + invalid-value scenarios green; handler/controller unit branches green (REMOTE-CI) |
| PC-05 | Concurrent writes never lose update; audit pair matches transition | PASS | VERIFY-HO: BDD concurrent-writes scenario green; single-statement `UPDATE…FROM…RETURNING` + Testcontainers gateway integration green (REMOTE-CI postgres lane) |
| PC-06 | Durability across restart/redeploy (DB, not memory) | PASS (restart-equivalent) | VERIFY-HO: BDD "survives a restart-equivalent fresh request" green; migration seeds CLOSED. Real redeploy ⇒ NOT TESTED, see NT-01 |
| PC-07 | Successful change audited SUCCEEDED, previous/new unredacted | PASS | VERIFY-HO: BDD audit assertions green + `RedactSensitiveMetadataTest` new case green (REMOTE-CI) |
| PC-08 | Rejected write audited REJECTED, no state change | PASS | VERIFY-HO: BDD REJECTED-audit scenario green |

### 5.2 Admin-authorization (modified capability)

| # | Scenario (spec) | Result | Evidence / reason |
|---|---|---|---|
| AA-01 | Read granted to OWNER/OPERATOR/AUDITOR; matrix exact | PASS | QA-EXEC-01: `auth.store.test.ts` permission-matrix tests green; `PlatformPermissionTest` green (REMOTE-CI). Server map inspected: OWNER both, OPERATOR+READ, AUDITOR+READ, SUPPORT_AGENT neither — traceability only |
| AA-02 | Manage OWNER-only; non-owner write denied forbidden | PASS | QA-EXEC-01 + VERIFY-HO BDD deny scenario green |
| AA-03 | Frontend mirror equals server; nav `configuration` live gated on own `platform.configuration.read` | PASS | QA-EXEC-01: `nav-registry.spec.ts` live-entry assertion green; registry inspected `status: 'live'`, `permission: 'platform.configuration.read'` — traceability only |
| AA-04 | Forced hidden nav still server-denied 401/403 | PASS | VERIFY-HO: BDD deny scenarios exercise server enforcement independent of client routing |

### 5.3 Platform-admin-audit (modified capability)

| # | Scenario (spec) | Result | Evidence / reason |
|---|---|---|---|
| AU-01 | Success audited CONFIGURATION_CHANGED/SUCCEEDED unredacted | PASS | VERIFY-HO: BDD + handler unit + redaction case green; `targetType="CONFIGURATION"`/`targetId="registration.mode"` in `RegistrationModeHandlers.kt:101-102` — traceability only |
| AU-02 | Rejected control audited REJECTED, no state change | PASS | VERIFY-HO: BDD REJECTED scenario green |
| AU-03 | Invalid value audited FAILED, no success event, no change | PASS | VERIFY-HO: BDD invalid-value scenario green; `InvalidRegistrationModeException → 400 VALIDATION_ERROR` via existing handler |

### 5.4 Registration (modified capability)

| # | Scenario (spec) | Result | Evidence / reason |
|---|---|---|---|
| RG-01 | Missing-everywhere fails closed to CLOSED | PASS | VERIFY-HO gateway fallback tests green (REMOTE-CI integration lane); BDD durability path consistent |
| RG-02 | Explicit env seeds persisted row | PASS | VERIFY-HO migration seed test green (seed CLOSED matches prod default) |
| RG-03 | Persisted row overrides property fallback | PASS | VERIFY-HO gateway override test + BDD owner-write-observed green |
| RG-04 | No row falls back to property | PASS | VERIFY-HO gateway no-row-fallback test green |
| RG-05 | Admin change effective on next evaluation, no restart | PASS | VERIFY-HO BDD owner-write-observed scenario green |
| RG-06 | Redeploy does not revert admin-set mode | PASS (restart-equivalent) | VERIFY-HO BDD restart-equivalent green. Real redeploy ⇒ NOT TESTED, see NT-01 |

### 5.5 Frontend acceptance (ConfigurationView, nav, i18n, contract)

| # | Scenario | Result | Evidence / reason |
|---|---|---|---|
| FE-01 | Configuration nav entry live and visible with read permission | PASS | QA-EXEC-01: nav-registry live-entry assertion green |
| FE-02 | View renders current mode; loading/success/error states | PASS | QA-EXEC-01: `ConfigurationView.spec.ts` (7 tests) green |
| FE-03 | Write control gated on `platform.configuration.manage`; confirm-before-write | PASS | QA-EXEC-01: gating + `window.confirm` tests green; `changeConfirm` strings present EN+ES (`i18n/index.ts:187,443`) — traceability only |
| FE-04 | POST sends `Idempotency-Key` (`crypto.randomUUID()`); success/error handled | PASS | QA-EXEC-01: Idempotency-Key + success/error-path tests green |
| FE-05 | i18n `configuration.{title,currentMode,changeTo,changeConfirm,changeSuccess}` EN+ES | PASS | QA-EXEC-01 renders through both locales' keys; key sets inspected identical EN/ES — traceability only. Visual ES in browser ⇒ NOT TESTED, see NT-03 |
| FE-06 | `Accept: application/vnd.api.v1+json` contract on config calls | PASS | BDD glue const + header send proven by green BDD runs (PC-01); frontend `authStore.request` path covered by FE-04 tests |
| FE-07 | Real-browser interaction (click-through, focus, keyboard, screen-reader) | NOT TESTED | See NT-02 |

### 5.6 Non-applicable categories (with reason)

Boundary values: mode enum has no numeric boundary; invalid-string boundary covered by PC-04/AU-03.
Repeated/interrupted: replay covered by `ConfigurationIdempotency` unit tests (REMOTE-CI) + BDD
replay-adjacent paths; network-interruption mid-flight needs live target (NT-01).
Responsive: no layout/breakpoint criterion in specs; admin view reuses established composition.
Exploratory beyond forced-nav: needs live target (NT-01).

## 6. Untested scope, reason, and rerun prerequisite

| ID | Scope | Reason | Rerun prerequisite |
|---|---|---|---|
| NT-01 | Live Back Office walkthrough: owner changes mode end-to-end on a deployed env; real backend restart/redeploy durability; concurrent operators against real deploy | No target, credentials, or permissions supplied; nothing invented (contract ⇒ NOT TESTED) | `sdd-qa` rerun (or ops acceptance) against staging/prod Back Office with OWNER session: change OPEN→INVITE_ONLY→CLOSED, re-read, restart/redeploy, confirm mode holds |
| NT-02 | Real-browser E2E for ConfigurationView (Playwright): navigation, confirm dialog, keyboard-only flow, screen-reader naming, error toast | No Playwright spec for this view in-repo; no browser target in-session | Add `apps/web/admin` Playwright spec for configuration + run `just app-test-e2e*`/admin E2E lane against a served admin build |
| NT-03 | Visual ES-locale confirmation + responsive rendering of ConfigurationView | Strings unit-proven; visual check needs served build | Serve admin build, screenshot EN+ES at desktop/mobile widths |
| NT-04 | Local `backend-bdd-postgres` + full `backend-check`/`admin-build` re-runs | Cost decision, not environment block; covered by REMOTE-CI SUCCESS on exact squash + VERIFY-HO local runs | `just backend-bdd-postgres` (after `just infra-up`), `just backend-check`, `just admin-build` if archive demands belt-and-braces |

## 7. Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| F-01 | P2 | OPEN (warning) | Operator acceptance on a live target not performed (NT-01). No contrary signal: all executable suites green. Recommended post-deploy walkthrough, not an archive blocker (rationale §9) |
| F-02 | P3 | OPEN (warning) | No Playwright E2E for ConfigurationView (NT-02). Vitest interaction coverage (confirm, gating, Idempotency-Key, error paths) mitigates; E2E is follow-up hardening |
| F-03 | P3 | OPEN (info) | Coverage not measured locally in verify or QA; remote Quality Gate passed at merge. No coverage-regression signal |
| F-04 | P3 | OPEN (by design) | `SMP_REGISTRATION_MODE` remains seed/fallback: env edits do NOT override an admin-set value. Operator comms needed at rollout; not a defect |
| — | CRITICAL/P0/P1 | NONE | No failures, no denied-but-undetected path, no redaction leak, no permission-matrix mismatch, no lost-update signal |

Static-analysis note: QA introduced no code, hence no new suppressions/`any`/baseline entries by
construction. Verify handoff confirms clean detekt/spotless/vue-tsc on this HEAD.

## 8. Final verdict

**PASS WITH WARNINGS**

## 9. Verdict rationale and implementation handoff

Every acceptance criterion of #672 traces to executable runtime evidence on the exact shipped
commit: 7/7 `@platform-configuration` BDD scenarios green within the 35/35
`platform-admin.feature` suite (real HTTP stack, `Accept` contract, audit assertions,
concurrency, restart-equivalent durability), the exact permission matrix green on both server
(`PlatformPermissionTest`) and client mirror (`auth.store.test.ts`, re-executed in-session),
audit SUCCEEDED/REJECTED/FAILED branches green with the redaction-safe `targetType/targetId`
wiring, DB-as-source-of-truth with seed/fallback green, and the admin UI behavior
(render, gating, confirm, `Idempotency-Key`, success/error, EN+ES keys) re-executed green in this
session (QA-EXEC-01: 35/35). Remote CI on the identical squash is all-SUCCESS, covering the lanes
deliberately not re-run locally (postgres, builds, security scanners, SonarCloud). No scenario
FAILED; nothing is BLOCKED.

The WARNINGS are the four `NOT TESTED` items (§6), all caused by the same environmental fact —
no deployed target or browser harness was supplied — plus the by-design seed/fallback operator
caveat (F-04). Per the archive gate, acceptance-relevant `NOT TESTED` normally blocks; the
explicit rationale for proceeding is: (a) the missing scope is exclusively live-target/browser
observation, which this environment cannot produce without inventing a target (forbidden);
(b) every spec scenario already has executable, runtime-proven evidence on the merged commit,
with zero production-code drift between verify and QA; (c) the residual risk is therefore
deployment-observation, owned post-merge by NT-01, not implementation correctness. No
CRITICAL/P0/P1 findings exist; P2/P3 items are warnings.

Handoff to `sdd-archive`: eligible to proceed. Archive must see this `qa-report.md` alongside
`verify-report.md`. Carry forward as archive notes: F-01 post-deploy walkthrough recommendation,
F-02 Playwright follow-up, F-04 operator comms on seed-vs-admin-set precedence. Do not close
GitHub issue #672 from this gate; do not fabricate product acceptance beyond the evidence above.
