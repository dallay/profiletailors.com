import { afterEach, describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { usePwaInstall } from './usePwaInstall'

const Harness = defineComponent({
  setup() {
    const { canInstall, showIosGuide } = usePwaInstall()
    return { canInstall, showIosGuide }
  },
  template: '<div />',
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('usePwaInstall', () => {
  it('starts without install prompt', () => {
    const wrapper = mount(Harness)
    expect(wrapper.vm.canInstall).toBe(false)
    wrapper.unmount()
  })

  it('shows the install guide for desktop-mode iPadOS Safari', () => {
    vi.stubGlobal('navigator', {
      userAgent:
        'Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15',
      platform: 'MacIntel',
      maxTouchPoints: 5,
      standalone: false,
    })

    const wrapper = mount(Harness)
    expect(wrapper.vm.showIosGuide).toBe(true)
    wrapper.unmount()
  })
})
