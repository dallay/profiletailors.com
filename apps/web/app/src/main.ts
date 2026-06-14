import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'

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

main().catch(console.error)
