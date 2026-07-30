import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import LoginForm from './LoginForm.vue'

const loginWithPassword = vi.hoisted(() => vi.fn())
const clearError = vi.hoisted(() => vi.fn())
vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({ loginWithPassword, clearError, error: 'auth.genericError' }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

function mountForm() {
  return mount(LoginForm, {
    attachTo: document.body,
    props: { email: '' },
    global: { stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } } },
  })
}

async function setEmail(wrapper: ReturnType<typeof mountForm>, email: string) {
  await wrapper.get('input[type="email"]').setValue(email)
  await wrapper.setProps({ email })
}

describe('LoginForm', () => {
  beforeEach(() => {
    loginWithPassword.mockReset()
    clearError.mockReset()
    document.body.innerHTML = ''
  })

  it('focuses the first invalid field and exposes field descriptions', async () => {
    const wrapper = mountForm()
    await wrapper.get('form').trigger('submit.prevent')
    const email = wrapper.get('input[type="email"]')
    expect(email.attributes('aria-invalid')).toBe('true')
    expect(email.attributes('aria-describedby')).toBe('login-email-error')
    expect(document.activeElement).toBe(email.element)
    expect(loginWithPassword).not.toHaveBeenCalled()
    expect(wrapper.find('#login-email-error[role="alert"]').exists()).toBe(false)
  })

  it('prevents duplicates, marks busy, and makes fields readonly while pending', async () => {
    let resolve!: () => void
    loginWithPassword.mockReturnValue(
      new Promise<void>((done) => {
        resolve = done
      }),
    )
    const wrapper = mountForm()
    await setEmail(wrapper, 'user@example.com')
    await wrapper.get('input[type="password"]').setValue('Str0ng!Pass')
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('form').trigger('submit.prevent')
    expect(loginWithPassword).toHaveBeenCalledOnce()
    expect(wrapper.get('form').attributes('aria-busy')).toBe('true')
    expect(wrapper.get('input[type="email"]').attributes('readonly')).toBeDefined()
    expect(wrapper.get('input[type="password"]').attributes('readonly')).toBeDefined()
    resolve()
    await flushPromises()
  })

  it('removes forgot-password navigation while login is pending', async () => {
    loginWithPassword.mockReturnValue(new Promise<void>(() => undefined))
    const wrapper = mount(LoginForm, {
      attachTo: document.body,
      props: { email: '', showForgotPassword: true },
      global: { stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } } },
    })
    await setEmail(wrapper, 'user@example.com')
    await wrapper.get('input[type="password"]').setValue('Str0ng!Pass')
    await wrapper.get('form').trigger('submit.prevent')

    const navigation = wrapper.get('[data-testid="forgot-password-navigation"]')
    expect(navigation.element.tagName).not.toBe('A')
    expect(navigation.attributes('aria-disabled')).toBe('true')
  })

  it('focuses a generic retryable form alert after authentication failure', async () => {
    loginWithPassword.mockRejectedValue(new Error('raw detail'))
    const wrapper = mountForm()
    await setEmail(wrapper, 'user@example.com')
    await wrapper.get('input[type="password"]').setValue('WrongPassword!')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toBe('auth.genericError')
    expect(alert.text()).not.toContain('raw detail')
    expect(document.activeElement).toBe(alert.element)
  })
})
