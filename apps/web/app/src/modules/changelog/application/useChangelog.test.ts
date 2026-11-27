import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { CHANGELOG_LAST_SEEN_KEY, CURRENT_CHANGELOG_FEED_VERSION } from '@profiletailors/shared-web'
import { buildChangelogFeedUrl, normalizeEntryUrls, useChangelog } from './useChangelog'

const mockLocale = ref('en')

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    locale: mockLocale,
  }),
}))

const sampleFeed = {
  version: CURRENT_CHANGELOG_FEED_VERSION,
  locale: 'en' as const,
  siteUrl: 'https://profiletailors.com/',
  generatedAt: '2026-10-10T00:00:00.000Z',
  entries: [
    {
      id: '2026-10-09-shortlinks',
      slug: '2026-10-09-shortlinks',
      locale: 'en' as const,
      date: '2026-10-09',
      version: 'v0.3.18',
      title: 'Workspace shortlinks',
      summary: 'Automatic URL shortening.',
      categories: ['feature' as const],
      url: 'https://profiletailors.com/changelog/2026-10-09-shortlinks/',
      sections: [
        {
          heading: 'Shortening',
          paragraphs: ['Distinct URLs are shortened automatically.'],
        },
      ],
    },
    {
      id: '2026-10-02-threads',
      slug: '2026-10-02-threads',
      locale: 'en' as const,
      date: '2026-10-02',
      version: 'v0.3.14',
      title: 'Threads integration',
      summary: 'Connect Threads alongside LinkedIn.',
      categories: ['feature' as const],
      url: 'https://profiletailors.com/changelog/2026-10-02-threads/',
      sections: [],
    },
  ],
}

describe('useChangelog', () => {
  beforeEach(() => {
    localStorage.clear()
    mockLocale.value = 'en'
  })

  it('builds localized feed URLs cleanly', () => {
    expect(buildChangelogFeedUrl('https://profiletailors.com/', 'en')).toBe(
      'https://profiletailors.com/changelog.json',
    )
    expect(buildChangelogFeedUrl('https://profiletailors.com', 'es')).toBe(
      'https://profiletailors.com/es/changelog.json',
    )
  })

  it('loads validated entries, tracks unread count, and marks as read', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue({
      ok: true,
      json: async () => sampleFeed,
    } as Response)

    const changelog = useChangelog({
      marketingOrigin: 'https://profiletailors.com',
      fetcher,
      autoFetch: false,
    })

    await changelog.refresh()

    expect(changelog.status.value).toBe('ready')
    expect(changelog.entries.value).toHaveLength(2)
    expect(changelog.activeEntry.value?.id).toBe('2026-10-09-shortlinks')
    expect(changelog.unreadCount.value).toBe(2)

    changelog.markAsRead()
    expect(changelog.unreadCount.value).toBe(0)
    expect(localStorage.getItem(CHANGELOG_LAST_SEEN_KEY)).toBe('2026-10-09-shortlinks')

    changelog.selectPrevious()
    expect(changelog.activeEntry.value?.id).toBe('2026-10-02-threads')
    expect(changelog.hasNext.value).toBe(true)
    expect(changelog.hasPrevious.value).toBe(false)

    changelog.selectNext()
    expect(changelog.activeEntry.value?.id).toBe('2026-10-09-shortlinks')
  })

  it('sets error state on HTTP failures or invalid payloads', async () => {
    const invalidFetcher = vi.fn<typeof fetch>().mockResolvedValue({
      ok: true,
      json: async () => ({ version: 99, entries: [] }),
    } as Response)

    const changelog = useChangelog({
      marketingOrigin: 'https://profiletailors.com',
      fetcher: invalidFetcher,
      autoFetch: false,
    })

    await changelog.refresh()
    expect(changelog.status.value).toBe('error')
  })

  it.each(['http://localhost:4323', 'https://profiletailors.com'])(
    'resolves feed section links against %s without changing external links',
    async (marketingOrigin) => {
      const section = sampleFeed.entries[0].sections[0]
      const hrefs = [
        '/docs/shortlinks?source=changelog#setup',
        'https://profiletailors.com/docs/shortlinks',
        'https://docs.example.com/shortlinks',
        'https://profiletailors.com.example.com/docs/shortlinks',
        '//docs.example.com/shortlinks',
      ]
      const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
        new Response(
          JSON.stringify({
            ...sampleFeed,
            entries: [
              {
                ...sampleFeed.entries[0],
                sections: hrefs.map((href) => ({ ...section, link: { href, label: 'Read more' } })),
              },
            ],
          }),
        ),
      )
      const changelog = useChangelog({ marketingOrigin, fetcher, autoFetch: false })

      await changelog.refresh()

      expect(changelog.activeEntry.value?.sections.map((item) => item.link?.href)).toEqual([
        `${marketingOrigin}/docs/shortlinks?source=changelog#setup`,
        `${marketingOrigin}/docs/shortlinks`,
        ...hrefs.slice(2),
      ])
    },
  )

  it('preserves external entry assets whose hosts only start with the feed host', () => {
    const image = 'https://profiletailors.com.example.com/shortlinks.svg'
    const rewritten = normalizeEntryUrls(
      [{ ...sampleFeed.entries[0], image }],
      sampleFeed.siteUrl,
      'http://localhost:4323',
    )

    expect(rewritten[0].image).toBe(image)
  })

  it('rewrites feed entry image and permalink URLs to the configured marketing origin', () => {
    const rewritten = normalizeEntryUrls(
      [
        {
          ...sampleFeed.entries[0],
          image: 'https://profiletailors.com/changelog/shortlinks.svg',
        },
      ],
      'https://profiletailors.com/',
      'http://localhost:4323',
    )

    expect(rewritten[0].image).toBe('http://localhost:4323/changelog/shortlinks.svg')
    expect(rewritten[0].url).toBe('http://localhost:4323/changelog/2026-10-09-shortlinks/')
  })
})
