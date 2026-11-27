import { z } from 'zod'
import type { ChangelogEntry, ChangelogFeed } from '../types/changelog'

export const CURRENT_CHANGELOG_FEED_VERSION = 1 as const

export const changelogLocaleSchema = z.enum(['en', 'es'])

export const changelogCategorySchema = z.enum(['feature', 'improvement', 'fix', 'security'])

export const changelogLinkSchema = z.object({
  label: z.string().min(1),
  href: z
    .string()
    .min(1)
    .refine(
      (value) =>
        value.startsWith('https://') || value.startsWith('http://') || value.startsWith('/'),
      { message: 'Link href must be an absolute HTTP(S) URL or root-relative path' },
    ),
})

export const changelogSectionSchema = z.object({
  heading: z.string().min(1),
  paragraphs: z.array(z.string().min(1)),
  bullets: z.array(z.string().min(1)).optional(),
  image: z.string().min(1).optional(),
  imageAlt: z.string().min(1).optional(),
  link: changelogLinkSchema.optional(),
})

export const changelogEntrySchema = z.object({
  id: z.string().min(1),
  slug: z.string().min(1),
  locale: changelogLocaleSchema,
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Date must use YYYY-MM-DD format'),
  version: z.string().min(1).optional(),
  title: z.string().min(1),
  summary: z.string().min(1),
  categories: z.array(changelogCategorySchema).min(1),
  image: z.string().min(1).optional(),
  imageAlt: z.string().min(1).optional(),
  url: z.string().min(1),
  sections: z.array(changelogSectionSchema),
})

export const changelogFeedSchema = z.object({
  version: z.literal(CURRENT_CHANGELOG_FEED_VERSION),
  locale: changelogLocaleSchema,
  siteUrl: z.string().min(1),
  generatedAt: z.string().min(1),
  entries: z.array(changelogEntrySchema),
})

export function validateChangelogEntry(input: unknown): ChangelogEntry | null {
  const result = changelogEntrySchema.safeParse(input)
  if (!result.success) {
    return null
  }
  return result.data
}

export function validateChangelogFeed(input: unknown): ChangelogFeed | null {
  const result = changelogFeedSchema.safeParse(input)
  if (!result.success) {
    return null
  }
  return result.data
}
