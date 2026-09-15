import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import WaitlistEntryView from './WaitlistEntryView.vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

const mockEntry = {
  id: 'entry-1',
  email: 'test@example.com',
  status: 'INVITED',
  joinedAt: '2024-01-01T00:00:00Z',
  invitedAt: '2024-01-02T00:00:00Z',
  cancelledAt: null,
  source: 'web',
  preferredLocale: 'en',
  earlyAccessConsent: true,
  marketingConsent: false,
  consentVersion: 'v1.0',
  metadataSummary: { formId: 'form-1', campaign: 'summer-2024' },
  version: 3,
  invitationHistory: [
    {
      id: 'inv-1',
      status: 'ACTIVE',
      issuedAt: '2024-01-02T00:00:00Z',
      expiresAt: '2024-01-09T00:00:00Z',
      deliveryStatus: 'SENT',
    },
  ],
}

const mockRequest = vi.fn()
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({
    hasPermission: () => true,
    request: mockRequest,
  }),
}))

function createWrapper(entryId = 'entry-1') {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        waitlist: {
          title: 'Waitlist',
          invitationHistory: 'Invitation History',
          resend: 'Resend Invitation',
          consentDetails: 'Consent Details',
          consentVersion: 'Consent Version',
          metadata: 'Metadata',
          invite: 'Invite',
          earlyAccessConsent: 'Early Access Consent',
          marketingConsent: 'Marketing Consent',
        },
        common: { loading: 'Loading...', error: 'Error', noData: 'No data', actions: 'Actions' },
      },
    },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/waitlist', name: 'waitlist', component: { template: '<div />' } },
      { path: '/admin/waitlist/:entryId', name: 'waitlist-entry', component: WaitlistEntryView },
    ],
  })
  router.push({ name: 'waitlist-entry', params: { entryId } })

  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(WaitlistEntryView, {
    global: {
      plugins: [router, pinia, i18n],
      stubs: {
        RouterLink: true,
        RouterView: true,
      },
    },
  })
}

describe('WaitlistEntryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockEntry),
    })
  })

  describe('basic render', () => {
    it('renders entry details', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).toContain('test@example.com')
    })
  })

  describe('invitation history', () => {
    it('renders invitation history table', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const table = wrapper.find('table[aria-label="Invitation History"]')
      expect(table.exists()).toBe(true)
    })
  })

  describe('consent fields', () => {
    it('renders consent fields when present', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).toContain('Consent Details')
    })

    it('renders consent version value', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).toContain('v1.0')
    })

    it('renders early access consent value', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).toContain('Yes')
    })

    it('renders marketing consent value', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).toContain('No')
    })
  })

  describe('metadata summary', () => {
    it('renders metadata summary section when present', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).toContain('Metadata')
    })

    it('renders metadata key-value pairs', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).toContain('form-1')
      expect(wrapper.text()).toContain('summer-2024')
    })

    it('does not render metadata section when empty', async () => {
      mockRequest.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({
          ...mockEntry,
          metadataSummary: {},
        }),
      })
      const wrapper = createWrapper()
      await flushPromises()
      expect(wrapper.text()).not.toContain('Metadata')
    })
  })

  describe('resend functionality', () => {
    it('resend button is visible when canResend and has active invitation', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const buttons = wrapper.findAll('button')
      const resendButton = buttons.find(b => b.text().includes('Resend Invitation'))
      expect(resendButton?.exists()).toBe(true)
    })

    it('resend button is not visible when no active invitation', async () => {
      mockRequest.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({
          ...mockEntry,
          invitationHistory: [
            {
              id: 'inv-1',
              status: 'USED',
              issuedAt: '2024-01-02T00:00:00Z',
              expiresAt: '2024-01-09T00:00:00Z',
              deliveryStatus: 'SENT',
            },
          ],
        }),
      })
      const wrapper = createWrapper()
      await flushPromises()
      const buttons = wrapper.findAll('button')
      const resendButton = buttons.find(b => b.text().includes('Resend Invitation'))
      expect(resendButton?.exists()).toBeFalsy()
    })

    it('clicking resend button updates dialog state', async () => {
      const wrapper = createWrapper()
      await flushPromises()
      const buttons = wrapper.findAll('button')
      const resendButton = buttons.find(b => b.text().includes('Resend Invitation'))
      await resendButton!.trigger('click')
      await flushPromises()
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    })
  })
})
