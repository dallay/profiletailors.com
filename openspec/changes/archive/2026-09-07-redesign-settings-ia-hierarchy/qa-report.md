# QA Report — redesign-settings-ia-hierarchy

## 1. Identity

- Change: `redesign-settings-ia-hierarchy`
- Mode: standard (Strict TDD not active for QA; no runner envelopes — `fallback` recorded, see §3)
- Phase: `qa` (after `verify` PASS, before `archive`)
- Date: 2026-09-07
- Executor: `sdd-qa` sub-agent, worktree `/Users/acosta/Dev/dallay/worktrees/feat-redesign-settings-center-account-security-preferences`
- QA constraint honored: no production code modified; no commits; no pushes.

## 2. Source artifacts and technical verification handoff

| Artifact | Status | Notes |
|---|---|---|
| `proposal.md` | Read | Scope: hero h1 → `display-lg` + `nav.settings` eyebrow + rewritten subtitle EN/ES ≤60 chars; hero `<aside>` removed; new Preferences Card (language only) between Channels/Workspace grid and PrivacySection; testids preserved. NOT-change set: AppHeader, sidebar account menu, theme toggle, Privacy/AccountClosure sections, router, stores, `scheduler-settings.spec.ts`. |
| Delta specs (`specs/`) | **Absent** | Change directory contains only `proposal.md`, `state.yaml`, `verify-report.md`. No `design.md`, no `tasks.md`, no delta specs were produced for this change. QA proceeded on proposal + verify-report per orchestrator scope; absence recorded as limitation, not blocker. |
| `design.md` | **Absent** | Same as above. |
| `tasks.md` | **Absent** | Same as above. |
| `verify-report.md` | Read, verdict **PASS** | 28/28 focused vitest, `vue-tsc` type-check PASS, scoped Biome PASS, full build NOT RUN (judged unnecessary). Two INFO observations, no defects. |
| `state.yaml` | Read | `current_phase: verify`, `next: qa`. |
| `openspec/config.yaml` | Read | QA evidence policy applies: product acceptance MUST NOT be inferred from unit/integration/BDD/static checks alone; acceptance-relevant BLOCKED/NOT TESTED normally blocks archive. |

## 3. Target, environment, permissions, and limitations

- Target: `apps/web/app` Settings surface (`/settings`, `SettingsView.vue` + `en/es` settings locales). No target URL or deployed environment was supplied; **no dev-server/browser run was performed** (per orchestrator scope: skip unless trivially available — it was not started in this session, and starting Portless + seeding auth state exceeds the cheap-evidence budget).
- Runner/FSM: no runner envelopes exist for this change → recorded as `fallback`: QA evidence is vitest component specs + `rg` static selector/template probes executed in THIS session. Static probes alone never produce PASS per contract; every reported PASS below is backed by an executed vitest run, with static grep used only as supporting evidence or for selector-compatibility checks explicitly marked as such.
- Capabilities available in this worktree: Vitest (executed), `rg` template/selector probes (executed), `vue-tsc`/Biome (deferred to verify-report, not re-run), Playwright E2E (NOT RUN — full suite not cheap here; see §6), live browser/a11y axe run (NOT RUN — no server started).

## 4. Capability inventory

| Capability | Disposition | Rationale |
|---|---|---|
| Vitest component/unit specs (settings + layout guards) | **Selected** | Cheapest executable observable evidence; 5 focused settings specs + 5 regression specs run in this session. |
| `rg` selector/template probes (testids, ordering, a11y attributes, copy) | **Selected** | Supports selector-compatibility (TC-22) and template spot-checks; never standalone PASS. |
| Playwright E2E (`scheduler-settings.spec.ts` TC-22, full suite) | **Rejected (NOT TESTED)** | Explicitly out of cheap-evidence budget per orchestrator scope; browsers/servers not already running. |
| Live dev-server browser verification (visual hierarchy, real toggle→persist→reload) | **Rejected (NOT TESTED)** | No target supplied; starting the stack exceeds scope. |
| Full `axe` accessibility run | **Rejected (NOT TESTED)** | No harness support cheaply in this lane; template spot-checks substituted (see scenario A5). |
| Full build / full unit suite | **Rejected** | Covered by verify handoff (type-check + 28/28); out of QA cheap-evidence scope; no code changed since verify (`git status` confirms only the expected 4 modified + 3 new spec files). |

## 5. Scenario matrix

Result values: `PASS` (executed here with evidence), `FAIL`, `BLOCKED`, `NOT TESTED`. Static inspection never yields PASS.

### A1 — Hero hierarchy: eyebrow + prominent title + subtitle (EN)

