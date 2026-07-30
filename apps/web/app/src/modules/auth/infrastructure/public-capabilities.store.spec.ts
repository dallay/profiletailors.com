import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from './auth-api'
import { usePublicCapabilitiesStore } from './public-capabilities.store'

describe('usePublicCapabilitiesStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('accepts only exact boolean capability values', async () => {
    vi.spyOn(authApi, 'fetchPublicCapabilities').mockResolvedValue({
      registrationEnabled: 'true',
      passwordRecoveryEnabled: 1,
    } as unknown as Awaited<ReturnType<typeof authApi.fetchPublicCapabilities>>)

    const store = usePublicCapabilitiesStore()
    await store.load()

    expect(store.registrationEnabled).toBe(false)
    expect(store.passwordRecoveryEnabled).toBe(false)
    expect(store.resolved).toBe(true)
  })

  it('shares one request between concurrent loads', async () => {
    let resolveRequest!: (value: unknown) => void
    const request = new Promise((resolve) => {
      resolveRequest = resolve
    })
    const fetch = vi.spyOn(authApi, 'fetchPublicCapabilities').mockReturnValue(request as never)
    const store = usePublicCapabilitiesStore()

    const first = store.load()
    const secondLoad = store.load()

    expect(fetch).toHaveBeenCalledOnce()
    resolveRequest({ registrationEnabled: true, passwordRecoveryEnabled: true })
    await Promise.all([first, secondLoad])
    expect(store.registrationEnabled).toBe(true)
    expect(store.passwordRecoveryEnabled).toBe(true)
  })

  it('fails closed, resolves the attempt, and can retry', async () => {
    const fetch = vi
      .spyOn(authApi, 'fetchPublicCapabilities')
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce({ registrationEnabled: true, passwordRecoveryEnabled: true })
    const store = usePublicCapabilitiesStore()

    await store.load()

    expect(store.registrationEnabled).toBe(false)
    expect(store.passwordRecoveryEnabled).toBe(false)
    expect(store.resolved).toBe(true)
    expect(store.error).toBe('Network error')

    await store.retry()

    expect(fetch).toHaveBeenCalledTimes(2)
    expect(store.registrationEnabled).toBe(true)
    expect(store.passwordRecoveryEnabled).toBe(true)
    expect(store.error).toBeNull()
  })
})
