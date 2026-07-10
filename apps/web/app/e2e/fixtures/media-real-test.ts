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
    const email = process.env.E2E_MEDIA_EMAIL
    const password = process.env.E2E_MEDIA_PASSWORD
    if (!email || !password) {
      throw new Error('E2E_MEDIA_EMAIL and E2E_MEDIA_PASSWORD must be set for real media tests')
    }
    await authenticateAs(page, {
      email,
      password,
    })

    await page.setExtraHTTPHeaders({
      'X-Workspace-Id': 'dev-workspace-001',
      'x-e2e-run-id': runId,
    })
    await use(page)

    // Teardown/cleanup runs after the test finishes
    const apiRequest = context.request
    const workspaceId = 'dev-workspace-001'

    async function fetchAllPublications(): Promise<Array<Record<string, unknown>>> {
      const all: Array<Record<string, unknown>> = []
      let cursor: string | undefined
      for (let i = 0; i < 50; i++) {
        const url = cursor
          ? `/api/publishing/publications?limit=100&cursor=${encodeURIComponent(cursor)}`
          : '/api/publishing/publications?limit=100'
        const res = await apiRequest.get(url, { headers: { 'X-Workspace-Id': workspaceId } })
        if (!res.ok()) {
          throw new Error(
            `Failed to list publications during teardown (${res.status()} ${res.statusText()}) for ${url}`,
          )
        }
        const data = (await res.json()) as {
          publications?: Array<Record<string, unknown>>
          cursor?: string
        }
        all.push(...(data.publications || []))
        if (!data.cursor) break
        cursor = data.cursor
      }
      return all
    }

    async function fetchAllAssets(): Promise<Array<Record<string, unknown>>> {
      const all: Array<Record<string, unknown>> = []
      let cursor: string | undefined
      for (let i = 0; i < 50; i++) {
        const url = cursor
          ? `/api/media/assets?pageSize=100&cursor=${encodeURIComponent(cursor)}`
          : '/api/media/assets?pageSize=100'
        const res = await apiRequest.get(url, { headers: { 'X-Workspace-Id': workspaceId } })
        if (!res.ok()) {
          throw new Error(
            `Failed to list assets during teardown (${res.status()} ${res.statusText()}) for ${url}`,
          )
        }
        const data = (await res.json()) as {
          assets?: Array<Record<string, unknown>>
          nextCursor?: string
        }
        all.push(...(data.assets || []))
        if (!data.nextCursor) break
        cursor = data.nextCursor
      }
      return all
    }

    // 1. Delete publications (paginated)
    try {
      const allPublications = await fetchAllPublications()
      for (const pub of allPublications) {
        const bodyText = (pub.bodyText as string) || ''
        const title = (pub.title as string) || ''
        if (bodyText.includes(`e2e-cas-${runId}-`) || title.includes(`e2e-cas-${runId}-`)) {
          await apiRequest.delete(`/api/publishing/publications/${pub.id}`, {
            headers: { 'X-Workspace-Id': workspaceId },
          })
        }
      }
    } catch (err) {
      console.warn('Failed to cleanup publications:', err)
    }

    // 2. Delete media assets (paginated)
    try {
      const allAssets = await fetchAllAssets()
      for (const asset of allAssets) {
        const filename = (asset.originalFilename as string) || ''
        if (filename.startsWith(`e2e-cas-${runId}-`)) {
          await apiRequest.delete(`/api/media/assets/${asset.assetId}`, {
            headers: { 'X-Workspace-Id': workspaceId },
          })
        }
      }
    } catch (err) {
      console.warn('Failed to cleanup media assets:', err)
    }

    // 3. Double check — fail if any run-owned publications OR assets remain
    const remainingPublications = (await fetchAllPublications()).filter(
      (pub) =>
        ((pub.bodyText as string) || '').includes(`e2e-cas-${runId}-`) ||
        ((pub.title as string) || '').includes(`e2e-cas-${runId}-`),
    )
    const remainingAssets = (await fetchAllAssets()).filter((asset) =>
      ((asset.originalFilename as string) || '').startsWith(`e2e-cas-${runId}-`),
    )
    if (remainingPublications.length > 0 || remainingAssets.length > 0) {
      const pubNames = remainingPublications.map((p) => (p as { id?: string }).id ?? '?')
      const assetNames = remainingAssets.map((a) => (a.originalFilename as string) ?? '?')
      throw new Error(
        `Teardown failed: ${remainingPublications.length} run-owned publications (${pubNames.join(', ')}), ${remainingAssets.length} run-owned assets (${assetNames.join(', ')}) still remain.`,
      )
    }
  },

  mediaPage: async ({ page }, use) => {
    await use(new MediaLibraryPage(page))
  },
})

export { expect }
export type { BrowserContext, Page }
