import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { initPwa, updatePwa } from './registerPwa'

const registerSW = vi.fn()

vi.mock('virtual:pwa-register', () => ({
  registerSW: (...args: unknown[]) => registerSW(...args),
}))

describe('registerPwa', () => {
  beforeEach(() => {
    registerSW.mockReset()
    vi.unstubAllEnvs()
    Reflect.deleteProperty(window.navigator, 'serviceWorker')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('does nothing outside production', () => {
    initPwa(vi.fn())
    expect(registerSW).not.toHaveBeenCalled()
  })

  it('registers the service worker and wires onNeedRefresh in production', () => {
    vi.stubEnv('PROD', 'true')
    Object.defineProperty(window.navigator, 'serviceWorker', { value: {}, configurable: true })
    const onNeedRefresh = vi.fn()
    initPwa(onNeedRefresh)

    expect(registerSW).toHaveBeenCalledOnce()
    const opts = registerSW.mock.calls[0]?.[0]
    expect(opts).toMatchObject({ onNeedRefresh })
  })

  it('does nothing when navigator has no serviceWorker', () => {
    Reflect.deleteProperty(window.navigator, 'serviceWorker')
    initPwa(vi.fn())
    expect(registerSW).not.toHaveBeenCalled()
  })

  it('calls the registered update function when updatePwa is invoked', async () => {
    vi.stubEnv('PROD', 'true')
    Object.defineProperty(window.navigator, 'serviceWorker', { value: {}, configurable: true })
    const update = vi.fn()
    registerSW.mockReturnValue(update)
    initPwa(vi.fn())

    await updatePwa()
    expect(update).toHaveBeenCalledWith(true)
  })

  it('resolves without error when no service worker was registered', async () => {
    registerSW.mockClear()
    await expect(updatePwa()).resolves.toBeUndefined()
    expect(registerSW).not.toHaveBeenCalled()
  })
})
