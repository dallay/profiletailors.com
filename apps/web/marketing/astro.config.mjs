// @ts-check
import { defineConfig } from 'astro/config'
import tailwindcss from '@tailwindcss/vite'
import icon from '@dallay/astro-icon'
import { resolve, join, extname } from 'node:path'
import { cpSync, createReadStream, existsSync } from 'node:fs'

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
      if (!req.url?.includes('..') && existsSync(filePath)) {
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
    plugins: [tailwindcss(), sharedAssetsPlugin],
    resolve: {
      alias: {
        // Import shared SVGs: import logo from '@shared/assets/profiletailors-logotype.svg'
        '@shared/assets': SHARED_ASSETS,
      },
    },
  },

  integrations: [icon()],
})
