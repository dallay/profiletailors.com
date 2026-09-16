import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import WaitlistView from './WaitlistView.vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

const mockEntries = {
  items: [
    {
      id: 'entry-1',
      email: 'test1@example.com',
      normalizedEmail: 'test1@example.com',
      status: 'PENDING',
      joinedAt: '2024-01-01T00:00:00Z',
      invitedAt: null,
      waitlistKey: 'test-key',
      source: 'web',
      version: 1,
    },
    {
      id: 'entry-2',
      email: 'test2@example.com',
      normalizedEmail: 'test2@example.com',
      status: 'INVITED',
      joinedAt: '2024-01-02T00:00:00Z',
      invitedAt: '2024-01-03T00:00:00Z',
      waitlistKey: 'test-key',
      source: 'web',
      version: 2,
    },
  ],
  page: 0,
  size: 25,
  totalElements: 2,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
}

const mockSummary = {
  PENDING: 5,
  INVITED: 3,
  CONVERTED: 10,
  CANCELLED: 2,
}

const mockRequest = vi.fn()
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({
    hasPermission: () => true,
    request: mockRequest,
  }),
}))

function createWrapper() {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        waitlist: {
          title: 'Waitlist',
          statuses: {
            pending: 'Pending',
            invited: 'Invited',
            converted: 'Converted',
            cancelled: 'Cancelled',
          },
          filters: {
            status: 'Status',
            search: 'Search',
            all: 'All',
            waitlistKey: 'Waitlist key',
            joinedFrom: 'Joined from',
            joinedTo: 'Joined to',
            invitedFrom: 'Invited from',
            invitedTo: 'Invited to',
          },
          entries: 'Waitlist Entries',
          invite: 'Invite',
          bulkTooMany: 'Select up to {max} entries at a time',
          cancel: 'Cancel',
          cancelConfirmTitle: 'Confirm Cancel',
          cancelConfirmMessage: 'Are you sure you want to cancel',
          cancelReason: 'Cancel Reason',
          inviteConfirmTitle: 'Confirm Invite',
          joinedAt: 'Joined',
          invitedAt: 'Invited',
        },
        common: { loading: 'Loading...', error: 'Error', noData: 'No data', actions: 'Actions' },
      },
    },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/waitlist', name: 'waitlist', component: { template: '<div />' } },
      {
        path: '/admin/waitlist/:entryId',
        name: 'waitlist-entry',
        component: { template: '<div />' },
      },
    ],
  })
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(WaitlistView, {
    global: {
      plugins: [router, pinia, i18n],
      stubs: {
        RouterLink: true,
        RouterView: true,
      },
    },
  })
}

