import type { GrowthScore } from '@modules/dashboard/domain/dashboard.types'

// ---------------------------------------------------------------------------
// Growth Score — Overall + breakdown
// ---------------------------------------------------------------------------

export const growthScore: GrowthScore = {
  overall: 74,
  breakdown: {
    consistency: 82,
    engagement: 71,
    growth: 68,
    reach: 75,
  },
  topOpportunity: 'Increase posting frequency on LinkedIn Tuesdays',
  trend: 'improving',
}
