import { defineConfig, type UserConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwind from '@tailwindcss/vite'
import type { InlineConfig as VitestInlineConfig } from 'vitest/node'
import { fileURLToPath, URL } from 'node:url'
import { computeBuildInfo } from '../../../scripts/compute-build-info.mjs'

const isE2eOrCi = Boolean(
  process.env.PLAYWRIGHT ||
    process.env.PLAYWRIGHT_BASE_URL ||
    process.env.CI ||
    process.env.NODE_ENV === 'test',
)

const buildInfo = process.env.VITEST
  ? {
      version: '0.3.9',
      gitSha: 'a1b2c3d',
      buildTime: '2026-09-18T16:00:00.000Z',
    }
  : computeBuildInfo(fileURLToPath(new URL('./package.json', import.meta.url)))

const config = {
  envDir: '../../..',
  define: {
    __APP_VERSION__: JSON.stringify(buildInfo.version),
    __GIT_SHA__: JSON.stringify(buildInfo.gitSha),
    __BUILD_TIME__: JSON.stringify(buildInfo.buildTime),
  },
  server: {
    port: parseInt(process.env.PORT || '5173', 10),
    strictPort: Boolean(process.env.WORKTREE_ID || process.env.PLAYWRIGHT),
    hmr: !isE2eOrCi,
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
      '@shared/assets': fileURLToPath(new URL('../../../shared/assets', import.meta.url)),
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
} satisfies UserConfig & { test?: VitestInlineConfig }

export default defineConfig(config)
