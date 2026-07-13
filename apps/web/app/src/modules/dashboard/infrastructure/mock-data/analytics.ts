import type {
  KpiMetric,
  ChannelPerformance,
  TopPost,
  AudienceGrowthPoint,
} from '@modules/dashboard/domain/dashboard.types'

// ---------------------------------------------------------------------------
// KPI Metrics — Executive Overview cards
// ---------------------------------------------------------------------------

export const kpiMetrics: KpiMetric[] = [
  {
    id: 'total-followers',
    label: 'dashboard.kpi.totalFollowers',
    value: '24.7K',
    delta: 8.3,
    deltaLabel: 'dashboard.kpi.vsLast30Days',
    sparklineData: [18200, 19100, 20400, 21800, 22900, 23600, 24700],
    trend: 'up',
  },
  {
    id: 'avg-engagement',
    label: 'dashboard.kpi.avgEngagement',
    value: '4.2%',
    delta: 1.1,
    deltaLabel: 'dashboard.kpi.vsLast30Days',
    sparklineData: [3.1, 3.4, 3.6, 3.9, 4, 4.1, 4.2],
    trend: 'up',
  },
  {
    id: 'total-impressions',
    label: 'dashboard.kpi.totalImpressions',
    value: '182K',
    delta: -2.4,
    deltaLabel: 'dashboard.kpi.vsLast30Days',
    sparklineData: [210000, 198000, 195000, 189000, 186000, 184000, 182000],
    trend: 'down',
  },
  {
    id: 'content-published',
    label: 'dashboard.kpi.contentPublished',
    value: '38',
    delta: 12,
    deltaLabel: 'dashboard.kpi.vsLast30Days',
    sparklineData: [28, 30, 32, 33, 35, 36, 38],
    trend: 'up',
  },
]

// ---------------------------------------------------------------------------
// Channel Performance — Cross-Channel Analytics
// ---------------------------------------------------------------------------

export const channelPerformance: ChannelPerformance[] = [
  {
    platform: 'linkedin',
    followers: 12400,
    growth: 11.2,
    engagementRate: 5.1,
    postsCount: 16,
    color: '#0A66C2',
  },
  {
    platform: 'twitter',
    followers: 8300,
    growth: 4.8,
    engagementRate: 3.2,
    postsCount: 14,
    color: '#1DA1F2',
  },
  {
    platform: 'bluesky',
    followers: 2800,
    growth: 18.5,
    engagementRate: 6.7,
    postsCount: 5,
    color: '#0085FF',
  },
  {
    platform: 'threads',
    followers: 1200,
    growth: 22.1,
    engagementRate: 2.9,
    postsCount: 3,
    color: '#E1306C',
  },
]

// ---------------------------------------------------------------------------
// Top Posts — Content Performance
// ---------------------------------------------------------------------------

export const topPosts: TopPost[] = [
  {
    id: 'tp-1',
    content:
      'The most common DDD mistake: creating anemic models and treating the database as the center of design. Focus on behavior first!',
    platform: 'linkedin',
    publishedAt: '2026-06-10T14:00:00Z',
    impressions: 14200,
    engagementRate: 7.2,
    reactions: 342,
    comments: 89,
    shares: 67,
  },
  {
    id: 'tp-2',
    content:
      'Why your architecture should follow Swiss design principles: visual minimalism, clear typography, and zero ornamental noise.',
    platform: 'twitter',
    publishedAt: '2026-06-08T10:30:00Z',
    impressions: 8900,
    engagementRate: 5.8,
    reactions: 201,
    comments: 45,
    shares: 38,
  },
  {
    id: 'tp-3',
    content:
      'CQRS is not just about read/write separation. It is about thinking in domain events and building systems that evolve.',
    platform: 'linkedin',
    publishedAt: '2026-06-06T16:15:00Z',
    impressions: 11300,
    engagementRate: 6.1,
    reactions: 278,
    comments: 62,
    shares: 51,
  },
  {
    id: 'tp-4',
    content:
      'Your API should be boring. Boring means predictable, consistent, and easy to debug. Excitement belongs in product features.',
    platform: 'bluesky',
    publishedAt: '2026-06-05T09:00:00Z',
    impressions: 3200,
    engagementRate: 8.4,
    reactions: 89,
    comments: 23,
    shares: 19,
  },
]

// ---------------------------------------------------------------------------
// Audience Growth — time series
// ---------------------------------------------------------------------------

export const audienceGrowthData: AudienceGrowthPoint[] = [
  { date: '2026-05-14', followers: 21200 },
  { date: '2026-05-17', followers: 21500 },
  { date: '2026-05-20', followers: 21800 },
  { date: '2026-05-23', followers: 22100, milestone: '22K followers' },
  { date: '2026-05-26', followers: 22400 },
  { date: '2026-05-29', followers: 22600 },
  { date: '2026-06-01', followers: 22900 },
  { date: '2026-06-04', followers: 23200 },
  { date: '2026-06-07', followers: 23600 },
  { date: '2026-06-10', followers: 24100, milestone: '24K followers' },
  { date: '2026-06-13', followers: 24700 },
]
