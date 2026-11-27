import type { ChangelogLocale } from '@profiletailors/shared-web'
import { getCollection, type CollectionEntry } from 'astro:content'
import { useTranslations } from '@i18n/utils'
import {
  buildChangelogFeed,
  buildChangelogRss,
  sortChangelogByDateDesc,
} from '@/lib/changelog'

async function loadFeedForLocale(site: URL | undefined, locale: ChangelogLocale) {
  const siteUrl = (site ?? new URL('https://profiletailors.com')).href
  const entries = await getCollection('changelog', ({ data }) => data.locale === locale)
  return buildChangelogFeed(
    entries.map((entry) => ({
      ...entry.data,
      body: entry.body ?? '',
    })),
    locale,
    siteUrl,
  )
}

export async function handleChangelogJsonRequest(
  site: URL | undefined,
  locale: ChangelogLocale,
): Promise<Response> {
  const feed = await loadFeedForLocale(site, locale)
  return new Response(`${JSON.stringify(feed, null, 2)}\n`, {
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
    },
  })
}

export async function handleChangelogRssRequest(
  site: URL | undefined,
  locale: ChangelogLocale,
): Promise<Response> {
  const feed = await loadFeedForLocale(site, locale)
  const t = useTranslations(locale)
  const xml = buildChangelogRss(feed, t.changelog.metaTitle, t.changelog.metaDescription)

  return new Response(xml, {
    headers: {
      'Content-Type': 'application/rss+xml; charset=utf-8',
    },
  })
}

export function buildChangelogStaticPaths(entries: readonly CollectionEntry<'changelog'>[]) {
  const sorted = sortChangelogByDateDesc(
    entries.map((entry) => ({ entry, date: entry.data.date, slug: entry.data.slug })),
  )

  return sorted.map((item, index) => ({
    params: { slug: item.slug },
    props: {
      entry: item.entry,
      nextEntry: index > 0 ? sorted[index - 1].entry : null,
      previousEntry: index < sorted.length - 1 ? sorted[index + 1].entry : null,
    },
  }))
}
