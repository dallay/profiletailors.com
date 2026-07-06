import { mount } from '@vue/test-utils'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import ThemeToggle from './ThemeToggle.vue'
import { createPinia, setActivePinia } from 'pinia'
import { useSettingsStore } from '@/stores/settings'

describe('ThemeToggle.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders correctly', () => {
    const wrapper = mount(ThemeToggle, {
      global: {
        mocks: {
          $t: (msg: string) => msg,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('toggles theme when clicked', async () => {
    const settings = useSettingsStore()
    settings.setTheme('light')

    const wrapper = mount(ThemeToggle, {
      global: {
        mocks: {
          $t: (msg: string) => msg,
        },
      },
    })

    const button = wrapper.find('button')
    await button.trigger('click')

    // settings.currentTheme should be 'dark' after toggle (assuming toggle logic works)
    // The component calls animateThemeChange which calls setTheme.
    expect(settings.currentTheme).toBe('dark')
  })
})
