import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockSaveConsent = vi.fn()
const mockCloseSettings = vi.fn()

let mockHasValidConsent = false
let mockForceOpen = false
let mockAnalyticsEnabled = false
let mockReceipt: unknown = null

vi.mock('@modules/settings/infrastructure/consent.store', () => ({
  useConsentStore: () => ({
    receipt: mockReceipt,
    hasValidConsent: mockHasValidConsent,
    forceOpen: mockForceOpen,
    analyticsEnabled: mockAnalyticsEnabled,
    saveConsent: mockSaveConsent,
    openSettings: vi.fn(),
    closeSettings: mockCloseSettings,
    loadFromStorage: vi.fn(),
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Stub Dialog so it simply renders the slot when open, nothing when closed.
const DialogStub = {
  props: ['open'],
  template:
    '<div data-testid="consent-dialog" v-if="open"><slot name="default" /><slot name="content" /></div>',
}
const ButtonStub = {
  emits: ['click'],
  template: '<button data-testid="button-stub" @click="$emit(\'click\')"><slot /></button>',
}
const SwitchStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template:
    '<label data-testid="switch-stub"><input type="checkbox" :checked="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', ($event.target).checked)" /></label>',
}

async function mountBanner() {
  const ConsentBanner = (await import('./ConsentBanner.vue')).default
  return mount(ConsentBanner, {
    global: {
      stubs: {
        Dialog: DialogStub,
        DialogContent: { template: '<div data-testid="dialog-content"><slot /></div>' },
        DialogTitle: { template: '<div data-testid="dialog-title"><slot /></div>' },
        DialogDescription: { template: '<div data-testid="dialog-description"><slot /></div>' },
        Switch: SwitchStub,
        Button: ButtonStub,
      },
    },
  })
}

describe('ConsentBanner', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockSaveConsent.mockReset()
    mockCloseSettings.mockReset()
    mockHasValidConsent = false
    mockForceOpen = false
    mockAnalyticsEnabled = false
    mockReceipt = null
  })

  it('shows the banner dialog when consent is missing', async () => {
    const wrapper = await mountBanner()
    await flushPromises()

    expect(wrapper.find('[data-testid="consent-dialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('consent.banner.title')
  })

  it('does NOT show the banner when valid consent exists and forceOpen is false', async () => {
    mockHasValidConsent = true
    mockForceOpen = false

    const wrapper = await mountBanner()
    await flushPromises()

    expect(wrapper.find('[data-testid="consent-dialog"]').exists()).toBe(false)
  })

  it('shows the banner when forceOpen is true even if consent exists', async () => {
    mockHasValidConsent = true
    mockForceOpen = true

    const wrapper = await mountBanner()
    await flushPromises()

    expect(wrapper.find('[data-testid="consent-dialog"]').exists()).toBe(true)
  })

  it('calls saveConsent with analytics=true on Accept All', async () => {
    const wrapper = await mountBanner()
    await flushPromises()

    const acceptButton = wrapper.find('[data-testid="accept-all-btn"]')
    expect(acceptButton.exists()).toBe(true)
    await acceptButton.trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: true,
      source: 'banner',
    })
  })

  it('calls saveConsent with analytics=false on Reject All', async () => {
    const wrapper = await mountBanner()
    await flushPromises()

    const rejectButton = wrapper.find('[data-testid="reject-all-btn"]')
    expect(rejectButton.exists()).toBe(true)
    await rejectButton.trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: false,
      source: 'banner',
    })
  })

  it('calls saveConsent with current toggle state on Save — analytics on', async () => {
    mockAnalyticsEnabled = true
    const wrapper = await mountBanner()
    await flushPromises()

    const saveButton = wrapper.find('[data-testid="save-btn"]')
    await saveButton.trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: true,
      source: 'banner',
    })
  })

  it('calls saveConsent with current toggle state on Save — analytics off', async () => {
    mockAnalyticsEnabled = false
    const wrapper = await mountBanner()
    await flushPromises()

    const saveButton = wrapper.find('[data-testid="save-btn"]')
    await saveButton.trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: false,
      source: 'banner',
    })
  })

  it('renders Accept, Reject, and Save buttons', async () => {
    const wrapper = await mountBanner()
    await flushPromises()

    expect(wrapper.find('[data-testid="accept-all-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reject-all-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="save-btn"]').exists()).toBe(true)
  })
})
