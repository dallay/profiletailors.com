import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import i18n from '@shared/i18n'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useSettingsStore } from '@modules/settings/infrastructure/settings.store'

async function main() {
  const app = createApp(App)
  const pinia = createPinia()

  app.use(pinia)

  const authStore = useAuthStore(pinia)
  useSettingsStore(pinia)

  // Hydrate session BEFORE mounting the router so the route guard
  // always sees resolved session state (avoids race where navigation
  // starts before hydration completes).
  try {
    await authStore.hydrateSession()
  } catch (error) {
    console.error('Failed to hydrate session:', error)
  }

  app.use(i18n)
  app.use(router)

  app.mount('#app')
}

try {
  await main()
} catch (error) {
  console.error(error)
}
