import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import i18n from '@/i18n'

/** Versioned storage key — bump to invalidate older persisted state. */
const STORAGE_KEY = 'pt_settings_v1'

const SUPPORTED_LOCALES = ['en', 'es'] as const
const SUPPORTED_THEMES = ['dark', 'light'] as const

type Locale = (typeof SUPPORTED_LOCALES)[number]
type Theme = (typeof SUPPORTED_THEMES)[number]

interface PersistedSettings {
  locale: Locale
  theme: Theme
}

const isLocale = (value: unknown): value is Locale =>
  typeof value === 'string' && (SUPPORTED_LOCALES as readonly string[]).includes(value)

const isTheme = (value: unknown): value is Theme =>
  typeof value === 'string' && (SUPPORTED_THEMES as readonly string[]).includes(value)

const readPersisted = (): PersistedSettings | null => {
  if (typeof localStorage === 'undefined') return null
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<PersistedSettings>
    if (!isLocale(parsed.locale) || !isTheme(parsed.theme)) return null
    return { locale: parsed.locale, theme: parsed.theme }
  } catch {
    return null
  }
}

const writePersisted = (value: PersistedSettings): void => {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
  } catch {
    // localStorage may be unavailable (e.g. private mode); fall back to in-memory.
  }
}

const applyTheme = (theme: Theme): void => {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.classList.toggle('light', theme === 'light')
  root.classList.toggle('dark', theme === 'dark')
  root.style.colorScheme = theme
}

const applyLocale = (locale: Locale): void => {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('lang', locale)
  i18n.global.locale.value = locale
}

export const useSettingsStore = defineStore('settings', () => {
  const initial = readPersisted() ?? { locale: 'en' as Locale, theme: 'dark' as Theme }

  const currentLocale = ref<Locale>(initial.locale)
  const currentTheme = ref<Theme>(initial.theme)

  /**
   * Sets the application locale.
   *
   * @param locale - The locale to apply
   */
  function setLocale(locale: Locale) {
    currentLocale.value = locale
    applyLocale(locale)
  }

  /**
   * Toggles the current locale between 'en' and 'es'.
   */
  function toggleLocale() {
    setLocale(currentLocale.value === 'en' ? 'es' : 'en')
  }

  /**
   * Sets the current theme and applies it to the document.
   *
   * @param theme - The theme to apply
   */
  function setTheme(theme: Theme) {
    currentTheme.value = theme
    applyTheme(theme)
  }

  /**
   * Switches the current theme between dark and light.
   */
  function toggleTheme() {
    setTheme(currentTheme.value === 'dark' ? 'light' : 'dark')
  }

  // Persist on every change. Use `flush: 'sync'` so writes are observable
  // synchronously after a set* call — this matters for tests and for
  // reading the persisted state from a second tab.
  watch(
    [currentLocale, currentTheme],
    ([locale, theme]) => {
      writePersisted({ locale, theme })
    },
    { immediate: true, flush: 'sync' },
  )

  // Apply DOM attributes for SSR safety — only run in browser.
  if (typeof document !== 'undefined') {
    applyTheme(currentTheme.value)
    applyLocale(currentLocale.value)
  }

  return {
    currentLocale,
    currentTheme,
    setLocale,
    toggleLocale,
    setTheme,
    toggleTheme,
  }
})
