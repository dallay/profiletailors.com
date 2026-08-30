# QA Report — DALLAY-413 Bulk Scheduling for Multiple Posts

**Change:** `dallay-413-bulk-scheduling` (DALLAY-413 / feature/dallay-413-featscheduling-bulk-scheduling-for-multiple-posts at `ad61ee44` + stacked pr1/pr2/pr3)
**Mode:** `openspec` (artifact_store.mode=openspec, strict_tdd=true)
**Phase:** `qa` (verify → qa)
**Date:** 2026-08-30
**Runner:** `fallback` — deterministic `sdd-quality-runner.mjs` not available; no deployed preview / no `just infra-up` / no Playwright browser session. Evidence via direct `just`/gradle/pnpm + static inspection. `fallback` limitation visible below.
**QA Executor:** `sdd-qa` sub-agent (capability-driven acceptance, not second technical verify)

---

## 1. Identity

| Field | Value |
|-------|-------|
| Change | `dallay-413-bulk-scheduling` |
| Linear | DALLAY-413 — Bulk Scheduling for Multiple Posts |
| Branch | `feature/dallay-413-featscheduling-bulk-scheduling-for-multiple-posts` (+ `pr1-domain`/`pr2-schedule`/`pr3-frontend` stacked) |
| Mode | `openspec` |
| Phase | `qa` |
| Date | 2026-08-30 |
| Verdict | **`BLOCKED`** (acceptance Required per `openspec/config.yaml`) |

---

## 2. Source Artifacts and Technical Verification Handoff

| Artifact | Status | Notes |
|----------|--------|-------|
| `proposal.md` | ✅ | Approach 1 sync chunked inside `publishing`; 8 risks |
| `specs/publishing/spec.md` (delta) | ✅ | 7 requirements × 14 scenarios (Gherkin 1-5 + isolation/idempotency/lifecycle) |
| `design.md` | ✅ | 8 decisions, file map 13/13, contracts, guards |
| `tasks.md` | ✅ | 15/15 `[x]` (Phase 1-6), High 950-1200L split 3 stacked PRs compliant |
| `verify-report.md` | ✅ | **PASS WITH WARNINGS** 2026-08-30 — 1614 frontend tests green, backend unit green, `backend-test` 31 tasks SUCCESS, detekt SUCCESS, `bulk` unit 18/18 + 3 suites 22/22 — but 8 WARNINGS |
| `state.yaml` | ✅ | `verify → qa` at handoff |
| `openspec/config.yaml` | ✅ | `qa.acceptance_required_for_behavior_changes: true`, evidence_policy, archive_blockers |

**Technical handoff (verify):** `PASS WITH WARNINGS` — no CRITICAL; 8 WARNINGS carried forward:

- W1 SSRF allowlist+magic-byte+10MB partial (private-IP deny only)
- W2 dummy `acc-bulk-placeholder` account
- W3 `bulkScheduling.enabled` docs-only flag
- W4 ProblemDetails 400 vs spec 403/404 for workspace mismatch / cross-workspace
- W5 `BulkImportRow` @AggregateRoot over-annotation (ADR-0015)
- W6 BDD stub assertions (14 scenarios, only 3 assert real counts)
- W7 E2E synthetic DOM injection (`bulk-import.spec.ts` route mocks + `page.evaluate` div)
- W8 `ConflictDetectionPolicy` not wired (no `hasConflict:true` warn-only)

These are promoted to QA findings with severities below.

---

## 3. Target, Environment, Permissions, Limitations

