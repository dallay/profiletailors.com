import { describe, expect, it } from 'vitest'
import { extractDistinctUrls, replaceShortenedUrl } from './shortlink-post-content'

describe('extractDistinctUrls', () => {
  it('returns each URL once in first-seen order', () => {
    expect(
      extractDistinctUrls(
        'Read https://example.com, then https://other.test and https://example.com again.',
      ),
    ).toEqual(['https://example.com', 'https://other.test'])
  })

  it('returns no URLs for plain text', () => {
    expect(extractDistinctUrls('No links here')).toEqual([])
  })
})

describe('replaceShortenedUrl', () => {
  it('replaces every occurrence of the selected URL', () => {
    expect(
      replaceShortenedUrl(
        'Read https://example.com and https://example.com',
        'https://example.com',
        'https://pt.link/a',
      ),
    ).toBe('Read https://pt.link/a and https://pt.link/a')
  })

  it('does not replace a URL that is only a prefix of another URL', () => {
    expect(
      replaceShortenedUrl(
        'https://example.com/path and https://example.com/pathology',
        'https://example.com/path',
        'https://pt.link/a',
      ),
    ).toBe('https://pt.link/a and https://example.com/pathology')
  })

  it('preserves punctuation after replacing the complete URL', () => {
    expect(
      replaceShortenedUrl(
        'Read https://example.com/path, now',
        'https://example.com/path',
        'https://pt.link/a',
      ),
    ).toBe('Read https://pt.link/a, now')
  })

  it('leaves the post unchanged when the selected URL is absent', () => {
    expect(
      replaceShortenedUrl('No matching link', 'https://example.com', 'https://pt.link/a'),
    ).toBe('No matching link')
  })
})
