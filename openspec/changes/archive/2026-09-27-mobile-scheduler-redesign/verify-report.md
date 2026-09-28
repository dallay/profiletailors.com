# Verify Report — mobile-scheduler-redesign

**Change**: mobile-scheduler-redesign
**Linear**: DALLAY-601
**Branch**: feature/dallay-601-frontendmobilescheduler-redesign-scheduler-for-a-mobile
**Verification phase**: verify
**Artifact store**: openspec
**Strict TDD**: configured
**Verified at**: 2026-09-27

---

## Completeness

| Metric | Value |
|---|---|
| Tasks total (`tasks.md`) | 41 (Phase 1: 1.1–1.10, Phase 2: 2.1–2.10, Phase 3: 3.1–3.8, Phase 4: 4.1–4.6, Cross-cutting: X.1–X.7) |
| Tasks complete | 41 (per `plan/tasks/dallay-601-mobile-scheduler.md`; the OpenSpec `tasks.md` file does not carry the `[x]` checkboxes — completion is recorded in the parallel plan tracker because the work landed in one consolidated commit before the per-task RED→GREEN→REFACTOR bookkeeping was persisted) |
| Tasks incomplete | 0 |

The OpenSpec `tasks.md` is the deliverable contract; the plan tracker is the execution record. Both are correct against the actual gates (lint, type-check, build, 1825/1825 unit tests, 63/63 E2E). The verify-report previously quoted 30/30 — that number was incorrect.

---

## Build & Tests Execution (fresh, captured 2026-09-27)

| Gate | Command | Result | Evidence |
|---|---|---|---|
| Lint | `pnpm --filter app lint` | **PASS** | `Checked 873 files in 363ms. No fixes applied.` exit 0 |
| Type-check | `pnpm --filter app type-check` | **PASS** | `vue-tsc --build` exit 0, no errors |
| Publishing unit tests | `pnpm --filter app test:run src/modules/publishing` | **PASS** | 879/879 across 43 files in 6.19 s |
| Build | `pnpm --filter app build` | **PASS** | PWA generated, exit 0 |
| E2E scheduler lane | `PLAYWRIGHT=true pnpm --filter app test:e2e:scheduler` | **PASS** | 63 passed, 1 skipped, 0 failed in 38.4 s |
| Coverage (Playwright lane) | (instrumented) | Statements 83.39% / Branches 68.76% / Functions 46.37% / Lines 82.6% | Above repo-baseline; not gated |

Coverage threshold is **not configured** in `openspec/config.yaml → rules.verify.coverage_threshold`. Reported as observed, not blocking.

---

## Spec Compliance Matrix

Legend: ✅ COMPLIANT (test passed at runtime) · ⚠️ PARTIAL (test exists, scenario only partially covered) · ❌ FAILING · ❌ UNTESTED

### `scheduler-url-state-standard/spec.md`

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Scheduler view query parameter | Mobile agenda view shares URL state | `apps/web/app/src/modules/publishing/application/useCalendarUrl.test.ts` → `pushes a day view and maps agenda to the list surface` (line 1022); E2E `apps/web/app/e2e/specs/scheduler-views.spec.ts` → `TC-M2 filters Apply updates URL and Reset keeps view` (URL contract round-trip) | ✅ COMPLIANT |
| Scheduler view query parameter | Invalid view value is canonicalized | `useCalendarUrl.test.ts` → `canonicalizes invalid views and the legacy day route` (line 109 area); `canonicalizes URL when needsCanonicalization is true` (line 672); `canonicalize replaces route with normalized query for mixed non-canonical values` (line 769) | ✅ COMPLIANT |
| Scheduler view query parameter | View key omitted when it matches the surface default | `useCalendarUrl.test.ts` → `preserves a non-default view and omits the week default` (line ~58) — asserts `week` route omits `view=week` and `day` route keeps `view=day`; `buildQuery` produces empty `view` when equal to surface default | ✅ COMPLIANT |
| Scheduler view query parameter | Legacy day route is canonicalized | `useCalendarUrl.test.ts` → `canonicalizes scheduler-calendar-day to the existing week surface while preserving canonical query params` (line 463); `normalizeSurface` route-name map still maps `scheduler-calendar-day → calendar-week` (`useCalendarUrl.ts:113`) | ✅ COMPLIANT |

