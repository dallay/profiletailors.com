# Growth Score Specification

## Purpose

Delivers a single composite metric (0–100) that summarizes overall social media growth health, with a breakdown of contributing factors and a top-opportunity copy block.

## Data Model

```ts
type GrowthTrend = 'improving' | 'declining' | 'stable'

interface GrowthScore {
  overall: number                                       // 0–100
  breakdown: {
    consistency: number
    engagement: number
    growth: number
    reach: number
  }
  topOpportunity: string                                // free-form copy
  trend: GrowthTrend
}
```

## Requirements

### Requirement: Score Display

`GrowthScore.vue` SHALL render a circular gauge (`ScoreGauge.vue`) sized 140×140 with stroke width 10, centered horizontally, and an "out of 100" label below the numeric value.

#### Scenario: Gauge renders

- GIVEN `score.overall` is any number
- WHEN the section renders
- THEN the gauge shows the score at `text-2xl` over a colored ring driven by the same thresholds as `ScoreGauge` (`>=80` success, `>=50` warning, otherwise error)
- AND the text "out of 100" appears below the numeric value via i18n key `dashboard.growthScore.outOf100`

#### Scenario: Trend indicator

- GIVEN `score.trend === 'improving'`
- WHEN the section header renders
- THEN a badge in success color shows the i18n key `dashboard.growthScore.improving` with an upward arrow (`↑`)
- AND the badge uses `variant="outline"` for the improving case

#### Scenario: Stable or declining trend

- GIVEN `score.trend === 'declining'`
- WHEN the section header renders
- THEN the badge uses `var(--error-color)` with a downward arrow

### Requirement: Factor Breakdown

The breakdown section SHALL render each entry of `score.breakdown` as a labelled progress bar.

#### Scenario: Breakdown bars render

- GIVEN `score.breakdown` contains `consistency`, `engagement`, `growth`, `reach`
- WHEN the section renders
- THEN four bars appear in the insertion order of the record
- AND each bar's width is `value%`
- AND each bar's color is `success` (≥80), `warning` (≥50), or `error` (<50)
- AND each label is `dashboard.growthScore.breakdown.{key}`

### Requirement: Top Opportunity Copy Block

The component SHALL render a single copy block labelled `dashboard.growthScore.topOpportunity` that displays the `score.topOpportunity` string verbatim. There is no clickable CTA in v1.

#### Scenario: Opportunity copy block renders

- GIVEN `score.topOpportunity` is a non-empty string
- WHEN the section renders
- THEN the copy block appears below the breakdown bars with the static label and the opportunity string
- AND if `topOpportunity` is null or empty the block is omitted

### Requirement: Mock Data Source

`useAnalyticsStore().growthScore` SHALL expose the score sourced from `mock-data/growth-score.ts`. There is no backend endpoint in v1.