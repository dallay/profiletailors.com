import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Button } from './index'

describe('Button primitive', () => {
  it('renders default button element with slot content', () => {
    const wrapper = mount(Button, {
      slots: { default: 'Click me' },
    })
    expect(wrapper.element.tagName).toBe('BUTTON')
    expect(wrapper.text()).toBe('Click me')
  })

  it('applies variant and size classes correctly', () => {
    const wrapper = mount(Button, {
      props: { variant: 'destructive', size: 'sm' },
      slots: { default: 'Delete' },
    })
    expect(wrapper.attributes('data-variant')).toBe('destructive')
    expect(wrapper.attributes('data-size')).toBe('sm')
  })

  it('handles disabled attribute', () => {
    const wrapper = mount(Button, {
      attrs: { disabled: true },
      slots: { default: 'Disabled' },
    })
    expect(wrapper.attributes('disabled')).toBeDefined()
  })
})