- Result: **PASS**
- Evidence: `SettingsView.hero.spec.ts` 7/7 green in this run — `settings-page-title` testid inside hero, `display-lg` token on h1, mono eyebrow above h1 bound to `nav.settings`, subtitle bound to `settings.subtitle`, EN copy ≤60 chars, overview pill kept. Template confirms order: eyebrow line 168 → h1 line 170 (`display-lg text-[36px] leading-[1.1]`).

### A2 — Hero subtitle copy exact (EN + ES, ≤60 chars)

- Result: **PASS**
- Evidence: hero spec equality/length tests green; locale files read this session: EN `Connect a channel, name your workspace, change the surface.` (59 chars), ES `Conecta un canal, nombra tu workspace, ajusta la superficie.` (60 chars) — exact match to proposal-approved copy.

### A3 — Language switch lives in Preferences Card; hero aside gone

- Result: **PASS**
- Evidence: `SettingsView.locale.spec.ts` 4/4 green — segmented control absent from hero overview, present inside `settings-preferences-panel`, legacy `<aside>` wrapper not rendered. `rg 'aside' SettingsView.vue` returns zero matches this session.

### A4 — Preferences Card ordering: after Channels/Workspace grid, before Privacy; eyebrow visible

- Result: **PASS**
- Evidence: `SettingsView.preferences-card.spec.ts` 5/5 green (eyebrow in card, card-header slot, segmented control inside card, card after grid, card before privacy section). Template line order confirms: `settings-channels-panel` (182) → `settings-preferences-panel` (370, `preferencesEyebrow` at 375) → `PrivacySection` (424) → `AccountClosureSection` (426).

### A5 — Language toggle persists and segmented control reflects `currentLocale`

- Result: **PASS WITH LIMITATION (component-level only)**
- Evidence: `settings-language-en/es` testids resolve inside the panel (locale + preferences-card + updated `SettingsView.spec.ts` assertions green); `SettingsView.spec.ts` exercises the mocked-locale render path. Template keeps token-identical segmented markup (`role="radiogroup"`, `:aria-label`, `sr-only` real radios, `focus-visible` styles at lines 386–414). **Limitation:** the real click→store→persist→reload interaction is acceptance-relevant and was NOT executed here — it is owned by E2E TC-22 (see A9, NOT TESTED).

### A6 — No regressions: channels panel states, workspace rename, privacy, closure

- Result: **PASS**
- Evidence: `SettingsView.spec.ts` 10/10 (empty channels + LinkedIn CTA, connection flow, normalized/unknown channels, needsReconnect badge, callback contract, direct-visit load, rename timer behavior, overview layout) + `SettingsView.validation.spec.ts` 2/2 (blank-rename guard, trim-before-submit) + untouched `PrivacySection.spec.ts` 5/5 + `AccountClosureSection.spec.ts` 8/8 — all green in this session (41/41 across the two runs covering these files).

### A7 — No regressions: header / sidebar / theme toggle unchanged

- Result: **PASS**
- Evidence: `git status` this session shows zero modifications to `AppHeader.vue`, `SidebarAccountSection.vue`, `ThemeToggle.vue`, `PrivacySection.vue`, `AccountClosureSection.vue`, `scheduler-settings.spec.ts`. Guard specs green: `AppHeader.test.ts` 4/4, `SidebarAccountSection.test.ts` 8/8, `ThemeToggle.test.ts` 2/2.

### A8 — Accessibility probes (radiogroup label, real radio semantics, focus-visible, `role=alert`)

- Result: **PASS (spot-check, template-level)**
- Evidence: `rg` this session — `role="radiogroup"` + `:aria-label="$t('settings.languageLabel')"` (lines 386–387), `sr-only` native radio inputs (399, 414), `focus-visible:outline` styles on both language labels (391, 406), `role="alert"` retained on channels error (280), rename error (350), `dsar-error` (PrivacySection 63), `closure-error` (AccountClosureSection 78). No full axe run (no harness support cheaply — recorded, not claimed).

### A9 — E2E TC-22 (locale persistence) selector compatibility

- Result: **PASS (static compatibility) + NOT TESTED (execution)**
- Evidence (static): TC-22 (`scheduler-settings.spec.ts:53–80`) clicks `page.getByTestId('settings-language-{locale}')`; both testids still resolve in `SettingsView.vue` (lines 390, 405) — grep-verified this session. The spec file itself is untouched by design. Execution of TC-22 / the Playwright suite was NOT RUN per scope (not already configured-and-cheap in this worktree); selector presence does not prove the E2E passes.

