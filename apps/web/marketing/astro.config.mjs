// @ts-check
import { defineConfig, envField } from 'astro/config'
import tailwindcss from '@tailwindcss/vite'
import icon from '@dallay/astro-icon'
import { codecovVitePlugin } from '@codecov/vite-plugin'
import { resolve, join, extname } from 'node:path'
import { cpSync, createReadStream, existsSync, statSync } from 'node:fs'

const SHARED_ASSETS = resolve('../../../shared/assets')
const SHARED_WEB_ASSETS = resolve('../../../shared/assets/web')

const MIME_TYPES = /** @type {Record<string, string>} */ ({
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
})

/** @type {import('vite').Plugin} */
const sharedAssetsPlugin = {
  name: 'shared-assets',
  // Serve shared/assets/web/* as static files at the root in dev
  configureServer(server) {
    server.middlewares.use((req, res, next) => {
      const filePath = join(SHARED_WEB_ASSETS, req.url ?? '')
      // Only serve actual files, not directories
      if (!req.url?.includes('..') && existsSync(filePath) && statSync(filePath).isFile()) {
        const ext = extname(filePath)
        res.setHeader('Content-Type', MIME_TYPES[ext] ?? 'application/octet-stream')
        createReadStream(filePath).pipe(res)
        return
      }
      next()
    })
  },
  // Copy shared/assets/web/* into dist/ at build time
  closeBundle() {
    cpSync(SHARED_WEB_ASSETS, 'dist', { recursive: true })
  },
}

// https://astro.build/config
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
        // Import shared SVGs: import logo from '@shared/assets/profiletailors-logotype.svg'
        '@shared/assets': SHARED_ASSETS,
      },
    },
    server: {
      watch: {
        // Use chokidar polling to avoid EISDIR errors on macOS with symlinks
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

  integrations: [icon()],
})
