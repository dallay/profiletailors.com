import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BulkPreviewTable from './BulkPreviewTable.vue'

describe('BulkPreviewTable', () => {
  it('renders rows with errors', () => {
    const wrapper = mount(BulkPreviewTable, {
      props: {
        rows: [
          { rowIndex: 0, status: 'VALID', errors: [], bodyText: 'Hello', scheduledFor: '2026-06-15T10:00:00Z' },
          { rowIndex: 1, status: 'INVALID', errors: [{ code: 'INVALID_DATE', message: 'bad date' }], bodyText: '', scheduledFor: 'not-a-date' },
        ],
      },
    })
    expect(wrapper.get('[data-testid="bulk-row-0"]').isVisible()).toBe(true)
    expect(wrapper.get('[data-testid="bulk-error-1-INVALID_DATE"]').text()).toContain('INVALID_DATE')
    expect(wrapper.get('[data-testid="bulk-row-status-1"]').text()).toBe('INVALID')
  })

  it('shows duplicate warning', () => {
    const wrapper = mount(BulkPreviewTable, {
      props: {
        rows: [{ rowIndex: 1, status: 'VALID', errors: [{ code: 'DUPLICATE', message: 'duplicate' }] } as any],
      },
    })
    expect(wrapper.get('[data-testid="bulk-error-1-DUPLICATE"]').classes()).toContain('text-warning')
  })

  it('shows empty state', () => {
    const wrapper = mount(BulkPreviewTable, { props: { rows: [] } })
    expect(wrapper.get('[data-testid="bulk-preview-empty"]').isVisible()).toBe(true)
  })
})
