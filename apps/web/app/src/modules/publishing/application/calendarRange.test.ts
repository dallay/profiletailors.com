import { describe, expect, it } from 'vitest'
import { getCalendarRange } from './calendarRange'

describe('getCalendarRange', () => {
  it('returns a Sunday through next Sunday week range', () => {
    const range = getCalendarRange('2026-06-17', 'week', 'UTC')

    expect(range.from).toBe('2026-06-14T00:00:00.000Z')
    expect(range.to).toBe('2026-06-21T00:00:00.000Z')
  })

  it('returns the first day through the next month exclusively', () => {
    const range = getCalendarRange('2026-02-15', 'month', 'UTC')

    expect(range.from).toBe('2026-02-01T00:00:00.000Z')
    expect(range.to).toBe('2026-03-01T00:00:00.000Z')
  })

  it('converts calendar boundaries in the selected timezone', () => {
    const range = getCalendarRange('2026-06-17', 'week', 'Europe/Madrid')

    expect(range.from).toBe('2026-06-13T22:00:00.000Z')
    expect(range.to).toBe('2026-06-20T22:00:00.000Z')
  })

  it('keeps a positive-offset calendar day stable across DST', () => {
    const range = getCalendarRange('2026-10-25', 'week', 'Australia/Sydney')

    expect(range.from).toBe('2026-10-24T13:00:00.000Z')
    expect(range.to).toBe('2026-10-31T13:00:00.000Z')
  })

  it('rejects malformed URL dates', () => {
    expect(() => getCalendarRange('not-a-date', 'week', 'UTC')).toThrow('Invalid calendar date')
  })

  it('rejects invalid calendar dates with valid regex pattern like 2026-02-31', () => {
    expect(() => getCalendarRange('2026-02-31', 'week', 'UTC')).toThrow('Invalid calendar date')
  })
})
