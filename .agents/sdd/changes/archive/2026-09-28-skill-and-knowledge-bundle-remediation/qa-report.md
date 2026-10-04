## QA Report — Acceptance

## 1. Identity

- Change: `skill-and-knowledge-bundle-remediation`
- Persistence mode: `openspec` file
- Phase: `qa` (acceptance; lifecycle `apply → verify → qa → archive`)
- Date: 2026-09-28
- QA mode: `fallback` — no versioned quality-runner envelope exists in the repository
  (`sdd-quality-runner.mjs` absent, same limitation recorded in `verify-report.md`).
  Every PASS below is backed by a directly executed local command with exit code,
  not by a runner envelope and not by prose.
- Worktree: branch `feat/skills-and-meta-core`. QA modified no source files.
  Only this report and `state.yaml` bookkeeping were written.
  Unrelated worktree changes (publishing, infra, `.env.example`, app e2e) were
  preserved untouched; the database changelog path is clean (Section 5,
  QA-UMB-3).

## 2. Source artifacts and technical verification handoff

Inputs read:

- `openspec/changes/skill-and-knowledge-bundle-remediation/verify-report.md`
  (verdict **PASS WITH WARNINGS**, 2026-09-28; 7 prior CRITICALs resolved,
  WARNINGs W-1..W-5 carried)
- `proposal.md`, `design.md` (AD-1..AD-9), `tasks.md` (53 tasks + CK-1..CK-5),
  `state.yaml` (`current_phase: verify`, `next: qa`), `apply-progress-3.md`
- `openspec/config.yaml` (archive blockers: unresolved CRITICAL/P0/P1;
  acceptance-relevant BLOCKED/NOT TESTED)

Handoff accepted from `sdd-verify`: all executable gates green locally;
remote CI / branch-protection evidence NOT RUN; WARNINGs W-1..W-5 documented
as non-blocking residuals. This report independently re-executed the
observable acceptance checks instead of trusting verify prose.

Scope reminder: governance/knowledge-bundle only. `publication-calendar-sse`
is absent from both `openspec/changes/` and `openspec/changes/archive/`
(stays withdrawn; nothing in this change reintroduces it — no
publishing/calendar product file is touched by the change's own diff;
QA-UMB-3).

## 3. Target, environment, permissions, limitations

- Target: no application under test and no general test runner for this
  change. The operable surface is the governance tooling itself:
  `skill-doctor.mjs`, `skill-comment-scan.mjs`, `spring-scrub-rg.mjs`,
  `regen-skill-registry.mjs`, the 4 focused test files, the
  `skill-doctor.yml` workflow, and the `just` lane map. Acceptance therefore
  means: an operator can run the documented gates, they pass, they fail when
  drift is injected, and every command the Playwright skill cites exists.
- Environment: local sandbox at `/Users/acosta/Dev/dallay/profiletailors.com`,
  Node runtime, `rg` available. No GitHub credentials, no Anthropic API key,
  no GitHub Actions remote execution, no branch-protection UI access.
- Permissions: read-only inspection plus writing this report and `state.yaml`
  bookkeeping. No commits or pushes performed.
- Limitations (visible warning): remote CI evidence, branch-protection
  required-check proof (TASK-047), and in-CI contextual sub-agent execution
  cannot be produced from this environment; they are recorded as BLOCKED
  scenarios (Section 5) with post-merge follow-ups (Section 7), not as passes.
  Static file-content inspection (rg/find/cat reads) is recorded as
  NOT TESTED per the QA contract — prose cannot promote it to PASS.

## 4. Capability inventory

