import { z, defineCollection } from 'astro:content'
import { glob } from 'astro/loaders'

const legal = defineCollection({
  loader: glob({ pattern: '**/*.md', base: './src/content/legal' }),
  schema: z.object({
    title: z.string(),
    description: z.string(),
    lastUpdated: z.string(),
    locale: z.enum(['en', 'es']),
    order: z.number(),
  }),
})

export const collections = { legal }
