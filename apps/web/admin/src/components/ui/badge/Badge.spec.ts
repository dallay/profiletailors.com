import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Badge } from './index'

describe('Badge primitive', () => {
  it('renders badge content', () => {
    const wrapper = mount(Badge, {
      slots: { default: 'Active' },
    })
    expect(wrapper.text()).toBe('Active')
  })

  it('supports destructive variant', () => {
    const wrapper = mount(Badge, {
      props: { variant: 'destructive' },
      slots: { default: 'Disabled' },
    })
    expect(wrapper.text()).toBe('Disabled')
  })
})
