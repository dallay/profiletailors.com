import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
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

function mountHeader() {
  return mount(AppHeader, { attachTo: document.body })
}

describe('AppHeader', () => {
  beforeEach(() => {
    routeState.name = 'analytics'
    routeState.path = '/analytics'
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the Workspace eyebrow and the resolved nav.analytics h1', () => {
    const wrapper = mountHeader()
    const text = wrapper.text()
    expect(text).toContain('Workspace')
    expect(text).toContain('nav.analytics')
  })

  it('does NOT render any theme toggle, language pill, or status pill in the header', () => {
    const wrapper = mountHeader()
    // No role="status" (status pill removed)
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    // No role="radio" (language pill removed)
    expect(wrapper.findAll('[role="radio"]').length).toBe(0)
    // No ThemeToggle component
    expect(wrapper.find('.theme-toggle').exists()).toBe(false)
    // No language button
    expect(wrapper.find('button[aria-label="settings.languageLabel"]').exists()).toBe(false)
  })

  it('updates the resolved h1 label when the route name changes', async () => {
    routeState.name = 'scheduler'
    const wrapper = mountHeader()
    await nextTick()
    expect(wrapper.text()).toContain('nav.scheduler')
  })
})
