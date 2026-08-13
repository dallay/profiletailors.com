import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'

const mockSaveConsent = vi.fn()
const mockHasValidConsent = ref(false)
const mockAnalyticsEnabled = ref(false)

vi.mock('@modules/settings/infrastructure/consent.store', () => ({
  useConsentStore: () => ({
    hasValidConsent: mockHasValidConsent.value,
    analyticsEnabled: mockAnalyticsEnabled.value,
    saveConsent: mockSaveConsent,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

const ButtonStub = {
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
}
const SwitchStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template:
    '<label data-testid="switch-stub"><input type="checkbox" :checked="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', $event.target.checked)" /></label>',
}

async function mountBanner() {
  const ConsentBanner = (await import('./ConsentBanner.vue')).default
  return mount(ConsentBanner, {
    global: {
      stubs: {
        Switch: SwitchStub,
        Button: ButtonStub,
      },
    },
  })
}

describe('ConsentBanner', () => {
  beforeEach(() => {
    mockSaveConsent.mockReset()
    mockHasValidConsent.value = false
    mockAnalyticsEnabled.value = false
  })

  it('shows a non-modal aside when consent is missing', async () => {
    const wrapper = await mountBanner()
    const banner = wrapper.find('[data-testid="consent-banner"]')

    expect(banner.exists()).toBe(true)
    expect(banner.element.tagName).toBe('ASIDE')
    expect(banner.attributes('role')).not.toBe('dialog')
    expect(banner.attributes('aria-modal')).toBeUndefined()
    expect(wrapper.find('[data-slot="dialog-overlay"]').exists()).toBe(false)
  })

  it('does not show the banner when valid consent exists', async () => {
    mockHasValidConsent.value = true

    const wrapper = await mountBanner()

    expect(wrapper.find('[data-testid="consent-banner"]').exists()).toBe(false)
  })

  it('keeps all first-level actions immediately available', async () => {
    const wrapper = await mountBanner()

    expect(wrapper.find('[data-testid="reject-all-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="customize-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="accept-all-btn"]').exists()).toBe(true)
  })

  it('calls saveConsent with analytics=true on Accept All', async () => {
    const wrapper = await mountBanner()

    await wrapper.find('[data-testid="accept-all-btn"]').trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({ analytics: true, source: 'banner' })
  })

  it('calls saveConsent with analytics=false on Reject All', async () => {
    const wrapper = await mountBanner()

    await wrapper.find('[data-testid="reject-all-btn"]').trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({ analytics: false, source: 'banner' })
  })

  it('does not write a receipt when Escape is pressed', async () => {
    const wrapper = await mountBanner()

    await wrapper.trigger('keydown', { key: 'Escape' })

    expect(mockSaveConsent).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="consent-banner"]').exists()).toBe(true)
  })

  it('expands Customize inline and saves the analytics toggle', async () => {
    const wrapper = await mountBanner()

    await wrapper.find('[data-testid="customize-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="customize-panel"]').exists()).toBe(true)

    await wrapper.find('[data-testid="analytics-toggle"]').find('input').setValue(true)
    await wrapper.find('[data-testid="save-btn"]').trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({ analytics: true, source: 'banner' })
  })

  it('returns from Customize without writing a receipt', async () => {
    const wrapper = await mountBanner()

    await wrapper.find('[data-testid="customize-btn"]').trigger('click')
    await wrapper.find('[data-testid="back-btn"]').trigger('click')

    expect(mockSaveConsent).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="customize-panel"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="consent-banner"]').exists()).toBe(true)
  })

  it('keeps the Necessary switch disabled', async () => {
    const wrapper = await mountBanner()

    await wrapper.find('[data-testid="customize-btn"]').trigger('click')

    expect(
      wrapper.find('[data-testid="necessary-toggle"]').find('input').attributes('disabled'),
    ).toBeDefined()
  })
})
