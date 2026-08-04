import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useHashtagSuggestions } from './useHashtagSuggestions'
import * as api from '@modules/publishing/services/hashtag-api'

describe('useHashtagSuggestions', () => {
  const savedSet = (id = 'set-1', hashtags = ['#one']): api.HashtagSavedSet => ({
    id,
    workspaceId: 'workspace-1',
    name: 'Set',
    hashtags,
    createdAt: '2026-08-03T10:00:00Z',
    updatedAt: '2026-08-03T10:00:00Z',
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('guards short analysis input and stores successful suggestions', async () => {
    const analyze = vi.spyOn(api, 'analyzeHashtags').mockResolvedValue({
      content: 'content',
      detectedTopics: ['technology'],
      suggestedHashtags: [
        {
          hashtag: '#testing',
          relevanceScore: 1,
          popularity: 'high',
          category: 'tech',
          usageCount: 1,
        },
      ],
      maxRecommended: 10,
    })
    const composable = useHashtagSuggestions()

    await composable.analyze('short')
    expect(analyze).not.toHaveBeenCalled()

    await composable.analyze('A'.repeat(50))
    expect(composable.suggestions.value[0]?.hashtag).toBe('#testing')
    expect(composable.isAnalyzing.value).toBe(false)
  })

  it('records analysis failures and always resets the analyzing state', async () => {
    vi.spyOn(api, 'analyzeHashtags').mockRejectedValue(new Error('analysis failed'))
    const composable = useHashtagSuggestions()

    await composable.analyze('A'.repeat(50))

    expect(composable.analyzeError.value).toBe('analyze_failed')
    expect(composable.isAnalyzing.value).toBe(false)
  })

  it('clears an earlier analysis error after the next successful analysis', async () => {
    const analyze = vi
      .spyOn(api, 'analyzeHashtags')
      .mockRejectedValueOnce(new Error('analysis failed'))
      .mockResolvedValueOnce({
        content: 'A'.repeat(50),
        detectedTopics: ['technology'],
        suggestedHashtags: [],
        maxRecommended: 10,
      })
    const composable = useHashtagSuggestions()

    await composable.analyze('A'.repeat(50))
    expect(composable.analyzeError.value).toBe('analyze_failed')

    await composable.analyze('B'.repeat(50))

    expect(analyze).toHaveBeenCalledTimes(2)
    expect(composable.analyzeError.value).toBeNull()
    expect(composable.isAnalyzing.value).toBe(false)
  })

  it('exposes the analyzing state while a valid analysis request is pending', async () => {
    let resolveAnalysis: ((result: api.HashtagAnalysisResult) => void) | undefined
    vi.spyOn(api, 'analyzeHashtags').mockReturnValue(
      new Promise((resolve) => {
        resolveAnalysis = resolve
      }),
    )
    const composable = useHashtagSuggestions()

    const pending = composable.analyze('A'.repeat(50))
    expect(composable.isAnalyzing.value).toBe(true)
    expect(composable.analyzeError.value).toBeNull()

    resolveAnalysis?.({
      content: 'A'.repeat(50),
      detectedTopics: [],
      suggestedHashtags: [],
      maxRecommended: 10,
    })
    await pending

    expect(composable.isAnalyzing.value).toBe(false)
  })

  it('applies saved sets up to the limit and builds a post block', () => {
    const composable = useHashtagSuggestions()
    composable.addHashtag('#one')
    composable.applySet({
      id: 'set-1',
      workspaceId: 'workspace-1',
      name: 'Set',
      hashtags: ['#two', '#three'],
      createdAt: '',
      updatedAt: '',
    })

    expect(composable.hashtagCount.value).toBe(3)
    expect(composable.buildHashtagBlock()).toBe('\n\n#one #two #three')
    composable.removeHashtag('#two')
    expect(composable.isAdded('#two')).toBe(false)
  })

  it('tracks approaching and reached limits while preventing new additions at the limit', () => {
    const composable = useHashtagSuggestions()

    for (let index = 0; index < 28; index += 1) {
      expect(composable.addHashtag(`#tag-${index}`)).toBe(true)
    }
    expect(composable.hashtagCount.value).toBe(28)
    expect(composable.isApproachingLimit.value).toBe(true)
    expect(composable.isAtLimit.value).toBe(false)

    composable.addHashtag('#tag-28')
    composable.addHashtag('#tag-29')
    expect(composable.hashtagCount.value).toBe(30)
    expect(composable.isApproachingLimit.value).toBe(false)
    expect(composable.isAtLimit.value).toBe(true)
    expect(composable.addHashtag('#tag-30')).toBe(false)
    expect(composable.hashtagCount.value).toBe(30)

    composable.removeHashtag('#tag-29')
    expect(composable.hashtagCount.value).toBe(29)
    expect(composable.isApproachingLimit.value).toBe(true)
  })

  it('stops applying a saved set once the limit is reached', () => {
    const composable = useHashtagSuggestions()
    for (let index = 0; index < 29; index += 1) {
      composable.addHashtag(`#tag-${index}`)
    }

    composable.applySet(savedSet('set-2', ['#first-new', '#second-new']))

    expect(composable.hashtagCount.value).toBe(30)
    expect(composable.isAdded('#first-new')).toBe(true)
    expect(composable.isAdded('#second-new')).toBe(false)
  })

  it('returns an empty block when no hashtags are selected', () => {
    expect(useHashtagSuggestions().buildHashtagBlock()).toBe('')
  })

  it('tracks saved-set creation and deletion state', async () => {
    vi.spyOn(api, 'saveHashtagSet').mockResolvedValue({
      id: 'set-1',
      workspaceId: 'workspace-1',
      name: 'Set',
      hashtags: ['#one'],
      createdAt: '',
      updatedAt: '',
    })
    const deleteSet = vi.spyOn(api, 'deleteHashtagSavedSet').mockResolvedValue(undefined)
    const composable = useHashtagSuggestions()

    await composable.saveSet('Set', ['#one'])
    expect(composable.savedSets.value).toHaveLength(1)
    await composable.deleteSet('set-1')
    expect(deleteSet).toHaveBeenCalledWith('set-1')
    expect(composable.savedSets.value).toHaveLength(0)
  })

  it('resets saving state and preserves saved sets when saving fails', async () => {
    vi.spyOn(api, 'saveHashtagSet').mockRejectedValue(new Error('save failed'))
    const composable = useHashtagSuggestions()

    await expect(composable.saveSet('Set', ['#one'])).rejects.toThrow('save failed')

    expect(composable.isSaving.value).toBe(false)
    expect(composable.savedSets.value).toEqual([])
  })

  it('keeps a saved set when deletion fails', async () => {
    vi.spyOn(api, 'deleteHashtagSavedSet').mockRejectedValue(new Error('delete failed'))
    const composable = useHashtagSuggestions()
    composable.savedSets.value = [savedSet()]

    await expect(composable.deleteSet('set-1')).rejects.toThrow('delete failed')

    expect(composable.savedSets.value).toEqual([savedSet()])
  })

  it('loads trending and saved sets without surfacing non-critical failures', async () => {
    const trending = vi.spyOn(api, 'fetchTrendingHashtags').mockResolvedValue({ hashtags: [] })
    const saved = vi
      .spyOn(api, 'listHashtagSavedSets')
      .mockRejectedValue(new Error('saved sets unavailable'))
    const composable = useHashtagSuggestions()

    await composable.loadTrending()
    await composable.loadSavedSets()

    expect(trending).toHaveBeenCalledOnce()
    expect(saved).toHaveBeenCalledOnce()
    expect(composable.savedSets.value).toEqual([])
  })

  it('stores successful trending and saved-set responses', async () => {
    const trending = vi.spyOn(api, 'fetchTrendingHashtags').mockResolvedValue({
      hashtags: [
        {
          hashtag: '#trending',
          relevanceScore: 0.9,
          popularity: 'trending',
          category: 'technology',
          usageCount: 100,
        },
      ],
    })
    const saved = vi.spyOn(api, 'listHashtagSavedSets').mockResolvedValue({ sets: [savedSet()] })
    const composable = useHashtagSuggestions()

    await Promise.all([composable.loadTrending(), composable.loadSavedSets()])

    expect(trending).toHaveBeenCalledOnce()
    expect(saved).toHaveBeenCalledOnce()
    expect(composable.trending.value[0]?.hashtag).toBe('#trending')
    expect(composable.savedSets.value).toEqual([savedSet()])
  })

  it('silently keeps the previous trending results when loading trending fails', async () => {
    const composable = useHashtagSuggestions()
    composable.trending.value = [
      {
        hashtag: '#existing',
        relevanceScore: 0.8,
        popularity: 'high',
        category: 'technology',
        usageCount: 20,
      },
    ]
    vi.spyOn(api, 'fetchTrendingHashtags').mockRejectedValue(new Error('trending unavailable'))

    await composable.loadTrending()

    expect(composable.trending.value[0]?.hashtag).toBe('#existing')
  })
})
