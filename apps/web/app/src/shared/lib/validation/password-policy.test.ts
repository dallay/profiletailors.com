import { describe, expect, it } from 'vitest'
import esAuth from '../../i18n/locales/es/auth'
import esPasswordRecovery from '../../i18n/locales/es/passwordRecovery'

describe('password policy copy (ES)', () => {
  it('auth password placeholder communicates a 12-character minimum', () => {
    expect(esAuth.passwordPlaceholder).toContain('12')
  })

  it('password recovery too-short message communicates a 12-character minimum', () => {
    expect(esPasswordRecovery.passwordTooShort).toContain('12')
  })

  it('password recovery policy hint communicates a 12-character minimum', () => {
    expect(esPasswordRecovery.passwordPolicy).toContain('12')
  })
})
