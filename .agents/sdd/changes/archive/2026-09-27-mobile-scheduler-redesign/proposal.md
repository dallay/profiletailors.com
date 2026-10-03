# Proposal: Mobile Scheduler Redesign

## Intent

Make the scheduler a deliberate mobile scheduling experience while preserving the URL contract,
all backend semantics, and the desktop density. The scheduler must become a phone-first
information architecture: the calendar dominates the viewport, primary actions stay reachable,
filters and secondary actions collapse, and the week does not compress seven columns into
unusable widths. Desktop users perceive no change.

## Background

Three concrete defects are visible in the current code:

1. **Toolbar overload.** `CalendarHeader.vue` (lines 76–199) renders a `flex flex-wrap` toolbar
   with a per-view month/week toggle, a Calendar/List toggle, three `<select>` filters
   (timezone, platform, status), and a "New Post" button. On 390 px every element wraps to
   multiple rows, the Month/Week and Calendar/List pills lose their pairing, and the platform
   select overflows.
2. **Hard-coded seven-column week.** `SchedulerView.vue` line 719 hard-codes
   `grid-cols-[48px_repeat(7,minmax(0,1fr))]` for the time-axis row, and line 744 repeats the
   same template for every hour row. On a 390 px viewport each column collapses to roughly
   `viewport_width / 7`, producing unreadable time slots.
3. **Dedicated Bulk Import row.** `SchedulerView.vue` lines 633–635 render
   `<div class="flex justify-end"><Button data-testid="open-bulk-import" …>Bulk Import</Button></div>`,
   which has no relationship to the calendar grid and pushes the mobile canvas down. The
   `app-tour` entry point (`AppHeader.vue` lines 60–64, `data-testid="start-tour-btn"`) has no
   scheduler-specific affordance.

The vocabulary is partly in place: `CalendarHeader.vue` line 25 pre-types
`calendarView: 'month' | 'week' | 'day'`; `periodLabel` (`SchedulerView.vue` lines 302–323) has a
"Day view" branch keyed off `calendarView === 'week'` but never exercises it. The timeline grid
is the missing piece.

## Scope

### In Scope

- URL-backed Day, 3 Days, Week, and Agenda views rendered under `/scheduler/calendar/{week,month,list}`.
- A new `view` query param (`day | 3-days | week | month | agenda`) on `calendar-week` /
  `calendar-month`. On `list` the rendered view is fixed to `agenda`.
- Mobile filter trigger that opens the existing shadcn-vue `Sheet` (`side="bottom"`) holding
  channels / status / timezone plus Apply and Reset; URL state remains canonical.
- Mobile overhead `…` `DropdownMenu` holding Bulk Import and Product tour on mobile only.
- Horizontally scrollable Week with sticky 48 px time gutter and `min-w-[120px]` day columns.
- A common timeline body component shared by Day / 3 Days / Week; Agenda reuses the list surface.
- Locale additions in `apps/web/app/src/shared/i18n/locales/{en,es}/scheduler.ts` (EN + ES in the
  same change).
- POM extensions in `apps/web/app/e2e/pages/scheduler-page.ts`; new scenarios in
  `scheduler-views.spec.ts` (already under `testMatch` of the existing scheduler Playwright
  config).
- Delta specs for `scheduler-url-state-standard` and `visual-calendar`.

### Out of Scope

- Backend, API contracts, `shared/web` consent / locale.
- Create Post modal layout (DALLAY-600).
- Bulk Import flow internals, provider integrations.
- Removal of `/scheduler/calendar/week | month | list` or the `scheduler-calendar-day` legacy
  alias.
- Profile Tailors visual identity.

## Capabilities

### New Capabilities

- `mobile-scheduler-shell`: mobile-only split in `SchedulerView.vue` driven by
  `useMediaQuery('(max-width: 768px)')` (the precedent in `SidebarProvider.vue` line 22).
  Includes the filter `Sheet`, the overhead `DropdownMenu`, and the compact mobile toolbar.
- `scheduler-view-registry`: discriminated union of views (`day | 3-days | week | month | agenda`)
  owned by `useCalendarUrl`, with canonicalization, view-aware `stepPeriod`, and `setView`.

### Modified Capabilities

- `scheduler-url-state-standard`: delta spec adds the `view` query param and its enumerated
  values; the legacy day-route scenario continues to canonicalize to `calendar-week`.
- `visual-calendar`: delta spec replaces the "Day view is NOT a top-level route" paragraph with
  the new view list and the horizontal-scroll / sticky-gutter / min 120 px day-column
  constraint.

## Approach

