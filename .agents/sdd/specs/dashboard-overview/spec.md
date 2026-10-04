# Executive Overview Specification

## Purpose

Delivers at-a-glance KPI cards with sparkline trends, period-over-period deltas, and comparison labels — the user's first signal of account health when opening the dashboard.

## Data Model

The overview consumes a fixed-period 30-day snapshot exposed by the analytics store. There is no user-facing period selector; the contract assumes a 30-day window for v1.

```ts
type Trend = 'up' | 'down' | 'flat'

interface KpiMetric {
  id: string
  label: string          // i18n key
  value: string          // formatted display value
  delta: number          // period-over-period change %
  deltaLabel: string     // i18n key for "vs last 30 days"
  sparklineData: number[] // 7-point trend
  trend: Trend
}

interface OverviewData {
  cards: KpiMetric[]
  period: '30d'
}
```

## Requirements

### Requirement: KPI Card Display

The system SHALL render a row of KPI cards (`data-kpi-card`) sourced from `useAnalyticsStore().kpiMetrics`. Each card SHALL display a label, formatted value, delta with direction indicator, and a sparkline. The first card is rendered as featured (`lg:col-span-2`).

#### Scenario: KPI card renders with positive trend

- GIVEN a KPI metric has `trend: 'up'` and `delta: 12.5`
- WHEN the card renders
- THEN the value displays inside `data-kpi-value` with a `text-2xl` (or `text-5xl` when featured) font weight
- AND an upward arrow with `+12.5%` (formatted via `formatDelta`) shows in `text-[var(--success-color)]`
- AND the sparkline renders an upward-trending SVG path in success color

#### Scenario: KPI card renders with negative trend

- GIVEN a KPI metric has `trend: 'down'`
- WHEN the card renders
- THEN a downward arrow with the delta shows in `text-[var(--error-color)]`
- AND the sparkline renders a downward-trending SVG path in error color

#### Scenario: KPI card renders neutral

- GIVEN a KPI metric has `trend: 'flat'`
- WHEN the card renders
- THEN a right-pointing arrow with the delta shows in `text-[var(--text-secondary)]`
- AND the sparkline renders using the default `var(--sparkline-stroke)` color

### Requirement: Header

The section SHALL render a header with the section title in i18n key `dashboard.executiveOverview.title` and a static label `dashboard.executiveOverview.last30Days` indicating the fixed 30-day window.

#### Scenario: Header renders

- GIVEN the dashboard view mounts
- WHEN the section renders
- THEN the header shows the localized title and a 30-day label
- AND the header uses `font-[var(--font-space-mono)]` uppercase tracking

### Requirement: Sparkline Rendering

Sparklines SHALL render as inline SVG paths inside `SparklineChart.vue` without any chart library dependency. The sparkline computes its own min/max range from the input data, so empty or single-point arrays render as an empty path.

#### Scenario: Sparkline renders correctly

- GIVEN a KPI metric has 7 data points in `sparklineData`
- WHEN the sparkline renders
- THEN it draws an SVG path scaled to the default 80×32 viewBox
- AND the stroke width is 1.5 with rounded line caps and joins
- AND the area underneath the line is filled with `var(--sparkline-fill)`
- AND the stroke color depends on the trend direction (success, error, or default)

#### Scenario: Empty or single-point sparkline

- GIVEN a KPI metric has fewer than 2 data points
- WHEN the sparkline renders
- THEN no path is drawn
- AND the component remains in the DOM at its declared size

### Requirement: Mock Data Source

The analytics store SHALL expose KPI metrics from the local `mock-data/analytics.ts` fixture. There is no backend endpoint in v1. The store surface is `useAnalyticsStore().kpiMetrics` (reactive array).

#### Scenario: Store seeds metrics from fixtures

- GIVEN the analytics store is initialised
- WHEN consumers read `kpiMetrics`
- THEN the array contains a defensive copy of the mock fixture values
- AND mutating the array does not mutate the underlying fixture
