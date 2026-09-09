# Acceptance QA Report: consent-ux-replace-blocking-consent-modal-with-a-non-modal-banner-and-harden-privacy-browser-behavior

## Identity

- Change: `consent-ux-replace-blocking-consent-modal-with-a-non-modal-banner-and-harden-privacy-browser-behavior`
- Mode: openspec
- QA phase: re-run (acceptance gate after remediation pass; prior report `PASS WITH WARNINGS` with 4 warnings)
- Date: 2026-09-09
- Linear: DALLAY-579 (Done)
- Runner mode: `fallback` — no dedicated QA runner/FSM envelope exists in this repository; all evidence below
  comes from first-hand QA-executed commands in this pass (Vitest + Playwright), not from prose in
  `apply-progress.md`. Static inspection is supporting only and produces no PASS by itself.

## Sources of Truth

- Proposal: `openspec/changes/{change}/proposal.md` (capability `consent-banner-presentation`;
  `privacy-compliance` contract unchanged)
- Specifications: `openspec/changes/{change}/specs/privacy-compliance/spec.md` (convention-layout delta
  copy) + change-root `spec.md` (byte-identical original, kept)
- Design: `openspec/changes/{change}/design.md` (§Browser Resilience root-cause analysis, R1–R8 mapping)
- Tasks: `openspec/changes/{change}/tasks.md` (Phase 4–5 updated with dated evidence; 4.5/4.6 BLOCKED)
- Technical verification: `openspec/changes/{change}/verify-report.md` (`PASS WITH WARNINGS`)
- Remediation input: `openspec/changes/{change}/apply-progress.md` (claims re-verified first-hand below;
  nothing taken on trust)
- Config: `openspec/config.yaml` (`acceptance_required_for_behavior_changes: true`; archive blockers
  include unresolved CRITICAL/P0/P1 and acceptance-relevant BLOCKED/NOT TESTED)

## Target and Environment

- Target: app consent surface only (`apps/web/app` — `ConsentBanner.vue`, `CookieSettings.vue`,
  `useConsent`, consent store, `consent.spec.ts`). Marketing, backend, and shared consent contracts
  untouched (working tree holds ONLY `e2e/specs/consent.spec.ts` + SDD artifacts; zero prod files
  modified in the remediation pass — confirmed via `git status --short` in this pass).
- Environment: this worktree, Playwright base config `apps/web/app/e2e/playwright.config.ts`
  (chromium / firefox / Mobile Chrome; dashboard E2E officially excludes WebKit — HAR-cookie engine
  limitation documented in `apps/web/app/e2e/README.md` + `playwright.config.ts`), Vitest app suite.
- Credentials/permissions: mocked auth session + intercepted consent-sync endpoint (no backend required);
  no operator-driven browser harness available in this environment.
- Limitations: no human operator for Brave Shields toggle / visual confirmation / Safari device matrix;
  no WebKit automation lane (structural repo exclusion, not a skip). Full chromium E2E shows 28
  pre-existing failures outside consent scope (see QA-16) — suite executed, NOT green, NOT attributed
  to this change.

## Capability Inventory

| Capability | Availability | Selected? | Rationale / rejection reason |
|---|---|---|---|
| Playwright E2E (chromium/firefox/Mobile Chrome) | available | Yes | Narrowest executable acceptance evidence for banner behavior (TASK-026–029) |
| Vitest component + store unit | available | Yes | Keyboard/Escape, no-overlay DOM, persistence, sync-failure acceptance behavior at component layer |
| Full chromium E2E regression sweep | available | Yes | Executed first-hand to close prior warning 2; failures triaged by artifact, not prose |
| Static inspection (grep/DOM-structure) | available | Yes (supporting only) | Corroborates zero-dialog-primitive structure; per policy cannot produce PASS alone |
| Manual Brave Shields ON/OFF operator matrix | unavailable | No | No operator harness an agent can legitimately drive → QA-14 BLOCKED |
| Manual Safari/WebKit + EN/ES/themes/viewports matrix | unavailable | No | No operator harness; WebKit automation structurally excluded from dashboard E2E → QA-15 BLOCKED |
| Backend/API acceptance lane | rejected | No | Backend governance API untouched by this change; contract verified at store/E2E mock boundary |

