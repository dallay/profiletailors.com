import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { usePublicationEventReconnect } from './usePublicationEventReconnect'

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
    const subscribe = vi.fn<() => Promise<null>>(async () => null)

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
})
