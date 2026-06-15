import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TopPerformingPosts from './TopPerformingPosts.vue'
import type { TopPost } from '@/lib/types/dashboard'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makePost(id: string, overrides: Partial<TopPost> = {}): TopPost {
  return {
    id,
    content: 'Test post content',
    platform: 'linkedin',
    publishedAt: '2026-06-10T08:00:00Z',
    impressions: 5000,
    engagementRate: 7.2,
    reactions: 342,
    comments: 89,
    shares: 67,
    ...overrides,
  }
}

describe('TopPerformingPosts', () => {
  it('renders the title and subtitle', () => {
    const wrapper = mount(TopPerformingPosts, {
      props: { posts: [makePost('p1')] },
    })
    expect(wrapper.text()).toContain('dashboard.contentPerformance.title')
    expect(wrapper.text()).toContain('dashboard.contentPerformance.subtitle')
  })

  it('renders all posts when filter is "all"', () => {
    const posts = [
      makePost('p1', { platform: 'linkedin', content: 'Post 1' }),
      makePost('p2', { platform: 'twitter', content: 'Post 2' }),
      makePost('p3', { platform: 'bluesky', content: 'Post 3' }),
    ]
    const wrapper = mount(TopPerformingPosts, {
      props: { posts },
    })
    expect(wrapper.text()).toContain('Post 1')
    expect(wrapper.text()).toContain('Post 2')
    expect(wrapper.text()).toContain('Post 3')
  })

  it('renders platform filter buttons', () => {
    const wrapper = mount(TopPerformingPosts, {
      props: { posts: [makePost('p1')] },
    })
    expect(wrapper.text()).toContain('dashboard.contentPerformance.allPlatforms')
    expect(wrapper.text()).toContain('LinkedIn')
    expect(wrapper.text()).toContain('X')
    expect(wrapper.text()).toContain('Bluesky')
    expect(wrapper.text()).toContain('Threads')
  })

  it('filters posts when a platform tab is clicked', async () => {
    const posts = [
      makePost('p1', { platform: 'linkedin', content: 'LinkedIn Post' }),
      makePost('p2', { platform: 'twitter', content: 'Twitter Post' }),
    ]
    const wrapper = mount(TopPerformingPosts, {
      props: { posts },
    })
    // Click "X" filter button
    const xButton = wrapper.findAll('button').filter((b) => b.text() === 'X')[0]!
    await xButton.trigger('click')
    expect(wrapper.text()).toContain('Twitter Post')
    expect(wrapper.text()).not.toContain('LinkedIn Post')
  })

  it('shows empty state when no posts match the filter', async () => {
    const posts = [makePost('p1', { platform: 'linkedin', content: 'LinkedIn Post' })]
    const wrapper = mount(TopPerformingPosts, {
      props: { posts },
    })
    // Click "X" filter
    const buttons = wrapper.findAll('button')
    const xButton = buttons.filter((b) => b.text() === 'X')[0]!
    await xButton.trigger('click')
    expect(wrapper.text()).toContain('dashboard.contentPerformance.noPostsMatch')
  })

  it('renders post metrics (reactions, comments, shares, engagement)', () => {
    const wrapper = mount(TopPerformingPosts, {
      props: {
        posts: [makePost('p1', { reactions: 342, comments: 89, shares: 67, engagementRate: 7.2 })],
      },
    })
    expect(wrapper.text()).toContain('dashboard.contentPerformance.reactions')
    expect(wrapper.text()).toContain('dashboard.contentPerformance.comments')
    expect(wrapper.text()).toContain('dashboard.contentPerformance.shares')
    expect(wrapper.text()).toContain('dashboard.contentPerformance.engagementRate')
  })
})
