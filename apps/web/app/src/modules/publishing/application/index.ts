/**
 * Application layer barrel
 *
 * Exposes only the public application layer API.
 * Internal helpers are NOT exported.
 *
 * @example
 * ```ts
 * import { useComposerScheduling } from '@modules/publishing/application'
 * ```
 */

// ============================================================================
// SCHEDULING
// ============================================================================

export {
  useComposerScheduling,
  type ComposerScheduleMode,
  type UseComposerSchedulingOptions,
} from './useComposerScheduling'

// ============================================================================
// VALIDATION
// ============================================================================

export {
  useComposerValidation,
  formatCharCount,
  getCharCountState,
  type UseComposerValidationOptions,
  type ComposerValidationResult,
  type CharCountState,
} from './useComposerValidation'

// ============================================================================
// TEXT FORMATTING
// ============================================================================

export {
  useComposerTextFormatting,
  type UseComposerTextFormattingOptions,
} from './useComposerTextFormatting'

// ============================================================================
// MEDIA PICKER
// ============================================================================

export { useComposerMediaPicker } from './useComposerMediaPicker'

// ============================================================================
// CALENDAR URL
// ============================================================================

export { useCalendarUrl } from './useCalendarUrl'

// ============================================================================
// QUEUED COUNTS
// ============================================================================

export { useQueuedCounts } from './useQueuedCounts'
