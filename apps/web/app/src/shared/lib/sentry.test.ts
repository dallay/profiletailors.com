import { createApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { configureAppSentry, initializeAppSentry } from './sentry'

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

describe('initializeAppSentry', () => {
  beforeEach(() => vi.clearAllMocks())

  it('does not initialize when not running a production build', () => {
    const app = createApp({})
    const router = createTestRouter()

    const reportError = initializeAppSentry(app, router, {
      dsn: 'https://public@example.ingest.sentry.io/123',
      environment: 'development',
      production: false,
      release: 'app@0.3.16+abcdef0',
      replayOnError: true,
    })

    expect(typeof reportError).toBe('function')
    reportError(new Error('local startup failure'))
    expect(sentryMock.init).not.toHaveBeenCalled()
    expect(sentryMock.replayIntegration).not.toHaveBeenCalled()
    expect(sentryMock.captureException).not.toHaveBeenCalled()
  })

  it('keeps Sentry disabled in the test build by default', () => {
    const reportError = configureAppSentry(createApp({}), createTestRouter())

    expect(typeof reportError).toBe('function')
    expect(sentryMock.init).not.toHaveBeenCalled()
  })

  it('does not initialize without a production DSN', () => {
    const reportError = initializeAppSentry(createApp({}), createTestRouter(), {
      dsn: '',
      environment: 'production',
      production: true,
      release: 'app@0.3.16+abcdef0',
      replayOnError: false,
    })

    expect(typeof reportError).toBe('function')
    reportError(new Error('missing DSN'))
    expect(sentryMock.init).not.toHaveBeenCalled()
    expect(sentryMock.captureException).not.toHaveBeenCalled()
  })

  it('uses production error and navigation sampling without enabling Replay by default', () => {
    const app = createApp({})
    const router = createTestRouter()

    const reportError = initializeAppSentry(app, router, {
      dsn: 'https://public@example.ingest.sentry.io/123',
      environment: 'production',
      production: true,
      release: 'app@0.3.16+abcdef0',
      replayOnError: false,
    })

    expect(typeof reportError).toBe('function')
    expect(sentryMock.browserTracingIntegration).toHaveBeenCalledWith({ router })
    expect(sentryMock.replayIntegration).not.toHaveBeenCalled()
    reportError(new Error('bootstrap failure'))
    expect(sentryMock.captureException).toHaveBeenCalledWith(expect.any(Error))
    expect(sentryMock.init).toHaveBeenCalledWith(
      expect.objectContaining({
        app,
        dsn: 'https://public@example.ingest.sentry.io/123',
        environment: 'production',
        release: 'app@0.3.16+abcdef0',
        sampleRate: 1,
        tracesSampleRate: 0.1,
        replaysSessionSampleRate: 0,
        replaysOnErrorSampleRate: 0,
        beforeSend: expect.any(Function),
        beforeSendSpan: expect.any(Function),
        beforeBreadcrumb: expect.any(Function),
      }),
    )
  })

  it('enables only masked error-session Replay when explicitly allowed', () => {
    initializeAppSentry(createApp({}), createTestRouter(), {
      dsn: 'https://public@example.ingest.sentry.io/123',
      environment: 'production',
      production: true,
      release: 'app@0.3.16+abcdef0',
      replayOnError: true,
    })

    expect(sentryMock.replayIntegration).toHaveBeenCalledWith(
      expect.objectContaining({
        maskAllText: true,
        maskAllInputs: true,
        blockAllMedia: true,
        networkCaptureBodies: false,
        networkRequestHeaders: [],
        networkResponseHeaders: [],
      }),
    )
    expect(sentryMock.init).toHaveBeenCalledWith(
      expect.objectContaining({ replaysSessionSampleRate: 0, replaysOnErrorSampleRate: 1 }),
    )
  })
})
