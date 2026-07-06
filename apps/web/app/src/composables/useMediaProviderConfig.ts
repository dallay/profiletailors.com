/**
 * Resolves which media providers are enabled in the SPA build.
 *
 * The flag is controlled at build time via `VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED`.
 * Default is `false` outside production, mirroring the backend default.
 */

function parseFlag(value: string | undefined): boolean {
  const lower = (value ?? 'false').trim().toLowerCase()
  return lower === 'true' || lower === '1' || lower === 'yes'
}

export interface MediaProviderConfig {
  unsplashEnabled: boolean
}

export function resolveMediaProviderConfig(): MediaProviderConfig {
  return {
    unsplashEnabled: parseFlag(
      import.meta.env.VITE_MEDIA_PROVIDER_UNSPLASH_ENABLED as string | undefined,
    ),
  }
}
