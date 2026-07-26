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
