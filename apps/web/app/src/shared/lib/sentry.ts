import * as Sentry from '@sentry/vue'
import type { App } from 'vue'
import type { Router } from 'vue-router'
import { buildRedactedSpan, sanitizeSentryPayload } from '@profiletailors/shared-web'

interface AppSentryConfiguration {
  dsn: string
  environment: string
  production: boolean
  release: string
  replayOnError: boolean
}

type ErrorReporter = (error: unknown) => void

export function configureAppSentry(app: App, router: Router): ErrorReporter {
  return initializeAppSentry(app, router, {
    dsn: import.meta.env.VITE_SENTRY_DSN ?? '',
    environment: import.meta.env.VITE_SENTRY_ENVIRONMENT ?? import.meta.env.MODE,
    production: import.meta.env.PROD,
    release: __SENTRY_RELEASE__,
    replayOnError: import.meta.env.VITE_SENTRY_REPLAY_ON_ERROR === 'true',
  })
}

export function initializeAppSentry(
  app: App,
  router: Router,
  configuration: AppSentryConfiguration,
): ErrorReporter {
  if (!configuration.production || !configuration.dsn.trim()) return () => undefined

  const replayIntegrations = configuration.replayOnError
    ? [
        Sentry.replayIntegration({
          maskAllText: true,
          maskAllInputs: true,
          blockAllMedia: true,
          networkCaptureBodies: false,
          networkRequestHeaders: [],
          networkResponseHeaders: [],
          beforeAddRecordingEvent: (event) => (sanitizeSentryPayload(event) ? event : null),
        }),
      ]
    : []

  Sentry.init({
    app,
    dsn: configuration.dsn,
    environment: configuration.environment,
    release: configuration.release,
    sampleRate: 1,
    tracesSampleRate: 0.1,
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: configuration.replayOnError ? 1 : 0,
    integrations: [Sentry.browserTracingIntegration({ router }), ...replayIntegrations],
    beforeSend: (event) => (sanitizeSentryPayload(event) ? event : null),
    beforeBreadcrumb: (breadcrumb) => (sanitizeSentryPayload(breadcrumb) ? breadcrumb : null),
    beforeSendSpan: (span) => (sanitizeSentryPayload(span) ? span : buildRedactedSpan(span)),
  })

  return (error) => Sentry.captureException(error)
}
