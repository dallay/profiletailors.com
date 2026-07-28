import { describe, expect, it } from 'vitest'
import {
  authCredentialsSchema,
  forgotPasswordSchema,
  registerSchema,
  resetPasswordSchema,
  workspaceNameSchema,
} from './schemas'

describe('authCredentialsSchema', () => {
  it('trims and validates auth credentials', () => {
    const result = authCredentialsSchema.parse({
      email: '  user@example.com  ',
      password: '  password123  ',
    })

    expect(result).toEqual({
      email: 'user@example.com',
      password: 'password123',
    })
  })

  it('rejects invalid email addresses', () => {
    const result = authCredentialsSchema.safeParse({
      email: 'not-an-email',
      password: 'password123',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.email).toContain('invalidEmail')
    }
  })

  it('rejects blank passwords after trimming', () => {
    const result = authCredentialsSchema.safeParse({
      email: 'user@example.com',
      password: '   ',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.password).toContain('passwordRequired')
    }
  })
})

describe('password recovery schemas', () => {
  it('normalizes a valid recovery email', () => {
    expect(forgotPasswordSchema.parse({ email: '  User@Example.COM  ' })).toEqual({
      email: 'user@example.com',
    })
  })

  it.each(['', 'not-an-email'])('rejects invalid recovery email %j', (email) => {
    expect(forgotPasswordSchema.safeParse({ email }).success).toBe(false)
  })

  it.each([8, 128])('accepts a matching password at the %i character boundary', (length) => {
    const password = 'a'.repeat(length)
    expect(resetPasswordSchema.safeParse({ password, confirmPassword: password }).success).toBe(
      true,
    )
  })

  it.each(['', 'a'.repeat(7), 'a'.repeat(129)])('rejects invalid password length', (password) => {
    expect(resetPasswordSchema.safeParse({ password, confirmPassword: password }).success).toBe(
      false,
    )
  })

  it('rejects a mismatching confirmation', () => {
    const result = resetPasswordSchema.safeParse({
      password: 'password-one',
      confirmPassword: 'password-two',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.confirmPassword).toContain('passwordsMustMatch')
    }
  })
})

describe('workspaceNameSchema', () => {
  it('trims workspace names before submission', () => {
    const result = workspaceNameSchema.parse('  Studio PT  ')

    expect(result).toBe('Studio PT')
  })

  it('rejects blank workspace names after trimming', () => {
    const result = workspaceNameSchema.safeParse('   ')

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0]?.message).toBe('workspaceNameRequired')
    }
  })

  it('rejects workspace names longer than 255 characters', () => {
    const result = workspaceNameSchema.safeParse('a'.repeat(256))

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0]?.message).toBe('workspaceNameTooLong')
    }
  })
})

describe('registerSchema', () => {
  it('validates a correct registration payload', () => {
    const result = registerSchema.safeParse({
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'password123',
      confirmedAgeEligibility: true,
      acceptedTerms: true,
    })

    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data).toEqual({
        email: 'user@example.com',
        password: 'password123',
        confirmPassword: 'password123',
        confirmedAgeEligibility: true,
        acceptedTerms: true,
      })
    }
  })

  it('rejects registration if age eligibility is unchecked', () => {
    const result = registerSchema.safeParse({
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'password123',
      confirmedAgeEligibility: false,
      acceptedTerms: true,
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.confirmedAgeEligibility).toContain(
        'ageEligibilityRequired',
      )
    }
  })

  it('rejects registration if terms are not accepted', () => {
    const result = registerSchema.safeParse({
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'password123',
      confirmedAgeEligibility: true,
      acceptedTerms: false,
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.acceptedTerms).toContain('termsRequired')
    }
  })

  it('rejects registration if passwords do not match', () => {
    const result = registerSchema.safeParse({
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'mismatchingPassword',
      confirmedAgeEligibility: true,
      acceptedTerms: true,
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.confirmPassword).toContain('passwordsMustMatch')
    }
  })

  it('rejects registration if confirmPassword is empty', () => {
    const result = registerSchema.safeParse({
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: '',
      confirmedAgeEligibility: true,
      acceptedTerms: true,
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.confirmPassword).toContain(
        'confirmPasswordRequired',
      )
    }
  })
})
