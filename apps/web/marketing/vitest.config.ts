/// <reference types="vitest/config" />

import { getViteConfig } from 'astro/config'
import type { UserConfig } from 'vite'
import type { InlineConfig as VitestInlineConfig } from 'vitest/node'

const config = {
  define: {
    __APP_VERSION__: JSON.stringify('0.2.13'),
    __GIT_SHA__: JSON.stringify('c3d4e5f'),
    __BUILD_TIME__: JSON.stringify('2026-09-18T16:00:00.000Z'),
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.ts'],
      exclude: ['src/**/*.test.ts', 'src/**/*.d.ts'],
    },
  },
} satisfies UserConfig & { test: VitestInlineConfig }

export default getViteConfig(config)
