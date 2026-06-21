import { describe, expect, it } from 'vitest'
import { authCredentialsSchema, workspaceNameSchema } from './schemas'

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
      expect(result.error.flatten().fieldErrors.email).toContain('Please enter a valid email address.')
    }
  })

  it('rejects blank passwords after trimming', () => {
    const result = authCredentialsSchema.safeParse({
      email: 'user@example.com',
      password: '   ',
    })

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.flatten().fieldErrors.password).toContain('Please enter your password.')
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
      expect(result.error.issues[0]?.message).toBe('Please enter a workspace name.')
    }
  })

  it('rejects workspace names longer than 255 characters', () => {
    const result = workspaceNameSchema.safeParse('a'.repeat(256))

    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0]?.message).toBe('Workspace name must be 255 characters or fewer.')
    }
  })
})
