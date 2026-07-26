import { computed } from 'vue'
import { useConsentStore } from '@modules/settings/infrastructure/consent.store'

export function useConsent() {
  const store = useConsentStore()

  const analyticsEnabled = computed(() => store.analyticsEnabled)
  const hasValidConsent = computed(() => store.hasValidConsent)
  const receipt = computed(() => store.receipt)

  function acceptAll() {
    store.saveConsent({ analytics: true, source: 'banner' })
  }

  function rejectAll() {
    store.saveConsent({ analytics: false, source: 'banner' })
  }

  function save(analytics: boolean) {
    store.saveConsent({ analytics, source: 'banner' })
  }

  function openSettings() {
    store.openSettings()
  }

  return {
    analyticsEnabled,
    hasValidConsent,
    receipt,
    acceptAll,
    rejectAll,
    save,
    openSettings,
  }
}
