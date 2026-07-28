import { ref, type Ref } from 'vue'

export type UseComposerTextFormattingOptions = {
  /** Reactive post text ref. */
  postText: Ref<string>

  /** Optional callback when an emoji is inserted. */
  onEmojiInserted?: (emoji: string) => void

  /** Optional callback when a hashtag is inserted. */
  onHashtagInserted?: (hashtag: string) => void

  /** Optional callback when AI assist has been applied. */
  onAiAssistApplied?: (text: string) => void
}

export type UseComposerTextFormattingResult = {
  isAiProcessing: import('vue').Ref<boolean>
  normalizeHashtag: (tag: string) => string
  appendHashtag: (tag: string) => boolean
  appendHashtagFromPrompt: () => boolean
  insertEmoji: (emoji: string) => void
  insertDefaultEmoji: () => void
  applyAiAssist: () => Promise<void>
  normalizeAllHashtags: (text: string) => string
  formatForBackend: (text: string) => string
}

/**
 * Provides composer text formatting utilities for hashtags, emojis, AI assistance, and backend preparation.
 *
 * @param options - Composer text state and optional callbacks for formatting events
 * @returns Formatting utilities and the current AI processing state
 *
 * @example
 * ```ts
 * const formatting = useComposerTextFormatting({ postText })
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
  /**
   * Normalizes a hashtag to lowercase and removes unsupported characters.
   *
   * @param tag - The hashtag to normalize, with or without a leading `#`
   * @returns The normalized hashtag prefixed with `#`, or an empty string when no valid characters remain
   */

  function normalizeHashtag(tag: string): string {
    const body = tag.startsWith('#') ? tag.slice(1) : tag
    const cleaned = body.toLowerCase().replace(/[^a-z0-9_]/g, '')
    return cleaned ? `#${cleaned}` : ''
  }

  /**
   * Appends a normalized hashtag to the post text.
   *
   * @param tag - The hashtag to normalize and append.
   * @returns `true` if the hashtag was appended, `false` if it was empty or invalid.
   */
  function appendHashtag(tag: string): boolean {
    if (!tag || tag.trim() === '') return false

    const normalized = normalizeHashtag(tag.trim())
    if (!normalized) return false

    const separator = options.postText.value.length > 0 ? ' ' : ''
    options.postText.value = `${options.postText.value}${separator}${normalized}`

    // Callback
    options.onHashtagInserted?.(normalized)

    return true
  }

  /**
   * Prompts the user for a hashtag and appends the valid normalized value to the post.
   *
   * @returns `true` if a hashtag was appended, `false` if no value was provided or the hashtag was invalid.
   */
  function appendHashtagFromPrompt(): boolean {
    const tag = prompt('Enter tag (e.g. #socialmedia):')
    if (!tag) return false
    return appendHashtag(tag)
  }

  // ============================================================================
  // EMOJIS
  // ============================================================================

  /** Inserts an emoji at the end of the post text, adding a space separator when needed. */
  function insertEmoji(emoji: string): void {
    if (!emoji) return

    const separator =
      options.postText.value.length > 0 && !options.postText.value.endsWith(' ') ? ' ' : ''
    options.postText.value = `${options.postText.value}${separator}${emoji}`

    // Callback
    options.onEmojiInserted?.(emoji)
  }

  /**
   * Inserts the default smiling face emoji into the post text.
   */
  function insertDefaultEmoji(): void {
    insertEmoji('🙂')
  }

  // ============================================================================
  // AI ASSIST
  // ============================================================================

  /**
   * Applies AI-assisted content to the post text.
   *
   * Generates sample content when the post is empty; otherwise appends the
   * Profile Tailors signature. Concurrent requests are ignored while processing.
   */
  async function applyAiAssist(): Promise<void> {
    if (isAiProcessing.value) return

    isAiProcessing.value = true

    try {
      // Simulate API delay
      await new Promise((resolve) => setTimeout(resolve, 800))

      if (!options.postText.value.trim()) {
        // Empty text: generate a sample post
        const generated =
          'Profile Tailors is officially launching! Minimalist scheduling, analytics, and multichannel delivery designed for creators. 🚀'
        options.postText.value = generated
        options.onAiAssistApplied?.(generated)
      } else {
        // Existing text: append brand signature
        const modified = `${options.postText.value}\n\nProgramado vía @ProfileTailors`
        options.postText.value = modified
        options.onAiAssistApplied?.(modified)
      }
    } finally {
      isAiProcessing.value = false
    }
  }

  // ============================================================================
  // TEXT NORMALISATION
  // ============================================================================

  /**
   * Normalizes hashtags and whitespace while preserving line breaks.
   *
   * @param text - The text whose hashtags and whitespace should be normalized.
   * @returns The normalized text with lowercase hashtags, standardized spacing, and preserved line breaks.
   *
   * @example
   * ```ts
   * normalizeAllHashtags('Hello   #Post!')
   * // 'Hello #post'
   * ```
   */
  function normalizeAllHashtags(text: string): string {
    return text
      .replace(/[ \t]+/g, ' ') // Normalise multiple spaces and tabs
      .split(/(\n+)/) // Split at line breaks, preserving them
      .map((part) => {
        if (/^\n+$/.test(part)) return part // Preserve line breaks
        // Normalise hashtags in each line
        return part
          .split(/\s+/)
          .map((word) => {
            if (word.startsWith('#')) {
              return word.toLowerCase().replace(/[^a-z0-9#_]/g, '')
            }
            return word
          })
          .join(' ')
      })
      .join('')
      .replace(/(\n)[ \t]+/g, '$1') // Strip spaces after line breaks
  }

  /**
   * Formats text for backend submission by normalizing hashtags, trimming surrounding whitespace, and limiting consecutive blank lines.
   *
   * @param text - The text to format.
   * @returns The normalized and trimmed text with no more than two consecutive line breaks.
   */
  function formatForBackend(text: string): string {
    return normalizeAllHashtags(text)
      .trim()
      .replace(/\n{3,}/g, '\n\n') // Max 2 consecutive line breaks
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
