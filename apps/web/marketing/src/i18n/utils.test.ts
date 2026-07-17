import { describe, it, expect } from 'vitest';
import { getLocaleFromUrl, useTranslations } from './utils';
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
    it('EN has legal key with all four policy sections', () => {
      const t = useTranslations(new URL('https://example.com/'));
      expect(t.legal.privacy).toBeDefined();
      expect(t.legal.terms).toBeDefined();
      expect(t.legal.cookies).toBeDefined();
      expect(t.legal.aup).toBeDefined();
    });

    it('ES has same legal section structure as EN', () => {
      const tEn = useTranslations(new URL('https://example.com/'));
      const tEs = useTranslations(new URL('https://example.com/es/'));
      expect(Object.keys(tEs.legal.privacy)).toEqual(Object.keys(tEn.legal.privacy));
      expect(Object.keys(tEs.legal.terms)).toEqual(Object.keys(tEn.legal.terms));
      expect(Object.keys(tEs.legal.cookies)).toEqual(Object.keys(tEn.legal.cookies));
      expect(Object.keys(tEs.legal.aup)).toEqual(Object.keys(tEn.legal.aup));
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

    it('keeps legal policy publication blocked until the evidence gate is approved', () => {
      expect(legalPublicationStatus).toBe(LEGAL_PUBLICATION_STATUS.BLOCKED);
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
