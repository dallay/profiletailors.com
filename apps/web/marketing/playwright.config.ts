import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {defineCoverageReporterConfig} from '@bgotink/playwright-coverage';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const previewPort = Number(process.env.PLAYWRIGHT_PORT || '4321');
const backendPort = process.env.SMP_BACKEND_PORT || '7638';

/**
 * Playwright configuration for Profile Tailors marketing site
 * @see https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report' }],
    [
      '@bgotink/playwright-coverage',
      defineCoverageReporterConfig({
        sourceRoot: path.join(__dirname, 'src'),
        resultDir: path.join(__dirname, 'coverage/e2e'),
        reports: [
          ['html'],
          ['lcovonly', { file: 'coverage.lcov' }],
          ['text-summary', { file: null }],
        ],
      }),
    ],
  ],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || `http://localhost:${previewPort}`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
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

  webServer: {
    command: `WAITLIST_ENABLED=true WAITLIST_API_BASE=http://localhost:${backendPort} pnpm build && ASTRO_PREVIEW_BACKGROUND=1 PORT=${previewPort} pnpm preview --port ${previewPort}`,
    url: `http://localhost:${previewPort}`,
    reuseExistingServer: process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === 'true',
    timeout: 120 * 1000,
  },
});
