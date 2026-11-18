import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import LoginForm from './LoginForm.vue'

const startBffLogin = vi.hoisted(() => vi.fn())
vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({ startBffLogin }),
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

describe('LoginForm', () => {
  beforeEach(() => startBffLogin.mockClear())

  it('offers BFF login without collecting local credentials', async () => {
    const wrapper = mount(LoginForm)
    expect(wrapper.find('input').exists()).toBe(false)
    expect(wrapper.get('button').text()).toBe('auth.submitLogin')

    await wrapper.get('button').trigger('click')

    expect(startBffLogin).toHaveBeenCalledOnce()
    expect(startBffLogin).toHaveBeenCalledWith()
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })

  it('prevents duplicate login initiation while navigation is pending', async () => {
    const wrapper = mount(LoginForm)

    await wrapper.get('button').trigger('click')
    await wrapper.get('button').trigger('click')

    expect(startBffLogin).toHaveBeenCalledOnce()
  })
})
