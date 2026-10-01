import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UiInput from './UiInput.vue'

describe('UiInput', () => {
  it('associates the optional visible label with the input', () => {
    const wrapper = mount(UiInput, {
      props: { label: 'Email address' },
    })

    expect(wrapper.find('label').attributes('for')).toBe(wrapper.find('input').attributes('id'))
  })

  it('preserves the input without an id when no label or id is supplied', () => {
    const wrapper = mount(UiInput)

    expect(wrapper.find('label').exists()).toBe(false)
    expect(wrapper.find('input').attributes('id')).toBeUndefined()
  })

  it('preserves an explicit input id without a label', () => {
    const wrapper = mount(UiInput, {
      props: { id: 'custom-input' },
    })

    expect(wrapper.find('input').attributes('id')).toBe('custom-input')
  })
})
