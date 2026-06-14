import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

/**
 * Playwright E2E configuration for the Profile Tailors SPA (apps/web/app).
 *
 * ## HAR-based API mocking
 *
 * All /api/* requests are served from hars/auth-flow.har via
 * routeFromHAR (configured in fixtures/base-test.ts).
 *
 * - **Replay mode** (default): API responses come from the HAR file.
 *   No backend required.
 * - **Record mode** (UPDATE_HAR=true): API calls hit the real SMP
 *   backend and responses are captured into the HAR file.
 *
 * ## Recording the HAR for the first time
 *
 *   UPDATE_HAR=true npx playwright test --grep @integration
 *
 * Or use the helper script:
 *
 *   ./scripts/record-har.sh
 *
 * ## Tag convention
 *
 * - @frontend     — Tests that only need the Vite dev server (rendering,
 *                    validation, responsive, i18n text)
 * - @integration  — Tests that make API calls (login, registration,
 *                    session, logout, security)
 *
 * Run only frontend tests (no API needed):
 *   npx playwright test --grep @frontend
 *
 * Run only integration tests:
 *   npx playwright test --grep @integration
 *
 * @see https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,

  timeout: 30_000,
  expect: { timeout: 10_000 },

  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
  ],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  /* ── Frontend dev server only (no SMP backend) ────────────── */
  webServer: {
    command: 'pnpm run dev:app',
    port: 5173,
    reuseExistingServer: !process.env.CI,
    cwd: path.resolve(__dirname, '..'),
    timeout: 30_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 12'] },
    },
  ],
})
