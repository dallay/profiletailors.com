import { describe, it, expect } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
import { getLocaleFromUrl, useTranslations } from './utils';
import {
  LEGAL_PUBLICATION_STATUS,
  legalPublicationStatus,
  isLegalPublicationApproved,
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

  describe('legal content collection', () => {
    it('has markdown files for all four policy sections in EN', () => {
      const slugs = ['privacy', 'terms', 'cookies', 'acceptable-use'];
      for (const slug of slugs) {
        const filePath = path.resolve(
          __dirname,
          '../../src/content/legal/en',
          `${slug}.md`,
        );
        const content = fs.readFileSync(filePath, 'utf-8');
        expect(content).toContain('---');
        expect(content).toContain('title:');
      }
    });

    it('has matching markdown files for all policy sections in ES', () => {
      const slugs = ['privacy', 'terms', 'cookies', 'acceptable-use'];
      for (const slug of slugs) {
        const filePath = path.resolve(
          __dirname,
          '../../src/content/legal/es',
          `${slug}.md`,
        );
        const content = fs.readFileSync(filePath, 'utf-8');
        expect(content).toContain('---');
        expect(content).toContain('title:');
      }
    });

    it('footer has legalLinks with 4 entries', () => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      expect(tEn.footer.legalLinks).toHaveLength(4);
      expect(tEs.footer.legalLinks).toHaveLength(4);
      tEn.footer.legalLinks.forEach((link) => {
        expect(link).toHaveProperty('label');
        expect(link).toHaveProperty('href');
      });
    });

    it('legal policy publication is approved and rendering content', () => {
      expect(legalPublicationStatus).toBe(LEGAL_PUBLICATION_STATUS.APPROVED);
      expect(isLegalPublicationApproved()).toBe(true);
    });

    it('does not contain unsupported provider or contractual claims in markdown files', () => {
      const locales = ['en', 'es'];
      const slugs = ['privacy', 'terms', 'cookies', 'acceptable-use'];
      const legalCopy = locales
        .flatMap((locale) =>
          slugs.map((slug) =>
            fs.readFileSync(
              path.resolve(__dirname, `../../src/content/legal/${locale}/${slug}.md`),
              'utf-8',
            ),
          ),
        )
        .join('\n');
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
});
