import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createCoverageConfig } from './coverage-config'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const appPort = Number(process.env.PLAYWRIGHT_PORT || '5173')

/**
 * Playwright config for real CAS media smoke tests.
 * Uses real backend/API traffic — NO HAR and NO /api/media/** interception.
 */
export default defineConfig({
  testDir: path.resolve(__dirname, 'specs'),
  testMatch: 'media-real*.spec.ts',

  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,

  timeout: 90_000,
  expect: { timeout: 20_000 },

  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-media-real-report', open: 'never' }],
    createCoverageConfig('media-real', __dirname),
  ],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || `http://localhost:${appPort}`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'en-US',
    timezoneId: 'Europe/Madrid',
  },

  webServer: {
    command: `PLAYWRIGHT=true VITE_API_BASE_URL="" PORT=${appPort} pnpm run dev:app`,
    port: appPort,
    reuseExistingServer: process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === 'true',
    cwd: path.resolve(__dirname, '..'),
    timeout: 30_000,
  },

  projects: [
    {
      name: 'media-real-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
