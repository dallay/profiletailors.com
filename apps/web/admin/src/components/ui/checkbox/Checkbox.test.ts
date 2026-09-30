import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Checkbox from './Checkbox.vue'

describe('Checkbox.vue', () => {
  it('renders correctly', () => {
    const wrapper = mount(Checkbox)
    expect(wrapper.exists()).toBe(true)
  })
})
