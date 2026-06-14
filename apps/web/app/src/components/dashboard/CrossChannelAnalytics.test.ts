import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import CrossChannelAnalytics from './CrossChannelAnalytics.vue'
import type { ChannelPerformance } from '@/lib/types/dashboard'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeChannel(platform: ChannelPerformance['platform'], overrides: Partial<ChannelPerformance> = {}): ChannelPerformance {
  return {
    platform,
    followers: 10000,
    growth: 5.2,
    engagementRate: 4.5,
    postsCount: 30,
    color: '#0A66C2',
    ...overrides,
  }
}

describe('CrossChannelAnalytics', () => {
  it('renders the title and subtitle', () => {
    const wrapper = mount(CrossChannelAnalytics, {
      props: { channels: [makeChannel('linkedin')] },
    })
    expect(wrapper.text()).toContain('dashboard.crossChannel.title')
    expect(wrapper.text()).toContain('dashboard.crossChannel.subtitle')
  })

  it('renders all channels', () => {
    const channels = [
      makeChannel('linkedin', { followers: 12400 }),
      makeChannel('twitter', { followers: 8300 }),
      makeChannel('bluesky', { followers: 2800 }),
      makeChannel('threads', { followers: 1200 }),
    ]
    const wrapper = mount(CrossChannelAnalytics, {
      props: { channels },
    })
    expect(wrapper.text()).toContain('LinkedIn')
    expect(wrapper.text()).toContain('X')
    expect(wrapper.text()).toContain('Bluesky')
    expect(wrapper.text()).toContain('Threads')
  })

  it('renders follower count and engagement rate per channel', () => {
    const wrapper = mount(CrossChannelAnalytics, {
      props: { channels: [makeChannel('linkedin', { followers: 12400, engagementRate: 5.1 })] },
    })
    expect(wrapper.text()).toContain('12.4K')
    expect(wrapper.text()).toContain('5.1%')
  })

  it('renders the follower context text for each channel', () => {
    const wrapper = mount(CrossChannelAnalytics, {
      props: { channels: [makeChannel('linkedin', { followers: 12400 })] },
    })
    expect(wrapper.text()).toContain('dashboard.crossChannel.followerContext')
  })

  it('renders bars for each channel', () => {
    const wrapper = mount(CrossChannelAnalytics, {
      props: { channels: [makeChannel('linkedin'), makeChannel('twitter')] },
    })
    const bars = wrapper.findAll('.rounded-full.overflow-hidden > div')
    expect(bars.length).toBe(2)
  })
})
