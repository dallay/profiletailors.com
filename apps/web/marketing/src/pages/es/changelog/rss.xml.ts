import type { APIRoute } from 'astro'
import { getCollection } from 'astro:content'
import { buildChangelogFeed, buildChangelogRss } from '@/lib/changelog'
import { useTranslations } from '@i18n/utils'

export const GET: APIRoute = async ({ site }) => {
  const siteUrl = (site ?? new URL('https://profiletailors.com')).href
  const entries = await getCollection('changelog', ({ data }) => data.locale === 'es')
  const feed = buildChangelogFeed(
    entries.map((entry) => ({
      ...entry.data,
      body: entry.body ?? '',
    })),
    'es',
    siteUrl,
  )
  const t = useTranslations('es')
  const xml = buildChangelogRss(feed, t.changelog.metaTitle, t.changelog.metaDescription)

  return new Response(xml, {
    headers: {
      'Content-Type': 'application/rss+xml; charset=utf-8',
    },
  })
}
