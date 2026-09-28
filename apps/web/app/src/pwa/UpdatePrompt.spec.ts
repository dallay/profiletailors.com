import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import UpdatePrompt from './UpdatePrompt.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const updatePwa = vi.fn()

vi.mock('./registerPwa', () => ({
  updatePwa: (...args: unknown[]) => updatePwa(...args),
}))

describe('UpdatePrompt', () => {
  const wrappers: VueWrapper[] = []

  beforeEach(() => {
    updatePwa.mockClear()
  })

  afterEach(() => {
    while (wrappers.length) wrappers.pop()!.unmount()
  })

  function mountPrompt(): VueWrapper {
    const wrapper = mount(UpdatePrompt)
    wrappers.push(wrapper)
    return wrapper
  }

  it('is hidden initially', () => {
    const wrapper = mountPrompt()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('appears when the pwa:need-refresh event fires', async () => {
    const wrapper = mountPrompt()
    window.dispatchEvent(new CustomEvent('pwa:need-refresh'))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
  })

  it('calls updatePwa and hides on refresh', async () => {
    const wrapper = mountPrompt()
    window.dispatchEvent(new CustomEvent('pwa:need-refresh'))
    await wrapper.vm.$nextTick()

    await wrapper.findAll('button')[0]!.trigger('click')
    expect(updatePwa).toHaveBeenCalledOnce()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('hides without updating on dismiss', async () => {
    const wrapper = mountPrompt()
    window.dispatchEvent(new CustomEvent('pwa:need-refresh'))
    await wrapper.vm.$nextTick()

    await wrapper.findAll('button')[1]!.trigger('click')
    expect(updatePwa).not.toHaveBeenCalled()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('is removed on unmount', async () => {
    const wrapper = mountPrompt()
    window.dispatchEvent(new CustomEvent('pwa:need-refresh'))
    await wrapper.vm.$nextTick()

    wrapper.unmount()
    window.dispatchEvent(new CustomEvent('pwa:need-refresh'))
    const fresh = mountPrompt()
    expect(fresh.find('[role="status"]').exists()).toBe(false)
  })
})
