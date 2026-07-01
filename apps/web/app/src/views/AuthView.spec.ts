import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AuthView from './AuthView.vue'

const routeState = vi.hoisted(() => ({
  name: 'login' as 'login' | 'register',
  query: {} as Record<string, unknown>,
}))
const replace = vi.hoisted(() => vi.fn())
const loginWithPassword = vi.hoisted(() => vi.fn())
const registerWithPassword = vi.hoisted(() => vi.fn())
const clearError = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ replace }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    error: null,
    isLoading: false,
    clearError,
    loginWithPassword,
    registerWithPassword,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function mountAuthView() {
  return mount(AuthView, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
      stubs: {
        Button: {
          template: '<button><slot /></button>',
        },
        RouterLink: {
          template: '<a><slot /></a>',
        },
      },
    },
  })
}

describe('AuthView validation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeState.name = 'login'
    routeState.query = {}
    replace.mockReset()
    clearError.mockReset()
    loginWithPassword.mockReset()
    registerWithPassword.mockReset()
  })

  it('does not submit login when client-side auth validation fails', async () => {
    const wrapper = mountAuthView()

    await wrapper.find('input#email').setValue('bad-email')
    await wrapper.find('input#password').setValue('   ')
    await wrapper.find('form').trigger('submit.prevent')

    expect(loginWithPassword).not.toHaveBeenCalled()
    // Looking for the translated key part from the mock
    expect(wrapper.text()).toContain('auth.invalidEmail')
    expect(wrapper.text()).toContain('auth.passwordRequired')
  })

  it('trims credentials and validates confirm password before submitting registration', async () => {
    routeState.name = 'register'
    registerWithPassword.mockResolvedValue(undefined)
    const wrapper = mountAuthView()

    await wrapper.find('input#email').setValue('  user@example.com  ')
    await wrapper.find('input#password').setValue('  password123  ')
    await wrapper.find('input#confirmPassword').setValue('  password123  ')
    await wrapper.find('form').trigger('submit.prevent')

    // Expecting payload WITHOUT confirmPassword as it is filtered in AuthView.vue
    expect(registerWithPassword).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'password123',
    })
    expect(replace).toHaveBeenCalledWith('/')
  })

  it('shows error if passwords do not match in registration', async () => {
    routeState.name = 'register'
    const wrapper = mountAuthView()

    await wrapper.find('input#email').setValue('user@example.com')
    await wrapper.find('input#password').setValue('password123')
    await wrapper.find('input#confirmPassword').setValue('mismatch')
    await wrapper.find('form').trigger('submit.prevent')

    expect(registerWithPassword).not.toHaveBeenCalled()
    // Looking for the translated key part
    expect(wrapper.text()).toContain('auth.passwordsMustMatch')
  })
})
