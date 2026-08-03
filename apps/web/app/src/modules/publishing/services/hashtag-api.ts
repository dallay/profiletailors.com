import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

export type HashtagPopularity = 'trending' | 'high' | 'medium' | 'low'

export type HashtagSuggestion = {
  hashtag: string
  relevanceScore: number
  popularity: HashtagPopularity
  category: string
  usageCount: number
}

export type HashtagAnalysisResult = {
  content: string
  detectedTopics: string[]
  suggestedHashtags: HashtagSuggestion[]
  maxRecommended: number
}

export type TrendingHashtagsResult = {
  hashtags: HashtagSuggestion[]
}

export type HashtagSavedSet = {
  id: string
  workspaceId: string
  name: string
  hashtags: string[]
  createdAt: string
  updatedAt: string
}

export type HashtagSavedSetsResult = {
  sets: HashtagSavedSet[]
}

async function request<T>(path: string, options?: { method?: string; body?: object }): Promise<T> {
  const auth = useAuthStore()
  return auth.apiFetch<T>(path, {
    method: options?.method ?? 'GET',
    workspaceScoped: true,
    ...(options?.body ? { body: JSON.stringify(options.body) } : {}),
  })
}

export async function analyzeHashtags(content: string): Promise<HashtagAnalysisResult> {
  return request<HashtagAnalysisResult>('/api/hashtags/analyze', {
    method: 'POST',
    body: { content },
  })
}

export async function fetchTrendingHashtags(): Promise<TrendingHashtagsResult> {
  return request<TrendingHashtagsResult>('/api/hashtags/trending')
}

export async function saveHashtagSet(name: string, hashtags: string[]): Promise<HashtagSavedSet> {
  return request<HashtagSavedSet>('/api/hashtags/saved-sets', {
    method: 'POST',
    body: { name, hashtags },
  })
}

export async function listHashtagSavedSets(): Promise<HashtagSavedSetsResult> {
  return request<HashtagSavedSetsResult>('/api/hashtags/saved-sets')
}

export async function deleteHashtagSavedSet(setId: string): Promise<void> {
  await request<void>(`/api/hashtags/saved-sets/${setId}`, { method: 'DELETE' })
}
