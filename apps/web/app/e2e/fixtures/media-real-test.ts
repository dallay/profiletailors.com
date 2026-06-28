import { test as base, expect, type BrowserContext, type Page } from '@playwright/test'
import path from 'node:path'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { authenticateAs } from './auth-helpers'
import { RequestLedger } from './media-request-ledger'
import { MediaLibraryPage } from '../pages/media-library-page'
import { mediaFiles } from './media-files'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

interface RunAsset {
  name: string
  path: string
  type: string
}

interface MediaRealFixtures {
  runId: string
  requestLedger: RequestLedger
  mediaPage: MediaLibraryPage
  runFiles: {
    readonly base: RunAsset
    readonly baseCopy: RunAsset
    readonly mutated: RunAsset
  }
}

/**
 * Real CAS test fixture that logs in via UI and sets workspace/run headers.
 * No HAR, no route interception of /api/media/**.
 */
export const test = base.extend<MediaRealFixtures>({
  runId: async ({ browser }, use) => {
    // Avoid unused parameter warnings
    const _ = browser
    const random = Math.random().toString(36).slice(2, 10)
    const runId = `${random}`
    await use(runId)
  },

  requestLedger: async ({ context }, use) => {
    const ledger = new RequestLedger(context)
    await use(ledger)
  },

  runFiles: async ({ runId }, use) => {
    const tempDir = path.resolve(__dirname, '../.generated/run-media', runId)
    fs.mkdirSync(tempDir, { recursive: true })

    const baseName = `e2e-cas-${runId}-${mediaFiles.base.name}`
    const baseCopyName = `e2e-cas-${runId}-${mediaFiles.baseCopy.name}`
    const mutatedName = `e2e-cas-${runId}-${mediaFiles.mutated.name}`

    const basePath = path.join(tempDir, baseName)
    const baseCopyPath = path.join(tempDir, baseCopyName)
    const mutatedPath = path.join(tempDir, mutatedName)

    fs.copyFileSync(mediaFiles.base.path, basePath)
    fs.copyFileSync(mediaFiles.baseCopy.path, baseCopyPath)
    fs.copyFileSync(mediaFiles.mutated.path, mutatedPath)

    await use({
      base: { name: baseName, path: basePath, type: mediaFiles.base.type },
      baseCopy: { name: baseCopyName, path: baseCopyPath, type: mediaFiles.baseCopy.type },
      mutated: { name: mutatedName, path: mutatedPath, type: mediaFiles.mutated.type },
    })

    // Clean up temporary files on disk
    try {
      fs.rmSync(tempDir, { recursive: true, force: true })
    } catch {
      // Ignore
    }
  },

  page: async ({ page, context, runId }, use) => {
    // Authenticate the active page first via login form
    await authenticateAs(page, {
      email: 'dev@profiletailors.com',
      password: 'S3cr3tP@ssw0rd*123',
    })

    await page.setExtraHTTPHeaders({
      'X-Workspace-Id': 'dev-workspace-001',
      'x-e2e-run-id': runId,
    })
    await use(page)

    // Teardown/cleanup runs after the test finishes
    const apiRequest = context.request
    const workspaceId = 'dev-workspace-001'

    // 1. Delete publications
    try {
      const publicationsResponse = await apiRequest.get('/api/publishing/publications?limit=100', {
        headers: {
          'X-Workspace-Id': workspaceId,
        },
      })
      if (publicationsResponse.ok()) {
        const data = await publicationsResponse.json()
        const publications = data.publications || []
        for (const pub of publications) {
          const bodyText = pub.bodyText || ''
          const title = pub.title || ''
          if (bodyText.includes(`e2e-cas-${runId}-`) || title.includes(`e2e-cas-${runId}-`)) {
            await apiRequest.delete(`/api/publishing/publications/${pub.id}`, {
              headers: {
                'X-Workspace-Id': workspaceId,
              },
            })
          }
        }
      }
    } catch (err) {
      console.warn('Failed to cleanup publications:', err)
    }

    // 2. Delete media assets
    try {
      const assetsResponse = await apiRequest.get('/api/media/assets?pageSize=100', {
        headers: {
          'X-Workspace-Id': workspaceId,
        },
      })
      if (assetsResponse.ok()) {
        const data = await assetsResponse.json()
        const assets = data.assets || []
        for (const asset of assets) {
          const filename = asset.originalFilename || ''
          if (filename.startsWith(`e2e-cas-${runId}-`)) {
            await apiRequest.delete(`/api/media/assets/${asset.assetId}`, {
              headers: {
                'X-Workspace-Id': workspaceId,
              },
            })
          }
        }
      }
    } catch (err) {
      console.warn('Failed to cleanup media assets:', err)
    }

    // 3. Double check and fail if any run assets remain
    const checkResponse = await apiRequest.get('/api/media/assets?pageSize=100', {
      headers: {
        'X-Workspace-Id': workspaceId,
      },
    })
    if (checkResponse.ok()) {
      const data = await checkResponse.json()
      const assets = (data.assets || []) as Array<{ originalFilename?: string | null }>
      const remaining = assets.filter((asset) =>
        (asset.originalFilename || '').startsWith(`e2e-cas-${runId}-`),
      )
      if (remaining.length > 0) {
        throw new Error(
          `Teardown failed: ${remaining.length} run-owned media assets still remain: ${remaining.map((a) => a.originalFilename).join(', ')}`,
        )
      }
    }
  },

  mediaPage: async ({ page }, use) => {
    await use(new MediaLibraryPage(page))
  },
})

export { expect }
export type { BrowserContext, Page }
