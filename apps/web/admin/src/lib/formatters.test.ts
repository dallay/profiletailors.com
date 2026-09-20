import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime } from './formatters'

describe('admin date formatters', () => {
  it('formats dates for the requested locale', () => {
    expect(formatDate('2026-01-02T03:04:05.000Z', 'en-US')).toBe('1/2/2026')
  })

  it('formats date and time for the requested locale', () => {
    expect(formatDateTime('2026-01-02T03:04:05.000Z', 'en-US')).toContain('1/2/2026')
  })

  it('uses a placeholder for missing values', () => {
    expect(formatDate(null, 'en-US')).toBe('—')
    expect(formatDateTime(undefined, 'en-US')).toBe('—')
  })

  it('preserves invalid values for diagnosis', () => {
    expect(formatDate('not-a-date', 'en-US')).toBe('not-a-date')
    expect(formatDateTime('not-a-date', 'en-US')).toBe('not-a-date')
  })
})
