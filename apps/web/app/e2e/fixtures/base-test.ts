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
 *   instead of replaying. Requires the live API/backend.
 * - Unmatched requests (non-API) fall through to the real network —
 *   static assets, fonts, etc. still come from the dev server.
 *
 * ## Refreshing the HAR
 *
 * 1. Start the API/backend you want to capture.
 * 2. Run: UPDATE_HAR=true npx playwright test --grep @integration
 * 3. Commit the updated hars/auth-flow.har
 *
 * From then on, tests replay from the HAR — no backend needed.
 *
 * ## Overriding per test
 *
 * Individual tests can still call page.route() for specific scenarios
 * (e.g., simulating a 401 on refresh). Those routes take priority over
 * the HAR because they are registered after the base fixture runs.
 * If the request should still be resolved by HAR replay, use route.fallback()
 * rather than route.continue().
 *
 * @see https://playwright.dev/docs/mock#mock-network-requests
 */

import { test as base, expect } from '@bgotink/playwright-coverage'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { resetSession } from './auth-helpers'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const HAR_PATH = path.resolve(__dirname, '../hars/auth-flow.har')
const UPDATE_HAR = process.env.UPDATE_HAR === 'true'

export const test = base.extend<{
  resetSession: () => Promise<void>
}>({
  page: async ({ page, context }, use) => {
    await context.routeFromHAR(HAR_PATH, {
      url: '**/api/**',
      update: UPDATE_HAR,
    })
    await use(page)
  },

  resetSession: async ({ page }, use) => {
    await use(async () => {
      await resetSession(page)
    })
  },
})

export { expect }
