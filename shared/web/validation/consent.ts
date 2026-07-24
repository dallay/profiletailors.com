import { z } from 'zod'

/** Centralized consent contract constants — single source of truth */
export const EXPECTED_CONSENT_VERSION = 1 as const
export const EXPECTED_REGION = 'EU' as const
export const CONSENT_SOURCES = ['banner', 'settings-panel'] as const

export const consentReceiptSchema = z.object({
  consentVersion: z.literal(EXPECTED_CONSENT_VERSION),
  // ISO calendar date — rejects impossible dates like 2026-02-31
  policyVersion: z.iso.date(),
  timestamp: z.iso.datetime(),
  region: z.literal(EXPECTED_REGION),
  categories: z.object({
    necessary: z.literal(true),
    analytics: z.boolean(),
  }),
  dnt: z.boolean(),
  source: z.enum(CONSENT_SOURCES),
})

export type ConsentReceipt = z.infer<typeof consentReceiptSchema>

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