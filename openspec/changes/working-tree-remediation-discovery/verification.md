# Verification Report: working-tree-remediation-discovery

**Change:** `working-tree-remediation-discovery`
**Mode:** OpenSpec
**Status:** **PENDING RE-VERIFICATION**
**Verified:** 2026-08-02
**Branch:** `feat/app-product-tour`
**HEAD at prior verification:** `3511aacd` (`test(smp): harden actuator health endpoint assertions`)

## Executive result

The three corrective commits are present at the expected boundaries. SEC-001 production
authorization is committed in `e3b78d16`, the remaining B English password contract and
registration E2E paths are committed in `272b1fdc`, and the Actuator test support is committed in
`3511aacd`. The five SEC-001 authorization BDD scenarios, focused password tests, focused Actuator
integration test, frontend unit/check gates, licensing gate, and backend lint gate provide positive
evidence.

The previous verification FAIL recorded the original distributed Slice F requirement. The approved
MVP decision now defers that capability: no Redis or distributed implementation is added, the
waitlist delta spec preserves the two-replica scenarios under a follow-up section, and the
untracked `WaitlistDistributedRateLimitE2ETest.kt` is removed. The current implementation remains
per-instance Caffeine and SMP waitlist limiting defaults OFF. This artifact records the changed
contract and awaits the next verification pass. The registration Playwright harness and known
backend/app baseline failures remain separate warnings; do not archive until `sdd-verify` reruns.

## Artifact and repository inspection

This report is being updated during apply to record the approved MVP contract. Its prior runtime
evidence remains historical until the next verification phase reruns the applicable checks.

The following artifacts were read before judging the implementation: `proposal.md`,
`exploration.md`, `design.md`, `tasks.md`, `apply-progress.md`, `state.yaml`, `openspec/config.yaml`,
all ten delta specifications under `specs/`, and the previous `verification.md`.

The prior verification snapshot contained the five unrelated app WIP paths, the OpenSpec change
directory, and the untracked Slice F test. During this apply, the current status contains the same
unrelated app WIP paths, the C4 documentation update, and the OpenSpec artifacts; the Slice F test
is gone. The unrelated app WIP paths were not touched.

| Status path | Classification | Finding |
|---|---|---|
| `apps/web/app/e2e/README.md` | Unrelated app WIP | Composer/application documentation, outside the active slice boundaries. |
| `apps/web/app/src/modules/auth/infrastructure/auth-api.ts` | Unrelated app WIP | AI quota error fields, outside the password/security slices. |
| `apps/web/app/src/modules/module-relocation.spec.ts` | Unrelated app WIP | Timeout-only test stabilization, outside this change. |
| `apps/web/app/src/shared/i18n/locales/en/composer.ts` | Unrelated app WIP | Composer groundwork, outside this change. |
| `apps/web/app/src/shared/i18n/locales/es/composer.ts` | Unrelated app WIP | Composer groundwork, outside this change. |
| `docs/architecture/c4/02-container.md` | MVP architecture documentation | C4 now distinguishes the future Redis/distributed bucket backend from the current per-JVM Caffeine waitlist limiter. |
| `openspec/changes/working-tree-remediation-discovery/` | Intentional OpenSpec WIP | Updated proposal, design, waitlist spec, tasks, apply-progress, verification, and state artifacts; unrelated OpenSpec files remain untouched. |

The removed test path is absent, the selected documentation/artifact changes pass `git diff --check`,
and no application code was modified by this apply phase.

## Corrective commit boundary audit

