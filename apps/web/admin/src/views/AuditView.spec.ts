import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AuditView from './AuditView.vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

const mockAuditLogsResult = {
  items: [
    {
      eventId: 'evt-1',
      occurredAt: '2024-06-01T12:00:00Z',
      action: 'USER_DISABLE',
      targetType: 'USER',
      targetId: 'user-2',
      result: 'SUCCEEDED',
    },
    {
      eventId: 'evt-2',
      occurredAt: '2024-06-01T11:00:00Z',
      action: 'ROLE_ASSIGN',
      targetType: 'USER',
      targetId: 'user-3',
      result: 'REJECTED',
    },
  ],
  page: 0,
  size: 25,
  totalItems: 2,
  totalPages: 1,
  hasPrevious: false,
  hasNext: false,
}

const mockRequest = vi.fn()
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest }),
}))

async function createView() {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/audit', name: 'audit', component: AuditView }],
  })
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        audit: {
          title: 'Audit Logs',
          empty: 'No audit logs found',
          occurredAt: 'Occurred At',
          action: 'Action',
          filterByAction: 'Filter by action...',
          targetType: 'Target Type',
          targetId: 'Target ID',
          result: 'Result',
          allResults: 'All Results',
          results: {
            succeeded: 'Succeeded',
            rejected: 'Rejected',
            failed: 'Failed',
          },
        },
        common: {
          loading: 'Loading...',
          error: 'An error occurred',
        },
      },
    },
  })

  await router.push({ name: 'audit' })
  const wrapper = mount(AuditView, {
    global: {
      plugins: [router, i18n],
      stubs: {
        PaginationControls: {
          props: ['page', 'totalPages', 'hasPrevious', 'hasNext'],
          template: `
            <div class="pagination">
              <button class="prev" :disabled="!hasPrevious" @click="$emit('previous')">Prev</button>
              <button class="next" :disabled="!hasNext" @click="$emit('next')">Next</button>
            </div>
          `,
        },
      },
    },
  })
  return { wrapper, router }
}

describe('AuditView', () => {
  beforeEach(() => {
    mockRequest.mockReset()
  })

  it('renders audit log entries successfully', async () => {
    mockRequest.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(mockAuditLogsResult),
    })

    const { wrapper } = await createView()
    await flushPromises()

    expect(wrapper.text()).toContain('Audit Logs')
    expect(wrapper.text()).toContain('USER_DISABLE')
    expect(wrapper.text()).toContain('user-2')
    expect(wrapper.text()).toContain('Succeeded')
    expect(wrapper.text()).toContain('ROLE_ASSIGN')
    expect(wrapper.text()).toContain('Rejected')
  })

  it('shows error message when request fails', async () => {
    mockRequest.mockResolvedValueOnce({ ok: false, status: 500 })

    const { wrapper } = await createView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('An error occurred')
  })

  it('filters audit logs by action and result', async () => {
    mockRequest
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockAuditLogsResult) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockAuditLogsResult) })

    const { wrapper } = await createView()
    await flushPromises()

    const actionInput = wrapper.find('input[type="text"]')
    await actionInput.setValue('USER_DISABLE')

    await flushPromises()

    expect(mockRequest).toHaveBeenCalledTimes(2)
    expect(mockRequest.mock.calls[1][0]).toContain('action=USER_DISABLE')
  })
})
