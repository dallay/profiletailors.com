import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import UpdatePrompt from './UpdatePrompt.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('./registerPwa', () => ({
  updatePwa: vi.fn(),
}))

afterEach(() => {
  vi.restoreAllMocks()
})

describe('UpdatePrompt', () => {
  it('announces the update message without including its controls in the status', async () => {
    const wrapper = mount(UpdatePrompt)

    expect(wrapper.find('[role="status"]').exists()).toBe(false)

    window.dispatchEvent(new Event('pwa:need-refresh'))
    await wrapper.vm.$nextTick()

    const announcement = wrapper.find('[role="status"]')
    expect(announcement.text()).toBe('pwa.update.message')
    expect(announcement.element.tagName).toBe('P')
    expect(wrapper.findAll('button')).toHaveLength(2)
    expect(wrapper.find('[role="status"] button').exists()).toBe(false)

    wrapper.unmount()
  })
})