**URL contract extension.** Extend `useCalendarUrl` (`application/useCalendarUrl.ts`) with a
`SchedulerView` discriminated union, a `view` field on `CalendarUrlState`, canonicalization rules
(`normalizeView` accepts only the five allowed values; absent means default for the current
surface), a `setView(view)` action, and view-aware `stepPeriod` (`day` ±1, `3-days` ±3,
`week` ±7). The `view` key follows the existing `buildQuery` rule: omitted when equal to the
surface default. `getCalendarRange` (`application/calendarRange.ts`) gains `day | three-days` in
its `CalendarSurface` union, reusing the same Sunday-aligned weekday math as week and skipping
weekday alignment for a single day. The route family stays
`/scheduler/calendar/{week,month,list}`; no new route names; the `scheduler-calendar-day` alias
remains a legacy redirect.

**Mobile shell split.** Inside `SchedulerView.vue`, gate mobile-only abstractions on
`useMediaQuery('(max-width: 768px)')` — the same primitive `SidebarProvider.vue` already uses.
On mobile, the existing `flex flex-wrap` toolbar is replaced by a compact task-oriented header
containing the page context, `New Post`, previous/next period, the period label, `Today`, and a
single view selector. Filters collapse behind one trigger that opens the existing shadcn-vue
`Sheet` (`side="bottom"`); Apply and Reset are wired as `DialogClose as-child` instances
following the precedent at `SheetContent.vue` lines 49–58. Bulk Import and Product tour move
into an overhead `…` `DropdownMenu`; `data-testid="open-bulk-import"` stays on the menu item so
`bulk-import.spec.ts` (lines 90–91, 136–137, 169–170) continues to pass. Desktop keeps the
existing inline `<select>` controls and the visible `Bulk Import` row.

**Common timeline body component.** Day / 3 Days / Week render through one timeline body
component parameterized by `days`. Week uses an `overflow-x-auto` viewport with the 48 px time
gutter as a sticky first column and `min-w-[120px]` on each day column; on viewports narrower
than the natural grid width, the calendar scrolls instead of compressing. Agenda reuses the
existing list surface as a chronological feed. The `publication-calendar-sse` apply slice lives
inside the same `onMounted` / `onUnmounted` / `workspaceStore.activeWorkspaceId` watcher
blocks; mobile-shell split is additive composition, not a replacement.

