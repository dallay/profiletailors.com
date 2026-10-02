/// <reference types="vitest/config" />

import { defineConfig, loadEnv, type PluginOption } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwind from '@tailwindcss/vite'
import { sentryVitePlugin } from '@sentry/vite-plugin'
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

const environmentRoot = fileURLToPath(new URL('../../../', import.meta.url))

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, environmentRoot, 'VITE_')
  const sentryGitSha = process.env.GIT_SHA?.trim() || buildInfo.gitSha
  const sentryRelease = `admin@${buildInfo.version}+${sentryGitSha}`
  const sentryEnabled = mode === 'production' && Boolean(environment.VITE_SENTRY_DSN?.trim())

  if (mode === 'production' && !sentryEnabled) {
    console.warn(
      'Sentry is disabled because VITE_SENTRY_DSN is not configured; source maps are off.',
    )
  }

  const sentryAuthToken = process.env.SENTRY_AUTH_TOKEN
  const sentryOrg = process.env.SENTRY_ORG
  const sentryProject = process.env.SENTRY_PROJECT
  const missingSentrySettings = [
    ...(!sentryAuthToken ? ['SENTRY_AUTH_TOKEN'] : []),
    ...(!sentryOrg ? ['SENTRY_ORG'] : []),
    ...(!sentryProject ? ['SENTRY_PROJECT'] : []),
  ]

  if (sentryEnabled && missingSentrySettings.length > 0) {
    throw new Error(`Sentry source-map upload requires ${missingSentrySettings.join(', ')}.`)
  }

  if (sentryEnabled && sentryProject !== 'profiletailors-admin') {
    throw new Error('SENTRY_PROJECT must be profiletailors-admin for this application.')
  }

  const pluginOptions = [
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
    sentryEnabled
      ? sentryVitePlugin({
          authToken: sentryAuthToken,
          org: sentryOrg,
          project: sentryProject,
          release: { name: sentryRelease },
          sourcemaps: { filesToDeleteAfterUpload: ['./dist/**/*.map'] },
          telemetry: false,
        })
      : false,
  ]
  const plugins = pluginOptions.flatMap((plugin): PluginOption[] => {
    if (typeof plugin === 'boolean' || plugin === null || plugin === undefined) return []
    return [plugin]
  })

  return {
    envDir: '../../..',
    define: {
      __APP_VERSION__: JSON.stringify(buildInfo.version),
      __GIT_SHA__: JSON.stringify(buildInfo.gitSha),
      __BUILD_TIME__: JSON.stringify(buildInfo.buildTime),
      __SENTRY_RELEASE__: JSON.stringify(sentryRelease),
    },
    build: { sourcemap: sentryEnabled ? 'hidden' : false },
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
    plugins,
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
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        reportsDirectory: './coverage',
        include: ['src/**/*.ts', 'src/**/*.vue'],
        exclude: ['src/**/*.test.ts', 'src/**/*.spec.ts', 'src/**/*.d.ts'],
      },
    },
  }
})
