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
 */

import { test as base, expect } from './base-test'
import { registerSchedulerMocks, resetSchedulerMocks } from './scheduler-mocks'

export const test = base.extend<{ resetSession: () => Promise<void> }>({
  page: async ({ page, context }, use) => {
    // base-test already registered routeFromHAR for auth.
    // Layer scheduler mocks on top — these take priority because
    // they are registered after routeFromHAR.
    await registerSchedulerMocks(context)
    resetSchedulerMocks()
    await use(page)
  },
})

export { expect }
