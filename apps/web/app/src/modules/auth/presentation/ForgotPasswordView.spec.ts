import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import ForgotPasswordView from './ForgotPasswordView.vue'

const load = vi.hoisted(() => vi.fn())
const state = reactive({ resolved: false, passwordRecoveryEnabled: false })
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
vi.mock('vue-i18n', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-i18n')>()),
  useI18n: () => ({ locale: { value: 'en' }, t: (key: string) => key }),
}))

describe('ForgotPasswordView capability gate', () => {
  beforeEach(() => {
    state.resolved = false
    state.passwordRecoveryEnabled = false
    load.mockReset()
  })

  it('waits before rendering a recovery form', () => {
    const wrapper = mount(ForgotPasswordView, { global: { stubs: { RouterLink: true } } })
    expect(wrapper.find('form').exists()).toBe(false)
    expect(load).toHaveBeenCalledOnce()
  })

  it('fails closed in place after capability failure or disablement', async () => {
    state.resolved = true
    const wrapper = mount(ForgotPasswordView, { global: { stubs: { RouterLink: true } } })
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('passwordRecovery.unavailableTitle')
  })
})
