import { computed } from 'vue'

/**
 * Build-time media provider flags for the SPA.
 *
 * `VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED` is parsed once per access.
 * Missing or invalid values always fall back to `false`.
 */

function parseFlag(value: string | undefined): boolean {
  const lower = (value ?? 'false').trim().toLowerCase()
  return lower === 'true' || lower === '1' || lower === 'yes'
}

export type MediaProviderConfig = {
  unsplashEnabled: boolean
}

export function useMediaProviderConfig() {
  return {
    unsplashEnabled: computed(() =>
      parseFlag(import.meta.env.VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED as string | undefined),
    ),
  }
}
