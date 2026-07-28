import { z } from 'zod'
import { CONSENT_SOURCES, CURRENT_CONSENT_VERSION, CURRENT_POLICY_VERSION } from '../types/consent'
import type { ConsentReceipt } from '../types/consent'

/** Centralized consent contract constants — single source of truth */
export const EXPECTED_CONSENT_VERSION = CURRENT_CONSENT_VERSION
export const EXPECTED_POLICY_VERSION = CURRENT_POLICY_VERSION
export const EXPECTED_REGION = 'EU' as const

export const consentReceiptSchema = z.object({
  consentVersion: z.literal(EXPECTED_CONSENT_VERSION),
  policyVersion: z.literal(EXPECTED_POLICY_VERSION),
  timestamp: z.iso.datetime(),
  region: z.literal(EXPECTED_REGION),
  categories: z.object({
    necessary: z.literal(true),
    analytics: z.boolean(),
  }),
  dnt: z.boolean(),
  source: z.enum(CONSENT_SOURCES),
})

export type { ConsentReceipt } from '../types/consent'

/**
 * Validate and parse a stored value.
 * Returns null if invalid (treating as no consent).
 */
export function validateConsentReceipt(raw: unknown): ConsentReceipt | null {
  try {
    return consentReceiptSchema.parse(raw)
  } catch {
    return null
  }
}
