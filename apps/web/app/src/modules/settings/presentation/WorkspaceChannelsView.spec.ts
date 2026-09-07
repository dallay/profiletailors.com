import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WorkspaceChannelsView from './WorkspaceChannelsView.vue'

const connectLinkedInMock = vi.hoisted(() => vi.fn())

const defaultStore = {
  channels: [] as never[],
  providerCatalog: [] as never[],
  connectLinkedInPersonalProfile: connectLinkedInMock,
}

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
  usePublishingStore: () => defaultStore,
}))

describe('WorkspaceChannelsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    connectLinkedInMock.mockResolvedValue(undefined)
    defaultStore.channels = []
    defaultStore.providerCatalog = []
  })

  it('renders header', () => {
    const wrapper = mount(WorkspaceChannelsView, { global: { stubs: { teleport: true } } })
    expect(wrapper.find('h1').text()).toBeTruthy()
  })

  it('renders empty state when no channels', () => {
    const wrapper = mount(WorkspaceChannelsView, { global: { stubs: { teleport: true } } })
    expect(wrapper.text()).toContain('noConnectedChannels')
  })

  it('renders channel list when channels are present', () => {
    defaultStore.channels = [
      {
        id: 'ch-1',
        name: 'Ada Lovelace',
        provider: 'linkedin',
        avatar: 'https://example.com/avatar.jpg',
        status: 'ACTIVE',
      },
    ]
    const wrapper = mount(WorkspaceChannelsView, { global: { stubs: { teleport: true } } })
    expect(wrapper.text()).toContain('Ada Lovelace')
    expect(wrapper.text()).toContain('linkedin')
  })

  it('calls connectLinkedInPersonalProfile when available action is triggered', async () => {
    defaultStore.providerCatalog = [
      {
        provider: 'linkedin',
        state: 'AVAILABLE',
        connectedChannelCount: 0,
        accountKinds: ['PERSONAL_PROFILE'],
        channelLimit: null,
        canConnectMore: true,
        reason: null,
      },
    ]
    const wrapper = mount(WorkspaceChannelsView, { global: { stubs: { teleport: true } } })
    const connectBtn = wrapper.find('button[type="button"]')
    await connectBtn.trigger('click')
    expect(connectLinkedInMock).toHaveBeenCalled()
  })
})
