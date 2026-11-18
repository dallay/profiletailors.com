import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from '@/views/LoginView.vue'
import { messages } from '@/i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { redirect: '/waitlist' } }),
}))

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts the admin BFF login with the requested return route', async () => {
    const authStore = useAdminAuthStore()
    const signIn = vi.fn()
    authStore.signIn = signIn
    const wrapper = mountLogin()

    await wrapper.get('[data-testid="admin-login-button"]').trigger('click')

    expect(signIn).toHaveBeenCalledWith('/waitlist')
    expect(wrapper.find('input[type="password"]').exists()).toBe(false)
  })
})

function mountLogin(locale = 'en') {
  return mount(LoginView, {
    global: {
      plugins: [createI18n({ legacy: false, locale, messages })],
    },
  })
}
