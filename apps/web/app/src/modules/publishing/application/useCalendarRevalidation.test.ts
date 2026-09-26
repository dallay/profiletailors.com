import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { useCalendarRevalidation } from './useCalendarRevalidation'

describe('useCalendarRevalidation', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('coalesces invalidations and executes the latest visible range once', async () => {
    const fetchCalendar = vi.fn().mockResolvedValue(undefined)
    const revalidation = useCalendarRevalidation({ fetchCalendar, debounceMs: 50, maxAgeMs: 5_000 })

    revalidation.request({ from: 'old', to: 'old' })
    revalidation.request({ from: 'new', to: 'new' })
    await vi.advanceTimersByTimeAsync(50)

    expect(fetchCalendar).toHaveBeenCalledOnce()
    expect(fetchCalendar).toHaveBeenCalledWith({ from: 'new', to: 'new' })
  })

  it('does not revalidate while hidden and revalidates on visible return', async () => {
    const fetchCalendar = vi.fn().mockResolvedValue(undefined)
    const revalidation = useCalendarRevalidation({ fetchCalendar, debounceMs: 50, maxAgeMs: 5_000 })

    revalidation.setVisible(false)
    revalidation.request({ from: 'from', to: 'to' })
    await vi.advanceTimersByTimeAsync(100)
    expect(fetchCalendar).not.toHaveBeenCalled()

    revalidation.setVisible(true)
    await vi.advanceTimersByTimeAsync(50)
    expect(fetchCalendar).toHaveBeenCalledOnce()

    revalidation.stop()
  })

  it('refetches on a new invalidation even when the previous fetch was recent', async () => {
    const fetchCalendar = vi.fn().mockResolvedValue(undefined)
    const revalidation = useCalendarRevalidation({ fetchCalendar, debounceMs: 50, maxAgeMs: 5_000 })

    revalidation.request({ from: 'old', to: 'old' })
    await vi.advanceTimersByTimeAsync(50)
    expect(fetchCalendar).toHaveBeenCalledOnce()

    revalidation.request({ from: 'new', to: 'new' })
    await vi.advanceTimersByTimeAsync(50)
    expect(fetchCalendar).toHaveBeenCalledTimes(2)
    expect(fetchCalendar).toHaveBeenLastCalledWith({ from: 'new', to: 'new' })

    revalidation.stop()
  })

  it('skips refresh on visible return when the range is fresh and clean', async () => {
    const fetchCalendar = vi.fn().mockResolvedValue(undefined)
    const revalidation = useCalendarRevalidation({ fetchCalendar, debounceMs: 50, maxAgeMs: 5_000 })

    revalidation.request({ from: 'from', to: 'to' })
    await vi.advanceTimersByTimeAsync(50)
    expect(fetchCalendar).toHaveBeenCalledOnce()

    revalidation.setVisible(false)
    revalidation.setVisible(true)
    await vi.advanceTimersByTimeAsync(100)
    expect(fetchCalendar).toHaveBeenCalledOnce()

    revalidation.stop()
  })

  it('refreshes on visible return when the range exceeded max age', async () => {
    const fetchCalendar = vi.fn().mockResolvedValue(undefined)
    const revalidation = useCalendarRevalidation({ fetchCalendar, debounceMs: 50, maxAgeMs: 5_000 })

    revalidation.request({ from: 'from', to: 'to' })
    await vi.advanceTimersByTimeAsync(50)
    expect(fetchCalendar).toHaveBeenCalledOnce()

    await vi.advanceTimersByTimeAsync(6_000)
    revalidation.setVisible(false)
    revalidation.setVisible(true)
    await vi.advanceTimersByTimeAsync(50)
    expect(fetchCalendar).toHaveBeenCalledTimes(2)

    revalidation.stop()
  })

  it('defers a pending invalidation until the in-flight fetch settles', async () => {
    let resolveFirst!: () => void
    const fetchCalendar = vi
      .fn()
      .mockImplementation(() => new Promise<void>((resolve) => (resolveFirst = resolve)))
    const revalidation = useCalendarRevalidation({ fetchCalendar, debounceMs: 50, maxAgeMs: 5_000 })

    revalidation.request({ from: 'old', to: 'old' })
    await vi.advanceTimersByTimeAsync(50)
    expect(fetchCalendar).toHaveBeenCalledOnce()

    revalidation.request({ from: 'new', to: 'new' })
    await vi.advanceTimersByTimeAsync(50)
    expect(fetchCalendar).toHaveBeenCalledOnce()

    resolveFirst()
    await vi.advanceTimersByTimeAsync(100)
    expect(fetchCalendar).toHaveBeenCalledTimes(2)
    expect(fetchCalendar).toHaveBeenLastCalledWith({ from: 'new', to: 'new' })

    revalidation.stop()
  })

  it('uses custom setTimeoutFn, clearTimeoutFn, and now options', async () => {
    const customSetTimeout = vi.fn((cb: () => void, delay: number) => setTimeout(cb, delay))
    const customClearTimeout = vi.fn((handle: ReturnType<typeof setTimeout>) =>
      clearTimeout(handle),
    )
    const mockTime = 1_000
    const customNow = vi.fn(() => mockTime)
    const fetchCalendar = vi.fn().mockResolvedValue(undefined)

    const revalidation = useCalendarRevalidation({
      fetchCalendar,
      debounceMs: 100,
      setTimeoutFn: customSetTimeout,
      clearTimeoutFn: customClearTimeout,
      now: customNow,
    })

    revalidation.request({ from: 'a', to: 'b' })
    expect(customSetTimeout).toHaveBeenCalledWith(expect.any(Function), 100)

    revalidation.setVisible(false)
    expect(customClearTimeout).toHaveBeenCalled()

    revalidation.setVisible(true)
    await vi.advanceTimersByTimeAsync(100)
    expect(fetchCalendar).toHaveBeenCalledOnce()
    expect(customNow).toHaveBeenCalled()

    revalidation.stop()
  })

  it('stops scheduling on component unmount when used inside Vue setup', async () => {
    const fetchCalendar = vi.fn().mockResolvedValue(undefined)

    const TestComponent = defineComponent({
      setup() {
        const revalidation = useCalendarRevalidation({ fetchCalendar, debounceMs: 50 })
        return { revalidation }
      },
      template: '<div>test</div>',
    })

    const wrapper = mount(TestComponent)
    wrapper.vm.revalidation.request({ from: 'a', to: 'b' })

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(100)

    expect(fetchCalendar).not.toHaveBeenCalled()
  })
})
