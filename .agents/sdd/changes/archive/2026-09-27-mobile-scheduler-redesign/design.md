# Design: Mobile Scheduler Redesign

## Context

The scheduler's desktop toolbar (per-view Month/Week toggle, Calendar/List toggle, three
inline `<select>` filters, a `New Post` button) wraps onto multiple rows at 390 px and a
hard-coded seven-column week grid compresses each day to roughly `viewport_width / 7`
(`SchedulerView.vue:719`, `:744`). A dedicated `Bulk Import` row (`SchedulerView.vue:633–635`)
pushes the mobile canvas down with no relationship to the calendar. The product surface
already enforces `is_mobile <= 768px` via `useMediaQuery('(max-width: 768px)')` in
`SidebarProvider.vue:22`; this change moves the scheduler behind the same breakpoint with
additive composition only.

Out of scope: backend, API, `shared/web` consent/locale, `Create Post` modal layout (DALLAY-600),
and removal of the `scheduler-calendar-day` legacy alias.

## Goals & Non-Goals

Lock the seven decisions already in `proposal.md` and both delta specs:
`day | 3-days | week | month | agenda` as URL-backed views; `view` query param on
`/scheduler/calendar/{week,month,list}`; mobile filter `Sheet` (bottom); mobile `⋯`
`DropdownMenu` for secondary actions; horizontally scrollable Week with 48 px sticky gutter
and `min-w-[120px]` day columns; no backend / shared-web / API contract changes; the
`scheduler-calendar-day` legacy alias still canonicalizes to `calendar-week`.

## Architectural Overview

`SchedulerView.vue` switches branches on `isMobile`. The desktop branch is byte-identical to
the pre-change template. The mobile branch composes a new
`presentation/components/mobile/MobileSchedulerShell.vue` which owns a compact header,
period navigation, and a parameterized timeline body. The `useCalendarRevalidation`
funnel and the `publication-calendar-sse` lifecycle blocks remain unchanged and live
inside the same `onMounted` / `onUnmounted` / `activeWorkspaceId` watchers in
`SchedulerView.vue`.

```text
SchedulerView.vue
├── isMobile = useMediaQuery('(max-width: 768px)')        (SidebarProvider.vue:22 precedent)
├── url = useCalendarUrl()                                (extended with view)
├── desktop branch (untouched existing tree)
└── mobile branch
    └── MobileSchedulerShell.vue
        ├── MobileSchedulerHeader
        │   ├── SchedulerViewSwitcher            (Day / 3 Days / Week / Agenda*)
        │   ├── FiltersTrigger -> SchedulerFiltersSheet (Sheet, side=bottom)
        │   └── MobileOverflowMenu               (DropdownMenu: Bulk Import, Product tour)
        ├── PeriodNavigation                     (prev / label / next / Today)
        ├── SchedulerTimelineBody                (days: Date[], calendarView-aware)
        │   ├── DayTimeline        (1 column)
        │   ├── ThreeDayTimeline   (3 columns)
        │   └── WeekTimeline       (7 columns, overflow-x-auto, sticky 48 px gutter)
        └── AgendaList (reuses existing list surface)
```

*Month is desktop-only because the issue requests no mobile Month timeline.

## URL Contract Delta (useCalendarUrl.ts)

| Addition | Shape | Behaviour |
|---|---|---|
| `SchedulerView` type | `'day' \| '3-days' \| 'week' \| 'month' \| 'agenda'` | Closed discriminated union. |
| `CalendarUrlState.view` | `SchedulerView` | Derived in `normalizeQuery` via `normalizeView(rawView, surface)`. |
| `normalizeView(raw, surface)` | `(string, SchedulerSurface) => SchedulerView` | absent/invalid → surface default; explicit allowed value kept. |
| `buildQuery` omission rule | new branch | omits `view` when equal to surface default, mirroring existing filter omission. |
| `setView(view)` | navigation method | `push` semantics, matching `setSurface`. |
| `stepPeriod(direction)` view-aware | `day` ±1 d, `3-days` ±3 d, `week` ±7 d, `month` existing math, `agenda` ±7 d | `agenda` jumps a week so navigation remains proportional to list density. |
| `needsCanonicalization` flag | includes `view !== surfaceDefaultView[surface]` | drives replace semantics for invalid `view` values. |

