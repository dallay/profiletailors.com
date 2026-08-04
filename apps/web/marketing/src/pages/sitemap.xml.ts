import type { APIRoute } from 'astro'

const ROUTES = ['/', '/privacy/', '/terms/', '/cookies/', '/acceptable-use/', '/accessibility/']

function buildUrl(base: URL, locale: 'en' | 'es', route: string): string {
    if (locale === 'en') {
        return new URL(route, base).href
    }
    if (route === '/') {
        return new URL('/es/', base).href
    }
    return new URL(`/es${route}`, base).href
}

export const GET: APIRoute = ({ site }) => {
    const base = site ?? new URL('https://profiletailors.com')
    const now = new Date().toISOString()

    const urls = ROUTES.flatMap((route) => {
        const enUrl = buildUrl(base, 'en', route)
        const esUrl = buildUrl(base, 'es', route)

        return [
            `<url><loc>${enUrl}</loc><lastmod>${now}</lastmod><changefreq>weekly</changefreq><priority>${route === '/' ? '1.0' : '0.7'}</priority></url>`,
            `<url><loc>${esUrl}</loc><lastmod>${now}</lastmod><changefreq>weekly</changefreq><priority>${route === '/' ? '0.9' : '0.6'}</priority></url>`,
        ]
    }).join('')

    const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${urls}</urlset>`

    return new Response(xml, {
        headers: {
            'Content-Type': 'application/xml; charset=utf-8',
        },
    })
}