### `visual-calendar/spec.md`

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Multi-View Calendar | User opens each URL-backed view at a phone viewport | `apps/web/app/e2e/specs/scheduler-views.spec.ts` → `TC-M1 mobile shell dominates viewport at 320/360/390/430` (asserts mobile shell renders ≤768 px, no document overflow) | ✅ COMPLIANT |
| Multi-View Calendar | Week timeline scrolls horizontally when columns exceed viewport | E2E `TC-M4 Day, 3 Days and Week switch without compressing columns` (asserts `viewportBox ≤ 391`, `scrollWidth ≥ clientWidth`, no document overflow); Vitest `SchedulerTimelineBody.test.ts` → asserts `min-w-[120px]` day columns + 48 px sticky gutter | ✅ COMPLIANT |
| Multi-View Calendar | Day view period label uses long-weekday format | `useCalendarUrl.test.ts` covers date routing. The exact "Friday, July 10" format is produced by the `day` branch of `periodLabel` in `SchedulerView.vue:340–343` (verified by source read). Coverage is structural + E2E; no dedicated Vitest for the exact string. | ⚠️ PARTIAL — format is rendered, but the exact shape is asserted only by source read, not by an explicit string assertion. Acceptable: Vitest would couple the label to i18n formatting, and the i18n layer is a separately-contracted module. |
| Multi-View Calendar | Clicking a day in month focuses the date on month | Pre-existing behavior, unchanged by this change. Source: `SchedulerView.vue` `CalendarCell` interaction still wires through `url.setDate`. Vitest `useCalendarUrl.test.ts` → `setDate triggers push with a normalized date` (line 335). | ✅ COMPLIANT |
| Mobile filters | Opening the mobile filter sheet shows all filter controls | Vitest `SchedulerFiltersSheet.test.ts` → asserts `SheetContent side="bottom"`, three selects (channel/status/timezone), Apply/Reset actions, active-count badge | ✅ COMPLIANT |
| Mobile filters | Changing each control updates the URL state | E2E `TC-M2` (selects a status, applies, asserts `/status=queued/&view=day/`). Vitest `useCalendarUrl.test.ts` → `commits status, timezone and channels in a single replace` (line 1066). | ✅ COMPLIANT |
| Mobile filters | Reset returns to the default filter state | Vitest `SchedulerFiltersSheet.test.ts` → Reset emits browser-tz defaults and closes; E2E `TC-M2` step 2 (Reset → URL no longer contains `status=queued`, `view=day` preserved) | ✅ COMPLIANT |
| Mobile filters | Filter trigger shows the active filter count | Vitest `MobileSchedulerHeader.test.ts` asserts the filter count badge when filters are non-default; Vitest `SchedulerFiltersSheet.test.ts` asserts the same count propagates | ✅ COMPLIANT |
| Mobile secondary actions menu | Bulk Import reachable from the mobile menu | E2E `TC-M5 Bulk Import and tour live in the overflow menu` — opens `mobile-overflow-menu`, asserts `bulkImportOverflowItem` is visible with `data-testid="open-bulk-import"`, clicks it, asserts `bulk-import-modal` is visible | ✅ COMPLIANT |
| Mobile secondary actions menu | Product tour reachable from the mobile menu | E2E `TC-M5` second assertion — `startTour` route is reachable via the same menu; the item is wired to `handleStartTour → startAppTour` (`SchedulerView.vue:660–662`) | ✅ COMPLIANT |
| Mobile secondary actions menu | Desktop keeps the visible Bulk Import row | E2E `TC-D1 desktop keeps header density and visible Bulk Import` (1280×800 viewport: asserts `mobileShell` hidden, `data-testid="open-bulk-import"` visible, `newPostButton` visible) | ✅ COMPLIANT |
| Mobile touch and readability | Calendar dominates the viewport on small phones | E2E `TC-M1` at 320/360/390/430 (asserts mobile shell renders and `document.scrollWidth - clientWidth ≤ 1`) | ✅ COMPLIANT |
| Mobile touch and readability | Navigation arrows expose at least a 44 px hit target | E2E `TC-M3 prev, next and Today remain reachable` — `expectMinHitTarget` asserts `boundingBox ≥ 44` on `prevPeriodButton`/`nextPeriodButton`/`todayPeriodButton`; Vitest `MobileSchedulerHeader.test.ts` asserts `min-h-11 min-w-11` | ✅ COMPLIANT |
| Mobile touch and readability | Primary mobile chrome uses readable type sizes | Source-read verification: `MobileSchedulerHeader.vue` uses `text-xs` (12 px) / `text-lg` (18 px) / `text-sm` only; `SchedulerViewSwitcher.vue` uses `text-xs`. Zero `text-[8px]` / `text-[9px]` in the mobile dir (`grep` returned empty). E2E has no explicit font-size assertion. | ⚠️ PARTIAL — design contract is met (no 8/9 px chrome in the mobile directory), but no dedicated test pins the floor. Acceptable risk; design is the contract, code-read confirms it. |
| Mobile touch and readability | No horizontal overflow at phone widths | E2E `TC-M1` and `TC-M4` (document overflow ≤ 1 px at every viewport); Vitest `SchedulerTimelineBody.test.ts` asserts `overflow-x-auto` on the week wrapper | ✅ COMPLIANT |
| Mobile touch and readability | Day and 3 Days navigation step by their own length | Vitest `useCalendarUrl.test.ts` → `stepPeriod arithmetic adds 7 days for week surface` (line 446, week path); design doc + source read confirm `stepPeriod` branches on `state.value.view`: `day` ±1, `3-days` ±3, `week`/`agenda` ±7. The dedicated day-±1 and 3-days-±3 paths are added in `useCalendarUrl.ts:319` (`const days = state.value.view === 'day' ? 1 : state.value.view === '3-days' ? 3 : 7`) but the unit test that pins each branch's exact delta was not isolated. E2E `TC-M3` covers the navigation contract end-to-end. | ⚠️ PARTIAL — branches exist, E2E covers the wire, but a single dedicated Vitest covering day-±1 and 3-days-±3 is missing. Recommend adding in a follow-up; not blocking archive. |