| Concern | Commit | Exact boundary | Evidence | Result |
|---|---|---|---|---|
| SEC-001 production enforcement | `e3b78d16` | `IdentitySecurityConfiguration.kt` only | `git show` confirms `/api/media/proxy` was removed from the GET `permitAll()` list; the current committed file has no such allowlist entry. | ✅ COMMITTED |
| B password contract completion | `272b1fdc` | `registration.spec.ts`, EN `auth.ts`, EN `passwordRecovery.ts` | `git show --name-status` contains exactly these three paths; all changed English copy and the E2E error detail say 12. Earlier B commit `3a8d82f4` contains backend enforcement, schema parity, Spanish copy, tests, and spec-sync. | ✅ COMMITTED |
| Actuator supporting test configuration | `3511aacd` | `ActuatorEndpointsIntegrationTest.kt` only | `git show --name-status` contains exactly this test; it disables unavailable Redis health and exposes health components for the existing assertions. | ✅ COMMITTED |
| SEC-001 runtime coverage | `850668ca` plus `e3b78d16` | BDD feature/support plus production security configuration | Current Cucumber XML contains all five SEC-001 cases with no failure elements. | ✅ VERIFIED |

## Completeness

| Metric | Result |
|---|---:|
| Checklist items | 28 |
| Complete | 27 |
| Incomplete | 1 |
| Core incomplete items | 0 |

Remaining unchecked tasks:

- No current MVP task remains for distributed waitlist rate limiting. `F7.1` is complete as a
  cancellation/deferral decision, not as a failed implementation.
- `I11.1` — final clean-tree/green-between-commits review. The review was rerun, but the tree is
  intentionally dirty with unrelated app WIP and OpenSpec artifacts, and the required backend
  gates are not globally green.

## Build and test execution

Only repository-defined commands were used for the requested gates, except for the explicitly
focused app and Actuator commands needed to isolate the corrective commits.

