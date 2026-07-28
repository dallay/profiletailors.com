import { describe, it, expect, vi } from 'vitest'
import { ref, type Ref } from 'vue'
import { useComposerTextFormatting } from './useComposerTextFormatting'

// Helper para crear refs
function createRef<T>(value: T): Ref<T> {
  return ref(value) as Ref<T>
}

describe('useComposerTextFormatting', () => {
  // ============================================================================
  // INITIALIZATION
  // ============================================================================

  describe('initialization', () => {
    it('starts with isAiProcessing false', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.isAiProcessing.value).toBe(false)
    })
  })

  // ============================================================================
  // HASHTAGS - normalizeHashtag
  // ============================================================================

  describe('normalizeHashtag', () => {
    it('adds # prefix if missing', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.normalizeHashtag('socialmedia')).toBe('#socialmedia')
    })

    it('keeps # prefix if present', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.normalizeHashtag('#socialmedia')).toBe('#socialmedia')
    })

    it('converts to lowercase', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.normalizeHashtag('#SOCIALMEDIA')).toBe('#socialmedia')
    })

    it('removes invalid characters', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.normalizeHashtag('#hello-world!')).toBe('#helloworld')
    })

    it('keeps numbers and underscores', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.normalizeHashtag('#social_media_2026')).toBe('#social_media_2026')
    })

    it('returns empty string for invalid input', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.normalizeHashtag('!!!')).toBe('')
    })

    it('returns empty string for # alone', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.normalizeHashtag('#')).toBe('')
    })

    it('returns empty string for # with only invalid characters', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      expect(formatting.normalizeHashtag('#!!!')).toBe('')
    })
  })

  // ============================================================================
  // HASHTAGS - appendHashtag
  // ============================================================================

  describe('appendHashtag', () => {
    it('appends hashtag to empty text', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.appendHashtag('socialmedia')

      expect(result).toBe(true)
      expect(postText.value).toBe('#socialmedia')
    })

    it('appends hashtag to existing text with space', () => {
      const postText = createRef('Hello world')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.appendHashtag('socialmedia')

      expect(result).toBe(true)
      expect(postText.value).toBe('Hello world #socialmedia')
    })

    it('adds # if missing', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      formatting.appendHashtag('socialmedia')

      expect(postText.value).toBe('#socialmedia')
    })

    it('normalizes hashtag before appending', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      formatting.appendHashtag('#HELLO-WORLD!')

      expect(postText.value).toBe('#helloworld')
    })

    it('returns false for empty input', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.appendHashtag('')

      expect(result).toBe(false)
      expect(postText.value).toBe('')
    })

    it('returns false for whitespace-only input', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.appendHashtag('   ')

      expect(result).toBe(false)
    })

    it('returns false for input that becomes empty after normalization', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.appendHashtag('!!!')

      expect(result).toBe(false)
    })

    it('calls onHashtagInserted callback', () => {
      const postText = createRef('')
      let insertedHashtag = ''
      const formatting = useComposerTextFormatting({
        postText,
        onHashtagInserted: (tag) => {
          insertedHashtag = tag
        },
      })

      formatting.appendHashtag('socialmedia')

      expect(insertedHashtag).toBe('#socialmedia')
    })

    it('does not call callback for invalid input', () => {
      const postText = createRef('')
      let callbackCalled = false
      const formatting = useComposerTextFormatting({
        postText,
        onHashtagInserted: () => {
          callbackCalled = true
        },
      })

      formatting.appendHashtag('')

      expect(callbackCalled).toBe(false)
    })
  })

  // ============================================================================
  // HASHTAGS - appendHashtagFromPrompt
  // ============================================================================

  describe('appendHashtagFromPrompt', () => {
    it('calls appendHashtag with prompt result', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      // Mock prompt
      const originalPrompt = global.prompt
      global.prompt = vi.fn(() => 'socialmedia') as any

      const result = formatting.appendHashtagFromPrompt()

      expect(result).toBe(true)
      expect(postText.value).toBe('#socialmedia')

      global.prompt = originalPrompt
    })

    it('returns false when user cancels', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const originalPrompt = global.prompt
      global.prompt = vi.fn(() => null) as any

      const result = formatting.appendHashtagFromPrompt()

      expect(result).toBe(false)
      expect(postText.value).toBe('')

      global.prompt = originalPrompt
    })
  })

  // ============================================================================
  // EMOJIS - insertEmoji
  // ============================================================================

  describe('insertEmoji', () => {
    it('inserts emoji to empty text', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      formatting.insertEmoji('🚀')

      expect(postText.value).toBe('🚀')
    })

    it('inserts emoji with space if text exists', () => {
      const postText = createRef('Hello')
      const formatting = useComposerTextFormatting({ postText })

      formatting.insertEmoji('🚀')

      expect(postText.value).toBe('Hello 🚀')
    })

    it('does not add space if text ends with space', () => {
      const postText = createRef('Hello ')
      const formatting = useComposerTextFormatting({ postText })

      formatting.insertEmoji('🚀')

      expect(postText.value).toBe('Hello 🚀')
    })

    it('does nothing for empty emoji', () => {
      const postText = createRef('Hello')
      const formatting = useComposerTextFormatting({ postText })

      formatting.insertEmoji('')

      expect(postText.value).toBe('Hello')
    })

    it('calls onEmojiInserted callback', () => {
      const postText = createRef('')
      let insertedEmoji = ''
      const formatting = useComposerTextFormatting({
        postText,
        onEmojiInserted: (emoji) => {
          insertedEmoji = emoji
        },
      })

      formatting.insertEmoji('🚀')

      expect(insertedEmoji).toBe('🚀')
    })
  })

  describe('insertDefaultEmoji', () => {
    it('inserts 🙂 emoji', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      formatting.insertDefaultEmoji()

      expect(postText.value).toBe('🙂')
    })

    it('inserts 🙂 with space if text exists', () => {
      const postText = createRef('Hello')
      const formatting = useComposerTextFormatting({ postText })

      formatting.insertDefaultEmoji()

      expect(postText.value).toBe('Hello 🙂')
    })
  })

  // ============================================================================
  // AI ASSIST
  // ============================================================================

  describe('applyAiAssist', () => {
    it('generates default text when empty', async () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      await formatting.applyAiAssist()

      expect(postText.value).toContain('Profile Tailors')
      expect(postText.value).toContain('🚀')
    })

    it('appends signature to existing text', async () => {
      const postText = createRef('My custom post')
      const formatting = useComposerTextFormatting({ postText })

      await formatting.applyAiAssist()

      expect(postText.value).toContain('My custom post')
      expect(postText.value).toContain('@ProfileTailors')
      expect(postText.value).toContain('\n\n')
    })

    it('sets isAiProcessing during processing', async () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const promise = formatting.applyAiAssist()

      expect(formatting.isAiProcessing.value).toBe(true)

      await promise

      expect(formatting.isAiProcessing.value).toBe(false)
    })

    it('calls onAiAssistApplied callback', async () => {
      const postText = createRef('')
      let appliedText = ''
      const formatting = useComposerTextFormatting({
        postText,
        onAiAssistApplied: (text) => {
          appliedText = text
        },
      })

      await formatting.applyAiAssist()

      expect(appliedText).toBeTruthy()
      expect(appliedText).toContain('Profile Tailors')
    })

    it('does nothing if already processing', async () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      // Iniciar primera llamada
      const promise1 = formatting.applyAiAssist()

      // Intentar segunda llamada mientras está procesando
      const promise2 = formatting.applyAiAssist()

      await Promise.all([promise1, promise2])

      // Solo se aplicó una vez
      expect(postText.value).toContain('Profile Tailors')
      expect(postText.value.split('Profile Tailors').length).toBe(2) // Solo una ocurrencia
    })
  })

  // ============================================================================
  // NORMALIZATION - normalizeAllHashtags
  // ============================================================================

  describe('normalizeAllHashtags', () => {
    it('normalizes all hashtags to lowercase', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.normalizeAllHashtags('Hello #WORLD #Foo')

      expect(result).toBe('Hello #world #foo')
    })

    it('removes invalid characters from hashtags', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.normalizeAllHashtags('#hello-world! #test')

      expect(result).toBe('#helloworld #test')
    })

    it('leaves non-hashtag words unchanged', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.normalizeAllHashtags('Hello WORLD Foo')

      expect(result).toBe('Hello WORLD Foo')
    })

    it('normalizes multiple spaces', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.normalizeAllHashtags('Hello    #world')

      expect(result).toBe('Hello #world')
    })
  })

  // ============================================================================
  // NORMALIZATION - formatForBackend
  // ============================================================================

  describe('formatForBackend', () => {
    it('normalizes hashtags', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.formatForBackend('#HELLO #WORLD')

      expect(result).toBe('#hello #world')
    })

    it('trims whitespace', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.formatForBackend('  Hello world  ')

      expect(result).toBe('Hello world')
    })

    it('reduces multiple line breaks to max 2', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.formatForBackend('Hello\n\n\n\n\nWorld')

      expect(result).toBe('Hello\n\nWorld')
    })

    it('preserves single and double line breaks', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.formatForBackend('Hello\nWorld\n\nFoo\nBar')

      expect(result).toBe('Hello\nWorld\n\nFoo\nBar')
    })

    it('combines all normalizations', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.formatForBackend('  #HELLO\n\n\n\n  #WORLD  ')

      expect(result).toBe('#hello\n\n#world')
    })
  })

  // ============================================================================
  // INTEGRATION
  // ============================================================================

  describe('integration', () => {
    it('builds a complete post with hashtags and emojis', () => {
      const postText = createRef('')
      const formatting = useComposerTextFormatting({ postText })

      formatting.appendHashtag('socialmedia')
      formatting.insertEmoji('🚀')
      formatting.appendHashtag('launch')
      formatting.insertEmoji('🎉')

      expect(postText.value).toBe('#socialmedia 🚀 #launch 🎉')
    })

    it('formats the complete post for backend', () => {
      const postText = createRef('  #HELLO #WORLD 🚀  ')
      const formatting = useComposerTextFormatting({ postText })

      const result = formatting.formatForBackend(postText.value)

      expect(result).toBe('#hello #world 🚀')
    })
  })
})
