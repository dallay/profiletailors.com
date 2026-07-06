// src/i18n/utils.ts
import { en } from './en'
import { es } from './es'

export type Locale = 'en' | 'es'

export const locales: Locale[] = ['en', 'es']
export const defaultLocale: Locale = 'en'

const translations = { en, es } as const

export type Translations = typeof en

export function getLocaleFromUrl(url: URL): Locale {
  const pathname = url.pathname
  const firstSegment = pathname.split('/').find(Boolean)

  if (firstSegment && locales.includes(firstSegment as Locale)) {
    return firstSegment as Locale
  }

  return defaultLocale
}

export function useTranslations(urlOrLang: URL | Locale): Translations {
  const lang = typeof urlOrLang === 'string' ? urlOrLang : getLocaleFromUrl(urlOrLang)
  return translations[lang] as Translations
}
