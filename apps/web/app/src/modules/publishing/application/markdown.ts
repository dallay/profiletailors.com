export type FormatResult = {
  text: string
  selectionStart: number
  selectionEnd: number
}

const SAFE_URL = /^(?:https?:\/\/|mailto:|www\.)/i

type ParsedBracketLink = {
  end: number
  label: string
  url: string
  image: boolean
}

function readBalancedDestination(
  text: string,
  openParen: number,
): { end: number; url: string } | null {
  let depth = 0
  for (let index = openParen; index < text.length; index++) {
    if (text[index] === '(') depth++
    else if (text[index] === ')') {
      depth--
      if (depth === 0) return { end: index + 1, url: text.slice(openParen + 1, index) }
    }
  }
  return null
}

function readBracketLink(text: string, start: number): ParsedBracketLink | null {
  const image = start > 0 && text[start - 1] === '!'
  const labelStart = start + 1
  const closeBracket = text.indexOf(']', labelStart)
  if (closeBracket === -1 || text[closeBracket + 1] !== '(') return null
  const destination = readBalancedDestination(text, closeBracket + 1)
  if (destination === null) return null
  return {
    end: destination.end,
    label: text.slice(labelStart, closeBracket),
    url: destination.url,
    image,
  }
}

function renderBracketLink(link: ParsedBracketLink): string {
  if (link.image) return link.label
  const trimmedUrl = link.url.trim()
  if (!SAFE_URL.test(trimmedUrl)) return link.label
  if (link.label.trim() === trimmedUrl) return trimmedUrl
  return link.label.trim() ? `${link.label} (${trimmedUrl})` : trimmedUrl
}

function stripLinksAndImages(text: string): string {
  let result = ''
  let cursor = 0
  while (cursor < text.length) {
    const openBracket = text.indexOf('[', cursor)
    if (openBracket === -1) return result + text.slice(cursor)
    const link = readBracketLink(text, openBracket)
    if (link === null) {
      result += text.slice(cursor, openBracket + 1)
      cursor = openBracket + 1
      continue
    }
    result += text.slice(cursor, link.image ? openBracket - 1 : openBracket)
    result += renderBracketLink(link)
    cursor = link.end
  }
  return result
}

function stripInlineMarkdown(text: string): string {
  return stripLinksAndImages(text)
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/\*(.+?)\*/g, '$1')
    .replace(/~~(.+?)~~/g, '$1')
    .replace(/`(.+?)`/g, '$1')
}

function stripHtmlTags(text: string): string {
  return text
    .replace(/<script\b[\s\S]*?<\/script>/gi, '')
    .replace(/<style\b[\s\S]*?<\/style>/gi, '')
    .replace(/<\/?[a-z][a-z0-9]*\b[^>]*>/gi, '')
}

