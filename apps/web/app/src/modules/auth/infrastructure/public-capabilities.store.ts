import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchPublicCapabilities } from '@modules/auth/infrastructure/auth-api'

export const usePublicCapabilitiesStore = defineStore('public-capabilities', () => {
  const _registrationEnabled = ref(false)
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const capabilityChecked = ref(false)
  const _loaded = ref(false)

  let _loadPromise: Promise<void> | null = null

  const registrationEnabled = computed(() => _registrationEnabled.value)

  async function load() {
    if (_loadPromise) return _loadPromise
    if (_loaded.value) return

    _loadPromise = _load()
    return _loadPromise
  }

  async function _load() {
    if (isLoading.value) return

    isLoading.value = true
    error.value = null

    try {
      const capabilities = await fetchPublicCapabilities()
      _registrationEnabled.value = capabilities.registrationEnabled
      _loaded.value = true
      capabilityChecked.value = true
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load capabilities'
      // Fail-closed: if the capability endpoint is unreachable, treat registration as disabled
      _registrationEnabled.value = false
      _loaded.value = true
      capabilityChecked.value = true
    } finally {
      isLoading.value = false
      _loadPromise = null
    }
  }

  return {
    capabilityChecked,
    error,
    isLoading,
    load,
    registrationEnabled,
  }
})
