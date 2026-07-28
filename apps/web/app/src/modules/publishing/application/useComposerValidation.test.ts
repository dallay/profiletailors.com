import { describe, it, expect } from 'vitest'
import { ref, isRef, type Ref } from 'vue'
import { useComposerValidation, formatCharCount, getCharCountState } from './useComposerValidation'

// Helper para crear refs de forma más fácil
function createRef<T>(value: T): Ref<T> {
  return ref(value) as Ref<T>
}

describe('useComposerValidation', () => {
  // ============================================================================
  // CHARACTER LIMITS
  // ============================================================================

  describe('character limits', () => {
    it('defaults to 3000 character limit', () => {
      const options = createMockOptions({
        postText: '',
        selectedChannel: undefined,
        attachmentCount: 0,
        isScheduleValid: true,
        isEditMode: false,
        isSubmitting: false,
      })

      const validation = useComposerValidation(options)

      expect(validation.charLimit).toBe(3000)
    })

    it('calculates charsRemaining correctly', () => {
      const postText = createRef('Hello World')
      const options = createMockOptions({
        postText,
        selectedChannel: undefined,
        attachmentCount: 0,
        isScheduleValid: true,
        isEditMode: false,
        isSubmitting: false,
      })

      const validation = useComposerValidation(options)

      expect(validation.charsRemaining.value).toBe(3000 - 11) // 11 chars in "Hello World"
    })

    it('detects when text is too long', () => {
      const postText = createRef('a'.repeat(3001))
      const options = createMockOptions({
        postText,
        selectedChannel: undefined,
        attachmentCount: 0,
        isScheduleValid: true,
        isEditMode: false,
        isSubmitting: false,
      })

      const validation = useComposerValidation(options)

      expect(validation.isTextTooLong.value).toBe(true)
      expect(validation.charsRemaining.value).toBe(-1)
    })

    it('detects when text is near limit', () => {
      // 2701 chars = 299 remaining, which is below the 10% threshold
      const postText = createRef('a'.repeat(2701))
      const options = createMockOptions({
        postText,
        selectedChannel: undefined,
        attachmentCount: 0,
        isScheduleValid: true,
        isEditMode: false,
        isSubmitting: false,
      })

      const validation = useComposerValidation(options)

      expect(validation.charsRemaining.value).toBe(299)
      expect(validation.isTextTooLong.value).toBe(false)
      expect(getCharCountState(validation.charsRemaining.value, validation.charLimit)).toBe(
        'warning',
      )
    })

    it('handles exactly-at-limit boundaries', () => {
      // charsRemaining === 0 is warning, not error
      expect(getCharCountState(0, 3000)).toBe('warning')

      // LinkedIn attachmentCount === 9 is within the limit (9 <= 9)
      const selectedChannel = createRef<ChannelShape>({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
        attachmentCount: 9,
      })
      const validation = useComposerValidation(options)
      expect(validation.isAttachmentCountValid.value).toBe(true)
    })

    it('hasText is false for empty text', () => {
      const postText = createRef('')
      const options = createMockOptions({
        postText,
        selectedChannel: undefined,
        attachmentCount: 0,
        isScheduleValid: true,
        isEditMode: false,
        isSubmitting: false,
      })

      const validation = useComposerValidation(options)

      expect(validation.hasText.value).toBe(false)
    })

    it('hasText is false for whitespace-only text', () => {
      const postText = createRef('   \n\t  ')
      const options = createMockOptions({
        postText,
        selectedChannel: undefined,
        attachmentCount: 0,
        isScheduleValid: true,
        isEditMode: false,
        isSubmitting: false,
      })

      const validation = useComposerValidation(options)

      expect(validation.hasText.value).toBe(false)
    })

    it('hasText is true for text with content', () => {
      const postText = createRef('Hello')
      const options = createMockOptions({
        postText,
        selectedChannel: undefined,
        attachmentCount: 0,
        isScheduleValid: true,
        isEditMode: false,
        isSubmitting: false,
      })

      const validation = useComposerValidation(options)

      expect(validation.hasText.value).toBe(true)
    })
  })

  // ============================================================================
  // ATTACHMENT LIMITS
  // ============================================================================

  describe('attachment limits', () => {
    it('returns 9 for LinkedIn', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.effectiveAttachmentLimit.value).toBe(9)
    })

    it('returns 4 for Twitter', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'twitter',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.effectiveAttachmentLimit.value).toBe(4)
    })

    it('returns 10 for Facebook', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'facebook',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.effectiveAttachmentLimit.value).toBe(10)
    })

    it('returns Infinity for unknown providers', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'unknown',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.effectiveAttachmentLimit.value).toBe(Number.POSITIVE_INFINITY)
    })

    it('uses channel-specific attachmentLimit if provided', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
        attachmentLimit: 5, // Custom limit
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.effectiveAttachmentLimit.value).toBe(5) // Custom limit wins
    })

    it('validates attachment count is within limit', () => {
      const attachmentCount = createRef(8)
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
        attachmentCount,
      })

      const validation = useComposerValidation(options)

      expect(validation.isAttachmentCountValid.value).toBe(true) // 8 <= 9

      attachmentCount.value = 10
      expect(validation.isAttachmentCountValid.value).toBe(false) // 10 > 9
    })
  })

  // ============================================================================
  // CAN SUBMIT
  // ============================================================================

  describe('canSubmit', () => {
    it('blocks submit when no channel in create mode', () => {
      const options = createMockOptions({
        postText: createRef('Hello'),
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(false)
      expect(validation.validationErrors.value).toContain('Channel is required')
    })

    it('allows submit when channel is selected in create mode', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(true)
    })

    it('allows submit without channel in edit mode', () => {
      const options = createMockOptions({
        postText: createRef('Hello'),
        isEditMode: createRef(true),
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(true)
    })

    it('blocks submit when text is empty', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef(''),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(false)
      expect(validation.validationErrors.value).toContain('Post text is required')
    })

    it('blocks submit when text is too long', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('a'.repeat(3001)),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(false)
      expect(validation.validationErrors.value.some((e) => e.includes('exceeds'))).toBe(true)
    })

    it('blocks submit when schedule is invalid in create mode', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
        isScheduleValid: createRef(false),
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(false)
      expect(validation.validationErrors.value).toContain('Schedule is invalid')
    })

    it('allows submit without schedule validation in edit mode', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
        isScheduleValid: createRef(false), // Invalid, but allowed in edit mode
        isEditMode: createRef(true),
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(true)
    })

    it('blocks submit when submitting', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
        isSubmitting: createRef(true),
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(false)
    })

    it('blocks submit when attachment count exceeds limit', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
        attachmentCount: createRef(10), // LinkedIn limit is 9
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(false)
      expect(
        validation.validationErrors.value.some((e) => e.includes('Too many attachments')),
      ).toBe(true)
    })
  })

  // ============================================================================
  // VALIDATION ERRORS
  // ============================================================================

  describe('validation errors', () => {
    it('returns empty array when all valid', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.validationErrors.value).toEqual([])
    })

    it('returns multiple errors when multiple validations fail', () => {
      const options = createMockOptions({
        postText: createRef(''), // Empty
        // No channel
      })

      const validation = useComposerValidation(options)

      expect(validation.validationErrors.value).toContain('Channel is required')
      expect(validation.validationErrors.value).toContain('Post text is required')
    })
  })

  // ============================================================================
  // WARNING COUNT
  // ============================================================================

  describe('warning count', () => {
    it('returns 0 when no warnings', () => {
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.warningCount.value).toBe(0)
    })

    it('returns 1 when near char limit', () => {
      // 2750 chars = 250 remaining, which is 8.3% (under 10% = warning)
      const postText = createRef('a'.repeat(2750))
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText,
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.warningCount.value).toBe(1)
    })

    it('returns 1 when near attachment limit', () => {
      // 7 attachments = 77% of 9 (near 70% threshold)
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
        attachmentCount: createRef(7), // 7/9 = 77%
      })

      const validation = useComposerValidation(options)

      expect(validation.warningCount.value).toBe(1)
    })

    it('returns 2 when both warnings present', () => {
      // 2750 chars = warning
      const postText = createRef('a'.repeat(2750))
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText,
        selectedChannel,
        attachmentCount: createRef(7), // 77% = warning
      })

      const validation = useComposerValidation(options)

      expect(validation.warningCount.value).toBe(2)
    })
  })

  // ============================================================================
  // REACTIVITY
  // ============================================================================

  describe('reactivity', () => {
    it('updates canSubmit when channel changes', () => {
      const selectedChannel = createRef<ChannelShape | undefined>(undefined)
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(false)

      selectedChannel.value = { id: 'ch1', provider: 'linkedin', name: 'Test', status: 'ACTIVE' }

      expect(validation.canSubmit.value).toBe(true)
    })

    it('updates canSubmit when text changes', () => {
      const postText = createRef('')
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText,
        selectedChannel,
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(false)

      postText.value = 'Hello'

      expect(validation.canSubmit.value).toBe(true)
    })

    it('updates attachmentCount when attachments change', () => {
      const attachmentCount = createRef(0)
      const selectedChannel = createRef({
        id: 'ch1',
        provider: 'linkedin',
        name: 'Test',
        status: 'ACTIVE',
      })
      const options = createMockOptions({
        postText: createRef('Hello'),
        selectedChannel,
        attachmentCount,
      })

      const validation = useComposerValidation(options)

      expect(validation.canSubmit.value).toBe(true)

      attachmentCount.value = 10 // Over limit

      expect(validation.canSubmit.value).toBe(false)
    })
  })
})

