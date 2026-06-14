/**
 * Base test fixture that wraps Playwright's test with HAR-based API mocking.
 *
 * Every spec file in this project should import test and expect from
 * this file instead of @playwright/test directly.
 *
 * ## How it works
 *
 * - routeFromHAR intercepts all /api/ requests and serves responses
 *   from the recorded HAR file (hars/auth-flow.har).
 * - When UPDATE_HAR=true, it records real API responses into the HAR file
 *   instead of replaying. Requires the live SMP backend.
 * - Unmatched requests (non-API) fall through to the real network —
 *   static assets, fonts, etc. still come from the dev server.
 *
 * ## First-time setup
 *
 * 1. Start the SMP backend (e.g., ./gradlew :server:smp:bootRun)
 * 2. Run: UPDATE_HAR=true npx playwright test --grep @integration
 * 3. Commit the updated hars/auth-flow.har
 *
 * From then on, tests run against the HAR — no backend needed.
 *
 * ## Overriding per test
 *
 * Individual tests can still call page.route() for specific scenarios
 * (e.g., simulating a 401 on refresh). Those routes take priority over
 * the HAR because they are registered after the base fixture runs.
 *
 * @see https://playwright.dev/docs/mock#mock-network-requests
 */

import { test as base, expect } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const HAR_PATH = path.resolve(__dirname, '../hars/auth-flow.har')

export const test = base.extend({
  page: async ({ page, context }, use) => {
    await context.routeFromHAR(HAR_PATH, {
      url: '**/api/**',
      update: process.env.UPDATE_HAR === 'true',
    })
    await use(page)
  },
})

export { expect }
