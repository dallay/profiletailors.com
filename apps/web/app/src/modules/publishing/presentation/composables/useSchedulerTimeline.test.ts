import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useSchedulerTimeline } from './useSchedulerTimeline'

describe('useSchedulerTimeline', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('computes nowFormattedTime and nowTopPx based on current time', () => {
    vi.setSystemTime(new Date('2026-06-15T10:30:00Z'))
    const days = ref([new Date('2026-06-15T00:00:00Z')])
    const hourSlots = ref([{ hour: 10, label: '10 AM' }])
    const isToday = (d: Date) => d.toISOString().startsWith('2026-06-15')

    const timeline = useSchedulerTimeline({ days, hourSlots, isToday })

    expect(timeline.hasToday.value).toBe(true)
    // 10:30 is 10 * 60 + 30 = 630 minutes. (630 / 60) * 96 = 1008px
    expect(timeline.nowTopPx.value).toBe(1008)
    expect(timeline.nowFormattedTime.value).toBe('10:30')
  })

  it('updates now ref when timer ticks', () => {
    vi.setSystemTime(new Date('2026-06-15T10:30:00Z'))
    const days = ref([new Date('2026-06-15T00:00:00Z')])
    const hourSlots = ref([{ hour: 10, label: '10 AM' }])

    const timeline = useSchedulerTimeline({ days, hourSlots, isToday: () => true })

    expect(timeline.nowFormattedTime.value).toBe('10:30')

    vi.setSystemTime(new Date('2026-06-15T10:30:35Z'))
    vi.advanceTimersByTime(30_000)

    expect(timeline.nowFormattedTime.value).toBe('10:31')
  })

  it('scrolls viewport to current time slot', () => {
    vi.setSystemTime(new Date('2026-06-15T10:30:00Z'))
    const days = ref([new Date('2026-06-15T00:00:00Z')])
    const hourSlots = ref([{ hour: 10, label: '10 AM' }])

    const timeline = useSchedulerTimeline({ days, hourSlots, isToday: () => true })

    const mockElement = { scrollTop: 0 } as HTMLElement
    timeline.viewportRef.value = mockElement

    timeline.scrollToCurrentTimeAndToday()

    // (10 - 1) * 96 = 864
    expect(mockElement.scrollTop).toBe(864)
  })

  it('triggers scrollToCurrentTimeAndToday when days or hourSlots change', async () => {
    vi.setSystemTime(new Date('2026-06-15T10:30:00Z'))
    const days = ref([new Date('2026-06-15T00:00:00Z')])
    const hourSlots = ref([{ hour: 10, label: '10 AM' }])

    const timeline = useSchedulerTimeline({ days, hourSlots, isToday: () => true })

    const mockElement = { scrollTop: 0 } as HTMLElement
    timeline.viewportRef.value = mockElement

    days.value = [new Date('2026-06-15T00:00:00Z'), new Date('2026-06-16T00:00:00Z')]
    await nextTick()
    await nextTick()

    expect(mockElement.scrollTop).toBe(864)
  })
})
