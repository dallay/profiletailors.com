import type { APIRoute } from 'astro'
import { getCollection } from 'astro:content'
import { buildChangelogFeed } from '@/lib/changelog'

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

  return new Response(`${JSON.stringify(feed, null, 2)}\n`, {
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
    },
  })
}
