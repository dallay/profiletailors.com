import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'
import {
  PROVIDER_ACTIONS,
  getProviderPresentation,
  type ProviderCatalogItem,
} from './provider-presentation'

describe('provider presentation', () => {
  it('maps the available LinkedIn catalog entry to its user-facing label and action', () => {
    const provider: ProviderCatalogItem = {
      provider: 'linkedin',
      accountKinds: ['PERSONAL_PROFILE'],
      state: 'AVAILABLE',
      reason: null,
      channelLimit: null,
      connectedChannelCount: 0,
      canConnectMore: true,
    }

    expect(getProviderPresentation(provider.provider)).toMatchObject({
      label: 'LinkedIn',
      icon: 'linkedin',
      action: PROVIDER_ACTIONS.CONNECT_LINKEDIN_PERSONAL_PROFILE,
    })
  })

  it('uses a neutral fallback without an action for unknown providers', () => {
    const presentation = getProviderPresentation('mastodon')

    expect(presentation).toEqual({
      label: 'Unknown provider',
      icon: 'neutral',
      badge: '•',
      action: null,
    })

    const wrapper = mount(SocialProviderIcon, { props: { provider: 'mastodon' } })
    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.text()).toBe('•')
  })
})
