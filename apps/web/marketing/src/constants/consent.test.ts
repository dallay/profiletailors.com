import { describe, it, expect } from 'vitest'
import { CURRENT_CONSENT_VERSION, CURRENT_POLICY_VERSION, PT_CONSENT_KEY } from './consent'

describe('consent constants', () => {
  it('exports CURRENT_CONSENT_VERSION correctly', () => {
    expect(CURRENT_CONSENT_VERSION).toBe(1)
  })

  it('exports CURRENT_POLICY_VERSION correctly', () => {
    expect(CURRENT_POLICY_VERSION).toBe('2026-07-23')
  })

  it('exports PT_CONSENT_KEY correctly', () => {
    expect(PT_CONSENT_KEY).toBe('pt-consent')
  })
})
