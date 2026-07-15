import { createI18n } from 'vue-i18n'
import en from './locales/en'
import es from './locales/es'

const messages = {
  en,
  es,
}

export type Translations = typeof en

/** Flatten a nested translation object into dot‑notation keys. */
export function flattenTranslations(obj: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      return flattenTranslations(value as Record<string, unknown>, path)
    }
    return [path]
  })
}

export const messages_en = en
export const messages_es = es

const i18n = createI18n({
  legacy: false, // Use Composition API
  locale: 'en', // default locale
  fallbackLocale: 'en',
  missingWarn: true,
  messages,
})

export default i18n
