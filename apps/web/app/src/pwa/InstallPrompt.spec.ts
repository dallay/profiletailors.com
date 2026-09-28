import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { ref } from 'vue'
import InstallPrompt from './InstallPrompt.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const canInstallRef = ref(false)
const showIosGuideRef = ref(false)
const mockInstall = vi.fn()

vi.mock('./usePwaInstall', () => ({
  usePwaInstall: () => ({
    canInstall: canInstallRef,
    install: mockInstall,
    showIosGuide: showIosGuideRef,
  }),
}))

describe('InstallPrompt', () => {
  const wrappers: VueWrapper[] = []

  beforeEach(() => {
    canInstallRef.value = false
    showIosGuideRef.value = false
    mockInstall.mockClear()
  })

  afterEach(() => {
    while (wrappers.length) wrappers.pop()!.unmount()
  })

  function mountPrompt(): VueWrapper {
    const wrapper = mount(InstallPrompt)
    wrappers.push(wrapper)
    return wrapper
  }

  it('renders nothing when neither canInstall nor showIosGuide is true', () => {
    const wrapper = mountPrompt()
    expect(wrapper.html()).not.toContain('pwa.install.cta')
    expect(wrapper.html()).not.toContain('pwa.install.iosGuide')
  })

  it('shows the install button when the browser fires beforeinstallprompt', async () => {
    canInstallRef.value = true
    const wrapper = mountPrompt()
    await wrapper.vm.$nextTick()
    expect(wrapper.html()).toContain('pwa.install.cta')
  })

  it('shows the iOS guide text when the device is iOS Safari', async () => {
    showIosGuideRef.value = true
    const wrapper = mountPrompt()
    await wrapper.vm.$nextTick()
    expect(wrapper.html()).toContain('pwa.install.iosGuide')
  })

  it('calls install when the button is clicked', async () => {
    canInstallRef.value = true
    const wrapper = mountPrompt()
    await wrapper.vm.$nextTick()

    await wrapper.findAll('button')[0]!.trigger('click')
    expect(mockInstall).toHaveBeenCalledOnce()
  })

  it('shows both the button and the iOS guide when applicable', async () => {
    canInstallRef.value = true
    showIosGuideRef.value = true
    const wrapper = mountPrompt()
    await wrapper.vm.$nextTick()
    expect(wrapper.html()).toContain('pwa.install.cta')
    expect(wrapper.html()).toContain('pwa.install.iosGuide')
  })
})
