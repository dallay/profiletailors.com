import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify('0.0.0'),
    __GIT_SHA__: JSON.stringify('test-sha'),
    __BUILD_TIME__: JSON.stringify('2026-01-01T00:00:00.000Z'),
  },
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
