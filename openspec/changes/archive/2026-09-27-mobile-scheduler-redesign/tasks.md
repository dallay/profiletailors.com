# Tasks: Mobile Scheduler Redesign

Decision needed before apply: No
Delivery strategy: single-pr
Delivery base: current `mobile` branch
Size exception: approved by the user for one reviewable PR despite high review workload and the forecasted 400-line budget risk
Chained PRs recommended: No

## Work Units

| Unit | Goal | PR | Base |
|---|---|---|---|
| 1 | URL + range (pure) | Single PR | `mobile` |
| 2 | Mobile shell + locales | Single PR | Unit 1 |
| 3 | Integration + E2E | Single PR | Unit 2 |

## Phase 1: URL contract + calendar range (Unit 1)

- [ ] 1.1 RED: `useCalendarUrl.test.ts` scenarios — `SchedulerView` surface defaults, `view=day` preserved, `view=hourly` flagged, `view=week` omitted, legacy `scheduler-calendar-day` canonicalizes. Verify: `pnpm --filter app test:run src/modules/publishing/application/useCalendarUrl.test.ts` (FAIL new).
- [ ] 1.2 GREEN: add `SchedulerView` union, `VALID_VIEWS`, `surfaceDefaultView`, `normalizeView`; extend `CalendarUrlState.view`, `normalizeQuery`, `buildQuery`, `needsCanonicalization`.
- [ ] 1.3 RED: `setView('day')` `push`; `setView('agenda')` from `calendar-month` → `scheduler-list`. Verify: same test (FAIL).
- [ ] 1.4 GREEN: implement `setView` via `navigate`.
- [ ] 1.5 RED: `stepPeriod` day ±1, 3-days ±3, agenda ±7; week ±7 and month `setMonth` stay green. Verify: same test (FAIL).
- [ ] 1.6 GREEN: dispatch `stepPeriod` by `state.view`.
- [ ] 1.7 RED: `calendarRange.test.ts` `day` `[00:00, +1d 00:00)` in UTC/Madrid; `three-days` `[00:00, +3d 00:00)` from Tuesday; existing green. Verify: `pnpm --filter app test:run src/modules/publishing/application/calendarRange.test.ts` (FAIL).
- [ ] 1.8 GREEN: extend `CalendarSurface`; branch `getCalendarRange`.
- [ ] 1.9 REFACTOR: extract `defaultViewForSurface`; drop duplicated `'week'`. Verify: both files (PASS).
- [ ] 1.10 Gate: `pnpm --filter app lint`, `pnpm --filter app type-check`, `pnpm --filter app test:run src/modules/publishing/application/` (PASS; no new warnings, no suppressions).

## Phase 2: Mobile shell components (Unit 2)

- [ ] 2.1 RED: `MobileSchedulerHeader.test.ts` proves title, `size-11` hit targets, period label, `Today`, `Filters` active-count badge; emits `newPost`/`prev`/`next`/`today`/`openFilters`. Verify: `pnpm --filter app test:run src/modules/publishing/presentation/components/mobile/MobileSchedulerHeader.test.ts` (FAIL).
- [ ] 2.2 GREEN: create `MobileSchedulerHeader.vue` with `size-11`/`min-h-11`/`min-w-11`.
- [ ] 2.3 RED: `SchedulerViewSwitcher.test.ts` — Day/3 Days/Week on `calendar-week`, `aria-current`, `change:view`. Verify: same cmd (FAIL). GREEN: create `.vue`.
- [ ] 2.4 RED: `SchedulerFiltersSheet.test.ts` — `SheetContent side="bottom"`, channels/status/timezone, active-count trigger, `Apply`/`Reset` (`SheetClose as-child`). Verify: same cmd (FAIL). GREEN: create `.vue`.
- [ ] 2.5 RED: `MobileOverflowMenu.test.ts` — `…` opens `DropdownMenu`; Bulk Import (`data-testid="open-bulk-import"`) + Product tour; `MoreHorizontal` `size-11`; emits `openBulkImport`/`startTour`. Verify: same cmd (FAIL). GREEN: create `.vue`.
- [ ] 2.6 RED: `SchedulerTimelineBody.test.ts` — 1/3/7 columns by `days.length`; week `48 px` sticky gutter; `min-w-[120px]`; `data-testid="scheduler-timeline-viewport"` `overflow-x-auto` for week else hidden. Verify: same cmd (FAIL). GREEN: create `.vue`.
- [ ] 2.7 RED: `MobileSchedulerShell.test.ts` — composes children, swaps body by `view`, exposes `data-testid="scheduler-mobile-shell"`; `agenda` delegates via `agendaSlot`. Verify: same cmd (FAIL). GREEN: create `.vue`.
- [ ] 2.8 RED: add `viewDay`/`viewThreeDays`/`viewWeek`/`viewMonth`/`viewAgenda`/`filters`/`filtersCount`/`apply`/`reset`/`moreActions`/`bulkImport`/`productTour` to `locales/{en,es}/scheduler.ts`. Verify: `pnpm --filter app type-check` (FAIL once consumed). GREEN: add keys; aggregators already import `./scheduler`.
- [ ] 2.9 REFACTOR: inline `useActiveFilterCount` in shell; pass precomputed count. Verify: `pnpm --filter app test:run src/modules/publishing/presentation/components/mobile/` (PASS).
- [ ] 2.10 Gate: `pnpm --filter app lint`, `pnpm --filter app type-check`, same Vitest (PASS; no new warnings, no suppressions).

