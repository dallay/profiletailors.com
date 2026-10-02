# Dashboard Scheduling Specification

## Purpose

Combines two scheduling widgets: a 7×24 heatmap of engagement scores (`HeatmapGrid`) and a compact list of the next scheduled posts (`UpcomingSchedule`).

## Data Model

```ts
interface PostingTimeSlot {
  day: 'Mon' | 'Tue' | 'Wed' | 'Thu' | 'Fri' | 'Sat' | 'Sun'
  hour: number             // 0–23
  score: number             // 0–100
}

interface ScheduleItem {
  id: string
  title: string
  platform: Platform
  scheduledFor: string     // ISO 8601
  status: 'queued' | 'scheduled' | 'published'
}
```

## Requirements

### Requirement: 7×24 Heatmap

`HeatmapGrid` SHALL render a 7-row by 24-column heatmap of engagement scores. Days are rendered top-to-bottom in the order `Mon → Sun`. Hour labels appear every three hours (`0:00, 3:00, 6:00, 9:00, 12:00, 15:00, 18:00, 21:00`).

#### Scenario: Heatmap renders with color intensity

- GIVEN `PostingTimeSlot[]` contains entries for some `day-hour` pairs
- When the section renders
- THEN every cell uses `color-mix(in srgb, var(--heatmap-high) score%, var(--heatmap-low))` with `score` clamped to `[5, 100]` via `Math.max(0.05, score / 100)`
- AND cells missing from the input render with the lowest intensity (treated as score `0`)
- AND each cell exposes the localized day, hour, and score as a `title` attribute

#### Scenario: Heatmap legend

- GIVEN the heatmap is rendered
- THEN a legend appears below the grid with five swatches at scores `20, 40, 60, 80, 100`
- AND the legend is bracketed by i18n keys `dashboard.postingTimes.low` and `dashboard.postingTimes.high`

### Requirement: Best Time Recommendation

`BestPostingTimes` SHALL render the `HeatmapGrid` followed by a single recommendation panel.

#### Scenario: Recommendation panel renders

- GIVEN the slots array is provided
- WHEN the section renders
- THEN the recommendation panel displays the static header `AI Recommendation` and reuses `dashboard.postingTimes.subtitle` as the body text
- AND no best day/best hour recommendation logic is computed in v1

### Requirement: Upcoming Schedule List

`UpcomingSchedule` SHALL display at most the next five scheduled posts, sorted in the order received from `useAnalyticsStore`.

#### Scenario: Schedule list renders up to five items

- GIVEN the parent passes five or more schedule items
- WHEN the section renders
- THEN only the first five are visible (`.slice(0, 5)`)
- AND each item shows formatted `scheduledFor` time (`HH:mm` 24-hour), the title, a colored platform badge, and a status text label
- AND statuses `queued`, `scheduled`, `published` map to the i18n keys `dashboard.upcomingSchedule.{queued|scheduled|published}` with distinct text colors

#### Scenario: No upcoming posts

- GIVEN the parent passes an empty array
- WHEN the section renders
- THEN a centered placeholder message appears using i18n key `dashboard.upcomingSchedule.noItems`

### Requirement: Composer CTA

`UpcomingSchedule` SHALL render a primary button labeled with the i18n key `scheduler.newPost`. The button is visual only in v1; navigation behaviour is owned by the scheduler feature.

#### Scenario: Composer CTA is visible

- GIVEN the section renders with or without items
- WHEN the section renders
- THEN the composer CTA is visible at the bottom of the card with `variant="outline"` and a small uppercase label

### Requirement: Mock Data Source

`useAnalyticsStore().postingTimeSlots` SHALL expose seeding posts sourced from `mock-data/scheduling.ts`. There is no backend endpoint in v1.