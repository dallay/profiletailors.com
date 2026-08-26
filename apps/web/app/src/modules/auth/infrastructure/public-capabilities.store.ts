import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  PublicCapabilities,
  PublicCapabilitiesDto,
} from '@modules/auth/domain/public-capabilities'
import { fetchPublicCapabilities } from '@modules/auth/infrastructure/auth-api'

const CLOSED_CAPABILITIES: PublicCapabilities = {
  registrationEnabled: false,
  passwordRecoveryEnabled: false,
  invitationAcceptanceEnabled: false,
}

function normalizeCapabilities(response: PublicCapabilitiesDto): PublicCapabilities {
  return {
    registrationEnabled: response.registrationEnabled === true,
    passwordRecoveryEnabled: response.passwordRecoveryEnabled === true,
    invitationAcceptanceEnabled: response.invitationAcceptanceEnabled === true,
  }
}

export const usePublicCapabilitiesStore = defineStore('public-capabilities', () => {
  const capabilities = ref<PublicCapabilities>({ ...CLOSED_CAPABILITIES })
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const resolved = ref(false)
  let loadPromise: Promise<void> | null = null

  const registrationEnabled = computed(() => capabilities.value.registrationEnabled)
  const passwordRecoveryEnabled = computed(() => capabilities.value.passwordRecoveryEnabled)
  const invitationAcceptanceEnabled = computed(() => capabilities.value.invitationAcceptanceEnabled)

  function load(): Promise<void> {
    if (loadPromise) return loadPromise
    if (resolved.value) return Promise.resolve()

    isLoading.value = true
    error.value = null
    loadPromise = fetchPublicCapabilities()
      .then((response) => {
        capabilities.value = normalizeCapabilities(response)
      })
      .catch((cause: unknown) => {
        capabilities.value = { ...CLOSED_CAPABILITIES }
        error.value = cause instanceof Error ? cause.message : 'Failed to load capabilities'
      })
      .finally(() => {
        resolved.value = true
        isLoading.value = false
        loadPromise = null
      })

    return loadPromise
  }

  function retry(): Promise<void> {
    resolved.value = false
    return load()
  }

  return {
    capabilitiesLoaded: resolved,
    error,
    invitationAcceptanceEnabled,
    isLoading,
    load,
    passwordRecoveryEnabled,
    registrationEnabled,
    resolved,
    retry,
  }
})
