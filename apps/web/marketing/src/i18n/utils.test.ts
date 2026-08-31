import { describe, it, expect } from 'vitest';
import {
  getLocaleFromUrl,
  useTranslations,
  counterpartPath,
  canonicalUrl,
  routeSeoEntries,
} from './utils';
import {
  LEGAL_PUBLICATION_STATUS,
  legalPublicationStatus,
} from '../legal/legal-publication';

describe('i18n utils', () => {
  describe('getLocaleFromUrl', () => {
    it.each([
      { pathname: 'https://example.com/', expected: 'en', scenario: 'root URL' },
      { pathname: 'https://example.com/es/', expected: 'es', scenario: 'Spanish URL' },
      { pathname: 'https://example.com/en/', expected: 'en', scenario: 'English URL' },
      { pathname: 'https://example.com/fr/', expected: 'en', scenario: 'unknown locale' },
    ])('should return "$expected" for $scenario', ({ pathname, expected }) => {
      const url = new URL(pathname);
      expect(getLocaleFromUrl(url)).toBe(expected);
    });
  });

  describe('useTranslations', () => {
    it.each([
      {
        pathname: 'https://example.com/',
        expectedLangSwitch: 'ES',
        expectedHeroLabel: 'EARLY ACCESS PREVIEW',
        scenario: 'root URL',
      },
      {
        pathname: 'https://example.com/es/',
        expectedLangSwitch: 'EN',
        expectedHeroLabel: 'VISTA PREVIA DE ACCESO ANTICIPADO',
        scenario: '/es/',
      },
    ])('returns translations for $scenario', ({ pathname, expectedLangSwitch, expectedHeroLabel }) => {
      const url = new URL(pathname);
      const t = useTranslations(url);

      expect(t.nav.langSwitch).toBe(expectedLangSwitch);
      expect(t.hero.label).toBe(expectedHeroLabel);
    });
  });

  describe('legal translations', () => {
    it('EN has legal key with all five policy sections', () => {
      const t = useTranslations(new URL('https://example.com/'));
      expect(t.legal.privacy).toBeDefined();
      expect(t.legal.terms).toBeDefined();
      expect(t.legal.cookies).toBeDefined();
      expect(t.legal.aup).toBeDefined();
      expect(t.legal.accessibility).toBeDefined();
    });

    it('ES has same legal section structure as EN', () => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      expect(Object.keys(tEs.legal.privacy)).toEqual(Object.keys(tEn.legal.privacy));
      expect(Object.keys(tEs.legal.terms)).toEqual(Object.keys(tEn.legal.terms));
      expect(Object.keys(tEs.legal.cookies)).toEqual(Object.keys(tEn.legal.cookies));
      expect(Object.keys(tEs.legal.aup)).toEqual(Object.keys(tEn.legal.aup));
      expect(Object.keys(tEs.legal.accessibility)).toEqual(Object.keys(tEn.legal.accessibility));
    });

    it('footer has legalLinks with 5 entries', () => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      expect(tEn.footer.legalLinks).toHaveLength(5);
      expect(tEs.footer.legalLinks).toHaveLength(5);
      tEn.footer.legalLinks.forEach((link) => {
        expect(link).toHaveProperty('label');
        expect(link).toHaveProperty('href');
      });
    });

    it('confirms the legal publication gate is APPROVED for the current policies', () => {
      expect(legalPublicationStatus).toBe(LEGAL_PUBLICATION_STATUS.APPROVED);
    });

    it('does not preserve unsupported provider or contractual claims in either locale', () => {
      const legalCopy = JSON.stringify({
        en: useTranslations(new URL('https://example.com/')).legal,
        es: useTranslations(new URL('https://example.com/es/')).legal,
      });
      const unsupportedClaims = [
        'Dallay (Profile Tailors)',
        'Vercel',
        'Auth0',
        'Clerk',
        'Neon',
        'Cloudflare R2',
        'AWS S3',
        'Sentry',
        'Grafana',
        'Prometheus',
        'DPA in place',
        'DPA vigente',
        '$100',
        '[Registered Business Address',
        '[Dirección Comercial Registrada',
      ];

      for (const claim of unsupportedClaims) {
        expect(legalCopy).not.toContain(claim);
      }
    });
  });

  describe('legal SEO (Ahrefs site audit)', () => {
    const legalSectionKeys = ['privacy', 'terms', 'cookies', 'aup', 'accessibility'] as const;

    type LegalSectionKey = typeof legalSectionKeys[number];

    it.each(legalSectionKeys)(
      'EN %s.description is between 120 and 160 characters',
      (key: LegalSectionKey): void => {
        const t = useTranslations(new URL('https://example.com/'));
        const description = t.legal[key].description;
        expect(description.length).toBeGreaterThanOrEqual(120);
        expect(description.length).toBeLessThanOrEqual(160);
      }
    );

    it.each(legalSectionKeys)(
      'ES %s.description is between 120 and 160 characters',
      (key: LegalSectionKey): void => {
        const t = useTranslations(new URL('https://example.com/es/'));
        const description = t.legal[key].description;
        expect(description.length).toBeGreaterThanOrEqual(120);
        expect(description.length).toBeLessThanOrEqual(160);
      }
    );

    it('EN /privacy/ title is at least 20 characters to avoid the Ahrefs "Title too short" warning', (): void => {
      const t = useTranslations(new URL('https://example.com/'));
      expect(t.legal.privacy.title.length).toBeGreaterThanOrEqual(20);
    });

    it('ES /es/privacy/ title is at least 20 characters', (): void => {
      const t = useTranslations(new URL('https://example.com/es/'));
      expect(t.legal.privacy.title.length).toBeGreaterThanOrEqual(20);
    });

    it('EN legal copy has no raw @profiletailors.com addresses (Cloudflare email obfuscation safeguard)', (): void => {
      const enLegal = useTranslations(new URL('https://example.com/')).legal;
      const rawEmailRegex = /(?<![:[])\b[a-zA-Z0-9._%+-]+@profiletailors\.com\b(?![\])])/g;
      const matches = JSON.stringify(enLegal).match(rawEmailRegex) ?? [];
      expect(matches).toEqual([]);
    });

    it('ES legal copy has no raw @profiletailors.com addresses (Cloudflare email obfuscation safeguard)', (): void => {
      const esLegal = useTranslations(new URL('https://example.com/es/')).legal;
      const rawEmailRegex = /(?<![:[])\b[a-zA-Z0-9._%+-]+@profiletailors\.com\b(?![\])])/g;
      const matches = JSON.stringify(esLegal).match(rawEmailRegex) ?? [];
      expect(matches).toEqual([]);
    });

    it('EN legal copy has mailto links for contact and accessibility addresses', (): void => {
      const enLegal = useTranslations(new URL('https://example.com/')).legal;
      const enStr = JSON.stringify(enLegal);
      expect(enStr).toContain('[contact@profiletailors.com](mailto:contact@profiletailors.com)');
      expect(enStr).toContain('[accessibility@profiletailors.com](mailto:accessibility@profiletailors.com)');
    });

    it('ES legal copy has mailto links for contact and accessibility addresses', (): void => {
      const esLegal = useTranslations(new URL('https://example.com/es/')).legal;
      const esStr = JSON.stringify(esLegal);
      expect(esStr).toContain('[contact@profiletailors.com](mailto:contact@profiletailors.com)');
      expect(esStr).toContain('[accessibility@profiletailors.com](mailto:accessibility@profiletailors.com)');
    });
  });

  describe('consent translations', () => {
    it('EN has a complete consent structure', () => {
      const t = useTranslations(new URL('https://example.com/'));
      expect(t.consent.banner.heading).toBeTruthy();
      expect(t.consent.banner.description).toBeTruthy();
      expect(t.consent.category.necessary.label).toBeTruthy();
      expect(t.consent.category.necessary.description).toBeTruthy();
      expect(t.consent.category.analytics.label).toBeTruthy();
      expect(t.consent.category.analytics.description).toBeTruthy();
      expect(t.consent.action.acceptAll).toBeTruthy();
      expect(t.consent.action.rejectAll).toBeTruthy();
      expect(t.consent.action.savePreferences).toBeTruthy();
      expect(t.consent.footer.cookieSettings).toBeTruthy();
      expect(t.consent.privacy.link).toBeTruthy();
    });

    it('ES has the same consent section structure as EN', () => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      expect(Object.keys(tEs.consent)).toEqual(Object.keys(tEn.consent));
      expect(Object.keys(tEs.consent.banner)).toEqual(Object.keys(tEn.consent.banner));
      expect(Object.keys(tEs.consent.category)).toEqual(Object.keys(tEn.consent.category));
      expect(Object.keys(tEs.consent.category.necessary)).toEqual(
        Object.keys(tEn.consent.category.necessary)
      );
      expect(Object.keys(tEs.consent.category.analytics)).toEqual(
        Object.keys(tEn.consent.category.analytics)
      );
      expect(Object.keys(tEs.consent.action)).toEqual(Object.keys(tEn.consent.action));
      expect(Object.keys(tEs.consent.footer)).toEqual(Object.keys(tEn.consent.footer));
      expect(Object.keys(tEs.consent.privacy)).toEqual(Object.keys(tEn.consent.privacy));
    });

    it('translates the consent banner copy differently per locale', () => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      expect(tEn.consent.banner.heading).not.toBe(tEs.consent.banner.heading);
      expect(tEn.consent.action.acceptAll).not.toBe(tEs.consent.action.acceptAll);
      expect(tEn.consent.action.rejectAll).not.toBe(tEs.consent.action.rejectAll);
      expect(tEn.consent.action.savePreferences).not.toBe(tEs.consent.action.savePreferences);
    });

    it('links the consent banner description to the privacy policy page in both locales', () => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      expect(tEn.consent.banner.description).toContain('href="/privacy/"');
      expect(tEs.consent.banner.description).toContain('href="/es/privacy/"');
    });

    it('marks the necessary category as always-required copy, distinct from the opt-in analytics category', () => {
      const t = useTranslations(new URL('https://example.com/'));
      expect(t.consent.category.necessary.label).not.toBe(t.consent.category.analytics.label);
      expect(t.consent.category.necessary.description).not.toBe(
        t.consent.category.analytics.description
      );
    });
  });

  describe('marketing claims', () => {
    it('does not advertise unverified integrations, demand, registration, pricing, or worldwide availability', () => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      const marketingCopy = JSON.stringify({
        en: { hero: tEn.hero, features: tEn.features, meta: tEn.meta },
        es: { hero: tEs.hero, features: tEs.features, meta: tEs.meta },
      });
      const unsupportedClaims = [
        'Instagram',
        'Twitter/X',
        'Facebook',
        '847',
        'Join waitlist',
        'Únete a la lista',
        "you're on the list",
        'estás en la lista',
        'Worldwide',
      ];

      for (const claim of unsupportedClaims) {
        expect(marketingCopy).not.toContain(claim);
      }
      expect(tEn.hero.status).toContain('not open yet');
      expect(tEs.hero.status).toContain('todavía no está abierta');
    });
  });

  describe('seo invariants — titles and meta (Ahrefs 9293424)', () => {
    const suffix = ' — Profile Tailors';
    const allTitles = (): Array<{ key: string; title: string }> => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      return [
        { key: 'en:/', title: tEn.meta.title },
        { key: 'es:/es/', title: tEs.meta.title },
        { key: 'en:/privacy/', title: tEn.legal.privacy.title },
        { key: 'es:/es/privacy/', title: tEs.legal.privacy.title },
        { key: 'en:/terms/', title: tEn.legal.terms.title },
        { key: 'es:/es/terms/', title: tEs.legal.terms.title },
        { key: 'en:/cookies/', title: tEn.legal.cookies.title },
        { key: 'es:/es/cookies/', title: tEs.legal.cookies.title },
        { key: 'en:/acceptable-use/', title: tEn.legal.aup.title },
        { key: 'es:/es/acceptable-use/', title: tEs.legal.aup.title },
        { key: 'en:/accessibility/', title: tEn.legal.accessibility.title },
        { key: 'es:/es/accessibility/', title: tEs.legal.accessibility.title },
      ];
    };
    const allDescriptions = (): Array<{ key: string; description: string }> => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      return [
        { key: 'en:/', description: tEn.meta.description },
        { key: 'es:/es/', description: tEs.meta.description },
        { key: 'en:/privacy/', description: tEn.legal.privacy.description },
        { key: 'es:/es/privacy/', description: tEs.legal.privacy.description },
        { key: 'en:/terms/', description: tEn.legal.terms.description },
        { key: 'es:/es/terms/', description: tEs.legal.terms.description },
        { key: 'en:/cookies/', description: tEn.legal.cookies.description },
        { key: 'es:/es/cookies/', description: tEs.legal.cookies.description },
        { key: 'en:/acceptable-use/', description: tEn.legal.aup.description },
        { key: 'es:/es/acceptable-use/', description: tEs.legal.aup.description },
        { key: 'en:/accessibility/', description: tEn.legal.accessibility.description },
        { key: 'es:/es/accessibility/', description: tEs.legal.accessibility.description },
      ];
    };

    it('all 12 titles are >=30 characters', (): void => {
      for (const { key, title } of allTitles()) {
        expect(title.length, key).toBeGreaterThanOrEqual(30);
      }
    });

    it('all 12 titles end with branded suffix', (): void => {
      for (const { key, title } of allTitles()) {
        expect(title.endsWith(suffix), key).toBe(true);
      }
    });

    it('all 12 titles are unique', (): void => {
      const titles: string[] = allTitles().map((t: { key: string; title: string }): string => t.title);
      expect(new Set(titles).size).toBe(12);
    });

    it('all 12 descriptions are 120-160 characters', (): void => {
      for (const { key, description } of allDescriptions()) {
        expect(description.length, key).toBeGreaterThanOrEqual(120);
        expect(description.length, key).toBeLessThanOrEqual(160);
      }
    });

    it('all 12 descriptions are unique', (): void => {
      const descs: string[] = allDescriptions().map((d: { key: string; description: string }): string => d.description);
      expect(new Set(descs).size).toBe(12);
    });

    it('contains no http:// href/src in built source expectations (repo rejects http)', (): void => {
      const allStrings = JSON.stringify([...allTitles(), ...allDescriptions()]);
      expect(allStrings).not.toContain('http://');
    });

    it('contains no cdn-cgi href expectation', (): void => {
      const allStrings = JSON.stringify([...allTitles(), ...allDescriptions()]);
      expect(allStrings).not.toContain('cdn-cgi');
    });

    it('contains no IndexNow artifacts', (): void => {
      const allStrings = JSON.stringify([...allTitles(), ...allDescriptions()]);
      expect(allStrings).not.toContain('api.indexnow.org');
      expect(allStrings).not.toContain('IndexNow');
    });
  });

  describe('seo guard — Lighthouse budget recorded', () => {
    it('lighthouse baseline exists and covers 12 URLs', async (): Promise<void> => {
      const { existsSync, readFileSync } = await import('node:fs');
      const { join } = await import('node:path');
      const baseline: string = join(process.cwd(), '..', '..', '..', 'docs', 'marketing', 'lighthouse', 'baseline.json');
      const alt: string = join(process.cwd(), 'docs', 'marketing', 'lighthouse', 'baseline.json');
      const candidates: string[] = [baseline, alt, join(process.cwd(), 'apps/web/marketing/docs/marketing/lighthouse/baseline.json')];
      const found: string | undefined = candidates.find((p: string): boolean => existsSync(p));
      expect(found, 'lighthouse baseline.json missing').toBeTruthy();
      const data: { urls?: unknown[] } & unknown[] = JSON.parse(readFileSync(found as string, 'utf8'));
      expect(Array.isArray((data as { urls?: unknown[] }).urls) || Array.isArray(data)).toBe(true);
      const urls: unknown[] = Array.isArray(data) ? (data as unknown[]) : ((data as { urls: unknown[] }).urls as unknown[]);
      expect(urls.length).toBeGreaterThanOrEqual(12);
    });
  });

  describe('seo guard — IndexNow absent and no http href (repo)', () => {
    it('source contains no IndexNow endpoint', async (): Promise<void> => {
      const { readFileSync, readdirSync } = await import('node:fs');
      const { join } = await import('node:path');
      const root: string = join(import.meta.dirname ?? '.', '..');
      const needle: string = ['api', 'indexnow', 'org'].join('.');
      const check = (dir: string): string[] => {
        const out: string[] = [];
        for (const entry of readdirSync(dir, { withFileTypes: true })) {
          if (entry.name.startsWith('.') || entry.name === 'node_modules' || entry.name === 'dist') continue;
          const full: string = join(dir, entry.name);
          if (entry.isDirectory()) out.push(...check(full));
          else if (entry.isFile() && /\.(ts|js|astro|mjs)$/.test(entry.name)) {
            const content: string = readFileSync(full, 'utf8');
            if (content.includes(needle) && !full.endsWith('utils.test.ts')) out.push(full);
          }
        }
        return out;
      };
      const hits: string[] = check(root);
      expect(hits, `IndexNow found in ${hits.join(',')}`).toEqual([]);
    });

    it('sitemap contract test file does not embed index-discovery endpoints in source', async (): Promise<void> => {
      const { readFileSync } = await import('node:fs');
      const { join } = await import('node:path');
      const root: string = join(import.meta.dirname ?? '.', '..');
      const sitemapTest: string = join(root, '__tests__', 'sitemap.xml.test.ts');
      const content: string = readFileSync(sitemapTest, 'utf8');
      const bannedTokens: string[] = ['api.indexnow.org', 'indexnow.org'];
      for (const token of bannedTokens) {
        expect(content.includes(token), `${sitemapTest} contains ${token}`).toBe(false);
      }
    });
  });

  describe('locale navigation parity and accessible-name alignment', () => {
    it('EN locale switch accessible name contains the visible destination code', () => {
      const t = useTranslations(new URL('https://example.com/'));
      expect(t.nav.langSwitch).toBe('ES');
      expect(t.nav.langSwitchLabel).toContain(t.nav.langSwitch);
    });

    it('ES locale switch accessible name contains the visible destination code', () => {
      const t = useTranslations(new URL('https://example.com/es/'));
      expect(t.nav.langSwitch).toBe('EN');
      expect(t.nav.langSwitchLabel).toContain(t.nav.langSwitch);
    });

    it('EN nav langSwitchLabel is in the current page language (English)', () => {
      const t = useTranslations(new URL('https://example.com/'));
      expect(t.nav.langSwitchLabel).toBe('Switch to Spanish (ES)');
    });

    it('ES nav langSwitchLabel is in the current page language (Spanish)', () => {
      const t = useTranslations(new URL('https://example.com/es/'));
      expect(t.nav.langSwitchLabel).toBe('Cambiar a inglés (EN)');
    });
  });

  describe('route inventory and pairing helpers', () => {
    it('counterpartPath maps / to /es/ and vice versa', () => {
      expect(counterpartPath('en', '/')).toBe('/es/');
      expect(counterpartPath('es', '/')).toBe('/');
    });

    it('counterpartPath maps legal routes to /es/<route>', () => {
      const legal: Array<'/privacy/' | '/terms/' | '/cookies/' | '/acceptable-use/' | '/accessibility/'> = [
        '/privacy/',
        '/terms/',
        '/cookies/',
        '/acceptable-use/',
        '/accessibility/',
      ];
      for (const r of legal) {
        expect(counterpartPath('en', r)).toBe(`/es${r}`);
        expect(counterpartPath('es', r)).toBe(r);
      }
    });

    it('canonicalUrl returns HTTPS trailing-slash URLs for all 6 routes', () => {
      const base = new URL('https://profiletailors.com');
      const routes: Array<'/' | '/privacy/' | '/terms/' | '/cookies/' | '/acceptable-use/' | '/accessibility/'> = [
        '/',
        '/privacy/',
        '/terms/',
        '/cookies/',
        '/acceptable-use/',
        '/accessibility/',
      ];
      for (const route of routes) {
        const en = canonicalUrl('en', route, base);
        const es = canonicalUrl('es', route, base);
        expect(en.startsWith('https://'), en).toBe(true);
        expect(en.endsWith('/'), en).toBe(true);
        expect(es.startsWith('https://'), es).toBe(true);
        expect(es.endsWith('/'), es).toBe(true);
      }
    });

    it('canonicalUrl for home EN is "/" and ES is "/es/"', () => {
      const base = new URL('https://profiletailors.com');
      expect(canonicalUrl('en', '/', base)).toBe('https://profiletailors.com/');
      expect(canonicalUrl('es', '/', base)).toBe('https://profiletailors.com/es/');
    });

    it('routeSeoEntries has exactly 6 entries with the expected shape', () => {
      const entries = routeSeoEntries();
      expect(entries).toHaveLength(6);
      for (const e of entries) {
        expect(e.route).toMatch(/^\/([a-z-]+\/?)?$/);
        expect(e.indexable).toBe(true);
        expect(['WebSite', 'WebPage']).toContain(e.jsonLdType);
        expect(e.title.length).toBeGreaterThanOrEqual(30);
        expect(e.description.length).toBeGreaterThanOrEqual(120);
        expect(e.description.length).toBeLessThanOrEqual(160);
      }
    });

    it('routeSeoEntries declares WebSite only for home and WebPage for legal routes', () => {
      const entries = routeSeoEntries();
      const home = entries.find((e) => e.route === '/');
      const privacy = entries.find((e) => e.route === '/privacy/');
      expect(home?.jsonLdType).toBe('WebSite');
      expect(privacy?.jsonLdType).toBe('WebPage');
    });
  });
});
