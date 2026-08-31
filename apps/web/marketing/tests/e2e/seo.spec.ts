import { test, expect } from '@playwright/test'

const ROUTES = ['/', '/privacy/', '/terms/', '/cookies/', '/acceptable-use/', '/accessibility/']
const URLS: string[] = ROUTES.flatMap((r) => (r === '/' ? ['/', '/es/'] : [r, `/es${r}`]))
const BOTS = ['OAI-SearchBot', 'GPTBot', 'PerplexityBot', 'ClaudeBot', 'Google-Extended', 'GoogleOther', 'Bingbot']

function isInternalHref(href: string | null): boolean {
  if (!href) return false
  const t = href.trim()
  if (!t) return false
  if (t.startsWith('mailto:') || t.startsWith('tel:') || t.startsWith('javascript:') || t.startsWith('data:')) return false
  if (t.startsWith('#')) return false
  if (t.startsWith('//')) {
    try {
      const u = new URL(`https:${t}`)
      return u.hostname === 'profiletailors.com' || u.hostname === 'localhost'
    } catch {
      return false
    }
  }
  if (t.startsWith('http://') || t.startsWith('https://')) {
    try {
      const u = new URL(t)
      return u.hostname === 'profiletailors.com' || u.hostname === 'localhost'
    } catch {
      return false
    }
  }
  if (t.startsWith('/')) return true
  if (!t.includes(':')) return true
  return false
}

function resolveToPreview(href: string, previewOrigin: string): string {
  try {
    const url = new URL(href, previewOrigin)
    if (url.hostname === 'profiletailors.com') {
      return new URL(url.pathname + url.search + url.hash, previewOrigin).href
    }
    return url.href
  } catch {
    return href
  }
}

