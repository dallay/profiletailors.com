# Executive Overview Specification

## Purpose

Delivers at-a-glance KPI cards with sparkline trends, period-over-period deltas, and comparison labels — the user's first signal of account health when opening the dashboard.

## Data Model

```ts
interface KpiCard {
  id: string
  label: string          // i18n key
  value: number | string // formatted display value
  format: 'number' | 'percent' | 'currency' | 'duration'
  delta: number          // period-over-period change (e.g. +12.5)
  deltaLabel: string     // "vs last 30 days"
  trend: number[]        // sparkline data points (last 14 periods)
  icon?: string          // optional icon identifier
  status: 'positive' | 'negative' | 'neutral'
}

interface OverviewData {
  cards: KpiCard[]
  period: '7d' | '30d' | '90d'
  lastUpdated: string    // ISO 8601
}
```

## Requirements

### Requirement: KPI Card Display

The system SHALL render a row of KPI cards, each showing label, formatted value, delta with direction indicator, and a sparkline.

#### Scenario: KPI card renders with positive trend

- GIVEN a KPI card has `delta: 12.5` and `status: 'positive'`
- WHEN the card renders
- THEN the value displays with the primary font at 5xl size
- AND a green delta badge shows "+12.5%"
- AND the sparkline renders an upward-trending SVG path

#### Scenario: KPI card renders with negative trend

- GIVEN a KPI card has `delta: -3.2` and `status: 'negative'`
- WHEN the card renders
- THEN a red delta badge shows "-3.2%"
- AND the sparkline renders a downward-trending SVG path

#### Scenario: KPI card renders neutral

- GIVEN a KPI card has `delta: 0` and `status: 'neutral'`
- WHEN the card renders
- THEN the delta badge shows "0%" in the secondary text color
- AND the sparkline renders a flat SVG path

### Requirement: Period Selector

The overview section SHALL include a period selector allowing the user to switch between 7-day, 30-day, and 90-day views.

#### Scenario: User changes period

- GIVEN the user clicks "30d" in the period selector
- WHEN the selection changes
- THEN all KPI cards update their values, deltas, and sparklines for the 30-day period
- AND the selector highlights "30d" as active

#### Scenario: Period persists across navigation

- GIVEN the user selects "90d" and navigates to Analytics
- WHEN the user returns to the dashboard
- THEN the period selector remains on "90d"

### Requirement: Sparkline Rendering

Sparklines SHALL render as inline SVG polylines within each card — no chart library dependency.

#### Scenario: Sparkline renders correctly

- GIVEN a KPI card has 14 data points in `trend`
- WHEN the sparkline renders
- THEN it draws an SVG polyline scaled to the card width
- AND the line uses the chart accent color from design tokens
- AND no axes, labels, or tooltips appear on the sparkline

### Requirement: Last Updated Timestamp

The overview SHALL display a "last updated" timestamp below the section header.

#### Scenario: Timestamp shows relative time

- GIVEN `lastUpdated` is "2026-06-13T10:30:00Z"
- WHEN the section renders
- THEN a label shows "Updated 5m ago" (relative to current time)
- AND the label uses `font-mono text-[10px] text-text-secondary`
