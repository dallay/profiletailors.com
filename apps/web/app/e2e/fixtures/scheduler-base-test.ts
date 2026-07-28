/**
 * Scheduler-specific base test fixture.
 *
 * Extends the main base-test (which provides HAR auth replay) with
 * scheduler-specific API mocks (publications, channels, workspaces).
 *
 * Every scheduler spec file should import test and expect from
 * this file instead of @playwright/test or ../fixtures/base-test.
 *
 * @see fixtures/base-test.ts for the HAR auth replay layer
 * @see fixtures/scheduler-mocks.ts for the scheduler mock definitions
 *
 * ═══════════════════════════════════════════════════════════════════════
 * Consent seeding
 * ═══════════════════════════════════════════════════════════════════════
 *
 * The ConsentBanner (a shadcn-vue Dialog) mounts inside AppShell and
 * shows as a modal overlay when no valid consent is found in localStorage.
 * This blocks the heading locators that all scheduler tests wait for.
 *
 * We seed a valid consent receipt via addInitScript so the banner never
 * appears during scheduler tests. The script runs before any app JS on
 * every navigation, so the Pinia store finds valid consent immediately.
 *
 * The separate consent.spec.ts test imports base-test.ts directly and
 * manages consent state on its own, so seeding here does NOT affect it.
 *
 * See also: clearConsent / setConsentReceipt in consent-helpers.ts
 *           (not used here — we want consent pre-seeded, not cleared).
 */

import { test as base, expect } from './base-test'
import { registerSchedulerMocks, resetSchedulerMocks } from './scheduler-mocks'
import {
  CURRENT_CONSENT_VERSION,
  CURRENT_POLICY_VERSION,
  CONSENT_STORAGE_KEY,
} from '../../../../../shared/web/types/consent'

/**
 * Valid ConsentReceipt that passes the Zod schema validation.
 * @see shared/web/validation/consent.ts — consentReceiptSchema
 */
const VALID_CONSENT = {
  consentVersion: CURRENT_CONSENT_VERSION,
  policyVersion: CURRENT_POLICY_VERSION,
  timestamp: new Date().toISOString(),
  region: 'EU',
  categories: { necessary: true, analytics: true },
  dnt: false,
  source: 'banner',
} as const

export const test = base.extend<{ resetSession: () => Promise<void> }>({
  page: async ({ page, context }, use) => {
    // base-test already registered routeFromHAR for auth.
    // Layer scheduler mocks on top — these take priority because
    // they are registered after routeFromHAR.
    await registerSchedulerMocks(context)
    resetSchedulerMocks()

    // Seed consent in localStorage before every navigation so the
    // ConsentBanner never blocks the dashboard UI in scheduler tests.
    // Wraps in try-catch for about:blank where localStorage may throw.
    const receipt = JSON.stringify(VALID_CONSENT)
    await context.addInitScript(
      ({ key, value }: { key: string; value: string }): void => {
        try {
          localStorage.setItem(key, value)
        } catch {}
      },
      { key: CONSENT_STORAGE_KEY, value: receipt },
    )

    await use(page)
  },
})

export { expect }
