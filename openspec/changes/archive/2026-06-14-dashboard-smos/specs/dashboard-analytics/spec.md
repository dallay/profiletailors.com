# Dashboard Analytics Specification

## Purpose

Combines two analytics views: Content Performance (top posts ranked by engagement) and Cross-Channel
Analytics (platform comparison bars and audience growth). This gives the user both post-level and
channel-level performance data in one section.

## Data Model

```ts
interface TopPost {
  id: string
  content: string
  platform: 'linkedin' | 'twitter' | 'instagram' | 'facebook'
  publishedAt: string      // ISO 8601
  metrics: {
    impressions: number
    engagement: number     // absolute count
    engagementRate: number // percentage
    clicks: number
    shares: number
  }
  thumbnailUrl?: string
}

interface PlatformMetric {
  platform: 'linkedin' | 'twitter' | 'instagram' | 'facebook'
  label: string
  followers: number
  engagementRate: number
  impressions: number
  growth: number           // delta from previous period
  color: string            // platform brand color token
}

interface AudienceGrowthPoint {
  date: string             // ISO date
  total: number
  breakdown: Record<string, number> // per-platform
}

interface AnalyticsData {
  topPosts: TopPost[]
  platforms: PlatformMetric[]
  audienceGrowth: AudienceGrowthPoint[]
  period: '7d' | '30d' | '90d'
}
```

## Requirements

### Requirement: Top Posts List

The system SHALL render top-performing posts ranked by engagement rate, with platform filter.

#### Scenario: Posts render with metrics

- GIVEN 5 top posts exist for the selected period
- WHEN the section renders
- THEN posts display in ranked order with content preview, platform badge, and key metrics
- AND each post shows impressions, engagement rate, and shares
- AND the top post has a "Top" indicator badge

#### Scenario: Platform filter narrows results

- GIVEN the user selects "LinkedIn" from the platform filter
- WHEN the filter applies
- THEN only LinkedIn posts appear in the list
- AND the ranking recalculates within the filtered set
- AND a "Clear filter" option appears

#### Scenario: Empty posts state

- GIVEN no posts exist for the selected period
- WHEN the section renders
- THEN a message displays: "No published posts in this period"
- AND a CTA links to the content pipeline

### Requirement: Cross-Channel Bars

The system SHALL render platform comparison as horizontal bar charts showing relative performance.

#### Scenario: Bars render for each platform

- GIVEN 3 platforms have data (LinkedIn, Twitter, Instagram)
- WHEN the section renders
- THEN each platform renders a horizontal bar proportional to its engagement rate
- AND bars use the platform's brand color
- AND follower count and growth delta appear next to each bar

#### Scenario: Single platform

- GIVEN only LinkedIn is connected
- WHEN the section renders
- THEN a single bar renders with full context
- AND a "Connect more platforms" prompt appears below

### Requirement: Audience Growth Mini-Chart

The section SHALL include a compact line chart showing audience growth over the selected period.

#### Scenario: Growth chart renders

- GIVEN audience growth data has 30 data points
- WHEN the section renders
- THEN a line chart displays total audience over time
- AND the chart uses the accent color for the line
- AND data points render as small circles on hover

### Requirement: Period Sync

Analytics sections SHALL respect the global dashboard period selector.

#### Scenario: Period change updates analytics

- GIVEN the user changes the period to "90d" in the Executive Overview
- WHEN the analytics section receives the new period
- THEN top posts, platform bars, and growth chart all update for 90 days
- AND data fetching shows skeleton states during transition
