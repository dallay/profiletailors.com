import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'
import { useQueuedCounts } from './useQueuedCounts'

function makePub(overrides: Partial<Publication> = {}): Publication {
  return {
    id: '107d4268-df5a-53e4-88bb-d6714c1e22c7',
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
      makePub({ id: 'cd66b912-3c45-5953-aaa7-bcee5538805c', status: 'QUEUED' }),
      makePub({ id: '17f7bc7a-35ef-5072-b086-9cb0e9c3c248', status: 'QUEUED' }),
      makePub({ id: 'e04b2ba2-61d3-502b-ab96-de7b7a5631b8', status: 'QUEUED' }),
      makePub({ id: 'f0892b3d-0322-55bc-a23b-3de3c973f673', status: 'PUBLISHED' }),
      makePub({ id: '9e5c408d-1b77-5155-9306-7ce7a2578410', status: 'PUBLISHED' }),
    ])

    const { total } = useQueuedCounts(publications)
    expect(total.value).toBe(3)
  })

  it('counts by provider', () => {
    const publications = ref<readonly Publication[]>([
      makePub({
        id: 'cd66b912-3c45-5953-aaa7-bcee5538805c',
        status: 'QUEUED',
        channels: ['linkedin'],
      }),
      makePub({
        id: '17f7bc7a-35ef-5072-b086-9cb0e9c3c248',
        status: 'QUEUED',
        channels: ['linkedin'],
      }),
      makePub({
        id: 'e04b2ba2-61d3-502b-ab96-de7b7a5631b8',
        status: 'QUEUED',
        channels: ['linkedin', 'twitter'],
      }),
    ])

    const { byProvider } = useQueuedCounts(publications)
    expect(byProvider.value.get('linkedin')).toBe(3)
    expect(byProvider.value.get('twitter')).toBe(1)
  })

  it('ignores non-QUEUED publications', () => {
    const publications = ref<readonly Publication[]>([
      makePub({
        id: 'cd66b912-3c45-5953-aaa7-bcee5538805c',
        status: 'PUBLISHED',
        channels: ['linkedin'],
      }),
      makePub({
        id: '17f7bc7a-35ef-5072-b086-9cb0e9c3c248',
        status: 'DRAFT',
        channels: ['twitter'],
      }),
      makePub({
        id: 'e04b2ba2-61d3-502b-ab96-de7b7a5631b8',
        status: 'SCHEDULED',
        channels: ['linkedin'],
      }),
    ])

    const { total, byProvider } = useQueuedCounts(publications)
    expect(total.value).toBe(0)
    expect(byProvider.value.get('linkedin')).toBeUndefined()
    expect(byProvider.value.get('twitter')).toBeUndefined()
  })

  it('reacts to changes in the publications ref', () => {
    const publications = ref<readonly Publication[]>([
      makePub({ id: 'cd66b912-3c45-5953-aaa7-bcee5538805c', status: 'QUEUED' }),
    ])

    const { total } = useQueuedCounts(publications)
    expect(total.value).toBe(1)

    publications.value = [
      ...publications.value,
      makePub({ id: '17f7bc7a-35ef-5072-b086-9cb0e9c3c248', status: 'QUEUED' }),
      makePub({ id: 'e04b2ba2-61d3-502b-ab96-de7b7a5631b8', status: 'QUEUED' }),
    ]
    expect(total.value).toBe(3)
  })
})
