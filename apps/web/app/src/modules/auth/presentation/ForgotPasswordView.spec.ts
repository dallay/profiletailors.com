import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ForgotPasswordView from './ForgotPasswordView.vue'

const requestPasswordReset = vi.hoisted(() => vi.fn())

vi.mock('@modules/auth/infrastructure/auth-api', () => ({ requestPasswordReset }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

function mountView() {
  return mount(ForgotPasswordView, {
    global: {
      mocks: { $t: (key: string) => key },
      stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } },
    },
  })
}

describe('ForgotPasswordView', () => {
  beforeEach(() => requestPasswordReset.mockReset())

  it('validates and normalizes email before submission', async () => {
    const wrapper = mountView()
    await wrapper.find('input[type="email"]').setValue('bad-email')
    await wrapper.find('form').trigger('submit.prevent')
    expect(requestPasswordReset).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('passwordRecovery.invalidEmail')

    requestPasswordReset.mockResolvedValue(undefined)
    await wrapper.find('input[type="email"]').setValue('  User@Example.COM  ')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(requestPasswordReset).toHaveBeenCalledWith('user@example.com')
    expect(wrapper.text()).toContain('passwordRecovery.forgotSuccessMessage')
  })

  it('locks duplicate submissions while pending', async () => {
    let resolveRequest!: () => void
    requestPasswordReset.mockReturnValue(
      new Promise<void>((resolve) => {
        resolveRequest = resolve
      }),
    )
    const wrapper = mountView()
    await wrapper.find('input[type="email"]').setValue('user@example.com')
    await wrapper.find('form').trigger('submit.prevent')
    await wrapper.find('form').trigger('submit.prevent')
    expect(requestPasswordReset).toHaveBeenCalledOnce()
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
    resolveRequest()
    await flushPromises()
  })

  it.each([
    [{ status: 429, code: 'AUTH_RATE_LIMIT_EXCEEDED' }, 'passwordRecovery.rateLimited'],
    [{ status: 503, code: 'PASSWORD_RECOVERY_DISABLED' }, 'passwordRecovery.unavailable'],
    [new Error('network'), 'passwordRecovery.genericError'],
  ])('maps safe API failures', async (error, message) => {
    requestPasswordReset.mockRejectedValue(error)
    const wrapper = mountView()
    await wrapper.find('input[type="email"]').setValue('user@example.com')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain(message)
  })
})
