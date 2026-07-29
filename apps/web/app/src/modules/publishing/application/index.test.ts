import { describe, it, expect } from 'vitest'
import { ref } from 'vue'

describe('application barrel exports', () => {
  it('exports useComposerScheduling', async (): Promise<void> => {
    const { useComposerScheduling } = await import('./index')
    expect(typeof useComposerScheduling).toBe('function')
  })

  it('exports useComposerValidation', async (): Promise<void> => {
    const { useComposerValidation } = await import('./index')
    expect(typeof useComposerValidation).toBe('function')
  })

  it('exports useComposerTextFormatting', async (): Promise<void> => {
    const { useComposerTextFormatting } = await import('./index')
    expect(typeof useComposerTextFormatting).toBe('function')
  })

  it('exports useComposerMediaPicker', async (): Promise<void> => {
    const { useComposerMediaPicker } = await import('./index')
    expect(typeof useComposerMediaPicker).toBe('function')
  })

  it('exports useCalendarUrl', async (): Promise<void> => {
    const { useCalendarUrl } = await import('./index')
    expect(typeof useCalendarUrl).toBe('function')
  })

  it('exports useQueuedCounts', async (): Promise<void> => {
    const { useQueuedCounts } = await import('./index')
    expect(typeof useQueuedCounts).toBe('function')
  })

  it('exports formatCharCount helper', async (): Promise<void> => {
    const { formatCharCount } = await import('./index')
    expect(typeof formatCharCount).toBe('function')
    expect(formatCharCount(3000, 3000)).toBe('3000 / 3000')
  })

  it('exports getCharCountState helper', async (): Promise<void> => {
    const { getCharCountState } = await import('./index')
    expect(typeof getCharCountState).toBe('function')
    expect(getCharCountState(3000, 3000)).toBe('normal')
  })

  it('composables can be instantiated from barrel imports', async (): Promise<void> => {
    const { useComposerScheduling, useComposerValidation, useComposerTextFormatting } =
      await import('./index')

    const scheduling = useComposerScheduling()
    expect(scheduling.scheduleMode.value).toBe('now')

    const postText = ref('Hello')
    const validation = useComposerValidation({
      postText,
      selectedChannel: ref(undefined),
      attachmentCount: ref(0),
      isScheduleValid: ref(true),
      isEditMode: ref(false),
      isSubmitting: ref(false),
    })
    expect(validation.charLimit).toBe(3000)

    const formatting = useComposerTextFormatting({ postText })
    expect(formatting.isAiProcessing.value).toBe(false)
  })
})
