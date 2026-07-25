/**
 * Consent receipt stored in localStorage.
 * Must be validated on read; invalid receipts = no consent.
 */
export interface ConsentReceipt {
  /** Material version: increment when purposes/categories change */
  consentVersion: number
  /** Privacy policy date: YYYY-MM-DD */
  policyVersion: string
  /** ISO 8601 timestamp of consent grant */
  timestamp: string
  /** Region code: 'EU' for MVP (over-compliance) */
  region: string
  /** Category choices */
  categories: {
    /** Always true — required for basic functionality */
    necessary: true
    /** Opt-in analytics consent */
    analytics: boolean
  }
  /** DNT or GPC was active at consent time */
  dnt: boolean
  /** How consent was given */
  source: 'banner' | 'settings'
}

/** Category names for consent */
export type ConsentCategory = 'necessary' | 'analytics'

/** Current system version — must match spec */
export const CURRENT_CONSENT_VERSION = 1

/** Privacy policy date — update when policy changes */
export const CURRENT_POLICY_VERSION = '2026-07-23'

/** localStorage key */
export const CONSENT_STORAGE_KEY = 'pt-consent'

/** Window global set by inline script */
export const ANALYTICS_FLAG = '__PT_CONSENT_ANALYTICS'
