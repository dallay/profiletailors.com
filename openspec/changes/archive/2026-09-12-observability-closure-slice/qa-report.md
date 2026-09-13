# Acceptance QA Report: observability-closure-slice

## Identity
- Change: `observability-closure-slice`
- Mode: openspec
- QA phase: acceptance QA after `sdd-verify`
- Date: 2026-09-11
- Execution mode: `fallback` — no `openspec/quality-runner.json` or configured QA runner/FSM was available. Command output and source references below are the auditable evidence.

## Sources of Truth
- Proposal: `openspec/changes/observability-closure-slice/proposal.md`
- Specifications: `openspec/changes/observability-closure-slice/specs/observability-boundary/spec.md`
- Design: `openspec/changes/observability-closure-slice/design.md`
- Tasks: `openspec/changes/observability-closure-slice/tasks.md`
- Apply progress: `openspec/changes/observability-closure-slice/apply-progress.md`
- Technical verification handoff: `openspec/changes/observability-closure-slice/verify-report.md` (`PASS WITH WARNINGS`)
- Repository policy: `openspec/config.yaml`, `Justfile`, `AGENTS.md`

## Target and Environment
- Target: technical observability boundary in `shared/observability`, SMP observability adapter and producers, and the associated observability documentation/ADR. This change declares no product capability and has no standalone browser target.
- Environment: local macOS worktree `/Users/acosta/Dev/dallay/worktrees/observability`; Kotlin/Gradle backend, Cucumber/Testcontainers BDD, Astro/Vitest/Playwright frontend lanes; local Docker/Testcontainers was available for the completed PostgreSQL and BDD runs.
- Credentials/permissions: backend tests use repository fixtures. Real app media E2E credentials were unavailable (`E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD` are not set).
- Limitations:
  - This is a technical refactoring, so the primary acceptance surface is executable backend/unit/BDD evidence plus source/doc contract checks, not a user-facing browser flow.
  - No runner envelope exists; results are fallback command evidence, not deterministic FSM enforcement.
  - The observability regression was reproduced and remediated: `CreateUploadedAssetHandler` was missing the project `@Service` marker, so mediator dispatch failed before media authorization. A Spring context regression test observed `NoSuchBeanDefinitionException` before the fix and passed after restoring the annotation.
  - `just backend-bdd-fast` now passes after the wiring fix (`BUILD SUCCESSFUL in 5m 24s`). The earlier BDD warning is stale and should not be treated as a remaining failure.
  - The app mocked media E2E claim of seven failures was not reproducible: `just app-test-e2e-media-mocked` passed all 36 tests on rerun. Vite emitted expected proxy `ECONNREFUSED` messages because the backend was not running.
  - The real media lane remains blocked because `E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD` are unavailable; no credentials were invented.

