import { validateConsentReceipt, type ConsentReceipt } from '../validation/consent'
import { CONSENT_STORAGE_KEY } from '../types/consent'

/**
 * Load consent receipt from localStorage.
 * Returns null if not found, invalid, or error occurs.
 */
export function loadConsent(): ConsentReceipt | null {
  try {
    const raw = localStorage.getItem(CONSENT_STORAGE_KEY)
    if (!raw) {
      return null
    }

    const parsed: unknown = JSON.parse(raw)
    return validateConsentReceipt(parsed)
  } catch {
    return null
  }
}

/**
 * Save consent receipt to localStorage.
 * Handles errors gracefully (silent fail).
 */
export function saveConsent(receipt: ConsentReceipt): void {
  try {
    localStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify(receipt))
  } catch (error) {
    console.error('[Consent] Failed to save consent:', error)
  }
}

/**
 * Clear consent receipt from localStorage.
 * Handles errors gracefully (silent fail).
 */
export function clearConsent(): void {
  try {
    localStorage.removeItem(CONSENT_STORAGE_KEY)
  } catch (error) {
    console.error('[Consent] Failed to clear consent:', error)
  }
}
