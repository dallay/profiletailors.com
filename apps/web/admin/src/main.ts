import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'

import App from './App.vue'
import router from './router'
import { messages } from './i18n'
import { useAdminAuthStore } from './stores/auth.store'
import { configureAdminSentry } from '@/lib/sentry'

const i18n = createI18n({
  legacy: false,
  locale: navigator.language.startsWith('es') ? 'es' : 'en',
  fallbackLocale: 'en',
  messages,
})

async function main() {
  const app = createApp(App)
  const reportStartupFailure = configureAdminSentry(app, router)

  try {
    const pinia = createPinia()

    app.use(pinia)

    const authStore = useAdminAuthStore(pinia)
    await authStore.hydrateSession()

    app.use(i18n)
    app.use(router)
    app.mount('#app')
  } catch (error) {
    reportStartupFailure(error)
    throw error
  }
}

main().catch(console.error)
