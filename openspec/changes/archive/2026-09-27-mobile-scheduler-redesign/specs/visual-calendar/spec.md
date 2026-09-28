# Delta for visual-calendar

## ADDED Requirements

### Requirement: Multi-View Calendar (mobile-first)

The visual calendar MUST expose five URL-backed views — `day`, `3-days`, `week`, `month`, and
`agenda` — every one reachable through the existing canonical surfaces
`/scheduler/calendar/{week,month,list}` together with the `view` query parameter defined in the
`scheduler-url-state-standard` spec. The allowed view surface is:

- `/scheduler/calendar/week` renders `day`, `3-days`, or `week` depending on the `view` query
  parameter
- `/scheduler/calendar/month` renders `month`
- `/scheduler/list` renders `agenda`

This requirement supersedes the legacy sentence "Day view is NOT a top-level route; clicking a
day in month/week focuses `date=YYYY-MM-DD` within the current week/month context without a
separate route." Clicking a day cell inside a month grid MUST continue to focus
`date=YYYY-MM-DD` on the same surface, but Day is now an explicit URL-backed view under
`/scheduler/calendar/week?view=day` rather than a hidden branch. The timeline body shared by
`day`, `3-days`, and `week` MUST use a sticky 48 px time gutter (the leftmost column showing
hour labels) that remains pinned while the day columns scroll. When the natural width of the
day columns exceeds the viewport width, the timeline viewport MUST be horizontally scrollable
and each day column MUST keep a `min-w-[120px]` minimum width so columns are never compressed
below a readable size. The period label rendered for the `day` view MUST use the long-weekday
plus day-of-month format (for example `Friday, July 10`).

#### Scenario: User opens each URL-backed view at a phone viewport

- GIVEN an authenticated user opens `/scheduler/calendar/week?view=day`,
  `?view=3-days`, `?view=week`, `?view=month`, or `/scheduler/list`
- WHEN each URL resolves
- THEN the rendered surface MUST match the requested view
- AND the calendar MUST dominate the viewport at 320, 360, 390, and 430 px widths
- AND the URL MUST contain `view=<value>` for the calendar-week variants and MUST omit `view`
  when the value matches the surface default

#### Scenario: Week timeline scrolls horizontally when columns exceed viewport

- GIVEN the user is on `/scheduler/calendar/week?view=week` at a 390 px viewport
- WHEN the timeline body renders
- THEN the time gutter MUST remain 48 px wide and pinned on the left
- AND each day column MUST be at least 120 px wide
- AND the calendar viewport MUST scroll horizontally rather than compress columns below 120 px

#### Scenario: Day view period label uses long-weekday format

- GIVEN the user navigates to `/scheduler/calendar/week?view=day&date=2026-07-10`
- WHEN the scheduler header renders the period label
- THEN the label MUST contain the long weekday name and the day-of-month
- AND the label MUST match the `Friday, July 10` shape the unused `SchedulerView.vue`
  `periodLabel` branch already produces

#### Scenario: Clicking a day in month focuses the date on month

- GIVEN the user is on `/scheduler/calendar/month?date=2026-06-15`
- WHEN the user clicks a day cell (e.g., June 20)
- THEN the URL becomes `/scheduler/calendar/month?date=2026-06-20`
- AND the calendar centers on June 20 while remaining in month view
- AND `view` is omitted from the URL

### Requirement: Mobile filters are a single trigger

On viewports at or below the existing `(max-width: 768px)` mobile breakpoint — the same boundary
the repository's `SidebarProvider.vue` already uses — the scheduler MUST expose a single visible
filter trigger instead of three inline `<select>` controls. Opening that trigger MUST present
the existing shadcn-vue `Sheet` primitive at `side="bottom"` containing channels, status, and
timezone controls together with Apply and Reset actions. The Apply and Reset actions MUST commit
their changes through the same canonical URL state used by the desktop inline selects
(`status`, `q`, `channels[]`, `timezone`), and MUST close the sheet on activation. The trigger
MUST display the active filter count whenever at least one filter is non-default, and MUST NOT
display a count when no filter differs from its default. On viewports wider than the mobile
breakpoint, the existing inline `<select>` controls MUST continue to render and the mobile
trigger MUST NOT be rendered.

#### Scenario: Opening the mobile filter sheet shows all filter controls

- GIVEN an authenticated user opens `/scheduler/calendar/week?view=day` at a viewport at or
  below 768 px
- WHEN the user activates the filter trigger
- THEN a `Sheet` with `side="bottom"` MUST appear
- AND the sheet MUST contain channels, status, and timezone controls
- AND the sheet MUST contain Apply and Reset actions

#### Scenario: Changing each control updates the URL state

- GIVEN the mobile filter sheet is open on `/scheduler/calendar/week?view=day`
- WHEN the user selects a channel, a status, and a timezone, then activates Apply
- THEN the sheet MUST close
- AND the URL MUST become
  `/scheduler/calendar/week?view=day&channels[]=<id>&status=<value>&timezone=<zone>`
- AND the rendered calendar MUST reflect the new filter context
- AND `view=day` MUST remain in the URL because `day` is not the surface default for
  `/scheduler/calendar/week`

#### Scenario: Reset returns to the default filter state

