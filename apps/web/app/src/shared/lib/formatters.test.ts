import { describe, it, expect, vi, afterEach } from 'vitest'
import { formatNumber, formatPercent, formatDelta, formatRelativeTime } from './formatters'

describe('formatNumber', () => {
  it('returns plain number under 1000', () => {
    expect(formatNumber(42)).toBe('42')
  })

  it('formats thousands with K suffix', () => {
    expect(formatNumber(1_500)).toBe('1.5K')
  })

  it('formats millions with M suffix', () => {
    expect(formatNumber(2_300_000)).toBe('2.3M')
  })

  it('formats exact 1000', () => {
    expect(formatNumber(1_000)).toBe('1.0K')
  })

  it('formats exact 1_000_000', () => {
    expect(formatNumber(1_000_000)).toBe('1.0M')
  })

  it('handles zero', () => {
    expect(formatNumber(0)).toBe('0')
  })
})

describe('formatPercent', () => {
  it('formats with one decimal', () => {
    expect(formatPercent(3.456)).toBe('3.5%')
  })

  it('formats zero', () => {
    expect(formatPercent(0)).toBe('0.0%')
  })

  it('formats whole number', () => {
    expect(formatPercent(100)).toBe('100.0%')
  })
})

describe('formatDelta', () => {
  it('formats positive delta with + sign', () => {
    expect(formatDelta(12)).toBe('+12%')
  })

  it('formats negative delta without + sign', () => {
    expect(formatDelta(-5)).toBe('-5%')
  })

  it('formats zero delta', () => {
    expect(formatDelta(0)).toBe('0%')
  })

  it('rounds to nearest integer', () => {
    expect(formatDelta(3.7)).toBe('+4%')
  })
})

describe('formatRelativeTime', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it.each([
    {
      now: '2026-06-13T12:00:30Z',
      input: '2026-06-13T12:00:00Z',
      expected: 'Just now',
      label: 'less than 1 minute',
    },
    {
      now: '2026-06-13T12:05:00Z',
      input: '2026-06-13T12:00:00Z',
      expected: '5m ago',
      label: 'minutes ago',
    },
    {
      now: '2026-06-13T14:00:00Z',
      input: '2026-06-13T12:00:00Z',
      expected: '2h ago',
      label: 'hours ago',
    },
    {
      now: '2026-06-16T12:00:00Z',
      input: '2026-06-13T12:00:00Z',
      expected: '3d ago',
      label: 'days ago',
    },
  ])('returns $expected for $label', ({ now, input, expected }) => {
    vi.setSystemTime(new Date(now))
    expect(formatRelativeTime(input)).toBe(expected)
  })

  it('returns formatted date for older than 7 days', () => {
    vi.setSystemTime(new Date('2026-06-21T12:00:00Z'))
    const result = formatRelativeTime('2026-06-13T12:00:00Z')
    expect(result).toMatch(/Jun 13/)
  })
})
