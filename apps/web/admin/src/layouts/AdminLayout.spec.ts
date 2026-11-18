import { describe, it, expect, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { messages } from '@/i18n'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { useAdminAuthStore } from '@/stores/auth.store'

const { replace } = vi.hoisted(() => ({ replace: vi.fn<(target: { name: string }) => void>() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace, currentRoute: { value: { name: 'dashboard' } } }),
  RouterView: { template: '<div />' },
  RouterLink: { template: '<a><slot /></a>' },
}))

describe('AdminLayout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    replace.mockClear()
  })

  it('leaves the admin surface when the admin session is denied', async () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'admin@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OWNER'],
    }
    const wrapper = mountLayout()

    store.accessDenied = true
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({ name: 'access-denied' })
    wrapper.unmount()
  })

  it('returns to the login screen when the admin session ends', async () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'admin@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OWNER'],
    }
    const wrapper = mountLayout()

    store.clearSession()
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({ name: 'login' })
    wrapper.unmount()
  })
})

function mountLayout() {
  return mount(AdminLayout, {
    shallow: true,
    global: {
      plugins: [createI18n({ legacy: false, locale: 'en', messages })],
    },
  })
}
