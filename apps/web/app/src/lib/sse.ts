export interface SseMessage {
  event: string
  data: string
}

export interface ParsedSseEvent<T = unknown> {
  event: string
  data: T | null
}

export function parseSseFrames(input: string): SseMessage[] {
  return input
    .replace(/\r\n/g, '\n')
    .split(/\n\n+/)
    .map((frame) => frame.trimEnd())
    .filter(Boolean)
    .map(parseSseFrame)
    .filter((message): message is SseMessage => message !== null)
}

export function parseSseFrame(frame: string): SseMessage | null {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of frame.replace(/\r\n/g, '\n').split('\n')) {
    if (!line || line.startsWith(':')) continue
    const separatorIndex = line.indexOf(':')
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex)
    const rawValue = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1)
    const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue

    if (field === 'event') event = value
    if (field === 'data') dataLines.push(value)
  }

  if (dataLines.length === 0) return null
  return { event, data: dataLines.join('\n') }
}

export async function consumeSseStream<T = unknown>(
  response: Response,
  onEvent: (event: ParsedSseEvent<T>) => void | Promise<void>,
): Promise<void> {
  if (!response.body) return

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() ?? ''

      for (const frame of frames) {
        await emitParsedFrame(frame, onEvent)
      }
    }

    buffer += decoder.decode()
    if (buffer.trim()) {
      await emitParsedFrame(buffer, onEvent)
    }
  } finally {
    reader.releaseLock()
  }
}

async function emitParsedFrame<T>(
  frame: string,
  onEvent: (event: ParsedSseEvent<T>) => void | Promise<void>,
): Promise<void> {
  const message = parseSseFrame(frame)
  if (!message) return

  try {
    await onEvent({
      event: message.event,
      data: JSON.parse(message.data) as T,
    })
  } catch {
    // Ignore malformed payloads; REST remains the canonical source of truth.
  }
}
