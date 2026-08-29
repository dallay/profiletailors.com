import { describe, it, expect } from 'vitest';
import { GET } from '../pages/robots.txt.ts';

describe('robots.txt', (): void => {
  it('returns allow-all with per-bot Allow and Sitemap', async (): Promise<void> => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    expect(res.headers.get('Content-Type')).toBe('text/plain; charset=utf-8');
    const body: string = await res.text();
    expect(body).toContain('User-agent: *');
    expect(body).toContain('Allow: /');
    expect(body).not.toContain('Disallow: /');
    expect(body).toContain('Sitemap: https://profiletailors.com/sitemap.xml');
    for (const bot of ['OAI-SearchBot', 'GPTBot', 'PerplexityBot', 'ClaudeBot', 'Google-Extended', 'GoogleOther', 'Bingbot']) {
      expect(body, `missing ${bot}`).toContain(`User-agent: ${bot}`);
      const start: number = body.indexOf(`User-agent: ${bot}`);
      const next: number = body.indexOf('User-agent:', start + 1);
      const stanza: string = next === -1 ? body.slice(start) : body.slice(start, next);
      expect(stanza, `Allow missing for ${bot}`).toContain('Allow: /');
    }
  });

  it('falls back to default origin when site is undefined', async (): Promise<void> => {
    const res: Response = await GET({ site: undefined } as never);
    const body: string = await res.text();
    expect(body).toContain('Sitemap: https://profiletailors.com/sitemap.xml');
  });
});
