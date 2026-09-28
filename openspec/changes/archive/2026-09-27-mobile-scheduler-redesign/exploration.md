# Exploration: Mobile Scheduler Redesign

## Purpose

Add four URL-backed calendar views (Day, 3 Days, Week, Agenda) and a mobile-first shell so the
scheduler is usable on phone-sized viewports without redesigning the route family, the URL
contract, or the desktop layout. The change is a thin delta over the existing `useCalendarUrl`,
`getCalendarRange`, `useCalendarRevalidation`, `useReactiveClock`, and `CalendarHeader.vue`
primitives, and a mobile-only split in `SchedulerView.vue` driven by `vueuse/core`'s
`useMediaQuery('(max-width: 768px)')` (the same primitive `SidebarProvider.vue` already uses).

## Background

The scheduler route family lives at `apps/web/app/src/router/index.ts` (lines 59–90) and renders
`apps/web/app/src/modules/publishing/views/SchedulerView.vue` for every entry. `useCalendarUrl`
already exposes a canonical URL controller, the `scheduler-url-state-standard` spec already
guarantees that `/scheduler/calendar/day` MUST canonicalize to
`/scheduler/calendar/week?…same query…`, and the visual calendar spec already requires day /
week / month views. Today the calendar exposes week and month inside the calendar routes and a
separate `list` surface — there is no Day or 3 Days timeline and no Agenda grouping.

Three concrete mobile defects are visible in the current code:

1. **Header overload on small viewports.** `CalendarHeader.vue` (lines 76–199) renders a
   flex-wrap toolbar containing a per-view month/week toggle, a Calendar/List toggle, three
   `<select>` controls (timezone, platform, status), and a "New Post" button. On a 390 px
   viewport every element wraps to multiple rows, the three pills (Month / Week, Calendar / List)
   lose their pairing, and the platform select overflows.

2. **Hard-coded seven-column week.** `SchedulerView.vue` line 719 hard-codes the time-axis plus
   seven equally-sized day columns:

   ```html
   <div class="shrink-0 grid grid-cols-[48px_repeat(7,minmax(0,1fr))] border-b border-border-subtle bg-bg-primary">
   ```

   and line 744 repeats the same `grid-cols-[48px_repeat(7,minmax(0,1fr))]` for every hour row.
   The `repeat(7,minmax(0,1fr))` collapses each day column to roughly `viewport_width / 7` on a
   390 px screen, producing unreadable time slots.

3. **Dedicated Bulk Import row on every viewport.** `SchedulerView.vue` lines 633–635 render a
   separate flex-justified row for Bulk Import:

   ```html
   <div class="flex justify-end">
     <Button data-testid="open-bulk-import" variant="outline" class="gap-2" @click="isBulkModalOpen = true">Bulk Import</Button>
   </div>
   ```

   This row has no relationship to the calendar grid and pushes the mobile canvas down, while the
   existing `app-tour` entry point (`AppHeader.vue` lines 60–64, `data-testid="start-tour-btn"`)
   has no scheduler-specific affordance at all.

The `calendarView` derived from the URL (`SchedulerView.vue` lines 39–42) is a binary
`'month' | 'week'` derived from `surface`, and `periodLabel` (lines 302–323) already has a "Day
view" branch keyed off `calendarView === 'week'` but uses a `'long weekday'` format that is never
exercised — a clue that a Day timeline was considered and not shipped. `CalendarHeader.vue` line
25 also pre-types `calendarView: 'month' | 'week' | 'day'` even though only `month | week` ever
flows through. The vocabulary is in place; the timeline grid is not.

## Goals & non-goals

### Goals (locked by the user)

1. Mobile view architecture in full scope: **Day, 3 Days, Week, Agenda** as real URL-backed views.
2. URL contract extends rather than restructures. Keep `/scheduler/calendar/{week,month,list}` as
   the only canonical surfaces and add a `view` query param with values `day | 3-days | week |
   month | agenda`. The existing `surface` continues to encode the route family
   (`calendar-week`, `calendar-month`, `list`). On `calendar-week` the rendered view is whichever
   `view` says (default `week`); on `calendar-month` the rendered view defaults to `month`; on
   `list` the rendered view is fixed `agenda`. `useCalendarUrl` and the
   `scheduler-url-state-standard` spec must be reconciled as a delta spec.
3. Filters UI: a single mobile filter trigger opens the existing shadcn-vue `Sheet` (see
   `apps/web/app/src/components/ui/sheet/*`, with `side="bottom"` confirmed in
   `SheetContent.vue` line 19) with channels / status / timezone inside, plus Apply and Reset
   buttons. URL state remains canonical. Desktop keeps the existing inline selects.
