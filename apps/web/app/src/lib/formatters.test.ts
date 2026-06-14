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

  it('returns Just now for less than 1 minute', () => {
    const now = new Date('2026-06-13T12:00:30Z')
    vi.setSystemTime(now)
    expect(formatRelativeTime('2026-06-13T12:00:00Z')).toBe('Just now')
  })

  it('returns minutes ago', () => {
    const now = new Date('2026-06-13T12:05:00Z')
    vi.setSystemTime(now)
    expect(formatRelativeTime('2026-06-13T12:00:00Z')).toBe('5m ago')
  })

  it('returns hours ago', () => {
    const now = new Date('2026-06-13T14:00:00Z')
    vi.setSystemTime(now)
    expect(formatRelativeTime('2026-06-13T12:00:00Z')).toBe('2h ago')
  })

  it('returns days ago', () => {
    const now = new Date('2026-06-16T12:00:00Z')
    vi.setSystemTime(now)
    expect(formatRelativeTime('2026-06-13T12:00:00Z')).toBe('3d ago')
  })

  it('returns formatted date for older than 7 days', () => {
    const now = new Date('2026-06-21T12:00:00Z')
    vi.setSystemTime(now)
    const result = formatRelativeTime('2026-06-13T12:00:00Z')
    expect(result).toMatch(/Jun 13/)
  })
})
