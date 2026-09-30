import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Input } from './index'

describe('Input primitive', () => {
  it('renders input element with modelValue', () => {
    const wrapper = mount(Input, {
      props: { modelValue: 'hello' },
    })
    expect(wrapper.element.tagName).toBe('INPUT')
    expect((wrapper.element as HTMLInputElement).value).toBe('hello')
  })

  it('emits update:modelValue on input event', async () => {
    const wrapper = mount(Input, {
      props: { modelValue: '' },
    })
    const input = wrapper.find('input')
    await input.setValue('new value')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['new value'])
  })
})
