import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockSaveConsent = vi.fn()

let mockAnalyticsEnabled = false

vi.mock('./useConsent', () => ({
  useConsent: () => ({
    analyticsEnabled: ref(mockAnalyticsEnabled),
    acceptAll: () => mockSaveConsent({ analytics: true, source: 'settings-panel' }),
    rejectAll: () => mockSaveConsent({ analytics: false, source: 'settings-panel' }),
    save: (analytics: boolean) => mockSaveConsent({ analytics, source: 'settings-panel' }),
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Stubs
const DialogStub = {
  props: ['open'],
  template: '<div data-testid="settings-dialog" v-if="open"><slot /><slot name="content" /></div>',
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

async function mountSettings(props: { open?: boolean } = {}) {
  const CookieSettings = (await import('./CookieSettings.vue')).default
  return mount(CookieSettings, {
    props: { open: props.open ?? false },
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

describe('CookieSettings', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockSaveConsent.mockReset()
    mockAnalyticsEnabled = false
  })

  it('shows the dialog when open prop is true', async () => {
    const wrapper = await mountSettings({ open: true })
    await flushPromises()

    expect(wrapper.find('[data-testid="settings-dialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('consent.banner.title')
  })

  it('does NOT show the dialog when open prop is false', async () => {
    const wrapper = await mountSettings({ open: false })
    await flushPromises()

    expect(wrapper.find('[data-testid="settings-dialog"]').exists()).toBe(false)
  })

  it('calls saveConsent with analytics=true and source=settings-panel on Accept All', async () => {
    const wrapper = await mountSettings({ open: true })
    await flushPromises()

    const buttons = wrapper.findAll('[data-testid="button-stub"]')
    // Last button is Accept All
    await buttons[buttons.length - 1]!.trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: true,
      source: 'settings-panel',
    })
  })

  it('calls saveConsent with analytics=false and source=settings-panel on Reject All', async () => {
    const wrapper = await mountSettings({ open: true })
    await flushPromises()

    const buttons = wrapper.findAll('[data-testid="button-stub"]')
    // First button is Reject All
    await buttons[0]!.trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: false,
      source: 'settings-panel',
    })
  })

  it('calls saveConsent with current toggle state on Save and source=settings-panel', async () => {
    mockAnalyticsEnabled = false
    const wrapper = await mountSettings({ open: true })
    await flushPromises()

    const buttons = wrapper.findAll('[data-testid="button-stub"]')
    // Middle button is Save
    await buttons[1]!.trigger('click')

    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: false,
      source: 'settings-panel',
    })
  })

  it('emits update:open false when closing after save', async () => {
    const wrapper = await mountSettings({ open: true })
    await flushPromises()

    const buttons = wrapper.findAll('[data-testid="button-stub"]')
    await buttons[1]!.trigger('click')

    expect(wrapper.emitted('update:open')).toBeTruthy()
    expect(wrapper.emitted('update:open')![0]).toEqual([false])
  })

  it('renders Accept, Reject, and Save buttons', async () => {
    const wrapper = await mountSettings({ open: true })
    await flushPromises()

    // All three buttons should exist (rendered via ButtonStub)
    const buttons = wrapper.findAll('[data-testid="button-stub"]')
    expect(buttons).toHaveLength(3)
  })
})
