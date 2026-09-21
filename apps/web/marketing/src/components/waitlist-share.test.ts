import { describe, expect, it, vi } from 'vitest'
import { shareWaitlist, waitlistShareUrl } from './waitlist-share'

describe('waitlistShareUrl', () => {
  it('uses the public https origin for English', () => {
    expect(waitlistShareUrl('en')).toBe('https://profiletailors.com/')
  })

  it('uses the public https origin for Spanish', () => {
    expect(waitlistShareUrl('es')).toBe('https://profiletailors.com/es/')
  })
})

describe('shareWaitlist', () => {
  it('prefers the platform share API', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    const writeText = vi.fn()
    const result = await shareWaitlist({
      url: 'https://profiletailors.com/',
      title: 'Profile Tailors waitlist',
      share,
      writeText,
    })
    expect(result).toBe('shared')
    expect(share).toHaveBeenCalledWith({
      title: 'Profile Tailors waitlist',
      url: 'https://profiletailors.com/',
    })
    expect(writeText).not.toHaveBeenCalled()
  })

  it('copies the URL when share is unavailable', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    const result = await shareWaitlist({
      url: 'https://profiletailors.com/',
      title: 'Profile Tailors waitlist',
      writeText,
    })
    expect(result).toBe('copied')
    expect(writeText).toHaveBeenCalledWith('https://profiletailors.com/')
  })

  it('copies the URL when share rejects with a non-abort error', async () => {
    const share = vi.fn().mockRejectedValue(new Error('share failed'))
    const writeText = vi.fn().mockResolvedValue(undefined)
    const result = await shareWaitlist({
      url: 'https://profiletailors.com/',
      title: 'Profile Tailors waitlist',
      share,
      writeText,
    })
    expect(result).toBe('copied')
    expect(writeText).toHaveBeenCalledWith('https://profiletailors.com/')
  })
})
