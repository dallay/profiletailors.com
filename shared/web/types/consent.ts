/** Current system version — must match spec */
export const CURRENT_CONSENT_VERSION = 1

/** Privacy policy date — update when policy changes */
export const CURRENT_POLICY_VERSION = '2026-07-23'

export const CONSENT_CATEGORIES = ['necessary', 'analytics'] as const
export const CONSENT_SOURCES = ['banner', 'settings-panel'] as const

export type ConsentCategory = (typeof CONSENT_CATEGORIES)[number]
export type ConsentSource = (typeof CONSENT_SOURCES)[number]

export interface ConsentCategories {
  necessary: true
  analytics: boolean
}

export interface ConsentReceipt {
  consentVersion: typeof CURRENT_CONSENT_VERSION
  policyVersion: typeof CURRENT_POLICY_VERSION
  timestamp: string
  region: 'EU'
  categories: ConsentCategories
  dnt: boolean
  source: ConsentSource
}

/** localStorage key */
export const CONSENT_STORAGE_KEY = 'pt-consent'

/** Window global set by inline script */
export const ANALYTICS_FLAG = '__PT_CONSENT_ANALYTICS'
