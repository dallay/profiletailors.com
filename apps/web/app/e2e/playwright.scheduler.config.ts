import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

/**
 * Playwright config for scheduler E2E tests.
 *
 * These tests require the FULL stack running:
 *   - Vite dev server on port 5173
 *   - Spring Boot backend on port 8080
 *   - WireMock (LinkedIn mock) on port 8089
 *   - PostgreSQL on port 5432
 *
 * ## Running
 *
 * Full stack first:
 *   docker compose up -d linkedin-wiremock postgresql
 *   SMP_PUBLISHING_WORKER_ENABLED=true SMP_PUBLISHING_WORKER_POLL_INTERVAL=PT5S \
 *     SMP_LINKEDIN_PUBLISHING_API_BASE_URL=http://localhost:8089 \
 *     ./gradlew :server:smp:bootRun --args='--spring.profiles.active=dev'
 *   pnpm --filter app dev
 *
 * Then:
 *   npx playwright test -c e2e/playwright.scheduler.config.ts
 *
 * Or use the npm script:
 *   pnpm --filter app test:e2e:scheduler
 *
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
    ['html', { outputFolder: 'playwright-scheduler-report' }],
    ['list'],
  ],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: process.env.CI ? 'on' : 'off',
    locale: 'en-US',
    timezoneId: 'Europe/Madrid',
  },

  projects: [
    {
      name: 'scheduler-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
