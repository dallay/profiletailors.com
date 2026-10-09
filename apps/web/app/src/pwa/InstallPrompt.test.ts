import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import InstallPrompt from './InstallPrompt.vue'

const { install } = vi.hoisted(() => ({ install: vi.fn() }))

vi.mock('./usePwaInstall', () => ({
  usePwaInstall: () => ({ canInstall: { value: true }, install, showIosGuide: { value: false } }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('InstallPrompt', () => {
  it('waits for the user to activate the install button', async () => {
    const wrapper = mount(InstallPrompt)

    expect(install).not.toHaveBeenCalled()
    await wrapper.get('button').trigger('click')
    expect(install).toHaveBeenCalledOnce()
  })
})
