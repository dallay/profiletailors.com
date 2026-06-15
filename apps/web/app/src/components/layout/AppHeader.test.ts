import { describe, it, expect, vi } from 'vitest'
import { reactive, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import AppHeader from './AppHeader.vue'

const routeState = reactive({ name: 'analytics', path: '/analytics' })
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/components/ui/sidebar', () => ({
  SidebarTrigger: { template: '<button class="sidebar-trigger" />' },
}))

vi.mock('@lucide/vue', () => ({
  PanelLeft: { name: 'PanelLeft', template: '<span />' },
}))

vi.mock('@/stores/settings', () => ({
  useSettingsStore: () => ({
    currentLocale: 'en',
    currentTheme: 'dark',
    setLocale: vi.fn(),
    setTheme: vi.fn(),
  }),
}))

function mountHeader() {
  return mount(AppHeader, { attachTo: document.body })
}

describe('AppHeader', () => {
  it('renders the Workspace eyebrow and the resolved nav.analytics h1', () => {
    const wrapper = mountHeader()
    const text = wrapper.text()
    expect(text).toContain('Workspace')
    expect(text).toContain('nav.analytics')
  })

  it('renders the status pill with the verbatim summary text', () => {
    const wrapper = mountHeader()
    const status = wrapper.find('[role="status"]')
    expect(status.exists()).toBe(true)
    expect(status.attributes('aria-label')).toBe('Session status')
    // Default mocked route is 'analytics', so the summary is "dark mode / EN"
    expect(status.text()).toBe('dark mode / EN')
  })

  it('renders the language pill with EN active and ES inactive', () => {
    const wrapper = mountHeader()
    const radios = wrapper.findAll('[role="radio"]')
    expect(radios.length).toBe(2)
    const en = radios[0]!
    const es = radios[1]!
    expect(en.attributes('aria-checked')).toBe('true')
    expect(es.attributes('aria-checked')).toBe('false')
  })

  it('does NOT render any theme control in the header (no /theme/i aria-label, no dark/light pair)', () => {
    const wrapper = mountHeader()
    const html = wrapper.html()
    // The header should not contain a button labeled "dark" or "light"
    const buttons = wrapper.findAll('button')
    for (const b of buttons) {
      const text = b.text().trim()
      expect(text).not.toBe('dark')
      expect(text).not.toBe('light')
    }
    // The status pill text contains "dark mode" — that is fine; we only
    // assert no clickable dark/light pair.
    expect(html).not.toMatch(/aria-label="[^"]*theme[^"]*"/i)
  })

  it('emits setLocale with the chosen locale when a language option is clicked', async () => {
    const wrapper = mountHeader()
    const es = wrapper.findAll('[role="radio"]')[1]!
    await es.trigger('click')
    await nextTick()

    expect(wrapper.emitted('setLocale')).toBeTruthy()
    expect(wrapper.emitted('setLocale')?.[0]).toEqual(['es'])
  })
})
