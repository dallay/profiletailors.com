import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import NotificationsView from './NotificationsView.vue'

const mockRequest = vi.fn()
const mockHasPermission = vi.fn(() => true)
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest, hasPermission: mockHasPermission }),
}))

const messages = {
  en: {
    notifications: {
      title: 'Notifications',
      channel: 'Channel',
      template: 'Template',
      recipient: 'Recipient',
      error: 'Error',
      retry: 'Retry',
      retryConfirm: 'Retry delivery to {recipient}? A new attempt will be scheduled.',
      retrySuccess: 'Retry scheduled.',
      retryNotEligible: 'This notification cannot be retried from Back Office.',
      empty: 'No notifications match the current filters.',
      allStatuses: 'All statuses',
      allChannels: 'All channels',
      filterStatus: 'Filter by status',
      filterChannel: 'Filter by channel',
      statuses: { pending: 'Pending', sent: 'Sent', failed: 'Failed' },
    },
    common: {
      loading: 'Loading...',
      error: 'An error occurred.',
      createdAt: 'Created',
      status: 'Status',
      actions: 'Actions',
    },
  },
}

const failedRow = {
  id: 'ntf-1',
  channel: 'EMAIL',
  templateId: 'platform.password-recovery',
  recipient: 'ops@example.com',
  status: 'FAILED',
  errorMessage: 'smtp timeout',
  createdAt: '2026-09-21T10:00:00Z',
  sentAt: null,
  failedAt: '2026-09-21T10:01:00Z',
}

function listResponse(data: unknown[] = [failedRow]) {
  return {
    ok: true,
    json: () =>
      Promise.resolve({
        data,
        meta: { currentPage: 0, pageSize: 25, totalElements: data.length, totalPages: 1 },
      }),
  }
}

function createView() {
  setActivePinia(createPinia())
  const i18n = createI18n({ legacy: false, locale: 'en', messages })
  return mount(NotificationsView, {
    global: {
      plugins: [i18n],
      stubs: {
        Table: { template: '<table><slot /></table>' },
        PaginationControls: { template: '<div />' },
      },
    },
  })
}

describe('NotificationsView', () => {
  beforeEach(() => {
    mockRequest.mockReset()
    mockHasPermission.mockReset()
    mockHasPermission.mockReturnValue(true)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows a loading state before the list resolves', () => {
    mockRequest.mockReturnValue(new Promise(() => {}))
    const wrapper = createView()
    expect(wrapper.text()).toContain('Loading...')
  })

  it('renders failed notifications from the admin API', async () => {
    mockRequest.mockResolvedValueOnce(listResponse())
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.text()).toContain('ops@example.com')
    expect(wrapper.text()).toContain('platform.password-recovery')
    expect(wrapper.text()).toContain('Failed')
  })

  it('hides retry when the caller lacks manage permission', async () => {
    mockHasPermission.mockReturnValue(false)
    mockRequest.mockResolvedValueOnce(listResponse())
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.findAll('button').some((button) => button.text() === 'Retry')).toBe(false)
  })

  it('retries a failed notification with an idempotency header', async () => {
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true),
    )
    vi.stubGlobal('crypto', { randomUUID: () => 'retry-key-1' })
    mockRequest
      .mockResolvedValueOnce(listResponse())
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ data: failedRow }) })
      .mockResolvedValueOnce(listResponse())

    const wrapper = createView()
    await flushPromises()

    const retryButton = wrapper.findAll('button').find((button) => button.text() === 'Retry')
    expect(retryButton).toBeDefined()
    await retryButton?.trigger('click')
    await flushPromises()

    expect(mockRequest).toHaveBeenCalledWith(
      '/api/admin/notifications/ntf-1/retry',
      expect.objectContaining({
        method: 'POST',
        headers: { 'X-Idempotency-Key': 'retry-key-1' },
      }),
    )
    expect(wrapper.find('[role="status"]').text()).toBe('Retry scheduled.')
  })

  it('shows an error when the initial fetch fails', async () => {
    mockRequest.mockResolvedValueOnce({ ok: false, status: 403 })
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('An error occurred.')
  })
})
