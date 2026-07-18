import { describe, it, expect } from 'vitest'
import { isValidEmail } from './waitlist-form-validator'

describe('isValidEmail', () => {
  it('accepts a normal-looking email', () => {
    expect(isValidEmail('user@example.com')).toBe(true)
  })

  it('rejects empty input', () => {
    expect(isValidEmail('')).toBe(false)
    expect(isValidEmail('   ')).toBe(false)
  })

  it('rejects missing @', () => {
    expect(isValidEmail('not-an-email')).toBe(false)
  })

  it('rejects missing domain dot', () => {
    expect(isValidEmail('user@example')).toBe(false)
  })

  it('rejects inputs over 320 chars', () => {
    const long = `${'a'.repeat(310)}@example.com`
    expect(isValidEmail(long)).toBe(false)
  })

  it('trims whitespace before validating', () => {
    expect(isValidEmail('  user@example.com  ')).toBe(true)
  })
})
