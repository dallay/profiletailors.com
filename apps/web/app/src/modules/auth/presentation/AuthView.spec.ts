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

const publicCapabilitiesState = vi.hoisted(() => ({
  registrationEnabled: true,
  capabilityChecked: true,
}))

vi.mock('@modules/auth/infrastructure/public-capabilities.store', () => ({
  usePublicCapabilitiesStore: () => ({
    load: publicCapabilitiesLoad,
    get registrationEnabled() {
      return publicCapabilitiesState.registrationEnabled
    },
    get capabilityChecked() {
      return publicCapabilitiesState.capabilityChecked
    },
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
          template: '<button v-bind="$attrs"><slot /></button>',
        },
        RouterLink: {
          props: ['to'],
          template: '<a :href="to"><slot /></a>',
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
    publicCapabilitiesState.registrationEnabled = true
    publicCapabilitiesState.capabilityChecked = true
    replace.mockReset()
    clearError.mockReset()
    loginWithPassword.mockReset()
    registerWithPassword.mockReset()
    publicCapabilitiesLoad.mockReset()
  })

  it('renders a native sign-in form that supports password managers and Enter submission', async (): Promise<void> => {
    loginWithPassword.mockResolvedValue(undefined)
    const wrapper = mountAuthView()

    const form = wrapper.find('form')
    const emailInput = wrapper.find('input#email')
    const passwordInput = wrapper.find('input#password')
    const submitButton = wrapper.find('button[type="submit"]')
    const toggleButton = wrapper.find('button[aria-label="Show"]')

    expect(form.exists()).toBe(true)
    expect(wrapper.find('label[for="email"]').exists()).toBe(true)
    expect(emailInput.attributes('type')).toBe('email')
    expect(emailInput.attributes('autocomplete')).toBe('username')
    expect(wrapper.find('label[for="password"]').exists()).toBe(true)
    expect(passwordInput.attributes('type')).toBe('password')
    expect(passwordInput.attributes('autocomplete')).toBe('current-password')
    expect(submitButton.text()).toBe('auth.submitLogin')
    expect(toggleButton.attributes('type')).toBe('button')

    await emailInput.setValue('user@example.com')
    await passwordInput.setValue('Str0ng!Pass')
    await form.trigger('submit')

    expect(loginWithPassword).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'Str0ng!Pass',
    })
  })

  it('shows a keyboard-reachable forgot password link only in login mode', () => {
    const login = mountAuthView()
    expect(login.find('a[href="/forgot-password"]').text()).toBe('auth.forgotPassword')

    routeState.name = 'register'
    const register = mountAuthView()
    expect(register.find('a[href="/forgot-password"]').exists()).toBe(false)
  })

  it('places federated sign-in buttons above the sign-in method divider', (): void => {
    const wrapper = mountAuthView()
    const text = wrapper.find('form').text()

    expect(text.indexOf('Continue with Google')).toBeLessThan(text.indexOf('or'))
    expect(text.indexOf('Continue with Apple')).toBeLessThan(text.indexOf('or'))
    expect(wrapper.find('[data-testid="sign-in-method-divider"]').text()).toBe('or')
  })

  it('toggles password visibility without submitting the form', async (): Promise<void> => {
    const wrapper = mountAuthView()
    const toggleButton = wrapper.find('button[aria-label="Show"]')

    await toggleButton.trigger('click')

    expect(wrapper.find('input#password').attributes('type')).toBe('text')
    expect(wrapper.find('button[aria-label="Hide"]').attributes('aria-pressed')).toBe('true')
    expect(loginWithPassword).not.toHaveBeenCalled()
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

describe('AuthView capability loading', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeState.name = 'login'
    routeState.query = {}
    publicCapabilitiesState.registrationEnabled = true
    publicCapabilitiesState.capabilityChecked = true
    replace.mockReset()
    clearError.mockReset()
    loginWithPassword.mockReset()
    registerWithPassword.mockReset()
    publicCapabilitiesLoad.mockReset()
  })

  it('loads capabilities on mount in login mode', () => {
    routeState.name = 'login'
    mountAuthView()

    expect(publicCapabilitiesLoad).toHaveBeenCalledOnce()
  })

  it('loads capabilities on mount in registration mode', () => {
    routeState.name = 'register'
    mountAuthView()

    expect(publicCapabilitiesLoad).toHaveBeenCalledOnce()
  })

  it('shows registration link in login mode when capabilities checked and enabled', () => {
    routeState.name = 'login'
    publicCapabilitiesState.capabilityChecked = true
    publicCapabilitiesState.registrationEnabled = true
    const wrapper = mountAuthView()

    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
  })

  it('hides registration link in login mode when capabilities not yet checked', () => {
    routeState.name = 'login'
    publicCapabilitiesState.capabilityChecked = false
    publicCapabilitiesState.registrationEnabled = true
    const wrapper = mountAuthView()

    const spans = wrapper.findAll('span')
    const closedMessage = spans.find((s) => s.text().includes('auth.registrationClosed'))
    expect(closedMessage).toBeDefined()
  })

  it('hides registration link in login mode when capabilities checked but disabled', () => {
    routeState.name = 'login'
    publicCapabilitiesState.capabilityChecked = true
    publicCapabilitiesState.registrationEnabled = false
    const wrapper = mountAuthView()

    const spans = wrapper.findAll('span')
    const closedMessage = spans.find((s) => s.text().includes('auth.registrationClosed'))
    expect(closedMessage).toBeDefined()
  })

  it('shows back-to-login link in registration mode regardless of capability state', () => {
    routeState.name = 'register'
    publicCapabilitiesState.capabilityChecked = false
    publicCapabilitiesState.registrationEnabled = false
    const wrapper = mountAuthView()

    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
  })
})
