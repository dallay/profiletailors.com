import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import DashboardView from './DashboardView.vue'

const mockRequest = vi.fn()
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest }),
}))

const messages = {
  en: {
    dashboard: {
      title: 'Dashboard',
      subtitle: 'Operations overview',
      period: 'Period',
      days: '{n} days',
      periodLabel: 'LAST {n} DAYS',
      waitlistOverview: 'Waitlist overview',
      invitationHealth: 'Invitation health',
      systemHealth: 'System health',
      pendingEntries: 'Pending entries',
      invitedEntries: 'Invited',
      convertedEntries: 'Converted',
      cancelledEntries: 'Cancelled',
      activeInvitations: 'Active invitations',
      expiringIn24h: 'Expiring in 24h',
      expiringIn7d: 'Expiring in 7 days',
      failedDeliveries: 'Failed deliveries',
      registrationsInPeriod: 'Registrations',
    },
    common: {
      loading: 'Loading...',
      error: 'An error occurred.',
      noData: 'No data available.',
      retry: 'Retry',
    },
  },
}

const summary = {
  pendingCount: 3,
  invitedCount: 2,
  convertedCount: 1,
  cancelledCount: 0,
  activeInvitationCount: 4,
  invitationsExpiringIn24h: 1,
  invitationsExpiringIn7d: 0,
  failedDeliveryCount: 2,
  registrationsInPeriod: 6,
  periodDays: 30,
}

function createView() {
  return mount(DashboardView, {
    global: { plugins: [createI18n({ legacy: false, locale: 'en', messages })] },
  })
}

describe('DashboardView', () => {
  beforeEach(() => mockRequest.mockReset())
  afterEach(() => vi.restoreAllMocks())

  it('shows a loading output while dashboard data is pending', () => {
    let resolveRequest!: (response: { ok: boolean; json: () => Promise<typeof summary> }) => void
    mockRequest.mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve
      }),
    )
    const wrapper = createView()

    expect(wrapper.get('output').text()).toBe('Loading...')
    resolveRequest({ ok: true, json: () => Promise.resolve(summary) })
  })

  it('renders waitlist, invitation, and delivery metrics from the API', async () => {
    mockRequest.mockResolvedValue({ ok: true, json: () => Promise.resolve(summary) })
    const wrapper = createView()
    await flushPromises()

    expect(mockRequest).toHaveBeenCalledWith('/api/admin/dashboard?periodDays=30')
    expect(wrapper.text()).toContain('Pending entries')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('Active invitations')
    expect(wrapper.text()).toContain('Failed deliveries')
    expect(wrapper.text()).toContain('Registrations')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    await wrapper.get('select').setValue(7)
    await flushPromises()
    expect(mockRequest).toHaveBeenLastCalledWith('/api/admin/dashboard?periodDays=7')
  })

  it('shows an error and retries the dashboard request', async () => {
    mockRequest
      .mockResolvedValueOnce({ ok: false, status: 503 })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(summary) })
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('An error occurred.')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Waitlist overview')
    expect(mockRequest).toHaveBeenCalledTimes(2)
  })
})
