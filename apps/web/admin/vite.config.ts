/// <reference types="vitest/config" />

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwind from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { computeBuildInfo } from '../../../scripts/compute-build-info.mjs'

const buildInfo = process.env.VITEST
  ? {
      version: '0.0.8',
      gitSha: 'b2c3d4e',
      buildTime: '2026-09-18T16:00:00.000Z',
    }
  : computeBuildInfo(fileURLToPath(new URL('./package.json', import.meta.url)))

export default defineConfig({
  envDir: '../../..',
  define: {
    __APP_VERSION__: JSON.stringify(buildInfo.version),
    __GIT_SHA__: JSON.stringify(buildInfo.gitSha),
    __BUILD_TIME__: JSON.stringify(buildInfo.buildTime),
  },
  server: {
    port: Number.parseInt(process.env.PORT || '5174', 10),
    strictPort: Boolean(process.env.WORKTREE_ID),
    host: true,
    allowedHosts: ['.localhost', 'pt-admin.localhost'],
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.SMP_BACKEND_PORT || '7638'}`,
        changeOrigin: true,
      },
    },
  },
  plugins: [
    vue(),
    tailwind(),
    {
      name: 'version-json',
      closeBundle() {
        const outDir = fileURLToPath(new URL('./dist', import.meta.url))
        writeFileSync(
          resolve(outDir, 'version.json'),
          `${JSON.stringify(buildInfo, null, 2)}\n`,
          'utf8',
        )
      },
    },
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@shared/assets': fileURLToPath(new URL('../../../shared/assets', import.meta.url)),
      '@profiletailors/vue-ui': fileURLToPath(
        new URL('../../../shared/vue-ui/src', import.meta.url),
      ),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.ts', 'src/**/*.spec.ts'],
    setupFiles: ['./src/vitest-setup.ts'],
  },
})

// Fix: deploy static version.json for dynamic deployed version badge
