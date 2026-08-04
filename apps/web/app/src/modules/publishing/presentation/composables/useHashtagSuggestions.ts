import { ref, computed } from 'vue'
import {
  analyzeHashtags,
  fetchTrendingHashtags,
  saveHashtagSet,
  listHashtagSavedSets,
  deleteHashtagSavedSet,
  type HashtagSuggestion,
  type HashtagSavedSet,
} from '@modules/publishing/services/hashtag-api'

export const LINKEDIN_HASHTAG_LIMIT = 30
export const ANALYZE_MIN_CHARS = 50

export function useHashtagSuggestions() {
  const suggestions = ref<HashtagSuggestion[]>([])
  const trending = ref<HashtagSuggestion[]>([])
  const savedSets = ref<HashtagSavedSet[]>([])
  const addedHashtags = ref<Set<string>>(new Set())
  const isAnalyzing = ref(false)
  const isSaving = ref(false)
  const analyzeError = ref<string | null>(null)

  const hashtagCount = computed(() => addedHashtags.value.size)
  const isAtLimit = computed(() => hashtagCount.value >= LINKEDIN_HASHTAG_LIMIT)
  const isApproachingLimit = computed(
    () =>
      hashtagCount.value >= LINKEDIN_HASHTAG_LIMIT - 2 &&
      hashtagCount.value < LINKEDIN_HASHTAG_LIMIT,
  )

  async function analyze(content: string) {
    if (content.length < ANALYZE_MIN_CHARS) return
    isAnalyzing.value = true
    analyzeError.value = null
    try {
      const result = await analyzeHashtags(content)
      suggestions.value = result.suggestedHashtags
    } catch {
      analyzeError.value = 'analyze_failed'
    } finally {
      isAnalyzing.value = false
    }
  }

  async function loadTrending() {
    try {
      const result = await fetchTrendingHashtags()
      trending.value = result.hashtags
    } catch {
      // trending is non-critical — fail silently
    }
  }

  async function loadSavedSets() {
    try {
      const result = await listHashtagSavedSets()
      savedSets.value = result.sets
    } catch {
      // non-critical
    }
  }

  function addHashtag(hashtag: string): boolean {
    if (isAtLimit.value) return false
    addedHashtags.value = new Set([...addedHashtags.value, hashtag])
    return true
  }

  function removeHashtag(hashtag: string) {
    const next = new Set(addedHashtags.value)
    next.delete(hashtag)
    addedHashtags.value = next
  }

  function isAdded(hashtag: string): boolean {
    return addedHashtags.value.has(hashtag)
  }

  function applySet(set: HashtagSavedSet) {
    for (const tag of set.hashtags) {
      if (isAtLimit.value) break
      addedHashtags.value = new Set([...addedHashtags.value, tag])
    }
  }

  async function saveSet(name: string, hashtags: string[]): Promise<void> {
    isSaving.value = true
    try {
      const created = await saveHashtagSet(name, hashtags)
      savedSets.value = [...savedSets.value, created]
    } finally {
      isSaving.value = false
    }
  }

  async function deleteSet(setId: string): Promise<void> {
    await deleteHashtagSavedSet(setId)
    savedSets.value = savedSets.value.filter((s) => s.id !== setId)
  }

  function buildHashtagBlock(): string {
    if (addedHashtags.value.size === 0) return ''
    return `\n\n${[...addedHashtags.value].join(' ')}`
  }

  return {
    suggestions,
    trending,
    savedSets,
    addedHashtags,
    isAnalyzing,
    isSaving,
    analyzeError,
    hashtagCount,
    isAtLimit,
    isApproachingLimit,
    analyze,
    loadTrending,
    loadSavedSets,
    addHashtag,
    removeHashtag,
    isAdded,
    applySet,
    saveSet,
    deleteSet,
    buildHashtagBlock,
  }
}