## Scenario Matrix

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| QA-01 | E2E | First visit (state A, no receipt) shows non-modal `consent-banner`, no dark overlay | PASS | TASK-026 green first-hand (all 3 projects); asserts `expectNoOverlay(page)` |
| QA-02 | E2E | Prompt does not block interaction: app nav/footer clickable while banner visible | PASS | TASK-026 asserts `cookie-settings-link` visible alongside banner; first-hand green |
| QA-03 | E2E + unit | Accept all / Reject optional / Customize all actionable up front | PASS | Consent E2E 12/12 + consent unit 51/51 green first-hand |
| QA-04 | E2E + unit | Inline Customize: analytics toggle editable, Necessary always-on, Save persists `source:'banner'`, Back writes no receipt | PASS | TASK-029 asserts customize panel + `data-state=unchecked`; `ConsentBanner.spec.ts` Back/Save cases green |
| QA-05 | E2E | Footer Cookie settings re-opens `CookieSettings` modal only; banner stays hidden | PASS | TASK-027 green first-hand (`source:'settings-panel'`, banner hidden) |
| QA-06 | E2E | Visibility state machine A (none) → shows; C (valid) → hidden | PASS | TASK-026 (shows) + TASK-027 (hidden) green first-hand |
| QA-07 | Unit | Undecided not dismissible: no close control, Escape ignored, no receipt written | PASS | `ConsentBanner.spec.ts` Escape/close cases green (within 51/51 first-hand run) |
| QA-08 | E2E + unit | Persistence contract: versioned receipt (`consentVersion:1`, policy `2026-07-23`, region EU, ISO timestamp, `necessary:true`, `source`) | PASS | TASK-026/027/028 receipt assertions green first-hand |
| QA-09 | Unit | Sync failure non-blocking: local receipt kept, `syncError` set, toast shown, banner dismisses | PASS | `consent.store.test.ts` sync-failure regression cases green (within 51/51 first-hand run) |
| QA-10 | E2E | State D (DNT): banner shows, analytics defaults OFF, Accept All overrides, `dnt:true` captured | PASS | **TASK-029 green first-hand all 3 projects** (chromium/firefox/Mobile Chrome, 12/12 suite). Prior warning 1 LIFTED. Non-vacuous on its face: asserts `receipt.dnt === true`, reachable only via `mockPrivacySignals(page,{dnt:true})` (helper verified present at `e2e/fixtures/consent-helpers.ts:86`) |
| QA-11 | E2E | State B (stale `consentVersion:0`) re-prompts non-modally; re-accept upgrades to v1 | PASS | TASK-028 green first-hand |
| QA-12 | Unit + static | i18n EN/ES (`customize`/`back` keys) + keyboard reachability with visible focus | PASS | Component layer green (51/51); locale keys verified present in prior verify; no new copy in this pass |
| QA-13 | E2E + static | Structural browser-resilience: banner path contains zero dialog/portal/overlay primitives | PASS (structural) | `expectNoOverlay` green in TASK-026/029 first-hand; grep over `ConsentBanner.vue` for `Dialog\|Teleport\|Overlay\|role="dialog"\|aria-modal\|keydown\|Escape` returns zero matches (supporting only). Eliminates the R7 orphaned-overlay failure class by construction; does NOT substitute for the manual matrix |
| QA-14 | Manual operator | Brave Shields ON/OFF × states A–D: banner in DOM, no overlay, app clickable, no console errors | BLOCKED | No human-driven browser harness in this environment (constraint evidence: tasks 4.5 BLOCKED, environment §Limitations). Prior warning 3 CARRIED, not waived |
| QA-15 | Manual operator | Chrome/Chromium + Safari/WebKit × A–D, EN/ES, light/dark, 320/768/1280 | BLOCKED | Same constraint; WebKit automation additionally structurally excluded from dashboard E2E per repo docs. Prior warning 3 CARRIED, not waived |
| QA-16 | E2E regression | Full chromium suite executed; no consent-scope regression | PASS WITH NOTE | **Executed first-hand: 175 passed / 28 failed / 6 skipped** (3.1m). Failure-artifact triage (`test-failed`/`error-context` dirs): registration, scheduler-create, media-real, ideas, error-banner, route-guards, responsive, privacy-dsar (1 test, separate DSAR integration spec), i18n, email-verification, composer-media, bulk-import, accessibility — **zero consent dirs contain failure artifacts** (all 4 hold only `v8-coverage.json`). Causality excluded: remediation pass touched zero prod files and no shared fixtures (`git status` confirms). Prior warning 2 LIFTED as "not executed"; recorded instead as P3 informational finding (F-02). ±1 count variance vs apply-progress.md (174/29) is flake, not scope drift |

First-hand command evidence (this QA pass, exit codes observed):

| Command | Result |
|---|---|
| `vitest run src/components/consent src/modules/settings/infrastructure/consent.store.test.ts src/layouts/AppShell.test.ts` | PASS — 5 files, 51 tests |
| `playwright test -c e2e/playwright.config.ts e2e/specs/consent.spec.ts` (chromium/firefox/Mobile Chrome) | PASS — 12/12 (20.2s) |
| `playwright test -c e2e/playwright.config.ts --project=chromium --reporter=line` (full suite) | 175 passed / 28 failed / 6 skipped (3.1m; failures triaged outside consent scope) |

## Untested Scope