## Capability Inventory
| Capability | Availability | Selected? | Rationale / rejection reason |
|---|---|---:|---|
| Focused Kotlin/JUnit runtime tests | available | Yes | Narrow executable evidence for sanitizer, safety boundary, adapter, pipeline, migrated media/publishing, and account-closure behavior. |
| Backend fast JUnit suite | available | Yes | Broad non-PostgreSQL backend regression evidence. `just backend-test-fast` passed. |
| Backend compile/format/Detekt | available | Yes | Observable operator/developer contract for the changed Kotlin boundary and callers. All selected checks passed. |
| Backend Cucumber fast BDD | available | Yes | Relevant authorization/media regression lane. Rerun after restoring handler wiring: `PASS`; `just backend-bdd-fast` completed with `BUILD SUCCESSFUL in 5m 24s`. |
| Backend PostgreSQL integration | available | Yes | Persistence/runtime lane requested by the user. `just backend-test-postgres` passed on the completed rerun. |
| Backend PostgreSQL Cucumber BDD | available | Not rerun | The remediation reran the narrowest affected fast BDD lane. The prior PostgreSQL result remains historical evidence and is not reclassified by this slice. |
| Backend full build | available | Not rerun | The remediation reran the narrowest affected fast BDD lane rather than the full build. |
| Repository backend check | available | Yes | Requested broad check. Completed rerun passed; repository recipe excludes the BDD tasks by definition. |
| Production legacy-consumer scan | available | Yes | Required observable closure condition: zero legacy helper consumers and zero `emitLegacy`. Passed with zero matches. |
| Sanitizer privacy/cause checks | available | Yes | Required observable privacy contract: focused safety suite passed. |
| Structured event-shape migration checks | available | Yes | Required stable dotted event names/named attributes and focused caller tests passed. |
| Documentation/ADR consistency | available | Yes | `doc-check` passed; ADR index and ADR-0021 were inspected; `git diff --check` passed. `docs-links` and `docs-lint` were run and exposed pre-existing/unrelated repository findings. |
| Browser/Playwright marketing E2E | available | Rejected | No product/browser surface is changed by this technical refactor; marketing E2E is not an acceptance target. |
| App mocked media Playwright E2E | available | Yes | User-requested reproduction lane. Rerun passed all 36 tests; the prior seven-failure claim was not reproducible. |
| App real media Playwright E2E | unavailable for acceptance | No | Runner exists, but required credentials are absent. The lane was attempted and remains blocked; no real credentials were invented. |
| Accessibility/responsive/locale browser checks | rejected | No | No browser UI, responsive layout, accessibility contract, or locale behavior is in scope. |
| Manual exploratory product session | rejected | No | No application-under-test or product capability is introduced by this change. |

