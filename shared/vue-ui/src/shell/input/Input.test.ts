import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Input from './Input.vue'

describe('Input', () => {
  it('renders model value and emits updates', async () => {
    const wrapper = mount(Input, {
      props: {
        modelValue: 'hello',
        class: 'custom-input',
        type: 'email',
        placeholder: 'Email',
      },
    })

    const input = wrapper.get('input')
    expect((input.element as HTMLInputElement).value).toBe('hello')
    expect(input.attributes('type')).toBe('email')
    expect(input.attributes('placeholder')).toBe('Email')
    expect(input.classes()).toContain('custom-input')

    await input.setValue('updated@example.com')

    expect(wrapper.emitted('update:modelValue')).toEqual([['updated@example.com']])
  })

  it('uses defaultValue when modelValue is absent', () => {
    const wrapper = mount(Input, {
      props: {
        defaultValue: 'seeded',
      },
    })

    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('seeded')
  })
})
