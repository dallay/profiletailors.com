import { describe, expect, it } from 'vitest'
import { consumeSseStream, parseSseFrame, parseSseFrames } from './sse'

describe('sse parser', () => {
  it('parses named event frames', () => {
    expect(
      parseSseFrame(
        'event: connected-channel.updated\ndata: {"type":"connected-channel.updated","workspaceId":"a219727d-019f-544c-95f9-ac30f00cc8b2"}',
      ),
    ).toEqual({
      event: 'connected-channel.updated',
      data: '{"type":"connected-channel.updated","workspaceId":"a219727d-019f-544c-95f9-ac30f00cc8b2"}',
    })
  })

  it('parses multiple frames and ignores comments without data', () => {
    expect(
      parseSseFrames(
        ': keepalive\n\nevent: heartbeat\n\nevent: connected-channel.removed\ndata: {"id":1}\n\n',
      ),
    ).toEqual([{ event: 'connected-channel.removed', data: '{"id":1}' }])
  })

  it('consumes streaming chunks and parses JSON data', async () => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        const encoder = new TextEncoder()
        controller.enqueue(encoder.encode('event: connected-channel.updated\n'))
        controller.enqueue(
          encoder.encode('data: {"socialAccountId":"cbbf8a05-cc34-5486-a144-e0fa1eb65d49"}\n\n'),
        )
        controller.close()
      },
    })
    const events: unknown[] = []

    await consumeSseStream(new Response(stream), (event) => {
      events.push(event)
    })

    expect(events).toEqual([
      {
        event: 'connected-channel.updated',
        data: { socialAccountId: 'cbbf8a05-cc34-5486-a144-e0fa1eb65d49' },
      },
    ])
  })

  it('ignores malformed JSON payloads', async () => {
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          new TextEncoder().encode('event: connected-channel.updated\ndata: {bad}\n\n'),
        )
        controller.close()
      },
    })
    const events: unknown[] = []

    await consumeSseStream(new Response(stream), (event) => {
      events.push(event)
    })

    expect(events).toEqual([])
  })
})
