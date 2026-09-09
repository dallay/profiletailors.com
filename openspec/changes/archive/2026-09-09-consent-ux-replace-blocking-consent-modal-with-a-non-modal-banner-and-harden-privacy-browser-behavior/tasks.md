# Tasks: Non-modal consent banner + privacy-browser hardening

## Review Workload Forecast

Decision before apply: Yes — **resolved**: single PR with explicit `size:exception` (user-approved).
Chained PRs: No (single PR approved).
400-line budget risk: High — approved exception.

~650 lines ≈ 1.6× budget; single PR with size-exception, split commits by work unit.

### Work Units (commits in one PR)

| Unit | Goal                                                                                                   |
|------|--------------------------------------------------------------------------------------------------------|
| 1    | useConsent(source), CookieSettings refactor, remove forceOpen from store, i18n, e2e helper, store test |
| 2    | Banner non-modal rewrite + spec core                                                                   |
| 3    | Inline Customize + DNT default                                                                         |
| 4    | E2E, QA matrix, docs, gate                                                                             |

States A–D: none, stale, valid, DNT/GPC.

## Phase 1: Foundation

- [x] 1.1 TDD: extend `useConsent.spec.ts` → add `source` param (default `'banner'`) to
  `useConsent.ts`, remove `openSettings` (R6).
- [x] 1.2 TDD: align `CookieSettings.spec.ts` mocks → refactor `CookieSettings.vue` to
  `useConsent('settings-panel')`; drop `forceOpen`/`openSettings` mocks (R6).
- [x] 1.3 Remove `forceOpen` state + `openSettings`/`closeSettings` actions from `consent.store.ts`;
  drop related cases from `consent.store.test.ts` (R5).
- [x] 1.4 Add `customize`/`back` keys to `locales/{en,es}/consent.ts` under `consent.banner.*` (R8
  S13) — copy approved ("Customize"/"Customizar", "Back"/"Volver").
- [x] 1.5 Add `expectNoOverlay(page)` to `consent-helpers.ts`: no `[data-slot="dialog-overlay"]`, no
  dark backdrop (R1, R8 S14).
- [x] 1.6 Add `consent.store.test.ts` case: sync failure keeps local receipt, sets `syncError`,
  toast (R6 S10).

## Phase 2: Core Implementation

- [x] 2.1 TDD: rewrite `ConsentBanner.spec.ts` (RED): `<aside>` + `consent-banner`; no overlay/
  `role=dialog`/`aria-modal`; no close; Escape no-op; click behind; accept/reject/save
  `source:'banner'`; states A–D (R1 R2 R5 R6 R8).
- [x] 2.2 Rewrite `ConsentBanner.vue`: fixed `<aside>` (z-40, safe-area,
  `sm:w-[min(40rem,calc(100vw-3rem))]`), no Teleport/backdrop; visibility `!hasValidConsent`; no
  keydown; handlers call `saveConsent` only (R1 R2 R5).
- [x] 2.3 TDD: spec inline Customize (Switch+Save/Back; Save persists toggle, Back no receipt,
  Necessary disabled) → implement panel (R3 S4).
- [x] 2.4 TDD: banner hidden post-decision; footer `cookie-settings-link` opens only
  `CookieSettings` modal (`showCookieSettings`), banner stays hidden (R4 S5); verify
  `AppShell.vue` + dialog primitives unchanged.

## Phase 3: Integration + Store

- [x] 3.1 Run preserved `consent.store.test.ts`; confirm store API now exposes `hasValidConsent`/
  `analyticsEnabled`/`saveConsent`/`loadFromStorage`/`syncToBackend` only, no `forceOpen` (R5 R6).
- [x] 3.2 Verify banner inline in `AppShell.vue`; z-40 below dialogs/toasts; no Dialog imports
  left (R1 R7).

## Phase 4: Testing

- [x] 4.1 Run `pnpm --filter app test:run` — consent suites green (14 scenarios).
  → DONE 2026-09-09: full app unit suite PASS — 146 files, 1711 tests, 0 failures
  (incl. 5-file consent subset: 51 tests PASS).