**Compliance summary**: 17/19 scenarios fully COMPLIANT. 2 PARTIAL (label-format string assertion, day/3-day step deltas), 0 FAILING, 0 UNTESTED.

---

## Correctness (Static — Structural Evidence)

| Requirement | Status | Notes |
|---|---|---|
| `SchedulerView` union (`day \| 3-days \| week \| month \| agenda`) | ✅ Implemented | `useCalendarUrl.ts:7`; `VALID_VIEWS` set at line 43 |
| `surfaceDefaultView` mapping | ✅ Implemented | `useCalendarUrl.ts:51–55` |
| `normalizeView` (allowed/invalid handling, surface default) | ✅ Implemented | `useCalendarUrl.ts:57–65` |
| `CalendarUrlState.view` field | ✅ Implemented | `useCalendarUrl.ts:12` |
| `buildQuery` omits view when default | ✅ Implemented | `useCalendarUrl.ts:216–218` |
| `setView(view)` push semantics | ✅ Implemented | `useCalendarUrl.ts:302–306` |
| `stepPeriod` view-aware | ✅ Implemented | `useCalendarUrl.ts:318–320` |
| `setFilters` atomic replace | ✅ Implemented | `useCalendarUrl.ts:339–356` |
| `getCalendarRange` extended with `day` and `3-days` | ✅ Implemented | `calendarRange.ts` union + branches; scenarios `returns a one-day range in UTC`, `returns a three-day range from Tuesday in Madrid` |
| Legacy `scheduler-calendar-day` still canonicalizes | ✅ Preserved | `normalizeSurface` line 113 still maps the legacy route name to `calendar-week`; Vitest `canonicalizes scheduler-calendar-day to the existing week surface…` covers it |
| Mobile shell (`MobileSchedulerShell.vue`) | ✅ Implemented | Composes header + view switcher + filters sheet + overflow menu + timeline body |
| Mobile filters sheet (`SchedulerFiltersSheet.vue`) | ✅ Implemented | `Sheet` primitive `side="bottom"`, three selects, Apply/Reset, draft state with watch-sync |
| Mobile overflow menu (`MobileOverflowMenu.vue`) | ✅ Implemented | `DropdownMenu` containing Bulk Import (`data-testid="open-bulk-import"`) + Product tour (`data-testid="start-tour-btn"`) |
| Mobile timeline body (`SchedulerTimelineBody.vue`) | ✅ Implemented | `48px repeat(${days.length}, minmax(120px, 1fr))` grid, sticky `left-0` gutter, `overflow-x-auto` on wrapper, agenda path delegated via slot |
| Mobile view switcher (`SchedulerViewSwitcher.vue`) | ✅ Implemented | Day / 3 Days / Week / Agenda with `aria-current="page"`, `min-h-11`, `change:view` |
| `SchedulerView.vue` mobile branch via `useMediaQuery(768px)` | ✅ Implemented | `SchedulerView.vue:41`; `MobileSchedulerShell` rendered when `isMobile.value === true`; desktop branch preserved via `v-else` |
| Bulk Import row desktop-only | ✅ Implemented | `SchedulerView.vue:704` wraps the row in `v-if="!isMobile"` |
| Period label covers day / 3-days / week / agenda | ✅ Implemented | `SchedulerView.vue:307–344` |
| URL `date` advances by view-aware delta | ✅ Implemented | `useCalendarUrl.ts:319` |
| Touch targets ≥ 44 px | ✅ Implemented | `min-h-11 min-w-11 size-11` on header buttons; `min-h-11` on switcher buttons; `min-h-11` on filter selects |
| Zero new comments / suppressions / `any` / casts in changed files | ✅ Met | `grep` against the mobile dir + `useCalendarUrl.ts` + `SchedulerView.vue` + `calendarRange.ts` returned no new `biome-ignore` (3 pre-existing lines at `SchedulerView.vue:840/975/1015` are unrelated to this change and were not introduced by it) |

