import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
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
    const onHidden = vi.fn()
    const { now, stop } = useReactiveClock({ onVisible, onHidden })

    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(onHidden).toHaveBeenCalledTimes(1)
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

  it('cancels alignment timer if document becomes hidden before alignment fires', () => {
    const { now, stop } = useReactiveClock()

    // Become hidden before 26_000ms delay finishes
    vi.advanceTimersByTime(10_000)
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))

    // Advance past the initial alignment boundary
    vi.advanceTimersByTime(30_000)
    expect(now.value).toEqual(new Date('2026-09-25T10:12:34.000Z'))

    stop()
  })

  it('stops clock automatically on component unmount when mounted in setup context', () => {
    const TestComp = defineComponent({
      setup() {
        const clock = useReactiveClock()
        return { clock }
      },
      template: '<div>{{ clock.now }}</div>',
    })

    const wrapper = mount(TestComp)
    expect(wrapper.vm.clock.now.value).toEqual(new Date('2026-09-25T10:12:34.000Z'))

    wrapper.unmount()
    vi.advanceTimersByTime(30_000)
    expect(wrapper.vm.clock.now.value).toEqual(new Date('2026-09-25T10:12:34.000Z'))
  })
})
