import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import RegisterForm from './RegisterForm.vue'

const registerWithPassword = vi.hoisted(() => vi.fn())
const clearError = vi.hoisted(() => vi.fn())
vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({ registerWithPassword, clearError, error: null }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

function mountForm() {
  return mount(RegisterForm, {
    attachTo: document.body,
    props: { email: '' },
    global: { stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } } },
  })
}

async function setEmail(wrapper: ReturnType<typeof mountForm>, email: string) {
  await wrapper.get('input[type="email"]').setValue(email)
  await wrapper.setProps({ email })
}

describe('RegisterForm', () => {
  beforeEach(() => {
    registerWithPassword.mockReset()
    document.body.innerHTML = ''
  })

  it('submits registration once and disables mutable navigation while pending', async () => {
    let resolve!: () => void
    registerWithPassword.mockReturnValue(
      new Promise<void>((done) => {
        resolve = done
      }),
    )
    const wrapper = mountForm()
    await setEmail(wrapper, 'user@example.com')
    const passwords = wrapper.findAll('input[type="password"]')
    await passwords[0]!.setValue('Str0ng!Passw0rd')
    await passwords[1]!.setValue('Str0ng!Passw0rd')
    await wrapper.get('#ageEligibility').setValue(true)
    await wrapper.get('#terms').setValue(true)
    await wrapper.get('form').trigger('submit.prevent')
    await wrapper.get('form').trigger('submit.prevent')
    expect(registerWithPassword).toHaveBeenCalledOnce()
    expect(wrapper.get('form').attributes('aria-busy')).toBe('true')
    expect(wrapper.get('[data-testid="login-navigation"]').attributes('aria-disabled')).toBe('true')
    resolve()
    await flushPromises()
  })

  it('focuses the actual first invalid registration field', async () => {
    const wrapper = mountForm()
    await setEmail(wrapper, 'user@example.com')
    await wrapper.get('form').trigger('submit.prevent')

    const password = wrapper.get('#register-password')
    expect(password.attributes('aria-invalid')).toBe('true')
    expect(document.activeElement).toBe(password.element)
    expect(registerWithPassword).not.toHaveBeenCalled()
  })

  it('removes mutable navigation while registration is pending', async () => {
    registerWithPassword.mockReturnValue(new Promise<void>(() => undefined))
    const wrapper = mountForm()
    await setEmail(wrapper, 'user@example.com')
    const passwords = wrapper.findAll('input[type="password"]')
    await passwords[0]!.setValue('Str0ng!Passw0rd')
    await passwords[1]!.setValue('Str0ng!Passw0rd')
    await wrapper.get('#ageEligibility').setValue(true)
    await wrapper.get('#terms').setValue(true)
    await wrapper.get('form').trigger('submit.prevent')

    const navigation = wrapper.get('[data-testid="login-navigation"]')
    expect(navigation.element.tagName).not.toBe('A')
    expect(navigation.attributes('aria-disabled')).toBe('true')
  })

  it('keeps passwords and consent local so unmounting clears them', async () => {
    const first = mountForm()
    await setEmail(first, 'kept@example.com')
    const passwords = first.findAll('input[type="password"]')
    await passwords[0]!.setValue('Secret123!')
    await first.get('#terms').setValue(true)
    first.unmount()

    const second = mount(RegisterForm, {
      props: { email: 'kept@example.com' },
      global: { stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } } },
    })
    expect((second.get('input[type="email"]').element as HTMLInputElement).value).toBe(
      'kept@example.com',
    )
    expect((second.findAll('input[type="password"]')[0]!.element as HTMLInputElement).value).toBe(
      '',
    )
    expect((second.get('#terms').element as HTMLInputElement).checked).toBe(false)
  })
})