export function stripMarkdownToPlainText(text: string): string {
  const lines = text.split('\n')
  const result: string[] = []
  let inCodeBlock = false

  for (const line of lines) {
    if (/^`{3}/.test(line.trim())) {
      inCodeBlock = !inCodeBlock
      continue
    }
    if (inCodeBlock) {
      result.push(line)
      continue
    }
    let processed = stripHtmlTags(line)
    processed = processed.replace(/^#{1,3}\s+/, '')
    processed = processed.replace(/^>\s?/, '')
    processed = processed.replace(/^[-*]\s+/, '')
    processed = processed.replace(/^\d+\.\s+/, '')
    result.push(stripInlineMarkdown(processed))
  }

  return result.join('\n')
}

function normalizeHashtag(tag: string): string {
  const body = tag
    .slice(1)
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, '')
  return body ? `#${body}` : ''
}

export function normalizeForSubmission(text: string): string {
  return stripMarkdownToPlainText(text)
    .replace(/[ \t]+/g, ' ')
    .split(/(\n+)/)
    .map((part) => {
      if (/^\n+$/.test(part)) return part
      return part
        .split(/\s+/)
        .map((word) => (word.startsWith('#') ? normalizeHashtag(word) : word))
        .join(' ')
    })
    .join('')
    .replace(/(\n)[ \t]+/g, '$1')
    .trim()
    .replace(/\n{3,}/g, '\n\n')
}

export function applyInlineFormat(
  text: string,
  selectionStart: number,
  selectionEnd: number,
  marker: string,
): FormatResult {
  const hasSelection = selectionStart !== selectionEnd

  if (!hasSelection) {
    const placeholder = 'text'
    const insertion = `${marker}${placeholder}${marker}`
    const newText = text.slice(0, selectionStart) + insertion + text.slice(selectionStart)
    return {
      text: newText,
      selectionStart: selectionStart + marker.length,
      selectionEnd: selectionStart + marker.length + placeholder.length,
    }
  }

  const selectedText = text.slice(selectionStart, selectionEnd)
  const before = text.slice(Math.max(0, selectionStart - marker.length), selectionStart)
  const after = text.slice(selectionEnd, selectionEnd + marker.length)

  if (before === marker && after === marker) {
    let runStart = selectionStart - marker.length
    while (runStart > 0 && text[runStart - 1] === marker[0]) runStart--
    let runEnd = selectionEnd + marker.length
    while (runEnd < text.length && text[runEnd] === marker[0]) runEnd++
    const leftRun = selectionStart - runStart
    const rightRun = runEnd - selectionEnd

    const isStandalone = leftRun === 1 && rightRun === 1

    if (isStandalone) {
      const newText =
        text.slice(0, selectionStart - marker.length) +
        selectedText +
        text.slice(selectionEnd + marker.length)
      return {
        text: newText,
        selectionStart: selectionStart - marker.length,
        selectionEnd: selectionEnd - marker.length,
      }
    }

    if (leftRun >= 2 && rightRun >= 2) {
      const removeStart = selectionStart - marker.length
      const removeEnd = selectionEnd + marker.length
      const newText = text.slice(0, removeStart) + selectedText + text.slice(removeEnd)
      return {
        text: newText,
        selectionStart: removeStart,
        selectionEnd: removeEnd - marker.length * 2,
      }
    }
  }

  const newText =
    text.slice(0, selectionStart) + marker + selectedText + marker + text.slice(selectionEnd)
  return {
    text: newText,
    selectionStart: selectionStart + marker.length,
    selectionEnd: selectionEnd + marker.length,
  }
}

export function applyLinePrefix(
  text: string,
  selectionStart: number,
  selectionEnd: number,
  prefix: string,
): FormatResult {
  const lineStart = text.lastIndexOf('\n', selectionStart - 1) + 1
  let lineEnd = text.indexOf('\n', selectionEnd)
  if (lineEnd === -1) lineEnd = text.length

  const block = text.slice(lineStart, lineEnd)
  const lines = block.split('\n')
  const allHavePrefix = lines.length > 0 && lines.every((line) => line.startsWith(prefix))

  const newLines = allHavePrefix
    ? lines.map((line) => line.slice(prefix.length))
    : lines.map((line) => prefix + line)

  const newBlock = newLines.join('\n')
  const newText = text.slice(0, lineStart) + newBlock + text.slice(lineEnd)

  if (lines.length === 1) {
    if (allHavePrefix) {
      return {
        text: newText,
        selectionStart: Math.max(lineStart, selectionStart - prefix.length),
        selectionEnd: Math.max(lineStart, selectionEnd - prefix.length),
      }
    }
    return {
      text: newText,
      selectionStart: selectionStart + prefix.length,
      selectionEnd: selectionEnd + prefix.length,
    }
  }

  return {
    text: newText,
    selectionStart: lineStart,
    selectionEnd: lineStart + newBlock.length,
  }
}

export function applyHeading(
  text: string,
  selectionStart: number,
  selectionEnd: number,
): FormatResult {
  const lineStart = text.lastIndexOf('\n', selectionStart - 1) + 1
  const lineEnd = text.indexOf('\n', selectionEnd)
  const end = lineEnd === -1 ? text.length : lineEnd
  const line = text.slice(lineStart, end)

  const headingMatch = /^(#{1,3})\s+/.exec(line)
  if (headingMatch) {
    const currentLevel = headingMatch[1]?.length ?? 0
    const nextLevel = currentLevel >= 3 ? 0 : currentLevel + 1
    const strippedLine = line.replace(/^#{1,3}\s+/, '')
    const newLine = nextLevel > 0 ? `${'#'.repeat(nextLevel)} ${strippedLine}` : strippedLine
    const newText = text.slice(0, lineStart) + newLine + text.slice(end)
    return {
      text: newText,
      selectionStart: lineStart,
      selectionEnd: lineStart + newLine.length,
    }
  }

  const newLine = `# ${line}`
  const newText = text.slice(0, lineStart) + newLine + text.slice(end)
  return {
    text: newText,
    selectionStart: lineStart,
    selectionEnd: lineStart + newLine.length,
  }
}

export function applyLinkFormat(
  text: string,
  selectionStart: number,
  selectionEnd: number,
): FormatResult {
  const hasSelection = selectionStart !== selectionEnd
  const urlPlaceholder = 'https://'
  const linkText = hasSelection ? text.slice(selectionStart, selectionEnd) : 'Link text'
  const insertion = `[${linkText}](${urlPlaceholder})`
  const newText = text.slice(0, selectionStart) + insertion + text.slice(selectionEnd)
  const urlStart = selectionStart + linkText.length + 3
  return {
    text: newText,
    selectionStart: urlStart,
    selectionEnd: urlStart + urlPlaceholder.length,
  }
}
