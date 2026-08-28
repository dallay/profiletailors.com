import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import AuthView from './AuthView.vue'

const routeState = vi.hoisted(() => ({ name: 'login', query: {} as Record<string, unknown> }))
const route = reactive(routeState)
const replace = vi.hoisted(() => vi.fn())
const load = vi.hoisted(() => vi.fn())
const capabilityState = vi.hoisted(() => ({
  resolved: false,
  registrationEnabled: false,
  invitationAcceptanceEnabled: false,
}))
const capabilities = reactive(capabilityState)
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ replace }) }))
vi.mock('@modules/auth/infrastructure/public-capabilities.store', () => ({
  usePublicCapabilitiesStore: () => ({
    load,
    passwordRecoveryEnabled: false,
    get resolved() {
      return capabilities.resolved
    },
    get registrationEnabled() {
      return capabilities.registrationEnabled
    },
    get invitationAcceptanceEnabled() {
      return capabilities.invitationAcceptanceEnabled
    },
  }),
}))
vi.mock('vue-i18n', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))

function mountView() {
  return mount(AuthView, {
    global: {
      stubs: {
        LoginForm: {
          props: ['email'],
          emits: ['update:email', 'success'],
          template:
            '<div data-testid="login"><button @click="$emit(\'update:email\', \'kept@example.com\')">email</button></div>',
        },
        RegisterForm: {
          props: ['email'],
          template: '<div data-testid="register">{{ email }}</div>',
        },
        AuthShell: { template: '<main><slot /></main>' },
        RegistrationUnavailable: {
          template: '<div data-testid="unavailable">Registration is currently unavailable</div>',
        },
        RouterLink: true,
      },
    },
  })
}

describe('AuthView orchestration', () => {
  beforeEach(() => {
    route.name = 'login'
    route.query = {}
    capabilities.resolved = false
    capabilities.registrationEnabled = false
    capabilities.invitationAcceptanceEnabled = false
    load.mockReset()
    replace.mockReset()
  })

  it('renders login immediately without waiting for capabilities', () => {
    const wrapper = mountView()
    expect(wrapper.find('[data-testid="login"]').exists()).toBe(true)
    expect(load).toHaveBeenCalledOnce()
  })

  it('fails closed in place on register after capabilities resolve', async () => {
    route.name = 'register'
    capabilities.resolved = true
    const wrapper = mountView()
    expect(wrapper.find('[data-testid="unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register"]').exists()).toBe(false)
  })

  it('preserves only email when switching from login to registration', async () => {
    capabilities.resolved = true
    capabilities.registrationEnabled = true
    const wrapper = mountView()
    await wrapper.get('button').trigger('click')
    route.name = 'register'
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="register"]').text()).toBe('kept@example.com')
  })

  it('allows invitation registration while public registration is disabled', () => {
    route.name = 'register'
    route.query = { invitationToken: 'raw-token' }
    capabilities.resolved = true
    capabilities.invitationAcceptanceEnabled = true
    const wrapper = mountView()
    expect(wrapper.find('[data-testid="register"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unavailable"]').exists()).toBe(false)
  })
})
