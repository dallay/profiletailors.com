import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createCoverageConfig } from './coverage-config'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const appPort = Number(process.env.PLAYWRIGHT_PORT || '5173')

/**
 * Playwright config for Media Library mocked UI tests.
 *
 * Media mocked tests run without a backend. Auth is provided by the base-test
 * HAR layer and media endpoints are overridden by media-specific stateful mocks.
 * This config intentionally does not configure HAR itself; the fixture owns that
 * layer so scheduler and media mocks stay separate.
 */
export default defineConfig({
  testDir: path.resolve(__dirname, 'specs'),
  testMatch: [
    'media-mocked*.spec.ts',
    'media-composer.spec.ts',
    'composer-media-attachments-mocked.spec.ts',
  ],

  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,

  timeout: 60_000,
  expect: { timeout: 15_000 },

  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-media-mocked-report', open: 'never' }],
    createCoverageConfig('media-mocked', __dirname),
  ],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || `http://localhost:${appPort}`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: process.env.CI ? 'retain-on-failure' : 'off',
    locale: 'en-US',
    timezoneId: 'Europe/Madrid',
  },

  webServer: {
    command: `PLAYWRIGHT=true VITE_API_BASE_URL="" MEDIA_HAR=off PORT=${appPort} pnpm run dev:app`,
    port: appPort,
    reuseExistingServer: process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === 'true',
    cwd: path.resolve(__dirname, '..'),
    timeout: 30_000,
  },

  projects: [
    {
      name: 'media-mocked-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
