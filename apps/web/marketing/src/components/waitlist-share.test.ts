import { describe, expect, it, vi } from 'vitest'
import { readWaitlistShareAttributes, shareWaitlist, waitlistShareUrl } from './waitlist-share'

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

describe('readWaitlistShareAttributes', () => {
  it('reads localized share attributes from the form', () => {
    document.body.innerHTML = `
      <form
        data-waitlist-share-url="https://profiletailors.com/es/"
        data-waitlist-share-title="Lista de espera de Profile Tailors"
        data-waitlist-share-copied="Enlace copiado"
      ></form>
    `
    const form = document.querySelector('form')
    if (!form) {
      throw new Error('form missing')
    }
    expect(readWaitlistShareAttributes(form)).toEqual({
      url: 'https://profiletailors.com/es/',
      title: 'Lista de espera de Profile Tailors',
      copied: 'Enlace copiado',
    })
  })

  it('rejects missing localized share attributes', () => {
    document.body.innerHTML = '<form></form>'
    const form = document.querySelector('form')
    if (!form) {
      throw new Error('form missing')
    }
    expect(() => readWaitlistShareAttributes(form)).toThrow('waitlist-share-attributes-missing')
  })
})