| Command | Exact result | Classification |
|---|---|---|
| `just frontend-test` | **PASS** — 11 test files, 85 passed, 0 failed, exit 0. | Positive marketing/unit evidence. |
| `just frontend-check` | **PASS** — 72 files, 0 errors, 0 warnings, 14 hints, exit 0. | Positive Astro/check evidence. |
| `just licence-check` | **PASS** — frontend license scan passed; `:server:smp:generateLicenseReport` completed with `BUILD SUCCESSFUL`, exit 0. | Positive G1/ADR-0012 evidence. The jk1 plugin emitted the known non-fatal configuration-cache serialization warning. |
| `just backend-lint` | **PASS** — `:server:smp:detekt`, `BUILD SUCCESSFUL`, exit 0. | Positive E quality-gate evidence; active `server/smp/detekt-baseline.xml` remains present. |
| `just backend-test-fast` | **Historical FAIL** — 1,482 tests: 1,478 passed, 2 failed, 2 skipped, exit 1. Prior failures were `ModularStructureTest` and the then-untracked `WaitlistDistributedRateLimitE2ETest` initialization. | Required gate remains non-zero because of the known Ideas → Publishing baseline violation; the Slice F failure is no longer current after the test removal and MVP deferral. |
| `just backend-bdd-fast` | **FAIL** — 155 tests: 151 passed, 4 failed, 0 skipped, exit 1. Failures: trending hashtags, saved hashtag deletion, scheduled publication, and quick-created scheduled publication. | All four match the established unrelated baseline; the five SEC-001 scenarios and the password 11/12 registration scenarios passed. |
| `pnpm --filter app test:run` | **FAIL** — 110 files: 1,225 passed, 15 failed, 1,240 total, exit 1. | Failures are the known 14 `Lightbulb` mock failures in `App.test.ts`/`AppShell.test.ts` and the known AI comparison assertion in `CreatePostModal.test.ts`. |
| `pnpm --filter app exec vitest run src/shared/lib/validation/password-policy.test.ts src/shared/lib/validation/schemas.test.ts` | **PASS** — 2 files, 24 passed, 0 failed, exit 0. | Focused B copy/schema evidence is green. |
| `pnpm --filter app exec playwright test -c e2e/playwright.config.ts e2e/specs/registration.spec.ts` | **FAIL** — 15 tests across Chromium, Firefox, and Mobile Chrome; all failed before the password assertion. The harness timed out while the email locator was detached/not editable or unavailable. | Reported separately as a registration E2E harness blocker; this run does not prove the browser contract assertion. |
| `./gradlew :server:smp:postgresIntegrationTest --tests 'com.profiletailors.smp.integration.ActuatorEndpointsIntegrationTest' --no-daemon --rerun-tasks` | **PASS** — 8 tests, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESSFUL`, exit 0. | Focused `3511aacd` integration evidence is green. |
| `just backend-test-postgres` | **Historical FAIL** — 273 tests, 1 failure: the then-untracked `WaitlistDistributedRateLimitE2ETest` initialization at line 125 due Liquibase attempting `localhost:5432`. | This was evidence for the superseded distributed requirement, not an MVP acceptance failure. The next verify phase should rerun the applicable PostgreSQL lane after the test removal. |

`just frontend-test-e2e` was not repeated in this rerun; the previous verified run remains positive
for the marketing scenarios (190 passed) and the app mocked-media lane (36 passed, 4 documented
skips). The previous full backend build evidence remains compilation-positive but did not reach a
final successful build; no new full build was claimed here. Coverage threshold is configured as
`0`, so no threshold-bearing coverage verdict applies and no separate coverage run was required.

### Known baseline failures

The following non-zero results are classified as baseline/out-of-scope rather than attributed to
the three corrective commits, but their exits still prevent a globally green gate:

- `ModularStructureTest`: existing Ideas → Publishing dependency violation.
- Four BDD scenarios: trending hashtags, saved hashtag deletion, scheduled publication, and
  quick-created scheduled publication.
- App unit failures: 14 missing `Lightbulb` exports in test mocks and one AI comparison assertion.
- The historical Slice F initialization failure is superseded by the approved MVP deferral and test removal; it is not a current baseline or MVP acceptance failure.

## Spec compliance matrix

Runtime compliance is marked complete only where a covering test passed. Source/manual evidence is
shown separately and does not replace a missing or failing runtime test.

| Requirement | Scenario | Covering evidence | Result |
|---|---|---|---|
| Privacy / consent | Banner renders always-dark on a light page | Source has fixed dark palette; existing tests do not assert the rendered computed hex values. | ⚠️ PARTIAL |
| Privacy / consent | Banner link meets WCAG-AA contrast | Existing marketing `accessibility.spec.ts` consent-banner axe scan passed. | ✅ COMPLIANT |
| Privacy / consent | Description text meets WCAG-AA contrast | Same passed consent-banner axe scan; source uses `#a3a3a3` on `#1a1a1a`. | ✅ COMPLIANT |
| Privacy / consent | Accept all sets analytics true | Existing `ConsentBanner.test.ts` and consent E2E coverage passed. | ✅ COMPLIANT |
| Privacy / consent | Reject all sets analytics false | Existing `ConsentBanner.test.ts` and consent E2E coverage passed. | ✅ COMPLIANT |
| E2E password contract | 11-character password rejected | Backend `registration.feature` exact-11 scenario passed, but `registration.spec.ts > 5.4 Short password returns validation error` failed before its assertion in all 3 browser projects. | ❌ FAILING |
| E2E password contract | Spec asserts 12-char minimum in the API contract | Text is present in the spec and committed E2E mock, but no runtime test asserts the documentation contract itself. | ❌ UNTESTED |
| E2E password contract | Spanish register copy says 12 | `password-policy.test.ts` passed all 3 assertions; current Spanish copy contains 12. | ✅ COMPLIANT |
| E2E password contract | 12-character password accepted | `registration.feature > Password exactly 12 characters is accepted` passed in `just backend-bdd-fast`. | ✅ COMPLIANT |
| E2E password contract | RegisterForm validation parity | `schemas.test.ts` passed, including register-schema coverage, but no runtime request-spy assertion proves no network call. | ⚠️ PARTIAL |
| Marketing a11y | Main content is focusable via skip link | Existing landing-page skip-link test passed; all six source targets are present, but all six pages are not exercised by that test. | ⚠️ PARTIAL |
| Marketing a11y | Programmatic hash focus lands on content | No passing direct page-load hash-focus test. | ❌ UNTESTED |
| Marketing a11y | Axe runs under reduced motion | Existing accessibility suite passed with `contextOptions.reducedMotion = reduce`. | ✅ COMPLIANT |
| Marketing SEO | `robots.txt` is served | Route and type/check evidence exist; no passing runtime request assertion was run. | ❌ UNTESTED |
| Marketing SEO | `sitemap.xml` is served | Route and type/check evidence exist; no passing runtime request assertion was run. | ❌ UNTESTED |
| Dependency licensing | GPL-2.0 dependency fails the gate | Current positive graph passed; no negative GPL fixture was executed. | ❌ UNTESTED |
| Dependency licensing | Compatible dependencies pass | `just licence-check` passed frontend and backend checks. | ✅ COMPLIANT |
| Dependency licensing | Bumped dependencies compile | Prior compile tasks succeeded, but the previous full backend build timed out and no new full build was run. | ❌ FAILING |
| IAM password | 11-character password rejected at handler | `ResetPasswordHandlerTest` rejection passed; exact-11 BDD registration scenario passed. | ✅ COMPLIANT |
| IAM password | 12-character password accepted at handler | Exact-12 BDD registration scenario passed; no exact-12 password-reset handler scenario was identified. | ⚠️ PARTIAL |
| IAM password | Register schema blocks fewer than 12 | Focused schema tests passed, but no request-spy assertion proves submission is never attempted. | ⚠️ PARTIAL |
| IAM authorization | Permission is evaluated by explicit identifier | Existing authorization tests ran, but no single named scenario proves the no-implicit-hierarchy branch. | ⚠️ PARTIAL |
| IAM authorization | Unauthenticated media proxy request is denied | `security-endpoint-authorization.feature > Unauthenticated request to media proxy path is rejected with 401` passed. | ✅ COMPLIANT |
| IAM authorization | Only explicit allowlist endpoints are public | All five SEC-001 BDD scenarios passed: three 401 protected paths and two 200 explicit public paths. | ✅ COMPLIANT |
| Quality gates | Backend lint is green after stub deletion | `just backend-lint` passed; stub is absent and active baseline is unchanged. | ✅ COMPLIANT |
| Quality gates | Ideas sources are format-clean | `just backend-lint` passed. | ✅ COMPLIANT |
| Quality gates | Renamed helper preserves behavior/no old callers | Existing backend tests passed for this area and source contains `requireConnectedAccountId`, not the old helper. | ✅ COMPLIANT |
| Waitlist | MVP uses bounded per-JVM rate-limit buckets and does not claim cross-replica enforcement | `Bucket4jRateLimiter.kt:59-72` uses a bounded Caffeine cache; no distributed store configuration/dependency exists. | ✅ CONTRACT UPDATED; REVERIFY |
| Waitlist | SMP waitlist limiter defaults OFF | `server/smp/src/main/resources/application.yaml:35-39` binds `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}` and documents DALLAY-512/DALLAY-513. | ✅ CONTRACT UPDATED; REVERIFY |
| Waitlist follow-up | Burst on replica A is visible to replica B | Preserved in the delta spec as a future scenario only; not an MVP requirement. | ⏭️ DEFERRED |
| Waitlist follow-up | Shared window resets for both replicas | Preserved in the delta spec as a future scenario only; not an MVP requirement. | ⏭️ DEFERRED |
| Publishing | Placeholder secret fails fast | Existing `HmacOAuthStateSignerTest` evidence passed, including `test-` rejection. | ✅ COMPLIANT |
| Publishing | Blank secret is rejected | Existing signer test passed. | ✅ COMPLIANT |
| Publishing | Real secret signs and verifies | Existing signer tests passed. | ✅ COMPLIANT |
| Publishing | BDD configuration boots with an accepted secret | Current BDD context booted and executed all 155 scenarios; failures occurred later in unrelated features. | ✅ COMPLIANT |
| Compliance docs | Retention claims match implementation | Manual inspection confirms no route/table; retention documents explicitly state planned/not implemented. | ✅ COMPLIANT (manual gate) |
| Compliance docs | Audit report matches fixes | Current code and BDD evidence match SEC-001/SEC-002, but `docs/security/audit-report.md` still says SEC-001 code commit is pending. | ⚠️ PARTIAL |
| Compliance docs | ADR-0012 acceptance criteria are met | `just licence-check`, `ci-local` wiring, and the source-offer runbook are present; license gate passed. | ✅ COMPLIANT |

