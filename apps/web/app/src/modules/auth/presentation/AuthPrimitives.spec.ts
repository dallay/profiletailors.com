import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AuthShell from './AuthShell.vue'
import AuthLegalLinks from './AuthLegalLinks.vue'
import PasswordField from './PasswordField.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ locale: { value: 'en' }, t: (key: string) => key }),
}))

describe('auth presentation primitives', () => {
  it('renders the adaptive shared brand asset with an accessible product name', () => {
    const wrapper = mount(AuthShell, { slots: { default: '<p>form</p>' } })
    const logo = wrapper.find('img')
    const logos = wrapper.findAll('img')
    expect(logos).toHaveLength(2)
    expect(logos.map((item) => item.attributes('data-asset'))).toEqual([
      'profiletailors-logotype.svg',
      'profiletailors-logotype-light.svg',
    ])
    expect(logo.attributes('src')).toContain('svg')
    expect(logos.every((item) => item.attributes('alt') === '')).toBe(true)
    expect(logos[0]!.classes()).toContain('dark:hidden')
    expect(logos[1]!.classes()).toContain('dark:block')
    expect(wrapper.get('[data-testid="brand-name"]').text()).toBe('Profile Tailors')
    expect(wrapper.text()).toContain('form')
  })

  it('links to the existing public legal pages', () => {
    const wrapper = mount(AuthLegalLinks)
    expect(wrapper.get('a[href="/terms"]').attributes('href')).toBe('/terms')
    expect(wrapper.get('a[href="/privacy"]').attributes('href')).toBe('/privacy')
  })

  it('announces password visibility through pressed state', async () => {
    const wrapper = mount(PasswordField, {
      props: { id: 'password', label: 'Password', modelValue: '' },
    })
    expect(wrapper.get('input').attributes('type')).toBe('password')
    expect(wrapper.get('button').attributes('aria-pressed')).toBe('false')
    await wrapper.get('button').trigger('click')
    expect(wrapper.get('input').attributes('type')).toBe('text')
    expect(wrapper.get('button').attributes('aria-pressed')).toBe('true')
  })
})
