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

    const publicationChip = wrapper.get('button.provider-color')
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

    const publicationChip = wrapper.get('button.provider-color')
    await publicationChip.trigger('click')

    expect(wrapper.emitted('click-day')).toBeUndefined()
  })

  it('emits click-publication when Enter key is pressed on a publication chip', async () => {
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications: [makePublication({ id: 'pub-enter' })],
      },
    })

    const publicationChip = wrapper.get('button.provider-color')
    await publicationChip.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('click-publication')).toHaveLength(1)
    expect(wrapper.emitted('click-publication')?.[0]?.[0]).toMatchObject({ id: 'pub-enter' })
    expect(wrapper.emitted('click-day')).toBeUndefined()
  })

  it('emits click-publication when Space key is pressed on a publication chip', async () => {
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications: [makePublication({ id: 'pub-space' })],
      },
    })

    const publicationChip = wrapper.get('button.provider-color')
    await publicationChip.trigger('keydown', { key: ' ' })

    expect(wrapper.emitted('click-publication')).toHaveLength(1)
    expect(wrapper.emitted('click-publication')?.[0]?.[0]).toMatchObject({ id: 'pub-space' })
    expect(wrapper.emitted('click-day')).toBeUndefined()
  })

  it('emits click-publication with the correct publication when multiple chips are rendered', async () => {
    const pub1 = makePublication({ id: 'pub-first', title: 'First' })
    const pub2 = makePublication({ id: 'pub-second', title: 'Second' })

    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications: [pub1, pub2],
      },
    })

    const chips = wrapper.findAll('button.provider-color')
    expect(chips).toHaveLength(2)

    await chips[1]!.trigger('click')

    expect(wrapper.emitted('click-publication')).toHaveLength(1)
    expect(wrapper.emitted('click-publication')?.[0]?.[0]).toMatchObject({ id: 'pub-second' })
  })

  it('renders the publication title in the chip text', async () => {
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications: [makePublication({ title: 'My scheduled post' })],
      },
    })

    const publicationChip = wrapper.get('button.provider-color')
    expect(publicationChip.text()).toContain('My scheduled post')
  })

  it('falls back to content substring when publication has no title', async () => {
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications: [
          makePublication({ title: undefined, content: 'Content without title here' }),
        ],
      },
    })

    const publicationChip = wrapper.get('button.provider-color')
    // title is falsy → renders pub.content.substring(0, 20)
    expect(publicationChip.text()).toContain('Content without titl')
  })

  it('truncates overflow publications and shows +N more indicator', () => {
    const publications = Array.from({ length: 5 }, (_, i) =>
      makePublication({ id: `pub-${i}`, title: `Post ${i}` }),
    )

    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications,
        maxVisible: 3,
      },
    })

    const chips = wrapper.findAll('button.provider-color')
    expect(chips).toHaveLength(3)
    expect(wrapper.text()).toContain('+2 more')
  })

  it('renders the conflict badge when publication has a conflict', () => {
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2026-06-15T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: false,
        publications: [makePublication({ hasConflict: true })],
      },
    })

    expect(wrapper.find('[data-testid="conflict-badge"]').exists()).toBe(true)
  })

  it('does not emit click-day on cell click when cell is past', async () => {
    const wrapper = mount(CalendarCell, {
      props: {
        date: new Date('2020-01-01T00:00:00.000Z'),
        isCurrentMonth: true,
        isToday: false,
        isPast: true,
        publications: [],
      },
    })

    await wrapper.trigger('click')

    expect(wrapper.emitted('click-day')).toBeUndefined()
  })
})