**Scenario summary:** The historical matrix above included two distributed Slice F scenarios that
are now explicitly deferred outside MVP. The next verify phase should recompute the matrix against
the updated contract; registration harness failures, remaining untested scenarios, and non-zero
baseline gates still prevent archive.

## Correctness: source and contract inspection

| Area | Status | Evidence |
|---|---|---|
| A1 consent | ✅ Implemented | Existing landed consent changes and prior marketing runtime evidence remain intact. |
| A2 marketing a11y/SEO | ✅ Implemented with test gaps | Focus targets, reduced-motion context, and Astro routes are present; direct hash/route assertions remain absent. |
| B password minimum and contract | ✅ Corrective boundary landed; E2E harness blocked | Backend, schemas, Spanish copy, spec-sync, and the three omitted English/registration paths are committed across `3a8d82f4` and `272b1fdc`; focused 24-test app run passes. Full registration Playwright cannot reach assertions. |
| C SEC-001 | ✅ Corrective boundary landed | `e3b78d16` contains the production allowlist deletion; current BDD XML shows all five SEC-001 scenarios passing. |
| Actuator test support | ✅ Corrective boundary landed | `3511aacd` contains only the two test properties; focused rerun passes 8/8. |
| D SEC-002 | ✅ Implemented | Placeholder guard, conditional wiring, test secrets, and signer evidence remain green. |
| E Ideas/baseline | ✅ Implemented | Formatting/helper rename landed; only the unreferenced stub was deleted; active baseline remains. |
| F distributed rate limit | ⏭️ Deferred outside MVP | Shared library is intentionally still per-JVM Caffeine; no Redis/distributed store/configuration/dependencies are added; the untracked test is removed. DALLAY-512/DALLAY-513 remain follow-up blockers. |
| G1/G2 build controls | ⚠️ Partial gate evidence | License gate passes; full dependency-bump build evidence remains incomplete. |
| H compliance docs | ⚠️ Mostly truthful; audit wording stale | Retention claims are softened correctly and ADR evidence passes, but the audit report still says SEC-001 code is pending. |
| I repository docs/config | ✅ Landed | `ad800dd0` contains the intended repository/config paths and `frontend-check` passes. |

