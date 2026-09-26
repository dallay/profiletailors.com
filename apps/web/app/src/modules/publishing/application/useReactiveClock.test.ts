import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useReactiveClock } from './useReactiveClock'

describe('useReactiveClock', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-25T10:12:34.000Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('aligns the first timer with the next minute and ticks while visible', () => {
    const { now, stop } = useReactiveClock()

    expect(now.value).toEqual(new Date('2026-09-25T10:12:34.000Z'))
    vi.advanceTimersByTime(26_000)
    expect(now.value).toEqual(new Date('2026-09-25T10:13:00.000Z'))

    stop()
  })

  it('pauses when hidden and emits a refresh when visible again', () => {
    const onVisible = vi.fn()
    const { now, stop } = useReactiveClock({ onVisible })

    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    vi.advanceTimersByTime(120_000)
    expect(now.value).toEqual(new Date('2026-09-25T10:12:34.000Z'))

    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(onVisible).toHaveBeenCalledTimes(1)
    expect(now.value).toEqual(new Date('2026-09-25T10:14:34.000Z'))

    stop()
    document.dispatchEvent(new Event('visibilitychange'))
    expect(onVisible).toHaveBeenCalledTimes(1)
  })
})
