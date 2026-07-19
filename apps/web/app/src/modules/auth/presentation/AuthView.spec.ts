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
const publicCapabilitiesLoad = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ replace }),
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    error: null,
    isLoading: false,
    clearError,
    loginWithPassword,
    registerWithPassword,
  }),
}))

vi.mock('@modules/auth/infrastructure/public-capabilities.store', () => ({
  usePublicCapabilitiesStore: () => ({
    load: publicCapabilitiesLoad,
    registrationEnabled: true,
    capabilityChecked: true,
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
    // Check both checkboxes to pass registerSchema validation
    await wrapper.find('input#ageEligibility').setValue(true)
    await wrapper.find('input#terms').setValue(true)
    await wrapper.find('form').trigger('submit.prevent')

    // Expecting full RegisterPayload with eligibility fields
    expect(registerWithPassword).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'password123',
      confirmedAgeEligibility: true,
      acceptedTermsVersion: 'terms-v1.0.0',
    })
    expect(replace).toHaveBeenCalledWith('/')
  })

  it('shows error if passwords do not match in registration', async () => {
    routeState.name = 'register'
    const wrapper = mountAuthView()

    await wrapper.find('input#email').setValue('user@example.com')
    await wrapper.find('input#password').setValue('password123')
    await wrapper.find('input#confirmPassword').setValue('mismatch')
    // Check both checkboxes so validation reaches the password match check
    await wrapper.find('input#ageEligibility').setValue(true)
    await wrapper.find('input#terms').setValue(true)
    await wrapper.find('form').trigger('submit.prevent')

    expect(registerWithPassword).not.toHaveBeenCalled()
    // Looking for the translated key part
    expect(wrapper.text()).toContain('auth.passwordsMustMatch')
  })

  it('blocks registration when age eligibility checkbox is unchecked', async () => {
    routeState.name = 'register'
    const wrapper = mountAuthView()

    await wrapper.find('input#email').setValue('user@example.com')
    await wrapper.find('input#password').setValue('Str0ng!Pass')
    await wrapper.find('input#confirmPassword').setValue('Str0ng!Pass')
    await wrapper.find('form').trigger('submit.prevent')

    expect(registerWithPassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('auth.ageEligibilityRequired')
  })

  it('blocks registration when terms checkbox is unchecked but age eligibility is checked', async () => {
    routeState.name = 'register'
    const wrapper = mountAuthView()

    await wrapper.find('input#email').setValue('user@example.com')
    await wrapper.find('input#password').setValue('Str0ng!Pass')
    await wrapper.find('input#confirmPassword').setValue('Str0ng!Pass')
    // Check only age eligibility, leave terms unchecked
    await wrapper.find('input#ageEligibility').setValue(true)
    await wrapper.find('form').trigger('submit.prevent')

    expect(registerWithPassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('auth.termsRequired')
  })

  it('passes eligibility flags to registerWithPassword when both checkboxes are checked', async () => {
    routeState.name = 'register'
    registerWithPassword.mockResolvedValue(undefined)
    const wrapper = mountAuthView()

    await wrapper.find('input#email').setValue('user@example.com')
    await wrapper.find('input#password').setValue('Str0ng!Pass')
    await wrapper.find('input#confirmPassword').setValue('Str0ng!Pass')

    // Check both checkboxes
    await wrapper.find('input#ageEligibility').setValue(true)
    await wrapper.find('input#terms').setValue(true)

    await wrapper.find('form').trigger('submit.prevent')

    expect(registerWithPassword).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'Str0ng!Pass',
      confirmedAgeEligibility: true,
      acceptedTermsVersion: 'terms-v1.0.0',
    })
  })
})
