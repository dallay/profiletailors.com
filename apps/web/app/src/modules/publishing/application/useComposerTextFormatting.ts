import { ref, type Ref } from 'vue'

/**
 * Options para el composable de text formatting
 */
export interface UseComposerTextFormattingOptions {
  /**
   * Texto del post (ref reactivo)
   */
  postText: Ref<string>

  /**
   * Callback opcional cuando se inserta un emoji
   */
  onEmojiInserted?: (emoji: string) => void

  /**
   * Callback opcional cuando se inserta un hashtag
   */
  onHashtagInserted?: (hashtag: string) => void

  /**
   * Callback opcional cuando se aplica AI assist
   */
  onAiAssistApplied?: (text: string) => void
}

/**
 * Composable que maneja el formateo de texto en el composer:
 * - Inserción de hashtags
 * - Inserción de emojis
 * - AI assist (placeholder para integración futura)
 * - Normalización de texto antes de enviar al backend
 *
 * @example
 * ```ts
 * const formatting = useComposerTextFormatting({
 *   postText,
 *   onAiAssistApplied: (text) => console.log('AI generated:', text),
 * })
 *
 * // Usar
 * formatting.appendHashtag('socialmedia')
 * formatting.insertEmoji('🚀')
 * await formatting.applyAiAssist()
 * ```
 */
export function useComposerTextFormatting(options: UseComposerTextFormattingOptions) {
  // ============================================================================
  // STATE
  // ============================================================================

  /**
   * Estado de procesamiento de AI (para UI loading states)
   */
  const isAiProcessing = ref(false)

  // ============================================================================
  // HASHTAGS
  // ============================================================================

  /**
   * Normaliza un hashtag: añade # si no lo tiene, limpia caracteres inválidos
   */
  function normalizeHashtag(tag: string): string {
    // Si ya tiene #, verificar que sea válido
    if (tag.startsWith('#')) {
      return tag.toLowerCase().replace(/[^a-z0-9#_]/g, '')
    }

    // Añadir # y limpiar
    const cleaned = tag.toLowerCase().replace(/[^a-z0-9_]/g, '')
    return cleaned ? `#${cleaned}` : ''
  }

  /**
   * Añade un hashtag al texto del post
   * - Si el hashtag no empieza con #, lo añade
   * - Si el texto ya tiene contenido, añade un espacio antes
   * - Si el hashtag es vacío después de normalizar, no hace nada
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
   * Pide al usuario un hashtag (usando prompt)
   * @returns true si se añadió, false si el usuario canceló o el input era inválido
   */
  function appendHashtagFromPrompt(): boolean {
    const tag = prompt('Enter tag (e.g. #socialmedia):')
    if (!tag) return false
    return appendHashtag(tag)
  }

  // ============================================================================
  // EMOJIS
  // ============================================================================

  /**
   * Inserta un emoji al final del texto
   * - Si el texto ya tiene contenido y no termina con espacio, añade espacio
   */
  function insertEmoji(emoji: string): void {
    if (!emoji) return

    const separator =
      options.postText.value.length > 0 && !options.postText.value.endsWith(' ') ? ' ' : ''
    options.postText.value = `${options.postText.value}${separator}${emoji}`

    // Callback
    options.onEmojiInserted?.(emoji)
  }

  /**
   * Inserta un emoji por defecto (🙂)
   */
  function insertDefaultEmoji(): void {
    insertEmoji('🙂')
  }

  // ============================================================================
  // AI ASSIST (PLACEHOLDER)
  // ============================================================================

  /**
   * Aplica AI Assist al texto
   * NOTA: Esta es una implementación placeholder.
   * En producción, esto debería llamar a un endpoint de AI.
   *
   * Comportamiento actual:
   * - Si el texto está vacío, genera un post de ejemplo
   * - Si hay texto, añade una firma con el brand
   */
  async function applyAiAssist(): Promise<void> {
    if (isAiProcessing.value) return

    isAiProcessing.value = true

    try {
      // Simular delay de API
      await new Promise((resolve) => setTimeout(resolve, 800))

      if (!options.postText.value.trim()) {
        // Texto vacío: generar post de ejemplo
        const generated =
          'Profile Tailors is officially launching! Minimalist scheduling, analytics, and multichannel delivery designed for creators. 🚀'
        options.postText.value = generated
        options.onAiAssistApplied?.(generated)
      } else {
        // Texto existente: añadir firma
        const modified = `${options.postText.value}\n\nProgramado vía @ProfileTailors`
        options.postText.value = modified
        options.onAiAssistApplied?.(modified)
      }
    } finally {
      isAiProcessing.value = false
    }
  }

  // ============================================================================
  // TEXT NORMALIZATION (para envío al backend)
  // ============================================================================

  /**
   * Normaliza hashtags en todo el texto para envío al backend
   * - Convierte todos los hashtags a lowercase
   * - Elimina caracteres especiales
   * - Normaliza espacios múltiples (pero preserva saltos de línea)
   */
  function normalizeAllHashtags(text: string): string {
    return text
      .replace(/[ \t]+/g, ' ') // Normaliza espacios y tabs múltiples
      .split(/(\n+)/) // Divide por saltos de línea preservándolos
      .map((part) => {
        // Si es un salto de línea, lo preserva
        if (/^\n+$/.test(part)) return part
        // Si no, normaliza hashtags en cada línea
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
      .replace(/(\n)[ \t]+/g, '$1') // Elimina espacios después de saltos de línea
  }

  /**
   * Formatea el texto completo para enviar al backend
   * - Normaliza hashtags
   * - Trim de espacios al inicio y final
   * - Normaliza saltos de línea múltiples
   */
  function formatForBackend(text: string): string {
    return normalizeAllHashtags(text)
      .trim()
      .replace(/\n{3,}/g, '\n\n') // Máximo 2 saltos de línea consecutivos
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
