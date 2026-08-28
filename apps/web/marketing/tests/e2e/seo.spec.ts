import { test, expect } from '@playwright/test'

const ROUTES = ['/', '/privacy/', '/terms/', '/cookies/', '/acceptable-use/', '/accessibility/']
const URLS: string[] = ROUTES.flatMap((r) => (r === '/' ? ['/', '/es/'] : [r, `/es${r}`]))
const BOTS = ['OAI-SearchBot', 'GPTBot', 'PerplexityBot', 'ClaudeBot', 'Google-Extended', 'GoogleOther', 'Bingbot']

function isInternalHref(href: string | null): boolean {
  if (!href) return false
  if (href.startsWith('mailto:')) return false
  if (href.startsWith('#')) return false
  if (href.startsWith('http://') || href.startsWith('https://')) {
    try {
      const u = new URL(href)
      return u.hostname === 'profiletailors.com' || u.hostname === 'localhost'
    } catch {
      return false
    }
  }
  return href.startsWith('/')
}

test.describe('SEO — Link hygiene crawl graph', () => {
  test('No broken or obfuscated href (12 URLs)', async ({ page, request }) => {
    const allHrefs = new Set<string>()
    const hrefToSources = new Map<string, string[]>()

    for (const url of URLS) {
      await page.goto(url)
      await page.waitForLoadState('networkidle')
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
    }

    for (const href of allHrefs) {
      const target = href.startsWith('http') ? href : href
      const res = await request.get(target)
      expect(res.status(), `broken ${href} from ${hrefToSources.get(href)?.join(',')}`).toBeLessThan(400)
    }
  })

  test('No http href on any of 12 URLs', async ({ page }) => {
    for (const url of URLS) {
      await page.goto(url)
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
    }
  })

  test('Sitemap parity — 12 loc and inbound >=2', async ({ page, request }) => {
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
      await page.goto(url)
      const hrefs = await page.evaluate(() =>
        Array.from(document.querySelectorAll('a[href]'))
          .map((a) => a.getAttribute('href'))
          .filter(Boolean) as string[]
      )
      const alternates = await page.evaluate(() =>
        Array.from(document.querySelectorAll('link[rel="alternate"][hreflang]'))
          .map((l) => l.getAttribute('href'))
          .filter(Boolean) as string[]
      )
      const combined = [...hrefs, ...alternates]
      for (const href of combined) {
        if (!href) continue
        const isAlt = alternates.includes(href)
        if (!isAlt && !isInternalHref(href)) continue
        if (!isAlt) {
          const aEl = page.locator(`a[href="${href}"]`).first()
          const rel = (await aEl.getAttribute('rel')) ?? ''
          if (rel.includes('nofollow')) continue
        }
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
    }

    for (const [path, count] of inbound) {
      expect(count, `orphan ${path} inbound ${count}`).toBeGreaterThanOrEqual(2)
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
      const idx = body.indexOf(`User-agent: ${bot}`)
      const slice = body.slice(idx, idx + 500)
      expect(slice, `Allow missing for ${bot}`).toContain('Allow: /')
    }
  })

  test('Layout robots meta is index,follow on 12 URLs', async ({ page }) => {
    for (const url of URLS) {
      await page.goto(url)
      const content = await page.getAttribute('meta[name="robots"]', 'content')
      expect(content, url).toMatch(/index/)
      expect(content, url).toMatch(/follow/)
      expect(content, url).not.toMatch(/noindex/)
    }
  })
})

test.describe('SEO — invariants head (titles, meta, h1, canonical, hreflang, og)', () => {
  test('Single H1 and Canonical and hreflang and og on 12 URLs', async ({ page }) => {
    const titles = new Set<string>()
    const descriptions = new Set<string>()
    for (const url of URLS) {
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

      const h1Count = await page.locator('h1').count()
      expect(h1Count, `${url} h1 count`).toBe(1)
      const h1Text = (await page.locator('h1').first().innerText()).trim()
      expect(h1Text.length, `${url} h1`).toBeGreaterThan(0)
      if (url !== '/' && url !== '/es/') {
        expect(h1Text, `${url} h1===title`).toBe(title)
      }

      const canonical = await page.getAttribute('link[rel="canonical"]', 'href')
      expect(canonical, `${url} canonical`).toBeTruthy()
      expect(canonical).toMatch(/^https:\/\/profiletailors\.com\//)
      expect(canonical).toMatch(/\/$/)

      const enHref = await page.getAttribute('link[rel="alternate"][hreflang="en"]', 'href')
      const esHref = await page.getAttribute('link[rel="alternate"][hreflang="es"]', 'href')
      const xHref = await page.getAttribute('link[rel="alternate"][hreflang="x-default"]', 'href')
      expect(enHref, `${url} hreflang en`).toBeTruthy()
      expect(esHref, `${url} hreflang es`).toBeTruthy()
      expect(xHref, `${url} hreflang x-default`).toBeTruthy()
      expect(xHref).toBe(enHref)

      const ogTitle = await page.getAttribute('meta[property="og:title"]', 'content')
      const ogDesc = await page.getAttribute('meta[property="og:description"]', 'content')
      const ogUrl = await page.getAttribute('meta[property="og:url"]', 'content')
      const ogType = await page.getAttribute('meta[property="og:type"]', 'content')
      expect(ogTitle, url).toBeTruthy()
      expect(ogDesc, url).toBeTruthy()
      expect(ogUrl, url).toBeTruthy()
      expect(ogType, url).toBe('website')
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
  })
})
