import { ref, type Ref } from 'vue'

export type UseComposerTextFormattingResult = {
  isAiProcessing: Ref<boolean>
  normalizeHashtag: (tag: string) => string
  appendHashtag: (tag: string) => boolean
  appendHashtagFromPrompt: () => boolean
  insertEmoji: (emoji: string) => void
  insertDefaultEmoji: () => void
  applyAiAssist: () => Promise<void>
  normalizeAllHashtags: (text: string) => string
  formatForBackend: (text: string) => string
}

export type UseComposerTextFormattingOptions = {
  postText: Ref<string>
  onEmojiInserted?: (emoji: string) => void
  onHashtagInserted?: (hashtag: string) => void
  onAiAssistApplied?: (text: string) => void
}

/**
 * Composable that handles text formatting in the composer:
 * - Hashtag insertion
 * - Emoji insertion
 * - AI assist (placeholder for future integration)
 * - Text normalization before backend submission
 *
 * @example
 * ```ts
 * const formatting = useComposerTextFormatting({
 *   postText,
 *   onAiAssistApplied: (text) => console.log('AI generated:', text),
 * })
 *
 * formatting.appendHashtag('socialmedia')
 * formatting.insertEmoji('🚀')
 * await formatting.applyAiAssist()
 * ```
 */
export function useComposerTextFormatting(
  options: UseComposerTextFormattingOptions,
): UseComposerTextFormattingResult {
  // ============================================================================
  // STATE
  // ============================================================================

  const isAiProcessing = ref(false)

  // ============================================================================
  // HASHTAGS
  // ============================================================================

  function normalizeHashtag(tag: string): string {
    if (tag.startsWith('#')) {
      const body = tag
        .slice(1)
        .toLowerCase()
        .replace(/[^a-z0-9_]/g, '')
      return body ? `#${body}` : ''
    }

    const cleaned = tag.toLowerCase().replace(/[^a-z0-9_]/g, '')
    return cleaned ? `#${cleaned}` : ''
  }

  function appendHashtag(tag: string): boolean {
    if (!tag || tag.trim() === '') return false

    const normalized = normalizeHashtag(tag.trim())
    if (!normalized) return false

    const separator = options.postText.value.length > 0 ? ' ' : ''
    options.postText.value = `${options.postText.value}${separator}${normalized}`

    options.onHashtagInserted?.(normalized)

    return true
  }

  function appendHashtagFromPrompt(): boolean {
    const tag = prompt('Enter tag (e.g. #socialmedia):')
    if (!tag) return false
    return appendHashtag(tag)
  }

  // ============================================================================
  // EMOJIS
  // ============================================================================

  function insertEmoji(emoji: string): void {
    if (!emoji) return

    const separator =
      options.postText.value.length > 0 && !options.postText.value.endsWith(' ') ? ' ' : ''
    options.postText.value = `${options.postText.value}${separator}${emoji}`

    options.onEmojiInserted?.(emoji)
  }

  function insertDefaultEmoji(): void {
    insertEmoji('🙂')
  }

  // ============================================================================
  // AI ASSIST (PLACEHOLDER)
  // ============================================================================

  async function applyAiAssist(): Promise<void> {
    if (isAiProcessing.value) return

    isAiProcessing.value = true

    try {
      await new Promise((resolve) => setTimeout(resolve, 800))

      if (!options.postText.value.trim()) {
        const generated =
          'Profile Tailors is officially launching! Minimalist scheduling, analytics, and multichannel delivery designed for creators. 🚀'
        options.postText.value = generated
        options.onAiAssistApplied?.(generated)
      } else {
        const modified = `${options.postText.value}\n\nProgramado vía @ProfileTailors`
        options.postText.value = modified
        options.onAiAssistApplied?.(modified)
      }
    } finally {
      isAiProcessing.value = false
    }
  }

  // ============================================================================
  // TEXT NORMALIZATION
  // ============================================================================

  function normalizeAllHashtags(text: string): string {
    return text
      .replace(/[ \t]+/g, ' ')
      .split(/(\n+)/)
      .map((part) => {
        if (/^\n+$/.test(part)) return part
        return part
          .split(/\s+/)
          .map((word) => {
            if (word.startsWith('#')) {
              return normalizeHashtag(word)
            }
            return word
          })
          .join(' ')
      })
      .join('')
      .replace(/(\n)[ \t]+/g, '$1')
  }

  function formatForBackend(text: string): string {
    return normalizeAllHashtags(text)
      .trim()
      .replace(/\n{3,}/g, '\n\n')
  }

  // ============================================================================
  // RETURN
  // ============================================================================

  return {
    // State
    isAiProcessing,

    // Hashtags
    normalizeHashtag,
    appendHashtag,
    appendHashtagFromPrompt,

    // Emojis
    insertEmoji,
    insertDefaultEmoji,

    // AI Assist
    applyAiAssist,

    // Normalization
    normalizeAllHashtags,
    formatForBackend,
  }
}
