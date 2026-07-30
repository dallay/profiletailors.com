/// <reference types="vitest/config" />

import { fileURLToPath, URL } from 'node:url'

import { defineConfig, type UserConfig } from 'vite'
import type { UserConfig as VitestUserConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwind from '@tailwindcss/vite'

const isE2eOrCi = Boolean(
  process.env.PLAYWRIGHT_BASE_URL || process.env.CI || process.env.NODE_ENV === 'test',
)

// https://vite.dev/config/
const config = {
  envDir: '../../..',
  server: {
    port: parseInt(process.env.PORT || '5173', 10),
    host: true,
    allowedHosts: ['.localhost', 'pt-app.localhost'],
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.SMP_BACKEND_PORT || '7638'}`,
        changeOrigin: true,
      },
    },
  },
  plugins: [vue(), !isE2eOrCi && vueDevTools(), tailwind()].filter(Boolean),
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@modules': fileURLToPath(new URL('./src/modules', import.meta.url)),
      '@shared': fileURLToPath(new URL('./src/shared', import.meta.url)),
      '@layouts': fileURLToPath(new URL('./src/layouts', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.ts', 'src/**/*.spec.ts'],
    setupFiles: ['./src/vitest-setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.ts', 'src/**/*.vue'],
      exclude: ['src/**/*.test.ts', 'src/**/*.spec.ts', 'src/**/*.d.ts'],
    },
  },
} satisfies UserConfig & Pick<VitestUserConfig, 'test'>

export default defineConfig(config)
