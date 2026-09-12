import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import ResetPasswordView from './ResetPasswordView.vue'

const load = vi.hoisted(() => vi.fn())
const state = reactive({ resolved: false, passwordRecoveryEnabled: false })
const route = vi.hoisted(() => ({ query: { token: 'opaque' } as Record<string, unknown> }))
vi.mock('@modules/auth/infrastructure/public-capabilities.store', () => ({
  usePublicCapabilitiesStore: () => ({
    load,
    get resolved() {
      return state.resolved
    },
    get passwordRecoveryEnabled() {
      return state.passwordRecoveryEnabled
    },
  }),
}))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  RouterLink: { props: ['to'], template: '<a><slot /></a>' },
}))
vi.mock('vue-i18n', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-i18n')>()),
  useI18n: () => ({ locale: { value: 'en' }, t: (key: string) => key }),
}))
vi.mock('@modules/auth/infrastructure/auth-api', () => ({ resetPassword: vi.fn() }))

describe('ResetPasswordView capability gate', () => {
  beforeEach(() => {
    state.resolved = false
    state.passwordRecoveryEnabled = false
    route.query = { token: 'opaque' }
    load.mockReset()
  })

  it('waits and preserves the token-bearing route without submitting', () => {
    const wrapper = mount(ResetPasswordView)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(load).toHaveBeenCalledOnce()
    expect(route.query.token).toBe('opaque')
  })

  it('fails closed in place when recovery is unavailable', () => {
    state.resolved = true
    const wrapper = mount(ResetPasswordView)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('passwordRecovery.unavailableTitle')
  })

  it('renders the form when enabled without depending on session state', () => {
    state.resolved = true
    state.passwordRecoveryEnabled = true
    const wrapper = mount(ResetPasswordView)
    expect(wrapper.find('form').exists()).toBe(true)
  })
})
