import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import dashboard from '@shared/i18n/locales/en/dashboard'
import HomeView from './HomeView.vue'

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({ displayName: 'Taylor' }),
}))
vi.mock('@/components/ui/sidebar', () => ({
  useSidebar: () => ({ isMobile: { value: false }, setOpen: vi.fn() }),
}))
vi.mock('@modules/dashboard/presentation/components/DashboardLayout.vue', () => ({
  default: { template: '<div>Dashboard metrics</div>' },
}))
vi.mock('@modules/publishing/presentation/components/CreatePostModal.vue', () => ({
  default: { template: '<div />' },
}))

describe('HomeView', () => {
  it('identifies illustrative metrics and explains where connected accounts remain available', () => {
    const wrapper = mount(HomeView, {
      global: {
        plugins: [createI18n({ legacy: false, locale: 'en', messages: { en: { dashboard } } })],
      },
    })
    expect(wrapper.get('h2').text()).toBe('Welcome back, Taylor')
    expect(wrapper.text()).toContain('Preview data')
    expect(wrapper.text()).toContain(
      'Dashboard metrics and recommendations are illustrative while workspace analytics are being connected. Your connected accounts remain available in the sidebar.',
    )
    expect(wrapper.text()).toContain('Dashboard metrics')
  })
})
