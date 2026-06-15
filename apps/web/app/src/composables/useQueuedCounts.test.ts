import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import type { Publication } from '@/stores/publishing'
import { useQueuedCounts } from './useQueuedCounts'

function makePub(overrides: Partial<Publication> = {}): Publication {
  return {
    id: 'pub-x',
    content: '',
    channels: ['linkedin'],
    scheduledAt: '2026-01-01T00:00:00Z',
    status: 'QUEUED',
    priority: false,
    ...overrides,
  }
}

describe('useQueuedCounts', () => {
  it('counts total QUEUED publications', () => {
    const publications = ref<readonly Publication[]>([
      makePub({ id: 'p1', status: 'QUEUED' }),
      makePub({ id: 'p2', status: 'QUEUED' }),
      makePub({ id: 'p3', status: 'QUEUED' }),
      makePub({ id: 'p4', status: 'PUBLISHED' }),
      makePub({ id: 'p5', status: 'PUBLISHED' }),
    ])

    const { total } = useQueuedCounts(publications)
    expect(total.value).toBe(3)
  })

  it('counts by provider', () => {
    const publications = ref<readonly Publication[]>([
      makePub({ id: 'p1', status: 'QUEUED', channels: ['linkedin'] }),
      makePub({ id: 'p2', status: 'QUEUED', channels: ['linkedin'] }),
      makePub({ id: 'p3', status: 'QUEUED', channels: ['linkedin', 'twitter'] }),
    ])

    const { byProvider } = useQueuedCounts(publications)
    expect(byProvider.value.get('linkedin')).toBe(3)
    expect(byProvider.value.get('twitter')).toBe(1)
  })

  it('ignores non-QUEUED publications', () => {
    const publications = ref<readonly Publication[]>([
      makePub({ id: 'p1', status: 'PUBLISHED', channels: ['linkedin'] }),
      makePub({ id: 'p2', status: 'DRAFT', channels: ['twitter'] }),
      makePub({ id: 'p3', status: 'SCHEDULED', channels: ['linkedin'] }),
    ])

    const { total, byProvider } = useQueuedCounts(publications)
    expect(total.value).toBe(0)
    expect(byProvider.value.get('linkedin')).toBeUndefined()
    expect(byProvider.value.get('twitter')).toBeUndefined()
  })

  it('reacts to changes in the publications ref', () => {
    const publications = ref<readonly Publication[]>([makePub({ id: 'p1', status: 'QUEUED' })])

    const { total } = useQueuedCounts(publications)
    expect(total.value).toBe(1)

    publications.value = [
      ...publications.value,
      makePub({ id: 'p2', status: 'QUEUED' }),
      makePub({ id: 'p3', status: 'QUEUED' }),
    ]
    expect(total.value).toBe(3)
  })
})