| Capability | Availability | Selected | Rationale |
|---|---|---|---|
| Deterministic governance gates (doctor strict, registry `--check`, comment-scan gate scopes, spring-scrub, drift injection on temp copy) | available | yes | Directly operable; exit-code evidence |
| Focused governance tests (`node --test`, 4 files) | available | yes | Directly operable; 12/12 pass/fail evidence |
| Workflow static execution (YAML parse, job/path-filter/permission assertion) | available | yes | Parser-executed, not prose |
| `just -l` lane existence map vs Playwright skill commands | available | yes | Operator-observable command contract |
| VCS worktree/changelog state (`git status`, `git diff --check`) | available | yes | Tool-executed observable state |
| Browser E2E (Playwright suites) | unavailable | no | Product E2E execution is out of scope for a governance-only change; lanes verified to exist, not executed |
| Backend builds/tests, product Vitest, coverage | rejected | no | No product code is touched by this change; broad builds would prove nothing about acceptance and were explicitly excluded |
| Remote GitHub Actions / branch-protection API / CI sub-agent execution | unavailable (no credentials/UI from sandbox) | no | Recorded as BLOCKED scenarios, not attempted |
| Accessibility, responsive, locale, persistence, exploratory manual QA | rejected | no | No user-facing surface changes; nothing observable to exercise |
| Static file-content reads (rg/find/cat) as pass evidence | available | no (as PASS basis) | QA contract forbids PASS from static inspection; used only as NOT TESTED observations |

## 5. Scenario matrix

Result contract: `PASS` only with executed-tool evidence. `BLOCKED` for
target/credential/permission/environment constraints. `NOT TESTED` where no
executable capability exists (including static-inspection-only observations).

### Umbrella

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-UMB-1 | Operator full-gate pass: doctor strict + registry check + focused tests + comment-scan gate scopes + spring-scrub 15/15 all green in one session | PASS | Doctor strict exit 0 (`Inspected 66 skills; PASS`); registry `--check` exit 0 (`Registry is current: 66 top-level skills`); `node --test` over the 4 governance files: 12 pass, 0 fail; comment-scan default scope exit 0 (`clean`) and contract scope (`--paths .agents --paths docs/architecture/adr`) exit 0; spring-scrub exit 0 on all 15 flat skill paths (Section 5, P0-B row) |
| QA-UMB-2 | Drift injection is blocked (CK-3 semantics, operator-reproducible) | PASS | Temp copy of `.agents/skills` (worktree untouched) with `CVIX` appended to `vue/SKILL.md`: doctor `--fail-on contamination` exits 1 with `BLOCK contamination …/vue/SKILL.md: known contamination: CVIX`; clean tree exits 0. Gate blocks drift |
| QA-UMB-3 | Change scope touches no product runtime and preserves unrelated state | PASS | `git status --porcelain -- server/smp/src/main/resources/db/changelog` empty (changelog preserved); `git diff --check` exit 0; unrelated worktree edits (publishing/infra/app-e2e) left untouched — QA wrote only `qa-report.md` + `state.yaml` |
| QA-UMB-4 | `publication-calendar-sse` is not reintroduced | PASS | Absent from `openspec/changes/` and `openspec/changes/archive/`; change diff contains no publishing/calendar product file |

### P0-A skill-discovery-identity

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P0A-1 | Doctor strict gate over identity/metadata over all skills | PASS | Exit 0, `Inspected 66 skills; PASS` with `--fail-on contamination,missing-frontmatter,missing-metadata,broken-paths,identity,invalid-values` |
| QA-P0A-2 | Registry truthfully reflects the executable bundle | PASS | `regen-skill-registry.mjs --check` exit 0, `66 top-level skills`, in agreement with doctor count |
| QA-P0A-3 | Flat layout (66 direct skills, 0 nested `SKILL.md`, no `shadcn-vue`) | NOT TESTED | Observed: 66 direct folders, 66 direct `SKILL.md`, 0 nested, `shadcn-vue/SKILL.md` absent — but evidence is static directory inspection only, which cannot produce PASS per contract |
| QA-P0A-4 | Historical 67-count text reconciled in proposal/specs/design | NOT TESTED | Static prose comparison only. Reconciliation note exists in `tasks.md` (TASK-002); proposal/specs/design still say 67. Tracked as F-QA-4 (P3) |

