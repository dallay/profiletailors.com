import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import RecurringScheduleModal from './RecurringScheduleModal.vue'

const mockT = (key: string) => key
const publication = {
  id: '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
  content: 'Test post',
  channels: [],
  scheduledAt: '2099-12-31T12:00:00.000Z',
  status: 'DRAFT' as const,
  priority: false,
}
const mockStore = vi.hoisted(() => ({
  createRecurringSchedule: vi.fn(),
  updateRecurringSchedule: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: mockT, locale: { value: 'en' } }),
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
}))

vi.mock('@shared/i18n', () => ({
  default: { global: { locale: { value: 'en' } } },
}))

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
  usePublishingStore: () => ({
    workspaces: [
      { id: '820c22d8-9ce7-5e76-867f-04bac4e53b59', name: 'Test Workspace', active: true },
    ],
    selectedWorkspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
    linkedinChannels: [
      { id: '2896e95d-50ec-51cd-a8c7-8aa8ded945d7', name: 'My Page', type: 'PAGE' as const },
      { id: '552d6af1-de96-5849-95bc-d9014818dec4', name: 'My Profile', type: 'PROFILE' as const },
    ],
    posts: [],
    fetchLinkedInChannels: vi.fn(),
    createPost: vi.fn(),
    fetchPosts: vi.fn(),
    createRecurringSchedule: mockStore.createRecurringSchedule,
    updateRecurringSchedule: mockStore.updateRecurringSchedule,
  }),
}))

function bodyQueryAll(selector: string): Element[] {
  return Array.from(document.body.querySelectorAll(selector))
}

function bodyQuery(selector: string): Element | null {
  return document.body.querySelector(selector)
}

function submitForm() {
  const form = bodyQuery('form')
  if (form) {
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  }
}

async function selectFrequency(frequency: string) {
  const select = bodyQuery('select[data-testid="frequency-select"]')
  if (select instanceof HTMLSelectElement) {
    select.value = frequency
    select.dispatchEvent(new Event('change', { bubbles: true }))
    await nextTick()
  }
}

async function setStartDate(value: string) {
  const input = bodyQuery('input[data-testid="starts-at-input"]')
  if (input instanceof HTMLInputElement) {
    input.value = value
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    await nextTick()
  }
}

describe('RecurringScheduleModal', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.clearAllMocks()
    mockStore.createRecurringSchedule.mockImplementation(async (templatePostId, input) => ({
      id: '7e4bf9f2-91c1-5cc2-8461-3b6779481a4b',
      workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      createdBy: '18f3c8ec-b5fd-5d86-ac83-465965479890',
      templatePostId,
      ...input,
      nextScheduledAt: input.startsAt,
      status: 'ACTIVE' as const,
      createdAt: null,
      updatedAt: null,
    }))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  describe('daily recurrence', () => {
    it('renders and accepts valid daily recurring schedule', async () => {
      const props = {
        isOpen: true,
        publication,
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      await selectFrequency('daily')
      await nextTick()
      await setStartDate('2099-12-31T12:00')
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeDefined()
      const savedEvent = wrapper.emitted('saved')?.[0]?.[0]
      expect(savedEvent).toBeDefined()
      expect((savedEvent as { frequency: string }).frequency).toBe('daily')
    })

    it('sets interval to 1 by default for daily recurrence', async () => {
      const props = {
        isOpen: true,
        publication,
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      await selectFrequency('daily')
      await nextTick()
      await setStartDate('2099-12-31T12:00')
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const savedEvent = wrapper.emitted('saved')?.[0]?.[0]
      expect(savedEvent).toBeDefined()
      expect((savedEvent as { interval: number }).interval).toBe(1)
    })
  })

  describe('weekly recurrence', () => {
    it('renders weekly frequency option', async () => {
      const props = {
        isOpen: true,
        publication,
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      await selectFrequency('weekly')
      await nextTick()

      const checkboxes = bodyQueryAll('input[type="checkbox"]')
      checkboxes.forEach((checkbox) => {
        checkbox.dispatchEvent(new Event('change', { bubbles: true }))
      })
      await nextTick()

      await setStartDate('2099-12-31T12:00')
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeDefined()
      const savedEvent = wrapper.emitted('saved')?.[0]?.[0]
      expect(savedEvent).toBeDefined()
      expect((savedEvent as { frequency: string }).frequency).toBe('weekly')
    })

    it('fails validation when no weekdays are selected for weekly', async () => {
      const props = {
        isOpen: true,
        publication,
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      await selectFrequency('weekly')
      await nextTick()

      const checkboxes = bodyQueryAll('input[type="checkbox"]')
      const selectedCheckbox = checkboxes.find(
        (checkbox) => checkbox instanceof HTMLInputElement && checkbox.checked,
      )
      selectedCheckbox?.dispatchEvent(new Event('change', { bubbles: true }))
      await nextTick()

      await setStartDate('2099-12-31T12:00')
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeUndefined()
      const errorEl = bodyQuery('[data-testid="recurrence-error"]')
      expect(errorEl?.textContent).toContain('weekdayRequired')
    })

    it('accepts valid weekly with weekdays selected', async () => {
      const props = {
        isOpen: true,
        publication,
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      await selectFrequency('weekly')
      await nextTick()
      await nextTick()

      const checkboxes = bodyQueryAll('input[type="checkbox"]')
      checkboxes.forEach((cb) => {
        ;(cb as HTMLInputElement).checked = false
        cb.dispatchEvent(new Event('change', { bubbles: true }))
      })
      const mondayCb = checkboxes.find((checkbox) => (checkbox as HTMLInputElement).value === '1')
      if (mondayCb instanceof HTMLInputElement) {
        mondayCb.checked = true
        mondayCb.dispatchEvent(new Event('change', { bubbles: true }))
      }
      await nextTick()

      await setStartDate('2099-12-31T12:00')
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeDefined()
      const savedEvent = wrapper.emitted('saved')?.[0]?.[0]
      expect(savedEvent).toBeDefined()
      expect((savedEvent as { daysOfWeek: number[] }).daysOfWeek).toContain(1)
    })
  })

  describe('monthly recurrence', () => {
    it('renders and accepts valid monthly recurring schedule', async () => {
      const props = {
        isOpen: true,
        publication,
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      await selectFrequency('monthly')
      await nextTick()
      await nextTick()

      const dayInput = bodyQuery('input[data-testid="day-of-month-input"]')
      if (dayInput instanceof HTMLInputElement) {
        dayInput.value = '15'
        dayInput.dispatchEvent(new Event('input', { bubbles: true }))
        dayInput.dispatchEvent(new Event('change', { bubbles: true }))
      }
      await nextTick()

      await setStartDate('2099-12-31T12:00')
      await nextTick()

      submitForm()
      await flushPromises()
      await nextTick()
      await nextTick()

      const emitted = wrapper.emitted()
      expect(emitted?.saved).toBeDefined()
      const savedEvent = wrapper.emitted('saved')?.[0]?.[0]
      expect(savedEvent).toBeDefined()
      expect((savedEvent as { dayOfMonth: number }).dayOfMonth).toBe(15)
    })
  })

  describe('error states', () => {
    it('shows error when frequency is missing', async () => {
      const props = {
        isOpen: true,
        publication,
        schedule: null,
      }

      const wrapper = mount(RecurringScheduleModal, {
        props,
        global: { plugins: [pinia], mocks: { $t: (k: string) => k } },
      })

      await flushPromises()
      await nextTick()

      await setStartDate('2000-01-01T12:00')
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
