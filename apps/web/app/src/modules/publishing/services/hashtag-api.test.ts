import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@modules/auth'
import {
  analyzeHashtags,
  deleteHashtagSavedSet,
  fetchTrendingHashtags,
  listHashtagSavedSets,
  saveHashtagSet,
} from './hashtag-api'

const analysisResult = {
  content: 'A long enough post about software testing and reliable delivery.',
  detectedTopics: ['technology'],
  suggestedHashtags: [],
  maxRecommended: 10,
}

describe('hashtag API', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('sends analyze and save requests with workspace scope', async () => {
    const auth = useAuthStore()
    const apiFetch = vi
      .spyOn(auth, 'apiFetch')
      .mockResolvedValueOnce(analysisResult)
      .mockResolvedValueOnce({
        id: 'set-1',
        workspaceId: 'workspace-1',
        name: 'Engineering',
        hashtags: ['#testing', '#delivery'],
        createdAt: '2026-08-03T10:00:00Z',
        updatedAt: '2026-08-03T10:00:00Z',
      })

    await analyzeHashtags('A long enough post about software testing and reliable delivery.')
    await saveHashtagSet('Engineering', ['#testing', '#delivery'])

    expect(apiFetch.mock.calls[0]).toEqual([
      '/api/hashtags/analyze',
      {
        method: 'POST',
        workspaceScoped: true,
        body: JSON.stringify({
          content: 'A long enough post about software testing and reliable delivery.',
        }),
      },
    ])
    expect(apiFetch.mock.calls[1]?.[1]).toMatchObject({
      method: 'POST',
      workspaceScoped: true,
      body: JSON.stringify({ name: 'Engineering', hashtags: ['#testing', '#delivery'] }),
    })
  })

  it('returns typed response data from analysis, trending, and saved-set requests', async () => {
    const auth = useAuthStore()
    const savedSet = {
      id: 'set-1',
      workspaceId: 'workspace-1',
      name: 'Engineering',
      hashtags: ['#testing'],
      createdAt: '2026-08-03T10:00:00Z',
      updatedAt: '2026-08-03T10:00:00Z',
    }
    vi.spyOn(auth, 'apiFetch')
      .mockResolvedValueOnce(analysisResult)
      .mockResolvedValueOnce({ hashtags: [] })
      .mockResolvedValueOnce({ sets: [savedSet] })

    await expect(analyzeHashtags(analysisResult.content)).resolves.toEqual(analysisResult)
    await expect(fetchTrendingHashtags()).resolves.toEqual({ hashtags: [] })
    await expect(listHashtagSavedSets()).resolves.toEqual({ sets: [savedSet] })
  })

  it('deletes a saved set with the requested identifier', async () => {
    const auth = useAuthStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(undefined)

    await deleteHashtagSavedSet('set-1')

    expect(apiFetch).toHaveBeenCalledWith('/api/hashtags/saved-sets/set-1', {
      method: 'DELETE',
      workspaceScoped: true,
    })
  })

  it('propagates a successful no-content delete response', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'apiFetch').mockResolvedValue(Object.create(null))

    await expect(deleteHashtagSavedSet('set-204')).resolves.toBeUndefined()
  })

  it('propagates transport errors from hashtag endpoints', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('network failed'))

    await expect(fetchTrendingHashtags()).rejects.toThrow('network failed')
  })

  it('reads trending and saved sets through workspace-scoped GET requests', async () => {
    const auth = useAuthStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ hashtags: [], sets: [] })

    await fetchTrendingHashtags()
    await listHashtagSavedSets()

    expect(apiFetch).toHaveBeenNthCalledWith(1, '/api/hashtags/trending', {
      method: 'GET',
      workspaceScoped: true,
    })
    expect(apiFetch).toHaveBeenNthCalledWith(2, '/api/hashtags/saved-sets', {
      method: 'GET',
      workspaceScoped: true,
    })
  })
})
