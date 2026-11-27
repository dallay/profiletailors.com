import { describe, expect, it } from 'vitest'
import {
  CURRENT_CHANGELOG_FEED_VERSION,
  validateChangelogEntry,
  validateChangelogFeed,
} from './changelog'

const validEntry = {
  id: '2026-10-09-workspace-shortlinks',
  slug: '2026-10-09-workspace-shortlinks',
  locale: 'en' as const,
  date: '2026-10-09',
  version: 'v0.3.18',
  title: 'Workspace shortlinks',
  summary: 'Automatic URL shortening for new posts.',
  categories: ['feature' as const],
  image: 'https://profiletailors.com/changelog/shortlinks.svg',
  imageAlt: 'Shortlinks preview',
  url: 'https://profiletailors.com/changelog/2026-10-09-workspace-shortlinks/',
  sections: [
    {
      heading: 'Automatic shortening',
      paragraphs: ['Shortens distinct URLs on submission.'],
      bullets: ['Best-effort behavior with non-blocking warnings.'],
      link: { label: 'Read more', href: 'https://profiletailors.com/changelog/' },
    },
  ],
}

describe('changelog validation', () => {
  it('validates a well-formed changelog entry and feed', () => {
    expect(validateChangelogEntry(validEntry)).toEqual(validEntry)

    const feed = {
      version: CURRENT_CHANGELOG_FEED_VERSION,
      locale: 'en' as const,
      siteUrl: 'https://profiletailors.com/',
      generatedAt: '2026-10-09T12:00:00.000Z',
      entries: [validEntry],
    }
    expect(validateChangelogFeed(feed)).toEqual(feed)
  })

  it('rejects feeds with unsupported schema versions', () => {
    const result = validateChangelogFeed({
      version: 2,
      locale: 'en',
      siteUrl: 'https://profiletailors.com/',
      generatedAt: '2026-10-09T12:00:00.000Z',
      entries: [],
    })
    expect(result).toBeNull()
  })

  it('rejects entries with invalid date format or unsafe link schemes', () => {
    expect(
      validateChangelogEntry({
        ...validEntry,
        date: '10/09/2026',
      }),
    ).toBeNull()

    expect(
      validateChangelogEntry({
        ...validEntry,
        sections: [
          {
            heading: 'Unsafe',
            paragraphs: ['Test'],
            link: { label: 'Click', href: 'javascript:alert(1)' },
          },
        ],
      }),
    ).toBeNull()
  })
})
