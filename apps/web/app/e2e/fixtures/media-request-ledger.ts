import type { BrowserContext, Request, Response } from '@playwright/test'

export interface CasEvent {
  method: 'PUT' | 'POST' | 'GET' | 'DELETE'
  url: string
  status?: number
  assetId?: string
  workspaceId?: string
  fixture?: string
  body?: unknown
  timestamp: number
}

/**
 * Request ledger for capturing and asserting CAS protocol sequences.
 * Attaches to browser context request/response lifecycle events and
 * filters media API traffic by assetId, workspace, fixture hash, and method.
 */
export class RequestLedger {
  private events: CasEvent[] = []
  private context: BrowserContext

  constructor(context: BrowserContext) {
    this.context = context
    this.attachListeners()
  }

  private attachListeners(): void {
    this.context.on('request', (request: Request) => {
      if (!this.isMediaApiRequest(request)) return

      const event: CasEvent = {
        method: request.method() as CasEvent['method'],
        url: request.url(),
        assetId: this.extractAssetId(request.url()),
        workspaceId: this.extractWorkspaceId(request),
        fixture: this.extractFixtureHash(request),
        timestamp: Date.now(),
      }

      this.events.push(event)
    })

    this.context.on('response', (response: Response) => {
      const request = response.request()
      if (!this.isMediaApiRequest(request)) return

      const index = this.events.findIndex(
        (e) => e.url === request.url() && e.method === request.method() && !e.status,
      )

      if (index >= 0) {
        this.events[index].status = response.status()
      }
    })

    this.context.on('requestfailed', (request: Request) => {
      if (!this.isMediaApiRequest(request)) return

      const index = this.events.findIndex(
        (e) => e.url === request.url() && e.method === request.method() && !e.status,
      )

      if (index >= 0) {
        this.events[index].status = 0
      }
    })
  }

  private isMediaApiRequest(request: Request): boolean {
    const url = request.url()
    return url.includes('/api/media/')
  }

  private extractAssetId(url: string): string | undefined {
    const match = /\/api\/media\/assets\/([^/?]+)/.exec(url)
    return match?.[1] ? decodeURIComponent(match[1]) : undefined
  }

  private extractWorkspaceId(request: Request): string | undefined {
    return request.headers()['x-workspace-id'] || undefined
  }

  private extractFixtureHash(request: Request): string | undefined {
    const postData = request.postDataJSON()
    if (postData && typeof postData === 'object' && 'fileHash' in postData) {
      return String(postData.fileHash)
    }
    return undefined
  }

  /**
   * Get all events for a specific assetId.
   */
  forAsset(assetId: string): CasEvent[] {
    return this.events.filter((e) => e.assetId === assetId)
  }

  /**
   * Get all events for a specific fixture hash.
   */
  forFixture(hash: string): CasEvent[] {
    return this.events.filter((e) => e.fixture === hash)
  }

  /**
   * Get all events of a specific method type.
   */
  forMethod(method: CasEvent['method']): CasEvent[] {
    return this.events.filter((e) => e.method === method)
  }

  /**
   * Assert that the event sequence matches the expected pattern.
   * Example: ['PUT 201', 'POST 200', 'GET 200']
   */
  assertSequence(assetId: string, expected: string[]): void {
    const events = this.forAsset(assetId)
    const actual = events.map((e) => `${e.method} ${e.status ?? '?'}`)

    if (actual.length < expected.length) {
      throw new Error(
        `Expected ${expected.length} events for asset ${assetId}, got ${actual.length}.\nExpected: ${expected.join(', ')}\nActual: ${actual.join(', ')}`,
      )
    }

    for (let i = 0; i < expected.length; i++) {
      if (actual[i] !== expected[i]) {
        throw new Error(
          `Event ${i} mismatch for asset ${assetId}.\nExpected: ${expected[i]}\nActual: ${actual[i]}\nFull sequence: ${actual.join(', ')}`,
        )
      }
    }
  }

  /**
   * Assert that zero POST requests with /upload were made.
   * Used to verify dedup scenarios skip binary upload.
   */
  assertZeroPosts(): void {
    const posts = this.events.filter((e) => e.method === 'POST' && e.url.includes('/upload'))
    if (posts.length > 0) {
      throw new Error(
        `Expected zero POST /upload requests, found ${posts.length}:\n${posts.map((p) => p.url).join('\n')}`,
      )
    }
  }

  /**
   * Get all captured events (for debugging).
   */
  getAllEvents(): CasEvent[] {
    return [...this.events]
  }

  /**
   * Reset the ledger (clear all events).
   */
  reset(): void {
    this.events = []
  }
}
