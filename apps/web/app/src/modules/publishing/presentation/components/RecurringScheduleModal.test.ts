import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import RecurringScheduleModal from './RecurringScheduleModal.vue'

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
  usePublishingStore: () => ({
    workspaces: [
      { id: 'ws-1', name: 'Test Workspace', active: true },
    ],
    selectedWorkspaceId: 'ws-1',
    linkedinChannels: [
      { id: 'li-1', name: 'My Page', type: 'PAGE' as const },
      { id: 'li-2', name: 'My Profile', type: 'PROFILE' as const },
    ],
    posts: [],
    fetchLinkedInChannels: vi.fn(),
    createPost: vi.fn(),
    fetchPosts: vi.fn(),
  }),
}))

function bodyQueryAll(selector: string): Element[] {
  return Array.from(document.body.querySelectorAll(selector))
}

function bodyQuery(selector: string): Element | null {
  return document.body.querySelector(selector)
}

function triggerChange(selector: string, value: string | string[]) {
  const el = bodyQuery(selector) as HTMLInputElement | HTMLSelectElement | null
  if (!el) return
  if (el instanceof HTMLSelectElement) {
    el.value = Array.isArray(value) ? value[0] : value
    el.dispatchEvent(new Event('change', { bubbles: true }))
  } else if (el.type === 'checkbox') {
    const checked = Array.isArray(value) ? value.includes((el as HTMLInputElement).value) : !!value
    ;(el as HTMLInputElement).checked = checked
    el.dispatchEvent(new Event('change', { bubbles: true }))
  } else {
    el.value = Array.isArray(value) ? value[0] : value
    el.dispatchEvent(new Event('input', { bubbles: true }))
    el.dispatchEvent(new Event('change', { bubbles: true }))
  }
}

function clickButton(selector: string) {
  const btn = bodyQuery(selector) as HTMLButtonElement | null
  if (!btn) return
  btn.click()
  document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
}

function submitForm() {
  const form = bodyQuery('form')
  if (!form) return
  const submitBtn = form.querySelector('button[type="submit"]') as HTMLButtonElement | null
  if (submitBtn) submitBtn.click()
}

function selectFrequency(freq: string) {
  triggerChange('select[data-testid="frequency-select"]', freq)
}

describe('RecurringScheduleModal', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  describe('daily recurrence', () => {
    it('renders and accepts valid daily recurring schedule', async () => {
      const props = {
        isOpen: true,
        publication: { id: 'pub-1', content: 'Test post' },
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      selectFrequency('daily')
      await nextTick()
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeDefined()
      const savedEvent = emitted?.saved?.[0] as Record<string, unknown>
      expect(savedEvent).toBeDefined()
      expect((savedEvent as { frequency: string }).frequency).toBe('daily')
    })

    it('sets interval to 1 by default for daily recurrence', async () => {
      const props = {
        isOpen: true,
        publication: { id: 'pub-1', content: 'Test post' },
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      selectFrequency('daily')
      await nextTick()
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      const savedEvent = emitted?.saved?.[0] as Record<string, unknown>
      expect((savedEvent as { interval: number }).interval).toBe(1)
    })
  })

  describe('weekly recurrence', () => {
    it('renders weekly frequency option', async () => {
      const props = {
        isOpen: true,
        publication: { id: 'pub-1', content: 'Test post' },
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      selectFrequency('weekly')
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeDefined()
      const savedEvent = emitted?.saved?.[0] as Record<string, unknown>
      expect((savedEvent as { frequency: string }).frequency).toBe('weekly')
    })

    it('fails validation when no weekdays are selected for weekly', async () => {
      const props = {
        isOpen: true,
        publication: { id: 'pub-1', content: 'Test post' },
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      selectFrequency('weekly')
      await nextTick()
      await nextTick()

      const checkboxes = bodyQueryAll('input[type="checkbox"]')
      checkboxes.forEach((cb) => {
        ;(cb as HTMLInputElement).checked = false
        cb.dispatchEvent(new Event('change', { bubbles: true }))
      })
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeUndefined()
      const errorEl = bodyQuery('[data-testid="recurrence-error"]')
      expect(errorEl?.textContent).toContain('recurring.schedule.validation.weekly.needs.weekday')
    })

    it('accepts valid weekly with weekdays selected', async () => {
      const props = {
        isOpen: true,
        publication: { id: 'pub-1', content: 'Test post' },
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      selectFrequency('weekly')
      await nextTick()
      await nextTick()

      const checkboxes = bodyQueryAll('input[type="checkbox"]')
      checkboxes.forEach((cb) => {
        ;(cb as HTMLInputElement).checked = false
        cb.dispatchEvent(new Event('change', { bubbles: true }))
      })
      const mondayCb = checkboxes.find((cb) => (cb as HTMLInputElement).value === '1')
      if (mondayCb) {
        ;(mondayCb as HTMLInputElement).checked = true
        mondayCb.dispatchEvent(new Event('change', { bubbles: true }))
      }
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeDefined()
      const savedEvent = emitted?.saved?.[0] as Record<string, unknown>
      expect((savedEvent as { daysOfWeek: number[] }).daysOfWeek).toContain(1)
    })
  })

  describe('monthly recurrence', () => {
    it('renders and accepts valid monthly recurring schedule', async () => {
      const props = {
        isOpen: true,
        publication: { id: 'pub-1', content: 'Test post' },
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      selectFrequency('monthly')
      await nextTick()
      await nextTick()

      const dayInput = bodyQuery('input[data-testid="day-of-month-input"]') as HTMLInputElement | null
      if (dayInput) {
        dayInput.value = '15'
        dayInput.dispatchEvent(new Event('input', { bubbles: true }))
        dayInput.dispatchEvent(new Event('change', { bubbles: true }))
      }
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeDefined()
      const savedEvent = emitted?.saved?.[0] as Record<string, unknown>
      expect((savedEvent as { dayOfMonth: number }).dayOfMonth).toBe(15)
    })
  })

  describe('error states', () => {
    it('shows error when frequency is missing', async () => {
      const props = {
        isOpen: true,
        publication: { id: 'pub-1', content: 'Test post' },
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeUndefined()
      const errorEl = bodyQuery('[data-testid="recurrence-error"]')
      expect(errorEl).toBeDefined()
    })
  })
})
