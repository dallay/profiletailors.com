import { describe, it, expect } from 'vitest';
import { getLocaleFromUrl, useTranslations } from './utils';

describe('i18n utils', () => {
  describe('getLocaleFromUrl', () => {
    it('should return "en" for root URL', () => {
      const url = new URL('https://example.com/');
      expect(getLocaleFromUrl(url)).toBe('en');
    });

    it('should return "es" for Spanish URL', () => {
      const url = new URL('https://example.com/es/');
      expect(getLocaleFromUrl(url)).toBe('es');
    });

    it('should return "en" for English URL', () => {
      const url = new URL('https://example.com/en/');
      expect(getLocaleFromUrl(url)).toBe('en');
    });

    it('should default to "en" for unknown locale', () => {
      const url = new URL('https://example.com/fr/');
      expect(getLocaleFromUrl(url)).toBe('en');
    });
  });

  describe('useTranslations', () => {
    it('should return English translations by default', () => {
      const url = new URL('https://example.com/');
      const t = useTranslations(url);
      
      expect(t.nav.langSwitch).toBe('ES');
      expect(t.hero.label).toBe('NOW IN EARLY ACCESS');
    });

    it('should return Spanish translations for /es/', () => {
      const url = new URL('https://example.com/es/');
      const t = useTranslations(url);
      
      expect(t.nav.langSwitch).toBe('EN');
      expect(t.hero.label).toBe('ACCESO ANTICIPADO');
    });
  });
});
