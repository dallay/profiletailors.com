export type ChangelogLocale = 'en' | 'es'

export type ChangelogCategory = 'feature' | 'improvement' | 'fix' | 'security'

export interface ChangelogLink {
  label: string
  href: string
}

export interface ChangelogSection {
  heading: string
  paragraphs: readonly string[]
  bullets?: readonly string[]
  image?: string
  imageAlt?: string
  link?: ChangelogLink
}

export interface ChangelogEntry {
  id: string
  slug: string
  locale: ChangelogLocale
  date: string
  version?: string
  title: string
  summary: string
  categories: readonly ChangelogCategory[]
  image?: string
  imageAlt?: string
  url: string
  sections: readonly ChangelogSection[]
}

export interface ChangelogFeed {
  version: 1
  locale: ChangelogLocale
  siteUrl: string
  generatedAt: string
  entries: readonly ChangelogEntry[]
}
