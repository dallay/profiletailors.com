import type { InboxItem, TeamMember, TeamActivityEvent } from '../types/dashboard'

// ---------------------------------------------------------------------------
// Inbox Items
// ---------------------------------------------------------------------------

export const inboxItems: InboxItem[] = [
  {
    id: 'inbox-1',
    type: 'comment',
    platform: 'linkedin',
    content: 'Great insights on DDD! I have been struggling with anemic models in my current project.',
    from: 'Sarah Chen',
    createdAt: '2026-06-13T10:30:00Z',
    priority: 'high',
  },
  {
    id: 'inbox-2',
    type: 'mention',
    platform: 'twitter',
    content: '@profiletailors This thread on CQRS was exactly what I needed. Bookmarked!',
    from: 'dev_marcus',
    createdAt: '2026-06-13T09:15:00Z',
    priority: 'normal',
  },
  {
    id: 'inbox-3',
    type: 'lead',
    platform: 'linkedin',
    content:
      'Interested in your consulting services for architecture review. Can we schedule a call?',
    from: 'James Rodriguez',
    createdAt: '2026-06-12T16:00:00Z',
    priority: 'high',
  },
  {
    id: 'inbox-4',
    type: 'message',
    platform: 'bluesky',
    content: 'Hey, do you have a recommended reading list for backend architecture?',
    from: 'kai_dev',
    createdAt: '2026-06-12T11:45:00Z',
    priority: 'normal',
  },
  {
    id: 'inbox-5',
    type: 'comment',
    platform: 'threads',
    content: 'The Swiss design analogy really clicked. Minimalism in code IS a feature.',
    from: 'design_with_anna',
    createdAt: '2026-06-11T14:20:00Z',
    priority: 'normal',
  },
]

// ---------------------------------------------------------------------------
// Team Members
// ---------------------------------------------------------------------------

export const teamMembers: TeamMember[] = [
  { id: 'tm-1', name: 'Yuniel', online: true },
  { id: 'tm-2', name: 'Ana', avatar: undefined, online: true },
  { id: 'tm-3', name: 'Carlos', online: false },
]

// ---------------------------------------------------------------------------
// Team Activity
// ---------------------------------------------------------------------------

export const teamActivity: TeamActivityEvent[] = [
  {
    id: 'ta-1',
    memberId: 'tm-1',
    memberName: 'Yuniel',
    action: 'Scheduled "Kotlin coroutines cheat sheet" for LinkedIn',
    timestamp: '2026-06-13T08:45:00Z',
  },
  {
    id: 'ta-2',
    memberId: 'tm-2',
    memberName: 'Ana',
    action: 'Approved "GraphQL vs REST" draft for review',
    timestamp: '2026-06-13T08:20:00Z',
  },
  {
    id: 'ta-3',
    memberId: 'tm-1',
    memberName: 'Yuniel',
    action: 'Published "DDD Mistakes" thread on Twitter',
    timestamp: '2026-06-12T14:00:00Z',
  },
  {
    id: 'ta-4',
    memberId: 'tm-3',
    memberName: 'Carlos',
    action: 'Added 3 content ideas to the pipeline',
    timestamp: '2026-06-12T10:30:00Z',
  },
  {
    id: 'ta-5',
    memberId: 'tm-2',
    memberName: 'Ana',
    action: 'Updated audience growth report for May',
    timestamp: '2026-06-11T16:00:00Z',
  },
]