`getCalendarRange` (`calendarRange.ts`) gains `'day' | 'three-days'` to the `CalendarSurface`
union: `day` returns `[00:00, next-day 00:00)` inclusive-start/exclusive-end; `three-days` returns
`[date 00:00, date+3d 00:00)` skipping Sunday alignment. `week` reuses the existing
`subtract({ days: getDayOfWeek(date, 'en-US', 'sun') })` math (`calendarRange.ts:55`).

`normalizeSurface` continues to canonicalize the legacy `scheduler-calendar-day` route name to
`calendar-week` (`useCalendarUrl.ts:88`).

## Mobile Shell Split

Introduce `presentation/components/mobile/MobileSchedulerShell.vue` plus four siblings
(`MobileSchedulerHeader`, `SchedulerViewSwitcher`, `SchedulerFiltersSheet`,
`MobileOverflowMenu`) under the same folder. `SchedulerView.vue` instantiates the shell
when `isMobile.value === true`; otherwise renders the existing template unchanged. The
desktop DOM is untouched. Each child has one purpose, typed props, and emits, satisfying
`frontend-architecture/SKILL.md` (`apps/web/app/src/modules/<feature>` boundary;
cross-feature consumers import the feature's stable barrel).

## Mobile Filters Sheet

`SchedulerFiltersSheet.vue` wraps the three controls (channels / status / timezone) plus
`Apply` and `Reset` inside `SheetContent side="bottom"` from
`apps/web/app/src/components/ui/sheet/SheetContent.vue` (the shadcn-vue primitive that
already implements `reka-ui` focus trap + ESC dismissal at `SheetContent.vue:8–12`,
`:49–58`). `Apply` uses `DialogClose as-child` mirroring the close-button pattern at
`SheetContent.vue:49–58`; `Reset` clears `status`, `q`, `channels[]` in a single
`useCalendarUrl` navigation while preserving `view` and `date`. Both actions emit the
existing `change:filter` event used by `CalendarHeader.vue:38–47` so the parent controller
stays a single source of truth.

Trigger label: localized `scheduler.filters` plus the active count when non-zero. Active
count = `(status !== 'all')` + `(channelIds.length > 0)` + `(timezone !== resolveBrowserTimezone())`.

## Mobile Overflow Menu

`SchedulerOverflowMenu.vue` exposes `⋯` `DropdownMenu` with `Bulk Import` and `Product
tour`. The `Bulk Import` item carries `data-testid="open-bulk-import"` so
`apps/web/app/e2e/specs/bulk-import.spec.ts` (lines 90–91, 136–137, 169–170) continues to
find the trigger. `Product tour` invokes the same handler `AppHeader.vue:55–65` uses
(`data-testid="start-tour-btn"`) by emitting a `startTour` event the parent bubbles up.
Desktop continues to render the existing visible `Bulk Import` row
(`SchedulerView.vue:633–635`) and skips the overflow menu entirely.

## Timeline Body

`SchedulerTimelineBody.vue` is parameterized by `days: Date[]`, `hourSlots`,
`getPublicationsForSlot`, `openNewPostForSlot`, `openPostDetail`. It reuses the
drag-and-drop and past-slot styling already proven on desktop
(`SchedulerView.vue:743–867`). `WeekTimeline` wraps the grid in `overflow-x-auto` with the
first column (48 px time gutter) on `position: sticky; left: 0`. Day column template is
`min-w-[120px]`; the grid repeat matches `days.length` (1 / 3 / 7). Document-level
horizontal overflow is asserted by E2E (see §12).

## Drag-and-Drop / Rescheduling

Drag-and-drop is desktop-only. Touch devices lack HTML5 `dragstart` semantics the desktop
path depends on (`SchedulerView.vue:168–223`), and the AGENTS.md comment-free policy
forbids a `// mobile fallback` comment. Mobile-only path: tap publication card → detail;
tap empty slot → composer. Long-press / native context menus are explicitly out of scope.

## Touch Targets & Readability

Per the delta spec scenario "Navigation arrows expose at least a 44 px hit target": the
period nav arrows and the list-button on the mobile header carry
`min-h-11 min-w-11 size-11` (44 px). Primary chrome (New Post, period label, view
selector, Today, Filters, Overflow) renders through the existing `text-xs` (12 px) /
`text-sm` (14 px) Tailwind utilities — never `text-[8px]` / `text-[9px]` — matching the
"primary mobile chrome uses readable type sizes" scenario. The numeric floors live in
this design (not in code comments) because the AGENTS.md comment-free policy forbids
explanatory comments.

## Locale

Both `apps/web/app/src/shared/i18n/locales/en/scheduler.ts` and `…/es/scheduler.ts` ship
in the same change (aggregated through `locales/{en,es}/index.ts:35`). New keys:
`viewDay`, `viewThreeDays`, `viewWeek`, `viewMonth`, `viewAgenda`, `filters`,
`filtersCount`, `apply`, `reset`, `moreActions`, `bulkImport`, `productTour`. The
Spanish strings can be longer; chrome uses `flex` containers, not fixed widths.

## E2E Coverage Extension

`apps/web/app/e2e/specs/scheduler-views.spec.ts` adds scenarios already under
`testMatch: ['scheduler*.spec.ts', 'bulk-import.spec.ts']` of
`playwright.scheduler.config.ts:46`. POM extensions land in
`apps/web/app/e2e/pages/scheduler-page.ts`. Matrix: `[320x568, 360x640, 390x844, 430x932,
1280x800]`.

For each viewport: mobile shell renders (mobile viewports only); document has no
horizontal overflow; New Post visible; Filters trigger visible; View switcher exposes
Day / 3 Days / Week; period nav arrows ≥ 44 px; Day renders one column with long-weekday
label (`Friday, July 10`); 3-Days renders three columns; Week horizontally scrolls
without compressing columns below 120 px; opening Filters sheet, applying, URL updates,
sheet closes; Reset clears `status` and `channels[]` while keeping `view`; Bulk Import
menu item carries `data-testid="open-bulk-import"` and opens the modal; post card tap
opens the detail modal.

POM additions: `viewSwitcher`, `dayViewButton`, `threeDaysViewButton`, `agendaViewButton`,
`mobileFiltersTrigger`, `mobileFiltersSheet`, `mobileOverflowMenu`,
`bulkImportOverflowItem`, `productTourOverflowItem`, `newPostPrimaryButton`,
`prevPeriodButton` / `nextPeriodButton` (asserted `boundingBox >= 44`).

## Test Layout

**Vitest (existing files).** `useCalendarUrl.test.ts` gains scenarios for `normalizeView`
(explicit allowed, default per surface, invalid canonicalization, omit-when-default),
`setView` (push), `stepPeriod` view-aware (day ±1, 3-days ±3, week ±7). `calendarRange.test.ts`
gains day and three-days scenarios (shape, timezone, exclusive upper bound).
`CalendarHeader.test.ts` asserts the mobile header omits inline selects on mobile
(`lg:hidden` segments) and preserves them on desktop (`lg:flex` segments).

**Vitest (new files).** `SchedulerFiltersSheet.test.ts` (count rendering, Apply closes
sheet, Reset semantics). `MobileOverflowMenu.test.ts` (items + testids + handlers).
`SchedulerTimelineBody.test.ts` (1 / 3 / 7 day configurations, sticky gutter class on
the Week wrapper).

**Playwright.** New scenarios land in `scheduler-views.spec.ts` (above matrix). The
`publication-calendar-sse` Playwright lane keeps passing because Bulk Import remains
triggerable via `data-testid="open-bulk-import"`.

## Merge Boundary with `publication-calendar-sse`

`publication-calendar-sse` is in `apply`. Both edits land in `SchedulerView.vue` and
`useCalendarUrl.ts`. The slice is orthogonal:

- `publication-calendar-sse` adds SSE lifecycle hooks inside existing lifecycle blocks
  (`SchedulerView.vue:84–115`); this change adds mobile-only rendering branches in the
  same template.
- `publication-calendar-sse` does not touch URL state; this change extends
  `useCalendarUrl` additively with `view`, `setView`, view-aware `stepPeriod`, and
  `normalizeView`.
- `buildQuery` continues to ignore `view` from canonical equivalence until `normalizeView`
  runs, so an uncanonicalized `view=hourly` is still flagged by
  `needsCanonicalization.value` without breaking the SSE controller tests.
- Apply task sequence: this change lands after the SSE wire-mapping changes in
  `useCalendarUrl`; merging either order produces the same final state.

## Risks

| Risk | Mitigation |
|---|---|
| EN/ES locale split | Both `locales/{en,es}/scheduler.ts` ship in the same change; aggregator already imports `./scheduler`. |
| `data-testid="open-bulk-import"` preserved | The testid moves into the `DropdownMenu` item; `bulk-import.spec.ts` continues to find it. POM exposes `bulkImportOverflowItem`. |
| Sticky gutter + 120 px floor regresses | E2E asserts horizontal scroll and no document overflow at 320 / 360 / 390 / 430. |
| `publication-calendar-sse` collision | Additive composition inside lifecycle blocks; URL contract change is additive. |

