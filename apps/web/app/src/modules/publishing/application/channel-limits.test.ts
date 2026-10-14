import { describe, expect, it } from 'vitest'
import { channelCharLimit } from './channel-limits'

describe('channelCharLimit', () => {
  it('returns 3000 for linkedin', () => {
    expect(channelCharLimit('linkedin')).toBe(3000)
  })

  it('returns 500 for threads', () => {
    expect(channelCharLimit('threads')).toBe(500)
  })

  it('is case-insensitive', () => {
    expect(channelCharLimit('LinkedIn')).toBe(3000)
  })

  it('falls back to 3000 for unknown providers', () => {
    expect(channelCharLimit('bluesky')).toBe(3000)
  })
})
