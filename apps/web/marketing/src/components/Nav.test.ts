// @vitest-environment node

import { experimental_AstroContainer as AstroContainer } from 'astro/container'
import { describe, expect, it } from 'vitest'
import { useTranslations } from '../i18n/utils'
import Nav from './Nav.astro'

describe('Nav language switch accessibility', () => {
  it.each([
    { locale: 'en', code: 'ES', name: 'Switch to Spanish (ES)' },
    { locale: 'es', code: 'EN', name: 'Cambiar a inglés (EN)' },
  ] as const)('includes the visible language code exactly once in the accessible name for $locale', async ({ locale, code, name }) => {
    const container = await AstroContainer.create()
    const t = useTranslations(locale)
    const langHref = locale === 'en' ? '/es/' : '/'
    const result = await container.renderToString(Nav, {
      props: {
        locale,
        langSwitch: t.nav.langSwitch,
        langHref,
        langLabel: t.nav.langSwitchLabel,
      },
    })
    const link = result.match(new RegExp(`<a[^>]*href="${langHref}"[^>]*>(.*?)</a>`, 's'))
    const accessibleName = link?.[0].match(/aria-label="([^"]*)"/)?.[1]

    expect(link?.[1].trim()).toBe(code)
    expect(accessibleName).toBe(name)
    expect(accessibleName?.match(new RegExp(`\\b${code}\\b`, 'g'))).toHaveLength(1)
  })
})
