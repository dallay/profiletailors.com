// @vitest-environment node

import { experimental_AstroContainer as AstroContainer } from 'astro/container'
import { describe, expect, it, vi } from 'vitest'
import { useTranslations } from '../i18n/utils'
import ChangelogIndexPage from '../pages/_ChangelogIndexPage.astro'
import * as englishRss from '../pages/changelog/rss.xml'
import * as spanishRss from '../pages/es/changelog/rss.xml'
import Nav from './Nav.astro'

vi.mock('astro:content', () => ({
  getCollection: async () => [],
}))

describe('Nav language switch accessibility', () => {
  it.each([
    { locale: 'en', code: 'ES', name: 'ES — Switch to Spanish' },
    { locale: 'es', code: 'EN', name: 'EN — Cambiar a inglés' },
  ] as const)('includes the visible language code exactly once in the accessible name for $locale', async ({ locale, code, name }) => {
    const container = await AstroContainer.create()
    const t = useTranslations(locale)
    const langHref = locale === 'en' ? '/es/' : '/'
    const result = await container.renderToString(Nav, {
      props: {
        locale,
        langSwitch: t.nav.langSwitch,
        langHref,
        langLabel: t.nav.langSwitchLabel,
      },
    })
    const link = result.match(new RegExp(`<a[^>]*href="${langHref}"[^>]*>(.*?)</a>`, 's'))
    const accessibleName = link?.[0].match(/aria-label="([^"]*)"/)?.[1]

    expect(link?.[1].trim()).toBe(code)
    expect(accessibleName).toBe(name)
    expect(accessibleName).toContain(code)
    expect(accessibleName).toMatch(new RegExp(`^${code}\\b`))
    expect(accessibleName?.match(new RegExp(`\\b${code}\\b`, 'g'))).toHaveLength(1)
  })
})

describe('changelog index', () => {
  it.each([
    { locale: 'en', message: 'No updates have been published yet.' },
    { locale: 'es', message: 'Aún no se han publicado novedades.' },
  ])('renders a visible initial empty state in $locale without JavaScript', async ({ locale, message }) => {
    const container = await AstroContainer.create()
    const html = await container.renderToString(ChangelogIndexPage, { props: { locale } })
    const emptyState = html.match(/<p\b[^>]*\bdata-changelog-empty\b[^>]*>[^<]*<\/p>/)

    expect(emptyState?.[0]).toContain(message)
    expect(emptyState?.[0]).not.toMatch(/\bhidden\b/)
  })

  it.each([
    { locale: 'en', path: '/changelog/', label: 'Changelog' },
    { locale: 'es', path: '/es/changelog/', label: 'Cambios' },
  ])('renders the footer changelog link in $locale', async ({ locale, path, label }) => {
    const container = await AstroContainer.create()
    const html = await container.renderToString(ChangelogIndexPage, { props: { locale } })
    const footer = html.match(/<footer\b[^>]*>[\s\S]*?<\/footer>/)?.[0]

    expect(footer).toMatch(new RegExp(`<a\\b[^>]*href="${path}"[^>]*>\\s*${label}\\s*<\\/a>`))
  })

  it.each([
    { locale: 'en', endpoint: englishRss, path: '/changelog/rss.xml', title: 'Product Changelog — Profile Tailors' },
    { locale: 'es', endpoint: spanishRss, path: '/es/changelog/rss.xml', title: 'Registro de cambios — Profile Tailors' },
  ])('advertises the RSS channel title in $locale', async ({ locale, endpoint, path, title }) => {
    const container = await AstroContainer.create()
    const html = await container.renderToString(ChangelogIndexPage, { props: { locale } })
    const rss = await container.renderToString(endpoint, { routeType: 'endpoint' })
    const alternate = html.match(/<link\b[^>]*type="application\/rss\+xml"[^>]*>/)?.[0]

    expect(rss).toContain(`<title>${title}</title>`)
    expect(alternate).toContain(`title="${title}"`)
    expect(alternate).toContain(`href="https://profiletailors.com${path}"`)
  })
})
