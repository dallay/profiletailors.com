import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useOnlineStatus } from './useOnlineStatus'

const Harness = defineComponent({
  setup() {
    const { status, retry } = useOnlineStatus()
    return { status, retry }
  },
  template: '<div />',
})

function okResponse(): Response {
  return new Response(null, { status: 200 })
}

function failedResponse(): Response {
  return new Response(null, { status: 503 })
}

describe('useOnlineStatus', () => {
  const wrappers: VueWrapper[] = []
  const mockFetch = vi.fn()

  beforeEach(() => {
    Object.defineProperty(window.navigator, 'onLine', { value: true, configurable: true })
    mockFetch.mockReset()
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    while (wrappers.length) wrappers.pop()!.unmount()
    vi.unstubAllGlobals()
    Object.defineProperty(window.navigator, 'onLine', { value: true, configurable: true })
  })

  function mountHarness(): VueWrapper {
    const wrapper = mount(Harness)
    wrappers.push(wrapper)
    return wrapper
  }

  it('starts as checking', () => {
    const wrapper = mountHarness()
    expect(wrapper.vm.status).toBe('checking')
  })

  it('reports offline when navigator goes offline', async () => {
    Object.defineProperty(window.navigator, 'onLine', { value: false, configurable: true })
    const wrapper = mountHarness()
    await flushPromises()
    expect(wrapper.vm.status).toBe('offline')
  })

  it('reports online when the heartbeat succeeds', async () => {
    mockFetch.mockResolvedValue(okResponse())
    const wrapper = mountHarness()
    await flushPromises()
    expect(wrapper.vm.status).toBe('online')
  })

  it('reports api-unreachable when the heartbeat fails with an error', async () => {
    mockFetch.mockRejectedValue(new TypeError('Failed to fetch'))
    const wrapper = mountHarness()
    await flushPromises()
    expect(wrapper.vm.status).toBe('api-unreachable')
  })

  it('reports api-unreachable when the heartbeat returns a non-ok status', async () => {
    mockFetch.mockResolvedValue(failedResponse())
    const wrapper = mountHarness()
    await flushPromises()
    expect(wrapper.vm.status).toBe('api-unreachable')
  })

  it('ignores a stale probe when a newer probe has already resolved', async () => {
    let resolveFirst!: (value: Response) => void
    const firstProbe = new Promise<Response>((resolve) => {
      resolveFirst = resolve
    })
    mockFetch.mockReturnValueOnce(firstProbe).mockResolvedValueOnce(okResponse())

    const wrapper = mountHarness()
    await flushPromises()
    await wrapper.vm.retry()
    resolveFirst(failedResponse())
    await flushPromises()

    expect(wrapper.vm.status).toBe('online')
  })

  it('ignores a stale probe rejection when a newer probe succeeds', async () => {
    let rejectFirst!: (reason: unknown) => void
    const firstProbe = new Promise<Response>((_resolve, reject) => {
      rejectFirst = reject
    })
    mockFetch.mockReturnValueOnce(firstProbe).mockResolvedValueOnce(okResponse())

    const wrapper = mountHarness()
    await flushPromises()
    await wrapper.vm.retry()
    rejectFirst(new TypeError('old probe failed'))
    await flushPromises()

    expect(wrapper.vm.status).toBe('online')
  })

  it('triggers a re-probe on window online events', async () => {
    mockFetch.mockResolvedValue(okResponse())
    mountHarness()
    await flushPromises()

    expect(mockFetch).toHaveBeenCalledTimes(1)
    window.dispatchEvent(new Event('online'))
    await flushPromises()
    expect(mockFetch).toHaveBeenCalledTimes(2)
  })

  it('reports offline when the probe fails and navigator is offline', async () => {
    let rejectProbe!: (reason: unknown) => void
    const pendingProbe = new Promise<Response>((_resolve, reject) => {
      rejectProbe = reject
    })
    mockFetch.mockReturnValue(pendingProbe)
    const wrapper = mountHarness()
    await flushPromises()

    Object.defineProperty(window.navigator, 'onLine', { value: false, configurable: true })
    rejectProbe(new TypeError('Failed to fetch'))
    await flushPromises()

    expect(wrapper.vm.status).toBe('offline')
  })

  it('aborts a hung probe after the timeout', async () => {
    vi.useFakeTimers()
    try {
      mockFetch.mockImplementation(
        (_url: string, init?: RequestInit) =>
          new Promise<Response>((_resolve, reject) => {
            init?.signal?.addEventListener('abort', () => {
              reject(new DOMException('Aborted', 'AbortError'))
            })
          }),
      )
      const wrapper = mountHarness()
      await vi.advanceTimersByTimeAsync(3000 + 100)
      expect(wrapper.vm.status).toBe('api-unreachable')
    } finally {
      vi.useRealTimers()
    }
  })
})
