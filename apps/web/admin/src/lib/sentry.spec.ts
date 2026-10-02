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
    expect(sentryMock.init).not.toHaveBeenCalled()

    initializeAdminSentry(createApp({}), createTestRouter(), {
      dsn: '',
      environment: 'production',
      production: true,
      release: 'admin@0.0.15+abcdef0',
    })

    expect(sentryMock.init).not.toHaveBeenCalled()
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
})
