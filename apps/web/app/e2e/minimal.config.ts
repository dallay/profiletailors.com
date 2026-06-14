import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './specs',
  use: { baseURL: 'http://localhost:5173' },
})