### P0-B backend-semantics

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P0B-1 | Spring reactive scrub clean on every Spring target | PASS | `spring-scrub-rg.mjs .agents/skills/<target>` exit 0 with `PASS: clean` for all 15 flat targets (`spring-boot`, `spring-boot-actuator`, `spring-boot-ai-mcp-server-patterns`, `spring-boot-api-standards`, `spring-boot-cache`, `spring-boot-data-neo4j-reactive`, `spring-boot-messaging`, `spring-boot-openapi`, `spring-boot-project-bootstrap`, `spring-boot-resilience`, `spring-boot-saga-pattern`, `spring-boot-security`, `spring-boot-testing-core`, `spring-boot-testing-integrations`, `spring-boot-testing-webflux`). Note: bare-name invocation (`mjs security`) resolves to the pre-flatten path and exits 2; the flat-path invocation above is the correct operator form for the current layout |
| QA-P0B-2 | Ghost `@ApplicationService` marker gone; canonical ports wording in AGENTS.md/ADR-0002 | NOT TESTED | Observed 0 hits for `ApplicationService` in `spring-boot/SKILL.md` with real `common.domain.Service` cited; 0 hits for `inward-facing` in AGENTS.md with canonical `domain-defined ports` present; `Ports location (added 2026-09-27)` present in ADR-0002 — all static-inspection evidence only |
| QA-P0B-3 | Scrubber regression suite guards the legacy-marker semantics | PASS | `spring-scrub-rg.test.mjs` 4/4 green inside the 12/12 focused run (clean pass, flagged outside marker, allowed inside `legacy:servlet`, no scope bleed) |

### P0-C external-contamination

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P0C-1 | Contamination gate blocks all 9 enforced tokens | PASS | Doctor `--fail-on contamination` exit 0 over 66 skills; doctor token list covers the 5 legacy + 4 Resume bounded-context tokens and its `detects every legacy external-contamination token` test passes inside the 12/12 run; QA-UMB-2 proves the gate fires on injection |

### P0-D playwright-rebuild

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P0D-1 | Playwright skill frontmatter is valid executable metadata | PASS | Independent YAML parse: `name: playwright`, metadata keys `category/family/source/version` present |
| QA-P0D-2 | Every lane the skill cites exists in the command hub | PASS | `just -l` lists `frontend-test-e2e`, `app-test-e2e-media-mocked`, `app-test-e2e-media-real`, `app-test-e2e-media`, `frontend-test-e2e-headed/ui/report`, `playwright-install`; skill documents the same set plus `scripts/run-playwright.mjs`, HAR doctrine (`routeFromHAR`, `UPDATE_HAR`), and admin as a real mocked lane. No E2E executed (out of scope) |
| QA-P0D-3 | Consent-contract literal precision (`pt-consent` / `consentVersion` / `EXPECTED_CONSENT_VERSION`) | NOT TESTED | Static read confirms the literals are absent (W-5 preserved); the referenced realities exist (`shared/web/validation/consent.ts`, both consent specs). Precision fix tracked as F-QA-5 (P3) |

### P0-E ui-governance-precedence

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P0E-1 | Precedence chain declared with tie-breaker | NOT TESTED | Observed `## UI precedence chain` at `.agents/DESIGN.md:141` — static-inspection evidence only; no dedicated executable scenario exists for this static contract |

### P1-A version-policy

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P1A-1 | No hardcoded version literals in the bundle | NOT TESTED | Observed 0 hits for the AD-6 literal set across skills/AGENTS/DESIGN — static-inspection evidence only; no automated literal detector exists (verify SUGGESTION carried) |

### P1-B modern-best-practices

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P1B-1 | Structural remediation observable (`shadcn-vue` gone, scope wording, re-anchored examples) | NOT TESTED | Observed `shadcn-vue/SKILL.md` absent and scope wording present — static inspection only. Closure remains hard-dependent on the P1-D deterministic gate (operational, QA-P1D-3), consistent with `blocked-verify` lifted for the deterministic part |

