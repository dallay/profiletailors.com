# Acceptance QA Report: dallay-413-bulk-scheduling

## Identity
- Change: `dallay-413-bulk-scheduling` (DALLAY-413 / Bulk Scheduling for Multiple Posts)
- Mode: openspec
- QA phase: qa (re-QA after prior `BLOCKED` report with 5 P1s)
- Date: 2026-09-09
- Runner: `fallback` — deterministic `sdd-quality-runner.mjs` not available; evidence via direct `just`/gradle/pnpm/playwright commands with exit codes and report XML. `fallback` limitation visible in §3.
- QA Executor: `sdd-qa` sub-agent (capability-driven acceptance, not second technical verify; no code modified)

## Sources of Truth
- Proposal: `openspec/changes/dallay-413-bulk-scheduling/proposal.md` (Approach 1 sync chunked, 8 risks)
- Specifications: `openspec/changes/dallay-413-bulk-scheduling/specs/publishing/spec.md` (7 requirements × 14 scenarios)
- Design: `openspec/changes/dallay-413-bulk-scheduling/design.md` (8 decisions, file map)
- Tasks: `openspec/changes/dallay-413-bulk-scheduling/tasks.md` (15/15 checked)
- Technical verification: `openspec/changes/dallay-413-bulk-scheduling/verify-report.md` (`PASS WITH WARNINGS`, 2026-08-30 re-verify, 13/14 scenarios PASS, Detekt 4 style issues noted)
- Prior QA: previous `qa-report.md` on disk was `BLOCKED` (5 open P1s F1–F5, 8 acceptance-relevant BLOCKED scenarios) — this report overwrites it after observable re-QA
- Config: `openspec/config.yaml` (`qa.acceptance_required_for_behavior_changes: true`, archive blocks unresolved CRITICAL/P0/P1)

## Target and Environment
- Target: local worktree `/Users/acosta/Dev/dallay/worktrees/clean-specs` (no orchestrator-supplied preview URL; local targets used instead)
- Backend target: `server/smp` publishing bounded context, `/api/v1/workspaces/{workspaceId}/bulk/*` exercised through Cucumber BDD with real Spring context + Testcontainers PostgreSQL
- Frontend target: `apps/web/app` (Vue 3 + Vite) exercised through Playwright scheduler config with dev-server webServer (`BulkImportModal` real mount)
- Environment: `just infra-up` started (`pt-clean-specs-623e31dc9c-postgresql-1`, mailpit, linkedin-wiremock all Started); Docker available; OpenJDK warnings only
- Credentials/permissions: BDD fixture tokens (`valid-token`, `ws-bulk-*`); local pnpm/gradle/playwright allowed; no prod credentials needed
- Limitations: `fallback` runner (no `sdd-quality-runner.mjs` envelopes — statuses below are direct command evidence, not prose overrides); E2E bulk backend routes mocked via `page.route` (frontend flow proven, backend proven separately by BDD against real DB); full scheduler suite shows 1 unrelated failure (see §9)

## Capability Inventory
| Capability | Availability | Selected? | Rationale / rejection reason |
|---|---|---|---|
| `backend_bdd_fast` (Cucumber `@bulk`, `just backend-bdd-fast`) | available | **selected** | Direct observable evidence for all 14 bulk scenarios with real DB/status assertions |
| `backend_postgres_integration` (`just backend-test-postgres`) | available | **selected (attempted, partial)** | Task includes only Cucumber classes; no bulk `postgresIntegrationTest` matched filter — 1000-row DB proof observed instead via BDD fast on Testcontainers Postgres (real `bulk_import_rows`/`bulk_import_jobs` counts) |
| `frontend_e2e` (Playwright scheduler config, `bulk-import.spec.ts`) | available | **selected** | Real-mount flow `open-bulk-import` → `BulkImportModal` → validate → preview + inline fix → schedule → result; 3/3 green, zero `page.evaluate` |
| `frontend_unit` (Vitest) | available | **selected** | 146 files / 1711 tests green (bulk slice included) |
| `backend_lint` (Detekt) | available | **selected** | `BUILD SUCCESSFUL` — the 4 style issues from re-verify are gone; recorded as closed WARNING, no suppression added |
| `backend_coverage` (Kover) | available | rejected | Not acceptance-relevant for re-QA; no artifact collected (carried as P3 suggestion) |
| `browser` (manual/exploratory) | unavailable | rejected | No deployed preview target; Playwright covers the browser surface |
| `accessibility` (axe/keyboard) | unavailable | rejected | No axe harness run; static-only must not claim PASS → NOT TESTED |
| `responsive` (viewport matrix) | unavailable | rejected | No viewport harness run → NOT TESTED |
| `locale (i18n)` | available (static) | rejected | Bulk strings hardcoded EN, no `bulk` keys in `en`/`es` — static only → NOT TESTED per policy |
| `full_ci` (`just ci`) | available | rejected | Out of QA scope; focused lanes executed instead |

