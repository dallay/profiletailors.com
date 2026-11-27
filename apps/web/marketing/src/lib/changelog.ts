import {
  CURRENT_CHANGELOG_FEED_VERSION,
  type ChangelogCategory,
  type ChangelogEntry,
  type ChangelogFeed,
  type ChangelogLink,
  type ChangelogLocale,
  type ChangelogSection,
} from '@profiletailors/shared-web'
import { useTranslations } from '@i18n/utils'

export interface RawChangelogItem {
  slug: string
  locale: ChangelogLocale
  date: string
  version?: string
  title: string
  summary: string
  categories: readonly ChangelogCategory[]
  image?: string
  imageAlt?: string
  body: string
}

const IMAGE_LINE_RE = /^!\[([^\]]*)\]\(([^)]+)\)$/
const STANDALONE_LINK_RE = /^\[([^\]]+)\]\(([^)]+)\)$/

function stripInlineMarkdown(text: string): string {
  return text
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1')
    .trim()
}

export function parseMarkdownSections(markdown: string, locale: ChangelogLocale): ChangelogSection[] {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const sections: ChangelogSection[] = []
  const fallbackHeading = useTranslations(locale).changelog.highlightsHeading

  let currentHeading = ''
  let paragraphs: string[] = []
  let bullets: string[] = []
  let image: string | undefined
  let imageAlt: string | undefined
  let link: ChangelogLink | undefined
  let paragraphBuffer: string[] = []

  const flushParagraph = () => {
    if (paragraphBuffer.length === 0) return
    const text = paragraphBuffer.join(' ').trim()
    paragraphBuffer = []
    if (!text) return
    const linkMatch = STANDALONE_LINK_RE.exec(text)
    if (linkMatch) {
      link = { label: linkMatch[1].trim(), href: linkMatch[2].trim() }
      return
    }
    paragraphs.push(stripInlineMarkdown(text))
  }

  const flushSection = () => {
    flushParagraph()
    if (
      !currentHeading &&
      paragraphs.length === 0 &&
      bullets.length === 0 &&
      !image &&
      !link
    ) {
      return
    }
    const section: ChangelogSection = {
      heading: currentHeading || fallbackHeading,
      paragraphs,
      ...(bullets.length > 0 ? { bullets } : {}),
      ...(image ? { image } : {}),
      ...(imageAlt ? { imageAlt } : {}),
      ...(link ? { link } : {}),
    }
    sections.push(section)
    currentHeading = ''
    paragraphs = []
    bullets = []
    image = undefined
    imageAlt = undefined
    link = undefined
  }

  for (const rawLine of lines) {
    const line = rawLine.trim()

    if (line.startsWith('## ') || line.startsWith('### ')) {
      flushSection()
      currentHeading = line.replace(/^#{2,3}\s+/, '').trim()
      continue
    }

    if (line === '') {
      flushParagraph()
      continue
    }

    const imageMatch = IMAGE_LINE_RE.exec(line)
    if (imageMatch) {
      flushParagraph()
      imageAlt = imageMatch[1].trim() || undefined
      image = imageMatch[2].trim()
      continue
    }

    if (line.startsWith('- ') || line.startsWith('* ')) {
      flushParagraph()
      bullets.push(stripInlineMarkdown(line.slice(2)))
      continue
    }

    paragraphBuffer.push(line)
  }

  flushSection()
  return sections
}

export function buildChangelogEntryPath(locale: ChangelogLocale, slug: string): string {
  return locale === 'es' ? `/es/changelog/${slug}/` : `/changelog/${slug}/`
}

export function buildChangelogIndexPath(locale: ChangelogLocale): string {
  return locale === 'es' ? '/es/changelog/' : '/changelog/'
}

export function toChangelogEntry(item: RawChangelogItem, siteUrl: string): ChangelogEntry {
  const path = buildChangelogEntryPath(item.locale, item.slug)
  const absoluteUrl = new URL(path, siteUrl).href
  const absoluteImage = item.image ? new URL(item.image, siteUrl).href : undefined

  return {
    id: item.slug,
    slug: item.slug,
    locale: item.locale,
    date: item.date,
    ...(item.version ? { version: item.version } : {}),
    title: item.title,
    summary: item.summary,
    categories: [...item.categories],
    ...(absoluteImage ? { image: absoluteImage } : {}),
    ...(item.imageAlt ? { imageAlt: item.imageAlt } : {}),
    url: absoluteUrl,
    sections: parseMarkdownSections(item.body, item.locale),
  }
}

export function sortChangelogByDateDesc<T extends { date: string; slug: string }>(
  items: readonly T[],
): T[] {
  return [...items].sort((a, b) => {
    const dateCompare = b.date.localeCompare(a.date)
    if (dateCompare !== 0) return dateCompare
    return b.slug.localeCompare(a.slug)
  })
}

export function buildChangelogFeed(
  items: readonly RawChangelogItem[],
  locale: ChangelogLocale,
  siteUrl: string,
  generatedAt: string = new Date().toISOString(),
): ChangelogFeed {
  const sorted = sortChangelogByDateDesc(items.filter((item) => item.locale === locale))
  return {
    version: CURRENT_CHANGELOG_FEED_VERSION,
    locale,
    siteUrl,
    generatedAt,
    entries: sorted.map((item) => toChangelogEntry(item, siteUrl)),
  }
}

export function formatChangelogDate(isoDate: string, locale: ChangelogLocale): string {
  const parsed = new Date(`${isoDate}T00:00:00Z`)
  return new Intl.DateTimeFormat(locale === 'es' ? 'es-ES' : 'en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(parsed)
}

export function formatShortChangelogDate(isoDate: string, locale: ChangelogLocale): string {
  const parsed = new Date(`${isoDate}T00:00:00Z`)
  return new Intl.DateTimeFormat(locale === 'es' ? 'es-ES' : 'en-US', {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(parsed)
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

export function buildChangelogRss(
  feed: ChangelogFeed,
  channelTitle: string,
  channelDescription: string,
): string {
  const indexUrl = new URL(buildChangelogIndexPath(feed.locale), feed.siteUrl).href
  const rssPath = feed.locale === 'es' ? '/es/changelog/rss.xml' : '/changelog/rss.xml'
  const selfUrl = new URL(rssPath, feed.siteUrl).href

  const itemsXml = feed.entries
    .map((entry) => {
      const pubDate = new Date(`${entry.date}T00:00:00Z`).toUTCString()
      const categoriesXml = entry.categories
        .map((cat) => `<category>${escapeXml(cat)}</category>`)
        .join('')
      return [
        '<item>',
        `<title>${escapeXml(entry.title)}</title>`,
        `<link>${escapeXml(entry.url)}</link>`,
        `<guid isPermaLink="true">${escapeXml(entry.url)}</guid>`,
        `<pubDate>${pubDate}</pubDate>`,
        `<description>${escapeXml(entry.summary)}</description>`,
        categoriesXml,
        '</item>',
      ].join('')
    })
    .join('\n')

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">',
    '<channel>',
    `<title>${escapeXml(channelTitle)}</title>`,
    `<link>${escapeXml(indexUrl)}</link>`,
    `<description>${escapeXml(channelDescription)}</description>`,
    `<language>${feed.locale}</language>`,
    `<atom:link href="${escapeXml(selfUrl)}" rel="self" type="application/rss+xml" />`,
    itemsXml,
    '</channel>',
    '</rss>',
  ].join('\n')
}