### P1-C comment-cleanup

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P1C-1 | Comment gate green on its CI scope | PASS | `skill-comment-scan.mjs --paths .agents/skills --allowlist …` exit 0 (`clean`); contract scope `--paths .agents --paths docs/architecture/adr` exit 0; scanner fixture tests green inside the 12/12 run |
| QA-P1C-2 | Broad `--all` scope cleanliness (8048 pre-existing violations) | NOT TESTED | No executable remediation claimed; recorded debt (W-1 → F-QA-1, P3). CI runs the default scope only |

### P1-D automated-skill-doctor

| ID | Scenario | Result | Evidence / reason |
|---|---|---|---|
| QA-P1D-1 | Workflow is parseable with the 4 deterministic jobs, correct triggers and least-privilege permissions | PASS | YAML parse yields jobs `skill-doctor`, `comment-scan`, `focused-tests`, `registry-drift`; `pull_request` path filters cover `.agents/**`, `**/SKILL.md`, `AGENTS.md`, the ADR-0025 file, the workflow itself; `focused-tests` runs exactly the 4 governance test files; no `llm-audit` job present (contextual review is out-of-band by design) |
| QA-P1D-2 | ADR-0025 exists, is indexed, and the sub-agent contracts declare the designed models | NOT TESTED | Observed ADR-0025 present and linked at `adr/README.md:52`, `skill-doctor.md` declares `model: sonnet`, `comment-cleanup.md` declares `model: haiku` — static reads only. The `haiku`-vs-`sonnet` spec/design conflict and the missing sub-agent smoke test are tracked as F-QA-3 |
| QA-P1D-3 | Mark workflow as required check (TASK-047) proven against branch protection | BLOCKED | Requires GitHub branch-protection UI/API after merge; impossible from the sandbox (no credentials). Definitionally post-merge — tracked as F-QA-2 |
| QA-P1D-4 | Contextual `llm-audit` executes inside CI on a touching PR | BLOCKED | No Anthropic credential is wired into the GitHub Actions environment; the workflow deterministically contains no such job and the sub-agent file scopes contextual review as out-of-band. Tracked as F-QA-2 |
| QA-P1D-5 | Remote `skill-doctor.yml` run observed green on the change PR | BLOCKED | No remote execution available from this environment; local job commands are the same scripts proven green in QA-UMB-1 |

## 6. Untested scope, reason, rerun prerequisite

| Scope | Reason | Rerun prerequisite |
|---|---|---|
| Static-contract observations (QA-P0A-3/4, QA-P0B-2, QA-P0D-3, QA-P0E-1, QA-P1A-1, QA-P1B-1, QA-P1D-2) | No executable capability exists for prose/metadata presence; contract forbids PASS from static inspection | A dedicated executable check (doctor rule, focused test, or gate script) covering each prose claim; until then these remain NOT TESTED observations |
| Product E2E / backend builds / Vitest / coverage | Governance-only change; broad product builds prove nothing about acceptance and were excluded by scope | Only if a future change touches product runtime |
| Remote CI, branch protection, in-CI contextual review (QA-P1D-3/4/5) | Environment constraint: no GitHub credentials, API access, or CI secrets from sandbox | Post-merge: GitHub Actions run of `skill-doctor.yml` on the PR + manual TASK-047 required-check marking |
| Sub-agent JSON smoke contract (REQ-SD-007) | No smoke test exists — the one TDD hole (W-3) | Author the smoke test, then rerun `node --test` |

## 7. Findings

No CRITICAL, P0, or P1 findings. All findings inherit verify WARNINGs W-1..W-5;
each states whether it blocks archive.

