// ---------------------------------------------------------------------------
// Dashboard Types — Social Media Operating System
// ---------------------------------------------------------------------------

export type Platform = 'linkedin' | 'twitter' | 'bluesky' | 'threads'
export type Trend = 'up' | 'down' | 'flat'
export type InsightType = 'recommendation' | 'alert' | 'opportunity'
export type InsightPriority = 'high' | 'medium' | 'low'

export type KpiMetric = {
  id: string
  label: string // i18n key
  value: string // formatted display value
  delta: number // period-over-period change %
  deltaLabel: string // "vs last 30 days"
  sparklineData: number[] // 7-point trend
  trend: Trend
}

export type AiInsight = {
  id: string
  type: InsightType
  title: string
  description: string
  actionLabel: string
  actionTarget?: string
  priority: InsightPriority
  createdAt: string
  dismissed: boolean
}

export type GrowthScore = {
  overall: number
  breakdown: {
    consistency: number
    engagement: number
    growth: number
    reach: number
  }
  topOpportunity: string
  trend: 'improving' | 'declining' | 'stable'
}

export type TopPost = {
  id: string
  content: string
  platform: Platform
  publishedAt: string
  impressions: number
  engagementRate: number
  reactions: number
  comments: number
  shares: number
}

export type ChannelPerformance = {
  platform: Platform
  followers: number
  growth: number
  engagementRate: number
  postsCount: number
  color: string
}

export type AudienceGrowthPoint = {
  date: string
  followers: number
  milestone?: string
}

export type ScheduleItem = {
  id: string
  title: string
  platform: Platform
  scheduledFor: string
  status: 'queued' | 'scheduled' | 'published'
}

export type PipelineCard = {
  id: string
  title: string
  content: string
  platform: Platform
  scheduledFor?: string
  author: string
  thumbnail?: string
  tags: string[]
}

export type PipelineColumn = {
  id: string
  title: string
  cards: PipelineCard[]
}

export type PostingTimeSlot = {
  day: string
  hour: number
  score: number
}

export type InboxItem = {
  id: string
  type: 'comment' | 'mention' | 'message' | 'lead'
  platform: Platform
  content: string
  from: string
  createdAt: string
  priority: 'high' | 'normal'
}

export type TeamMember = {
  id: string
  name: string
  avatar?: string
  online: boolean
}

export type TeamActivityEvent = {
  id: string
  memberId: string
  memberName: string
  action: string
  timestamp: string
}
