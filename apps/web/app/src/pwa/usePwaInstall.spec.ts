import { describe, it, expect } from 'vitest'
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

describe('usePwaInstall', () => {
  it('starts without install prompt', () => {
    const wrapper = mount(Harness)
    expect(wrapper.vm.canInstall).toBe(false)
  })
})