### A10 — Real-browser visual hierarchy + responsive + i18n rendering + exploratory

- Result: **NOT TESTED**
- Reason: no dev-server/browser target in this session (see §3). No evidence claimed.

## 6. Untested scope, reason, and rerun prerequisite

| Untested scope | Reason | Rerun prerequisite |
|---|---|---|
| TC-22 execution + full Playwright settings lane | Skipped per cheap-evidence scope; browsers/servers not running | `just app-test-e2e-media-mocked` (or the configured settings E2E lane) with a seeded session, then re-run TC-22 |
| Real-browser toggle→persist→reload, visual hierarchy, responsive, ES rendering | No target supplied; server start out of budget | Start `just dev-frontend` (Portless), navigate `/settings`, toggle EN/ES, reload, screenshot EN+ES at desktop + mobile widths |
| Full axe/a11y run | No cheap harness in this lane | Add `axe-core` Playwright check or manual screen-reader/keyboard pass on the Preferences Card |
| Full build / full unit suite re-run | Deferred to verify handoff; nothing changed since | `just app-build` + full `app` vitest if archive requires it |

## 7. Findings

| # | Severity | Finding | Status |
|---|---|---|---|
| F1 | **P3** | Eyebrow + h1 both render the identical `nav.settings` string (duplicate visible text, noted INFO in verify-report) | Open (accepted, out of scope per brief; future copy pass) |
| F2 | **P2** | Click→store→persist→reload language interaction has no executed E2E evidence in this change cycle (TC-22 not run); component-level coverage only | Open — recommended follow-up: run TC-22 lane before or alongside archive |
| F3 | **P3** | Pre-existing Biome infos in untouched `media-api.legacy-removal.spec.ts` (last touched in 2102b915) | Pre-existing, not introduced here, not fixed (correctly left alone) |

No `CRITICAL`, `P0`, or `P1` findings. No `FAIL` scenarios. QA introduced zero code changes (no probes added, nothing to delete).

## 8. Final verdict

**PASS WITH WARNINGS**

## 9. Verdict rationale and implementation handoff

- All 55 executed tests pass in THIS session: 28/28 focused settings specs + 13/13 Privacy/AccountClosure specs + 14/14 header/sidebar/theme guard specs. Template/selector probes confirm hero hierarchy, exact EN (59) / ES (60) copy, aside removal, card ordering with eyebrow, preserved TC-22 testids, and retained a11y attributes. Protected files are byte-untouched per `git status`.
- Warnings (not failures): browser-level acceptance (A10) and TC-22 execution (A9-exec) are NOT TESTED — no server/E2E target was available in this lane, and config evidence policy forbids inferring product acceptance from component specs alone. F2 (P2) records the one acceptance-relevant gap: the real toggle→persist→reload path.
- Handoff: implementation matches proposal on every "What changes" / "What does NOT change" item; verify PASS stands uncontradicted. Archive gate must weigh §6 NOT TESTED items against policy: this is a template/locale/test-only change with 55/55 green + verify's type-check/Biome, so archive MAY proceed with this report's explicit rationale and visible warning, with F2 (run TC-22 lane) as the recommended follow-up. If archive policy requires zero acceptance-relevant NOT TESTED, run the TC-22 lane first, then archive.

## 10. Addendum — TC-22 lane executed (orchestrator session, 2026-09-07)

F2 is now CLOSED with real-browser evidence. The orchestrator ran the settings E2E lane after freeing port 5173 (an orphaned `astro preview` from worktree `pr-961-dallay-570`, PID 77623, 11.5h old, was squatting the HAR-recorded port; killed with user approval):

- Root cause of the first failed attempt confirmed environmental, not the change: HAR entries are recorded against `http://localhost:5173`, the squatter forced a 5199 run, replay did not match, login POST went unanswered. Reran on 5173: green.
- `e2e/specs/scheduler-settings.spec.ts` full file on `playwright.scheduler.config.ts` (Chromium, backend-free HAR replay + scheduler mocks, own vite dev server): **2 passed (TC-21 theme persistence 4.2s, TC-22 locale persistence 4.9s)**.
- TC-22 exercises exactly the relocated control: clicks `settings-language-{en,es}` inside the new Preferences Card, asserts `localStorage pt_settings_v1` persistence, reloads, re-asserts persistence and `settings-shell` visibility. The moved language switcher works end-to-end in a real browser.
- Verdict upgraded on this axis: no acceptance-relevant NOT TESTED items remain from §6 except live visual/axe review, which stays an accepted P3 gap for a template-only change with component + E2E coverage.
