import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SidebarConnectSection from './SidebarConnectSection.vue'
import type { ProviderCatalogItem } from '@shared/lib/provider-presentation'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const providers: ProviderCatalogItem[] = [
  {
    provider: 'linkedin',
    accountKinds: ['PERSONAL_PROFILE'],
    state: 'AVAILABLE',
    reason: null,
    channelLimit: null,
    connectedChannelCount: 0,
    canConnectMore: true,
  },
  {
    provider: 'instagram',
    accountKinds: ['PERSONAL_PROFILE'],
    state: 'LOCKED',
    reason: 'CAPACITY_REACHED',
    channelLimit: null,
    connectedChannelCount: 1,
    canConnectMore: true,
  },
  {
    provider: 'threads',
    accountKinds: ['PERSONAL_PROFILE'],
    state: 'HIDDEN',
    reason: null,
    channelLimit: null,
    connectedChannelCount: 0,
    canConnectMore: true,
  },
]

describe('SidebarConnectSection', () => {
  it('renders an available provider as an actionable connect CTA', async () => {
    const wrapper = mount(SidebarConnectSection, { props: { providers } })

    const connectButton = wrapper.get('[data-testid="connect-provider-linkedin"]')
    expect(connectButton.text()).toContain('LinkedIn')
    await connectButton.trigger('click')

    expect(wrapper.emitted('connect')?.[0]).toEqual([providers[0]])
  })

  it('renders a locked provider reason without an actionable CTA', () => {
    const wrapper = mount(SidebarConnectSection, { props: { providers } })

    expect(wrapper.get('[data-testid="locked-provider-instagram"]').text()).toContain(
      'CAPACITY_REACHED',
    )
    expect(wrapper.find('[data-testid="connect-provider-instagram"]').exists()).toBe(false)
  })

  it('omits hidden providers and all static coming-soon controls', () => {
    const wrapper = mount(SidebarConnectSection, { props: { providers } })

    expect(wrapper.text()).not.toContain('Threads')
    expect(wrapper.text()).not.toContain('channels.more')
    expect(wrapper.findAll('button')).toHaveLength(1)
  })
})
