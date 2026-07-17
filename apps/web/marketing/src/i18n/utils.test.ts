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
});
