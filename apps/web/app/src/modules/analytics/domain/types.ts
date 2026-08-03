export type DateRange = {
  startDate: string
  endDate: string
}

export type DailyMetric = {
  date: string
  impressions: number
  engagements: number
  clicks: number
}

export type AnalyticsOverview = {
  period: DateRange
  totalImpressions: number
  totalEngagements: number
  engagementRate: number
  totalClicks: number
  newFollowers: number
  clickThroughRate: number
  dailyMetrics: DailyMetric[]
}

export type PostAnalyticsSummary = {
  postId: string
  title: string | null
  bodyText: string | null
  provider: string
  publishedAt: string
  impressions: number
  clicks: number
  engagements: number
  reactions: number
  comments: number
  shares: number
  engagementRate: number
}

export type PostAnalyticsList = {
  posts: PostAnalyticsSummary[]
  total: number
  page: number
  size: number
}

export type BestTimeSlot = {
  dayOfWeek: number
  hour: number
  score: number
}

export type BestTimesRecommendation = {
  slots: BestTimeSlot[]
}

export type DateRangePreset = 'last7' | 'last30' | 'last90' | 'custom'