4. Secondary actions: Bulk Import and Product tour move into an overhead `…` `DropdownMenu`
   (`apps/web/app/src/components/ui/dropdown-menu/*`) on mobile only. Desktop keeps the existing
   visible Bulk Import row.
5. Calendar layout for Week: horizontally scrollable, sticky/fixed time gutter (48 px), minimum
   day-column width around 120 px, no seven-column compression. Day / 3 Days / Week share a
   common timeline body component parameterized by days; Agenda reuses the existing list surface
   as a chronological feed.
6. Reuse `useCalendarUrl`, `getCalendarRange`, `useReactiveClock`, `useCalendarRevalidation`
   without rewriting them.

### Non-goals

- No backend changes; no API contract changes; no `shared/web` changes.
- No rewrite of the desktop calendar layout, the Create Post modal, or the Bulk Import flow.
- No new route family and no removal of the `scheduler-calendar-day` legacy alias.
- No changes to the visual identity (Profile Tailors Nothing-inspired theme in
  `.agents/DESIGN.md`).

## Current architecture

| File | Role |
|---|---|
| `apps/web/app/src/router/index.ts` (lines 59–90) | Declares the four scheduler routes; all resolve to `SchedulerView.vue`. |
| `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | Owns URL→UI rendering: calendar grid, list, Bulk Import row, recurring schedules, modals. 993 lines. |
| `apps/web/app/src/modules/publishing/presentation/components/CalendarHeader.vue` | Flex-wrap toolbar with view toggle, surface toggle, three `<select>` filters, and "New Post" button. |
| `apps/web/app/src/modules/publishing/application/useCalendarUrl.ts` | `createCalendarUrlController` + `useCalendarUrl()`. Owns `surface`, `date`, `timezone`, `status`, `q`, `channelIds`, `postId`. |
| `apps/web/app/src/modules/publishing/application/calendarRange.ts` | `getCalendarRange(date, surface, timezone)` — currently `'week' \| 'month'` only. Uses `@internationalized/date`. |
| `apps/web/app/src/modules/publishing/application/useReactiveClock.ts` | One-minute reactive `Date` aligned to wall-clock minute with visibility handling. |
| `apps/web/app/src/modules/publishing/application/useCalendarRevalidation.ts` | Debounced, coalesced, visibility-aware fetcher for `CalendarRange`. |
| `apps/web/app/src/modules/publishing/application/usePublicationEventReconnect.ts` | SSE/BroadcastChannel reconnect adapter used by `SchedulerView`. |
| `apps/web/app/src/modules/publishing/application/calendar-invalidation-channel.ts` | BroadcastChannel-based cross-tab invalidation. |
| `apps/web/app/src/components/ui/sheet/*` | shadcn-vue `Sheet` primitive with `side="top" \| "right" \| "bottom" \| "left"`. |
| `apps/web/app/src/components/ui/dropdown-menu/*` | shadcn-vue `DropdownMenu` primitive set (Trigger / Content / Item / etc.). |
| `apps/web/app/src/components/ui/sidebar/SidebarProvider.vue` (line 22) | Existing precedent: `const isMobile = useMediaQuery('(max-width: 768px)')` from `@vueuse/core`. |
| `apps/web/app/src/shared/i18n/locales/{en,es}/scheduler.ts` | Existing `scheduler` translation bundle; both `index.ts` files already import it. |
| `apps/web/app/e2e/playwright.scheduler.config.ts` | Scheduler-only Playwright config, `testMatch: ['scheduler*.spec.ts', 'bulk-import.spec.ts']`, dev server on `5173`, timezone `Europe/Madrid`, locale `en-US`. |
| `apps/web/app/e2e/pages/scheduler-page.ts` | Page Object Model: heading (`/all channels/i`), `weekViewButton` (`/week/i`), `listViewButton` (`'List', exact`), `todayButton`, `forwardButton`, `backwardButton`. |
| `openspec/specs/visual-calendar/spec.md` | Source spec. Already requires day / week / month views. |
| `openspec/specs/scheduler-url-state-standard/spec.md` | Source spec. Already enumerates the query keys and canonical routes. |

## Existing capability inventory

| Capability | Path | Reuse plan |
|---|---|---|
| `useCalendarUrl` | `application/useCalendarUrl.ts` | Extend in-place: add `SchedulerView` discriminated union, `view` field on `CalendarUrlState`, canonicalization rules, `setView(view)`, view-aware `stepPeriod`. |
| `getCalendarRange` | `application/calendarRange.ts` | Extend the `CalendarSurface` union to include `'day' \| 'three-days'` and the `start/end` arithmetic — Day uses `date.subtract({ days })` already in place, 3 Days uses `add({ days: 3 })`. |
| `useReactiveClock` | `application/useReactiveClock.ts` | Use unchanged. The body component already aligns with minute boundaries. |
| `useCalendarRevalidation` | `application/useCalendarRevalidation.ts` | Use unchanged. `request(range)` already supports any `CalendarRange` shape; only the range shape changes. |
| `usePublicationEventReconnect` | `application/usePublicationEventReconnect.ts` | Use unchanged. The SSE lifecycle stays inside `SchedulerView`. |
| `Sheet` (shadcn-vue) | `components/ui/sheet/*` | Reuse for the mobile filter bottom-sheet; `side="bottom"` is the default animation state in `SheetContent.vue` lines 41–47. |
| `DropdownMenu` (shadcn-vue) | `components/ui/dropdown-menu/*` | Reuse for the mobile overhead `…` menu. |
| `SidebarProvider.isMobile` | `components/ui/sidebar/SidebarProvider.vue` (line 22) | Reuse the `useMediaQuery('(max-width: 768px)')` precedent; do not invent a new responsive helper. |
| `vueuse/core` `useMediaQuery` | `package.json` | Already a direct dependency. |
| `Sheet`-based sidebar | `components/ui/sidebar/Sidebar.vue` (line 33) | Direct precedent for combining `Sheet` with `isMobile` to swap UI shells. |
| scheduler Playwright config | `e2e/playwright.scheduler.config.ts` | Reuse unchanged. New mobile view scenarios go in `scheduler-views.spec.ts` (already under `testMatch`). |
| `SchedulerPage` POM | `e2e/pages/scheduler-page.ts` | Extend with `dayViewButton`, `threeDaysViewButton`, `agendaViewButton`, `openFilterSheet`/`applyFilterButton`/`resetFilterButton`, `openOverflowMenu`/`bulkImportOverflowItem`/`productTourOverflowItem`. |

## Touch points & risk surface

### Must change

- `apps/web/app/src/modules/publishing/application/useCalendarUrl.ts` — add `view`, `SchedulerView`,
  `VALID_VIEWS`, `setView`, view-aware `stepPeriod` (`'day'` steps ±1, `'3-days'` steps ±3,
  `'week'` steps ±7), `normalizeView`, `buildQuery` rule for the `view` key, canonicalization
  branch.
- `apps/web/app/src/modules/publishing/application/calendarRange.ts` — extend `CalendarSurface`
  with `'day' | 'three-days'`; reuse the same Sunday-aligned weekday math for week / 3-days, and
  skip weekday alignment for single-day.
- `apps/web/app/src/modules/publishing/views/SchedulerView.vue` — derive `view` from
  `url.state.value.view` (with defaults per surface), feed `getCalendarRange` with the extended
  surface, gate mobile-only shell, replace inline Bulk Import row on mobile, gate the shared
  timeline component by `view`.
- `apps/web/app/src/modules/publishing/presentation/components/CalendarHeader.vue` — add mobile
  filter trigger (`Sheet` opener), overhead `DropdownMenu` for secondary actions on mobile,
  expose `day` and `3-days` toggles when `surface === 'calendar-week'`, and emit `change:view`
  with the new `SchedulerView` values.
- `apps/web/app/src/shared/i18n/locales/{en,es}/scheduler.ts` — add `viewDay`, `viewThreeDays`,
  `viewWeek`, `viewMonth`, `viewAgenda`, `filters`, `apply`, `reset`, `moreActions`, `bulkImport`,
  `productTour`. Spanish copy must remain unconstrained by fixed-width containers
  (AGENTS.md, "Key Gotchas").
- `apps/web/app/e2e/pages/scheduler-page.ts` — extend the POM per the table above.

### Must reconcile (delta specs only)

- `openspec/specs/scheduler-url-state-standard/spec.md` — extend the canonical query contract with
  `view` and its enumerated values; update the "Legacy day route is canonicalized" scenario to
  reflect that the legacy route still canonicalizes but Day is now a real view rendered under
  `calendar-week`.
- `openspec/specs/visual-calendar/spec.md` — replace the "Day view is NOT a top-level route"
  paragraph (line 14) with the new view list (`day | 3-days | week | month | agenda`) and the
  horizontal scroll / sticky gutter / min 120 px day-column constraint.

### Must remain untouched

- `apps/web/app/src/router/index.ts` route family (no new canonical route names).
- `apps/web/app/src/router/index.ts` line 81 (`scheduler-calendar-day`) — kept as a legacy alias
  that canonicalizes to `calendar-week` so the existing
  `useCalendarUrl.test.ts` "canonicalizes scheduler-calendar-day to the existing week surface"
  scenario (lines 419–447) remains green.
- `apps/web/app/src/modules/publishing/application/useCalendarRevalidation.ts`,
  `useReactiveClock.ts`, `usePublicationEventReconnect.ts`,
  `calendar-invalidation-channel.ts`.
- `apps/web/app/src/modules/publishing/application/index.ts` public surface beyond adding the
  new `view`-related exports.
- `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` (no backend / store
  contract changes).
- `openspec/changes/reactive-calendar-browser-sync/` (archived) and the in-flight
  `openspec/changes/publication-calendar-sse/` `apply` slice — they are an orthogonal capability
  that already touches `SchedulerView.vue`, so the diff must merge cleanly there.

### Risks / interaction surface

1. **`publication-calendar-sse` is mid-apply.** Its `state.yaml` lists
   `current_phase: apply` with `source_implementation_authorized: true` but `completed: [explore,
   propose, spec, design, tasks]`. Both this change and that one will edit `SchedulerView.vue`
   and `useCalendarUrl.ts` (the latter via SSE wire mapping). The slice order must keep the SSE
   wiring intact: `revalidation.request(currentRange.value)` and
   `reconnect.start/stop` continue to be called from the same lifecycle block. Concretely, the
   mobile-shell split must happen **inside** the existing `onMounted` / `onUnmounted` /
   `workspaceStore.activeWorkspaceId` watcher blocks, not as a replacement.

2. **`open-bulk-import` testid is contractual.** Three call sites in
   `apps/web/app/e2e/specs/bulk-import.spec.ts` (lines 90–91, 136–137, 169–170) rely on the
   current `<div class="flex justify-end">` row's `data-testid="open-bulk-import"` button. The
   redesign MUST keep the testid wired to the same trigger semantically (the Bulk Import modal
   must still open). On mobile the trigger moves into the `DropdownMenu` and must keep
   `data-testid="open-bulk-import"`; on desktop the existing row stays.

3. **No `event-card` testid exists.** Search across `apps/web/app/e2e/specs/` confirms the only
   `data-testid` references that touch scheduler chrome are `open-bulk-import` and
   `bulk-import-modal`. `scheduler-root`, `scheduler-workspace`, `calendar-mode`, and
   `week-timeline-viewport` are declared in `SchedulerView.vue` lines 620, 674, 675, 743 but are
   not referenced by E2E specs. The redesign can rely on Vue roles / text content for new
   affordances without preserving every testid.

4. **Sheet bottom-sheet keyboard / focus management.** The mobile filter `Sheet` inherits
   `reka-ui`'s focus trap and ESC handling. Apply and Reset buttons inside the sheet must
   dismiss the sheet on activation (the existing `DialogClose as-child` precedent at
   `SheetContent.vue` lines 49–58). All controls must use shadcn-vue primitive labels so
   accessibility rules do not regress.

5. **i18n registration is two steps.** New keys must be added in both
   `apps/web/app/src/shared/i18n/locales/en/scheduler.ts` and `…/es/scheduler.ts`, and both
   `index.ts` aggregators already import `./scheduler` (lines 11 and 35 of each). No new
   `i18n` registration work is needed beyond adding the keys.

## Open questions

1. **Minimum day-column width.** The locked decision says "around 120 px". The candidate value
   `120 px` is consistent with the existing 48 px time gutter and 7 columns on a 1024 px
   desktop viewport (`(1024 − 48) / 7 ≈ 139 px`); on a 390 px mobile viewport the timeline
   scrolls horizontally rather than compressing. Resolved at design slice: pick `min-w-[120px]`
   on the day column template — track this as a numeric rationale in `design.md` rather than as
   a magic-number `MagicNumber` finding.

2. **Breakpoint that splits desktop vs mobile.** The existing precedent
   (`SidebarProvider.vue` line 22) uses `(max-width: 768px)`. The locked decision says
   "mobile-only" abstractions should use the same breakpoint. Resolved at design slice: adopt
   the existing `768 px` boundary via a local `useMediaQuery('(max-width: 768px)')` so the
   scheduler chrome and the sidebar swap shells in lockstep.

3. **Default `view` per surface.** On `calendar-week` the default is `week`; on
   `calendar-month` the default is `month`; on `list` the rendered view is fixed `agenda`. The
   locked decision resolves this. The implementation detail — whether `view` is omitted from
   the URL when it equals the default — should follow the existing `buildQuery` rule (omit
   when equal to default). Resolved: yes, omit when default.

## Out of scope

- Backend, API contracts, `shared/web` consent / locale changes.
- Create Post modal layout, Bulk Import flow, Profile Tailors visual identity.
- Removal of any current canonical route (`/scheduler/calendar/week`, `/scheduler/calendar/month`,
  `/scheduler/list`) and removal of the `scheduler-calendar-day` legacy alias.
- Independent build of mobile-only components outside the publishing module folder (frontend
  architecture rules say cross-feature consumers must use the feature's stable `index.ts`
  barrel).