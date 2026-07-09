/// <reference types="vitest/config" />

import { fileURLToPath, URL } from 'node:url'

import { defineConfig, type UserConfig } from 'vite'
import type { UserConfig as VitestUserConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwind from '@tailwindcss/vite'

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
  plugins: [vue(), vueDevTools(), tailwind()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: [
      'src/**/*.test.ts',
      'src/**/*.spec.ts',
      'e2e/fixtures/**/*.test.ts',
      'e2e/pages/**/*.test.ts',
    ],
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
