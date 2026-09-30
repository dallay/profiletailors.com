import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SidebarInput from './SidebarInput.vue'

describe('SidebarInput', () => {
  it('renders input element and forwards practical attrs/classes', () => {
    const wrapper = mount(SidebarInput, {
      props: { class: 'custom-sidebar-input' },
      attrs: {
        placeholder: 'Search',
        disabled: true,
        type: 'text',
      },
    })

    const input = wrapper.get('input')
    expect(input.attributes('placeholder')).toBe('Search')
    expect(input.attributes('disabled')).toBeDefined()
    expect(input.attributes('type')).toBe('text')
    expect(input.classes()).toContain('custom-sidebar-input')
  })
})