| Dimension | Value | Evidence |
|-----------|-------|----------|
| Target | **No deployed target supplied** — local worktree only | Orchestrator did not provide preview URL / env |
| Frontend target | `apps/web/app` (Vue 3 + Vite, publishing module) | `apps/web/app/e2e/specs/bulk-import.spec.ts` exists but not mounted via real `SchedulerView` |
| Backend target | `server/smp` publishing bounded context, `/api/v1/workspaces/{workspaceId}/bulk/*` | `BulkPublishingController.kt` 4 endpoints, `R2dbcBulkImportJobRepository` |
| Infra | Docker/Testcontainers **not started** (`just infra-up` not run in QA) | Postgres integration + `@bulk @fast` BDD require Testcontainers/Postgres |
| Credentials | `valid-token` / `ws-bulk-1` fixtures only (no real OAuth/X-Workspace-Id session) | `BddDatabaseSupport.USER_BEARER`, `X-Workspace-Id` |
| Permissions | Local `pnpm`/`gradle` allowed; `just backend-bdd-fast`/`just backend-test-postgres`/`just app-test-e2e-media-mocked` require infra + browser — **BLOCKED** | Documented below |
| Browser | Playwright available but no dev server (`just dev-frontend` not running) | Synthetic check only |
| Limitation | `sdd-quality-runner.mjs` unavailable → `fallback` evidence (direct commands + static). **Static inspection MUST NOT produce PASS** per QA contract | Verbatim preserved |
| Capability harness | No general test runner harness for acceptance; no `agent-browser` session | `fallback` visible |

**Implication:** Observable acceptance (CSV drop → validate → preview inline fix → schedule chunked → poll job → templates) cannot be proven end-to-end. Scenarios requiring live browser, real Postgres, or workspace-scoped BDD are `BLOCKED` with rerun prerequisite, not invented as PASS.

---

## 4. Capability Inventory

Select only capabilities that can produce observable evidence. `UNAVAILABLE` → `NOT TESTED`.

| Capability | Runner / Command | Available | Selected | Rationale |
|------------|-----------------|-----------|----------|-----------|
| `backend_unit` | JUnit5+MockK `just backend-test-fast` | available | **selected** | Direct evidence: 1614 green + 22 bulk unit |
| `backend_architecture` | ArchUnit/Modulith `just backend-test-fast` | available | **selected** | Included in non-postgres run ✅ |
| `backend_bdd_fast` | Cucumber `@bulk @fast @smoke` `just backend-bdd-fast` | **available but BLOCKED** | **rejected** | Requires Testcontainers + Spring context + Postgres fixture; not executed (BDD glue only checks `!=null`) |
| `backend_postgres_integration` | `just backend-test-postgres` | **available but BLOCKED** | **rejected** | Requires `just infra-up` + Docker; 1000-row 10-20tx not proven |
| `backend_coverage` | Kover `just backend-coverage` | available | **rejected** | Not collected in verify/QA — no artifact |
| `frontend_unit` | Vitest `just frontend-test` | available | **selected** | 135 files 1614 passed; bulk slice 3 files 18 passed |
| `frontend_e2e` | Playwright `just app-test-e2e-media-mocked` / `just frontend-test-e2e` | **available but BLOCKED** | **rejected (attempted, BLOCKED)** | `bulk-import.spec.ts` is synthetic (route mocks + injected div, no `BulkImportModal.vue` mount) — cannot prove real CSV flow |
| `frontend_lint` | Biome `just frontend-lint` | available | **rejected** | Not acceptance-relevant for bulk behavior |
| `browser` | Playwright / agent-browser | **unavailable** | **rejected** | No dev server target supplied |
| `accessibility` | axe-core / keyboard / focus-trap | **unavailable** | **rejected** | No harness; static only — must not claim PASS |
| `responsive` | viewport 320/768/1280 | **unavailable** | **rejected** | No visual viewport harness |
| `locale (i18n)` | en/es parity | available (static) | **selected (static)** | No bulk i18n keys; static inspection only → NOT TESTED per policy |
| `persistence` | R2DBC batch inserts chunked 100 / handler 50 | **BLOCKED** | **rejected** | Requires Postgres integration run |
| `full_ci` | `just ci` orchestration | available | **rejected** | Not executed in QA window |
| `exploratory` | manual ad-hoc | **unavailable** | **rejected** | No target to explore |
| `manual` | human QA | **unavailable** | **rejected** | No session |

**Selected for evidence:** `backend_unit`, `backend_architecture`, `frontend_unit` (plus static checks for a11y/responsive/i18n/persistence — recorded as `NOT TESTED` per contract, not PASS).

---

## 5. Scenario Matrix

