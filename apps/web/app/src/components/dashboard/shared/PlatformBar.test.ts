import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PlatformBar from './PlatformBar.vue'
import type { ChannelPerformance } from '@/lib/types/dashboard'

function makeChannel(overrides: Partial<ChannelPerformance> = {}): ChannelPerformance {
  return {
    platform: 'linkedin',
    followers: 12400,
    growth: 5.2,
    engagementRate: 5.1,
    postsCount: 42,
    color: '#0A66C2',
    ...overrides,
  }
}

describe('PlatformBar', () => {
  it('renders the platform name', () => {
    const wrapper = mount(PlatformBar, {
      props: { channel: makeChannel(), maxFollowers: 20000 },
    })
    expect(wrapper.text()).toContain('LinkedIn')
  })

  it('renders formatted follower count', () => {
    const wrapper = mount(PlatformBar, {
      props: { channel: makeChannel({ followers: 12400 }), maxFollowers: 20000 },
    })
    expect(wrapper.text()).toContain('12.4K')
  })

  it('renders formatted engagement rate', () => {
    const wrapper = mount(PlatformBar, {
      props: { channel: makeChannel({ engagementRate: 5.1 }), maxFollowers: 20000 },
    })
    expect(wrapper.text()).toContain('5.1%')
  })

  it('renders the bar with proportional width', () => {
    const channel = makeChannel({ followers: 10000 })
    const wrapper = mount(PlatformBar, {
      props: { channel, maxFollowers: 20000 },
    })
    const barFill = wrapper.find('.rounded-full.overflow-hidden > div')
    expect(barFill.attributes('style')).toContain('width: 50%')
  })

  it('handles unknown platform gracefully', () => {
    const wrapper = mount(PlatformBar, {
      props: {
        channel: makeChannel({ platform: 'unknown' as any, followers: 5000 }),
        maxFollowers: 10000,
      },
    })
    expect(wrapper.text()).toContain('unknown')
  })
})
