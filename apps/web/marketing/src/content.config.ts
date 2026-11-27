import { defineCollection } from 'astro:content'
import { glob } from 'astro/loaders'
import { z } from 'astro/zod'

const changelog = defineCollection({
  loader: glob({
    pattern: '**/*.md',
    base: './src/content/changelog',
    generateId: ({ entry }) => entry.replace(/\.md$/, ''),
  }),
  schema: z.object({
    slug: z.string().min(1),
    locale: z.enum(['en', 'es']),
    date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
    version: z.string().min(1).optional(),
    title: z.string().min(1),
    summary: z.string().min(1),
    categories: z.array(z.enum(['feature', 'improvement', 'fix', 'security'])).min(1),
    image: z.string().min(1).optional(),
    imageAlt: z.string().min(1).optional(),
  }),
})

export const collections = { changelog }
