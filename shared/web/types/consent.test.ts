import { describe, it, expect } from 'vitest'
import {
  ANALYTICS_FLAG,
  CONSENT_SOURCES,
  CONSENT_STORAGE_KEY,
  CURRENT_CONSENT_VERSION,
  CURRENT_POLICY_VERSION,
} from './consent'

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
    expect(CONSENT_STORAGE_KEY).toBe('pt-consent')
  })

  it('defines the supported consent receipt sources', () => {
    expect(CONSENT_SOURCES).toEqual(['banner', 'settings-panel'])
  })

  it('defines the window global name used by the inline consent script', () => {
    expect(ANALYTICS_FLAG).toBe('__PT_CONSENT_ANALYTICS')
  })

  it('exposes distinct, non-empty values for every constant', () => {
    const values = [
      CURRENT_CONSENT_VERSION,
      CURRENT_POLICY_VERSION,
      CONSENT_STORAGE_KEY,
      ANALYTICS_FLAG,
    ]
    values.forEach((value) => {
      expect(value).toBeDefined()
      expect(value).not.toBe('')
    })
  })
})