| ID | Inherits | Severity | Status | Blocks archive? |
|---|---|---|---|---|
| F-QA-1 `--all` comment debt (8048 pre-existing violations in supporting assets/references) | W-1 | P3 | Open, accepted debt | No — CI gate scope is green (QA-P1C-1); follow-up housekeeping change (`skills-comment-broad-scope`) |
| F-QA-2 Contextual CI out-of-band + TASK-047 manual required-check pending (QA-P1D-3/4/5 BLOCKED) | W-2 | P2 | Open, operational follow-up | No — BLOCKED items are environment constraints, not defects; TASK-047 is definitionally post-merge (workflow must land first); deterministic blocking is proven (QA-UMB-2). Post-merge: confirm Actions run + mark required check |
| F-QA-3 Sub-agent contract gaps: no JSON smoke test (REQ-SD-007 NOT TESTED); spec(`haiku`)-vs-design(`sonnet`) model conflict, implementation follows design as later authority | W-3 | P2 | Open | No — deterministic gate is unaffected; reconcile spec/design wording and add the smoke test as a fast follow-up |
| F-QA-4 66-vs-67 documentation drift (executable bundle and registry agree on 66; proposal/specs/design still say 67; reconciliation note only in `tasks.md`) | W-4 | P3 | Open | No under the docs/config-only exception, with this visible warning — but the one-line-per-artifact count correction is recommended before archive so the source spec is not left stale |
| F-QA-5 Playwright consent precision (`pt-consent` / `consentVersion` / `EXPECTED_CONSENT_VERSION` literals absent; REQ-PR-006 precision gap) | W-5 | P3 | Open | No — surfaces, commands, HAR doctrine, and consent-spec pointers are truthful; one-line precision fix as fast follow-up |

## 8. Final verdict

**PASS WITH WARNINGS**

## 9. Verdict rationale and implementation handoff

- Every acceptance scenario with an executable capability PASSES on fresh
  local evidence: doctor strict exit 0 (66 skills), registry `--check` exit 0
  (66 entries, agreeing), 12/12 focused governance tests, comment-scan gate
  scopes exit 0, spring-scrub exit 0 on all 15 flat targets, drift injection
  exits 1 on a temp copy (blocking proven) while the clean tree exits 0,
  workflow parses with the 4 deterministic jobs and correct path filters,
  Playwright frontmatter parses with canonical metadata, all cited `just`
  lanes exist, Playwright admin is truthfully a real mocked lane,
  contamination gate covers all 9 tokens, changelog preserved,
  `git diff --check` clean, `publication-calendar-sse` not reintroduced.
- The BLOCKED scenarios (QA-P1D-3/4/5) are remote-environment constraints,
  and the NOT TESTED scenarios are static-contract observations. This change
  touches no product runtime — it is docs/config-only (skills, scripts,
  agents, ADRs, one workflow) — so the archive gate's docs/config-only
  exception applies with explicit rationale: nothing in the BLOCKED/NOT
  TESTED set reflects a product defect, and the single merge-gating
  operational item (TASK-047) can only be performed after merge.
- WARNINGs W-1..W-5 are carried as findings F-QA-1..F-QA-5 (P2/P3, none
  blocking). Recommended before archive: the F-QA-4 count-text correction
  (66, not 67) in proposal/specs/design so the source spec is not archived
  stale; F-QA-5 consent literals and the F-QA-3 smoke-test/model
  reconciliation as fast follow-ups. Post-merge: confirm the Actions run and
  perform TASK-047.
- Handoff to `sdd-archive`: `verify-report.md` (PASS WITH WARNINGS) and this
  `qa-report.md` are both present. Archive may proceed under the
  docs/config-only exception with the visible warnings above; unresolved
  items are P2/P3 operational follow-ups, and no CRITICAL/P0/P1 remains.
- QA note on `state.yaml`: the file as handed over did not parse as YAML
  (two pre-existing unquoted scalars from earlier phases: the
  `files_rewrite_from_scratch` value containing `SKILL.md: 377` and the
  `path_filters` flow sequence with bare `**` globs). QA applied minimal
  quoting-only fixes to those two lines plus its own `qa_report` value and
  the `current_phase`/`next`/`completed` bookkeeping. No semantic content
  was altered; the file now parses (`YAML-OK`, `current_phase: qa`,
  `next: archive`).
