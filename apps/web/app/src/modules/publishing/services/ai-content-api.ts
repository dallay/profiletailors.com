import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

export type AiGenerationFormat = 'standard' | 'thread' | 'tips' | 'question' | 'story'
export type AiGenerationTone = 'professional' | 'casual' | 'inspirational' | 'educational'
export type AiGenerationLength = 'short' | 'medium' | 'long'

export type AiUserProfile = {
  display_name: string
  email?: string | null
  username?: string | null
}

export type AiGenerationContext = {
  user_profile?: AiUserProfile
  industry?: string
  target_audience?: string
}

export type AiGenerationOptions = {
  format: AiGenerationFormat
  tone: AiGenerationTone
  length: AiGenerationLength
  keywords: string[]
}

export type AiGenerationRequest = {
  prompt: string
  context: AiGenerationContext
  options: AiGenerationOptions
}

export type AiOptimizationRequest = AiGenerationRequest & {
  content: string
}

export type AiRegenerationRequest = AiGenerationRequest & {
  previous_content: string
}

export type AiSuggestion = {
  text: string
}

export type AiContentResponse = {
  content: string
  suggestions?: AiSuggestion[]
  title?: string | null
}

export type AiSuggestionsResponse = {
  postId: string
  suggestions: AiSuggestion[]
}

function normalizeKeywords(keywords: string[]): string[] {
  return keywords.map((keyword) => keyword.trim()).filter((keyword) => keyword.length > 0)
}

async function requestAi<T>(path: string, body?: object): Promise<T> {
  const auth = useAuthStore()
  const response = await auth.apiFetch<T>(path, {
    method: body ? 'POST' : 'GET',
    workspaceScoped: true,
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  return response
}

export async function generateAiPostContent(
  request: AiGenerationRequest,
): Promise<AiContentResponse> {
  return requestAi<AiContentResponse>('/api/v1/ai/generate', {
    ...request,
    options: {
      ...request.options,
      keywords: normalizeKeywords(request.options.keywords),
    },
  })
}

export async function optimizeAiPostContent(
  request: AiOptimizationRequest,
): Promise<AiContentResponse> {
  return requestAi<AiContentResponse>('/api/v1/ai/optimize', {
    ...request,
    options: {
      ...request.options,
      keywords: normalizeKeywords(request.options.keywords),
    },
  })
}

export async function regenerateAiPostContent(
  request: AiRegenerationRequest,
): Promise<AiContentResponse> {
  return requestAi<AiContentResponse>('/api/v1/ai/regenerate', {
    ...request,
    options: {
      ...request.options,
      keywords: normalizeKeywords(request.options.keywords),
    },
  })
}

export async function fetchAiSuggestions(postId: string): Promise<AiSuggestionsResponse> {
  return requestAi<AiSuggestionsResponse>(`/api/v1/ai/suggestions/${postId}`)
}
