import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { Channel } from '@modules/publishing/domain/channel'
import ComposerChannelSelector from './ComposerChannelSelector.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
  isSocialProvider: vi.fn(() => true),
}))

function makeChannel(overrides: Partial<Channel> = {}): Channel {
  return {
    id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
    name: 'My LinkedIn',
    handle: '@mylinkedin',
    provider: 'linkedin',
    avatar: '',
    accountId: '788c8103-35f1-5899-b15a-6ca994253c99',
    status: 'ACTIVE',
    avatarUrl: undefined,
    ...overrides,
  }
}

function mountSelector(propsOverride: Record<string, unknown> = {}) {
  return mount(ComposerChannelSelector, {
    props: {
      channels: [] as Channel[],
      selectedChannelIds: [] as string[],
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
    expect(allButtons).toHaveLength(1)
    expect(wrapper.find('[title="composer.channelAdd"]').exists()).toBe(true)
  })

  it('renders one channel button per active channel', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        name: 'Channel A',
        provider: 'linkedin',
      }),
      makeChannel({
        id: '8d7c2526-b27d-5c90-861f-d9b91afe47f2',
        name: 'Channel B',
        provider: 'twitter',
      }),
    ]
    const wrapper = mountSelector({ channels })
    const buttons = wrapper.findAll('[data-testid="channel-button"]')
    expect(buttons).toHaveLength(2)
  })

  it('hides the channel name in the chip; the preview header shows it', (): void => {
    const channels = [
      makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef', name: 'My LinkedIn Profile' }),
    ]
    const wrapper = mountSelector({ channels })
    expect(wrapper.text()).not.toContain('My LinkedIn Profile')
  })

  it('renders the Threads provider icon for a connected Threads channel', (): void => {
    const channels = [
      makeChannel({
        id: '2f51c458-452b-5126-ae7d-7ae5de60a627',
        name: 'Threads profile',
        provider: 'threads',
      }),
    ]
    const wrapper = mountSelector({ channels })

    expect(wrapper.find('[data-testid="provider-icon-threads"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Threads profilet')
  })

  it('marks the selected channel with the accent border, not a check icon', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({
      channels,
      selectedChannelIds: ['92c0751b-b11e-5b27-b9bd-8c4b03c253ef'],
    })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    expect(button.classes()).toContain('border-text-display')
    expect(wrapper.find('[data-testid="channel-selected-icon"]').exists()).toBe(false)
  })

  it('marks every selected id with the accent border', (): void => {
    const channels = [
      makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef', name: 'LinkedIn' }),
      makeChannel({
        id: '8d7c2526-b27d-5c90-861f-d9b91afe47f2',
        name: 'Threads',
        provider: 'threads',
      }),
    ]
    const wrapper = mountSelector({
      channels,
      selectedChannelIds: [
        '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        '8d7c2526-b27d-5c90-861f-d9b91afe47f2',
      ],
    })
    const buttons = wrapper.findAll('[data-testid="channel-button"]')
    expect(buttons.filter((b) => b.classes().includes('border-text-display'))).toHaveLength(2)
  })

  it('does not render an X icon on non-selected channels', (): void => {
    const channels = [
      makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' }),
      makeChannel({ id: '8d7c2526-b27d-5c90-861f-d9b91afe47f2' }),
    ]
    const wrapper = mountSelector({
      channels,
      selectedChannelIds: ['92c0751b-b11e-5b27-b9bd-8c4b03c253ef'],
    })
    expect(wrapper.find('[data-testid="channel-deselected-icon"]').exists()).toBe(false)
  })

  it('emits toggle with the channel id when a non-selected channel button is clicked', async (): Promise<void> => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels, selectedChannelIds: [] })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    await button.trigger('click')
    const emissions = wrapper.emitted('toggle') ?? []
    expect(emissions).toHaveLength(1)
    expect(emissions[0]).toEqual(['92c0751b-b11e-5b27-b9bd-8c4b03c253ef'])
  })

  it('emits toggle when a selected channel button is clicked to deselect it', async (): Promise<void> => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({
      channels,
      selectedChannelIds: ['92c0751b-b11e-5b27-b9bd-8c4b03c253ef'],
    })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    await button.trigger('click')
    expect(wrapper.emitted('toggle')).toEqual([['92c0751b-b11e-5b27-b9bd-8c4b03c253ef']])
  })

  it('does not emit toggle when in edit mode', async (): Promise<void> => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels, selectedChannelIds: [], isEditMode: true })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    await button.trigger('click')
    expect(wrapper.emitted('toggle')).toBeUndefined()
  })

  it('disables channel buttons in edit mode', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels, isEditMode: true })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    expect(button.attributes('disabled')).toBe('')
  })

  it('does not disable channel buttons in create mode', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels, isEditMode: false })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('renders the channel avatar when avatarUrl is provided', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        avatarUrl: 'https://example.com/avatar.jpg',
      }),
    ]
    const wrapper = mountSelector({ channels })
    const avatars = wrapper.findAll('img')
    expect(avatars).toHaveLength(1)
    expect(avatars[0]!.attributes('src')).toBe('https://example.com/avatar.jpg')
  })

  it('uses the channel name as the avatar alternative text', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        name: 'Profile Tailors LinkedIn',
        avatarUrl: 'https://example.com/avatar.jpg',
      }),
    ]
    const wrapper = mountSelector({ channels })

    expect(wrapper.get('img').attributes('alt')).toBe('')
  })

  it('uses generic alternative text when an avatar has no channel name', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        name: '',
        avatarUrl: 'https://example.com/avatar.jpg',
      }),
    ]
    const wrapper = mountSelector({ channels })

    expect(wrapper.get('img').attributes('alt')).toBe('')
  })

  it('renders initials with a provider badge when avatarUrl is missing', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        name: 'My Profile',
        avatarUrl: undefined,
        provider: 'linkedin',
      }),
    ]
    const wrapper = mountSelector({ channels })
    const fallbacks = wrapper.findAll('[data-testid="channel-avatar-fallback"]')
    expect(fallbacks).toHaveLength(1)
    expect(fallbacks[0]!.text()).toBe('MP')
    const badge = wrapper.find('[data-testid="channel-provider-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.find('svg').exists()).toBe(true)
  })

  it('renders a Buffer-style chip with the user photo as the primary element and a small provider badge', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        name: 'WireMock Threads User',
        avatarUrl: 'https://example.com/avatar.jpg',
        provider: 'threads',
      }),
    ]
    const wrapper = mountSelector({ channels })
    const img = wrapper.get('img')
    expect(img.classes()).toContain('size-11')
    expect(img.classes()).toContain('rounded-xl')
    const badge = wrapper.find('[data-testid="channel-provider-badge"]')
    expect(badge.classes()).toContain('size-5')
  })

  it('falls back to initials with a small provider badge when avatarUrl is missing', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        name: 'My Profile',
        avatarUrl: undefined,
        provider: 'linkedin',
      }),
    ]
    const wrapper = mountSelector({ channels })
    const fallback = wrapper.find('[data-testid="channel-avatar-fallback"]')
    expect(fallback.exists()).toBe(true)
    expect(fallback.text()).toBe('MP')
    expect(fallback.classes()).toContain('rounded-xl')
    const badge = wrapper.find('[data-testid="channel-provider-badge"]')
    expect(badge.exists()).toBe(true)
  })

  it('renders the centralized provider badge for Twitter', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        avatarUrl: undefined,
        provider: 'twitter',
      }),
    ]
    const wrapper = mountSelector({ channels })
    expect(wrapper.find('[data-testid="channel-provider-badge"]').text()).toBe('𝕏')
  })

  it('applies the accent border and selected styling to the selected channel', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({
      channels,
      selectedChannelIds: ['92c0751b-b11e-5b27-b9bd-8c4b03c253ef'],
    })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    expect(button.classes()).toContain('border-text-display')
  })

  it('applies unselected border to non-selected channels', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({
      channels,
      selectedChannelIds: ['8d7c2526-b27d-5c90-861f-d9b91afe47f2'],
    })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    expect(button.classes()).toContain('border-border-visible')
    expect(button.classes()).not.toContain('border-text-display')
  })

  it('applies disabled opacity style when in edit mode', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels, isEditMode: true })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    expect(button.classes()).toContain('opacity-60')
    expect(button.classes()).toContain('cursor-not-allowed')
  })

  it('includes a connect-more button that matches the avatar size and shape', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels })
    const add = wrapper.find('[data-testid="channel-add"]')
    expect(add.exists()).toBe(true)
    expect(add.classes()).toContain('size-11')
    expect(add.classes()).toContain('rounded-xl')
  })

  it('has a select-channels label for accessibility', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels })
    expect(wrapper.find('[data-testid="channel-selector"]').exists()).toBe(true)
  })

  it('includes the provider in the channel button accessible name', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        name: 'My Profile',
        provider: 'linkedin',
      }),
    ]
    const wrapper = mountSelector({ channels })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    expect(button.attributes('aria-label')).toContain('My Profile')
    expect(button.attributes('aria-label')).toContain('linkedin')
  })

  it('uses the channel name as accessible name when provider is absent', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        name: 'My Profile',
        provider: undefined,
      }),
    ]
    const wrapper = mountSelector({ channels })
    const button = wrapper.findAll('[data-testid="channel-button"]')[0]!
    expect(button.attributes('aria-label')).toBe('My Profile')
  })

  it('sets the avatar image alt to empty to avoid duplication', (): void => {
    const channels = [
      makeChannel({
        id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
        avatarUrl: 'https://example.com/avatar.jpg',
      }),
    ]
    const wrapper = mountSelector({ channels })
    const img = wrapper.find('img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('alt')).toBe('')
  })

  it('sets i18n accessible name on the add-channel button', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels })
    const add = wrapper.find('[data-testid="channel-add"]')
    expect(add.attributes('aria-label')).toBe('composer.channelAdd')
    expect(add.attributes('title')).toBe('composer.channelAdd')
  })

  it('emits add when the channel-add button is clicked', (): void => {
    const channels = [makeChannel({ id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef' })]
    const wrapper = mountSelector({ channels })
    const add = wrapper.find('[data-testid="channel-add"]')
    add.trigger('click')
    const emitted = wrapper.emitted<[]>().add
    expect(emitted).toHaveLength(1)
  })
})
