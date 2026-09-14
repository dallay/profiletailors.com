import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BulkPreviewTable from './BulkPreviewTable.vue'

describe('BulkPreviewTable', () => {
  it('renders rows with errors', () => {
    const wrapper = mount(BulkPreviewTable, {
      props: {
        rows: [
          {
            rowIndex: 0,
            status: 'VALID',
            errors: [],
            bodyText: 'Hello',
            scheduledFor: '2026-06-15T10:00:00Z',
          },
          {
            rowIndex: 1,
            status: 'INVALID',
            errors: [{ code: 'INVALID_DATE', message: 'bad date' }],
            bodyText: '',
            scheduledFor: 'not-a-date',
          },
        ],
      },
    })
    expect(wrapper.get('[data-testid="bulk-row-0"]').isVisible()).toBe(true)
    expect(wrapper.get('[data-testid="bulk-error-1-INVALID_DATE"]').text()).toContain(
      'INVALID_DATE',
    )
    expect(wrapper.get('[data-testid="bulk-row-status-1"]').text()).toBe('INVALID')
  })

  it('shows duplicate warning', () => {
    const wrapper = mount(BulkPreviewTable, {
      props: {
        rows: [
          {
            rowIndex: 1,
            status: 'VALID',
            errors: [{ code: 'DUPLICATE', message: 'duplicate row' }],
            bodyText: 'dup',
            scheduledFor: '2026-06-15T10:00:00Z',
          },
        ],
      },
    })
    expect(wrapper.get('[data-testid="bulk-error-1-DUPLICATE"]').text()).toContain('DUPLICATE')
    expect(wrapper.get('[data-testid="bulk-error-1-DUPLICATE"]').text()).toContain('duplicate row')
  })

  it('shows empty state', () => {
    const wrapper = mount(BulkPreviewTable, { props: { rows: [] } })
    expect(wrapper.get('[data-testid="bulk-preview-empty"]').isVisible()).toBe(true)
  })

  it('associates editable inputs with labels', () => {
    const wrapper = mount(BulkPreviewTable, {
      props: {
        editable: true,
        rows: [
          {
            rowIndex: 0,
            status: 'VALID',
            errors: [],
            bodyText: 'Hello',
            scheduledFor: '2026-06-15T10:00:00Z',
          },
          {
            rowIndex: 1,
            status: 'VALID',
            errors: [],
            bodyText: 'World',
            scheduledFor: '2026-06-16T10:00:00Z',
          },
        ],
      },
    })
    for (const rowIndex of [0, 1]) {
      for (const field of ['body', 'scheduled']) {
        const input = wrapper.get(`[data-testid="bulk-row-${field}-${rowIndex}"]`)
        const id = input.attributes('id')
        expect(id).toBeTruthy()
        expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      }
    }
    const ids = wrapper.findAll('input').map((input) => input.attributes('id'))
    expect(new Set(ids).size).toBe(ids.length)
  })
})
