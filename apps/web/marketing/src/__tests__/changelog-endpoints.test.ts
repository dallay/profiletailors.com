import { describe, it, expect, vi } from 'vitest'
import { GET as getEnglishJson } from '../pages/changelog.json'
import { GET as getSpanishJson } from '../pages/es/changelog.json'
import { GET as getEnglishRss } from '../pages/changelog/rss.xml'
import { GET as getSpanishRss } from '../pages/es/changelog/rss.xml'

vi.mock('astro:content', () => ({
  getCollection: async (_collection: string, filter?: (entry: { data: { locale: string } }) => boolean) => {
    const mockEntries = [
      {
        data: {
          slug: '2026-10-09-shortlinks',
          locale: 'en',
          date: '2026-10-09',
          title: 'Workspace shortlinks',
          summary: 'Automatic URL shortening.',
          categories: ['feature'],
        },
        body: '## Automatic shortening\n\nShortlinks work.',
      },
      {
        data: {
          slug: '2026-10-09-shortlinks',
          locale: 'es',
          date: '2026-10-09',
          title: 'Enlaces cortos',
          summary: 'Acortado automático.',
          categories: ['feature'],
        },
        body: '## Acortado automático\n\nEnlaces cortos funcionan.',
      },
    ]
    return filter ? mockEntries.filter(filter) : mockEntries
  },
}))

describe('changelog endpoints', () => {
  it('serves english changelog json feed with correct headers and locale', async () => {
    const res = await getEnglishJson({ site: new URL('https://profiletailors.com') } as never)
    expect(res.status).toBe(200)
    expect(res.headers.get('Content-Type')).toBe('application/json; charset=utf-8')
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('*')
    const json = await res.json()
    expect(json.locale).toBe('en')
    expect(json.entries).toHaveLength(1)
    expect(json.entries[0].title).toBe('Workspace shortlinks')
  })

  it('serves spanish changelog json feed with correct headers and locale', async () => {
    const res = await getSpanishJson({ site: new URL('https://profiletailors.com') } as never)
    expect(res.status).toBe(200)
    expect(res.headers.get('Content-Type')).toBe('application/json; charset=utf-8')
    expect(res.headers.get('Access-Control-Allow-Origin')).toBe('*')
    const json = await res.json()
    expect(json.locale).toBe('es')
    expect(json.entries).toHaveLength(1)
    expect(json.entries[0].title).toBe('Enlaces cortos')
  })

  it('serves english rss xml feed with correct content type and items', async () => {
    const res = await getEnglishRss({ site: new URL('https://profiletailors.com') } as never)
    expect(res.status).toBe(200)
    expect(res.headers.get('Content-Type')).toBe('application/rss+xml; charset=utf-8')
    const xml = await res.text()
    expect(xml).toContain('<title>Product Changelog — Profile Tailors</title>')
    expect(xml).toContain('<title>Workspace shortlinks</title>')
  })

  it('serves spanish rss xml feed with correct content type and items', async () => {
    const res = await getSpanishRss({ site: new URL('https://profiletailors.com') } as never)
    expect(res.status).toBe(200)
    expect(res.headers.get('Content-Type')).toBe('application/rss+xml; charset=utf-8')
    const xml = await res.text()
    expect(xml).toContain('<title>Registro de cambios — Profile Tailors</title>')
    expect(xml).toContain('<title>Enlaces cortos</title>')
  })
})
