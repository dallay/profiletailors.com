import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref, computed } from 'vue'
import BulkImportModal from './BulkImportModal.vue'
import { BULK_CANONICAL_HEADER } from '@modules/publishing/domain/bulk'

const mockValidateResult = ref<unknown>(null)
const mockScheduleResult = ref<unknown>(null)
const mockError = ref<string | null>(null)
const mockIsValidating = ref(false)
const mockIsScheduling = ref(false)
const mockHasValidationErrors = ref(false)

const mockValidate = vi.fn()
const mockSchedule = vi.fn()

vi.mock('@modules/publishing/application/useBulkImport', () => ({
  useBulkImport: () => ({
    validateResult: mockValidateResult,
    scheduleResult: mockScheduleResult,
    error: mockError,
    isValidating: mockIsValidating,
    isScheduling: mockIsScheduling,
    hasValidationErrors: computed(() => mockHasValidationErrors.value),
    validate: mockValidate,
    schedule: mockSchedule,
  }),
}))

const mockParse = vi.fn((text: string) => ({
  header: text.split('\n')[0]?.split(',') ?? [],
  rows: [],
  headerValid: text.startsWith(BULK_CANONICAL_HEADER),
}))

vi.mock('@modules/publishing/application/useBulkCsvParser', () => ({
  useBulkCsvParser: () => ({ parse: mockParse }),
}))



vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () =>
    Object.assign(async () => ({}), { raw: async () => new Response(null, { status: 204 }) }),
  resolveApiUrl: vi.fn((p: string) => `https://api.test${p}`),
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

function mountModal(props: Record<string, unknown> = {}) {
  const wrapper = mount(BulkImportModal, {
    attachTo: document.body,
    props: { isOpen: true, ...props },
    global: {
      plugins: [createPinia()],
      stubs: {
        BulkPreviewTable: {
          template: '<div data-testid="bulk-preview-table"></div>',
          props: ['rows', 'editable'],
        },
        BulkTemplatePicker: {
          template:
            '<div data-testid="bulk-template-picker"><button data-testid="template-download" @click="$emit(\'download\', \'csv,from,template\')">download</button></div>',
        },
      },
    },
  })
  return wrapper
}

