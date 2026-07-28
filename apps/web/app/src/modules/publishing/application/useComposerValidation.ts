import { computed, type Ref } from 'vue'

/**
 * Options para el composable de validación
 */
export interface UseComposerValidationOptions {
  /**
   * Texto del post
   */
  postText: Ref<string>

  /**
   * Canal seleccionado (undefined si no hay ninguno)
   */
  selectedChannel: Ref<
    | {
        id: string
        provider: string
        name: string
        status: string
        attachmentLimit?: number
      }
    | undefined
  >

  /**
   * Número actual de attachments
   */
  attachmentCount: Ref<number>

  /**
   * Si el schedule es válido
   */
  isScheduleValid: Ref<boolean>

  /**
   * Si está en modo edición (no requiere canal obligatorio)
   */
  isEditMode: Ref<boolean>

  /**
   * Si está actualmente enviando/submitiendo
   */
  isSubmitting: Ref<boolean>
}

/**
 * Resultados de validación del composer
 */
export interface ComposerValidationResult {
  /**
   * Límite de caracteres (3000 para LinkedIn)
   */
  charLimit: number

  /**
   * Caracteres restantes
   */
  charsRemaining: Ref<number>

  /**
   * Si el texto excede el límite
   */
  isTextTooLong: Ref<boolean>

  /**
   * Si hay texto (no vacío)
   */
  hasText: Ref<boolean>

  /**
   * Límite de attachments efectivo según el canal
   */
  effectiveAttachmentLimit: Ref<number>

  /**
   * Si la cantidad de attachments es válida
   */
  isAttachmentCountValid: Ref<boolean>

  /**
   * Número de attachments actuales
   */
  attachmentCount: Ref<number>

  /**
   * Si puede hacer submit (todas las validaciones pasan)
   */
  canSubmit: Ref<boolean>

  /**
   * Errores de validación como array de strings
   */
  validationErrors: Ref<string[]>

  /**
   * Número de warnings (para UI)
   */
  warningCount: Ref<number>
}

/**
 * Composable que maneja toda la validación del composer:
 * - Límite de caracteres
 * - Campos requeridos
 * - Límite de attachments por canal
 * - Validación completa para submit
 *
 * @example
 * ```ts
 * const validation = useComposerValidation({
 *   postText,
 *   selectedChannel: computed(() => channels.find(...)),
 *   attachmentCount: computed(() => attachments.length),
 *   isScheduleValid: scheduling.isScheduleValid,
 *   isEditMode: computed(() => !!editingPublication),
 *   isSubmitting: ref(false),
 * })
 *
 * // En template
 * :disabled="!validation.canSubmit.value"
 * {{ validation.charsRemaining.value }} / {{ validation.charLimit }}
 * ```
 */
