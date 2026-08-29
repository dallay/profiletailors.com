/// <reference types="vitest/config" />

import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwind from '@tailwindcss/vite'

export default defineConfig({
  envDir: '../../..',
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
  plugins: [vue(), tailwind()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@shared/assets': fileURLToPath(new URL('../../../shared/assets', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.ts', 'src/**/*.spec.ts'],
    setupFiles: ['./src/vitest-setup.ts'],
  },
})