describe('BulkImportModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    mockValidateResult.value = null
    mockScheduleResult.value = null
    mockError.value = null
    mockIsValidating.value = false
    mockIsScheduling.value = false
    mockHasValidationErrors.value = false
    mockValidate.mockReset()
    mockSchedule.mockReset()
    mockParse.mockClear()
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('renders modal when isOpen true and hides when false', async () => {
    const w1 = mountModal({ isOpen: true })
    expect(document.body.querySelector('[data-testid="bulk-import-modal"]')).not.toBeNull()
    w1.unmount()
    document.body.innerHTML = ''
    const w2 = mountModal({ isOpen: false })
    expect(document.body.querySelector('[data-testid="bulk-import-modal"]')).toBeNull()
    w2.unmount()
  })

  it('close button emits close', async () => {
    const wrapper = mountModal()
    const closeBtn = document.body.querySelector('[data-testid="bulk-modal-close"]') as HTMLElement
    closeBtn.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('click self on backdrop emits close', async () => {
    const wrapper = mountModal()
    const backdrop = document.body.querySelector('[data-testid="bulk-import-modal"]') as HTMLElement
    backdrop.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('file input handles file text', async () => {
    const wrapper = mountModal()
    const file = new File(['hello,2026-09-01T10:00:00Z'], 'test.csv', { type: 'text/csv' })
    const input = document.body.querySelector('[data-testid="bulk-file-input"]') as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [file], configurable: true })
    input.dispatchEvent(new Event('change'))
    await new Promise((r) => setTimeout(r, 0))
    await wrapper.vm.$nextTick()
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    expect(textarea.value).toContain('hello')
    wrapper.unmount()
  })

  it('file input early returns when no file', async () => {
    const wrapper = mountModal()
    const input = document.body.querySelector('[data-testid="bulk-file-input"]') as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [], configurable: true })
    input.dispatchEvent(new Event('change'))
    await wrapper.vm.$nextTick()
    expect(mockValidate).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('template download sets csvText', async () => {
    const wrapper = mountModal()
    const btn = document.body.querySelector('[data-testid="template-download"]') as HTMLElement
    btn.click()
    await wrapper.vm.$nextTick()
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    expect(textarea.value).toBe('csv,from,template')
    wrapper.unmount()
  })

  it('validate button disabled when csv empty and enabled when csv present', async () => {
    const wrapper = mountModal()
    const btn = document.body.querySelector(
      '[data-testid="bulk-validate-btn"]',
    ) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    textarea.value = 'some csv'
    textarea.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    expect(btn.disabled).toBe(false)
    wrapper.unmount()
  })

  it('validate button shows Validating when isValidating', async () => {
    mockIsValidating.value = true
    const wrapper = mountModal()
    expect(document.body.textContent).toContain('Validating')
    wrapper.unmount()
  })

  it('schedule button disabled when no validateResult', async () => {
    const wrapper = mountModal()
    const btn = document.body.querySelector(
      '[data-testid="bulk-schedule-btn"]',
    ) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    wrapper.unmount()
  })

  it('schedule button enabled when validateResult present', async () => {
    mockValidateResult.value = { rows: [] }
    const wrapper = mountModal()
    await wrapper.vm.$nextTick()
    const btn = document.body.querySelector(
      '[data-testid="bulk-schedule-btn"]',
    ) as HTMLButtonElement
    expect(btn.disabled).toBe(false)
    wrapper.unmount()
  })

  it('handleValidate does nothing when csv empty', async () => {
    const wrapper = mountModal()
    const btn = document.body.querySelector(
      '[data-testid="bulk-validate-btn"]',
    ) as HTMLButtonElement
    btn.click()
    await wrapper.vm.$nextTick()
    expect(mockValidate).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('handleValidate calls validate and swallows error', async () => {
    const wrapper = mountModal()
    mockValidate.mockRejectedValueOnce(new Error('fail'))
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    textarea.value = 'csv content'
    textarea.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    const btn = document.body.querySelector(
      '[data-testid="bulk-validate-btn"]',
    ) as HTMLButtonElement
    btn.click()
    await wrapper.vm.$nextTick()
    await new Promise((r) => setTimeout(r, 0))
    expect(mockValidate).toHaveBeenCalledWith('csv content')
    wrapper.unmount()
  })

  it('handleValidate succeeds', async () => {
    mockValidate.mockResolvedValueOnce({ rows: [] })
    const wrapper = mountModal()
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    textarea.value = 'valid csv'
    textarea.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    const btn = document.body.querySelector(
      '[data-testid="bulk-validate-btn"]',
    ) as HTMLButtonElement
    btn.click()
    await wrapper.vm.$nextTick()
    await new Promise((r) => setTimeout(r, 0))
    expect(mockValidate).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('handleSchedule does nothing when csv empty', async () => {
    mockValidateResult.value = { rows: [] }
    const wrapper = mountModal()
    const btn = document.body.querySelector(
      '[data-testid="bulk-schedule-btn"]',
    ) as HTMLButtonElement
    ;(
      document.body.querySelector('[data-testid="bulk-csv-textarea"]') as HTMLTextAreaElement
    ).value = ''
    btn.click()
    await wrapper.vm.$nextTick()
    expect(mockSchedule).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('handleSchedule does nothing when hasValidationErrors true', async () => {
    mockValidateResult.value = { rows: [] }
    mockHasValidationErrors.value = true
    const wrapper = mountModal()
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    textarea.value = 'csv'
    textarea.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    mockHasValidationErrors.value = true
    await wrapper.vm.$nextTick()
    const btn = document.body.querySelector(
      '[data-testid="bulk-schedule-btn"]',
    ) as HTMLButtonElement
    btn.click()
    await wrapper.vm.$nextTick()
    await new Promise((r) => setTimeout(r, 0))
    expect(mockSchedule).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('handleSchedule emits scheduled on success', async () => {
    mockValidateResult.value = { rows: [] }
    mockHasValidationErrors.value = false
    mockSchedule.mockResolvedValueOnce({ jobId: 'job-123' })
    const wrapper = mountModal()
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    textarea.value = 'csv content'
    textarea.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    mockValidateResult.value = { rows: [] }
    await wrapper.vm.$nextTick()
    const btn = document.body.querySelector(
      '[data-testid="bulk-schedule-btn"]',
    ) as HTMLButtonElement
    btn.click()
    await wrapper.vm.$nextTick()
    await new Promise((r) => setTimeout(r, 0))
    expect(mockSchedule).toHaveBeenCalledWith('csv content')
    expect(wrapper.emitted('scheduled')?.[0]).toEqual(['job-123'])
    wrapper.unmount()
  })

  it('handleSchedule swallows error', async () => {
    mockValidateResult.value = { rows: [] }
    mockSchedule.mockRejectedValueOnce(new Error('schedule fail'))
    const wrapper = mountModal()
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    textarea.value = 'csv'
    textarea.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    mockValidateResult.value = { rows: [] }
    await wrapper.vm.$nextTick()
    const btn = document.body.querySelector(
      '[data-testid="bulk-schedule-btn"]',
    ) as HTMLButtonElement
    btn.click()
    await new Promise((r) => setTimeout(r, 0))
    expect(wrapper.emitted('scheduled')).toBeUndefined()
    wrapper.unmount()
  })

  it('displays error when bulk.error present', async () => {
    mockError.value = 'something went wrong'
    const wrapper = mountModal()
    await wrapper.vm.$nextTick()
    expect(document.body.querySelector('[data-testid="bulk-error"]')?.textContent).toContain(
      'something went wrong',
    )
    wrapper.unmount()
  })

  it('displays header error when parse headerValid false and csv trimmed', async () => {
    mockParse.mockReturnValue({ header: [], rows: [], headerValid: false })
    const wrapper = mountModal()
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    textarea.value = 'bad header'
    textarea.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    expect(document.body.querySelector('[data-testid="bulk-header-error"]')).not.toBeNull()
    wrapper.unmount()
  })

  it('displays schedule result when scheduleResult present', async () => {
    mockScheduleResult.value = { jobId: 'job-xyz', scheduledCount: 2, totalRows: 3 }
    const wrapper = mountModal()
    await wrapper.vm.$nextTick()
    expect(
      document.body.querySelector('[data-testid="bulk-schedule-result"]')?.textContent,
    ).toContain('job-xyz')
    wrapper.unmount()
  })

  it('shows BulkPreviewTable when validateResult present', async () => {
    mockValidateResult.value = { rows: [{ rowIndex: 0, status: 'VALID', errors: [] }] }
    const wrapper = mountModal()
    await wrapper.vm.$nextTick()
    expect(document.body.querySelector('[data-testid="bulk-preview-table"]')).not.toBeNull()
    wrapper.unmount()
  })

  it('watch clears validateResult scheduleResult and error when csvText changes', async () => {
    mockValidateResult.value = { rows: [] }
    mockScheduleResult.value = { jobId: 'j1' } as never
    mockError.value = 'err'
    const wrapper = mountModal()
    const textarea = document.body.querySelector(
      '[data-testid="bulk-csv-textarea"]',
    ) as HTMLTextAreaElement
    textarea.value = 'new value'
    textarea.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    expect(mockValidateResult.value).toBeNull()
    expect(mockScheduleResult.value).toBeNull()
    expect(mockError.value).toBeNull()
    wrapper.unmount()
  })

  it('scheduling button shows Scheduling when isScheduling', async () => {
    mockIsScheduling.value = true
    const wrapper = mountModal()
    expect(document.body.textContent).toContain('Scheduling')
    wrapper.unmount()
  })
})
