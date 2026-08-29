import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from '@/views/LoginView.vue'
import { messages } from '@/i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

const replace = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { redirect: '/waitlist' } }),
  useRouter: () => ({ replace }),
}))

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    replace.mockReset()
  })

  it('submits credentials and navigates to a safe redirect', async () => {
    const authStore = useAdminAuthStore()
    const signIn = vi.fn().mockResolvedValue(undefined)
    authStore.signIn = signIn
    const wrapper = mountLogin()

    await wrapper.get('[data-testid="admin-login-email"]').setValue(' Admin@Example.com ')
    await wrapper.get('[data-testid="admin-login-password"]').setValue(' password ')
    await wrapper.get('[data-testid="admin-login-form"]').trigger('submit')
    await flushPromises()

    expect(signIn).toHaveBeenCalledWith('admin@example.com', ' password ')
    expect(replace).toHaveBeenCalledWith('/waitlist')
  })

  it('shows the API error for invalid credentials', async () => {
    const authStore = useAdminAuthStore()
    authStore.signIn = vi.fn().mockRejectedValue(new Error('invalid'))
    const wrapper = mountLogin()

    await wrapper.get('[data-testid="admin-login-email"]').setValue('admin@example.com')
    await wrapper.get('[data-testid="admin-login-password"]').setValue('password')
    await wrapper.get('[data-testid="admin-login-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-login-error"]').text()).toContain('Unable to sign in')
  })
})

function mountLogin() {
  return mount(LoginView, {
    global: {
      plugins: [createI18n({ legacy: false, locale: 'en', messages })],
    },
  })
}
