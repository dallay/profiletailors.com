import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@modules/auth'
import {
  generateAiPostContent,
  optimizeAiPostContent,
  regenerateAiPostContent,
} from './ai-content-api'

describe('AI content API', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('normalizes keywords and sends a workspace-scoped generate request', async () => {
    const auth = useAuthStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ content: 'Generated' })

    await generateAiPostContent({
      prompt: 'testing',
      context: {},
      options: {
        format: 'standard',
        tone: 'professional',
        length: 'short',
        keywords: [' Kotlin ', '', 'cloud'],
      },
    })

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/ai/generate', {
      method: 'POST',
      workspaceScoped: true,
      body: JSON.stringify({
        prompt: 'testing',
        context: {},
        options: {
          format: 'standard',
          tone: 'professional',
          length: 'short',
          keywords: ['Kotlin', 'cloud'],
        },
      }),
    })
  })

  it('returns the mapped content response without dropping optional suggestions or title', async () => {
    const auth = useAuthStore()
    const response = {
      content: 'Generated content',
      title: 'A stronger title',
      suggestions: [{ text: 'Open with a concrete result.' }],
    }
    vi.spyOn(auth, 'apiFetch').mockResolvedValue(response)

    await expect(
      generateAiPostContent({
        prompt: 'testing',
        context: {},
        options: {
          format: 'standard',
          tone: 'professional',
          length: 'medium',
          keywords: [],
        },
      }),
    ).resolves.toEqual(response)
  })

  it('uses dedicated optimize and regenerate endpoints with their content fields', async () => {
    const auth = useAuthStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ content: 'Updated' })
    const request = {
      prompt: 'testing',
      context: {},
      options: {
        format: 'standard' as const,
        tone: 'professional' as const,
        length: 'short' as const,
        keywords: [],
      },
    }

    await optimizeAiPostContent({ ...request, content: 'Draft' })
    await regenerateAiPostContent({ ...request, previous_content: 'Previous' })

    expect(apiFetch.mock.calls.map(([path]) => path)).toEqual([
      '/api/v1/ai/optimize',
      '/api/v1/ai/regenerate',
    ])
    expect(apiFetch.mock.calls[0]?.[1]).toMatchObject({
      body: expect.stringContaining('"content":"Draft"'),
    })
    expect(apiFetch.mock.calls[1]?.[1]).toMatchObject({
      body: expect.stringContaining('"previous_content":"Previous"'),
    })
  })

  it('fetches AI suggestions with a GET request', async () => {
    const auth = useAuthStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
      postId: 'post-1',
      suggestions: [{ text: 'Try a stronger hook.' }],
    })

    const { fetchAiSuggestions } = await import('./ai-content-api')
    await fetchAiSuggestions('post-1')

    expect(apiFetch).toHaveBeenCalledWith('/api/v1/ai/suggestions/post-1', {
      method: 'GET',
      workspaceScoped: true,
    })
  })
})
