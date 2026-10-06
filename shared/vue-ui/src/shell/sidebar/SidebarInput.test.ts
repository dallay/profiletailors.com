import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SidebarInput from './SidebarInput.vue'

describe('SidebarInput', () => {
  it('renders input element and forwards practical attrs/classes', () => {
    const wrapper = mount(SidebarInput, {
      props: { class: 'custom-sidebar-input' },
      attrs: {
        id: 'sidebar-search',
        'aria-label': 'Search navigation',
        placeholder: 'Search',
        disabled: true,
        type: 'text',
      },
    })

    const input = wrapper.get('input')
    expect(input.attributes('id')).toBe('sidebar-search')
    expect(input.attributes('aria-label')).toBe('Search navigation')
    expect(input.attributes('placeholder')).toBe('Search')
    expect(input.attributes('disabled')).toBeDefined()
    expect(input.attributes('type')).toBe('text')
    expect(input.classes()).toContain('custom-sidebar-input')
  })
})
