import { describe, it, expect, vi, beforeEach } from 'vitest'
import { reactive, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SidebarAccountSection from './SidebarAccountSection.vue'

const routeState = reactive({ path: '/', fullPath: '/' })
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      locale: { value: 'en' },
    },
  }),
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@lucide/vue', () => ({
  LogOut: { name: 'LogOut', template: '<span />' },
  Settings: { name: 'Settings', template: '<span />' },
}))

const user = {
  displayName: 'Yuniel A.',
  email: 'yuniel@example.com',
  initials: 'YA',
  isRefreshing: false,
}

describe('SidebarAccountSection', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeState.path = '/'
    routeState.fullPath = '/'
  })

  it('closed state shows the trigger only — no panel in the DOM', () => {
    const wrapper = mount(SidebarAccountSection, {
      props: { user },
      attachTo: document.body,
    })

    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(false)
    expect(wrapper.find('button[aria-haspopup="menu"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Yuniel A.')
    expect(wrapper.text()).toContain('YA')
  })

  it('open popover shows account, theme, language, and logout controls', async () => {
    const wrapper = mount(SidebarAccountSection, {
      props: { user },
      attachTo: document.body,
    })

    const trigger = wrapper.find('button[aria-haspopup="menu"]')
    await trigger.trigger('click')
    await nextTick()

    const panel = wrapper.find('#sidebar-account-menu')
    expect(panel.exists()).toBe(true)
    expect(panel.attributes('role')).toBe('menu')

    const items = panel.findAll('[role="menuitem"]')
    expect(items).toHaveLength(2)
    expect(panel.text()).toContain('Account settings')
    expect(panel.text()).toContain('settings.themeLabel')
    expect(panel.text()).toContain('settings.languageLabel')
    expect(panel.text()).toContain('Log Out')
    expect(panel.findAll('[role="radiogroup"]')).toHaveLength(2)
  })

  it('emits openSettings when Account settings is clicked, then closes the popover', async () => {
    const wrapper = mount(SidebarAccountSection, {
      props: { user },
      attachTo: document.body,
    })
    await wrapper.find('button[aria-haspopup="menu"]').trigger('click')
    await nextTick()

    const settingsBtn = wrapper
      .findAll('[role="menuitem"]')
      .find((b) => b.text().includes('Account settings'))
    expect(settingsBtn).toBeTruthy()
    await settingsBtn?.trigger('click')
    await nextTick()

    expect(wrapper.emitted('openSettings')).toBeTruthy()
    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(false)
  })

  it('emits logout when Log Out is clicked, then closes the popover', async () => {
    const wrapper = mount(SidebarAccountSection, {
      props: { user },
      attachTo: document.body,
    })
    await wrapper.find('button[aria-haspopup="menu"]').trigger('click')
    await nextTick()

    const logoutBtn = wrapper.findAll('[role="menuitem"]').find((b) => b.text().includes('Log Out'))
    expect(logoutBtn).toBeTruthy()
    await logoutBtn?.trigger('click')
    await nextTick()

    expect(wrapper.emitted('logout')).toBeTruthy()
    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(false)
  })

  it('Escape, click-outside, and route change all close the popover', async () => {
    const wrapper = mount(SidebarAccountSection, {
      props: { user },
      attachTo: document.body,
    })
    const trigger = wrapper.find('button[aria-haspopup="menu"]')

    // Escape
    await trigger.trigger('click')
    await nextTick()
    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    await nextTick()
    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(false)

    // Click outside
    await trigger.trigger('click')
    await nextTick()
    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(true)
    const outside = document.createElement('div')
    document.body.appendChild(outside)
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    await nextTick()
    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(false)
    document.body.removeChild(outside)

    // Route change
    await trigger.trigger('click')
    await nextTick()
    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(true)
    routeState.path = '/scheduler'
    routeState.fullPath = '/scheduler'
    await nextTick()
    await nextTick()
    expect(wrapper.find('#sidebar-account-menu').exists()).toBe(false)
  })

  it('trigger exposes the full ARIA trio (haspopup, expanded, controls)', () => {
    const wrapper = mount(SidebarAccountSection, {
      props: { user },
      attachTo: document.body,
    })
    const trigger = wrapper.find('button[aria-haspopup="menu"]')
    expect(trigger.attributes('aria-haspopup')).toBe('menu')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(trigger.attributes('aria-controls')).toBe('sidebar-account-menu')
  })
})