## Phase 3: SchedulerView integration + CalendarHeader parity (Unit 3)

- [ ] 3.1 RED: `SchedulerView.test.ts` — desktop `CalendarHeader` mounts by default; `data-testid="scheduler-mobile-shell"` renders when `isMobile=true`; SSE subscriber + revalidation still called. Verify: `pnpm --filter app test:run src/modules/publishing/views/SchedulerView.test.ts` (FAIL).
- [ ] 3.2 GREEN: add `useMediaQuery('(max-width: 768px)')` to `SchedulerView.vue`; wrap template in `<template v-if="!isMobile">`; add `<MobileSchedulerShell v-else>`; preserve SSE lifecycle.
- [ ] 3.3 RED: shell receives `view` from `url.state.value.view`; `change:view` triggers `url.setView`; route shell events to existing handlers and `url.setView`/`url.setSurface('list')`. Verify: same cmd (FAIL). GREEN: wire it.
- [ ] 3.4 RED: `openFilters`/`openBulkImport`/`startTour` open `isFilterSheetOpen`/`isBulkModalOpen`; `data-testid="open-bulk-import"` reachable via overflow. Verify: same cmd (FAIL). GREEN: add `isFilterSheetOpen` ref; wire Bulk Import to `isBulkModalOpen=true`; bubble `startTour`.
- [ ] 3.5 RED: `CalendarHeader.test.ts` — inline selects `lg:hidden` on mobile, `lg:flex` on desktop; existing emits stay green. Verify: `pnpm --filter app test:run src/modules/publishing/presentation/components/CalendarHeader.test.ts` (FAIL).
- [ ] 3.6 GREEN: split `CalendarHeader.vue` into `lg:flex`/`lg:hidden` segments.
- [ ] 3.7 REFACTOR: share period/prev/next/today segment via inline `<template>`; drop duplicated hit-target utilities. Verify: both files (PASS).
- [ ] 3.8 Gate: `pnpm --filter app lint`, `pnpm --filter app type-check`, `pnpm --filter app test:run src/modules/publishing/` (PASS; no new warnings, no suppressions).

## Phase 4: POM extensions and Playwright E2E

- [ ] 4.1 RED: extend `e2e/pages/scheduler-page.ts` with `dayViewButton`, `threeDaysViewButton`, `agendaViewButton`, `viewSwitcher`, `mobileFiltersTrigger`, `mobileFiltersSheet`, `mobileOverflowMenu`, `bulkImportOverflowItem`, `productTourOverflowItem`, `newPostPrimaryButton`, `prevPeriodButton`, `nextPeriodButton` (with `boundingBox >= 44` helper). Verify: `pnpm --filter app test:e2e:scheduler -- --grep "TC-02"` (TC-02 green).
- [ ] 4.2 GREEN: add new POM members via `getByRole`/`getByTestId`.
- [ ] 4.3 RED: extend `e2e/specs/scheduler-views.spec.ts` matrix `[320x568, 360x640, 390x844, 430x932, 1280x800]` — mobile shell renders ≤768 px, no document overflow, chrome visible, nav arrows ≥44 px, Day one column with `Friday, July 10`, 3 Days three columns, Week scrolls without compressing below 120 px, Apply updates URL + closes, Reset clears `status`/`channels[]` keeping `view`, Bulk Import opens modal, post tap opens detail. Verify: `pnpm --filter app test:e2e:scheduler` (FAIL new; TC-02..TC-04 green).
- [ ] 4.4 GREEN: implement scenarios via new POM.
- [ ] 4.5 REFACTOR: extract `expectMinHitTarget(locator, 44)`. Verify: same cmd (PASS).
- [ ] 4.6 Gate: same cmd (full lane green; record outcomes).

## Cross-cutting

- [ ] X.1 `pnpm --filter app test:run` (PASS; record counts).
- [ ] X.2 `pnpm --filter app lint` (PASS; no new warnings/suppressions).
- [ ] X.3 `pnpm --filter app type-check` (PASS).
- [ ] X.4 `pnpm --filter app build` (PASS).
- [ ] X.5 `pnpm --filter app test:e2e:scheduler` (full lane PASS; record matrix).
- [ ] X.6 Static analysis: no new warnings/suppressions/casts/widened `any`/comments (zero-comment policy); no weakened rules.
- [ ] X.7 `publication-calendar-sse` files untouched; record diff hash.

## Completion Criteria

- [ ] All `scheduler-url-state-standard` ADDED scenarios pass in `useCalendarUrl.test.ts`.
- [ ] All `visual-calendar` ADDED scenarios have Vitest or Playwright evidence.
- [ ] Strict-TDD order visible: RED → GREEN → REFACTOR with recorded outcomes.
- [ ] No new comments, suppressions, widened `any`, or weakened static-analysis configuration.
- [ ] `data-testid="open-bulk-import"` reachable on desktop and mobile; `bulk-import.spec.ts` passes.
- [ ] `scheduler-calendar-day` alias still canonicalizes to `calendar-week`.
- [ ] Local, CI, remote, deployed evidence distinguished in the final report.