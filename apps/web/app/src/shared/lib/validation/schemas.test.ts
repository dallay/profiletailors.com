import { describe, expect, it } from 'vitest'
import { authCredentialsSchema, registerSchema, workspaceNameSchema } from './schemas'

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
    })

    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data).toEqual({
        email: 'user@example.com',
        password: 'password123',
        confirmPassword: 'password123',
      })
    }
  })

  it('rejects registration if passwords do not match', () => {
    const result = registerSchema.safeParse({
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'mismatchingPassword',
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
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.confirmPassword).toContain(
        'confirmPasswordRequired',
      )
    }
  })
})
