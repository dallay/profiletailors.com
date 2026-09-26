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

export { getCalendarRange, type CalendarRange, type CalendarSurface } from './calendarRange'
export { useReactiveClock, type ReactiveClock, type ReactiveClockOptions } from './useReactiveClock'
export {
  createCalendarInvalidationChannel,
  type CalendarInvalidation,
  type CalendarInvalidationReason,
} from './calendar-invalidation-channel'
export {
  useCalendarRevalidation,
  type CalendarRevalidation,
  type CalendarRevalidationOptions,
} from './useCalendarRevalidation'
export {
  usePublicationEventReconnect,
  type PublicationEventReconnect,
  type PublicationEventReconnectOptions,
} from './usePublicationEventReconnect'

export { useCalendarUrl } from './useCalendarUrl'

// ============================================================================
// QUEUED COUNTS
// ============================================================================

export { useQueuedCounts } from './useQueuedCounts'
