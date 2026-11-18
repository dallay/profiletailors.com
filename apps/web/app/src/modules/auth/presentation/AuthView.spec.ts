import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import AuthView from './AuthView.vue'

const routeState = vi.hoisted(() => ({ name: 'login', query: {} as Record<string, unknown> }))
const route = reactive(routeState)
const load = vi.hoisted(() => vi.fn())
const capabilityState = vi.hoisted(() => ({
  resolved: false,
  registrationEnabled: false,
  invitationAcceptanceEnabled: false,
}))
const capabilities = reactive(capabilityState)
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({}) }))
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
type VueI18nModule = typeof import('vue-i18n')
type ImportOriginal = <T extends VueI18nModule = VueI18nModule>() => Promise<T>
type I18nMock = { useI18n: () => { t: (key: string) => string } }

vi.mock(
  'vue-i18n',
  async (importOriginal: ImportOriginal): Promise<I18nMock> => ({
    ...(await importOriginal()),
    useI18n: (): { t: (key: string) => string } => ({
      t: (key: string): string => key,
    }),
  }),
)

function mountView(): ReturnType<typeof mount> {
  return mount(AuthView, {
    global: {
      stubs: {
        LoginForm: {
          template: '<div data-testid="login"><button>Continue with pt-app</button></div>',
        },
        RegisterForm: {
          name: 'RegisterForm',
          props: ['email', 'invitationToken'],
          template:
            '<div data-testid="register" :data-invitation-token="invitationToken">{{ email }}</div>',
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
  })

  it('renders login immediately without waiting for capabilities', (): void => {
    const wrapper = mountView()
    expect(wrapper.find('[data-testid="login"]').exists()).toBe(true)
    expect(load).toHaveBeenCalledOnce()
  })

  it('fails closed in place on register after capabilities resolve', (): void => {
    route.name = 'register'
    capabilities.resolved = true
    const wrapper = mountView()
    expect(wrapper.find('[data-testid="unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register"]').exists()).toBe(false)
  })

  it('renders BFF login as a separate flow from invitation registration', async (): Promise<void> => {
    capabilities.resolved = true
    capabilities.registrationEnabled = true
    const wrapper = mountView()
    route.name = 'register'
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="register"]').exists()).toBe(true)
  })

  it('allows invitation registration while public registration is disabled', (): void => {
    route.name = 'register'
    route.query = { invitationToken: 'raw-token' }
    capabilities.resolved = true
    capabilities.invitationAcceptanceEnabled = true
    const wrapper = mountView()
    expect(wrapper.find('[data-testid="register"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unavailable"]').exists()).toBe(false)
    expect(wrapper.getComponent({ name: 'RegisterForm' }).props('invitationToken')).toBe(
      'raw-token',
    )
  })
})
