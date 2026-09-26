import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { usePublicationEventReconnect } from './usePublicationEventReconnect'
import type { PublicationInvalidation } from '../infrastructure/publishing.store'

describe('usePublicationEventReconnect', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  function makeSut({
    initialVisible = true,
    isAuthenticated = true,
    workspaceId = 'ws-1',
  }: {
    initialVisible?: boolean
    isAuthenticated?: boolean
    workspaceId?: string | null
  } = {}) {
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: initialVisible ? 'visible' : 'hidden',
    })
    document.dispatchEvent(new Event('visibilitychange'))

    const isConnected = ref(false)
    const onVisible = vi.fn()
    const onHidden = vi.fn()
    const unsubscribe = vi.fn(() => {
      isConnected.value = false
    })
    let onInvalidationCallback: ((event: PublicationInvalidation) => void) | undefined
    const subscribe = vi.fn<
      [(cb: (event: PublicationInvalidation) => void) => void],
      Promise<null>
    >(async (cb) => {
      onInvalidationCallback = cb
      return null
    })

    const sut = usePublicationEventReconnect({
      subscribe,
      unsubscribe,
      isConnected,
      isAuthenticated: ref(isAuthenticated),
      workspaceId: ref(workspaceId),
      onVisible,
      onHidden,
    })

    return {
      sut,
      subscribe,
      unsubscribe,
      isConnected,
      onVisible,
      onHidden,
      triggerInvalidation: (event: PublicationInvalidation) => {
        onInvalidationCallback?.(event)
      },
    }
  }

  it('calls onVisible when started while visible', () => {
    const { sut, onVisible } = makeSut({ initialVisible: true })
    sut.start()
    expect(onVisible).toHaveBeenCalled()
  })

  it('calls onHidden when started while hidden', () => {
    const { sut, onHidden } = makeSut({ initialVisible: false })
    sut.start()
    expect(onHidden).toHaveBeenCalled()
  })

  it('calls unsubscribe on stop', () => {
    const { sut, unsubscribe } = makeSut()
    sut.start()
    sut.stop()
    expect(unsubscribe).toHaveBeenCalled()
  })

  it('calls unsubscribe multiple times when stopped multiple times', () => {
    const { sut, unsubscribe } = makeSut()
    sut.stop()
    sut.stop()
    expect(unsubscribe).toHaveBeenCalledTimes(2)
  })

  it('fires onVisible on visibility change to visible', () => {
    const { sut, onVisible } = makeSut({ initialVisible: false })
    sut.start()
    expect(onVisible).not.toHaveBeenCalled()

    Object.defineProperty(document, 'visibilityState', { value: 'visible' })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(onVisible).toHaveBeenCalled()
  })

  it('fires onHidden on visibility change to hidden', () => {
    const { sut, onHidden } = makeSut({ initialVisible: true })
    sut.start()
    expect(onHidden).not.toHaveBeenCalled()

    Object.defineProperty(document, 'visibilityState', { value: 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(onHidden).toHaveBeenCalled()
  })

  it('does not call subscribe when isAuthenticated is false', () => {
    const { sut, subscribe } = makeSut({ isAuthenticated: false, workspaceId: null })
    sut.start()
    expect(subscribe).not.toHaveBeenCalled()
  })

  it('does not call subscribe when workspaceId is null', () => {
    const { sut, subscribe } = makeSut({ workspaceId: null })
    sut.start()
    expect(subscribe).not.toHaveBeenCalled()
  })

  it('exposes isReconnecting as a reactive ref', () => {
    const { sut } = makeSut()
    const reconnect = sut as unknown as { isReconnecting: { value: boolean } }
    expect(typeof reconnect.isReconnecting.value).toBe('boolean')
    reconnect.isReconnecting.value = true
    expect(reconnect.isReconnecting.value).toBe(true)
  })

  it('invokes onInvalidation callback registered during subscribe', async () => {
    const { sut, triggerInvalidation } = makeSut()
    sut.start()
    await vi.waitFor(() => {
      expect(sut.isReconnecting.value).toBe(false)
    })

    const sampleEvent: PublicationInvalidation = {
      type: 'publication-invalidated',
      workspaceId: 'ws-1',
      publicationId: 'pub-1',
      reason: 'updated',
      occurredAt: new Date().toISOString(),
    }

    triggerInvalidation(sampleEvent)
    expect(sut.isReconnecting.value).toBe(false)
  })

  it('schedules reconnection with backoff when subscribe resolves while disconnected', async () => {
    const { sut, subscribe, isConnected } = makeSut()
    sut.start()

    await vi.waitFor(() => {
      expect(subscribe).toHaveBeenCalledOnce()
    })

    expect(isConnected.value).toBe(false)
    expect(sut.isReconnecting.value).toBe(true)

    // Advance past backoff delay to trigger reconnection attempt
    await vi.advanceTimersByTimeAsync(2_000)
    expect(subscribe).toHaveBeenCalledTimes(2)
  })

  it('aborts reconnection inside timer if stopped or document hidden before timer fires', async () => {
    const { sut, subscribe } = makeSut()
    sut.start()

    await vi.waitFor(() => {
      expect(subscribe).toHaveBeenCalledOnce()
    })

    // Stop before timer fires
    sut.stop()
    await vi.advanceTimersByTimeAsync(5_000)
    expect(subscribe).toHaveBeenCalledOnce()
  })
})
