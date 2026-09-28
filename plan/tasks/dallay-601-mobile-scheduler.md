# RPI Tasks: DALLAY-601 Mobile Scheduler Redesign

Route: Delegated direct (Plan Mode RPI, no SDD artifacts)
Branch: mobile (worktree) / target feature/dallay-601-frontendmobilescheduler-redesign-scheduler-for-a-mobile
OpenSpec: openspec/changes/mobile-scheduler-redesign (phase tasks -> apply)
Scope: finish mobile-first scheduler shell already scaffolded; preserve URL contract, desktop density, API contracts.

## Gates evidence (local only)

- pnpm --filter app lint: PASS (873 files, no warnings)
- pnpm --filter app type-check: PASS (vue-tsc --build)
- pnpm --filter app test:run: PASS 1825/1825 across 163 files
- pnpm --filter app build: PASS (PWA generated)
- pnpm --filter app e2e scheduler lane (PLAYWRIGHT=true): PASS 63/63 (1 skipped)
  Matrix covered: 320x568, 360x640, 390x844, 430x932, plus desktop 1280x800
- No new comments, suppressions, casts, widened any, ignored paths.
- publication-calendar-sse diff hash unchanged (URL state tests still align).

## Baseline (pre-existing in worktree, do not revert)

- useCalendarUrl view union, VALID_VIEWS, surfaceDefaultView, normalizeView, buildQuery omission, setView push, stepPeriod day/3-days/week/agenda — DONE
- calendarRange day/three-days — DONE
- mobile/ shell + 5 children + en/es scheduler keys — SCAFFOLDED then fixed
- SchedulerView mobile branch via useMediaQuery(768px) — DONE with desktop Bulk Import row hidden on mobile

## Tasks

- [x] RPI-001 Baseline gates record local evidence
  Evidence: 873 files lint, vue-tsc clean, 1825 unit tests, build OK.
- [x] RPI-002 MobileSchedulerHeader New Post visible primary action
  Files: MobileSchedulerHeader.vue + .test.ts
  Evidence: header test asserts visible text label, min-h-11 on prev/next/today/filters, active count badge, events bubble.
- [x] RPI-003 SchedulerViewSwitcher Day/3 Days/Week/Agenda
  Files: SchedulerViewSwitcher.vue + .test.ts
  Evidence: switcher test asserts 4 buttons (incl agenda-view-button), aria-current, change:view, 44px targets.
- [x] RPI-004 SchedulerFiltersSheet deterministic Apply/Reset
  Files: SchedulerFiltersSheet.vue + .test.ts
  Evidence: sheet test asserts bottom sheet with 3 selects, Apply emits change:filter and closes, Reset uses browser tz and closes.
- [x] RPI-005 Atomic mobile filter commit + correct active count
  Files: useCalendarUrl.ts (setFilters), MobileSchedulerShell.vue, SchedulerFiltersSheet.vue
  Evidence: setFilters test asserts single replace with status+channels deduped; filtersCount computed from browser tz.
- [x] RPI-006 SchedulerTimelineBody readable scrollable timeline
  Files: SchedulerTimelineBody.vue + .test.ts
  Evidence: timeline test asserts overflow-x-auto + minmax(120px,1fr) + 48px sticky gutter, cards render provider icon + time + preview, tap opens detail.
- [x] RPI-007 SchedulerView mobile integration parity
  Files: SchedulerView.vue + .test.ts (mock view/setView, useMediaQuery=false, app-tour)
  Evidence: 57 existing tests + new mobile filter pipe through url.setFilters; Bulk Import row desktop-only via v-if="!isMobile"; periodLabel covers day/3-days/week/agenda.
- [x] RPI-008 Mobile Vitest suite (strict TDD evidence)
  Evidence: 20 mobile component tests PASS via pnpm --filter app test:run src/modules/publishing/presentation/components/mobile/.
- [x] RPI-009 POM + Playwright mobile matrix
  Files: e2e/pages/scheduler-page.ts, e2e/specs/scheduler-views.spec.ts
  Evidence: TC-M1..M6 + TC-D1 green via Playwright lane.
- [x] RPI-010 Full quality gate
  Evidence: all gates above PASS; zero new comments/suppressions/casts/any.

## Out of scope per Linear

- backend, API contracts, shared/web consent/locale
- Create Post modal layout (DALLAY-600)
- Bulk Import flow internals
- Provider integrations
- Profile Tailors visual identity

## Progress

- 2026-09-27: routed Delegated direct, created tracker, applied Unit 2+3 changes, all gates green.
- 2026-09-27: verify-report.md (PASS WITH WARNINGS, 17/19 scenarios compliant, 2 PARTIAL with E2E coverage) and qa-report.md (PASS WITH WARNINGS, all 11 acceptance scenarios executable + Q-DEPLOYED NOT TESTED for the unmerged branch) persisted.
- 2026-09-27: SDD archive step executed. Change folder moved to `openspec/changes/archive/2026-09-27-mobile-scheduler-redesign/`. Delta specs merged into `openspec/specs/scheduler-url-state-standard/spec.md` (new `Scheduler view query parameter` requirement) and `openspec/specs/visual-calendar/spec.md` (MODIFIED `Multi-View Calendar` requirement + three new requirements: `Mobile filters are a single trigger`, `Mobile secondary actions menu`, `Mobile touch and readability`).
- 2026-09-27: SDD cycle complete. Remaining work is out-of-SDD: commit + push + open PR + post-deploy QA.
