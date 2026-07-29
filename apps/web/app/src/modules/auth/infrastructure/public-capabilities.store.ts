import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { PublicCapabilities } from '@modules/auth/domain/public-capabilities'
import { fetchPublicCapabilities } from '@modules/auth/infrastructure/auth-api'

export const usePublicCapabilitiesStore = defineStore('public-capabilities', () => {
  const _capabilities = ref<PublicCapabilities>({
    registrationEnabled: false,
    passwordRecoveryEnabled: false,
    ssoProviders: [],
  })
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const capabilitiesLoaded = ref(false)

  let _loadPromise: Promise<void> | null = null

  const registrationEnabled = computed(() => _capabilities.value.registrationEnabled)
  const passwordRecoveryEnabled = computed(() => _capabilities.value.passwordRecoveryEnabled)
  const ssoProviders = computed(() => _capabilities.value.ssoProviders)

  async function load(): Promise<void> {
    if (_loadPromise) return _loadPromise
    if (capabilitiesLoaded.value) return

    _loadPromise = _load()
    return _loadPromise
  }

  async function _load(): Promise<void> {
    if (isLoading.value) return

    isLoading.value = true
    error.value = null

    try {
      const response = await fetchPublicCapabilities()

      // Defensive normalization (backend may be old or incomplete)
      _capabilities.value = {
        registrationEnabled: response.registrationEnabled ?? false,
        passwordRecoveryEnabled: response.passwordRecoveryEnabled ?? false,
        ssoProviders: Array.isArray(response.ssoProviders) ? response.ssoProviders : [],
      }

      capabilitiesLoaded.value = true
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load capabilities'
      // Fail-closed: deny by default
      _capabilities.value = {
        registrationEnabled: false,
        passwordRecoveryEnabled: false,
        ssoProviders: [],
      }
      capabilitiesLoaded.value = true
    } finally {
      isLoading.value = false
      _loadPromise = null
    }
  }

  return {
    capabilitiesLoaded,
    error,
    isLoading,
    load,
    registrationEnabled,
    passwordRecoveryEnabled,
    ssoProviders,
  }
})
