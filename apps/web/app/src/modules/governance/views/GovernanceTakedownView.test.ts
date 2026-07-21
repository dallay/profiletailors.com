import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { TakedownReportResponse } from '@modules/governance/services/governance-api'

// ---------------------------------------------------------------------------
// Mocks — hoisted for vi.mock factories
// ---------------------------------------------------------------------------

const { mockListReports, mockApprove, mockReject } = vi.hoisted(() => ({
  mockListReports: vi.fn(),
  mockApprove: vi.fn(),
  mockReject: vi.fn(),
}))

vi.mock('@modules/governance/services/governance-api', () => ({
  listTakedownReports: mockListReports,
  approveTakedown: mockApprove,
  rejectTakedown: mockReject,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'en' },
  }),
}))

vi.mock('@/components/ui/button', () => ({
  Button: {
    name: 'Button',
    props: ['type', 'variant', 'size', 'disabled'],
    emits: ['click'],
    template: '<button :disabled="disabled" @click.stop="$emit(\'click\')"><slot /></button>',
  },
}))

vi.mock('@/components/ui/badge', () => ({
  Badge: { name: 'Badge', template: '<span class="ui-badge"><slot /></span>' },
}))

vi.mock('@/components/ui/alert-dialog', () => ({
  AlertDialog: { template: '<div class="ui-alert-dialog"><slot /></div>' },
  AlertDialogTrigger: { template: '<span class="ui-alert-dialog-trigger"><slot /></span>' },
  AlertDialogContent: { template: '<div class="ui-alert-dialog-content"><slot /></div>' },
  AlertDialogHeader: { template: '<div class="ui-alert-dialog-header"><slot /></div>' },
  AlertDialogTitle: { template: '<span class="ui-alert-dialog-title"><slot /></span>' },
  AlertDialogDescription: { template: '<p class="ui-alert-dialog-description"><slot /></p>' },
  AlertDialogFooter: { template: '<div class="ui-alert-dialog-footer"><slot /></div>' },
  AlertDialogCancel: { template: '<button class="ui-alert-dialog-cancel"><slot /></button>' },
  AlertDialogAction: {
    template: '<button class="ui-alert-dialog-action" @click="$emit(\'click\')"><slot /></button>',
  },
}))

vi.mock('@/components/ui/label', () => ({
  Label: { template: '<label><slot /></label>' },
}))

vi.mock('@/components/ui/textarea', () => ({
  Textarea: { template: '<textarea />' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Shield: stub,
    Flag: stub,
    Check: stub,
    X: stub,
    AlertTriangle: stub,
    CheckCircle2: stub,
    Clock: stub,
  }
})

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeReport(overrides: Partial<TakedownReportResponse> = {}): TakedownReportResponse {
  return {
    reportId: 'rpt-1',
    workspaceId: 'ws-1',
    assetId: 'ast-1',
    reporterEmail: 'reporter@example.com',
    reason: 'Copyright infringement',
    status: 'REPORTED',
    createdAt: '2026-07-21T10:00:00Z',
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

import GovernanceTakedownView from './GovernanceTakedownView.vue'

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('GovernanceTakedownView.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  function mountView(reports: TakedownReportResponse[]) {
    mockListReports.mockResolvedValue(reports)
    return shallowMount(GovernanceTakedownView, {
      global: {
        stubs: {
          Teleport: true,
        },
        mocks: { $t: (key: string) => key },
      },
    })
  }

  it('renders the title and subtitle', async () => {
    const wrapper = mountView([])
    await flushPromises()
    expect(wrapper.text()).toContain('governance.takedown.review.title')
    expect(wrapper.text()).toContain('governance.takedown.review.subtitle')
  })

  it('shows loading indicator while fetching', () => {
    mockListReports.mockReturnValue(new Promise(() => {}))
    const wrapper = mountView([])
    expect(wrapper.text()).toContain('governance.takedown.review.loading')
  })

  it('shows empty state when no reports returned', async () => {
    const wrapper = mountView([])
    await flushPromises()
    expect(wrapper.text()).toContain('governance.takedown.review.empty')
    expect(wrapper.text()).toContain('governance.takedown.review.emptyHint')
  })

  it('renders a list of reports', async () => {
    const reports = [
      makeReport({ reportId: 'rpt-1', reason: 'First report' }),
      makeReport({ reportId: 'rpt-2', reason: 'Second report' }),
    ]
    const wrapper = mountView(reports)
    await flushPromises()

    expect(wrapper.text()).toContain('First report')
    expect(wrapper.text()).toContain('Second report')
  })

  it('shows error banner when list fails', async () => {
    // Set rejection BEFORE mount — mountView calls mockResolvedValue, so inline mount
    mockListReports.mockRejectedValue(new Error('Failed to fetch'))
    const wrapper = shallowMount(GovernanceTakedownView, {
      global: {
        stubs: { Teleport: true },
        mocks: { $t: (key: string) => key },
      },
    })
    await flushPromises()

    // The component uses apiError.message ?? t('...'), so it shows the Error message
    expect(wrapper.text()).toContain('Failed to fetch')
  })

  it('calls approveTakedown when approve button is clicked', async () => {
    mockApprove.mockResolvedValue(makeReport({ reportId: 'rpt-1', status: 'APPROVED' }))
    mockListReports.mockResolvedValue([makeReport({ reportId: 'rpt-1' })])

    const wrapper = mount(GovernanceTakedownView, {
      global: {
        stubs: { Teleport: true },
        mocks: { $t: (key: string) => key },
      },
    })
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const approveBtn = buttons.find((b) => b.text() === 'governance.takedown.review.approveAction')
    expect(approveBtn).toBeDefined()

    // Dispatch a native click event directly on the button element
    approveBtn!.element.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(mockApprove).toHaveBeenCalledTimes(1)
    expect(mockApprove).toHaveBeenCalledWith('rpt-1')
  })

  // Reject-flow interaction test needs AlertDialog open/close orchestration
  // which depends on Radix-vue internals. The approve test above validates
  // the click → handler → API pattern for the simpler case.
  it.todo('calls rejectTakedown with rejection reason after opening reject dialog')
})
