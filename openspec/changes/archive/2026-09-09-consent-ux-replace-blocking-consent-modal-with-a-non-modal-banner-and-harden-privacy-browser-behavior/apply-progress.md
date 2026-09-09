# Apply Progress: DALLAY-579 — consent non-modal banner remediation

Change: `consent-ux-replace-blocking-consent-modal-with-a-non-modal-banner-and-harden-privacy-browser-behavior`
Date: 2026-09-09 | Mode: openspec | Delivery: single PR, `size:exception` (user-approved, per tasks.md forecast)

## Goal of this pass

Remediate the 3 archive-blocking items (DNT E2E gap, unrun full app E2E, unrun manual
browser matrix) plus Phase 4–5 housekeeping, without archiving, syncing specs, or closing Linear.

## Completed

- [x] 4.1 — full app unit suite: **146 files / 1711 tests PASS** (`pnpm --filter app test:run`).
- [x] 4.2 — consent E2E retained scenarios green (see below).
- [x] 4.3 — **TASK-029 DNT E2E added** to `apps/web/app/e2e/specs/consent.spec.ts`:
  `mockPrivacySignals(page, { dnt: true })` → banner visible + `expectNoOverlay` → Customize
  panel analytics toggle `data-state=unchecked` → Back → Accept All → banner hidden, receipt
  `{ analytics: true, necessary: true, source: 'banner', dnt: true }`.
- [x] 4.4 — scheduler lane **42/42 PASS** (exit 0); full base-config chromium suite executed and
  results recorded (174 passed / 29 failed / 6 skipped — failures pre-existing, outside consent
  scope; NOT claimed green).
- [x] 5.2 — `ui/dialog/*`, `shared/web/*` untouched; zero `forceOpen`/`closeSettings` refs;
  `openSettings` only as unrelated local emit in `SidebarAccountSection.vue`.
- [x] 5.3 — quality gate green for all touched lanes: `type-check` PASS, `just frontend-lint`
  PASS (67 files), biome on touched E2E files PASS, unit 1711/1711, consent E2E 12/12.
- [x] Spec-layout reconciliation: copied change-root `spec.md` → `specs/privacy-compliance/spec.md`
  (byte-identical, original NOT deleted). **Flagged target domain: `privacy-compliance`**
  (banner presentation + receipt contract live there; `governance-consent-api` covers the backend
  sync endpoint only and is untouched by this change).

## RED → GREEN → REFACTOR evidence (TASK-029)

| Step | Action | Result |
|------|--------|--------|
| RED | Signal line temporarily disabled, `--grep TASK-029`, chromium | FAIL as expected at `expect(receipt.dnt).toBe(true)` (line 223) — proves DNT-sensitivity, test not vacuous |
| GREEN | Signal restored, same invocation | PASS (2.5s) |
| REGRESSION | Full `consent.spec.ts`, all 3 projects (chromium/firefox/Mobile Chrome) | 12/12 PASS |
| REFACTOR | n/a — test-only change, follows TASK-026/027 locator patterns; no prod code touched | — |

## Commands run (exit codes)

| Command | Result |
|---------|--------|
| `pnpm --filter app exec vitest run src/components/consent src/modules/settings/infrastructure/consent.store.test.ts src/layouts/AppShell.test.ts` | PASS (5 files, 51 tests) |
| `pnpm --filter app test:run` (full unit, unfiltered) | PASS (146 files, 1711 tests) |
| `pnpm --filter app type-check` | PASS |
| `just frontend-lint` | PASS (67 files) |
| `playwright test -c e2e/playwright.config.ts e2e/specs/consent.spec.ts --project=chromium --grep TASK-029` (signal disabled) | FAIL at dnt assertion (RED proof) |
| same, signal enabled | PASS |
| `playwright test -c e2e/playwright.config.ts e2e/specs/consent.spec.ts` (all projects) | 12/12 PASS |
| `playwright test -c e2e/playwright.config.ts --project=chromium` (full suite) | 174 passed / 29 failed / 6 skipped |
| `pnpm --filter app test:e2e:scheduler -- --grep @consent` | 42/42 PASS, exit 0 (one earlier transient non-zero exit with zero test failures; re-runs green) |

## Still open / BLOCKED (not fabricated)

- [ ] 4.5 Manual Brave Shields ON/OFF × states A–D — BLOCKED: requires human operator driving a
  real Brave instance (Shields toggle, visual confirmation). `Brave Browser.app` exists on this
  host but there is no manual-QA harness an agent can legitimately operate.
- [ ] 4.6 Manual Chrome/Chromium + Safari/WebKit × states A–D, EN/ES, light/dark, viewports —
  BLOCKED (same reason). Partial automated cover recorded: consent.spec.ts green on
  chromium + firefox + Mobile Chrome. Note: dashboard E2E officially excludes WebKit
  (Playwright/HAR-cookie engine limitation, documented in `apps/web/app/e2e/README.md` and
  `playwright.config.ts`), so automated Safari coverage is structurally unavailable, not merely
  skipped.
- [ ] 5.1 Durable Brave root-cause ADR / PR notes — OPEN: root cause is documented in `design.md`
  §Browser Resilience and the proposal, but no standalone ADR or PR notes exist. Orchestrator
  decision: accept `design.md` as the durable record or file an ADR at archive time.

## Draft policy-exception text (for orchestrator approval — NOT self-approved)

> **Exception request (archive gate, DALLAY-579):** proceed to `sdd-archive` with warnings 4.5/4.6
> (manual Brave Shields ON/OFF and Safari/WebKit matrix) recorded as **NOT RUN — operator
> acceptance outstanding**, instead of PASS. **Rationale:** (1) the R7 failure class (orphaned
> modal overlay via `DialogPortal`) was removed structurally — the banner path contains zero
> dialog/portal/overlay primitives, verified by component tests and `expectNoOverlay` E2E
> assertions on chromium/firefox/Mobile Chrome; worst case under aggressive filtering is an
> unstyled/hidden banner, never a blocking overlay; (2) the DNT/GPC state-D scenario now has a
> dedicated passing E2E test (TASK-029); (3) full unit (1711) and consent E2E (12/12) suites are
> green. **Residual risk:** visual/rendering quirks specific to Brave Shields cosmetic filtering
> or WebKit compositing (non-blocking by construction) could reach users unnoticed.
> **Warning to remain visible in `qa-report.md` and release notes until a human runs the matrix:**
> states A–D × Brave Shields ON/OFF, Safari, EN/ES, light/dark, 320/768/1280. **Docs-only
> exception is NOT invoked** — this is a behavior change; this text requests explicit maintainer
> acceptance, not silent passage.

## Files changed (uncommitted, for orchestrator review)

| File | Action |
|------|--------|
| `apps/web/app/e2e/specs/consent.spec.ts` | Modified: +TASK-029 DNT scenario, `mockPrivacySignals` import, header task list |
| `openspec/changes/.../specs/privacy-compliance/spec.md` | Created: byte-identical copy of change-root `spec.md` (original kept) |
| `openspec/changes/.../tasks.md` | Modified: Phase 4–5 boxes checked with dated evidence; 4.5/4.6 marked BLOCKED; 5.1 left OPEN |
| `openspec/changes/.../apply-progress.md` | Created (this file) |

No commits made. No prod code modified. Zero comments/suppressions added; no static-analysis
configuration touched.
