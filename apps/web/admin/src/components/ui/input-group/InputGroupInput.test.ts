import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import InputGroupInput from './InputGroupInput.vue'

describe('InputGroupInput', () => {
  it('renders input element and forwards practical attrs/classes', () => {
    const wrapper = mount(InputGroupInput, {
      props: { class: 'custom-input-group' },
      attrs: {
        placeholder: 'Channel name',
        type: 'text',
      },
    })

    const input = wrapper.get('input')
    expect(input.classes()).toContain('custom-input-group')
    expect(input.attributes('placeholder')).toBe('Channel name')
    expect(input.attributes('type')).toBe('text')
  })
})
