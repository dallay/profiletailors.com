import { describe, expect, it } from 'vitest'
import { validateChangelogFeed } from '@profiletailors/shared-web'
import {
  buildChangelogFeed,
  buildChangelogRss,
  formatChangelogDate,
  parseMarkdownSections,
  sortChangelogByDateDesc,
  type RawChangelogItem,
} from './changelog'

const sampleItems: RawChangelogItem[] = [
  {
    slug: '2026-09-15-calendar',
    locale: 'en',
    date: '2026-09-15',
    version: 'v0.3.6',
    title: 'Calendar workspace',
    summary: 'Plan LinkedIn content in one calendar.',
    categories: ['feature', 'security'],
    image: '/changelog/calendar-security.svg',
    imageAlt: 'Calendar preview',
    body: [
      '## Calendar-first view',
      '',
      'Plan posts across **week**, day, and month.',
      '',
      '- Create and reorder drafts.',
      '- Built for WCAG 2.2 AA.',
      '',
      '[Learn more](https://profiletailors.com/changelog/)',
    ].join('\n'),
  },
  {
    slug: '2026-10-09-shortlinks',
    locale: 'en',
    date: '2026-10-09',
    version: 'v0.3.18',
    title: 'Workspace shortlinks',
    summary: 'Automatic URL shortening.',
    categories: ['feature'],
    body: '## Automatic shortening\n\nDistinct URLs are shortened on submission.',
  },
  {
    slug: '2026-10-09-shortlinks',
    locale: 'es',
    date: '2026-10-09',
    version: 'v0.3.18',
    title: 'Enlaces cortos',
    summary: 'Acortado automático de URLs.',
    categories: ['feature'],
    body: '## Acortado automático\n\nLas URLs distintas se acortan al enviar.',
  },
]

describe('marketing changelog helpers', () => {
  it('parses markdown headings, paragraphs, bullets, and standalone links into sections', () => {
    const sections = parseMarkdownSections(sampleItems[0].body)
    expect(sections).toEqual([
      {
        heading: 'Calendar-first view',
        paragraphs: ['Plan posts across week, day, and month.'],
        bullets: ['Create and reorder drafts.', 'Built for WCAG 2.2 AA.'],
        link: {
          label: 'Learn more',
          href: 'https://profiletailors.com/changelog/',
        },
      },
    ])
  })

  it('builds a schema-valid JSON feed sorted newest-first per locale', () => {
    const feed = buildChangelogFeed(
      sampleItems,
      'en',
      'https://profiletailors.com/',
      '2026-10-10T00:00:00.000Z',
    )

    expect(validateChangelogFeed(feed)).toEqual(feed)
    expect(feed.entries.map((entry) => entry.slug)).toEqual([
      '2026-10-09-shortlinks',
      '2026-09-15-calendar',
    ])
    expect(feed.entries[1].image).toBe('https://profiletailors.com/changelog/calendar-security.svg')
  })

  it('builds valid RSS 2.0 XML for a locale feed', () => {
    const feed = buildChangelogFeed(
      sampleItems,
      'es',
      'https://profiletailors.com/',
      '2026-10-10T00:00:00.000Z',
    )
    const rss = buildChangelogRss(feed, 'Cambios — Profile Tailors', 'Novedades de producto')

    expect(rss).toContain('<?xml version="1.0" encoding="UTF-8"?>')
    expect(rss).toContain('<language>es</language>')
    expect(rss).toContain('<title>Enlaces cortos</title>')
    expect(rss).toContain('https://profiletailors.com/es/changelog/2026-10-09-shortlinks/')
  })

  it('formats dates and sorts deterministically', () => {
    expect(formatChangelogDate('2026-10-09', 'en')).toContain('2026')
    const sorted = sortChangelogByDateDesc([
      { date: '2026-09-01', slug: 'a' },
      { date: '2026-10-01', slug: 'b' },
    ])
    expect(sorted[0].slug).toBe('b')
  })
})
