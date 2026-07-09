/**
 * useFeatureFlags — fetch and cache backend feature flags
 *
 * Queries /api/flags once per session to determine which features are enabled.
 * The backend controls availability based on environment configuration
 * (e.g., UNSPLASH_ACCESS_KEY presence enables unsplashProviderEnabled).
 *
 * @example
 * ```ts
 * const { isUnsplashProviderEnabled, isLoading } = useFeatureFlags()
 * ```
 */
import { ref, readonly, computed } from 'vue'
import { resolveApiUrl } from '@/lib/auth-api'

interface FeatureFlags {
  unsplashProviderEnabled: boolean
}

const flags = ref<FeatureFlags>({
  unsplashProviderEnabled: false,
})

const isLoading = ref(false)
const isLoaded = ref(false)
const error = ref<Error | null>(null)

/**
 * Fetch feature flags from the backend. Only fetches once per session.
 */
async function fetchFeatureFlags(): Promise<void> {
  if (isLoaded.value || isLoading.value) return

  isLoading.value = true
  error.value = null

  try {
    const url = resolveApiUrl('/api/flags')
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
    })

    if (!response.ok) {
      throw new Error(`Failed to fetch feature flags: ${response.status} ${response.statusText}`)
    }

    const data = (await response.json()) as { flags: FeatureFlags }
    flags.value = data.flags
    isLoaded.value = true
  } catch (err) {
    error.value = err instanceof Error ? err : new Error(String(err))
    // On error, keep flags at their default (all false)
  } finally {
    isLoading.value = false
  }
}

/**
 * useFeatureFlags composable
 *
 * Returns reactive feature flag state and triggers fetch on first call.
 * Subsequent calls return the cached result.
 */
export function useFeatureFlags() {
  // Auto-fetch on first use
  if (!isLoaded.value && !isLoading.value) {
    void fetchFeatureFlags()
  }

  return {
    isUnsplashProviderEnabled: readonly(computed(() => flags.value.unsplashProviderEnabled)),
    isLoading: readonly(isLoading),
    isLoaded: readonly(isLoaded),
    error: readonly(error),
    refetch: fetchFeatureFlags,
  }
}
