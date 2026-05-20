import { ref } from 'vue'
import { defineStore } from 'pinia'
import i18n from '@/i18n'

export const useSettingsStore = defineStore('settings', () => {
  const currentLocale = ref<'en' | 'es'>('en')
  const currentTheme = ref<'dark' | 'light'>('dark')

  function setLocale(locale: 'en' | 'es') {
    currentLocale.value = locale
    // Update vue-i18n locale
    i18n.global.locale.value = locale
    // Update HTML lang attribute for accessibility/SEO
    document.documentElement.setAttribute('lang', locale)
  }

  function toggleLocale() {
    setLocale(currentLocale.value === 'en' ? 'es' : 'en')
  }

  function setTheme(theme: 'dark' | 'light') {
    currentTheme.value = theme
    if (theme === 'light') {
      document.documentElement.classList.add('light')
      document.documentElement.classList.remove('dark')
    } else {
      document.documentElement.classList.add('dark')
      document.documentElement.classList.remove('light')
    }
  }

  function toggleTheme() {
    setTheme(currentTheme.value === 'dark' ? 'light' : 'dark')
  }

  // Initialize DOM attributes
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('lang', currentLocale.value)
    // Dark by default
    document.documentElement.classList.add('dark')
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
