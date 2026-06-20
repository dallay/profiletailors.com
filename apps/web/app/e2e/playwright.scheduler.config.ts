import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {defineCoverageReporterConfig} from '@bgotink/playwright-coverage'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

/**
 * Playwright config for scheduler E2E tests.
 *
 * ## Mock strategy
 *
 * Scheduler tests use a two-layer mock approach:
 *
 * 1. **Auth replay** (auth-flow.har via routeFromHAR): Login, refresh,
 *    logout, and profile endpoints are served from the recorded HAR file.
 * 2. **Scheduler mocks** (scheduler-mocks.ts via context.route):
 *    Publications, channels, workspaces, and LinkedIn connection endpoints
 *    are mocked programmatically with realistic response shapes.
 *
 * Together, these layers make ALL scheduler tests backend-free.
 *
 * ## Recording a real HAR (optional)
 *
 * If you want to capture real backend responses instead of mocks:
 *
 *   UPDATE_HAR=true npx playwright test -c e2e/playwright.scheduler.config.ts
 *
 * This records actual API responses into the HAR file. The scheduler
 * mocks will still be active but unmatched requests fall through to HAR.
 *
 * ## Running
 *
 *   pnpm --filter app test:e2e:scheduler
 *
 * Or directly:
 *   npx playwright test -c e2e/playwright.scheduler.config.ts
 *
 * @see fixtures/scheduler-mocks.ts for mock definitions
 * @see specs/e2e/scheduler-posts-test-plan.md
 */
export default defineConfig({
  testDir: path.resolve(__dirname, 'specs'),
  testMatch: 'scheduler*.spec.ts',

  fullyParallel: false, // Scheduler tests are order-dependent
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1, // Serial execution for shared state

  timeout: 60_000, // Longer timeout for worker polling
  expect: { timeout: 15_000 },

  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-scheduler-report' }],
    [
      '@bgotink/playwright-coverage',
      defineCoverageReporterConfig({
        sourceRoot: path.resolve(__dirname, '..'),
        resultDir: path.resolve(__dirname, '../coverage/e2e'),
        reports: [
          ['html'],
          ['lcovonly', { file: 'coverage.lcov' }],
          ['text-summary', { file: null }],
        ],
      }),
    ],
  ],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: process.env.CI ? 'on' : 'off',
    locale: 'en-US',
    timezoneId: 'Europe/Madrid',
  },

  /* ── Frontend dev server (no backend required) ─────────── */
  webServer: {
    command: 'VITE_API_BASE_URL="" pnpm run dev:app',
    port: 5173,
    reuseExistingServer: true,
    cwd: path.resolve(__dirname, '..'),
    timeout: 30_000,
  },

  projects: [
    {
      name: 'scheduler-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
