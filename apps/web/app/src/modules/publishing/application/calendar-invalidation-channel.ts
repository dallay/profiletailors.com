export type CalendarInvalidationReason =
  | 'created'
  | 'updated'
  | 'deleted'
  | 'rescheduled'
  | 'cancelled'

export type CalendarInvalidation = {
  version: 1
  type: 'calendar-invalidated'
  workspaceId: string
  publicationId?: string
  reason: CalendarInvalidationReason
  occurredAt: string
}

type CalendarInvalidationInput = {
  workspaceId: string
  publicationId?: string
  reason: CalendarInvalidationReason
}

type CalendarInvalidationListener = (message: CalendarInvalidation) => void

type CalendarInvalidationChannel = {
  publish: (input: CalendarInvalidationInput) => void
  close: () => void
}

const CHANNEL_NAME = 'profiletailors-calendar-invalidation'
const REASONS = new Set<CalendarInvalidationReason>([
  'created',
  'updated',
  'deleted',
  'rescheduled',
  'cancelled',
])

function isCalendarInvalidation(
  value: unknown,
  workspaceId: string,
): value is CalendarInvalidation {
  if (!value || typeof value !== 'object') return false
  const message = value as Record<string, unknown>
  return (
    message.version === 1 &&
    message.type === 'calendar-invalidated' &&
    message.workspaceId === workspaceId &&
    typeof message.workspaceId === 'string' &&
    (!('publicationId' in message) || typeof message.publicationId === 'string') &&
    typeof message.occurredAt === 'string' &&
    !Number.isNaN(Date.parse(message.occurredAt)) &&
    typeof message.reason === 'string' &&
    REASONS.has(message.reason as CalendarInvalidationReason)
  )
}

export function createCalendarInvalidationChannel(
  workspaceId: string,
  onMessage?: CalendarInvalidationListener,
): CalendarInvalidationChannel {
  if (typeof BroadcastChannel === 'undefined') {
    return {
      publish: () => undefined,
      close: () => undefined,
    }
  }

  const channel = new BroadcastChannel(CHANNEL_NAME)
  const handleMessage = (event: MessageEvent<unknown>): void => {
    if (onMessage && isCalendarInvalidation(event.data, workspaceId)) onMessage(event.data)
  }
  channel.addEventListener('message', handleMessage)

  return {
    publish: (input) => {
      const message: CalendarInvalidation = {
        version: 1,
        type: 'calendar-invalidated',
        workspaceId: input.workspaceId,
        ...(input.publicationId ? { publicationId: input.publicationId } : {}),
        reason: input.reason,
        occurredAt: new Date().toISOString(),
      }
      if (message.workspaceId === workspaceId && REASONS.has(message.reason)) {
        channel.postMessage(message)
      }
    },
    close: () => {
      channel.removeEventListener('message', handleMessage)
      channel.close()
    },
  }
}
