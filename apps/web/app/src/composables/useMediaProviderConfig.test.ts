import { describe, it, expect, afterEach, vi } from 'vitest'
import { useMediaProviderConfig } from './useMediaProviderConfig'

describe('useMediaProviderConfig', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('defaults to disabled when the env is absent', () => {
    vi.stubEnv('VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED', undefined)
    expect(useMediaProviderConfig().unsplashEnabled.value).toBe(false)
  })

  it('returns false for explicit "false"', () => {
    vi.stubEnv('VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED', 'false')
    expect(useMediaProviderConfig().unsplashEnabled.value).toBe(false)
  })

  it('returns true for explicit "true"', () => {
    vi.stubEnv('VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED', 'true')
    expect(useMediaProviderConfig().unsplashEnabled.value).toBe(true)
  })
})
