import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['types/**/*.ts', 'utils/**/*.ts', 'validation/**/*.ts'],
      exclude: ['**/*.test.ts', '**/*.spec.ts', '**/*.d.ts'],
    },
  },
})
