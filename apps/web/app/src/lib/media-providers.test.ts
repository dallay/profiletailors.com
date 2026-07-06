import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  searchProviderPhotos,
  importProviderPhoto,
  type MediaProviderSearchResponse,
  type MediaProviderImportResponse,
} from './media-providers'

vi.mock('./auth-api', () => ({
  resolveApiUrl: (path: string) => path,
}))

describe('media-providers', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('searchProviderPhotos sends query and page and returns the typed response', async () => {
    const token = 'tkn'
    const workspaceId = 'ws-1'
    const response: MediaProviderSearchResponse = {
      items: [
        {
          externalId: 'unsplash:abc',
          previewUrl: 'https://cdn.example/abc/preview',
          fullUrl: 'https://cdn.example/abc/full',
          width: 1024,
          height: 768,
          authorName: 'A',
          authorUrl: 'https://example/a',
          sourceUrl: 'https://unsplash.com/photos/abc',
        },
      ],
      page: { number: 1, size: 20, total: 1 },
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => response,
    })
    vi.stubGlobal('fetch', fetchMock)

    const result = await searchProviderPhotos('unsplash', workspaceId, 'mountains', 1, token)

    expect(result).toEqual(response)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(
      `/api/workspaces/${workspaceId}/media/providers/unsplash/search?query=mountains&page=1`,
    )
    expect(init.method).toBe('GET')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer tkn')
    expect((init.headers as Record<string, string>)['X-Workspace-Id']).toBe(workspaceId)
  })

  it('searchProviderPhotos throws an error with status 404 when the provider is unknown', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: async () => ({ title: 'Not Found' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(searchProviderPhotos('unsplash', 'ws-1', 'q', 1, 't')).rejects.toMatchObject({
      status: 404,
    })
  })

  it('importProviderPhoto POSTs the externalId and returns the typed response', async () => {
    const token = 'tkn'
    const workspaceId = 'ws-1'
    const response: MediaProviderImportResponse = {
      assetId: 'asset-1',
      deduped: false,
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => response,
    })
    vi.stubGlobal('fetch', fetchMock)

    const result = await importProviderPhoto('unsplash', workspaceId, 'unsplash:abc', token)

    expect(result).toEqual(response)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`/api/workspaces/${workspaceId}/media/providers/unsplash/import`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ externalId: 'unsplash:abc' })
  })

  it('importProviderPhoto surfaces 400 INVALID_EXTERNAL_ID', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: async () => ({ title: 'Bad Request', code: 'INVALID_EXTERNAL_ID' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      importProviderPhoto('unsplash', 'ws-1', 'wrong-prefix:abc', 't'),
    ).rejects.toMatchObject({ status: 400, code: 'INVALID_EXTERNAL_ID' })
  })

  it('importProviderPhoto surfaces 502 PROVIDER_ERROR from upstream', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      statusText: 'Bad Gateway',
      json: async () => ({ title: 'Provider Error', code: 'PROVIDER_ERROR' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      importProviderPhoto('unsplash', 'ws-1', 'unsplash:abc', 't'),
    ).rejects.toMatchObject({ status: 502, code: 'PROVIDER_ERROR' })
  })
})