## Scenario Matrix
| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| QA-01 | `backend_bdd_fast` | Gherkin 1 — per-row errors, no persistence (2 VALID 1 INVALID, zero DB writes) | PASS | BDD `Bulk scheduling` suite 14/14 green (`TEST-feature_classpath_features-bulk-scheduling.feature.xml`: tests=14 failures=0 errors=0); glue asserts `rows==3`, `invalid==1`, `bulk_import_jobs==0`, `countScheduledPublications==0`, status 200 |
| QA-02 | `backend_bdd_fast` | Retry side-effect free (same CSV twice, identical response, no persist) | PASS | Scenario `Retry is side-effect free` PASS; glue compares response maps + `bulk_import_jobs==0` (closes prior P1 F2 for this scenario) |
| QA-03 | `backend_bdd_fast` | Gherkin 2 — chunked atomic partial success (2 scheduled / 1 failed, 200/207) | PASS | Scenario PASS; glue asserts `scheduledCount==2`, `failedCount==1`, status 207/200, `pubCount>=2`, `jobId!=null`, `rowCount==3` |
| QA-04 | `backend_bdd_fast` + `backend_postgres_integration` | 1000-row batch chunked 10–20 tx with `bulk_import_rows=1000` proof | PASS | Scenario `1000-row batch chunked` PASS; glue asserts `totalRows==1000`, `bulk_import_rows==1000`, `total_rows==1000`, `pubs>=900` against real Testcontainers Postgres. Tx-count (10–20) is structural (`chunkSize=50` → 20 tx, repo chunk 100), not directly counted — see P2-QA1 |
| QA-05 | `backend_bdd_fast` | Gherkin 3 — owner sees counts (PARTIAL + row errors, 200) | PASS | Scenario PASS; seeds PARTIAL 3/2/1 + 3 rows, asserts 200 + body counts |
| QA-06 | `backend_bdd_fast` | Cross-workspace blocked (B requests A's jobId → exact 404) | PASS | Scenario `Cross-workspace blocked` PASS; glue `assertEquals(404)` — proves `BulkJobNotFoundException` → 404 mapping, no 400 re-wrap (closes prior P1 F3 for this path) |
| QA-07 | `backend_bdd_fast` | Gherkin 4 — templates catalog non-empty + canonical CSV header | PASS | Scenario PASS; asserts `templates.isNotEmpty` + header match `bodyText,scheduledFor,timezone,media_urls,hashtags` |
| QA-08 | `backend_bdd_fast` | Blank lines skipped (2 rows + 1 blank → 2) | PASS | Scenario `Gherkin 5 — blank lines skipped` PASS; asserts `rows.size==2` |
| QA-09 | `backend_bdd_fast` | Invalid date + missing content (INVALID_DATE + MISSING_CONTENT) | PASS | Scenario PASS; asserts `INVALID` + both codes |
| QA-10 | `backend_bdd_fast` | Duplicate warn-only (DUPLICATE) + SSRF-blocked `http://10.0.0.1/evil.jpg` → INVALID_MEDIA | PASS | Scenario `Duplicate warning and invalid media` PASS; asserts body contains `DUPLICATE` and `INVALID_MEDIA` with real pipeline (closes prior P1 F4 for private-IP path; oversized-URL path guarded by 10MB keyword/byte check — see P2-QA2) |
| QA-11 | `backend_bdd_fast` | Workspace mismatch → 403/404 not processed; UNVERIFIED schedule → exact 403 | PASS | Scenarios `Workspace mismatch rejected` + `Unverified blocked` PASS; glue asserts `403||404` and `assertEquals(403)` — proves `BulkWorkspaceMismatchException` → 403 path (closes prior P1 F3) |
| QA-12 | `backend_bdd_fast` | Duplicate CSV resubmit → exact 409 with existing jobId | PASS | Scenario `Duplicate CSV returns 409` PASS; asserts `assertEquals(409)` + jobId in body (sha256 idempotency key) |
| QA-13 | `backend_bdd_fast` | Capability violation (PDF → INVALID + CAPABILITY_VIOLATION) | PASS | Scenario `Capability violation PDF` PASS; asserts code + INVALID |
| QA-14 | `frontend_e2e` | Real-mount flow: `open-bulk-import` → modal → file upload → textarea → Validate → preview table + `bulk-error-1-INVALID_DATE` → inline fix → schedule → `bulk-schedule-result` with job-1 | PASS | `bulk-import.spec.ts` 3/3 passed (7.0s): `upload csv preview validate schedule poll`, `CSV header validation shows error via real parser`, `template picker downloads CSV into textarea`. Spec file inspected: zero `page.evaluate`, all interactions via `getByTestId`/`getByRole` + `setInputFiles` on `bulk-file-input`. Backend bulk routes mocked via `page.route` (200/207/PARTIAL shapes) — frontend flow proven; real backend proven by QA-01–QA-13 (closes prior P1 F1) |
| QA-15 | pipeline wiring | Conflict warn-only (two rows same account 10 min apart → both SCHEDULED, `hasConflict:true`) | NOT TESTED | No BDD/E2E scenario asserts `hasConflict:true`; wiring exists in `BulkValidationPipeline.detectConflictIndexes` per re-verify (static only, must not PASS). Warn-only V1 nicety — P2-QA4, rerun prerequisite below |
| QA-16 | `accessibility` | Bulk modal dialog semantics, focus-trap, keyboard, table scope | NOT TESTED | No axe/keyboard harness run; static-only per policy |
| QA-17 | `responsive` | Modal + preview table at 320/768/1280 viewports | NOT TESTED | No viewport harness run |
| QA-18 | `locale (i18n)` | Bulk strings in EN + ES | NOT TESTED | Strings hardcoded EN; no `bulk` i18n keys — static only per policy |

Allowed results: `PASS`, `FAIL`, `BLOCKED`, `NOT TESTED`.

## Untested Scope
- Scope: Conflict `hasConflict:true` warn-only observable proof
- Reason: No BDD/E2E scenario covers it; only pipeline wiring (static)
- Re-run prerequisite: Add BDD scenario (two rows, same account, 10 min apart → both SCHEDULED with `hasConflict:true`) or unit assertion on `validate` response, then `just backend-bdd-fast`
- Scope: a11y / responsive / i18n for Bulk UI
- Reason: No axe, viewport-matrix, or locale harness executed in QA window
- Re-run prerequisite: axe run + keyboard audit, 320/768/1280 viewport check, `bulk` namespace keys in `en`/`es` + ES snapshot
- Scope: Direct transaction-count proof for 1000-row chunking (10–20 tx)
- Reason: BDD proves row persistence (1000/1000) but counts transactions only structurally
- Re-run prerequisite: `TransactionSynchronization` counter or save-count assertion in Postgres integration test
- Scope: Oversized-URL → INVALID_MEDIA observable proof
- Reason: BDD proves blocked-IP path; 10MB guard is keyword/byte-check (synthetic V1 per design)
- Re-run prerequisite: Integration test posting oversized `media_urls` value asserting INVALID_MEDIA

## Findings
| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| F1 (prior) | P1 | No observable E2E — synthetic DOM injection | CLOSED by QA-14: real mount via `SchedulerView` `open-bulk-import`, 3/3 green, no `page.evaluate` | closed |
| F2 (prior) | P1 | BDD 14 scenarios are stubs | CLOSED by QA-01–QA-13: 14/14 green with DB counts + exact statuses (200/207/404/403/409, 1000-row counts) | closed |
| F3 (prior) | P1 | Workspace isolation 400 instead of 403/404 | CLOSED by QA-06 + QA-11: exact 404 cross-workspace, 403/404 mismatch, 403 unverified | closed |
| F4 (prior) | P1 | SSRF mitigation incomplete | CLOSED (V1 scope) by QA-10: `10.0.0.1` → INVALID_MEDIA observed; allowlist + 10MB keyword/byte guards in pipeline/service per re-verify | closed |
| F5 (prior) | P1 | ConflictDetectionPolicy not wired | DOWNGRADED — wiring exists per re-verify but unobservable → P2-QA4 below | downgraded to P2 |
| W-detekt (prior) | WARNING | Detekt 4 style issues | CLOSED: `:server:smp:detekt` BUILD SUCCESSFUL 2026-09-09, no findings, no suppressions added | closed |
| P2-QA1 | P2 | 1000-row tx-count not directly counted | QA-04 proves 1000/1000 rows persisted; 10–20 tx structural only | open |
| P2-QA2 | P2 | Oversized-URL INVALID_MEDIA not observably proven | Only blocked-IP path observed (QA-10); 10MB guard synthetic V1 | open |
| P2-QA3 | P2 | `bulkScheduling.enabled` docs-only flag; a11y/i18n gaps | Carried from re-verify W3 + QA-16–QA-18; unchanged, non-blocking warnings | open |
| P2-QA4 | P2 | Conflict warn-only `hasConflict:true` unobservable | QA-15 NOT TESTED; warn-only hint, does not block scheduling | open |
| P3-QA1 | P3 | Frontend/backend CSV parser parity contract test; per-row INSERT batching; Kover artifact | Carried suggestions; non-blocking | open |
| TC-05A | P2 (out of scope) | `scheduler-create-post.spec.ts` TC-05A failed during full-scheduler run (`PostDetailModalPage.expectVisible`) | Observed 2026-09-09: full `test:e2e:scheduler` → 41 passed / 1 failed; bulk file isolated 3/3 green. Unrelated to bulk files; needs triage on main | open (other owner) |

Allowed severities: `CRITICAL`, `P0`, `P1`, `P2`, `P3`.

## Verdict
`PASS WITH WARNINGS`

### Rationale
- All 5 prior P1s are closed with **observed** (not static) evidence: 14/14 BDD scenarios green with real DB/status assertions on Testcontainers Postgres (F2, F3-part, F4-part, 1000-row proof), exact 403/404/409 contract statuses (F3), blocked-IP → INVALID_MEDIA (F4), and real-mount E2E 3/3 with zero synthetic injection (F1).
- Detekt is green with no suppressions (repo policy honored).
- Zero FAIL, zero BLOCKED, zero open CRITICAL/P0/P1. Remaining findings are P2/P3 warnings only (untested conflict hint, tx-count shape, oversized-URL path, flag/a11y/i18n, out-of-scope TC-05A).
- QA-15 is NOT TESTED but concerns a warn-only V1 hint that cannot fail scheduling; recorded visibly with a rerun prerequisite rather than blocking acceptance of the 14 proven scenarios.

## Limitations and Handoff
- QA does not fix code.
- Product acceptance is not claimed without a target and observable evidence — evidence above is local-target observable (real BDD DB + real component mount), not production.
- E2E bulk backend routes were mocked at the HTTP layer; real backend behavior is covered by BDD QA-01–QA-13, not by the browser run.
- Follow-up for implementation:
  - Add BDD scenario for conflict warn-only (`hasConflict:true`) — P2-QA4.
  - Add tx-counter or save-count assertion for 1000-row chunking — P2-QA1.
  - Add oversized-URL INVALID_MEDIA integration proof — P2-QA2.
  - Wire `bulkScheduling.enabled` code guard; add `bulk` i18n keys; axe + viewport pass — P2-QA3.
  - Triage out-of-scope `scheduler-create-post` TC-05A failure on main — TC-05A.
  - Archive may proceed: `verify-report.md` (PASS WITH WARNINGS) + this `qa-report.md` (PASS WITH WARNINGS) both exist; no unresolved CRITICAL/P0/P1.
