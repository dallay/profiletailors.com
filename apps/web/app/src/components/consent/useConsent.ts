import { computed, type ComputedRef } from 'vue'
import { useConsentStore } from '@modules/settings/infrastructure/consent.store'
import type { ConsentReceipt, ConsentSource } from '@profiletailors/shared-web'

export type UseConsentResult = {
  analyticsEnabled: ComputedRef<boolean>
  hasValidConsent: ComputedRef<boolean>
  receipt: ComputedRef<ConsentReceipt | null>
  acceptAll: () => void
  rejectAll: () => void
  save: (analytics: boolean) => void
}

/**
 * Provides consent state and actions for a consent source.
 *
 * @param source - The source associated with saved consent, defaulting to `'banner'`
 * @returns The consent state and actions for accepting, rejecting, or saving analytics consent
 */
export function useConsent(source: ConsentSource = 'banner'): UseConsentResult {
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
