// @ts-check
import { defineConfig, envField } from 'astro/config'
import vue from '@astrojs/vue'
import tailwindcss from '@tailwindcss/vite'
import icon from '@dallay/astro-icon'
import { codecovVitePlugin } from '@codecov/vite-plugin'
import { resolve, join, extname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { cpSync, createReadStream, existsSync, statSync, writeFileSync } from 'node:fs'
import { computeBuildInfo } from '../../../scripts/compute-build-info.mjs'

const SHARED_ASSETS = resolve('../../../shared/assets')
const SHARED_WEB_ASSETS = resolve('../../../shared/assets/web')

const buildInfo = computeBuildInfo(
  fileURLToPath(new URL('./package.json', import.meta.url))
)

const MIME_TYPES = /** @type {Record<string, string>} */ ({
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
})

/** @type {import('vite').Plugin} */
const sharedAssetsPlugin = {
  name: 'shared-assets',
  configureServer(server) {
    server.middlewares.use((req, res, next) => {
      const filePath = join(SHARED_WEB_ASSETS, req.url ?? '')
      if (!req.url?.includes('..') && existsSync(filePath) && statSync(filePath).isFile()) {
        const ext = extname(filePath)
        res.setHeader('Content-Type', MIME_TYPES[ext] ?? 'application/octet-stream')
        createReadStream(filePath).pipe(res)
        return
      }
      next()
    })
  },
  closeBundle() {
    cpSync(SHARED_WEB_ASSETS, 'dist', { recursive: true })
    writeFileSync(join('dist', 'version.json'), `${JSON.stringify(buildInfo, null, 2)}\n`, 'utf8')
  },
}

export default defineConfig({
  site: 'https://profiletailors.com',

  env: {
    schema: {
      AHREFS_ANALYTICS_KEY: envField.string({ context: 'client', access: 'public', optional: true }),
      WAITLIST_ENABLED: envField.boolean({ context: 'client', access: 'public', optional: true, default: false }),
      WAITLIST_API_BASE: envField.string({ context: 'client', access: 'public', optional: true, default: '' }),
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'es'],
    routing: {
      prefixDefaultLocale: false,
    },
  },

  server: {
    port: process.env.PORT ? parseInt(process.env.PORT) : 4321,
    host: process.env.HOST || 'localhost',
  },

  vite: {
    define: {
      __APP_VERSION__: JSON.stringify(buildInfo.version),
      __GIT_SHA__: JSON.stringify(buildInfo.gitSha),
      __BUILD_TIME__: JSON.stringify(buildInfo.buildTime),
    },
    plugins: [
      tailwindcss(),
      sharedAssetsPlugin,
      codecovVitePlugin({
        enableBundleAnalysis: process.env.CODECOV_TOKEN !== undefined,
        bundleName: 'marketing',
        uploadToken: process.env.CODECOV_TOKEN,
      }),
    ],
    resolve: {
      alias: {
        '@shared/assets': SHARED_ASSETS,
      },
    },
    server: {
      watch: {
        usePolling: true,
        interval: 1000,
        ignored: [
          '**/node_modules/**',
          '**/.astro/**',
          '**/bazel-*',
          '**/.git/**',
          '**/bazel-out/**',
          '**/bazel-testlogs/**',
          '**/bazel-profiletailors.com/**',
          '**/.cache/**',
        ],
      },
    },
  },

  integrations: [icon(), vue()],
})

// Fix: deploy static version.json for dynamic deployed version badge
