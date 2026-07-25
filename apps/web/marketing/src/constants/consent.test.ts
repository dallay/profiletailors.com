import { describe, it, expect } from 'vitest'
import { CURRENT_CONSENT_VERSION, CURRENT_POLICY_VERSION, PT_CONSENT_KEY } from './consent'

describe('consent constants', () => {
  it('defines the current consent version as a positive integer', () => {
    expect(CURRENT_CONSENT_VERSION).toBe(1)
    expect(Number.isInteger(CURRENT_CONSENT_VERSION)).toBe(true)
    expect(CURRENT_CONSENT_VERSION).toBeGreaterThan(0)
  })

  it('defines the policy version in YYYY-MM-DD format', () => {
    expect(CURRENT_POLICY_VERSION).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('defines the localStorage key used for the consent receipt', () => {
    expect(PT_CONSENT_KEY).toBe('pt-consent')
    expect(typeof PT_CONSENT_KEY).toBe('string')
  })

  it('exposes distinct, non-empty values for every constant', () => {
    const values = [CURRENT_CONSENT_VERSION, CURRENT_POLICY_VERSION, PT_CONSENT_KEY]
    values.forEach((value) => {
      expect(value).toBeDefined()
      expect(value).not.toBe('')
    })
  })
})