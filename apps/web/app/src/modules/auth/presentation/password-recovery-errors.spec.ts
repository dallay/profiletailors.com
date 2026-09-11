import { describe, expect, it } from 'vitest'
import { resolveRequestErrorKey, resolveResetTitleKey } from './password-recovery-errors'

describe('resolveRequestErrorKey', () => {
  it('maps rate limiting to the rate-limited key', () => {
    expect(resolveRequestErrorKey(429, undefined)).toBe('passwordRecovery.rateLimited')
    expect(resolveRequestErrorKey(undefined, 'AUTH_RATE_LIMIT_EXCEEDED')).toBe(
      'passwordRecovery.rateLimited',
    )
  })

  it('maps disabled recovery to the unavailable key', () => {
    expect(resolveRequestErrorKey(503, undefined)).toBe('passwordRecovery.unavailable')
    expect(resolveRequestErrorKey(undefined, 'PASSWORD_RECOVERY_DISABLED')).toBe(
      'passwordRecovery.unavailable',
    )
  })

  it('maps anything else to the generic key', () => {
    expect(resolveRequestErrorKey(500, undefined)).toBe('passwordRecovery.genericError')
    expect(resolveRequestErrorKey(undefined, undefined)).toBe('passwordRecovery.genericError')
  })
})

describe('resolveResetTitleKey', () => {
  it('resolves one title key per status', () => {
    expect(resolveResetTitleKey('success')).toBe('passwordRecovery.resetSuccessTitle')
    expect(resolveResetTitleKey('invalid')).toBe('passwordRecovery.invalidLinkTitle')
    expect(resolveResetTitleKey('form')).toBe('passwordRecovery.resetTitle')
  })
})
