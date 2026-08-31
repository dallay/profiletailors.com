import {
  BULK_CANONICAL_HEADER,
  type ParsedCsvResult,
  type ParsedCsvRow,
} from '@modules/publishing/domain/bulk'

export function parseCsvLine(line: string): string[] {
  const result: string[] = []
  let current = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const c = line.charAt(i)
    if (c === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"'
        i++
      } else {
        inQuotes = !inQuotes
      }
    } else if (c === ',' && !inQuotes) {
      result.push(current)
      current = ''
    } else {
      current += c
    }
  }
  result.push(current)
  return result.map((v) => v.trim())
}

export function useBulkCsvParser(): {
  parse: (csvText: string) => ParsedCsvResult
  parseCsvLine: (line: string) => string[]
} {
  function parse(csvText: string): ParsedCsvResult {
    const normalized = csvText.replace(/^\uFEFF/, '')
    if (normalized.trim() === '') return { header: [], rows: [], headerValid: false }
    const rawLines = normalized.split(/\r?\n/)
    const headerLine = rawLines[0]?.trim() ?? ''
    if (headerLine === '') return { header: [], rows: [], headerValid: false }
    const header = parseCsvLine(headerLine).map((h) => h.trim())
    const canonical = BULK_CANONICAL_HEADER.split(',')
    const headerValid =
      header.length === canonical.length &&
      header.map((h) => h.toLowerCase()).join(',') ===
        canonical.map((h) => h.toLowerCase()).join(',')

    const indexMap = new Map<string, number>()
    header.forEach((h, i) => indexMap.set(h.toLowerCase(), i))

    const bodyIdx = indexMap.get('bodytext') ?? 0
    const scheduledIdx = indexMap.get('scheduledfor') ?? 1
    const timezoneIdx = indexMap.get('timezone') ?? 2
    const mediaIdx = indexMap.get('media_urls') ?? 3
    const hashtagsIdx = indexMap.get('hashtags') ?? 4

    const rows: ParsedCsvRow[] = []
    let rowIndex = 0
    for (const rawLine of rawLines.slice(1)) {
      if (rawLine.trim() === '') continue
      const cols = parseCsvLine(rawLine)
      const padded =
        cols.length < header.length
          ? [...cols, ...Array(header.length - cols.length).fill('')]
          : cols
      const bodyText = padded[bodyIdx] ?? ''
      const scheduledFor = padded[scheduledIdx] ?? ''
      const timezone = padded[timezoneIdx] ?? ''
      const mediaUrls = padded[mediaIdx] ?? ''
      const hashtags = padded[hashtagsIdx] ?? ''
      if (bodyText.trim() === '' && scheduledFor.trim() === '' && mediaUrls.trim() === '') continue
      rows.push({ rowIndex, bodyText, scheduledFor, timezone, mediaUrls, hashtags })
      rowIndex++
    }
    return { header, rows, headerValid }
  }

  return { parse, parseCsvLine }
}
