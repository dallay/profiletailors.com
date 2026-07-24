import { describe, it, expect } from 'vitest'
import { consentReceiptSchema, validateConsentReceipt } from './consent'
import type { ConsentReceipt } from '../types/consent'

describe('consentReceiptSchema', () => {
  const validReceipt: ConsentReceipt = {
    consentVersion: 1,
    policyVersion: '2026-07-23',
    timestamp: '2026-07-23T10:00:00Z',
    region: 'EU',
    categories: {
      necessary: true,
      analytics: true,
    },
    dnt: false,
    source: 'banner',
  }

  it('validates a complete receipt', () => {
    const result = consentReceiptSchema.safeParse(validReceipt)
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data).toEqual(validReceipt)
    }
  })

  it('rejects wrong consentVersion', () => {
    const receipt = { ...validReceipt, consentVersion: 0 }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects invalid policyVersion format', () => {
    const receipt = { ...validReceipt, policyVersion: 'July 23, 2026' }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects non-ISO timestamp', () => {
    const receipt = { ...validReceipt, timestamp: '2026-07-23 10:00:00' }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects region code not 2 characters', () => {
    const receipt = { ...validReceipt, region: 'USA' }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects necessary not true', () => {
    const receipt = { ...validReceipt, categories: { necessary: false, analytics: true } }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects invalid source', () => {
    const receipt = { ...validReceipt, source: 'invalid' }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects a non-integer consentVersion', () => {
    const receipt = { ...validReceipt, consentVersion: 1.5 }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects a negative consentVersion', () => {
    const receipt = { ...validReceipt, consentVersion: -1 }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects a timestamp missing timezone information', () => {
    const receipt = { ...validReceipt, timestamp: '2026-07-23T10:00:00' }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects analytics as a non-boolean value', () => {
    const receipt = { ...validReceipt, categories: { necessary: true, analytics: 'true' } }
    const result = consentReceiptSchema.safeParse(receipt)
    expect(result.success).toBe(false)
  })

  it('rejects a receipt missing the dnt field', () => {
    const { dnt, ...receiptWithoutDnt } = validReceipt
    const result = consentReceiptSchema.safeParse(receiptWithoutDnt)
    expect(result.success).toBe(false)
  })
})

describe('validateConsentReceipt', () => {
  it('returns parsed receipt for valid input', () => {
    const receipt = {
      consentVersion: 1,
      policyVersion: '2026-07-23',
      timestamp: '2026-07-23T10:00:00Z',
      region: 'EU',
      categories: { necessary: true, analytics: false },
      dnt: true,
      source: 'settings',
    }
    expect(validateConsentReceipt(receipt)).toEqual(receipt)
  })

  it('returns null for invalid input', () => {
    const invalid = { consentVersion: 0 }
    expect(validateConsentReceipt(invalid)).toBeNull()
  })

  it('returns null for null input', () => {
    expect(validateConsentReceipt(null)).toBeNull()
  })

  it('returns null for non-object input', () => {
    expect(validateConsentReceipt('string')).toBeNull()
    expect(validateConsentReceipt(123)).toBeNull()
  })
})
