import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VersionBadge from './VersionBadge.vue'

describe('VersionBadge', () => {
  it('renders the deployed version and short SHA', () => {
    const wrapper = mount(VersionBadge)

    expect(wrapper.text()).toBe('v0.0.0 · test-sha')
  })

  it('exposes the build time through the title attribute', () => {
    const wrapper = mount(VersionBadge)

    expect(wrapper.attributes('title')).toBe('2026-01-01T00:00:00.000Z')
  })
})
