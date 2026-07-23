import { z } from 'zod'

export const consentReceiptSchema = z.object({
  consentVersion: z.number().int().min(1),
  policyVersion: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Must be YYYY-MM-DD'),
  timestamp: z.string().datetime(),
  region: z.string().length(2),
  categories: z.object({
    necessary: z.literal(true),
    analytics: z.boolean(),
  }),
  dnt: z.boolean(),
  source: z.enum(['banner', 'settings']),
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