Every scenario MUST be `PASS`/`FAIL`/`BLOCKED`/`NOT TESTED` with evidence or reason. Runner `UNAVAILABLE`→`NOT TESTED`, external prevention→`BLOCKED`. **Static inspection MUST NOT produce PASS**.

### 5.1 Bulk Behavior Scenarios (delta spec 7 req × 14 scenarios)

| # | Scenario | Category | Result | Evidence / Reason |
|---|----------|----------|--------|-------------------|
| 1 | **Gherkin 1 — per-row errors, no persistence** (POST /bulk/validate 2 VALID 1 INVALID, no DB) | happy-path | **PASS** (with warning) | `BulkPublishingHandlersTest.validate handler returns per-row errors no persistence` + `BulkValidationPipelineTest` 9/9; BDD scenario exists but DB-no-write not asserted — verify W6. Handler pure, no repo save ✅ |
| 2 | **Retry side-effect free** (same CSV twice, same response, no persist) | repeated/interrupted | **BLOCKED** | No idempotent-repeat unit test; BDD `responses MUST match` only checks `latestBulkResponse != null` (W6). Requires `just backend-bdd-fast` with DB count proof — infra unavailable. Rerun prerequisite: execute BDD with DB assertion. |
| 3 | **Gherkin 2 — chunked atomic, partial success** (2 VALID 1 INVALID → scheduled 2 failed 1, 200/207) | happy-path + state-transition | **PASS** | `BulkPublishingHandlersTest.schedule handler chunked 50-100 with 200_207 partial` 1/1 ✅; controller returns 207 when `failed>0 && scheduled>0` ✅ |
| 4 | **1000-row batch chunked 10-20 tx** | boundary + persistence | **BLOCKED** | Design `chunkSize=50 → 20 tx` + `saveRows` chunk 100 → 10 batches is structural only. No 1000-row integration run; BDD asserts `!= null` only (W6). Requires `just backend-test-postgres` with tx count + job status proof. |
| 5 | **Gherkin 3 — owner sees counts** (GET /bulk/jobs/{jobId} PARTIAL counts+errors 200) | happy-path | **BLOCKED** | `GetBulkJobHandler` workspace-scoped read exists, but no positive counts unit test; BDD scenario lacks count assertions. Observable 200 with counts not proven. Rerun: `just backend-bdd-fast` + unit for `BulkJobResult` mapping. |
| 6 | **Cross-workspace blocked** (B requests A's jobId → 404) | unauthorized/security | **PASS (with warning)** | Repo `WHERE workspace_id=:workspaceId AND id=:jobId` + `BulkJobNotFoundException` unit 404 ✅; BUT controller re-wraps `ex: BulkJobNotFound` as `PublicationValidationException → 400` (W4) — contract test will get 400 not 404. Real cross-workspace BDD glue missing. |
| 7 | **Gherkin 4 — catalog and CSV correct** (GET /templates + /{id}/csv canonical header) | happy-path | **PASS** | `BulkPublishingHandlersTest.templates handler returns canonical header` + `BulkTemplate.canonicalHeader() = bodyText,scheduledFor,timezone,media_urls,hashtags` validated backend+frontend ✅; BDD checks `body.contains header` ✅ |
| 8 | **Blank lines skipped** (2 rows + 1 blank → 2) | boundary | **PASS** | Pipeline `if (rawLine.isBlank()) continue` + `isBlankRow` + tests `skips blank lines and returns two rows` ✅; frontend `useBulkCsvParser skips blank lines` ✅ |
| 9 | **Gherkin 5 — invalid date + missing content** (not-a-date + empty body/media → INVALID_DATE + MISSING_CONTENT) | negative | **PASS** | Pipeline test `flags INVALID_DATE and MISSING_CONTENT` ✅; pipeline adds both codes before status calc ✅ |
| 10 | **Duplicate warning + invalid media** (2nd duplicate DUPLICATE warn, blocked URL INVALID_MEDIA) | negative + security | **PASS** | Pipeline `seenHashes.add → DUPLICATE` not in `hasInvalid` (VALID with warning) + `isPrivateOrInvalidUrl → INVALID_MEDIA` tests ✅ (`second duplicate warns DUPLICATE`, `invalid media url flagged`, `private 10 dot flagged`) |
| 11 | **Workspace mismatch / unverified blocked** (A→B 403/404, UNVERIFIED 403 not process) | unauthorized/security | **BLOCKED** | `requireWorkspacePath` exists but throws `PublicationValidationException → 400` not 403/404 (W4/W2). `requireEmailVerification` guard present but no workspace-mismatch unit test; BDD unverified scenario has no status assertion. Requires contract test with exact 403/404. |
| 12 | **Duplicate CSV returns 409** (same sha256(ws+principal+csvHash) → 409 + jobId) | repeated + security | **PASS** | `findByIdempotencyKey != null → DuplicateBulkImportException(jobId)` + handler test `throws 409 on duplicate sha256` ✅; migration unique `idempotency_key` ✅; controller maps to 409 body `{jobId}` ✅ (dual ProblemDetails path) |
| 13 | **Capability violation PDF** (LinkedIn PDF → INVALID capability error) | negative | **PASS** | `ProviderCapabilityValidator` throw → `CAPABILITY_VIOLATION` pipeline test `capability violation PDF flagged` ✅ |
| 14 | **Conflict warn-only** (two rows same account 10 min → both SCHEDULED hasConflict:true) | state-transition | **BLOCKED** | Spec requires `ConflictDetectionPolicy` warn-only V1; impl never calls it (W8) — currently no-op, no test. Requires wiring + unit for `hasConflict:true` dual rows. Rerun: add policy call + BDD. |

### 5.2 Cross-Cutting Acceptance Categories

| Category | Applicable | Result | Evidence / Reason |
|----------|------------|--------|-------------------|
| happy-path | Yes | **BLOCKED (partial PASS)** | See scenarios 1,3,7 PASS, but full CSV→validate→fix→schedule→poll user flow not observably proven (synthetic E2E) |
| negative | Yes | **PASS** (with gap) | Scenarios 9,10,13 PASS; SSRF negative partially PASS (private-IP deny) — see W1 |
| boundary | Yes | **PASS / BLOCKED** | Blank/BOM/quoted PASS; 1000-row boundary BLOCKED (requires Postgres) |
| repeated/interrupted | Yes | **BLOCKED** | Retry idempotence (scenario 2) + 409 idempotency PASS structurally but repeat-call observable not proven |
| unauthorized/security | Yes | **BLOCKED** | Cross-workspace + workspace mismatch 400-instead-of-403/404 (W4); SSRF allowlist missing (W1) |
| state-transition | Yes | **BLOCKED** | 200→207→SCHEDULING→PARTIAL lifecycle not polled observably; conflict warn-only not wired (W8) |
| browser | Yes | **BLOCKED** | `bulk-import.spec.ts` injects fake modal via `page.evaluate` + mocks routes; does not mount `BulkImportModal.vue`/`BulkPreviewTable.vue`/`BulkTemplatePicker.vue` via `SchedulerView.vue` wire. Evidence: `bulk-import.spec.ts:51-62` `innerHTML = '<textarea data-testid="bulk-csv-textarea">…'` . Rerun: real mount + `just app-test-e2e-media-mocked` with dev server |
| accessibility | Yes | **NOT TESTED** | Static: `BulkImportModal`/`PreviewTable`/`TemplatePicker` have zero `aria-*`, no `role="dialog"`, no focus-trap, no keyboard ESC handling beyond `click.self`; no axe run. Per contract static ≠ PASS. Rerun: axe + keyboard nav audit |
| responsive | Yes | **NOT TESTED** | `BulkImportModal` uses `max-w-3xl`/`max-h-[90vh]`/`overflow-auto` but no 320/768/1280 viewport check performed; table `overflow-auto` not proven on narrow. Rerun: viewport matrix |
| i18n | Yes | **NOT TESTED** | No `bulk` keys in `apps/web/app/src/shared/i18n/locales/en|es`; strings hardcoded English ("Bulk Import", "Validate", "Schedule", "Invalid header — expected …"). Spanish will overflow; `Undecided` locale handling not tested. Rerun: extract i18n + ES snapshot |
| persistence | Yes | **BLOCKED** | `R2dbcBulkImportJobRepository.saveRows` chunked 100 + handler chunk 50 structurally satisfies 10-20 tx, but no `just backend-test-postgres` with 1000-row insert + rollback proof. Rerun: Postgres integration |
| exploratory | Yes | **NOT TESTED** | No manual exploratory session against preview; potential drift frontend vs backend CSV parse (BOM/quoted) with no contract test (S2) |

**Fallback note:** Runner envelopes unavailable → preserved `status`/`reason` above; `UNAVAILABLE` mapped to `NOT TESTED`, `BLOCKED` to `BLOCKED` per QA contract. No prose overrides.

---

## 6. Untested Scope, Reason, and Rerun Prerequisite

| Scope | Reason | Rerun Prerequisite |
|-------|--------|--------------------|
| Full BDD 14 scenarios with real DB/status assertions | `just backend-bdd-fast` not executed; glue asserts `!=null` only (W6) | `just infra-up && just backend-bdd-fast` with `@bulk` tag; add DB-count + status-code assertions in `BulkBddSteps.kt` |
| 1000-row 10-20 tx chunking | `just backend-test-postgres` not run; repo loops per-row `INSERT` with `CAST(:errors AS jsonb)` | `just backend-test-postgres --tests "*R2dbcBulkImportJobRepositoryTest*"` + verify 10-20 tx via `TransactionSynchronization` counter |
| Real E2E CSV→validate→fix→schedule→poll | No dev server; `bulk-import.spec.ts` synthetic DOM (W7) | `pnpm --filter app dev` + `just app-test-e2e-media-mocked` (or `media-real`) with real component mount via `SchedulerView.vue` test-id flow |
| Cross-workspace & unverified 403/404 contract | `BulkPublishingController.getJob` re-wraps 404 as 400 (W4); missing unit | Fix `PublishingProblemDetailsHandler` mapping for `BulkJobNotFoundException` → 404, add WebTestClient 403/404 contract tests |
| SSRF allowlist+magic-byte+10MB | Only private-IP deny impl (W1) | Implement allowlist + `MediaAssetResolver` fetch with magic-byte + 10MB limit; add SSRF Postgres test |
| Job poll lifecycle (SCHEDULING→PARTIAL/FAILED) | `useBulkImport.pollJob` not exercised against real `/jobs/{id}` | Add E2E poll assertion + BDD for `GetBulkJob` status transitions |
| a11y, responsive, i18n | No axe/viewport/locale harness; static only | axe `pnpm --filter app test:e2e` with `@axe-core/playwright`, viewport matrix, i18n snapshot for `bulk` namespace |
| Kover coverage | `just backend-coverage` not collected | Run coverage gate and attach report |

---

## 7. Findings

Severity: `CRITICAL` > `P0` > `P1` > `P2` > `P3`. Status `open` unless fixed. CRITICAL/P0/P1 unresolved block archive per `archive_blockers`; P2/P3 are warnings unless config says otherwise. This is a behavior change → `acceptance_required` true.

| # | Title | Severity | Status | Evidence | Impact |
|---|-------|----------|--------|----------|--------|
| F1 | **No observable acceptance E2E** — synthetic DOM injection, not real BulkImportModal flow | **P1** | **open** | `bulk-import.spec.ts:51-62` creates `div[data-testid=bulk-import-modal]` with hard-coded textarea/buttons; all 4 bulk routes mocked; never mounts `BulkImportModal.vue:46 Teleport to=body` + `BulkPreviewTable.vue` + `BulkTemplatePicker.vue` | Archive blocker per config — cannot claim user acceptance; rerun real E2E |
| F2 | **BDD 14 scenarios are stubs** — no DB, counts, status-code, or tx proof | **P1** | **open** | `BulkBddSteps.kt:95-155` — `thenResponsesMustMatch` `assertTrue != null`, `thenPersistTransactions` `assertTrue != null`, no counts/status; 7 scenarios without glue beyond header | Contract gaps hide regressions; fix glue then `just backend-bdd-fast` |
| F3 | **Workspace isolation returns 400 not 403/404** | **P1** | **open** | `BulkPublishingController.requireWorkspacePath → throw PublicationValidationException → PublishingProblemDetailsHandler:114 → 400`; `getJob` catch `BulkJobNotFound → PublicationValidationException → 400` instead of handler:122 `→404` | Cross-workspace enumeration contract violated; client security handling wrong |
| F4 | **SSRF mitigation incomplete** (allowlist + magic-byte + 10MB missing) | **P1** | **open** | Design requires allowlist + magic-byte + 10MB via `MediaAssetResolver`; impl only `isPrivateOrInvalidUrl` (10/192.168/172.16/127/fe80/fc/fd/169.254 + scheme) — `BulkValidationPipeline.kt` + `PublicationCreationService.kt` | `media_urls` attacker can still SSRF to allowed public IP or oversized payload |
| F5 | **ConflictDetectionPolicy not wired** — no `hasConflict:true` warn-only | **P1** | **open** | No import/call of `ConflictDetectionPolicy` in `BulkValidationPipeline.kt` or `PublicationCreationService.kt`; design says each row MUST pass 15-min same-account `SCHEDULED`/`QUEUED` check warn-only | Spec Reuse Publishing Lifecycle violation; users get no conflict warning |
| F6 | **1000-row chunking not proven** (10-20 tx claim structural) | **P2** | **open** | Handler `chunked(50)` + repo `saveRows` chunk 100 → claimed 10-20, but `just backend-test-postgres` never run; repo per-row `INSERT` loop not batched | Perf/lock/timeout risk for 1000 rows; could still time out |
| F7 | **Bulk placeholder account dummy** `acc-bulk-placeholder` | **P2** | **open** | `BulkPublishingHandlers.kt:221` / `PublicationCreationService.kt:58-71` always `acc-bulk-placeholder` or synthetic `SocialAccount(displayName=Bulk Placeholder)`; no workspace ACTIVE lookup | Publishing will target wrong account in prod; needs real `SocialAccountRepository.findActiveByWorkspace` |
| F8 | **`bulkScheduling.enabled` docs-only flag** | **P2** | **open** | `docs/api-versioning.md` claims flag guards routes+UI; `BulkPublishingController`/`BulkImportModal.vue` have no flag check | Cannot disable bulk in prod without code change; rollback plan incomplete |
| F9 | **No a11y on Bulk UI** (no aria, focus-trap, keyboard) | **P2** | **open** | `BulkImportModal.vue` `Teleport to body` has no `role=dialog`/`aria-modal`/`aria-labelledby`, no `useFocusTrap`; `BulkPreviewTable.vue` table has `<th>` but no `scope`, inputs no `aria-label`; `BulkTemplatePicker.vue` no loading announcement | WCAG failure; keyboard users cannot use modal safely |
| F10 | **No i18n for Bulk strings** (hardcoded EN) | **P2** | **open** | `"Bulk Import"`, `"Validate"`, `"Schedule"`, `"Invalid header — expected …"` hardcoded in `BulkImportModal.vue:50,71,74,66`; no keys added to `shared/i18n/locales/en|es` | ES locale missing; longer ES text will break fixed layout; violates bilingual requirement |
| F11 | **`BulkImportRow` @AggregateRoot over-annotation** | **P3** | **open** | `BulkModels.kt:32` marks `BulkImportRow` as `@AggregateRoot` though aggregate is `BulkImportJob` (ADR-0015 sole entry point) | Confuses Modulith boundary; should be `@Entity` |
| F12 | **Frontend/backend CSV parsers duplicated, no contract test** | **P3** | **open** | Both `useBulkCsvParser.ts` and `BulkValidationPipeline.kt` implement BOM/quoted/blank/ header logic independently; `verify-report.md:S2` notes missing parity test | Drift risk; fix: extractor + cross-contract test |
| F13 | **Zero-comments policy** — compliant | **P3** | **closed** | `grep` shows 0 `//` in `bulk.ts`/`useBulk*`; backend uses no KDoc/TSDoc suppression | No action |
| F14 | **No shared/web consent coupling** — compliant | **P3** | **closed** | Bulk stays in `publishing` bounded context; `shared/web` not imported; `shared/lib/sse` + `provider-presentation` usage is allowed via `publishing.store` existing pattern | No action |

**Counts:** 8×P1/P2 open acceptance-relevant; 0 CRITICAL/P0. `F13`/`F14` closed positive confirmations.

---

## 8. Final Verdict

**`BLOCKED`**

Not `PASS`, not `PASS WITH WARNINGS`. Per `openspec/config.yaml` `qa.acceptance_required_for_behavior_changes: true` and `evidence_policy: "Do not infer product acceptance from unit, integration, BDD, or static checks alone"`, observable user/operator behavior was not proven.

**Why BLOCKED not NOT TESTED:** An executable E2E harness exists (`bulk-import.spec.ts` + Playwright) but was prevented by environment — no target/dev server/infra. That is external execution prevention → `BLOCKED` per contract (not absence of harness). Static-only checks are `NOT TESTED` and visibly recorded but do not alone determine verdict; the 8 acceptance-relevant `BLOCKED` scenarios do.

---

## 9. Verdict Rationale and Implementation Handoff

### Rationale

- **Technical verify is green** (1614 tests, 22 bulk unit, type-check, detekt) — implementation conforms structurally to 7 requirements, hexagonal layers intact, frontend module boundaries respected.
- **Acceptance is not green.** Core observable flows — CSV upload→validate preview with per-row VALID/INVALID + inline fix, chunked schedule with 200/207 partial, job status poll with real 200 counts, templates catalog+CSV download, workspace isolation (exact 403/404), 1000-row 10-20 tx, SSRF guard, idempotency 409 → existing jobId — cannot be observed because:
  - BDD 14 scenarios exist but 7 glue methods stub `assertTrue != null` without DB/status/tx proof (F2)
  - E2E injects synthetic DOM + mocks all bulk routes, never exercising real `BulkImportModal.vue` tree (F1 = P1 blocker)
  - Postgres integration never run (F6)
  - Security lifecycle gaps (F3, F4, F5, F7, F8)
- With `acceptance_required=true`, a fabricated `PASS WITH WARNINGS` would violate the QA contract ("Do not infer product acceptance from unit/BDD/static checks alone"). The correct auditable verdict is `BLOCKED`.

### Archive Gate

Archive **MUST NOT** proceed while `BLOCKED` acceptance scenarios and unresolved **P1** findings remain (config `archive_blockers`). `verify-report.md` + this `qa-report.md` both exist, so report-presence gate is satisfied, but acceptance gate is not. This is a behavior change (not docs/config-only), so the docs-only exception does not apply.

### Handoff — What must happen before Re-QA → Archive

**P1 blockers (must fix):**

1. **Real E2E** — remove synthetic `page.evaluate` injection in `bulk-import.spec.ts`; mount `BulkImportModal.vue` via `SchedulerView.vue` wire (data-testid `open-bulk-import`), drive CSV file drop → validate → assert `BulkPreviewTable` rows with `data-status` + `bulk-error-*` codes → fix inline → schedule → assert `bulk-schedule-result` → poll `GET /bulk/jobs/{jobId}` until `PARTIAL` (not mocked). Run `just app-test-e2e-media-mocked` (or `media-real`) on CI preview.
2. **BDD glue** — implement missing steps: `responses MUST match` (compare JSON), `no DB writes` (count `bulk_import_jobs`/`rows` =0 after validate), `persist in 10-20 transactions` (expose tx counter or assert via `R2dbcBulkImportJobRepository` save count), `MUST return 200 with counts`, `MUST return 404` cross-workspace with exact status, `MUST return 403` unverified, `MUST be INVALID with CAPABILITY_VIOLATION`. Then `just infra-up && just backend-bdd-fast`.
3. **Workspace isolation status** — stop re-wrapping `BulkJobNotFoundException` as `PublicationValidationException`; let `PublishingProblemDetailsHandler.kt:122` map `BulkJobNotFound → 404` and `requireWorkspacePath` mismatch → 403 (or 404 per spec) consistently; add WebTestClient contract tests for `GET /bulk/jobs/{id}` cross-workspace 404 and `POST /bulk/*` with mismatched `X-Workspace-Id` 403.
4. **SSRF** — implement allowlist + `MediaAssetResolver` magic-byte + 10MB check for `media_urls` → `INVALID_MEDIA`; add BDD/Integration for blocked `10.0.0.1` + oversized URL.
5. **Conflict warn-only** — wire `ConflictDetectionPolicy` (15-min, same account, `SCHEDULED`/`QUEUED` only) warn-only in `BulkValidationPipeline` or `PublicationCreationService` to set `hasConflict:true` without blocking; add unit test for two rows 10 min apart.

**P2 (warnings, archive may proceed with visible warning if policy allows, but fix recommended before prod):**

- F6 `just backend-test-postgres` 1000-row 10-20 tx proof (or batch with `DatabaseClient.inConnection`)
- F7 replace `acc-bulk-placeholder` with `SocialAccountRepository.findActiveByWorkspaceId` lookup
- F8 add `bulkScheduling.enabled` code guard in controller + frontend feature flag
- F9 a11y (dialog roles, focus-trap, table scope, aria-labels)
- F10 i18n keys for Bulk strings in `en`/`es`

**P3 (next change):** F11 aggregate annotation, F12 CSV parser contract test. F13/F14 already compliant (zero-comments, no shared/web coupling) — keep.

### Re-QA Prerequisites

- `just infra-up` + `just backend-bdd-fast` (14 bulk scenarios green with real assertions)
- `just backend-test-postgres` (1000-row chunk + 409 + SSRF)
- `just backend-coverage` (attach Kover artifact)
- `just app-test-e2e-media-mocked` with real `BulkImportModal` mount (no `page.evaluate` injection)
- Optional: `pnpm --filter app type-check && pnpm --filter app test:run` (1614) — already green, re-run after fixes
- Manual axe + viewport + i18n spot-checks or automated equivalents

### Risk if Shipped Without Re-QA

Shipping `BLOCKED` acceptance as-is risks: workspace isolation bypass perceived as 400 (security), SSRF to public IPs with oversized media, silent conflict collisions without warning, bulk posts landing on dummy account, and unprovable 1000-row reliability — all hidden by unit-green but observable-failed signals.

---

## Appendix — Evidence References

- Verify: `openspec/changes/dallay-413-bulk-scheduling/verify-report.md` (PASS WITH WARNINGS, 15/15 tasks, 8 WARNINGS, 1614 tests, fallback runner)
- Delta spec: `openspec/changes/dallay-413-bulk-scheduling/specs/publishing/spec.md`
- Design: `openspec/changes/dallay-413-bulk-scheduling/design.md` (8 decisions)
- Apply progress: `openspec/changes/dallay-413-bulk-scheduling/apply-progress.md`
- Backend: `BulkModels.kt`, `BulkValidationPipeline.kt: isPrivateOrInvalidUrl`, `PublicationCreationService.kt`, `BulkPublishingHandlers.kt: chunked(50)+runAtomically`, `BulkPublishingController.kt: requireWorkspacePath`, `R2dbcBulkImportJobRepository.kt: saveRows chunk 100`, `PublishingProblemDetailsHandler.kt:114/122/131`
- Frontend: `domain/bulk.ts:BULK_CANONICAL_HEADER`, `useBulkCsvParser.ts: BOM/quoted/blank`, `useBulkImport.ts: validate/schedule/poll`, `BulkImportModal.vue`, `BulkPreviewTable.vue`, `BulkTemplatePicker.vue`, `publishing.store.ts: bulkBasePath`
- Tests: `BulkModelsTest 8`, `BulkValidationPipelineTest 9`, `BulkPublishingHandlersTest 5`, `useBulkCsvParser.test.ts 9`, `useBulkImport.test.ts 6`, `BulkPreviewTable.test.ts 3` → 18 bulk slice + 1614 full
- BDD: `bulk-scheduling.feature` 14 scenarios, `BulkBddSteps.kt` stubs
- E2E: `apps/web/app/e2e/specs/bulk-import.spec.ts` synthetic `innerHTML` injection
- Config: `openspec/config.yaml` qa acceptance_required

*Evidence mode: `fallback` — `sdd-quality-runner.mjs` envelopes unavailable; direct commands + file reads above. No claim of product acceptance.*

