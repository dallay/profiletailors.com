import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const adminPort = Number(process.env.PLAYWRIGHT_PORT || '5174')

/**
 * Playwright config for the admin Back Office mocked lane.
 *
 * Tests run without a backend: auth, session, waitlist list/summary, and the
 * bulk invitation endpoint are intercepted in fixtures/admin-mocks.ts with a
 * stateful fake that mirrors the SMP contract (per-entry outcomes, summary
 * counts, validation errors). See openspec/specs/e2e/bulk-waitlist-invitation-test-plan.md.
 */
export default defineConfig({
  testDir: path.resolve(__dirname, 'specs'),
  testMatch: ['*.spec.ts'],

  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,

  timeout: 60_000,
  expect: { timeout: 15_000 },

  reporter: [['list'], ['html', { outputFolder: 'playwright-mocked-report', open: 'never' }]],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || `http://localhost:${adminPort}`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: process.env.CI ? 'retain-on-failure' : 'off',
    locale: 'en-US',
    timezoneId: 'Europe/Madrid',
  },

  webServer: {
    command: `PLAYWRIGHT=true PORT=${adminPort} pnpm run dev:app`,
    port: adminPort,
    reuseExistingServer: process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === 'true',
    cwd: path.resolve(__dirname, '..'),
    timeout: 30_000,
  },

  projects: [
    {
      name: 'admin-mocked-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
