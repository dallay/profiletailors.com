import path from 'node:path'
import { defineCoverageReporterConfig } from '@bgotink/playwright-coverage'

/**
 * Factory for Playwright coverage reporter configuration.
 * Keeps coverage output directories distinct per suite to avoid collisions.
 */
export function createCoverageConfig(suiteName: string, __dirname: string) {
  return [
    '@bgotink/playwright-coverage',
    defineCoverageReporterConfig({
      sourceRoot: path.resolve(__dirname, '..'),
      resultDir: path.resolve(__dirname, `../coverage/${suiteName}-e2e`),
      reports: [
        ['html'],
        ['lcovonly', { file: 'coverage.lcov' }],
        ['text-summary', { file: null }],
      ],
    }),
  ] as const
}