## Scenario Matrix
| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| QA-01 | Focused Kotlin/JUnit | A normal operational sink failure is isolated once and does not escape into the business caller. | PASS | `./gradlew :shared:observability:test --tests 'com.profiletailors.observability.OperationalEventSafetyTest' --no-daemon --rerun-tasks`: `BUILD SUCCESSFUL in 5s`; shared observability suite also passed. |
| QA-02 | Focused Kotlin/JUnit | Cancellation propagates unchanged without fallback emission; fatal `Error` is not swallowed. | PASS | `OperationalEventSafetyTest` and `OperationalEventPipelineBehaviorTest` passed; pipeline command ended `BUILD SUCCESSFUL`. |
| QA-03 | Sanitizer privacy | Sensitive credential/token/privacy fields and values are redacted, causes become safe `errorType`, ordinary author/authorship context remains visible, and source event data is not mutated. | PASS | Shared safety tests passed; `./gradlew :shared:observability:test --no-daemon` ended `BUILD SUCCESSFUL in 3s`. |
| QA-04 | Event-shape migration | Changed producers emit stable dotted names with bounded named attributes while preserving business outcomes and original causes. | PASS | Focused media/publishing and account-closure tests passed; combined focused run ended `BUILD SUCCESSFUL in 17s`. |
| QA-05 | Boundary safety | `Slf4jOperationalEventSink` only formats/dispatches already-sanitized events and bootstrap retains the best-effort decorator around the adapter. | PASS | `Slf4jOperationalEventSinkTest` and `OperationalEventPipelineBehaviorTest` passed; compile and source inspection confirm no adapter catch/sanitize path. |
| QA-06 | Legacy helper removal | Production source contains zero legacy severity-helper imports/calls and zero `emitLegacy` consumers after helper removal. | PASS | Fresh `rg` scan over `server/smp/src/main` and `shared` returned `NO MATCHES` for `emitLegacy`, legacy `com.profiletailors.observability.{trace,debug,info,warn,error}` imports, and `operationalEvents.{trace,debug,info,warn,error}` calls. |
| QA-07 | Backend regression | Focused and broad non-BDD backend tests remain green. | PASS | `just backend-test-fast` passed; `just backend-check` completed `BUILD SUCCESSFUL in 6s`; `just backend-test-postgres` completed `BUILD SUCCESSFUL in 3s`; `just backend-lint-shared` passed. |
| QA-08 | Backend BDD negative/authorization | Pending local user media registration is denied with `EMAIL_VERIFICATION_REQUIRED` and no asset; verified local user follows the normal media rule and persists one asset. | PASS | The regression was reproduced as missing Spring registration of `CreateUploadedAssetHandler`: a new context test failed with `NoSuchBeanDefinitionException` before the fix. Restoring the project `@Service` marker made the test pass, and `just backend-bdd-fast` then completed `BUILD SUCCESSFUL in 5m 24s`. |
| QA-09 | Full backend build | Full backend build completes with all selected backend suites. | NOT RERUN | The remediation reran the narrowest affected backend BDD lane as requested. The earlier full-build failure was caused by the now-fixed handler registration defect; a fresh full build remains outside this remediation slice. |
| QA-10 | Compilation/static quality | Changed Kotlin compiles, formatting is clean, and Detekt is clean. | PASS | `./gradlew :server:smp:spotlessKotlinCheck :server:smp:compileKotlin --no-daemon`: `BUILD SUCCESSFUL in 3s`; `./gradlew :server:smp:detekt --no-daemon`: `BUILD SUCCESSFUL in 14s`; `just backend-lint-shared`: `BUILD SUCCESSFUL in 2s`. |
| QA-11 | Documentation/ADR consistency | Usage documentation, ADR-0021, and ADR index describe the final boundary, matcher scope, non-goals, and implementation status. | PASS | `docs/observability-usage.md`, `docs/architecture/adr/0021-operational-event-safety-boundary.md`, and `docs/architecture/adr/README.md` inspected; `just doc-check` passed; `git diff --check` returned exit 0. |
| QA-12 | Documentation repository gate | Repository documentation checks are clean. | PASS WITH WARNINGS | `just doc-check` passed. `just docs-links` found two external-link issues: Codecov URL `403 Forbidden` and GNU AGPL URL timeout. `just docs-lint` found 76 issues in 17 files, primarily generated Playwright report markdown under `apps/web/app/e2e/...`; the changed docs are not implicated. These are unrelated repository hygiene warnings, not silent passes. |
| QA-13 | Marketing frontend regression | Marketing lint, type/content check, unit tests, and build remain green. | PASS | `just frontend-lint`: 67 files checked, no fixes; `just frontend-check`: 0 errors/0 warnings/0 hints; `just frontend-test`: 14 files and 137 tests passed; `just frontend-build`: 12 pages built. This is supplemental because marketing is outside the change target. |
| QA-14 | Admin frontend regression | Admin type-check, unit tests, and build remain green. | PASS | `just admin-check` completed `vue-tsc --build`; `just admin-test`: 4 files and 29 tests passed; `just admin-build`: Vite build completed. Supplemental only. |
| QA-15 | App mocked media regression | Existing app mocked media lane remains green. | PASS | `just app-test-e2e-media-mocked`: 36 passed in 1.1m on rerun. The prior seven-failure claim was not reproducible. Vite logged expected proxy `ECONNREFUSED` messages because no backend was running. |
| QA-16 | App real media regression | Real media acceptance lane can authenticate and run. | BLOCKED | `just app-test-e2e-media-real` was attempted while `E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD` were unavailable. The lane started three tests and failed at the credential-dependent fixture boundary. This is an external credential constraint; no credentials were invented. |
| QA-17 | Repeated/state-transition behavior | Repeated structured event emission, interrupted/cancellation behavior, and migrated caller state transitions remain stable. | PASS | Focused safety/pipeline tests passed, including cancellation and original-cause/rethrow assertions; media/publishing/account focused tests passed. |
| QA-18 | Browser/accessibility/responsive/locale | Browser UI, responsive, accessibility, and locale acceptance for the observability closure. | NOT TESTED | Not applicable to the technical backend/shared refactor and no product browser target exists. No PASS is claimed from static inspection. |
| QA-19 | Persistence/operator outcome | Sanitized event output can be emitted without changing business operation outcomes; PostgreSQL integration remains green. | PASS | Focused pipeline tests passed original-cause and rethrow behavior; `just backend-test-postgres` passed. |