## Design coherence

| Design decision | Status | Notes |
|---|---|---|
| Atomic slice commits and no mixed paths | ✅ Followed | `e3b78d16`, `272b1fdc`, and `3511aacd` each contain only their intended paths; unrelated WIP and F were not staged. |
| B independent 12-character constants and register schema parity | ✅ Followed | Existing B implementation plus the corrective English contract paths match the design. |
| C default-deny removes `/api/media/proxy` | ✅ Followed | Production allowlist now matches the decision and the five BDD scenarios pass. |
| Actuator properties are test-only | ✅ Followed | Redis health is disabled and components exposed only in `ActuatorEndpointsIntegrationTest`. |
| D global placeholder guard with safe BDD secrets | ✅ Followed | Existing signer tests and BDD boot evidence remain positive. |
| A2 Astro routes/reduced-motion context | ✅ Followed | Source and prior E2E evidence match the design. |
| E deletes only the detekt stub, not active baseline | ✅ Followed | `backend-lint` passes and the active baseline is untouched. |
| F two replicas share a distributed store | ⏭️ Deferred outside MVP | The approved design explicitly rejects adding Redis now. Future shared-window coverage follows DALLAY-512/DALLAY-513; F7.1 is not a failed MVP task. |
| G1/G2 split policy from dependency churn | ✅ Followed | Separate commits and current license evidence match the design. |
| H validates retention before documenting it | ✅ Followed with stale audit status | Retention docs match current reality; the audit report needs a later status refresh after the corrective commit. |
| I validates manifests/recipes before docs | ✅ Followed | Current check evidence is positive. |

