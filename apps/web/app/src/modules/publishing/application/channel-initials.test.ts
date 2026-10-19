import { describe, expect, it } from 'vitest'
import { channelInitials } from './channel-initials'

describe('channelInitials', () => {
  it('returns PT when the name is missing', () => {
    expect(channelInitials(null)).toBe('PT')
    expect(channelInitials(undefined)).toBe('PT')
    expect(channelInitials('')).toBe('PT')
    expect(channelInitials('   ')).toBe('PT')
  })

  it('returns the first two letters of a single word uppercased', () => {
    expect(channelInitials('Ana')).toBe('AN')
  })

  it('returns the first letters of the first two words uppercased', () => {
    expect(channelInitials('Profile Tailors LinkedIn')).toBe('PT')
    expect(channelInitials('WireMock Dev User')).toBe('WD')
  })

  it('returns PT when the single word resolves to empty after trim', () => {
    expect(channelInitials('  ')).toBe('PT')
  })
})