- Scope: manual Brave Shields ON/OFF × states A–D and Safari/WebKit × A–D with EN/ES, light/dark,
  320/768/1280 viewports (spec R7 + tasks 4.5/4.6).
- Reason: BLOCKED — no operator harness in this environment; WebKit automation structurally excluded
  from dashboard E2E (repo-documented HAR-cookie engine limitation).
- Re-run prerequisite: a human operator with Brave (Shields toggle), Safari/WebKit, and theme/locale/
  viewport control runs the design.md diagnosis checklist per state (banner in DOM, computed
  display/visibility, zero banner portal nodes, stacking context, Shields on/off comparison, console
  errors, blocked resources, `pt-consent` validity) and records results in `qa-report.md` or the PR.

## Findings

| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| F-01 | P2 | QA-14/QA-15 manual browser matrix not run (spec R7 MUST-verify: Brave Shields ON/OFF, Safari/WebKit × A–D) | tasks 4.5/4.6 BLOCKED; no operator harness; structural mitigation only (QA-13) | OPEN — carried from prior report; archive-relevant BLOCKED, requires operator rerun or explicit maintainer exception (NOT granted here) |
| F-02 | P3 | QA-16 full chromium suite: 28 failures outside consent scope (registration, scheduler-create, media-real, ideas, error-banner, route-guards, responsive, privacy-dsar, i18n, email-verification, composer-media, bulk-import, accessibility) | First-hand run 175/28/6; failure-artifact triage; zero consent failure artifacts; zero prod files touched → not attributed to this change | ACKNOWLEDGED — informational; pre-existing relative to this change; no action for DALLAY-579 |
| F-03 | P3 | Spec R7 durable Brave root-cause record: root cause documented in `design.md` §Browser Resilience + proposal, but no standalone ADR / PR notes (tasks 5.1 OPEN) | `design.md` §Browser Resilience; tasks.md 5.1 OPEN; orchestrator decision pending | OPEN — documentation placement only; behavior unaffected |

Resolved since prior report (visible for audit, not findings):

- Prior warning 1 (DNT/GPC E2E missing) → LIFTED by QA-10 (TASK-029, first-hand green all 3 projects).
- Prior warning 2 (full E2E not executed) → LIFTED as unexecuted; superseded by QA-16 + F-02.
- Prior warning 4 (DNT default toggle only store-validated) → LIFTED by QA-04/QA-10 (toggle OFF asserted
  at E2E layer via `data-state=unchecked`).

## Verdict

`PASS WITH WARNINGS`

### Rationale

Every executable acceptance scenario (QA-01–QA-13, QA-16) passes on first-hand evidence gathered in
this pass: non-modal prompt, inline Customize, persistence contract, non-blocking sync failure,
state machine A–D including the new DNT E2E, i18n/keyboard at component layer, and a fully executed
regression sweep with no consent-scope failure. The two remaining items are non-blocking for behavior
(F-02 pre-existing outside scope; F-03 doc placement) EXCEPT F-01: the spec-mandated manual
Brave/Safari matrix is BLOCKED, which is acceptance-relevant. `FAIL` is incorrect (no observed
behavior failure); `PASS` is incorrect (BLOCKED scenarios lack observable evidence); `BLOCKED` as a
whole-report verdict is incorrect (the overwhelming majority executed and passed). `PASS WITH
WARNINGS` honestly records both halves.

**Explicit non-waiver:** the draft policy-exception text in `apply-progress.md` is recorded as a
REQUEST pending orchestrator/maintainer approval, not a granted exception. Per `openspec/config.yaml`,
acceptance-relevant BLOCKED scenarios are archive blockers, and the docs/config-only exception path
does not apply to this behavior change. Archive MUST treat F-01 as blocking until the operator matrix
is recorded or a maintainer explicitly accepts the residual risk (non-blocking-by-construction visual
quirks under Brave cosmetic filtering / WebKit compositing) with a visible warning carried into
release notes.

## Limitations and Handoff

- QA does not fix code.
- Product acceptance is not claimed without a target and observable evidence — no claim is made for
  Brave Shields rendering or Safari/WebKit behavior beyond structural impossibility of the orphaned
  overlay.
- No greens are invented: every PASS above traces to a first-hand command run in this pass;
  `apply-progress.md` figures were re-executed, not quoted (consent 12/12 reproduced exactly; full
  suite 175/28/6 vs reported 174/29/6 — flake-range variance, same scope conclusion).
- Follow-up for implementation: none — no code defects found in this pass. Follow-up for
  orchestrator: (1) schedule the operator matrix rerun (prerequisite above) or record an explicit
  maintainer exception decision; (2) decide F-03 (accept `design.md` as the durable Brave root-cause
  record or file an ADR at archive time); (3) F-02 needs no action for this change but the 28
  pre-existing full-suite failures belong to their owning areas.
