import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VersionBadge from './VersionBadge.vue'

describe('VersionBadge', () => {
  it('renders the deployed version and the short SHA', () => {
    const wrapper = mount(VersionBadge)
    expect(wrapper.text()).toBe('v0.3.9 · a1b2c3d')
  })

  it('exposes the build time through the title attribute', () => {
    const wrapper = mount(VersionBadge)
    expect(wrapper.attributes('title')).toBe('2026-09-18T16:00:00.000Z')
  })

  it('renders nothing interactive', () => {
    const wrapper = mount(VersionBadge)
    expect(wrapper.element.tagName).toBe('SPAN')
    expect(wrapper.attributes('role')).toBeUndefined()
  })
})
