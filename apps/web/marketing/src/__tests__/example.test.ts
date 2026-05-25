import { describe, it, expect } from 'vitest'

describe('Vitest sanity check', () => {
  it('should pass a basic assertion', () => {
    expect(1 + 1).toBe(2)
  })

  it('should handle string operations', () => {
    const greeting = 'Profile Tailors'
    expect(greeting).toContain('Profile')
    expect(greeting.toLowerCase()).toBe('profile tailors')
  })

  it('should work with arrays', () => {
    const platforms = ['Twitter', 'LinkedIn', 'Instagram']
    expect(platforms).toHaveLength(3)
    expect(platforms).toContain('LinkedIn')
  })
})
