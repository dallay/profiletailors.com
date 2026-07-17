import { describe, it, expect } from 'vitest';
import { getLocaleFromUrl, useTranslations } from './utils';

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
        expectedHeroLabel: 'NOW IN EARLY ACCESS',
        scenario: 'root URL',
      },
      {
        pathname: 'https://example.com/es/',
        expectedLangSwitch: 'EN',
        expectedHeroLabel: 'ACCESO ANTICIPADO',
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
  });
});
