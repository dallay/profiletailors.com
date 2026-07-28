import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ResetPasswordView from './ResetPasswordView.vue'

const resetPassword = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({
  query: { token: 'valid-capability' } as Record<string, unknown>,
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({ resetPassword }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

function mountView() {
  return mount(ResetPasswordView, { global: { mocks: { $t: (key: string) => key } } })
}

async function fillAndSubmit(wrapper: ReturnType<typeof mountView>) {
  await wrapper.find('input#new-password').setValue('NewPassword123!')
  await wrapper.find('input#confirm-new-password').setValue('NewPassword123!')
  await wrapper.find('form').trigger('submit.prevent')
}

describe('ResetPasswordView', () => {
  beforeEach(() => {
    resetPassword.mockReset()
    route.query = { token: 'valid-capability' }
  })

  it.each([{}, { token: '' }, { token: ['one', 'two'] }])(
    'shows one invalid link state for unusable query token',
    (query) => {
      route.query = query
      const wrapper = mountView()
      expect(wrapper.find('form').exists()).toBe(false)
      expect(wrapper.text()).toContain('passwordRecovery.invalidLinkMessage')
      expect(wrapper.find('a[href="/forgot-password"]').exists()).toBe(true)
    },
  )

  it('enforces password policy and confirmation before submission', async () => {
    const wrapper = mountView()
    await wrapper.find('input#new-password').setValue('short')
    await wrapper.find('input#confirm-new-password').setValue('different')
    await wrapper.find('form').trigger('submit.prevent')
    expect(resetPassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('passwordRecovery.passwordTooShort')
    expect(wrapper.text()).toContain('passwordRecovery.passwordsMustMatch')
  })

  it('locks duplicate submissions and shows terminal success without auto-login', async () => {
    let resolveRequest!: () => void
    resetPassword.mockReturnValue(
      new Promise<void>((resolve) => {
        resolveRequest = resolve
      }),
    )
    const wrapper = mountView()
    await fillAndSubmit(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    expect(resetPassword).toHaveBeenCalledOnce()
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
    resolveRequest()
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('passwordRecovery.resetSuccessMessage')
    expect(wrapper.find('a[href="/login"]').exists()).toBe(true)
  })

  it.each([
    'INVALID_PASSWORD_RESET_TOKEN',
    'EXPIRED_PASSWORD_RESET_TOKEN',
    'USED_PASSWORD_RESET_TOKEN',
  ])('maps %s to the same invalid link state without backend detail', async (code) => {
    resetPassword.mockRejectedValue({ status: 400, code, detail: `secret detail ${code}` })
    const wrapper = mountView()
    await fillAndSubmit(wrapper)
    await flushPromises()
    expect(wrapper.text()).toContain('passwordRecovery.invalidLinkMessage')
    expect(wrapper.text()).not.toContain('secret detail')
    expect(wrapper.find('a[href="/forgot-password"]').exists()).toBe(true)
  })

  describe('reset unavailable / throttled error branches', () => {
    it('maps reset 429/AUTH_RATE_LIMIT_EXCEEDED to rate-limited message without backend detail', async () => {
      resetPassword.mockRejectedValue({
        status: 429,
        code: 'AUTH_RATE_LIMIT_EXCEEDED',
        detail: 'backend rate detail',
      })
      const wrapper = mountView()
      await fillAndSubmit(wrapper)
      await flushPromises()
      // Form remains visible (not invalid-link state)
      expect(wrapper.find('form').exists()).toBe(true)
      // Safe UI message shown via role=alert
      const alert = wrapper.find('[role="alert"]')
      expect(alert.exists()).toBe(true)
      expect(alert.text()).toContain('passwordRecovery.rateLimited')
      // No backend detail leaked
      expect(wrapper.text()).not.toContain('backend rate detail')
      expect(wrapper.text()).not.toContain('429')
    })

    it('maps reset 503/PASSWORD_RECOVERY_DISABLED to unavailable message without backend detail', async () => {
      resetPassword.mockRejectedValue({
        status: 503,
        code: 'PASSWORD_RECOVERY_DISABLED',
        detail: 'backend disabled detail',
      })
      const wrapper = mountView()
      await fillAndSubmit(wrapper)
      await flushPromises()
      // Form remains visible (not invalid-link state)
      expect(wrapper.find('form').exists()).toBe(true)
      const alert = wrapper.find('[role="alert"]')
      expect(alert.exists()).toBe(true)
      expect(alert.text()).toContain('passwordRecovery.unavailable')
      // No backend detail leaked
      expect(wrapper.text()).not.toContain('backend disabled detail')
      expect(wrapper.text()).not.toContain('503')
    })

    it('maps reset unknown/network error to generic safe message without backend detail', async () => {
      resetPassword.mockRejectedValue(new Error('Network request failed'))
      const wrapper = mountView()
      await fillAndSubmit(wrapper)
      await flushPromises()
      // Form remains visible (not invalid-link state)
      expect(wrapper.find('form').exists()).toBe(true)
      const alert = wrapper.find('[role="alert"]')
      expect(alert.exists()).toBe(true)
      expect(alert.text()).toContain('passwordRecovery.genericError')
      // Internal error detail not shown
      expect(wrapper.text()).not.toContain('Network request failed')
    })
  })
})
