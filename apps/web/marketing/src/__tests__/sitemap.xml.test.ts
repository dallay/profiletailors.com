import { describe, it, expect } from 'vitest';
import { GET } from '../pages/sitemap.xml.ts';

const EN_ROUTES = ['/', '/privacy/', '/terms/', '/cookies/', '/acceptable-use/', '/accessibility/'];
const EXPECTED_12_PATHS = [
  'https://profiletailors.com/',
  'https://profiletailors.com/es/',
  'https://profiletailors.com/privacy/',
  'https://profiletailors.com/es/privacy/',
  'https://profiletailors.com/terms/',
  'https://profiletailors.com/es/terms/',
  'https://profiletailors.com/cookies/',
  'https://profiletailors.com/es/cookies/',
  'https://profiletailors.com/acceptable-use/',
  'https://profiletailors.com/es/acceptable-use/',
  'https://profiletailors.com/accessibility/',
  'https://profiletailors.com/es/accessibility/',
];

describe('sitemap.xml — 12 URL SEO contract', () => {
  it('returns 200 OK with XML content type', async () => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    expect(res.status).toBe(200);
    expect(res.headers.get('Content-Type')).toBe('application/xml; charset=utf-8');
  });

  it('emits valid XML with sitemap namespace', async () => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    const xml: string = await res.text();
    expect(xml).toMatch(/^<\?xml version="1\.0" encoding="UTF-8"\?>/);
    expect(xml).toContain('<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">');
    expect(xml.trim().endsWith('</urlset>')).toBe(true);
  });

  it('lists exactly the 12 approved canonical URLs', async () => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    const xml: string = await res.text();
    const locs: string[] = [...xml.matchAll(/<loc>(.*?)<\/loc>/g)].map((m: RegExpMatchArray): string => m[1]);
    expect(locs).toHaveLength(12);
    for (const expected of EXPECTED_12_PATHS) {
      expect(locs, `missing ${expected}`).toContain(expected);
    }
  });

  it('uses HTTPS with trailing slashes for every loc', async () => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    const xml: string = await res.text();
    const locs: string[] = [...xml.matchAll(/<loc>(.*?)<\/loc>/g)].map((m: RegExpMatchArray): string => m[1]);
    for (const loc of locs) {
      expect(loc.startsWith('https://'), loc).toBe(true);
      expect(loc.endsWith('/'), loc).toBe(true);
      expect(loc.startsWith('http://'), loc).toBe(false);
      expect(loc, `cdn-cgi in ${loc}`).not.toContain('cdn-cgi');
    }
  });

  it('every route has EN/ES pairing (counterpart path exists)', async () => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    const xml: string = await res.text();
    const locs: string[] = [...xml.matchAll(/<loc>(.*?)<\/loc>/g)].map((m: RegExpMatchArray): string => m[1]);
    const set = new Set(locs);
    for (const path of EN_ROUTES) {
      const enUrl = `https://profiletailors.com${path}`;
      const esUrl = path === '/' ? 'https://profiletailors.com/es/' : `https://profiletailors.com/es${path}`;
      expect(set.has(enUrl), `missing EN ${enUrl}`).toBe(true);
      expect(set.has(esUrl), `missing ES ${esUrl}`).toBe(true);
    }
  });

  it('home URL has highest priority and legal routes are lower', async () => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    const xml: string = await res.text();
    const homeEntry: string | undefined = xml.match(/<url><loc>https:\/\/profiletailors\.com\/<\/loc>[\s\S]*?<\/url>/)?.[0];
    expect(homeEntry, 'home url entry missing').toBeTruthy();
    expect(homeEntry).toContain('<priority>1.0</priority>');
    const legalEntry: string | undefined = xml.match(/<url><loc>https:\/\/profiletailors\.com\/privacy\/<\/loc>[\s\S]*?<\/url>/)?.[0];
    expect(legalEntry).toContain('<priority>0.7</priority>');
  });

  it('every url entry has lastmod, changefreq, and priority', async () => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    const xml: string = await res.text();
    const entries: RegExpMatchArray[] = [...xml.matchAll(/<url>[\s\S]*?<\/url>/g)];
    expect(entries.length).toBe(12);
    for (const entry of entries) {
      expect(entry[0]).toContain('<lastmod>');
      expect(entry[0]).toContain('<changefreq>weekly</changefreq>');
      expect(entry[0]).toContain('<priority>');
    }
  });

  it('does not reference cdn-cgi or IndexNow endpoints', async () => {
    const res: Response = await GET({ site: new URL('https://profiletailors.com') } as never);
    const xml: string = await res.text();
    expect(xml).not.toContain('cdn-cgi');
    expect(xml).not.toContain(['api', 'indexnow', 'org'].join('.'));
    expect(xml).not.toMatch(/indexnow/i);
  });

  it('falls back to default origin when site is undefined', async () => {
    const res: Response = await GET({ site: undefined } as never);
    const xml: string = await res.text();
    const locs: string[] = [...xml.matchAll(/<loc>(.*?)<\/loc>/g)].map((m: RegExpMatchArray): string => m[1]);
    expect(locs).toHaveLength(12);
    for (const loc of locs) {
      expect(loc.startsWith('https://profiletailors.com/'), loc).toBe(true);
    }
  });
});
