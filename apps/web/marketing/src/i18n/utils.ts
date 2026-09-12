// src/i18n/utils.ts
import { en } from './en'
import { es } from './es'

export type Locale = 'en' | 'es'

export const locales: Locale[] = ['en', 'es']
export const defaultLocale: Locale = 'en'

const translations = { en, es } as const

export type Translations = typeof en

/** Shape of any single legal policy section from the i18n structure */
export type LegalPolicy = Translations['legal']['privacy']
/** All legal translations (privacy, terms, cookies, aup) */
export type LegalTranslations = Translations['legal']

export type RouteId =
  | '/'
  | '/privacy/'
  | '/terms/'
  | '/cookies/'
  | '/acceptable-use/'
  | '/accessibility/'

export type RouteSeo = {
  route: RouteId
  title: string
  description: string
  indexable: boolean
  jsonLdType: 'WebSite' | 'WebPage'
}

const ROUTE_INVENTORY: readonly RouteId[] = [
  '/',
  '/privacy/',
  '/terms/',
  '/cookies/',
  '/acceptable-use/',
  '/accessibility/',
] as const

export function counterpartPath(locale: Locale, route: RouteId): RouteId | string {
  if (route === '/') {
    return locale === 'en' ? '/es/' : '/'
  }
  return locale === 'en' ? `/es${route}` : route
}

export function canonicalUrl(locale: Locale, route: RouteId, base: URL): string {
  const path = locale === 'en' ? route : route === '/' ? '/es/' : `/es${route}`
  return new URL(path, base).href
}

export function routeSeoEntries(): readonly RouteSeo[] {
  const enTranslations = translations.en
  return ROUTE_INVENTORY.map((route): RouteSeo => {
    if (route === '/') {
      return {
        route,
        title: enTranslations.meta.title,
        description: enTranslations.meta.description,
        indexable: true,
        jsonLdType: 'WebSite',
      }
    }
    const key = legalRouteKey(route)
    const section = enTranslations.legal[key]
    return {
      route,
      title: section.title,
      description: section.description,
      indexable: true,
      jsonLdType: 'WebPage',
    }
  })
}

type LegalRouteKey = 'privacy' | 'terms' | 'cookies' | 'aup' | 'accessibility'

function legalRouteKey(route: RouteId): LegalRouteKey {
  switch (route) {
    case '/privacy/':
      return 'privacy'
    case '/terms/':
      return 'terms'
    case '/cookies/':
      return 'cookies'
    case '/acceptable-use/':
      return 'aup'
    case '/accessibility/':
      return 'accessibility'
    default:
      throw new Error(`Unknown legal route ${route}`)
  }
}

export function getLocaleFromUrl(url: URL): Locale {
  const pathname = url.pathname
  const firstSegment = pathname.split('/').find(Boolean)

  if (firstSegment && locales.includes(firstSegment as Locale)) {
    return firstSegment as Locale
  }

  return defaultLocale
}

export function useTranslations(urlOrLang: URL | Locale): Translations {
  const lang = typeof urlOrLang === 'string'
    ? urlOrLang
    : getLocaleFromUrl(urlOrLang)
  return translations[lang] as Translations
}