export function useComposerValidation(
  options: UseComposerValidationOptions,
): ComposerValidationResult {
  // ============================================================================
  // CONSTANTS
  // ============================================================================

  const CHAR_LIMIT = 3000
  const WARNING_THRESHOLD = 0.1 // 10% del límite = warning

  // Límites por proveedor (platform)
  const PLATFORM_ATTACHMENT_LIMITS: Record<string, number> = {
    linkedin: 9,
    twitter: 4,
    facebook: 10,
    instagram: 10,
  }

  // ============================================================================
  // COMPUTED - Límite de caracteres
  // ============================================================================

  const charsRemaining = computed(() => CHAR_LIMIT - options.postText.value.length)

  const isTextTooLong = computed(() => charsRemaining.value < 0)

  const hasText = computed(() => options.postText.value.trim().length > 0)

  const isNearLimit = computed(() => charsRemaining.value < CHAR_LIMIT * WARNING_THRESHOLD)

  // ============================================================================
  // COMPUTED - Límite de attachments
  // ============================================================================

  const effectiveAttachmentLimit = computed<number>(() => {
    // Si el canal tiene un límite específico, usarlo
    const channelLimit = options.selectedChannel?.value?.attachmentLimit
    if (channelLimit !== undefined) {
      return channelLimit
    }

    // Si no, usar el límite del proveedor
    const provider = options.selectedChannel?.value?.provider
    if (provider && provider in PLATFORM_ATTACHMENT_LIMITS) {
      return PLATFORM_ATTACHMENT_LIMITS[provider] ?? Number.POSITIVE_INFINITY
    }

    // Default: infinito (sin límite)
    return Number.POSITIVE_INFINITY
  })

  const isAttachmentCountValid = computed(
    () => options.attachmentCount.value <= effectiveAttachmentLimit.value,
  )

  const attachmentCount = computed(() => options.attachmentCount.value)

  // ============================================================================
  // COMPUTED - Validación completa
  // ============================================================================

  const canSubmit = computed(() => {
    // No puede submit si ya está submitiendo
    if (options.isSubmitting.value) return false

    // En modo edición: necesita texto válido y attachments válidos
    if (options.isEditMode.value) {
      return hasText.value && !isTextTooLong.value && isAttachmentCountValid.value
    }

    // En modo creación: necesita todo
    return (
      !!options.selectedChannel?.value &&
      hasText.value &&
      !isTextTooLong.value &&
      options.isScheduleValid.value &&
      isAttachmentCountValid.value
    )
  })

  // ============================================================================
  // COMPUTED - Errores
  // ============================================================================

  const validationErrors = computed(() => {
    const errors: string[] = []

    // Errores bloqueantes
    if (options.isEditMode.value) {
      // Modo edición no requiere canal
    } else if (!options.selectedChannel?.value) {
      errors.push('Channel is required')
    }

    if (!hasText.value) {
      errors.push('Post text is required')
    }

    if (isTextTooLong.value) {
      errors.push(`Text exceeds ${CHAR_LIMIT} character limit`)
    }

    if (options.isEditMode.value) {
      // Modo edición no requiere schedule válido
    } else if (!options.isScheduleValid.value) {
      errors.push('Schedule is invalid')
    }

    if (!isAttachmentCountValid.value) {
      const limit = Number.isFinite(effectiveAttachmentLimit.value)
        ? `${options.attachmentCount.value}/${effectiveAttachmentLimit.value}`
        : `${options.attachmentCount.value} (no limit)`
      errors.push(`Too many attachments (${limit})`)
    }

    return errors
  })

  // ============================================================================
  // COMPUTED - Warnings (no bloqueantes)
  // ============================================================================

  const warningCount = computed(() => {
    let warnings = 0

    // Warning: cerca del límite de caracteres
    if (isNearLimit.value && !isTextTooLong.value) {
      warnings++
    }

    // Warning: attachments cerca del límite
    const attachmentRatio =
      effectiveAttachmentLimit.value === Number.POSITIVE_INFINITY
        ? 0
        : options.attachmentCount.value / effectiveAttachmentLimit.value

    if (attachmentRatio >= 0.7 && attachmentRatio < 1) {
      warnings++
    }

    return warnings
  })

  // ============================================================================
  // RETURN
  // ============================================================================

  return {
    charLimit: CHAR_LIMIT,
    charsRemaining,
    isTextTooLong,
    hasText,
    effectiveAttachmentLimit,
    isAttachmentCountValid,
    attachmentCount,
    canSubmit,
    validationErrors,
    warningCount,
  }
}

/**
 * Helper para formatear el contador de caracteres
 */
export function formatCharCount(remaining: number, limit: number): string {
  if (remaining < 0) {
    return `${Math.abs(remaining)} over limit`
  }
  return `${remaining} / ${limit}`
}

/**
 * Helper para obtener el estado del contador (normal, warning, error)
 */
export type CharCountState = 'normal' | 'warning' | 'error'

export function getCharCountState(remaining: number, limit: number): CharCountState {
  if (remaining < 0) return 'error'
  if (remaining < limit * 0.1) return 'warning'
  return 'normal'
}
