import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createCalendarInvalidationChannel,
  type CalendarInvalidationReason,
} from './calendar-invalidation-channel'

describe('calendar invalidation channel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('publishes only the minimal validated invalidation', () => {
    const postMessage = vi.fn()
    const close = vi.fn()
    class BroadcastChannelStub {
      postMessage = postMessage
      close = close
      addEventListener = vi.fn()
      removeEventListener = vi.fn()
    }
    vi.stubGlobal('BroadcastChannel', BroadcastChannelStub)
    const channel = createCalendarInvalidationChannel('workspace-a')

    channel.publish({ workspaceId: 'workspace-a', publicationId: 'pub-1', reason: 'updated' })

    expect(postMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        version: 1,
        type: 'calendar-invalidated',
        workspaceId: 'workspace-a',
        publicationId: 'pub-1',
        reason: 'updated',
        occurredAt: expect.any(String),
      }),
    )
    expect(postMessage.mock.calls[0]?.[0]).not.toHaveProperty('content')
  })

  it('skips publishing if workspaceId does not match or reason is invalid', () => {
    const postMessage = vi.fn()
    class BroadcastChannelStub {
      postMessage = postMessage
      close = vi.fn()
      addEventListener = vi.fn()
      removeEventListener = vi.fn()
    }
    vi.stubGlobal('BroadcastChannel', BroadcastChannelStub)
    const channel = createCalendarInvalidationChannel('workspace-a')

    channel.publish({ workspaceId: 'workspace-b', reason: 'updated' })
    expect(postMessage).not.toHaveBeenCalled()

    channel.publish({ workspaceId: 'workspace-a', reason: 'unknown' as CalendarInvalidationReason })
    expect(postMessage).not.toHaveBeenCalled()
  })

  it('receives valid messages and invokes onMessage callback', () => {
    let listener: ((event: MessageEvent<unknown>) => void) | undefined
    const addEventListener = vi.fn(
      (_type: string, callback: (event: MessageEvent<unknown>) => void) => {
        listener = callback
      },
    )
    class BroadcastChannelStub {
      addEventListener = addEventListener
      postMessage = vi.fn()
      close = vi.fn()
      removeEventListener = vi.fn()
    }
    vi.stubGlobal('BroadcastChannel', BroadcastChannelStub)
    const received = vi.fn()
    const subscription = createCalendarInvalidationChannel('workspace-a', received)

    const validMessage = {
      version: 1,
      type: 'calendar-invalidated',
      workspaceId: 'workspace-a',
      publicationId: 'pub-123',
      reason: 'created',
      occurredAt: new Date().toISOString(),
    }

    listener?.({ data: validMessage } as MessageEvent)

    expect(received).toHaveBeenCalledOnce()
    expect(received).toHaveBeenCalledWith(validMessage)
    subscription.close()
  })

  it('ignores malformed and foreign messages', () => {
    let listener: ((event: MessageEvent<unknown>) => void) | undefined
    const addEventListener = vi.fn(
      (_type: string, callback: (event: MessageEvent<unknown>) => void) => {
        listener = callback
      },
    )
    const channelInstance = {
      postMessage: vi.fn(),
      close: vi.fn(),
      addEventListener,
      removeEventListener: vi.fn(),
    }
    class BroadcastChannelStub {
      addEventListener = addEventListener
      postMessage = channelInstance.postMessage
      close = channelInstance.close
      removeEventListener = channelInstance.removeEventListener
    }
    vi.stubGlobal('BroadcastChannel', BroadcastChannelStub)
    const received = vi.fn()
    const subscription = createCalendarInvalidationChannel('workspace-a', received)

    listener?.({ data: null } as MessageEvent)
    listener?.({
      data: {
        version: 1,
        type: 'calendar-invalidated',
        workspaceId: 'workspace-b',
        reason: 'updated',
        occurredAt: new Date().toISOString(),
      },
    } as MessageEvent)
    listener?.({
      data: {
        version: 1,
        type: 'other',
        workspaceId: 'workspace-a',
        reason: 'updated',
        occurredAt: new Date().toISOString(),
      },
    } as MessageEvent)
    listener?.({
      data: {
        version: 1,
        type: 'calendar-invalidated',
        workspaceId: 'workspace-a',
        reason: 'updated',
        occurredAt: 'invalid',
      },
    } as MessageEvent)

    expect(received).not.toHaveBeenCalled()
    subscription.close()
    expect(channelInstance.close).toHaveBeenCalledOnce()
  })

  it('supports environments without BroadcastChannel', () => {
    const channel = createCalendarInvalidationChannel('workspace-a')
    expect(() => channel.publish({ workspaceId: 'workspace-a', reason: 'created' })).not.toThrow()
    expect(() => channel.close()).not.toThrow()
  })
})
