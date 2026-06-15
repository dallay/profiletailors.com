import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { useConnectMessage } from './useConnectMessage'

function mountComposable<T>(setup: () => T): { result: T; unmount: () => void } {
  let captured!: T
  const Harness = defineComponent({
    setup() {
      captured = setup()
      return () => h('div')
    },
  })
  const wrapper = mount(Harness, { attachTo: document.body })
  return { result: captured, unmount: () => wrapper.unmount() }
}

describe('useConnectMessage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('show sets the message', () => {
    const { result } = mountComposable(() => useConnectMessage())
    expect(result.message.value).toBe('')

    result.show('Connecting...')
    expect(result.message.value).toBe('Connecting...')
  })

  it('auto-clears the message after the duration', () => {
    const { result } = mountComposable(() => useConnectMessage({ defaultDurationMs: 100 }))
    result.show('Hello', 100)
    expect(result.message.value).toBe('Hello')

    vi.advanceTimersByTime(100)
    expect(result.message.value).toBe('')
  })

  it('uses the default duration when none is provided', () => {
    const { result } = mountComposable(() => useConnectMessage({ defaultDurationMs: 2000 }))
    result.show('Hi')
    expect(result.message.value).toBe('Hi')

    vi.advanceTimersByTime(1999)
    expect(result.message.value).toBe('Hi')
    vi.advanceTimersByTime(1)
    expect(result.message.value).toBe('')
  })

  it('a subsequent show cancels the prior timer', () => {
    const { result } = mountComposable(() => useConnectMessage())
    result.show('First', 100)
    expect(result.message.value).toBe('First')

    vi.advanceTimersByTime(50)
    result.show('Second', 200)
    expect(result.message.value).toBe('Second')

    // The original 100ms timer would have fired here — assert it did NOT
    vi.advanceTimersByTime(60)
    expect(result.message.value).toBe('Second')

    vi.advanceTimersByTime(200)
    expect(result.message.value).toBe('')
  })

  it('clear() cancels the pending timer and empties the message', () => {
    const { result } = mountComposable(() => useConnectMessage())
    result.show('Pending', 500)
    expect(result.message.value).toBe('Pending')

    result.clear()
    expect(result.message.value).toBe('')

    // Advancing past the original duration must NOT mutate anything
    vi.advanceTimersByTime(500)
    expect(result.message.value).toBe('')
  })

  it('unmount cancels the pending timer', () => {
    const { result, unmount } = mountComposable(() => useConnectMessage())
    result.show('Stays', 1000)
    expect(result.message.value).toBe('Stays')

    unmount()

    // Advancing past the original duration must not throw and must not mutate
    // a stale ref. We assert the spy on clearTimeout was called during unmount.
    expect(vi.getTimerCount()).toBe(0)
    vi.advanceTimersByTime(1500)
  })
})
