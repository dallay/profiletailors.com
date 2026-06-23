import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import CalendarCell from './CalendarCell.vue'
import type { Publication } from '@/stores/publishing'

vi.mock('@lucide/vue', () => ({
  Plus: { template: '<svg />' },
}))

vi.mock('@/components/ConflictBadge.vue', () => ({
  default: { template: '<div data-testid="conflict-badge" />' },
}))

vi.mock('@/lib/provider-styles', () => ({
  getProviderColor: () => 'provider-color',
  getProviderBadge: () => 'LI',
}))

function makePublication(overrides: Partial<Publication> = {}): Publication {
  return {
    id: 'pub-1',
    content: 'Month view publication content',
    title: 'Month view publication',
    channels: ['linkedin'],
    scheduledAt: '2026-06-15T10:00:00.000Z',
    status: 'QUEUED',
    priority: false,
    ...overrides,
  }
}

describe('CalendarCell', () => {
  it('emits click-publication when a publication chip is clicked', async () => {
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications: [makePublication()],
      },
    })

    const publicationChip = wrapper.get('[role="button"][tabindex="0"].provider-color')
    await publicationChip.trigger('click')

    expect(wrapper.emitted('click-publication')).toHaveLength(1)
    expect(wrapper.emitted('click-publication')?.[0]?.[0]).toMatchObject({ id: 'pub-1' })
  })

  it('does not emit click-day when a publication chip is clicked', async () => {
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications: [makePublication()],
      },
    })

    const publicationChip = wrapper.get('[role="button"][tabindex="0"].provider-color')
    await publicationChip.trigger('click')

    expect(wrapper.emitted('click-day')).toBeUndefined()
  })
})
