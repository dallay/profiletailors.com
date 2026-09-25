import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const appDir = fileURLToPath(new URL('..', import.meta.url))

export default defineConfig({
  testDir: './specs',
  testMatch: 'pwa-offline.spec.ts',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: { baseURL: 'http://localhost:4173' },
  projects: [{ name: 'pwa-preview', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'pnpm run preview -- --host localhost --port 4173 --strictPort',
    url: 'http://localhost:4173/offline',
    reuseExistingServer: false,
    cwd: path.resolve(appDir),
    timeout: 30_000,
  },
})