## Untested Scope
- Scope: Product/browser acceptance, accessibility, responsive behavior, locale behavior, and real media credentials-dependent flow.
- Reason: The proposal declares no product capability or frontend behavior; real media E2E additionally requires credentials not present in this environment. The browser categories are explicitly not applicable to this target, while the real media run is externally blocked.
- Re-run prerequisite: For this change, rerun `just backend-bdd-fast`, `just backend-bdd-postgres`, and `just backend-build` after the media authorization scenarios are triaged/fixed or explicitly dispositioned. For the app real lane, provide test-only `E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD`. Do not archive based on this report alone while acceptance-relevant failures remain.

## Target, Environment, Permissions, and Limitations

- Target: technical observability boundary in `shared/observability`, SMP observability adapter and producers, and associated documentation/ADR. No product capability delta or standalone browser target is declared.
- Environment: local macOS worktree `/Users/acosta/Dev/dallay/worktrees/observability`; Kotlin/Gradle backend, Cucumber/Testcontainers BDD, Astro/Vitest/Playwright frontend lanes, and worktree-scoped Docker infrastructure.
- Permissions: repository backend fixtures and local Docker were available. `E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD` are unset.
- Limitations: no configured `openspec/quality-runner.json` or QA runner/FSM was available, so this report uses `fallback` command evidence. Real media E2E is not included by `just ci` and was not run.

## Capability Inventory

| Capability | Status | Rationale |
|---|---|---|
| Local CI command runner | selected | Executes `just ci` and records exact exit status/output. |
| Gradle backend/build/test runner | selected | Executes unit, static analysis, full build, and BDD lanes. |
| Docker/Testcontainers PostgreSQL | selected | Required PostgreSQL integration and BDD acceptance lanes were available after `just infra-up`. |
| Browser/Playwright | selected | Included marketing Playwright E2E in `just ci`; all browsers passed. |
| Real external-media E2E | unavailable | Required credentials are absent; no credentials were invented. |
| Product API/data/accessibility/responsive/locale/manual exploratory target | rejected | No product capability or executable target is in this technical refactor. |

## Current Execution Evidence

- Before execution, `git status --short --untracked-files=all` showed the existing observability/docs/test/OpenSpec changes; no reset, clean, checkout, or unrelated source cleanup was performed. After execution, the same change set remains uncommitted and `git diff --check` is clean. `HEAD` remains `86b28a2d`.
- `just ci`: **PASS**, exit status `0`; final run completed all 15 checks. Evidence: `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/observability-just-ci-final-final.log`.
  - Passed: Gitleaks, documentation freshness, licence scan, marketing lint, app lint/unit/build, admin lint/unit/build, frontend coverage/build, Detekt, backend fast unit tests, backend fast BDD, and marketing Playwright E2E across browsers.
  - Final marker: `✅ Full CI Pipeline Complete — all 15 checks passed`.
- `just backend-build`: first run **FAIL**, exit status `1`, because the existing regression test import order failed `:server:smp:spotlessKotlinCheck`; the same run also hit a PostgreSQL connection failure because the direct recipe did not receive the worktree-mapped Docker port. Evidence: `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/observability-backend-build.log`.
- The first failure was reproduced narrowly with `./gradlew :server:smp:spotlessKotlinCheck --no-daemon`; Spotless identified only `server/smp/src/test/kotlin/com/profiletailors/smp/PlatformBootstrapContextTest.kt` import ordering. `./gradlew :server:smp:spotlessApply --no-daemon` corrected formatting, `spotlessKotlinCheck` then passed, and the full `just backend-build` rerun passed with exit status `0`. No production/source behavior was changed.
- PostgreSQL infrastructure was started through `just infra-up`; the worktree-mapped port was `33331`. With that explicit environment mapping, `just backend-test-postgres` passed, exit status `0`, and `just backend-bdd-postgres` passed, exit status `0`. Evidence: `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/observability-backend-postgres-final.log` and `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/observability-backend-bdd-postgres-final.log`.
- `just backend-test-fast`, `just backend-bdd-fast`, and `./gradlew :server:smp:detekt --no-daemon` passed.
- `just docs-lint` remains **FAIL**, exit status `1`, but the failures are ignored generated Playwright `apps/web/app/test-results/**/error-context.md` files and are unrelated to the observability change. `just doc-check` passed. Evidence: `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/observability-docs-lint-after.log`.
- Real media E2E credentials remain unset (`E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD`), but `just ci` does not invoke the real media lane; it invokes marketing E2E only. The real media lane is therefore not claimed as run and remains an explicit environment limitation.