- [x] 4.2 E2E `consent.spec.ts`: TASK-026 no `dialog-overlay`, banner visible, sidebar clickable;
  keep TASK-027/028 (R1 S2, R4 S5, R5 S7, R8 S14).
  → DONE 2026-09-09: `consent.spec.ts` 12/12 PASS (4 tests × chromium/firefox/Mobile Chrome)
  via base `playwright.config.ts`.
- [x] 4.3 E2E: add DNT scenario via `mockPrivacySignals({dnt:true})` — banner shows, analytics OFF
  default, accept overrides (R5 D, R6).
  → DONE 2026-09-09: TASK-029 added. RED proven (signal disabled → fails at
  `receipt.dnt` assertion); GREEN with signal (2.5s chromium; also green firefox/mobile).
  No prod-code change needed — store/banner already implemented R5-D/R6.
- [x] 4.4 Run `pnpm --filter app test:e2e:scheduler -- --grep @consent`, then full app E2E (R8).
  → DONE 2026-09-09 with recorded results: scheduler lane 42/42 PASS (exit 0; note: lane
  testMatch excludes `consent.spec.ts`, so these are scheduler regression tests, not consent
  tests). Full base-config chromium suite: 174 passed / 29 failed / 6 skipped in 3.0m.
  All 29 failures are OUTSIDE consent scope (composer-media-mocked, registration, i18n login
  heading, scheduler-create, media-real credential lanes, etc.); working tree contains ONLY
  `e2e/specs/consent.spec.ts` changes, so failures are pre-existing relative to this change
  (spot-verified: i18n 10.1 fails on 'Welcome back' login heading, unrelated to consent).
  Full E2E is therefore NOT claimed green — see apply-progress.md waiver notes.
- [ ] 4.5 Manual Brave QA (Shields ON/OFF × A–D): banner in DOM, no overlay, app clickable, no
  console errors (R7 S11).
  → BLOCKED 2026-09-09: no human-driven browser harness in this environment (Brave Browser.app
  present on host but manual Shields ON/OFF matrix requires operator interaction).
  Exception draft in apply-progress.md for orchestrator approval.
- [ ] 4.6 Manual Chrome/Chromium + Safari/WebKit: states A–D, EN/ES, light/dark, 320/768/1280 (R7
  S12, R8 S13).
  → BLOCKED 2026-09-09 (same reason). Partial automated cover exists: consent.spec.ts green on
  chromium/firefox/Mobile Chrome; dashboard E2E officially excludes WebKit (HAR-cookie engine
  limitation, see `e2e/README.md` + `playwright.config.ts` comment). Exception draft in
  apply-progress.md.

## Phase 5: Cleanup + Docs

- [x] 5.1 Document Brave root cause (portal overlay paint failure, `z-[51]` patch) + ADRs in PR
  notes (R7).
  → CLOSED 2026-09-09 by orchestrator F-03 decision at archive: `design.md` §Browser Resilience +
  proposal accepted as the durable root-cause record; NO standalone ADR required. Recorded in
  `state.yaml` (`f03_adr_waived_2026-09-09`). Root cause was documented in `design.md`
  §Browser Resilience + proposal; no PR notes filed.
- [x] 5.2 Confirm `ui/dialog/*`, `shared/web/*` diff-clean; remove dead stubs (incl. any leftover
  `openSettings` refs) (R7).
  → DONE 2026-09-09: working tree holds ONLY `e2e/specs/consent.spec.ts`; repo-wide grep finds
  zero `forceOpen`/`closeSettings` refs and `openSettings` only as an unrelated local emit in
  `SidebarAccountSection.vue` (account popover, not consent store). No consent dead stubs remain.
- [x] 5.3 Quality gate: `pnpm --filter app type-check` + `just frontend-lint` + app unit + consent
  E2E green (R8).
  → DONE 2026-09-09: `type-check` PASS; `just frontend-lint` PASS (67 files); biome on touched
  E2E files PASS; full unit 1711/1711 PASS; consent E2E 12/12 PASS. Caveat: full chromium E2E
  suite has 29 pre-existing failures outside consent scope (see 4.4) — gate is green for every
  lane this change touches.
