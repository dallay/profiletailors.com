// src/i18n/utils.ts
import { en } from './en'
import { es } from './es'

export type Locale = 'en' | 'es'

export const locales: Locale[] = ['en', 'es']
export const defaultLocale: Locale = 'en'

const translations = { en, es } as const

export type Translations = typeof en

export function useTranslations(lang: Locale): Translations {
  return translations[lang]
}
