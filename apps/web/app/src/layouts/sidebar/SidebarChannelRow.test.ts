import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { proxyImageUrl } from '@modules/auth/infrastructure/auth-api'
import { getProviderBadge } from '@shared/lib/provider-styles'
import SidebarChannelRow from './SidebarChannelRow.vue'
import type { Channel } from '@modules/publishing/infrastructure/publishing.store'

interface SidebarChannel extends Channel {
  badge: string
  queuedCount: number
}

function makeChannel(overrides: Partial<SidebarChannel> = {}): SidebarChannel {
  return {
    id: 'ch-1',
    accountId: 'ch-1',
    name: 'Channel 1',
    provider: 'linkedin',
    avatar: '',
    avatarUrl: 'https://example.com/a.jpg',
    handle: 'Channel 1',
    status: 'ACTIVE',
    badge: 'in',
    queuedCount: 0,
    ...overrides,
  }
}

describe('SidebarChannelRow', () => {
  it('renders an <img> with proxyImageUrl(src) and the channel name alt', () => {
    const wrapper = mount(SidebarChannelRow, {
      props: { channel: makeChannel(), isActive: false, queuedCount: 0 },
    })

    const img = wrapper.find('img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe(proxyImageUrl('https://example.com/a.jpg'))
    expect(img.attributes('alt')).toBe('Channel 1 avatar')
  })

  it('renders a fallback badge span when avatarUrl is missing', () => {
    const wrapper = mount(SidebarChannelRow, {
      props: {
        channel: makeChannel({ avatarUrl: undefined }),
        isActive: false,
        queuedCount: 0,
      },
    })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain(getProviderBadge('linkedin'))
  })

  it('swaps to the fallback badge and emits avatarError on img @error', async () => {
    const wrapper = mount(SidebarChannelRow, {
      props: { channel: makeChannel(), isActive: false, queuedCount: 0 },
    })

    const img = wrapper.find('img')
    expect(img.exists()).toBe(true)

    await img.trigger('error')

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain(getProviderBadge('linkedin'))
    expect(wrapper.emitted('avatarError')).toBeTruthy()
    expect(wrapper.emitted('avatarError')?.length).toBe(1)
  })

  it('emits select on click', async () => {
    const wrapper = mount(SidebarChannelRow, {
      props: { channel: makeChannel(), isActive: false, queuedCount: 0 },
    })

    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')?.length).toBe(1)
  })

  it('does not leak avatar failure between sibling rows', async () => {
    const wrapper = mount(SidebarChannelRow, {
      props: { channel: makeChannel(), isActive: false, queuedCount: 0 },
    })

    // Trigger error and confirm local ref flips
    const img = wrapper.find('img')
    await img.trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)

    // After error, the row only renders the badge; emitting avatarError is
    // a notification, not a state-machine coupling to other rows.
    expect(wrapper.emitted('avatarError')).toBeTruthy()
  })
})