describe('WaitlistView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockImplementation((url: string) => {
      if (url.includes('/summary')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockSummary),
        })
      }
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockEntries),
      })
    })
  })

  describe('basic render', () => {
    it('renders page title', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.find('h1').text()).toBe('Waitlist')
    })
  })

  describe('summary card', () => {
    it('renders summary card with status counts when summary is present', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const summaryChips = wrapper.findAll('.admin-chip')
      expect(summaryChips).toHaveLength(4)
      expect(summaryChips[0]?.text()).toContain('Pending')
      expect(summaryChips[0]?.text()).toContain('5')
      expect(summaryChips[1]?.text()).toContain('Invited')
      expect(summaryChips[1]?.text()).toContain('3')
      expect(summaryChips[2]?.text()).toContain('Converted')
      expect(summaryChips[2]?.text()).toContain('10')
      expect(summaryChips[3]?.text()).toContain('Cancelled')
      expect(summaryChips[3]?.text()).toContain('2')
    })
  })

  describe('loading and error states', () => {
    it('shows loading state while fetching', async () => {
      let resolvePromise: (value: Response) => void = () => {}
      mockRequest.mockImplementation(
        () =>
          new Promise<Response>((resolve) => {
            resolvePromise = resolve
          }),
      )
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('Loading...')
      resolvePromise({ ok: true, json: () => Promise.resolve(mockEntries) } as Response)
      await flushPromises()
    })

    it('shows error state when fetch fails', async () => {
      mockRequest.mockResolvedValue({
        ok: false,
        status: 500,
      })
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.find('[role="alert"]').exists()).toBe(true)
      expect(wrapper.text()).toContain('Error')
    })
  })

  describe('filter functionality', () => {
    it('renders waitlistKey filter input', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const waitlistKeyInput = wrapper.find('input[aria-label="Waitlist key"]')
      expect(waitlistKeyInput.exists()).toBe(true)
    })

    it('renders date filter inputs for joined dates', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const joinedFromInput = wrapper.find('input[aria-label="Joined from"]')
      const joinedToInput = wrapper.find('input[aria-label="Joined to"]')
      expect(joinedFromInput.exists()).toBe(true)
      expect(joinedToInput.exists()).toBe(true)
    })

    it('renders date filter inputs for invited dates', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const invitedFromInput = wrapper.find('input[aria-label="Invited from"]')
      const invitedToInput = wrapper.find('input[aria-label="Invited to"]')
      expect(invitedFromInput.exists()).toBe(true)
      expect(invitedToInput.exists()).toBe(true)
    })

    it('sends date filters in request when set', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      mockRequest.mockClear()

      const joinedFromInput = wrapper.find('input[aria-label="Joined from"]')
      await joinedFromInput.setValue('2024-01-01')

      await flushPromises()
      expect(mockRequest).toHaveBeenCalledWith(expect.stringContaining('joinedFrom=2024-01-01'))
    })

    it('resets to page 0 when filter changes', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      mockRequest.mockClear()

      const searchInput = wrapper.find('input[type="search"]')
      await searchInput.setValue('test')
      await flushPromises()

      const calls = mockRequest.mock.calls
      const lastCall = calls[calls.length - 1]?.[0] as string
      expect(lastCall).toContain('page=0')
    })
  })

  describe('cancel functionality', () => {
    it('cancel button exists for cancellable entries', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const buttons = wrapper.findAll('button')
      const cancelButtons = buttons.filter((b) => b.text() === 'Cancel')
      expect(cancelButtons.length).toBeGreaterThan(0)
    })

    it('cancel button opens dialog', async () => {
      vi.stubGlobal('confirm', () => true)
      const wrapper = createWrapper()
      await flushPromises()

      const cancelButton = wrapper.findAll('button').find((b) => b.text() === 'Cancel')
      expect(cancelButton).toBeDefined()
      await cancelButton?.trigger('click')
      await flushPromises()

      expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
      vi.stubGlobal('confirm', () => {})
    })

    it('sends expectedVersion in cancel request', async () => {
      vi.stubGlobal('confirm', () => true)
      let cancelBody: { reason?: string; expectedVersion?: number } = {}

      mockRequest.mockImplementation((url: string, options?: RequestInit) => {
        if (url.includes('/cancel')) {
          if (options?.body) {
            cancelBody = JSON.parse(options.body as string)
          }
          return Promise.resolve({ ok: true, json: () => Promise.resolve({}) })
        }
        if (url.includes('/summary')) {
          return Promise.resolve({ ok: true, json: () => Promise.resolve(mockSummary) })
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve(mockEntries) })
      })

      const wrapper = createWrapper()
      await flushPromises()

      const cancelButton = wrapper.findAll('button').find((b) => b.text() === 'Cancel')
      expect(cancelButton).toBeDefined()
      await cancelButton?.trigger('click')
      await flushPromises()

      const dialog = wrapper.find('[role="dialog"]')
      expect(dialog.exists()).toBe(true)

      const reasonInput = dialog.find('#cancel-reason')
      expect(reasonInput.exists()).toBe(true)
      await reasonInput.setValue('Test reason')

      const confirmButton = dialog.find('.admin-button-danger')
      expect(confirmButton.exists()).toBe(true)
      await confirmButton.trigger('click')
      await flushPromises()

      expect(cancelBody.expectedVersion).toBeDefined()
      expect(cancelBody.expectedVersion).toBe(1)
      expect(cancelBody.reason).toBe('Test reason')
      vi.stubGlobal('confirm', () => {})
    })
  })

  describe('entry table', () => {
    it('renders entries in table', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const table = wrapper.find('table')
      expect(table.exists()).toBe(true)
      expect(wrapper.text()).toContain('test1@example.com')
      expect(wrapper.text()).toContain('test2@example.com')
    })

    it('displays status for each entry', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).toContain('Pending')
      expect(wrapper.text()).toContain('Invited')
    })
  })

  describe('bulk invite', () => {
    const bulkPayload = {
      results: [
        {
          entryId: 'entry-1',
          outcome: 'invited',
          invitationId: '00000000-0000-0000-0000-0000000000a1',
        },
        { entryId: 'entry-2', outcome: 'skipped', code: 'ALREADY_INVITED' },
      ],
      summary: { requested: 2, invited: 1, skipped: 1, failed: 0 },
    }

    beforeEach(() => {
      mockRequest.mockImplementation((url: string) => {
        if (url.includes('/invitations:bulk')) {
          return Promise.resolve({ ok: true, json: () => Promise.resolve(bulkPayload) })
        }
        if (url.includes('/summary')) {
          return Promise.resolve({ ok: true, json: () => Promise.resolve(mockSummary) })
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve(mockEntries) })
      })
    })

    it('renders a selection checkbox per entry when the operator can invite', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.findAll('[data-testid="bulk-select"]')).toHaveLength(2)
    })

    it('posts selected entry ids and renders per-entry outcomes', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const boxes = wrapper.findAll('[data-testid="bulk-select"]')
      expect(boxes).toHaveLength(2)
      await boxes[0]?.setValue(true)
      await boxes[1]?.setValue(true)
      await wrapper.get('[data-testid="bulk-invite"]').trigger('click')
      await flushPromises()

      const bulkCall = mockRequest.mock.calls.find((call) =>
        String(call[0]).includes('/invitations:bulk'),
      )
      expect(bulkCall).toBeDefined()
      expect(bulkCall?.[1]).toMatchObject({ method: 'POST' })
      const sentBody = String((bulkCall?.[1] as RequestInit | undefined)?.body ?? '')
      expect(sentBody).toContain('entry-1')
      expect(sentBody).toContain('entry-2')

      const results = wrapper.get('[data-testid="bulk-results"]')
      expect(results.text()).toContain('entry-1')
      expect(results.text()).toContain('invited')
      expect(results.text()).toContain('ALREADY_INVITED')

      const summaryCalls = mockRequest.mock.calls.filter((call) =>
        String(call[0]).includes('/summary'),
      )
      expect(summaryCalls).toHaveLength(2)
    })

    it('shows the generic error when the bulk request rejects', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      mockRequest.mockRejectedValueOnce(new Error('network down'))
      const boxes = wrapper.findAll('[data-testid="bulk-select"]')
      await boxes[0]?.setValue(true)
      await wrapper.get('[data-testid="bulk-invite"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[role="alert"]').text()).toContain('Error')
    })

    it('blocks submission above the entry maximum without sending the request', async () => {
      const manyEntries = {
        ...mockEntries,
        items: Array.from({ length: 51 }, (_, index) => ({
          id: `bulk-${index}`,
          email: `bulk-${index}@example.com`,
          normalizedEmail: `bulk-${index}@example.com`,
          status: 'PENDING',
          joinedAt: '2024-01-01T00:00:00Z',
          invitedAt: null,
          waitlistKey: 'test-key',
          source: 'web',
          version: 1,
        })),
        totalElements: 51,
      }
      mockRequest.mockImplementation((url: string) => {
        if (url.includes('/summary')) {
          return Promise.resolve({ ok: true, json: () => Promise.resolve(mockSummary) })
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve(manyEntries) })
      })
      const wrapper = createWrapper()
      await flushPromises()
      await wrapper.get('[data-testid="bulk-select-all"]').setValue(true)
      await wrapper.get('[data-testid="bulk-invite"]').trigger('click')
      await flushPromises()

      const bulkCalls = mockRequest.mock.calls.filter((call) =>
        String(call[0]).includes('/invitations:bulk'),
      )
      expect(bulkCalls).toHaveLength(0)
      expect(wrapper.get('[role="alert"]').text()).toContain('50')
    })

    it('surfaces the bulk error code when the request fails', async () => {
      mockRequest.mockImplementation((url: string) => {
        if (url.includes('/invitations:bulk')) {
          return Promise.resolve({
            ok: false,
            status: 400,
            json: () => Promise.resolve({ properties: { code: 'VALIDATION_ERROR' } }),
          })
        }
        if (url.includes('/summary')) {
          return Promise.resolve({ ok: true, json: () => Promise.resolve(mockSummary) })
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve(mockEntries) })
      })
      const wrapper = createWrapper()
      await flushPromises()
      const boxes = wrapper.findAll('[data-testid="bulk-select"]')
      await boxes[0]?.setValue(true)
      await wrapper.get('[data-testid="bulk-invite"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[role="alert"]').text()).toContain('VALIDATION_ERROR')
    })
  })
})