- GIVEN the mobile filter sheet is open on
  `/scheduler/calendar/week?view=day&status=draft&channels[]=acc-123`
- WHEN the user activates Reset
- THEN the sheet MUST close
- AND the URL MUST drop `status` and `channels[]`
- AND `view=day` MUST remain in the URL
- AND the calendar MUST render with no filter context beyond `date` and `timezone`

#### Scenario: Filter trigger shows the active filter count

- GIVEN the user has set two channels and one status on
  `/scheduler/calendar/week?view=day&channels[]=acc-1&channels[]=acc-2&status=draft`
- WHEN the scheduler header renders
- THEN the filter trigger MUST display a non-zero count
- WHEN the user resets to the default filter state
- THEN the filter trigger MUST display no count

### Requirement: Mobile secondary actions menu

On viewports at or below the existing `(max-width: 768px)` mobile breakpoint, the scheduler MUST
expose an overhead `…` trigger that opens a `DropdownMenu` containing Bulk Import and Product
tour actions. Opening the Bulk Import entry MUST continue to surface the existing Bulk Import
modal and MUST keep the `data-testid="open-bulk-import"` testid on the trigger so existing
`bulk-import.spec.ts` references at lines 90–91, 136–137, and 169–170 remain satisfied. Opening
the Product tour entry MUST reuse the existing `data-testid="start-tour-btn"` entry point
already declared in `AppHeader.vue` lines 60–64, scoped to the scheduler context. On viewports
wider than the mobile breakpoint the scheduler MUST render the existing visible Bulk Import row
and MUST NOT render the overhead `…` menu.

#### Scenario: Bulk Import reachable from the mobile menu

- GIVEN an authenticated user opens `/scheduler/calendar/week?view=day` at a viewport at or
  below 768 px
- WHEN the user opens the overhead `…` menu and activates Bulk Import
- THEN the Bulk Import modal MUST open
- AND the activation target MUST carry `data-testid="open-bulk-import"`

#### Scenario: Product tour reachable from the mobile menu

- GIVEN an authenticated user opens `/scheduler/list` at a viewport at or below 768 px
- WHEN the user opens the overhead `…` menu and activates Product tour
- THEN the scheduler app tour MUST start
- AND the existing `data-testid="start-tour-btn"` entry point MUST remain reachable from the
  scheduler chrome

#### Scenario: Desktop keeps the visible Bulk Import row

- GIVEN an authenticated user opens `/scheduler/calendar/week` at a viewport wider than 768 px
- WHEN the scheduler header renders
- THEN the Bulk Import button MUST render in the existing visible row with
  `data-testid="open-bulk-import"`
- AND the overhead `…` menu MUST NOT render

### Requirement: Mobile touch and readability

On viewports at or below the existing `(max-width: 768px)` mobile breakpoint the scheduler MUST
remain phone-usable: the calendar body MUST be the dominant viewport area, every interactive
chrome element MUST expose a hit target of at least 44 px in either dimension (the visible icon
may stay small as long as the surrounding button is at least 44 px square), primary chrome
labels (New Post, period label, view selector, Today, and the filter / overflow triggers) MUST
render at a font size of at least 11 px so they remain readable on a 390 px viewport, and the
document MUST NOT introduce unintended horizontal overflow at 320, 360, 390, or 430 px widths.

#### Scenario: Calendar dominates the viewport on small phones

- GIVEN an authenticated user opens `/scheduler/calendar/week?view=day` at a 360 px viewport
- WHEN the scheduler renders
- THEN the calendar body MUST occupy the majority of the visible viewport area
- AND the toolbar chrome MUST NOT push the calendar off-screen

#### Scenario: Navigation arrows expose at least a 44 px hit target

- GIVEN an authenticated user opens `/scheduler/calendar/week?view=day` at a 390 px viewport
- WHEN the scheduler renders the period navigation arrows and the list button
- THEN each control MUST be at least 44 px square
- AND tapping the visual icon area or its immediate padding MUST activate the control

#### Scenario: Primary mobile chrome uses readable type sizes

- GIVEN an authenticated user opens `/scheduler/list` at a 390 px viewport
- WHEN the scheduler header renders
- THEN New Post, the period label, the view selector, and Today MUST render at a font size of
  at least 11 px
- AND no primary chrome label MUST render at 8 or 9 px

#### Scenario: No horizontal overflow at phone widths

- GIVEN an authenticated user opens `/scheduler/calendar/week?view=day`,
  `?view=3-days`, `?view=week`, `?view=month`, or `/scheduler/list` at 320, 360, 390, and 430
  px viewports
- WHEN each view renders
- THEN the document MUST NOT introduce horizontal scrolling at the document level
- AND the Week timeline's horizontal scroll MUST be confined to its own viewport container

#### Scenario: Day and 3 Days navigation step by their own length

- GIVEN the user is on `/scheduler/calendar/week?view=day`
- WHEN the user activates the next-period control
- THEN the URL `date` MUST advance by exactly one calendar day
- AND when the user is on `/scheduler/calendar/week?view=3-days`
- WHEN the user activates the next-period control
- THEN the URL `date` MUST advance by exactly three calendar days
- AND Today MUST return `date` to the current local date regardless of the active view
