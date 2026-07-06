import { describe, it, expect, afterEach } from 'vitest'
import { resolveMediaProviderConfig } from './useMediaProviderConfig'

describe('resolveMediaProviderConfig', () => {
  const original = process.env.VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED

  afterEach(() => {
    if (original === undefined) {
      delete process.env.VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED
    } else {
      process.env.VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED = original
    }
  })

  it('defaults to disabled when the env is absent', () => {
    delete process.env.VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED
    expect(resolveMediaProviderConfig().unsplashEnabled).toBe(false)
  })

  it('returns false for explicit "false"', () => {
    process.env.VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED = 'false'
    expect(resolveMediaProviderConfig().unsplashEnabled).toBe(false)
  })

  it('returns true for explicit "true"', () => {
    process.env.VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED = 'true'
    expect(resolveMediaProviderConfig().unsplashEnabled).toBe(true)
  })
})
