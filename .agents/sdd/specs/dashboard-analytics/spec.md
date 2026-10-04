# Dashboard Analytics Specification

## Purpose

Combines three analytics views surfaced by the dashboard analytics store: a list of top-performing posts with a platform filter, platform-comparison bars driven by follower counts (with engagement-rate context), and an audience-growth line chart.

## Data Model

```ts
type Platform = 'linkedin' | 'twitter' | 'bluesky' | 'threads'

interface TopPost {
  id: string
  content: string
  platform: Platform
  publishedAt: string      // ISO 8601
  impressions: number
  engagementRate: number   // percentage 0–100
  reactions: number
  comments: number
  shares: number
}

interface ChannelPerformance {
  platform: Platform
  followers: number
  growth: number
  engagementRate: number
  postsCount: number
  color: string            // CSS color or token
}

interface AudienceGrowthPoint {
  date: string             // ISO date
  followers: number
  milestone?: string
}
```

## Requirements

### Requirement: Top Performing Posts List

The system SHALL render top-performing posts ranked by their position in the `useAnalyticsStore().topPosts` array, with a platform filter and a clear empty state.

#### Scenario: Posts render with metrics

- GIVEN the store exposes top posts for the period
- WHEN `TopPerformingPosts.vue` renders
- THEN each post appears as a row with a 1-based rank, content preview, platform badge, relative published-time, reactions, comments, shares, and engagement rate
- AND posts are displayed in the order received from the store
- AND the metric labels live under i18n keys `dashboard.contentPerformance.reactions|comments|shares|engagementRate`

#### Scenario: Platform filter narrows results

- GIVEN the user clicks a platform chip ("LinkedIn", "X", "Bluesky", "Threads" or "All platforms")
- WHEN the filter changes
- THEN only posts whose `platform` matches the active filter remain visible
- AND selecting "All platforms" restores the unfiltered list
- AND the active chip uses an inverted display style (`bg-[var(--text-display)] text-[var(--background-primary)]`)

#### Scenario: Empty filtered posts state

- GIVEN the active filter excludes every post
- WHEN the section renders
- THEN a centered message appears using i18n key `dashboard.contentPerformance.noPostsMatch`

### Requirement: Cross-Channel Bars

The system SHALL render platform comparison as horizontal bars whose width is proportional to the largest follower count in the visible set. The bars do not normalize behaviour.

#### Scenario: Bars render for each platform

- GIVEN three or more channels exist
- WHEN `CrossChannelAnalytics.vue` renders
- THEN each channel renders a horizontal bar with width `channel.followers / maxFollowers`
- AND the bar color comes from `channel.color`
- AND the row also shows the formatted follower count and engagement rate
- AND a `followerContext` line below each bar references the platform display name

#### Scenario: Single platform

- GIVEN only one channel is exposed by the store
- WHEN the section renders
- THEN a single bar appears with the full context (count + engagement rate + followerContext)

### Requirement: Audience Growth Mini-Chart

The system SHALL render audience growth as a line chart with 5 horizontal grid lines, x-axis date labels at the first / middle / last index, an area fill, milestone annotations, and a hover tooltip exposing the date and follower count.

#### Scenario: Growth chart renders

- GIVEN the store exposes at least two `AudienceGrowthPoint` entries
- WHEN `AudienceGrowthChart.vue` renders
- THEN the chart displays a line in `var(--chart-line)` over an area fill at opacity 0.15
- AND five y-axis tick labels show evenly between `yMin` and `yMax`
- AND x-axis labels appear at the first, middle, and last index using `date`
- AND any `milestone` annotation draws a dashed vertical line, a 6px success-color marker, and the label `dashboard.audienceGrowth.milestone`

#### Scenario: Tooltip on hover

- GIVEN the chart has data and the user hovers over a data point
- WHEN the hover fires
- THEN a tooltip appears showing the date and the formatted follower number
- AND the marker grows to radius 5 and swaps stroke/fill colors

### Requirement: Mock Data Source

The analytics store SHALL expose `topPosts`, `channelPerformance`, and `audienceGrowth` as defensive copies of the local `mock-data/analytics.ts` fixture. There is no backend endpoint in v1.

#### Scenario: Store seeds analytics from fixtures

- GIVEN the analytics store is initialised
- WHEN consumers read `topPosts`, `channelPerformance`, or `audienceGrowth`
- THEN each array contains a defensive copy of the mock fixture
- AND mutating the array does not mutate the underlying fixture

### Requirement: Follower Count as Bar Width

The bar width is driven by followers (not engagement rate). The engagement rate appears next to each bar as context.

#### Scenario: Largest follower count anchors the bars

- GIVEN a set of channels with different `followers` values
- WHEN the chart renders
- THEN the largest follower count produces a 100% wide bar
- AND every other channel's bar is proportional to its followers relative to that maximum