## TDD compliance audit

Strict TDD is enabled in `openspec/config.yaml`.

| Area | RED → GREEN → REFACTOR evidence | Result |
|---|---|---|
| Original B/C/D and other slices | `apply-progress.md` records the in-tree failing-first work, including D guard tests and the SEC-001 BDD experiment. | ✅/⚠️ Partial — not every historical RED command is independently reproducible from commit history. |
| SEC-001 corrective commit | `apply-progress.md` records restoring the public permit entry produced the SEC-001 failure and removing it restored the baseline; current five scenarios pass. | ✅ Confirmed |
| B corrective contract paths | Existing schema/copy tests pass; the corrective commit is a contract-sync commit and contains no new production behavior beyond the already-tested policy. | ⚠️ RED evidence for the corrective sync itself is not independently recorded; Playwright remains harness-blocked. |
| Actuator corrective support | Focused integration test was rerun from a clean task graph and passed 8/8. | ⚠️ GREEN confirmed; separate RED capture is not present. |
| Tests committed before/with implementation | No evidence of production implementation being committed after a test-only change in the corrective commits; SEC-001 uses pre-existing BDD coverage. | ✅ No code-before-test violation found |

## Issues found

### DECISION RECORDED

1. **Distributed waitlist rate limiting is deferred outside MVP.** No distributed bucket store
   exists: the shared rate-limit library uses per-JVM Caffeine, `RateLimitProperties` has no store
   configuration, and no Redis dependency is present. The untracked two-replica test was removed
   because it tested that deliberately deferred capability. F7.1 is cancelled/deferred, not failed.
### CRITICAL

1. **Registration E2E harness is blocked.** All 15 `registration.spec.ts` matrix executions fail
   during page/locator startup or DOM detachment before the password-contract assertion. The
   committed B paths and focused 24-test unit run are positive, but browser-level coverage is not
   green.
2. **`just backend-test-fast` is non-zero.** The historical run reported 1,478/1,482 passed with
   the known `ModularStructureTest` baseline failure and the then-untracked F initialization
   failure; rerun after removal in verify.
3. **`just backend-bdd-fast` is non-zero.** Four known hashtag/publication scenarios fail, even
   though all five SEC-001 scenarios pass.
4. **Six scenarios remain untested and one dependency compilation scenario remains failing** in the
   compliance matrix. Static source evidence cannot replace runtime coverage under the verification
   contract.

### WARNING

- `docs/security/audit-report.md` still says SEC-001 code commit is pending even though `e3b78d16`
  now commits the production change; the security behavior is correct, but the document status is
  stale.
- The license report task emits the known non-fatal Gradle configuration-cache serialization
  warning.
- The previous marketing/app E2E run contains four documented app-media skips.
- `I11.1` cannot be closed while the intentionally preserved app WIP/OpenSpec paths and non-zero
  baseline gates remain.

### SUGGESTION

- Run `sdd-verify` against the updated MVP contract. Keep DALLAY-512/DALLAY-513 as explicit
  follow-ups before enabling waitlist limiting in a multi-replica deployment.
- Repair the app Playwright registration harness and add a direct no-network assertion for register
  schema rejection.
- Add direct runtime tests for computed consent palette, all legal-page hash focus, robots/sitemap
  responses, and the negative GPL license fixture.
- Refresh the audit report's SEC-001 commit/status references after the verification gate is resolved.

## Final verdict

**PENDING RE-VERIFICATION.** The approved MVP contract removes distributed waitlist rate limiting
from current acceptance: bounded per-JVM Caffeine remains, SMP waitlist limiting defaults OFF, no
Redis dependency is added, and F7.1 is cancelled/deferred. Registration E2E coverage is still
harness-blocked and required backend gates retain known non-zero baseline failures. Re-run
`sdd-verify` against this updated contract; archive is not yet permitted.