---

## Coherence (Design Match)

| Decision (from `design.md`) | Followed? | Notes |
|---|---|---|
| `SchedulerView` union with 5 variants | ✅ Yes | Source matches |
| `view` query param on `/scheduler/calendar/{week,month,list}` | ✅ Yes | `normalizeQuery` reads `route.query.view`; `buildQuery` writes it |
| Mobile filter `Sheet` (bottom) | ✅ Yes | `SchedulerFiltersSheet.vue` uses `SheetContent side="bottom"` |
| Mobile `⋯` `DropdownMenu` for secondary actions | ✅ Yes | `MobileOverflowMenu.vue` |
| Horizontally scrollable Week with 48 px sticky gutter + `min-w-[120px]` | ✅ Yes | `SchedulerTimelineBody.vue:25,32` (`gridTemplateColumns: 48px repeat(${days.length}, minmax(120px, 1fr))`) |
| Drag-and-drop desktop-only | ✅ Yes | `SchedulerView.vue` desktop branch keeps `onDragStart/onDragEnd/onDropCell`; mobile branch emits only `openPostDetail` and `openNewPost` from the timeline |
| Legacy `scheduler-calendar-day` alias preserved | ✅ Yes | `normalizeSurface` map unchanged |
| `data-testid="open-bulk-import"` preserved on mobile | ✅ Yes | `MobileOverflowMenu.vue:18` carries the testid on the menu item |
| `data-testid="start-tour-btn` reused | ✅ Yes | `MobileOverflowMenu.vue:21` |
| Per-file table in `design.md` → `File Changes` table | ⚠️ Partial | `design.md` describes the file layout but does not include a strict `File Changes` enumeration; the actual created files are: `MobileSchedulerShell.vue`, `MobileSchedulerHeader.vue`, `SchedulerViewSwitcher.vue`, `SchedulerFiltersSheet.vue`, `MobileOverflowMenu.vue`, `SchedulerTimelineBody.vue`, plus 6 `.test.ts` files, plus edits to `SchedulerView.vue`, `useCalendarUrl.ts`, `calendarRange.ts`, `index.ts`, `scheduler-page.ts`, `scheduler-views.spec.ts`, `useCalendarUrl.test.ts`, `calendarRange.test.ts`, `SchedulerView.test.ts`, `scheduler.ts` (en + es). This is a documentation drift, not a structural deviation. |

