import { computed, type Ref } from 'vue'

/** Default character limit across providers */
const CHAR_LIMIT = 3000

/** Warning threshold: 10% of the character limit triggers a warning */
const WARNING_THRESHOLD = 0.1

/** Per-provider attachment limits */
const PLATFORM_ATTACHMENT_LIMITS: Record<string, number> = {
  linkedin: 9,
  twitter: 4,
  facebook: 10,
  instagram: 10,
}

/**
 * Options for the composer validation composable.
 */
export type UseComposerValidationOptions = {
  /** Reactive post text ref. */
  postText: Ref<string>

  /** Selected channel ref (undefined when none selected). */
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

  /** Current attachment count. */
  attachmentCount: Ref<number>

  /** Whether the schedule configuration is valid. */
  isScheduleValid: Ref<boolean>

  /** Whether the composer is in edit mode (channel not required). */
  isEditMode: Ref<boolean>

  /** Whether the form is currently being submitted. */
  isSubmitting: Ref<boolean>
}

/**
 * Composer validation result.
 */
export type ComposerValidationResult = {
  /** Character limit for the active provider. */
  charLimit: number

  /** Remaining characters. */
  charsRemaining: import('vue').ComputedRef<number>

  /** Whether the text exceeds the character limit. */
  isTextTooLong: import('vue').ComputedRef<boolean>

  /** Whether there is non-whitespace text. */
  hasText: import('vue').ComputedRef<boolean>

  /** Effective attachment limit based on the selected channel. */
  effectiveAttachmentLimit: import('vue').ComputedRef<number>

  /** Whether the current attachment count is within the limit. */
  isAttachmentCountValid: import('vue').ComputedRef<boolean>

  /** Current attachment count. */
  attachmentCount: import('vue').ComputedRef<number>

  /** Whether all validations pass and the form can be submitted. */
  canSubmit: import('vue').ComputedRef<boolean>

  /** Validation error messages. */
  validationErrors: import('vue').ComputedRef<string[]>

  /** Number of active warnings (non-blocking). */
  warningCount: import('vue').ComputedRef<number>
}

/**
 * Creates reactive validation state for composer content, scheduling, channels, and attachments.
 *
 * @param options - Reactive composer inputs used to calculate validation results.
 * @returns Computed validation results and the character limit.
 *
 * @example
 * ```ts
 * const validation = useComposerValidation({
 *   postText,
 *   selectedChannel,
 *   attachmentCount,
 *   isScheduleValid,
 *   isEditMode,
 *   isSubmitting,
 * })
 * ```
 */
export function useComposerValidation(
  options: UseComposerValidationOptions,
): ComposerValidationResult {
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
      errors.push(
        `Too many attachments (${options.attachmentCount.value}/${effectiveAttachmentLimit.value})`,
      )
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
 * Formats the character count relative to its limit.
 *
 * @param remaining - The number of characters remaining.
 * @param limit - The maximum character limit.
 * @returns A formatted character count string.
 */
export function formatCharCount(remaining: number, limit: number): string {
  if (remaining < 0) {
    return `${Math.abs(remaining)} over limit`
  }
  return `${remaining} / ${limit}`
}

/**
 * Returns the char-count display state: normal, warning, or error.
 */
export type CharCountState = 'normal' | 'warning' | 'error'

export function getCharCountState(remaining: number, limit: number): CharCountState {
  if (remaining < 0) return 'error'
  if (remaining < limit * 0.1) return 'warning'
  return 'normal'
}
