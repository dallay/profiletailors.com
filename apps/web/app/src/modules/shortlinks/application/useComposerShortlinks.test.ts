import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useComposerShortlinks } from './useComposerShortlinks'

const { createShortlink } = vi.hoisted(() => ({ createShortlink: vi.fn() }))

vi.mock('../infrastructure/shortlinks-api', () => ({ createShortlink }))

describe('useComposerShortlinks', () => {
  beforeEach(() => {
    createShortlink.mockReset()
  })

  it('shortens each complete URL once and reuses successful results across retries', async () => {
    createShortlink.mockResolvedValue({ shortUrl: 'https://pt.link/1' })
    const composer = useComposerShortlinks()

    const first = await composer.shorten(
      'See https://example.com/path, then https://example.com/pathology',
    )
    const retried = await composer.shorten(
      'See https://example.com/path and https://example.com/pathology',
    )

    expect(first).toContain('https://pt.link/1,')
    expect(retried).toContain('https://pt.link/1')
    expect(createShortlink).toHaveBeenCalledTimes(2)
    expect(createShortlink).toHaveBeenCalledWith('https://example.com/pathology', {
      signal: expect.any(AbortSignal),
    })
  })

  it('reports failed source URLs and retries them on the next attempt', async () => {
    createShortlink
      .mockRejectedValueOnce(new Error('temporary'))
      .mockResolvedValueOnce({ shortUrl: 'https://pt.link/retried' })
    const composer = useComposerShortlinks()

    await composer.shorten('See https://example.com')
    expect(composer.shortlinkWarning.value).toBe(true)
    expect(composer.failedUrls.value).toEqual(['https://example.com'])
    expect(await composer.shorten('See https://example.com')).toBe('See https://pt.link/retried')
    expect(composer.shortlinkWarning.value).toBe(false)
    expect(createShortlink).toHaveBeenCalledTimes(2)
  })

  it('does not create links when shortening is disabled', async () => {
    const composer = useComposerShortlinks()

    await expect(composer.shorten('https://example.com', true)).resolves.toBe('https://example.com')
    expect(createShortlink).not.toHaveBeenCalled()
  })
})
