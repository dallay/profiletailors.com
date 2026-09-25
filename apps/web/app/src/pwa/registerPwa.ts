import { registerSW } from 'virtual:pwa-register'

export function initPwa(onNeedRefresh: () => void): void {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return
  registerSW({ onNeedRefresh })
}