**Progressive enhancement on desktop.** Desktop renders unchanged: inline filters, visible
`Bulk Import`, drag-and-drop rescheduling, full-width seven-column week.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `apps/web/app/src/modules/publishing/application/useCalendarUrl.ts` | Modify | Add `SchedulerView`, `view`, canonicalization, `setView`, view-aware `stepPeriod`. |
| `apps/web/app/src/modules/publishing/application/calendarRange.ts` | Modify | Extend `CalendarSurface` with `'day' \| 'three-days'`; reuse Sunday alignment. |
| `apps/web/app/src/modules/publishing/application/index.ts` | Modify | Export new `view`-related symbols. |
| `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | Modify | Mobile shell split; gate common timeline body by `view`; preserve SSE lifecycle hooks. |
| `apps/web/app/src/modules/publishing/presentation/components/CalendarHeader.vue` | Modify | Mobile filter trigger, overhead `DropdownMenu`, view selector for day / 3-days / week. |
| `apps/web/app/src/modules/publishing/presentation/components/SchedulerTimelineBody.vue` | Create | Shared timeline body component for Day / 3 Days / Week (sticky gutter, day-column width). |
| `apps/web/app/src/modules/publishing/presentation/components/SchedulerFiltersSheet.vue` | Create | Bottom-sheet wrapper around channels / status / timezone with Apply / Reset. |
| `apps/web/app/src/modules/publishing/presentation/components/SchedulerOverflowMenu.vue` | Create | Overhead `…` `DropdownMenu` for Bulk Import and Product tour (mobile only). |
| `apps/web/app/src/shared/i18n/locales/en/scheduler.ts`, `…/es/scheduler.ts` | Modify | Add `viewDay`, `viewThreeDays`, `viewWeek`, `viewMonth`, `viewAgenda`, `filters`, `apply`, `reset`, `moreActions`, `bulkImport`, `productTour`. |
| `apps/web/app/e2e/pages/scheduler-page.ts` | Modify | Add `dayViewButton`, `threeDaysViewButton`, `agendaViewButton`, sheet and overflow POMs. |
| `apps/web/app/e2e/specs/scheduler-views.spec.ts` | Create/Modify | Mobile view, sheet, overflow, and horizontal-scroll scenarios at 320 / 390 / 430. |
| `openspec/specs/scheduler-url-state-standard/spec.md` | Modify | Delta spec for the `view` query param. |
| `openspec/specs/visual-calendar/spec.md` | Modify | Replace "Day view is NOT a top-level route"; add scroll/gutter/width constraint. |
| `openspec/changes/publication-calendar-sse/` (sibling change) | Coordinate | This change must merge cleanly; SSE lifecycle lives inside existing `SchedulerView` lifecycle blocks. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `publication-calendar-sse` apply slice collides on `SchedulerView.vue` / `useCalendarUrl.ts`. | Medium | Mobile-shell split is additive composition inside the existing `onMounted` / `onUnmounted` / workspace-watcher blocks. `view` slices cleanly off the URL contract (SSE does not touch URL state). Verify after each apply by running `just app-test-e2e` scheduler lanes. |
| `data-testid="open-bulk-import"` testid breaks `bulk-import.spec.ts` (lines 90–91, 136–137, 169–170). | Medium | The testid moves with the trigger into the `DropdownMenu` item; the Bulk Import modal still opens. POM exposes a `bulkImportOverflowItem` locator. |
| Locale additions split across EN / ES in separate changes. | Medium | Both `apps/web/app/src/shared/i18n/locales/en/scheduler.ts` and `…/es/scheduler.ts` ship in the same change; aggregator `index.ts` already imports `./scheduler`. |
| Mobile filter `Sheet` focus / keyboard regression. | Low | Reuse the shadcn-vue `Sheet` primitive (`reka-ui` focus trap + ESC); Apply and Reset are `DialogClose as-child`; controls use shadcn-vue labels. |
| Week overflow at narrow widths regresses to seven-column compression. | Low | Day-column template is `min-w-[120px]`; viewport is `overflow-x-auto`; document-level horizontal scroll is asserted in the E2E matrix. |
| Spanish copy clipped by fixed-width containers. | Low | No fixed-width containers wrap new copy; locale strings are reused for trigger labels. |

## Rollback Plan

Revert the frontend changes to `useCalendarUrl.ts`, `calendarRange.ts`, `SchedulerView.vue`,
`CalendarHeader.vue`, the three new components, both locale files, and the POM / E2E specs as
one change. The `view` query param is additive and absent means default, so removing it leaves
no orphaned state. The `scheduler-calendar-day` legacy alias is never removed, so no
re-canonicalization side effects. The `scheduler-url-state-standard` and `visual-calendar` delta
specs revert to their pre-change state. No backend, API, or `shared/web` rollback required.

## Dependencies

- Existing `@vueuse/core` `useMediaQuery` (already a direct dependency in `apps/web/app/package.json`).
- Existing shadcn-vue `Sheet` and `DropdownMenu` primitives under `apps/web/app/src/components/ui/`.
- Existing `@internationalized/date` (already used by `getCalendarRange`).
- Existing scheduler Playwright config `apps/web/app/e2e/playwright.scheduler.config.ts`.
- No new package, no new dependency, no backend change.

## Success Criteria

- [ ] URL state is canonical for all four mobile views (share / restore / refresh round-trip).
- [ ] Mobile filters live behind a single trigger; URL state remains canonical; Apply / Reset
      semantics are testable.
- [ ] Mobile Week does not compress seven columns into ~40 px; horizontally scrollable; time
      gutter sticky; `min-w-[120px]` day columns.
- [ ] Bulk Import and Product tour remain reachable from a single mobile overflow; desktop
      unchanged (visible `Bulk Import` row, inline filter selects).
- [ ] New Post remains a directly visible primary action on mobile.
- [ ] Document has no unintended horizontal overflow at 320 / 390 / 430 widths.
- [ ] All existing scheduler Playwright E2E lanes remain green
      (`scheduler-views.spec.ts`, `scheduler-calendar.spec.ts`, `bulk-import.spec.ts`).
- [ ] No backend, API, or `shared/web` contract changes.
- [ ] SchedulePost quick-create, drag-drop, post-detail modal, list surface, recurring
      schedules, reconnect prompt, and the `publication-calendar-sse` lifecycle continue to
      function on both desktop and mobile.
- [ ] `data-testid="open-bulk-import"` continues to be reachable on both desktop and mobile.

## References

- Linear: <https://linear.app/dallay/issue/DALLAY-601>
- GitHub: <https://github.com/dallay/profiletailors.com/issues/1191>
- Branch: `feature/dallay-601-frontendmobilescheduler-redesign-scheduler-for-a-mobile`
- Sibling change: `openspec/changes/publication-calendar-sse/`
- Source specs:
  - `openspec/specs/scheduler-url-state-standard/spec.md`
  - `openspec/specs/visual-calendar/spec.md`
  - `openspec/specs/publishing/spec.md`
- Surface context: `apps/web/app/PRODUCT.md`
- Design system: `.agents/DESIGN.md`