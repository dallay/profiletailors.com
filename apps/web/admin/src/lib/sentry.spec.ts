import { createApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { configureAdminSentry, initializeAdminSentry } from './sentry'

const sentryMock = vi.hoisted(() => ({
  init: vi.fn((_options: unknown) => undefined),
  browserTracingIntegration: vi.fn((_options: unknown) => ({ name: 'browser-tracing' })),
  replayIntegration: vi.fn((_options: unknown) => ({ name: 'replay' })),
  captureException: vi.fn((_error: unknown) => undefined),
}))

vi.mock('@sentry/vue', () => sentryMock)

function createTestRouter() {
  return createRouter({ history: createMemoryHistory(), routes: [] })
}

interface DeliveryCallbacks {
  beforeSend: (payload: Record<string, unknown>) => Record<string, unknown> | null
  beforeBreadcrumb: (payload: Record<string, unknown>) => Record<string, unknown> | null
  beforeSendSpan: (payload: Record<string, unknown>) => Record<string, unknown>
}

function isDeliveryCallbacks(value: unknown): value is DeliveryCallbacks {
  return (
    typeof value === 'object' &&
    value !== null &&
    'beforeSend' in value &&
    typeof value.beforeSend === 'function' &&
    'beforeBreadcrumb' in value &&
    typeof value.beforeBreadcrumb === 'function' &&
    'beforeSendSpan' in value &&
    typeof value.beforeSendSpan === 'function'
  )
}

function lastInitCallbacks(): DeliveryCallbacks {
  const options: unknown = sentryMock.init.mock.calls.at(-1)?.[0]
  if (!isDeliveryCallbacks(options)) throw new Error('Sentry init was not called')
  return options
}

describe('initializeAdminSentry', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps Sentry disabled in the test build by default', () => {
    const reportError = configureAdminSentry(createApp({}), createTestRouter())

    expect(typeof reportError).toBe('function')
    expect(sentryMock.init).not.toHaveBeenCalled()
  })

  it('does not initialize outside production or without a DSN', () => {
    const disabled = initializeAdminSentry(createApp({}), createTestRouter(), {
      dsn: 'https://public@example.ingest.sentry.io/123',
      environment: 'test',
      production: false,
      release: 'admin@0.0.15+abcdef0',
    })

    expect(typeof disabled).toBe('function')
    disabled(new Error('non-production bootstrap failure'))
    expect(sentryMock.init).not.toHaveBeenCalled()

    const withoutDsn = initializeAdminSentry(createApp({}), createTestRouter(), {
      dsn: '',
      environment: 'production',
      production: true,
      release: 'admin@0.0.15+abcdef0',
    })

    expect(typeof withoutDsn).toBe('function')
    withoutDsn(new Error('missing DSN'))
    expect(sentryMock.init).not.toHaveBeenCalled()
    expect(sentryMock.captureException).not.toHaveBeenCalled()
    expect(sentryMock.replayIntegration).not.toHaveBeenCalled()
  })

  it('captures production errors with bounded client tracing and no Replay', () => {
    const app = createApp({})
    const router = createTestRouter()
    const reportError = initializeAdminSentry(app, router, {
      dsn: 'https://public@example.ingest.sentry.io/123',
      environment: 'production',
      production: true,
      release: 'admin@0.0.15+abcdef0',
    })

    reportError(new Error('admin bootstrap failure'))

    expect(sentryMock.captureException).toHaveBeenCalledWith(expect.any(Error))
    expect(sentryMock.browserTracingIntegration).toHaveBeenCalledWith({ router })
    expect(sentryMock.replayIntegration).not.toHaveBeenCalled()
    expect(sentryMock.init).toHaveBeenCalledWith(
      expect.objectContaining({
        app,
        dsn: 'https://public@example.ingest.sentry.io/123',
        environment: 'production',
        release: 'admin@0.0.15+abcdef0',
        sampleRate: 1,
        tracesSampleRate: 0.1,
        beforeSend: expect.any(Function),
        beforeSendSpan: expect.any(Function),
        beforeBreadcrumb: expect.any(Function),
      }),
    )
  })

  it('sanitizes payloads through the registered delivery callbacks', () => {
    initializeAdminSentry(createApp({}), createTestRouter(), {
      dsn: 'https://public@example.ingest.sentry.io/123',
      environment: 'production',
      production: true,
      release: 'admin@0.0.15+abcdef0',
    })

    const callbacks = lastInitCallbacks()

    const event = {
      request: {
        url: 'https://admin.profiletailors.com/users?token=reset-secret',
        headers: { Authorization: 'Bearer access-secret', Accept: 'application/json' },
      },
      user: { id: 'operator-123', email: 'operator@example.com' },
    }
    expect(callbacks.beforeSend(event)).toBe(event)
    expect(event.request.url).toBe('https://admin.profiletailors.com/users')
    expect(event.user).toEqual({ id: 'operator-123' })
    expect(callbacks.beforeSend(Object.freeze({ note: 'hello' }))).toBeNull()

    const breadcrumb = { message: 'hello operator@example.com' }
    expect(callbacks.beforeBreadcrumb(breadcrumb)).toBe(breadcrumb)
    expect(breadcrumb.message).toBe('hello [redacted]')
    expect(callbacks.beforeBreadcrumb(Object.freeze({ note: 'hello' }))).toBeNull()

    const span = {
      trace_id: 'trace-123',
      span_id: 'span-789',
      name: 'ui.action',
      start_timestamp: 1720000000,
      status: 'ok',
      is_segment: true,
      attributes: { principalId: 'operator-123', password: 'plaintext-secret' },
    }
    expect(callbacks.beforeSendSpan(span)).toBe(span)
    expect(span.attributes).toEqual({ principalId: 'operator-123' })

    const redacted = callbacks.beforeSendSpan(
      Object.freeze({
        trace_id: 'trace-123',
        span_id: 'span-789',
        name: 'ui.action password=plaintext-secret',
        start_timestamp: 1720000000,
        status: 'ok',
        is_segment: true,
        attributes: {},
      }),
    )
    expect(redacted).toMatchObject({ trace_id: 'trace-123', name: 'redacted', attributes: {} })
  })
})