## Findings
| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| F-001 | P2 | Documentation lint over generated Playwright artifacts | `just docs-lint` reports generated `apps/web/app/test-results/**/error-context.md` Markdown violations; `openspec/**` is excluded by the recipe and the changed observability docs pass targeted freshness/diff checks. | Open, unrelated repository hygiene |
| F-002 | P2 | Real media E2E | `E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD` are unset. `Justfile` confirms `just ci` does not include `app-test-e2e-media-real`; no credential fabrication was attempted. | Not run / environment limitation |

## Scenario Matrix

| Scenario | Result | Evidence / reason |
|---|---|---|
| Observability safety boundary and structured-event behavior | PASS | Focused evidence handed off by verify; `just ci` backend unit and fast BDD checks passed. |
| Full local CI pipeline | PASS | `just ci` exit status `0`; all 15 labelled checks passed. |
| Full backend build | PASS | Final `just backend-build` exit status `0` after formatting-only Spotless correction. |
| PostgreSQL integration suite | PASS | Final `just backend-test-postgres` exit status `0` using the worktree-mapped port. |
| PostgreSQL BDD suite | PASS | Final `just backend-bdd-postgres` exit status `0` using the worktree-mapped port. |
| Fast backend BDD suite | PASS | Final `just backend-bdd-fast` exit status `0`. |
| Backend static analysis | PASS | `./gradlew :server:smp:detekt --no-daemon` exit status `0`. |
| Documentation freshness | PASS | `just doc-check` exit status `0`. |
| Documentation lint | NOT TESTED | Applicable command was run but failed on ignored generated Playwright reports; static inspection cannot be converted to a pass. See F-001. |
| Real external-media acceptance flow | NOT TESTED | No approved credentials; `just ci` does not include this lane. Rerun with approved credentials and the configured real-media command. |
| Unauthorized/security, accessibility, responsive, locale, persistence, repeated/interrupted, and exploratory product flows | NOT TESTED | This technical refactor declares no product capability and has no standalone product target; no executable acceptance target was supplied. |

## Untested Scope and Rerun Prerequisites

- Real media E2E: provide approved `E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD`, ensure the real-CAS environment is available, then run `just app-test-e2e-media-real`.
- Product/browser acceptance beyond the repository CI targets: provide a running product target and permissions/credentials; this change has no product capability delta.
- Documentation lint: rerun after generated Playwright `test-results` artifacts are removed or the repository lint scope is intentionally corrected by its owner; this QA phase does not modify unrelated hygiene configuration.

## Verdict
**PASS WITH WARNINGS**

### Rationale
`just ci` now passes with exit status `0`, all required fast backend and frontend CI checks pass, the full backend build passes, and both PostgreSQL integration and PostgreSQL BDD lanes pass with the worktree-specific database port. The only observed failure is the unrelated documentation lint over ignored generated Playwright reports, recorded as P2. Real media E2E is not part of `just ci` and remains untested because credentials are absent; it is explicitly not claimed as product acceptance.

## Limitations and Handoff
- QA uses `fallback` command evidence because no configured QA runner/FSM was available.
- QA did not modify production source code. The repository formatter corrected import order in the already-changed regression test during the full-build rerun; that formatting-only change is preserved in the uncommitted diff.
- The observability closure has no observed acceptance-owned defect in the final evidence.
- Do not archive based on `just ci` alone if repository policy requires zero unresolved P2 findings or a completed real-media lane; otherwise the docs-only/generated-artifact warning is explicit and bounded.

## Implementation Handoff
- Preserve the restored `@Service` annotation on `CreateUploadedAssetHandler` and its regression test.
- Preserve the shared observability safety-boundary implementation and current uncommitted observability diff.
- Treat F-001 as pre-existing/unrelated generated-report hygiene, not as an observability behavior failure.
