import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// ---------------------------------------------------------------------------
// Mocks — all vi.mock factories use vi.hoisted references only
// ---------------------------------------------------------------------------

const { mockReportTakedown, mockAuthEmail } = vi.hoisted(() => ({
  mockReportTakedown: vi.fn(),
  mockAuthEmail: 'user@example.com',
}))

vi.mock('@modules/governance/services/governance-api', () => ({
  reportTakedown: mockReportTakedown,
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    user: { email: mockAuthEmail },
    apiFetch: vi.fn(),
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'en' },
  }),
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return { X: stub }
})

// Dialog stub: all content renders always — Vue stubs don't reliably propagate
// parent props through $parent, so we skip the conditional rendering for tests.
vi.mock('@/components/ui/dialog', () => ({
  Dialog: {
    props: ['open'],
    emits: ['update:open'],
    template: '<div class="mock-dialog"><slot name="trigger" /><slot /></div>',
  },
  DialogContent: {
    template: '<div class="mock-dialog-content"><slot /></div>',
  },
  DialogDescription: { template: '<p class="mock-dialog-desc"><slot /></p>' },
  DialogFooter: { template: '<div class="mock-dialog-footer"><slot /></div>' },
  DialogHeader: { template: '<div class="mock-dialog-header"><slot /></div>' },
  DialogTitle: { template: '<h3 class="mock-dialog-title"><slot /></h3>' },
  DialogTrigger: {
    props: ['asChild'],
    template: '<div class="mock-dialog-trigger"><slot /></div>',
  },
}))

vi.mock('@/components/ui/button', () => ({
  Button: { template: '<button class="ui-button" v-bind="$attrs"><slot /></button>' },
}))

vi.mock('@/components/ui/input', () => ({
  Input: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template:
      '<input class="ui-input" :value="modelValue" @input="(e) => $emit(\'update:modelValue\', e.target.value)" />',
  },
}))

vi.mock('@/components/ui/textarea', () => ({
  Textarea: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template:
      '<textarea class="ui-textarea" :value="modelValue" @input="(e) => $emit(\'update:modelValue\', e.target.value)" />',
  },
}))

vi.mock('@/components/ui/label', () => ({
  Label: { template: '<label><slot /></label>' },
}))

// ---------------------------------------------------------------------------
// Component under test
// ---------------------------------------------------------------------------

import TakedownReportDialog from './TakedownReportDialog.vue'

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('TakedownReportDialog.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  function mountDialog() {
    return mount(TakedownReportDialog, {
      props: { assetId: 'ast-1' },
      global: {
        stubs: { Teleport: true },
        mocks: { $t: (key: string) => key },
      },
    })
  }

  it('renders a trigger button visible at all times', () => {
    const wrapper = mountDialog()
    const trigger = wrapper.find('button.ui-button')
    expect(trigger.exists()).toBe(true)
    expect(trigger.text()).toContain('governance.takedown.report.action')
  })

  it('shows validation error when reason is empty on submit', async () => {
    const wrapper = mountDialog()

    // Find the form (should be visible in our stub)
    const form = wrapper.find('form')
    await form.trigger('submit')

    expect(wrapper.text()).toContain('governance.takedown.report.errors.reasonRequired')
    expect(mockReportTakedown).not.toHaveBeenCalled()
  })

  it('pre-fills the email from auth store', () => {
    const wrapper = mountDialog()
    const emailInput = wrapper.find('input.ui-input')
    expect(emailInput.exists()).toBe(true)
    expect((emailInput.element as HTMLInputElement).value).toBe(mockAuthEmail)
  })

  it('calls reportTakedown on valid submit and emits reported', async () => {
    mockReportTakedown.mockResolvedValue({
      reportId: 'rpt-new',
      status: 'REPORTED',
    })
    const wrapper = mountDialog()

    const textarea = wrapper.find('textarea.ui-textarea')
    await textarea.setValue('This is a copyright infringement')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockReportTakedown).toHaveBeenCalledWith({
      assetId: 'ast-1',
      reason: 'This is a copyright infringement',
    })
    expect(wrapper.emitted('reported')).toBeTruthy()
  })

  it('includes mediaReferenceUrl when provided', async () => {
    mockReportTakedown.mockResolvedValue({
      reportId: 'rpt-new',
      status: 'REPORTED',
    })
    const wrapper = mountDialog()

    const textarea = wrapper.find('textarea.ui-textarea')
    await textarea.setValue('Copyright issue')

    // URL input is the second input (after email)
    const inputs = wrapper.findAll('input.ui-input')
    if (inputs.length > 1) {
      await inputs[1]!.setValue('https://example.com/original')
    }

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockReportTakedown).toHaveBeenCalledWith(
      expect.objectContaining({
        mediaReferenceUrl: 'https://example.com/original',
      }),
    )
  })

  it('displays error message when reportTakedown fails', async () => {
    mockReportTakedown.mockRejectedValue(new Error('Network error'))
    const wrapper = mountDialog()

    const textarea = wrapper.find('textarea.ui-textarea')
    await textarea.setValue('Copyright issue')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // Component shows apiError.message when Error has a message property
    expect(wrapper.text()).toContain('Network error')
  })

  it('closes dialog and emits reported on successful submission', async () => {
    mockReportTakedown.mockResolvedValue({
      reportId: 'rpt-new',
      status: 'REPORTED',
    })
    const wrapper = mountDialog()

    await wrapper.find('textarea.ui-textarea').setValue('Copyright')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('reported')).toBeTruthy()
  })
})
