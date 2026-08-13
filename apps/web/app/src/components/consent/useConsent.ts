import { computed } from 'vue'
import { useConsentStore } from '@modules/settings/infrastructure/consent.store'
import type { ConsentSource } from '@profiletailors/shared-web'

/**
 * Provides consent state and actions for a consent source.
 *
 * @param source - The source associated with saved consent, defaulting to `'banner'`
 * @returns The consent state and actions for accepting, rejecting, or saving analytics consent
 */
export function useConsent(source: ConsentSource = 'banner') {
  const store = useConsentStore()

  const analyticsEnabled = computed(() => store.analyticsEnabled)
  const hasValidConsent = computed(() => store.hasValidConsent)
  const receipt = computed(() => store.receipt)

  function acceptAll() {
    store.saveConsent({ analytics: true, source })
  }

  function rejectAll() {
    store.saveConsent({ analytics: false, source })
  }

  function save(analytics: boolean) {
    store.saveConsent({ analytics, source })
  }

  return {
    analyticsEnabled,
    hasValidConsent,
    receipt,
    acceptAll,
    rejectAll,
    save,
  }
}
