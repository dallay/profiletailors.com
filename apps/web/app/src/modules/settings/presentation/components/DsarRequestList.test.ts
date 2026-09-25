import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DsarRequestList from './DsarRequestList.vue'
import type { DsarRequest } from '@modules/settings/infrastructure/privacy.store'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('DsarRequestList', () => {
  const mockTableStubs = {
    global: {
      stubs: {
        Table: { template: '<table><slot /></table>' },
        TableHeader: { template: '<thead><slot /></thead>' },
        TableBody: { template: '<tbody><slot /></tbody>' },
        TableRow: { template: '<tr><slot /></tr>' },
        TableHead: { template: '<th><slot /></th>' },
        TableCell: { template: '<td><slot /></td>' },
        TableEmpty: { template: '<tr><td><slot /></td></tr>' },
        Button: { template: '<button><slot /></button>' },
        DsarStatusBadge: { template: '<span data-testid="status-badge"><slot /></span>' },
      },
    },
  }

  it('renders empty message when requests list is empty and not loading', () => {
    const wrapper = mount(DsarRequestList, {
      props: { requests: [], loading: false },
      ...mockTableStubs,
    })

    expect(wrapper.text()).toContain('settings.privacy.list.empty')
  })

  it('renders loading text when loading is true', () => {
    const wrapper = mount(DsarRequestList, {
      props: { requests: [], loading: true },
      ...mockTableStubs,
    })

    expect(wrapper.text()).toContain('common.loading')
  })

  it('renders request rows sorted by createdAt descending', () => {
    const requests: DsarRequest[] = [
      {
        id: 'req-1',
        type: 'ACCESS',
        status: 'COMPLETED',
        createdAt: '2026-01-01T10:00:00Z',
        updatedAt: '2026-01-01T10:00:00Z',
      },
      {
        id: 'req-2',
        type: 'DELETION',
        status: 'PENDING',
        createdAt: '2026-02-01T10:00:00Z',
        updatedAt: '2026-02-01T10:00:00Z',
      },
    ]

    const wrapper = mount(DsarRequestList, {
      props: { requests, loading: false },
      ...mockTableStubs,
    })

    const rows = wrapper.findAll('[data-testid="dsar-request-row"]')
    expect(rows).toHaveLength(2)
    // req-2 is newer so it should be rendered first
    expect(rows[0]?.text()).toContain('settings.privacy.form.type.DELETION')
    expect(rows[1]?.text()).toContain('settings.privacy.form.type.ACCESS')
  })

  it('shows download button only for COMPLETED EXPORT requests with resultRef', () => {
    const requests: DsarRequest[] = [
      {
        id: 'export-completed',
        type: 'EXPORT',
        status: 'COMPLETED',
        resultRef: 'export-file.json',
        createdAt: '2026-01-01T10:00:00Z',
        updatedAt: '2026-01-01T10:00:00Z',
      },
      {
        id: 'export-pending',
        type: 'EXPORT',
        status: 'PENDING',
        createdAt: '2026-01-02T10:00:00Z',
        updatedAt: '2026-01-02T10:00:00Z',
      },
    ]

    const wrapper = mount(DsarRequestList, {
      props: { requests, loading: false },
      ...mockTableStubs,
    })

    const downloadButtons = wrapper.findAll('[data-testid="dsar-download-btn"]')
    expect(downloadButtons).toHaveLength(1)
    const downloadLink = downloadButtons[0]?.find('a')
    expect(downloadLink?.attributes('href')).toBe(
      '/api/v1/privacy/requests/export-completed/download',
    )
    expect(downloadLink?.attributes('download')).toBe('export-file.json')
  })
})
