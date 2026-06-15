import type { PipelineColumn } from '../types/dashboard'

// ---------------------------------------------------------------------------
// Content Pipeline — Kanban columns
// ---------------------------------------------------------------------------

export const pipelineColumns: PipelineColumn[] = [
  {
    id: 'ideas',
    title: 'dashboard.pipeline.column.ideas',
    cards: [
      {
        id: 'pc-1',
        title: 'Thread on hexagonal architecture',
        content: 'A 5-tweet thread explaining ports and adapters with real-world analogies.',
        platform: 'twitter',
        author: 'Yuniel',
        tags: ['architecture', 'thread'],
      },
      {
        id: 'pc-2',
        title: 'Case study: migrating to WebFlux',
        content:
          'Document the journey from Spring MVC to WebFlux — lessons learned and performance wins.',
        platform: 'linkedin',
        author: 'Yuniel',
        tags: ['case-study', 'spring'],
      },
    ],
  },
  {
    id: 'drafting',
    title: 'dashboard.pipeline.column.drafting',
    cards: [
      {
        id: 'pc-3',
        title: 'GraphQL vs REST in 2026',
        content:
          'Why REST is still the right default for most APIs, and when GraphQL actually makes sense.',
        platform: 'linkedin',
        author: 'Yuniel',
        scheduledFor: '2026-06-15T10:00:00Z',
        tags: ['api', 'opinion'],
      },
    ],
  },
  {
    id: 'review',
    title: 'dashboard.pipeline.column.review',
    cards: [
      {
        id: 'pc-4',
        title: 'Kotlin coroutines cheat sheet',
        content:
          'Visual guide to structured concurrency, flows, and channels for backend developers.',
        platform: 'linkedin',
        author: 'Yuniel',
        tags: ['kotlin', 'reference'],
      },
    ],
  },
  {
    id: 'scheduled',
    title: 'dashboard.pipeline.column.scheduled',
    cards: [
      {
        id: 'pc-5',
        title: 'Why testing matters more than coverage',
        content: 'A focused post on meaningful tests vs. chasing 100% coverage metrics.',
        platform: 'twitter',
        author: 'Yuniel',
        scheduledFor: '2026-06-14T14:00:00Z',
        tags: ['testing', 'opinion'],
      },
      {
        id: 'pc-6',
        title: 'Docker multi-stage builds explained',
        content:
          'Step-by-step guide to building lean production images with multi-stage Dockerfiles.',
        platform: 'bluesky',
        author: 'Yuniel',
        scheduledFor: '2026-06-16T09:00:00Z',
        tags: ['docker', 'devops'],
      },
    ],
  },
]
