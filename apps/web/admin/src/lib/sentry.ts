import * as Sentry from '@sentry/vue'
import type { App } from 'vue'
import type { Router } from 'vue-router'
import { buildRedactedSpan, sanitizeSentryPayload } from '@profiletailors/shared-web'

interface AdminSentryConfiguration {
  dsn: string
  environment: string
  production: boolean
  release: string
}

type ErrorReporter = (error: unknown) => void

export function configureAdminSentry(app: App, router: Router): ErrorReporter {
  return initializeAdminSentry(app, router, {
    dsn: import.meta.env.VITE_SENTRY_DSN ?? '',
    environment: import.meta.env.VITE_SENTRY_ENVIRONMENT ?? import.meta.env.MODE,
    production: import.meta.env.PROD,
    release: __SENTRY_RELEASE__,
  })
}

export function initializeAdminSentry(
  app: App,
  router: Router,
  configuration: AdminSentryConfiguration,
): ErrorReporter {
  if (!configuration.production || !configuration.dsn.trim()) return () => undefined

  Sentry.init({
    app,
    dsn: configuration.dsn,
    environment: configuration.environment,
    release: configuration.release,
    sampleRate: 1,
    tracesSampleRate: 0.1,
    integrations: [Sentry.browserTracingIntegration({ router })],
    beforeSend: (event) => (sanitizeSentryPayload(event) ? event : null),
    beforeBreadcrumb: (breadcrumb) => (sanitizeSentryPayload(breadcrumb) ? breadcrumb : null),
    beforeSendSpan: (span) => (sanitizeSentryPayload(span) ? span : buildRedactedSpan(span)),
  })

  return (error) => Sentry.captureException(error)
}
