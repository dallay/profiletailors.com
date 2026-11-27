import { afterEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import i18n from '@shared/i18n'
import SidebarChangelogButton from './SidebarChangelogButton.vue'

enableAutoUnmount(afterEach)

function mountButton(unreadCount = 0) {
  return mount(SidebarChangelogButton, {
    global: { plugins: [i18n] },
    props: { unreadCount },
  })
}

describe('SidebarChangelogButton', () => {
  it('renders trigger button and emits open on click', async () => {
    const wrapper = mountButton(0)
    const button = wrapper.find('[data-testid="changelog-trigger"]')

    expect(button.exists()).toBe(true)
    expect(wrapper.find('[data-testid="changelog-unread-badge"]').exists()).toBe(false)

    await button.trigger('click')
    expect(wrapper.emitted('open')).toHaveLength(1)
  })

  it('renders unread badge when unreadCount is greater than zero', () => {
    const wrapper = mountButton(3)
    const badge = wrapper.find('[data-testid="changelog-unread-badge"]')

    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('3')
  })

  it('updates aria-label to include unread count when unreadCount is positive', () => {
    const wrapperWithUnread = mountButton(2)
    const buttonWithUnread = wrapperWithUnread.find('[data-testid="changelog-trigger"]')
    expect(buttonWithUnread.attributes('aria-label')).toContain('2')

    const wrapperZero = mountButton(0)
    const buttonZero = wrapperZero.find('[data-testid="changelog-trigger"]')
    expect(buttonZero.attributes('aria-label')).not.toContain('(')
  })
})
