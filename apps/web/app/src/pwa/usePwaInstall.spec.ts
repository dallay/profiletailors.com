import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { usePwaInstall } from './usePwaInstall'

const IPHONE_SAFARI_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1'
const MAC_SAFARI_UA =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15'

const Harness = defineComponent({
  setup() {
    const { canInstall, install, showIosGuide } = usePwaInstall()
    return { canInstall, install, showIosGuide }
  },
  template: '<div />',
})

function installPromptEvent(): Event {
  let resolveUserChoice!: (value: { outcome: 'accepted' | 'dismissed' }) => void
  const mockEvent = Object.assign(new Event('beforeinstallprompt'), {
    prompt: async () => {},
    userChoice: new Promise<{ outcome: 'accepted' | 'dismissed' }>((resolve) => {
      resolveUserChoice = resolve
    }),
  })
  ;(mockEvent as { _resolveUserChoice?: typeof resolveUserChoice })._resolveUserChoice =
    resolveUserChoice
  return mockEvent
}

describe('usePwaInstall', () => {
  const wrappers: VueWrapper[] = []

  beforeEach(() => {
    Object.defineProperty(window.navigator, 'userAgent', {
      value: 'jsdom',
      configurable: true,
    })
    Object.defineProperty(window.navigator, 'platform', { value: '', configurable: true })
    if ('standalone' in navigator)
      Reflect.deleteProperty(navigator as Record<string, unknown>, 'standalone')
  })

  afterEach(() => {
    while (wrappers.length) wrappers.pop()!.unmount()
  })

  function mountHarness(): VueWrapper {
    const wrapper = mount(Harness)
    wrappers.push(wrapper)
    return wrapper
  }

  it('starts without an install prompt', () => {
    const wrapper = mountHarness()
    expect(wrapper.vm.canInstall).toBe(false)
  })

  it('exposes install when beforeinstallprompt fires', async () => {
    const wrapper = mountHarness()
    window.dispatchEvent(installPromptEvent())
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.canInstall).toBe(true)
  })

  it('runs the browser install flow and clears the prompt', async () => {
    const wrapper = mountHarness()
    const event = installPromptEvent()
    window.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.canInstall).toBe(true)
    const resolveUserChoice = (event as { _resolveUserChoice?: (v: { outcome: string }) => void })
      ._resolveUserChoice
    const installPromise = wrapper.vm.install()
    resolveUserChoice!({ outcome: 'accepted' })
    await installPromise
    expect(wrapper.vm.canInstall).toBe(false)
  })

  it('resolves quietly when install is called with no pending prompt', async () => {
    const wrapper = mountHarness()
    await expect(wrapper.vm.install()).resolves.toBeUndefined()
  })

  it('ignores events without the install payload', async () => {
    const wrapper = mountHarness()
    window.dispatchEvent(new Event('beforeinstallprompt'))
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.canInstall).toBe(false)
  })

  it('shows the iOS guide on iPhone Safari', () => {
    Object.defineProperty(window.navigator, 'userAgent', {
      value: IPHONE_SAFARI_UA,
      configurable: true,
    })
    const wrapper = mountHarness()
    expect(wrapper.vm.showIosGuide).toBe(true)
  })

  it('does not show the iOS guide when already installed standalone', () => {
    Object.defineProperty(window.navigator, 'userAgent', {
      value: IPHONE_SAFARI_UA,
      configurable: true,
    })
    Object.defineProperty(navigator as Record<string, unknown>, 'standalone', {
      value: true,
      configurable: true,
    })
    const wrapper = mountHarness()
    expect(wrapper.vm.showIosGuide).toBe(false)
  })

  it('does not show the iOS guide on desktop Mac Safari', () => {
    Object.defineProperty(window.navigator, 'userAgent', {
      value: MAC_SAFARI_UA,
      configurable: true,
    })
    Object.defineProperty(window.navigator, 'platform', { value: 'MacIntel', configurable: true })
    const wrapper = mountHarness()
    expect(wrapper.vm.showIosGuide).toBe(false)
  })
})