test.describe('SEO — Link hygiene crawl graph', () => {
  test('No broken or obfuscated href (12 URLs)', async ({ page, request }) => {
    const allHrefs = new Set<string>()
    const hrefToSources = new Map<string, string[]>()

    for (const url of URLS) {
      await test.step(`crawl ${url}`, async () => {
        await page.goto(url)
        await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
        const hrefs = await page.evaluate(() => {
          return Array.from(document.querySelectorAll('a[href]'))
            .map((a) => a.getAttribute('href'))
            .filter(Boolean) as string[]
        })
        for (const href of hrefs) {
          if (!isInternalHref(href)) continue
          expect(href, `cdn-cgi in ${url} -> ${href}`).not.toContain('cdn-cgi')
          expect(href, `http:// in ${url} -> ${href}`).not.toContain('http://')
          const normalized = href.split('#')[0].split('?')[0]
          allHrefs.add(normalized)
          const arr = hrefToSources.get(normalized) ?? []
          arr.push(url)
          hrefToSources.set(normalized, arr)
        }
      })
    }

    const previewOrigin = new URL(await page.evaluate(() => location.href)).origin
    for (const href of allHrefs) {
      const target = resolveToPreview(href, previewOrigin)
      const res = await request.get(target)
      expect(res.status(), `broken ${href} from ${hrefToSources.get(href)?.join(',')}`).toBeLessThan(400)
    }
  })

  test('No http href on any of 12 URLs', async ({ page }) => {
    for (const url of URLS) {
      await test.step(`check http href ${url}`, async () => {
        await page.goto(url)
        await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
        const hrefs = await page.evaluate(() =>
          Array.from(document.querySelectorAll('[href]'))
            .map((el) => el.getAttribute('href'))
            .concat(Array.from(document.querySelectorAll('[src]')).map((el) => el.getAttribute('src')))
            .filter(Boolean)
        )
        for (const h of hrefs as string[]) {
          if (h.includes('serif.com')) continue
          expect(h, `${url} -> ${h}`).not.toMatch(/^http:\/\//)
        }
      })
    }
  })

  test('Sitemap parity — 12 loc and inbound >=1', async ({ page, request }) => {
    const res = await request.get('/sitemap.xml')
    expect(res.status()).toBe(200)
    const xml = await res.text()
    const locs = [...xml.matchAll(/<loc>(.*?)<\/loc>/g)].map((m) => m[1])
    expect(locs).toHaveLength(12)
    for (const loc of locs) {
      expect(loc).toMatch(/^https:\/\/profiletailors\.com\//)
      expect(loc).toMatch(/\/$/)
    }

    const inbound = new Map<string, number>()
    for (const loc of locs) {
      const u = new URL(loc)
      inbound.set(u.pathname, 0)
    }

    for (const url of URLS) {
      await test.step(`inbound ${url}`, async () => {
        await page.goto(url)
        await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
        const anchors: Array<{ href: string; rel: string }> = await page.evaluate(() =>
          Array.from(document.querySelectorAll('a[href]')).map((a) => ({
            href: a.getAttribute('href') ?? '',
            rel: a.getAttribute('rel') ?? '',
          }))
        )
        for (const { href, rel } of anchors) {
          if (!href) continue
          if (!isInternalHref(href)) continue
          if (rel.split(/\s+/).includes('nofollow')) continue
          let pathname: string
          try {
            pathname = new URL(href, 'https://profiletailors.com').pathname
          } catch {
            continue
          }
          if (inbound.has(pathname)) {
            inbound.set(pathname, (inbound.get(pathname) ?? 0) + 1)
          }
        }
      })
    }

    for (const [path, count] of inbound) {
      expect(count, `orphan ${path} inbound ${count}`).toBeGreaterThanOrEqual(1)
    }
  })
})

test.describe('SEO — robots.txt per-bot Allow', () => {
  test('per-bot Allow and Does not block AI', async ({ request }) => {
    const res = await request.get('/robots.txt')
    expect(res.status()).toBe(200)
    const body = await res.text()
    expect(body).toContain('User-agent: *')
    expect(body).toContain('Allow: /')
    expect(body).not.toContain('Disallow: /')
    expect(body).toContain('Sitemap: https://profiletailors.com/sitemap.xml')
    for (const bot of BOTS) {
      expect(body, `missing bot ${bot}`).toContain(`User-agent: ${bot}`)
      const start = body.indexOf(`User-agent: ${bot}`)
      const next = body.indexOf('User-agent:', start + 1)
      const stanza = next === -1 ? body.slice(start) : body.slice(start, next)
      expect(stanza, `Allow missing for ${bot}`).toContain('Allow: /')
    }
  })

  test('Layout robots meta is index,follow on 12 URLs', async ({ page }) => {
    for (const url of URLS) {
      await test.step(`robots meta ${url}`, async () => {
        await page.goto(url)
        await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
        const content = (await page.getAttribute('meta[name="robots"]', 'content')) ?? ''
        const directives: string[] = content
          .split(',')
          .map((s: string): string => s.trim().toLowerCase())
          .filter(Boolean)
        expect(directives, `${url} missing index`).toContain('index')
        expect(directives, `${url} missing follow`).toContain('follow')
        expect(directives, `${url} should not contain noindex`).not.toContain('noindex')
        expect(directives, `${url} should not contain nofollow`).not.toContain('nofollow')
      })
    }
  })
})

test.describe('SEO — invariants head (titles, meta, h1, canonical, hreflang, og)', () => {
  test('Single H1 and Canonical and hreflang and og on 12 URLs', async ({ page }) => {
    const titles = new Set<string>()
    const descriptions = new Set<string>()
    const canonicalMap: Record<string, string> = {
      '/': 'https://profiletailors.com/',
      '/es/': 'https://profiletailors.com/es/',
      '/privacy/': 'https://profiletailors.com/privacy/',
      '/es/privacy/': 'https://profiletailors.com/es/privacy/',
      '/terms/': 'https://profiletailors.com/terms/',
      '/es/terms/': 'https://profiletailors.com/es/terms/',
      '/cookies/': 'https://profiletailors.com/cookies/',
      '/es/cookies/': 'https://profiletailors.com/es/cookies/',
      '/acceptable-use/': 'https://profiletailors.com/acceptable-use/',
      '/es/acceptable-use/': 'https://profiletailors.com/es/acceptable-use/',
      '/accessibility/': 'https://profiletailors.com/accessibility/',
      '/es/accessibility/': 'https://profiletailors.com/es/accessibility/',
    }
    const hreflangMap: Record<string, { en: string; es: string }> = {
      '/': { en: 'https://profiletailors.com/', es: 'https://profiletailors.com/es/' },
      '/es/': { en: 'https://profiletailors.com/', es: 'https://profiletailors.com/es/' },
      '/privacy/': { en: 'https://profiletailors.com/privacy/', es: 'https://profiletailors.com/es/privacy/' },
      '/es/privacy/': { en: 'https://profiletailors.com/privacy/', es: 'https://profiletailors.com/es/privacy/' },
      '/terms/': { en: 'https://profiletailors.com/terms/', es: 'https://profiletailors.com/es/terms/' },
      '/es/terms/': { en: 'https://profiletailors.com/terms/', es: 'https://profiletailors.com/es/terms/' },
      '/cookies/': { en: 'https://profiletailors.com/cookies/', es: 'https://profiletailors.com/es/cookies/' },
      '/es/cookies/': { en: 'https://profiletailors.com/cookies/', es: 'https://profiletailors.com/es/cookies/' },
      '/acceptable-use/': { en: 'https://profiletailors.com/acceptable-use/', es: 'https://profiletailors.com/es/acceptable-use/' },
      '/es/acceptable-use/': { en: 'https://profiletailors.com/acceptable-use/', es: 'https://profiletailors.com/es/acceptable-use/' },
      '/accessibility/': { en: 'https://profiletailors.com/accessibility/', es: 'https://profiletailors.com/es/accessibility/' },
      '/es/accessibility/': { en: 'https://profiletailors.com/accessibility/', es: 'https://profiletailors.com/es/accessibility/' },
    }
    for (const url of URLS) {
      await test.step(`head ${url}`, async () => {
        await page.goto(url)
        const title = (await page.title()).trim()
        expect(title.length, `${url} title length ${title}`).toBeGreaterThanOrEqual(30)
        expect(title, `${url} suffix`).toContain(' — Profile Tailors')
        expect(title.endsWith(' — Profile Tailors'), url).toBe(true)
        titles.add(title)

        const desc = (await page.getAttribute('meta[name="description"]', 'content')) ?? ''
        expect(desc.length, `${url} desc ${desc}`).toBeGreaterThanOrEqual(120)
        expect(desc.length, `${url} desc ${desc}`).toBeLessThanOrEqual(160)
        descriptions.add(desc)

        const h1 = page.getByRole('heading', { level: 1 })
        await expect(h1, `${url} h1 count`).toHaveCount(1)
        const h1Text = (await h1.innerText()).trim()
        expect(h1Text.length, `${url} h1`).toBeGreaterThan(0)
        if (url !== '/' && url !== '/es/') {
          expect(h1Text, `${url} h1===title`).toBe(title)
        }

        const canonical = await page.getAttribute('link[rel="canonical"]', 'href')
        expect(canonical, `${url} canonical`).toBe(canonicalMap[url])
        const expected = hreflangMap[url]
        const enHref = await page.getAttribute('link[rel="alternate"][hreflang="en"]', 'href')
        const esHref = await page.getAttribute('link[rel="alternate"][hreflang="es"]', 'href')
        const xHref = await page.getAttribute('link[rel="alternate"][hreflang="x-default"]', 'href')
        expect(enHref, `${url} hreflang en`).toBe(expected.en)
        expect(esHref, `${url} hreflang es`).toBe(expected.es)
        expect(xHref, `${url} hreflang x-default`).toBe(expected.en)

        const ogTitle = await page.getAttribute('meta[property="og:title"]', 'content')
        const ogDesc = await page.getAttribute('meta[property="og:description"]', 'content')
        const ogUrl = await page.getAttribute('meta[property="og:url"]', 'content')
        const ogType = await page.getAttribute('meta[property="og:type"]', 'content')
        expect(ogTitle, url).toBeTruthy()
        expect(ogDesc, url).toBeTruthy()
        expect(ogUrl, url).toBeTruthy()
        expect(ogType, url).toBe('website')
      })
    }
    expect(titles.size, 'titles unique').toBe(12)
    expect(descriptions.size, 'descriptions unique').toBe(12)
  })
})

test.describe('SEO — IndexNow intentionally absent', () => {
  test('No IndexNow artifacts', async ({ request }) => {
    const res = await request.get('/sitemap.xml')
    const xml = await res.text()
    expect(xml).not.toContain('api.indexnow.org')
    const robots = await (await request.get('/robots.txt')).text()
    expect(robots).not.toContain('api.indexnow.org')
    const { readdirSync, existsSync } = await import('node:fs')
    const { join } = await import('node:path')
    const candidates = [
      join(process.cwd(), 'dist'),
      join(process.cwd(), 'apps/web/marketing/dist'),
      join(process.cwd(), '..', '..', 'dist'),
    ]
    const dist = candidates.find((p) => existsSync(p))
    if (dist) {
      const files: string[] = readdirSync(dist)
      const keyFiles: string[] = files.filter((f: string): boolean => /^[a-f0-9]{32}\.txt$/i.test(f) || f.toLowerCase() === 'key.txt')
      expect(keyFiles, `IndexNow key file found in dist: ${keyFiles.join(',')}`).toEqual([])
    }
  })
})

test.describe('SEO — accessible name alignment (WCAG 2.5.3 label-content-name-mismatch)', () => {
  const langLinkByUrl: Record<string, { code: string; hrefContains: string }> = {
    '/': { code: 'ES', hrefContains: '/es/' },
    '/es/': { code: 'EN', hrefContains: '/' },
    '/privacy/': { code: 'ES', hrefContains: '/es/privacy/' },
    '/es/privacy/': { code: 'EN', hrefContains: '/privacy/' },
    '/terms/': { code: 'ES', hrefContains: '/es/terms/' },
    '/es/terms/': { code: 'EN', hrefContains: '/terms/' },
    '/cookies/': { code: 'ES', hrefContains: '/es/cookies/' },
    '/es/cookies/': { code: 'EN', hrefContains: '/cookies/' },
    '/acceptable-use/': { code: 'ES', hrefContains: '/es/acceptable-use/' },
    '/es/acceptable-use/': { code: 'EN', hrefContains: '/acceptable-use/' },
    '/accessibility/': { code: 'ES', hrefContains: '/es/accessibility/' },
    '/es/accessibility/': { code: 'EN', hrefContains: '/accessibility/' },
  }

  for (const url of URLS) {
    test(`locale switch on ${url} has accessible name containing visible code`, async ({ page }) => {
      await page.goto(url)
      const { code } = langLinkByUrl[url]
      const link = page.locator('nav[aria-label="Main"] a').filter({ hasText: code }).first()
      await expect(link, `lang switch link not found on ${url}`).toBeVisible()
      const text = (await link.innerText()).trim()
      expect(text, `${url} visible text`).toBe(code)
      const ariaLabel = (await link.getAttribute('aria-label')) ?? ''
      expect(ariaLabel, `${url} aria-label missing`).toBeTruthy()
      expect(ariaLabel, `${url} aria-label "${ariaLabel}" must contain visible "${code}"`).toContain(code)
    })
  }
})

test.describe('SEO — main landmark on legal pages', () => {
  for (const url of URLS.filter((u) => u !== '/' && u !== '/es/')) {
    test(`${url} exposes a single main landmark`, async ({ page }) => {
      await page.goto(url)
      const mainCount = await page.locator('main, [role="main"]').count()
      expect(mainCount, `${url} main landmark count`).toBeGreaterThanOrEqual(1)
      expect(mainCount, `${url} must not have multiple main landmarks`).toBeLessThanOrEqual(1)
    })
  }
})

test.describe('SEO — JSON-LD structured data identity', () => {
  const expectedJsonLd: Record<string, { type: string; inLanguage: string }> = {
    '/': { type: 'WebSite', inLanguage: 'en' },
    '/es/': { type: 'WebSite', inLanguage: 'es' },
    '/privacy/': { type: 'WebPage', inLanguage: 'en' },
    '/es/privacy/': { type: 'WebPage', inLanguage: 'es' },
    '/terms/': { type: 'WebPage', inLanguage: 'en' },
    '/es/terms/': { type: 'WebPage', inLanguage: 'es' },
    '/cookies/': { type: 'WebPage', inLanguage: 'en' },
    '/es/cookies/': { type: 'WebPage', inLanguage: 'es' },
    '/acceptable-use/': { type: 'WebPage', inLanguage: 'en' },
    '/es/acceptable-use/': { type: 'WebPage', inLanguage: 'es' },
    '/accessibility/': { type: 'WebPage', inLanguage: 'en' },
    '/es/accessibility/': { type: 'WebPage', inLanguage: 'es' },
  }

  for (const url of URLS) {
    test(`${url} JSON-LD type and language match expected identity`, async ({ page }) => {
      await page.goto(url)
      const jsonLd = await page
        .locator('script[type="application/ld+json"]')
        .first()
        .innerText()
      const parsed: unknown = JSON.parse(jsonLd)
      expect(parsed, `${url} JSON-LD parses`).toBeTruthy()
      const data = parsed as { '@type'?: string; '@context'?: string; url?: string; inLanguage?: string; name?: string; description?: string }
      expect(data['@context'], `${url} context`).toBe('https://schema.org')
      expect(data['@type'], `${url} type`).toBe(expectedJsonLd[url].type)
      expect(data.inLanguage, `${url} language`).toBe(expectedJsonLd[url].inLanguage)
      expect(typeof data.url, `${url} url`).toBe('string')
      const expectedCanonical: string = `https://profiletailors.com${url}`
      expect(data.url, `${url} canonical match`).toBe(expectedCanonical)
      expect(typeof data.description, `${url} description`).toBe('string')
      expect((data.description ?? '').length, `${url} description length`).toBeGreaterThanOrEqual(50)
      expect(typeof data.name, `${url} name`).toBe('string')
      expect((data.name ?? '').length, `${url} name length`).toBeGreaterThan(0)
    })
  }
})

test.describe('SEO — Markdown mailto links render without cdn-cgi obfuscation', () => {
  const legalUrls = URLS.filter((u) => u !== '/' && u !== '/es/')
  for (const url of legalUrls) {
    test(`${url} renders mailto contact links and no cdn-cgi paths`, async ({ page }) => {
      await page.goto(url)
      const mailtoHrefs: string[] = await page.evaluate(() => {
        return Array.from(document.querySelectorAll('a[href^="mailto:"]'))
          .map((a) => a.getAttribute('href') ?? '')
          .filter(Boolean)
      })
      expect(mailtoHrefs.length, `${url} must contain at least one mailto link`).toBeGreaterThan(0)
      for (const href of mailtoHrefs) {
        expect(href.startsWith('mailto:'), `${url} mailto ${href}`).toBe(true)
        expect(href, `${url} mailto ${href} contains cdn-cgi`).not.toContain('cdn-cgi')
      }
      const allHrefs: string[] = await page.evaluate(() => {
        return Array.from(document.querySelectorAll('a[href]'))
          .map((a) => a.getAttribute('href') ?? '')
          .filter(Boolean)
      })
      for (const href of allHrefs) {
        expect(href, `${url} href ${href} contains cdn-cgi`).not.toContain('cdn-cgi')
      }
    })
  }
})

test.describe('SEO — canonical and hreflang parity between EN/ES counterparts', () => {
  const pairings: Array<[string, string]> = [
    ['/', '/es/'],
    ['/privacy/', '/es/privacy/'],
    ['/terms/', '/es/terms/'],
    ['/cookies/', '/es/cookies/'],
    ['/acceptable-use/', '/es/acceptable-use/'],
    ['/accessibility/', '/es/accessibility/'],
  ]

  for (const [enPath, esPath] of pairings) {
    test(`${enPath} hreflang en points to itself and es points to ${esPath}`, async ({ page }) => {
      await page.goto(enPath)
      const enHref = await page.getAttribute('link[rel="alternate"][hreflang="en"]', 'href')
      const esHref = await page.getAttribute('link[rel="alternate"][hreflang="es"]', 'href')
      const xHref = await page.getAttribute('link[rel="alternate"][hreflang="x-default"]', 'href')
      expect(enHref, `${enPath} hreflang en`).toBe(`https://profiletailors.com${enPath}`)
      expect(esHref, `${enPath} hreflang es`).toBe(`https://profiletailors.com${esPath}`)
      expect(xHref, `${enPath} hreflang x-default`).toBe(`https://profiletailors.com${enPath}`)
    })

    test(`${esPath} hreflang en points to ${enPath} and es points to itself`, async ({ page }) => {
      await page.goto(esPath)
      const enHref = await page.getAttribute('link[rel="alternate"][hreflang="en"]', 'href')
      const esHref = await page.getAttribute('link[rel="alternate"][hreflang="es"]', 'href')
      const xHref = await page.getAttribute('link[rel="alternate"][hreflang="x-default"]', 'href')
      expect(enHref, `${esPath} hreflang en`).toBe(`https://profiletailors.com${enPath}`)
      expect(esHref, `${esPath} hreflang es`).toBe(`https://profiletailors.com${esPath}`)
      expect(xHref, `${esPath} hreflang x-default`).toBe(`https://profiletailors.com${enPath}`)
    })
  }
})
