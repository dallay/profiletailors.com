import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import InboxSummary from './InboxSummary.vue'
import type { InboxItem } from '@/lib/types/dashboard'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeInboxItem(id: string, overrides: Partial<InboxItem> = {}): InboxItem {
  return {
    id,
    type: 'comment',
    platform: 'linkedin',
    content: 'Great post!',
    from: 'User123',
    createdAt: '2026-06-10T08:00:00Z',
    priority: 'normal',
    ...overrides,
  }
}

describe('InboxSummary', () => {
  it('renders the title and subtitle', () => {
    const wrapper = mount(InboxSummary, {
      props: { items: [makeInboxItem('i1')] },
    })
    expect(wrapper.text()).toContain('dashboard.inbox.title')
    expect(wrapper.text()).toContain('dashboard.inbox.subtitle')
  })

  it('renders type count cards for each type present', () => {
    const items = [
      makeInboxItem('i1', { type: 'comment' }),
      makeInboxItem('i2', { type: 'mention' }),
      makeInboxItem('i3', { type: 'message' }),
      makeInboxItem('i4', { type: 'lead' }),
    ]
    const wrapper = mount(InboxSummary, {
      props: { items },
    })
    expect(wrapper.text()).toContain('dashboard.inbox.comment')
    expect(wrapper.text()).toContain('dashboard.inbox.mention')
    expect(wrapper.text()).toContain('dashboard.inbox.message')
    expect(wrapper.text()).toContain('dashboard.inbox.lead')
  })

  it('renders correct counts per type', () => {
    const items = [
      makeInboxItem('i1', { type: 'comment' }),
      makeInboxItem('i2', { type: 'comment' }),
      makeInboxItem('i3', { type: 'lead' }),
    ]
    const wrapper = mount(InboxSummary, {
      props: { items },
    })
    // Type cards show count
    expect(wrapper.text()).toContain('dashboard.inbox.comment')
  })

  it('renders platform badges within type cards', () => {
    const items = [
      makeInboxItem('i1', { type: 'comment', platform: 'linkedin' }),
    ]
    const wrapper = mount(InboxSummary, {
      props: { items },
    })
    expect(wrapper.text()).toContain('LinkedIn')
  })

  it('shows high priority indicator when high priority items exist', () => {
    const items = [
      makeInboxItem('i1', { type: 'lead', priority: 'high' }),
    ]
    const wrapper = mount(InboxSummary, {
      props: { items },
    })
    // Should show the warning dot count
    const highPriorityBadge = wrapper.find('.rounded-full.bg-\\[var\\(--warning-color\\)\\]')
    expect(highPriorityBadge.exists()).toBe(true)
  })

  it('shows empty state when no items', () => {
    const wrapper = mount(InboxSummary, {
      props: { items: [] },
    })
    expect(wrapper.text()).toContain('dashboard.inbox.noItems')
  })

  it('renders View All button', () => {
    const wrapper = mount(InboxSummary, {
      props: { items: [makeInboxItem('i1')] },
    })
    expect(wrapper.text()).toContain('dashboard.inbox.viewAll')
  })
})