// ============================================================================
// HELPERS
// ============================================================================

type ChannelShape = {
  id: string
  provider: string
  name: string
  status: string
  attachmentLimit?: number
}

type MockOptions = {
  postText: Ref<string> | string
  selectedChannel: Ref<ChannelShape | undefined> | ChannelShape | undefined
  attachmentCount: Ref<number> | number
  isScheduleValid: Ref<boolean> | boolean
  isEditMode: Ref<boolean> | boolean
  isSubmitting: Ref<boolean> | boolean
}

function normalizeRef<T>(value: Ref<T> | T | undefined): Ref<T | undefined> {
  if (value === undefined) return ref(undefined) as Ref<T | undefined>
  if (isRef(value)) return value as Ref<T | undefined>
  return ref(value) as Ref<T | undefined>
}

function createMockOptions(overrides: Partial<MockOptions>): {
  postText: Ref<string>
  selectedChannel: Ref<ChannelShape | undefined>
  attachmentCount: Ref<number>
  isScheduleValid: Ref<boolean>
  isEditMode: Ref<boolean>
  isSubmitting: Ref<boolean>
} {
  return {
    postText: normalizeRef(overrides.postText ?? '') as Ref<string>,
    selectedChannel: normalizeRef(overrides.selectedChannel),
    attachmentCount: normalizeRef(overrides.attachmentCount ?? 0) as Ref<number>,
    isScheduleValid: normalizeRef(overrides.isScheduleValid ?? true) as Ref<boolean>,
    isEditMode: normalizeRef(overrides.isEditMode ?? false) as Ref<boolean>,
    isSubmitting: normalizeRef(overrides.isSubmitting ?? false) as Ref<boolean>,
  }
}

// ============================================================================
// FORMAT HELPERS
// ============================================================================

describe('formatCharCount', () => {
  it('formats remaining chars correctly', () => {
    expect(formatCharCount(3000, 3000)).toBe('3000 / 3000')
    expect(formatCharCount(1500, 3000)).toBe('1500 / 3000')
    expect(formatCharCount(0, 3000)).toBe('0 / 3000')
  })

  it('formats over limit correctly', () => {
    expect(formatCharCount(-1, 3000)).toBe('1 over limit')
    expect(formatCharCount(-50, 3000)).toBe('50 over limit')
  })
})

describe('getCharCountState', () => {
  it('returns normal when above threshold', () => {
    expect(getCharCountState(500, 3000)).toBe('normal')
    expect(getCharCountState(300, 3000)).toBe('normal') // 10%
  })

  it('returns warning when below threshold but above 0', () => {
    expect(getCharCountState(299, 3000)).toBe('warning')
    expect(getCharCountState(100, 3000)).toBe('warning')
  })

  it('returns error when negative', () => {
    expect(getCharCountState(0, 3000)).toBe('warning')
    expect(getCharCountState(-1, 3000)).toBe('error')
    expect(getCharCountState(-100, 3000)).toBe('error')
  })
})
