import { describe, it, expect } from 'vitest'

describe('Vitest sanity check', () => {
  it('should pass a basic assertion', () => {
    expect(typeof describe).toBe('function')
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
