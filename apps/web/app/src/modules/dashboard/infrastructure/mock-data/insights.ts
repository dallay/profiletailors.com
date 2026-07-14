import type { AiInsight } from '@modules/dashboard/domain/dashboard.types'

// ---------------------------------------------------------------------------
// AI Insights — Recommendations, alerts, opportunities
// ---------------------------------------------------------------------------

export const aiInsights: AiInsight[] = [
  {
    id: 'insight-1',
    type: 'recommendation',
    title: 'Post more on LinkedIn Tuesdays',
    description:
      'Your LinkedIn engagement peaks on Tuesdays between 9-11 AM. Publishing 2 more posts per week in this window could increase reach by an estimated 15%.',
    actionLabel: 'View schedule',
    actionTarget: '/scheduler',
    priority: 'high',
    createdAt: '2026-06-13T08:00:00Z',
    dismissed: false,
  },
  {
    id: 'insight-2',
    type: 'alert',
    title: 'Twitter engagement dropped 12%',
    description:
      'Your Twitter engagement rate fell from 3.6% to 3.2% over the past two weeks. Consider revisiting your thread strategy.',
    actionLabel: 'View analytics',
    actionTarget: '/analytics',
    priority: 'high',
    createdAt: '2026-06-12T14:30:00Z',
    dismissed: false,
  },
  {
    id: 'insight-3',
    type: 'opportunity',
    title: 'Bluesky is growing fast',
    description:
      'Your Bluesky followers grew 18.5% this month. Cross-posting your top LinkedIn content here could accelerate growth further.',
    actionLabel: 'Create post',
    actionTarget: '/composer',
    priority: 'medium',
    createdAt: '2026-06-11T10:00:00Z',
    dismissed: false,
  },
  {
    id: 'insight-4',
    type: 'recommendation',
    title: 'Add hashtags to Threads posts',
    description:
      'Posts with 3-5 hashtags on Threads receive 40% more impressions. Your recent Threads posts have been hashtag-free.',
    actionLabel: 'View posts',
    priority: 'low',
    createdAt: '2026-06-10T16:00:00Z',
    dismissed: false,
  },
  {
    id: 'insight-5',
    type: 'opportunity',
    title: 'Carousel posts outperform static',
    description:
      'Your carousel posts on LinkedIn average 2.3x more engagement than static images. Consider converting your next text post into a carousel.',
    actionLabel: 'Create carousel',
    priority: 'medium',
    createdAt: '2026-06-09T09:00:00Z',
    dismissed: false,
  },
]
