import { describe, it, expect } from 'vitest'
import { parseCsvLine, useBulkCsvParser } from './useBulkCsvParser'
import { BULK_CANONICAL_HEADER } from '@modules/publishing/domain/bulk'

describe('useBulkCsvParser', () => {
  it('exposes canonical header', () => {
    expect(BULK_CANONICAL_HEADER).toBe('bodyText,scheduledFor,timezone,media_urls,hashtags')
  })

  it('parses quoted fields with commas', () => {
    expect(parseCsvLine('"hello, world",2026-06-15T10:00:00Z,UTC,,')).toEqual([
      'hello, world',
      '2026-06-15T10:00:00Z',
      'UTC',
      '',
      '',
    ])
  })

  it('handles BOM prefix', () => {
    const { parse } = useBulkCsvParser()
    const csv = `\uFEFF${BULK_CANONICAL_HEADER}\nHello,2026-06-15T10:00:00Z,UTC,,`
    const result = parse(csv)
    expect(result.headerValid).toBe(true)
    expect(result.rows).toHaveLength(1)
    expect(result.rows[0]?.bodyText).toBe('Hello')
  })

  it('skips blank lines', () => {
    const { parse } = useBulkCsvParser()
    const csv = `${BULK_CANONICAL_HEADER}\nHello,2026-06-15T10:00:00Z,UTC,,\n\nWorld,2026-06-16T10:00:00Z,UTC,,`
    const result = parse(csv)
    expect(result.rows).toHaveLength(2)
  })

  it('skips blank rows where body/scheduled/media empty', () => {
    const { parse } = useBulkCsvParser()
    const csv = `${BULK_CANONICAL_HEADER}\n,,,,\nHello,2026-06-15T10:00:00Z,UTC,,`
    const result = parse(csv)
    expect(result.rows).toHaveLength(1)
    expect(result.rows[0]?.bodyText).toBe('Hello')
  })

  it('validates canonical header case-insensitive', () => {
    const { parse } = useBulkCsvParser()
    const csv = `BodyText,ScheduledFor,Timezone,media_urls,hashtags\nHello,2026-06-15T10:00:00Z,UTC,,`
    expect(parse(csv).headerValid).toBe(true)
  })

  it('invalid header yields headerValid false', () => {
    const { parse } = useBulkCsvParser()
    const csv = `wrong,header\nHello,2026-06-15T10:00:00Z`
    expect(parse(csv).headerValid).toBe(false)
  })

  it('handles escaped quotes', () => {
    expect(parseCsvLine('"say ""hi""",2026-06-15T10:00:00Z,UTC,,')).toEqual([
      'say "hi"',
      '2026-06-15T10:00:00Z',
      'UTC',
      '',
      '',
    ])
  })

  it('returns empty for blank csv', () => {
    const { parse } = useBulkCsvParser()
    expect(parse('').rows).toHaveLength(0)
    expect(parse('   \n  ').rows).toHaveLength(0)
  })
})
