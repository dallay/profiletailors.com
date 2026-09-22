import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { ref } from 'vue'
import GovernanceView from './GovernanceView.vue'

const mockRequest = vi.fn()
const mockHasPermission = vi.fn<(permission: string) => boolean>(() => true)
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest, hasPermission: mockHasPermission }),
}))

const routeParams = ref<Record<string, string>>({})
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams.value }),
  useRouter: () => ({ push: mockPush }),
}))

const messages = {
  en: {
    governance: {
      title: 'Takedown Governance',
      reportId: 'Report ID',
      workspace: 'Workspace',
      assetId: 'Asset ID',
      reportedBy: 'Reported By',
      reason: 'Reason',
      status: 'Status',
      reportedAt: 'Reported At',
      reviewedAt: 'Reviewed At',
      reviewer: 'Reviewer',
      rejectionReason: 'Rejection Reason',
      reporterEmail: 'Reporter Email',
      mediaUrl: 'Media URL',
      filterByStatus: 'Filter by status',
      allStatuses: 'All statuses',
      approve: 'Approve Takedown',
      reject: 'Reject Report',
      approveConfirm: 'Approve this takedown request?',
      rejectConfirm: 'Reject this report?',
      noAssetStatus: 'No asset status available',
      assetStatus: { active: 'Active', suspended: 'Suspended', deleted: 'Deleted' },
      statuses: { REPORTED: 'Reported', APPROVED: 'Approved', DISMISSED: 'Dismissed' },
    },
    common: {
      loading: 'Loading...',
      error: 'An error occurred.',
      back: 'Back',
      createdAt: 'Created',
      status: 'Status',
      actions: 'Actions',
    },
  },
}

const reportedRow = {
  reportId: 'rep-1',
  workspaceId: 'ws-1',
  assetId: 'asset-1',
  reportedById: 'user-1',
  reason: 'copyright',
  status: 'REPORTED',
  rejectionReason: null,
  reviewedById: null,
  reviewedAt: null,
  reporterEmail: 'reporter@example.com',
  mediaReferenceUrl: 'https://example.com/media/asset-1',
  createdAt: '2026-09-22T10:00:00Z',
  updatedAt: '2026-09-22T10:00:00Z',
  assetStatus: null,
}

function listResponse(data: unknown[] = [reportedRow]) {
  return {
    ok: true,
    json: () =>
      Promise.resolve({
        items: data,
        page: 0,
        size: 20,
        totalElements: data.length,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      }),
  }
}

function createView() {
  setActivePinia(createPinia())
  const i18n = createI18n({ legacy: false, locale: 'en', messages })
  return mount(GovernanceView, {
    global: {
      plugins: [i18n],
      stubs: {
        Table: { template: '<table><slot /></table>' },
        PaginationControls: { template: '<div />' },
      },
    },
  })
}

describe('GovernanceView', () => {
  beforeEach(() => {
    mockRequest.mockReset()
    mockHasPermission.mockReset()
    mockHasPermission.mockReturnValue(true)
    mockPush.mockReset()
    routeParams.value = {}
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows a loading state before the list resolves', () => {
    mockRequest.mockReturnValue(new Promise(() => {}))
    const wrapper = createView()
    expect(wrapper.text()).toContain('Loading...')
  })

  it('renders reported takedowns from the admin API', async () => {
    mockRequest.mockResolvedValueOnce(listResponse())
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.text()).toContain('rep-1')
    expect(wrapper.text()).toContain('ws-1')
    expect(wrapper.text()).toContain('Reported')
  })

  it('hides approve and reject when the caller lacks manage permission', async () => {
    mockHasPermission.mockImplementation(
      (permission: string) => permission === 'platform.governance.read',
    )
    mockRequest.mockResolvedValueOnce(listResponse())
    const wrapper = createView()
    await flushPromises()

    const buttons = wrapper.findAll('button').map((button) => button.text())
    expect(buttons).not.toContain('Approve Takedown')
    expect(buttons).not.toContain('Reject Report')
  })

  it('shows an error when the initial fetch fails', async () => {
    mockRequest.mockResolvedValueOnce({ ok: false, status: 403 })
    const wrapper = createView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('An error occurred.')
  })

  it('fetches report detail when a report id is routed', async () => {
    routeParams.value = { reportId: 'rep-1' }
    mockRequest.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(reportedRow) })
    const wrapper = createView()
    await flushPromises()

    expect(mockRequest).toHaveBeenCalledWith(
      '/api/admin/takedown-reports/rep-1',
      expect.objectContaining({}),
    )
    expect(wrapper.text()).toContain('rep-1')
    expect(wrapper.text()).toContain('reporter@example.com')
  })

  it('approves a reported takedown with confirm and idempotency header', async () => {
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true),
    )
    routeParams.value = { reportId: 'rep-1' }
    mockRequest
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(reportedRow) })
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ ...reportedRow, status: 'APPROVED' }),
      })
    const wrapper = createView()
    await flushPromises()

    const approveButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Approve Takedown')
    expect(approveButton).toBeDefined()
    await approveButton?.trigger('click')
    await flushPromises()

    expect(mockRequest).toHaveBeenCalledWith(
      '/api/admin/takedown-reports/rep-1/approve',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
      }),
    )
    expect(wrapper.text()).toContain('Approved')
  })
})
