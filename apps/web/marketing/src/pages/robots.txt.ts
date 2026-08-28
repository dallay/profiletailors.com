import type { APIRoute } from 'astro'

export const GET: APIRoute = ({ site }) => {
    const base = site ?? new URL('https://profiletailors.com')
    const sitemapUrl = new URL('/sitemap.xml', base).href

    const body = [
        'User-agent: *',
        'Allow: /',
        '',
        'User-agent: OAI-SearchBot',
        'Allow: /',
        '',
        'User-agent: GPTBot',
        'Allow: /',
        '',
        'User-agent: PerplexityBot',
        'Allow: /',
        '',
        'User-agent: ClaudeBot',
        'Allow: /',
        '',
        'User-agent: Google-Extended',
        'Allow: /',
        '',
        'User-agent: GoogleOther',
        'Allow: /',
        '',
        'User-agent: Bingbot',
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
