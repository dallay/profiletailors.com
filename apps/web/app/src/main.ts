import './assets/main.css'
import 'driver.js/dist/driver.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import i18n from '@shared/i18n'
import { initPwa } from '@/pwa/registerPwa'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useSettingsStore } from '@modules/settings/infrastructure/settings.store'
import { configureAppSentry } from '@shared/lib/sentry'

async function main() {
  const app = createApp(App)
  const reportStartupFailure = configureAppSentry(app, router)
  const pinia = createPinia()

  app.use(pinia)

  const authStore = useAuthStore(pinia)
  useSettingsStore(pinia)

  try {
    try {
      await authStore.hydrateSession()
    } catch (error) {
      reportStartupFailure(error)
      console.error('Failed to hydrate session:', error)
    }

    app.use(i18n)
    app.use(router)

    initPwa(() => window.dispatchEvent(new CustomEvent('pwa:need-refresh')))

    app.mount('#app')
  } catch (error) {
    reportStartupFailure(error)
    throw error
  }
}

try {
  await main()
} catch (error) {
  console.error(error)
}
