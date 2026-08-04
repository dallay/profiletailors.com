import type { APIRoute } from 'astro'

export const GET: APIRoute = ({ site }) => {
    const base = site ?? new URL('https://profiletailors.com')
    const sitemapUrl = new URL('/sitemap.xml', base).href

    const body = [
        'User-agent: *',
        'Allow: /',
        '',
        `Sitemap: ${sitemapUrl}`,
    ].join('\n')

    return new Response(body, {
        headers: {
            'Content-Type': 'text/plain; charset=utf-8',
        },
    })
}
