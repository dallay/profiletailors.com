import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ChannelShape } from '@modules/publishing/infrastructure/publishing.store'
import ComposerChannelSelector from './ComposerChannelSelector.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
  isSocialProvider: vi.fn(() => true),
}))

function makeChannel(overrides: Partial<ChannelShape> = {}): ChannelShape {
  return {
    id: 'ch-1',
    name: 'My LinkedIn',
    handle: '@mylinkedin',
    provider: 'linkedin',
    status: 'ACTIVE',
    avatarUrl: null,
    ...overrides,
  }
}

function mountSelector(propsOverride: Record<string, unknown> = {}) {
  return mount(ComposerChannelSelector, {
    props: {
      channels: [] as ChannelShape[],
      selectedChannelId: null as string | null,
      isEditMode: false,
      ...propsOverride,
    },
    global: {
      mocks: {
        $t: (key: string) => key,
      },
    },
  })
}

describe('ComposerChannelSelector.vue', () => {
  it('renders only the connect-more button when channels list is empty', (): void => {
    const wrapper = mountSelector({ channels: [] })
    const allButtons = wrapper.findAll('button')
    // Only the "Connect another channel" button is shown when there are no channels
    expect(allButtons.length).toBe(1)
    expect(wrapper.find('[title="Connect another channel"]').exists()).toBe(true)
  })

  it('renders one channel button per active channel', (): void => {
    const channels = [
      makeChannel({ id: 'ch-1', name: 'Channel A', provider: 'linkedin' }),
      makeChannel({ id: 'ch-2', name: 'Channel B', provider: 'twitter' }),
    ]
    const wrapper = mountSelector({ channels })
    const buttons = wrapper.findAll('button:not([title])')
    expect(buttons.length).toBe(2)
  })

  it('shows the channel name in the button', (): void => {
    const channels = [makeChannel({ id: 'ch-1', name: 'My LinkedIn Profile' })]
    const wrapper = mountSelector({ channels })
    expect(wrapper.text()).toContain('My LinkedIn Profile')
  })

  it('shows a checkmark icon on the selected channel', (): void => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, selectedChannelId: 'ch-1' })
    const checkIcon = wrapper.find('[data-testid="channel-selected-icon"]')
    expect(checkIcon.exists()).toBe(true)
  })

  it('shows an X icon on non-selected channels', (): void => {
    const channels = [makeChannel({ id: 'ch-1' }), makeChannel({ id: 'ch-2' })]
    const wrapper = mountSelector({ channels, selectedChannelId: 'ch-1' })
    const deselectIcons = wrapper.findAll('[data-testid="channel-deselected-icon"]')
    expect(deselectIcons.length).toBe(1)
  })

  it('emits select with the channel id when a non-selected channel button is clicked', async (): Promise<void> => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, selectedChannelId: null })
    const button = wrapper.findAll('button:not([title])')[0]
    await button.trigger('click')
    const emissions = wrapper.emitted('select') ?? []
    expect(emissions).toHaveLength(1)
    expect(emissions[0]).toEqual(['ch-1'])
  })

  it('does not emit select when a selected channel button is clicked', async (): Promise<void> => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, selectedChannelId: 'ch-1' })
    const button = wrapper.findAll('button:not([title])')[0]
    await button.trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('does not emit select when in edit mode', async (): Promise<void> => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, selectedChannelId: null, isEditMode: true })
    const button = wrapper.findAll('button:not([title])')[0]
    await button.trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('disables channel buttons in edit mode', (): void => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, isEditMode: true })
    const button = wrapper.findAll('button:not([title])')[0]
    expect(button.attributes('disabled')).toBe('')
  })

  it('does not disable channel buttons in create mode', (): void => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, isEditMode: false })
    const button = wrapper.findAll('button:not([title])')[0]
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('renders the channel avatar when avatarUrl is provided', (): void => {
    const channels = [makeChannel({ id: 'ch-1', avatarUrl: 'https://example.com/avatar.jpg' })]
    const wrapper = mountSelector({ channels })
    const avatars = wrapper.findAll('img')
    expect(avatars.length).toBe(1)
    expect(avatars[0].attributes('src')).toBe('https://example.com/avatar.jpg')
  })

  it('renders a provider fallback when avatarUrl is missing', (): void => {
    const channels = [makeChannel({ id: 'ch-1', avatarUrl: null, provider: 'linkedin' })]
    const wrapper = mountSelector({ channels })
    const fallbacks = wrapper.findAll('[data-testid="channel-avatar-fallback"]')
    expect(fallbacks.length).toBe(1)
    expect(fallbacks[0].text()).toContain('in')
  })

  it('renders a Twitter fallback with first letter of provider', (): void => {
    const channels = [makeChannel({ id: 'ch-1', avatarUrl: null, provider: 'twitter' })]
    const wrapper = mountSelector({ channels })
    const fallbacks = wrapper.findAll('[data-testid="channel-avatar-fallback"]')
    expect(fallbacks[0].text()).toBe('t')
  })

  it('applies bold and selected styling to the selected channel', (): void => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, selectedChannelId: 'ch-1' })
    const button = wrapper.findAll('button:not([title])')[0]
    expect(button.classes()).toContain('border-text-display')
    expect(button.classes()).toContain('bg-bg-primary')
    expect(button.classes()).toContain('text-text-display')
    expect(button.classes()).toContain('font-bold')
  })

  it('applies unselected styling to non-selected channels', (): void => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, selectedChannelId: 'ch-2' })
    const button = wrapper.findAll('button:not([title])')[0]
    expect(button.classes()).toContain('border-border-visible')
    expect(button.classes()).toContain('text-text-secondary')
  })

  it('applies disabled opacity style when in edit mode', (): void => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels, isEditMode: true })
    const button = wrapper.findAll('button:not([title])')[0]
    expect(button.classes()).toContain('opacity-60')
    expect(button.classes()).toContain('cursor-not-allowed')
  })

  it('includes a connect-more button', (): void => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels })
    expect(wrapper.find('[title="Connect another channel"]').exists()).toBe(true)
  })

  it('has a select-channels label for accessibility', (): void => {
    const channels = [makeChannel({ id: 'ch-1' })]
    const wrapper = mountSelector({ channels })
    expect(wrapper.find('[data-testid="channel-selector"]').exists()).toBe(true)
  })
})
