export type FormatResult = {
  text: string
  selectionStart: number
  selectionEnd: number
}

const SAFE_URL = /^(?:https?:\/\/|mailto:|www\.)/i

function stripInlineMarkdown(text: string): string {
  return text
    .replace(/!\[([^\]]*)\]\(([^()]*(?:\([^()]*\)[^()]*)*)\)/g, '$1')
    .replace(
      /\[([^\]]*)\]\(([^()]*(?:\([^()]*\)[^()]*)*)\)/g,
      (_match, label: string, url: string) => {
        const trimmedUrl = url.trim()
        if (!SAFE_URL.test(trimmedUrl)) {
          return label
        }
        if (label.trim() === trimmedUrl) {
          return trimmedUrl
        }
        return label.trim() ? `${label} (${trimmedUrl})` : trimmedUrl
      },
    )
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/\*(.+?)\*/g, '$1')
    .replace(/~~(.+?)~~/g, '$1')
    .replace(/`(.+?)`/g, '$1')
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
    let processed = line
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

  const headingMatch = line.match(/^(#{1,3})\s+/)
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