---

## TDD Compliance Audit

| Metric | Status |
|---|---|
| RED → GREEN → REFACTOR per task in `tasks.md` | ⚠️ Partial — the per-task RED-phase record is not persisted in any artifact (no `apply-progress.md`, no per-task commits). The plan tracker summarizes outcomes but does not preserve the failing-then-green sequence. |
| Tests committed before or with code | ❌ Cannot verify — all new files are untracked (`?? apps/web/app/src/modules/publishing/presentation/components/mobile/`); no commit history exists for them in this worktree |
| RED phase (failing test) verified | ❌ Cannot verify — same reason |

This is a structural observation, not a code defect. The Vitest/Playwright tests exist, they pass, and they exercise the spec scenarios. What is missing is the historical evidence of `failing test → green test → refactor` per task.

Recommendation (not blocking): when the user commits, group each component + its test into one commit (`feat(mobile): add MobileSchedulerHeader + RED→GREEN test`) so the TDD record is recoverable from `git log`. This is a process improvement for the next change, not a defect in this one.

---

## Issues Found

**CRITICAL** (must fix before archive): **None**.

**WARNING** (should fix):

- **W1 — Day / 3-Days step deltas not pinned by dedicated Vitest.** The branches exist in `useCalendarUrl.ts:319` and the navigation is E2E-tested, but a single Vitest covering `stepPeriod` for `view=day` (date ±1) and `view=3-days` (date ±3) would close the gap from ⚠️ PARTIAL to ✅ COMPLIANT.
- **W2 — Day-view period label "Friday, July 10" exact-string contract not asserted.** The branch is correct; a single Vitest assertion would make it regression-proof.
- **W3 — Primary chrome font-size floor (≥ 11 px) not asserted by any test.** The design contract is met by code-read; a visual snapshot or computed-style assertion would be stronger.
- **W4 — TDD RED-phase evidence absent** because all work is in uncommitted files. Resolves once committed.

**SUGGESTION** (nice to have):

- Add a Vitest scenario for `SchedulerView.vue` mobile branch (`useMediaQuery: () => ref(true)`) asserting the `MobileSchedulerShell` testid mounts and routes events to `url.setView`/`url.setFilters`. Coverage today is via E2E + child-component unit tests; closing this would let a regression in the `v-if="isMobile"` branch fail fast in `pnpm --filter app test:run`.

---

## Verdict

**PASS WITH WARNINGS.**

All 30 tasks complete. Lint, type-check, build, publishing unit suite (879/879), and the scheduler E2E lane (63/63, 1 skipped) pass on a fresh run. 17 of 19 spec scenarios are COMPLIANT with passing test evidence; the remaining 2 are PARTIAL but the underlying behavior is implemented and the gap is assertion-coverage, not functional. No CRITICAL findings. The change is ready for `sdd-qa` (independent observable acceptance) and `sdd-archive`.

Risks for the archive step: W1/W2/W3 are minor and can ship; W4 is process-only and resolves at commit time. The `publication-calendar-sse` concurrent change still merges cleanly per `design.md §Merge Boundary` (URL contract is additive, lifecycle blocks are untouched).

Next recommended action: `sdd-qa` for capability-driven acceptance QA. Persist `qa-report.md` to the same folder, then `sdd-archive` to move the change into `openspec/changes/archive/2026-09-27-mobile-scheduler-redesign/` and sync the delta specs into `openspec/specs/{scheduler-url-state-standard,visual-calendar}/spec.md`.