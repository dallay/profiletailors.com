import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { toast } from 'vue-sonner'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import {
  type ConsentReceipt,
  CONSENT_STORAGE_KEY,
  CURRENT_CONSENT_VERSION,
  CURRENT_POLICY_VERSION,
  validateConsentReceipt,
} from '@profiletailors/shared-web'

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const useConsentStore = defineStore('consent', () => {
  // ── State ──────────────────────────────────────────────────────────────
  const receipt = ref<ConsentReceipt | null>(null)
  const forceOpen = ref(false)
  const syncError = ref<string | null>(null)

  // ── Getters ────────────────────────────────────────────────────────────
  const hasValidConsent = computed(() => {
    if (!receipt.value) return false
    return receipt.value.consentVersion === CURRENT_CONSENT_VERSION
  })

  const analyticsEnabled = computed(() => {
    return receipt.value?.categories.analytics ?? false
  })

  // ── Actions ─────────────────────────────────────────────────────────────

  /** Load consent receipt from localStorage. Re-reads and validates. */
  function loadFromStorage(): void {
    try {
      const raw = localStorage.getItem(CONSENT_STORAGE_KEY)
      if (!raw) {
        receipt.value = null
        return
      }
      receipt.value = validateConsentReceipt(JSON.parse(raw))
    } catch {
      receipt.value = null
    }
  }

  /**
   * Save consent to localStorage and optionally sync to backend.
   *
   * @param params.analytics - Whether analytics consent is granted
   * @param params.source - Origin: 'banner' or 'settings-panel'
   */
  function saveConsent(params: { analytics: boolean; source: 'banner' | 'settings-panel' }): void {
    const newReceipt: ConsentReceipt = {
      consentVersion: CURRENT_CONSENT_VERSION,
      policyVersion: CURRENT_POLICY_VERSION,
      timestamp: new Date().toISOString(),
      region: 'EU',
      categories: {
        necessary: true,
        analytics: params.analytics,
      },
      dnt: detectDNTSignal(),
      source: params.source,
    }

    // Persist to localStorage
    try {
      localStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify(newReceipt))
    } catch (err) {
      console.error('[Consent] Failed to save to localStorage:', err)
    }
    receipt.value = newReceipt
    syncError.value = null

    // Backend sync for authenticated users
    if (userIsAuthenticated()) {
      syncToBackend(newReceipt).catch((err: unknown) => {
        syncError.value = 'Consent saved locally, sync failed.'
        toast.error('Consent saved locally, sync failed.')
        console.error('[Consent] Backend sync failed:', err)
      })
    }
  }

  /** Sync consent receipt to backend governance API. */
  async function syncToBackend(receipt: ConsentReceipt): Promise<void> {
    const auth = useAuthStore()

    await auth.apiFetch('/api/governance/consent', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subjectReference: { user: auth.user?.principalId },
        purpose: 'web.analytics',
        granted: receipt.categories.analytics,
        timestamp: receipt.timestamp,
      }),
      workspaceScoped: true,
    })
  }

  /** Open consent settings (force banner visibility). */
  function openSettings(): void {
    forceOpen.value = true
  }

  /** Close consent settings. */
  function closeSettings(): void {
    forceOpen.value = false
  }

  // Initialize from storage
  loadFromStorage()

  return {
    // State
    receipt,
    forceOpen,
    syncError,
    // Getters
    hasValidConsent,
    analyticsEnabled,
    // Actions
    loadFromStorage,
    saveConsent,
    syncToBackend,
    openSettings,
    closeSettings,
  }
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Detect DNT or GPC browser privacy signals. SSR-safe. */
function detectDNTSignal(): boolean {
  if (typeof navigator === 'undefined') return false
  return (
    (navigator as Navigator & { globalPrivacyControl?: boolean }).doNotTrack === '1' ||
    (navigator as Navigator & { globalPrivacyControl?: boolean }).doNotTrack === 'yes' ||
    (window as { doNotTrack?: string }).doNotTrack === '1' ||
    (navigator as Navigator & { globalPrivacyControl?: boolean }).globalPrivacyControl === true
  )
}

/** Check if the user is authenticated (has user principal). */
function userIsAuthenticated(): boolean {
  try {
    const auth = useAuthStore()
    return !!auth.user?.principalId
  } catch {
    return false
  }
}
